package com.voxelmind.mod.auth;

import com.sun.net.httpserver.HttpServer;
import com.voxelmind.mod.VoxelMindMod;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Temporary localhost HTTP server that receives the OAuth callback from the browser.
 * Listens for GET /callback?access_token=...&refresh_token=...
 * Auto-shuts down after 60s timeout or after receiving a callback.
 */
public class OAuthCallbackServer {
    private final int port;
    private final BiConsumer<String, String> onSuccess;
    private final Consumer<String> onError;
    private HttpServer server;
    private ScheduledExecutorService timeoutExecutor;

    private static final String SUCCESS_HTML = """
            <!DOCTYPE html>
            <html><head><title>VoxelMind</title>
            <style>body{font-family:sans-serif;display:flex;justify-content:center;align-items:center;height:100vh;margin:0;background:#1a1a2e;color:#e0e0e0;}
            .card{text-align:center;padding:40px;border-radius:12px;background:#16213e;box-shadow:0 4px 20px rgba(0,0,0,0.3);}
            h1{color:#4ade80;}</style></head>
            <body><div class="card"><h1>Login Successful!</h1><p>You can close this tab and return to Minecraft.</p></div></body></html>
            """;

    public OAuthCallbackServer(int port, BiConsumer<String, String> onSuccess, Consumer<String> onError) {
        this.port = port;
        this.onSuccess = onSuccess;
        this.onError = onError;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/callback", exchange -> {
                try {
                    URI uri = exchange.getRequestURI();
                    var params = parseQuery(uri.getQuery());
                    String accessToken = params.getOrDefault("access_token", "");
                    String refreshToken = params.getOrDefault("refresh_token", "");

                    // Send response before processing
                    byte[] response = SUCCESS_HTML.getBytes();
                    exchange.getResponseHeaders().set("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.getResponseBody().close();

                    if (!accessToken.isEmpty() && !refreshToken.isEmpty()) {
                        onSuccess.accept(accessToken, refreshToken);
                    } else {
                        onError.accept("No tokens received from browser.");
                    }

                    // Shutdown after successful callback
                    stop();
                } catch (Exception e) {
                    VoxelMindMod.LOGGER.error("Callback error: {}", e.getMessage());
                    onError.accept("Callback error: " + e.getMessage());
                }
            });

            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "VoxelMind-OAuth");
                t.setDaemon(true);
                return t;
            }));
            server.start();

            // Auto-shutdown after 60s
            timeoutExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VoxelMind-OAuthTimeout");
                t.setDaemon(true);
                return t;
            });
            timeoutExecutor.schedule(() -> {
                if (server != null) {
                    onError.accept("Login timed out. Try again.");
                    stop();
                }
            }, 60, TimeUnit.SECONDS);

            VoxelMindMod.LOGGER.info("OAuth callback server started on port {}", port);
        } catch (IOException e) {
            VoxelMindMod.LOGGER.error("Failed to start OAuth server: {}", e.getMessage());
            onError.accept("Could not start login server on port " + port);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (timeoutExecutor != null) {
            timeoutExecutor.shutdownNow();
            timeoutExecutor = null;
        }
    }

    private java.util.Map<String, String> parseQuery(String query) {
        var map = new java.util.HashMap<String, String>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                map.put(pair.substring(0, eq), java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return map;
    }
}
