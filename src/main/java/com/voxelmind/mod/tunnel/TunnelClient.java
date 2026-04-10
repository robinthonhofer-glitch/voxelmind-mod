package com.voxelmind.mod.tunnel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.voxelmind.mod.VoxelMindMod;
import com.voxelmind.mod.config.ModConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WSS client that connects to the VoxelMind Relay on Hetzner.
 * Opens a tunnel so the remote Mineflayer agent can reach the local MC server.
 *
 * Supports multiple simultaneous TCP connections via channel multiplexing.
 * Each binary frame is prefixed with a 1-byte channelId.
 */
public class TunnelClient {
    private static final Gson GSON = new Gson();
    private static final int RECONNECT_DELAY_MS = 5000;

    private final String botId;
    private final int localPort;

    private volatile TunnelStatus status = TunnelStatus.DISCONNECTED;
    private volatile String relayHost = null;
    private volatile int tunnelPort = 0;
    private volatile String errorMessage = null;

    private WebSocket webSocket;
    private final Map<Integer, TunnelBridge> bridges = new ConcurrentHashMap<>();
    private volatile boolean shouldReconnect = false;
    private final AtomicReference<StringBuilder> textBuffer = new AtomicReference<>(new StringBuilder());
    private final AtomicReference<ByteBuffer> binaryBuffer = new AtomicReference<>(null);

    public TunnelClient(String botId, int localPort) {
        this.botId = botId;
        this.localPort = localPort;
    }

    public TunnelStatus getStatus() { return status; }
    public String getRelayHost() { return relayHost; }
    public int getTunnelPort() { return tunnelPort; }
    public String getErrorMessage() { return errorMessage; }
    public String getBotId() { return botId; }

    public CompletableFuture<TunnelClient> connect() {
        CompletableFuture<TunnelClient> ready = new CompletableFuture<>();
        shouldReconnect = true;
        status = TunnelStatus.CONNECTING;
        errorMessage = null;

        String relayUrl = ModConfig.getRelayUrl();
        String token = ModConfig.getAccessToken();

        if (token.isEmpty()) {
            status = TunnelStatus.ERROR;
            errorMessage = "Not logged in";
            ready.completeExceptionally(new RuntimeException(errorMessage));
            return ready;
        }

        HttpClient httpClient = HttpClient.newHttpClient();
        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(relayUrl), new WebSocket.Listener() {

                    @Override
                    public void onOpen(WebSocket ws) {
                        VoxelMindMod.LOGGER.info("TunnelClient [{}]: WSS connected to {}", botId, relayUrl);
                        webSocket = ws;
                        status = TunnelStatus.AUTHENTICATING;

                        JsonObject auth = new JsonObject();
                        auth.addProperty("type", "auth");
                        auth.addProperty("token", token);
                        ws.sendText(GSON.toJson(auth), true);
                        ws.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        StringBuilder buf = textBuffer.get();
                        buf.append(data);

                        if (last) {
                            String message = buf.toString();
                            buf.setLength(0);
                            handleJsonMessage(message, ready);
                        }

                        ws.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
                        if (last && binaryBuffer.get() == null) {
                            // Single frame — route directly
                            routeBinaryFrame(data);
                        } else {
                            // Multi-frame — accumulate
                            ByteBuffer existing = binaryBuffer.get();
                            if (existing == null) {
                                byte[] copy = new byte[data.remaining()];
                                data.get(copy);
                                binaryBuffer.set(ByteBuffer.wrap(copy));
                            } else {
                                ByteBuffer combined = ByteBuffer.allocate(existing.remaining() + data.remaining());
                                combined.put(existing);
                                combined.put(data);
                                combined.flip();
                                binaryBuffer.set(combined);
                            }
                            if (last) {
                                ByteBuffer full = binaryBuffer.getAndSet(null);
                                if (full != null) {
                                    routeBinaryFrame(full);
                                }
                            }
                        }

                        ws.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                        VoxelMindMod.LOGGER.info("TunnelClient [{}]: WSS closed (code={}, reason={})", botId, statusCode, reason);
                        handleDisconnect(ready);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        VoxelMindMod.LOGGER.error("TunnelClient [{}]: WSS error: {}", botId, error.getMessage());
                        handleDisconnect(ready);
                    }
                });

