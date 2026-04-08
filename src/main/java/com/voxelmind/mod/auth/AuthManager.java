package com.voxelmind.mod.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.voxelmind.mod.VoxelMindMod;
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
        }, error -> {
            state = State.ERROR;
            errorMessage = error;
            VoxelMindMod.LOGGER.error("Login failed: {}", error);
        });

        callbackServer.start();

        // Open browser to VoxelMind login page
        String loginUrl = "https://voxel-mind.com/auth/mod-login?callback=http://localhost:9876/callback";
        try {
            java.awt.Desktop.getDesktop().browse(URI.create(loginUrl));
        } catch (Exception e) {
            VoxelMindMod.LOGGER.error("Failed to open browser: {}", e.getMessage());
            state = State.ERROR;
            errorMessage = "Could not open browser. Visit: " + loginUrl;
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

    public void cancelLogin() {
        if (callbackServer != null) {
            callbackServer.stop();
            callbackServer = null;
        }
        if (state == State.LOGGING_IN) {
            state = State.LOGGED_OUT;
        }
    }
}
