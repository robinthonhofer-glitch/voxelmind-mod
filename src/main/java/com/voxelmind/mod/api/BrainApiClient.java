package com.voxelmind.mod.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.voxelmind.mod.VoxelMindMod;
import com.voxelmind.mod.api.dto.AgentStatus;
import com.voxelmind.mod.api.dto.ApiError;
import com.voxelmind.mod.api.dto.BotInfo;
import com.voxelmind.mod.api.dto.BotState;
import com.voxelmind.mod.api.dto.ProfileInfo;
import com.voxelmind.mod.auth.AuthManager;
import com.voxelmind.mod.config.ModConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Central REST client for all Brain API calls.
 * All methods return CompletableFuture — never blocks the MC main thread.
 */
public class BrainApiClient {
    private static final Gson GSON = new Gson();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final BrainApiClient INSTANCE = new BrainApiClient();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    public static BrainApiClient get() { return INSTANCE; }

    // ─── Bot CRUD ───

    public CompletableFuture<List<BotInfo>> listBots() {
        return sendAsync("GET", "/bots", null)
                .thenApply(body -> GSON.fromJson(body, new TypeToken<List<BotInfo>>(){}.getType()));
    }

    public CompletableFuture<BotInfo> createBot(String name, String personalityId, String ownerPlayerName) {
        var payload = Map.of(
                "bot_name", name,
                "personality_id", personalityId,
                "owner_player_name", ownerPlayerName
        );
        return sendAsync("POST", "/bots", GSON.toJson(payload))
                .thenApply(body -> GSON.fromJson(body, BotInfo.class));
    }

    public CompletableFuture<Void> deleteBot(String botId) {
        return sendAsync("DELETE", "/bots/" + botId, null)
                .thenApply(body -> null);
    }

    public CompletableFuture<BotInfo> updateBot(String botId, String name, String personalityId) {
        var map = new java.util.HashMap<String, String>();
        if (name != null) map.put("bot_name", name);
        if (personalityId != null) map.put("personality_id", personalityId);
        return sendAsync("PATCH", "/bots/" + botId, GSON.toJson(map))
                .thenApply(body -> GSON.fromJson(body, BotInfo.class));
    }

    // ─── Spawn/Despawn ───

    public CompletableFuture<Void> spawnBot(String botId, String host, int port) {
        var payload = Map.of("host", host, "port", port);
        return sendAsync("POST", "/bots/" + botId + "/spawn", GSON.toJson(payload))
                .thenApply(body -> null);
    }

    public CompletableFuture<Void> despawnBot(String botId) {
        return sendAsync("POST", "/bots/" + botId + "/despawn", null)
                .thenApply(body -> null);
    }

    // ─── Status ───

    public CompletableFuture<BotState> getBotState(String botId) {
        return sendAsync("GET", "/bots/" + botId + "/state", null)
                .thenApply(body -> GSON.fromJson(body, BotState.class));
    }

    public CompletableFuture<AgentStatus> getAgentStatus() {
        return sendAsync("GET", "/agent-status", null)
                .thenApply(body -> GSON.fromJson(body, AgentStatus.class));
    }

    // ─── Profile / Feedback ───

    public CompletableFuture<ProfileInfo> getProfile() {
        return sendAsync("GET", "/profile", null)
                .thenApply(body -> GSON.fromJson(body, ProfileInfo.class));
    }

    public CompletableFuture<Void> updateMcUsername(String mcUsername) {
        var payload = Map.of("mc_username", mcUsername);
        return sendAsync("PATCH", "/profile", GSON.toJson(payload))
                .thenApply(body -> null);
    }

    public CompletableFuture<Void> sendFeedback(String text, String botId) {
        var map = new java.util.HashMap<String, String>();
        map.put("text", text);
        if (botId != null && !botId.isEmpty()) map.put("bot_id", botId);
        return sendAsync("POST", "/feedback", GSON.toJson(map))
                .thenApply(body -> null);
    }

    public CompletableFuture<Boolean> healthCheck() {
        return http.sendAsync(
                HttpRequest.newBuilder()
                        .uri(URI.create(ModConfig.getBrainUrl() + "/health"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(r -> r.statusCode() == 200)
         .exceptionally(e -> false);
    }

    // ─── Internal ───

    private CompletableFuture<String> sendAsync(String method, String path, String body) {
        return doSend(method, path, body, true);
    }

    private CompletableFuture<String> doSend(String method, String path, String body, boolean retryOn401) {
        String token = ModConfig.getAccessToken();
        if (token.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new ApiException("Not logged in. Use /vm login first."));
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(ModConfig.getBrainUrl() + path))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");

        switch (method) {
            case "GET" -> builder.GET();
            case "DELETE" -> builder.DELETE();
            case "POST" -> builder.POST(body != null
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody());
            case "PATCH" -> builder.method("PATCH", body != null
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody());
        }

        return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    int status = response.statusCode();

                    // 401 → try token refresh once
                    if (status == 401 && retryOn401) {
                        return AuthManager.get().refreshAccessToken()
                                .thenCompose(success -> {
                                    if (success) {
                                        return doSend(method, path, body, false);
                                    }
                                    return CompletableFuture.<String>failedFuture(
                                            new ApiException("Session expired. Please /vm login again."));
                                });
                    }

                    if (status >= 200 && status < 300) {
                        return CompletableFuture.completedFuture(response.body());
                    }

                    // Parse error message from response
                    String errorMsg;
                    try {
                        ApiError err = GSON.fromJson(response.body(), ApiError.class);
                        errorMsg = err != null && err.error != null ? err.error : response.body();
                    } catch (Exception e) {
                        errorMsg = response.body();
                    }

                    // Friendly messages
                    if (status == 502 && errorMsg.contains("No agent connected")) {
                        errorMsg = "VoxelMind server is starting up, try again in a moment.";
                    }

                    return CompletableFuture.failedFuture(new ApiException(errorMsg));
                })
                .exceptionally(e -> {
                    if (e.getCause() instanceof ApiException) {
                        throw (ApiException) e.getCause();
                    }
                    throw new ApiException("Cannot reach VoxelMind servers. Check your internet connection.");
                });
    }

    /**
     * Unchecked exception for API errors — safe to catch in CompletableFuture chains.
     */
    public static class ApiException extends RuntimeException {
        public ApiException(String message) { super(message); }
    }
}