        return ready;
    }

    /**
     * Route a binary frame: first byte = channelId, rest = TCP data.
     *
     * If the bridge for this channel exists but is already dead (e.g. the
     * local MC server disconnected), we evict it from the map and tell the
     * relay to tear down the agent-side connection. Otherwise the agent
     * keeps sending keepalive packets into a zombie bridge forever.
     */
    private void routeBinaryFrame(ByteBuffer frame) {
        if (frame.remaining() < 2) return;

        int channelId = frame.get() & 0xFF;
        TunnelBridge b = bridges.get(channelId);
        if (b != null && b.isConnected()) {
            b.writeToTcp(frame.slice());
            return;
        }

        // Bridge missing or dead: drop once, then tell the relay to give up on this channel
        if (b != null) {
            bridges.remove(channelId, b);
            VoxelMindMod.LOGGER.warn("TunnelClient [{}]: Bridge for channel {} is dead, requesting relay close ({} bytes dropped)",
                    botId, channelId, frame.remaining());
        } else {
            VoxelMindMod.LOGGER.warn("TunnelClient [{}]: No bridge for channel {} — dropping {} bytes", botId, channelId, frame.remaining());
        }
        sendCloseChannel(channelId);
    }

    /**
     * Tell the relay to destroy the agent-side TCP socket for this channel.
     * Fire-and-forget.
     */
    private void sendCloseChannel(int channelId) {
        if (webSocket == null) return;
        try {
            JsonObject msg = new JsonObject();
            msg.addProperty("type", "close_channel");
            msg.addProperty("botId", botId);
            msg.addProperty("channelId", channelId);
            webSocket.sendText(GSON.toJson(msg), true);
        } catch (Exception e) {
            VoxelMindMod.LOGGER.debug("TunnelClient [{}]: Failed to send close_channel for channel {}: {}",
                    botId, channelId, e.getMessage());
        }
    }

    private void handleJsonMessage(String json, CompletableFuture<TunnelClient> ready) {
        try {
            JsonObject msg = GSON.fromJson(json, JsonObject.class);
            String type = msg.has("type") ? msg.get("type").getAsString() : "";

            switch (type) {
                case "auth_ok" -> {
                    VoxelMindMod.LOGGER.info("TunnelClient [{}]: Authenticated, requesting tunnel", botId);
                    JsonObject req = new JsonObject();
                    req.addProperty("type", "request_tunnel");
                    req.addProperty("botId", botId);
                    webSocket.sendText(GSON.toJson(req), true);
                }
                case "auth_error" -> {
                    String message = msg.has("message") ? msg.get("message").getAsString() : "Auth failed";
                    VoxelMindMod.LOGGER.error("TunnelClient [{}]: Auth error: {}", botId, message);
                    status = TunnelStatus.ERROR;
                    errorMessage = message;
                    shouldReconnect = false;
                    ready.completeExceptionally(new RuntimeException("Tunnel auth failed: " + message));
                }
                case "tunnel_ready" -> {
                    tunnelPort = msg.get("tcpPort").getAsInt();
                    relayHost = msg.get("relayHost").getAsString();
                    VoxelMindMod.LOGGER.info("TunnelClient [{}]: Tunnel ready at {}:{}", botId, relayHost, tunnelPort);
                    status = TunnelStatus.READY;
                    ready.complete(this);
                }
                case "tunnel_error" -> {
                    String message = msg.has("message") ? msg.get("message").getAsString() : "Tunnel failed";
                    VoxelMindMod.LOGGER.error("TunnelClient [{}]: Tunnel error: {}", botId, message);
                    status = TunnelStatus.ERROR;
                    errorMessage = message;
                    ready.completeExceptionally(new RuntimeException("Tunnel error: " + message));
                }
                case "tcp_connected" -> {
                    int channelId = msg.has("channelId") ? msg.get("channelId").getAsInt() : 0;
                    VoxelMindMod.LOGGER.info("TunnelClient [{}]: Agent TCP connected (channel {})", botId, channelId);

                    // New bridge for this channel — each TCP connection = fresh MC session
                    TunnelBridge b = new TunnelBridge("localhost", localPort);
                    final int ch = channelId;

                    // When the local TCP side dies (MC kick, connection reset, etc.),
                    // remove the bridge from our routing map AND tell the relay to
                    // tear down the agent-side connection — otherwise the agent
                    // spams keepalives into a zombie bridge.
                    b.setOnClose(() -> {
                        bridges.remove(ch, b);
                        sendCloseChannel(ch);
                    });

                    try {
                        b.connectSync(data -> {
                            if (webSocket != null) {
                                // Prepend channelId byte
                                ByteBuffer frame = ByteBuffer.allocate(1 + data.remaining());
                                frame.put((byte) ch);
                                frame.put(data);
                                frame.flip();
                                webSocket.sendBinary(frame, true);
                            }
                        });
                        bridges.put(channelId, b);
                    } catch (IOException e) {
                        VoxelMindMod.LOGGER.error("TunnelClient [{}]: Bridge connect failed for channel {}: {}", botId, channelId, e.getMessage());
                        // Tell relay we can't serve this channel so the agent-side socket gets closed
                        sendCloseChannel(channelId);
                    }
                }
                case "tcp_disconnected" -> {
                    int channelId = msg.has("channelId") ? msg.get("channelId").getAsInt() : 0;
                    VoxelMindMod.LOGGER.info("TunnelClient [{}]: Agent TCP disconnected (channel {})", botId, channelId);
                    TunnelBridge b = bridges.remove(channelId);
                    if (b != null) b.close();
                }
                default -> VoxelMindMod.LOGGER.warn("TunnelClient [{}]: Unknown message type: {}", botId, type);
            }
        } catch (Exception e) {
            VoxelMindMod.LOGGER.error("TunnelClient [{}]: Failed to parse relay message: {}", botId, e.getMessage());
        }
    }

    private void stopAllBridges() {
        for (TunnelBridge b : bridges.values()) {
            b.close();
        }
        bridges.clear();
    }

    private void handleDisconnect(CompletableFuture<TunnelClient> ready) {
        stopAllBridges();
        tunnelPort = 0;

        if (shouldReconnect) {
            status = TunnelStatus.CONNECTING;
            VoxelMindMod.LOGGER.info("TunnelClient [{}]: Reconnecting in {}ms...", botId, RECONNECT_DELAY_MS);
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException ignored) {}
                if (shouldReconnect) {
                    connect();
                }
            });
        } else {
            status = TunnelStatus.DISCONNECTED;
        }

        ready.completeExceptionally(new RuntimeException("Tunnel disconnected"));
    }

    public void disconnect() {
        shouldReconnect = false;
        status = TunnelStatus.DISCONNECTED;
        stopAllBridges();
        if (webSocket != null) {
            try {
                JsonObject closeMsg = new JsonObject();
                closeMsg.addProperty("type", "close_tunnel");
                closeMsg.addProperty("botId", botId);
                webSocket.sendText(GSON.toJson(closeMsg), true);
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnect");
            } catch (Exception ignored) {}
            webSocket = null;
        }
        VoxelMindMod.LOGGER.info("TunnelClient [{}]: Disconnected", botId);
    }
}
