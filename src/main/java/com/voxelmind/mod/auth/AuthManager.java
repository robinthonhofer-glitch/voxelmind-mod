package com.voxelmind.mod.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.voxelmind.mod.VoxelMindMod;
import com.voxelmind.mod.api.BrainApiClient;
import com.voxelmind.mod.config.ModConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Manages authentication state and token lifecycle.
 */
public class AuthManager {
    private static final String SUPABASE_URL = "https://sltwxqpxuogecbyfoqlb.supabase.co";
    private static final String SUPABASE_ANON_KEY = "sb_publishable_7iBeTBEFX8PS_wmE2XTReg_k-h52237";
    private static final Gson GSON = new Gson();

    private static final AuthManager INSTANCE = new AuthManager();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private OAuthCallbackServer callbackServer;

    public enum State { LOGGED_OUT, LOGGING_IN, LOGGED_IN, ERROR }
    private volatile State state = State.LOGGED_OUT;
    private volatile String errorMessage = "";

    public static AuthManager get() { return INSTANCE; }

    public State getState() {
        if (ModConfig.isLoggedIn() && state == State.LOGGED_OUT) {
            state = State.LOGGED_IN;
        }
        return state;
    }

    public String getErrorMessage() { return errorMessage; }

    /**
     * Start the OAuth browser login flow.
     */
    public void startLogin() {
        if (state == State.LOGGING_IN) return;
        state = State.LOGGING_IN;
        errorMessage = "";

        callbackServer = new OAuthCallbackServer(9876, (accessToken, refreshToken) -> {
            ModConfig.setTokens(accessToken, refreshToken);
            state = State.LOGGED_IN;
            VoxelMindMod.LOGGER.info("Login successful!");
            // Silently push the current MC username to the Brain so the server side
            // (Whisper detection, Follow-Player, etc.) knows who owns this account.
            syncMcUsernameSilent();
        }, error -> {
            state = State.ERROR;
            errorMessage = error;
            VoxelMindMod.LOGGER.error("Login failed: {}", error);
            sendOAuthFailedTelemetry("callback", error);
        });

        callbackServer.start();

        // Open browser to VoxelMind login page
        String loginUrl = "https://voxel-mind.com/auth/mod-login?callback=http://localhost:9876/callback";
        try {
            net.minecraft.util.Util.getOperatingSystem().open(URI.create(loginUrl));
            VoxelMindMod.LOGGER.info("Opened browser: {}", loginUrl);
        } catch (Exception e) {
            VoxelMindMod.LOGGER.error("Failed to open browser: {}", e.getMessage());
            state = State.ERROR;
            errorMessage = "Could not open browser. Visit: " + loginUrl;
            sendOAuthFailedTelemetry("browser_open", e.getMessage());
        }
    }

    /**
     * Logout — clear tokens.
     */
    public void logout() {
        ModConfig.clearTokens();
        state = State.LOGGED_OUT;
        errorMessage = "";
        VoxelMindMod.LOGGER.info("Logged out.");
    }

    /**
     * Refresh the access token using the stored refresh token.
     * Returns true if refresh succeeded.
     */
    public CompletableFuture<Boolean> refreshAccessToken() {
        String refreshToken = ModConfig.getRefreshToken();
        if (refreshToken.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        String body = GSON.toJson(java.util.Map.of("refresh_token", refreshToken));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("apikey", SUPABASE_ANON_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                        String newAccess = json.get("access_token").getAsString();
                        String newRefresh = json.get("refresh_token").getAsString();
                        ModConfig.setTokens(newAccess, newRefresh);
                        VoxelMindMod.LOGGER.info("Token refreshed successfully.");
                        return true;
                    }
                    VoxelMindMod.LOGGER.warn("Token refresh failed: HTTP {}", response.statusCode());
                    return false;
                })
                .exceptionally(e -> {
                    VoxelMindMod.LOGGER.warn("Token refresh error: {}", e.getMessage());
                    return false;
                });
    }

    /**
     * Fire-and-forget: tell the Brain our current Minecraft session username so the
     * server can populate profiles.mc_username. The user should never have to set
     * this manually — the mod already knows it.
     */
    public void syncMcUsernameSilent() {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc == null || mc.getSession() == null) return;
            String name = mc.getSession().getUsername();
            if (name == null || name.isEmpty()) return;
            com.voxelmind.mod.api.BrainApiClient.get().updateMcUsername(name)
                    .exceptionally(e -> {
                        VoxelMindMod.LOGGER.debug("Silent mc_username sync failed: {}",
                                e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            VoxelMindMod.LOGGER.debug("syncMcUsernameSilent skipped: {}", e.getMessage());
        }
    }

    public void cancelLogin() {
        if (callbackServer != null) {
            callbackServer.stop();
            callbackServer = null;
        }
        if (state == State.LOGGING_IN) {
            state = State.LOGGED_OUT;
        }
    }

    /**
     * Fire-and-forget telemetry for OAuth failures.
     * Safe to call at any point — if there's no token yet, BrainApiClient will skip silently.
     */
    private void sendOAuthFailedTelemetry(String stage, String errorMsg) {
        try {
            JsonObject data = new JsonObject();
            data.addProperty("stage", stage);
            if (errorMsg != null) {
                data.addProperty("error", errorMsg);
            }
            BrainApiClient.get().sendTelemetry("oauth_failed", "error", data, null);
        } catch (Exception e) {
            VoxelMindMod.LOGGER.debug("AuthManager: Telemetry send failed: {}", e.getMessage());
        }
    }
}
