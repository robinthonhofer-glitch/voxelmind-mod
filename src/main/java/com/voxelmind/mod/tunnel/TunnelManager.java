package com.voxelmind.mod.tunnel;

import com.voxelmind.mod.VoxelMindMod;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages tunnel clients for all bots.
 * Each bot gets its own TunnelClient (own WSS connection).
 */
public class TunnelManager {
    private static final TunnelManager INSTANCE = new TunnelManager();

    private final Map<String, TunnelClient> tunnels = new ConcurrentHashMap<>();

    public static TunnelManager get() { return INSTANCE; }

    /**
     * Open a tunnel for a bot. Returns future that completes when tunnel is ready.
     */
    public CompletableFuture<TunnelClient> openTunnel(String botId, int localPort) {
        // Close existing tunnel for this bot if any
        closeTunnel(botId);

        TunnelClient client = new TunnelClient(botId, localPort);
        tunnels.put(botId, client);

        return client.connect().whenComplete((result, error) -> {
            if (error != null) {
                VoxelMindMod.LOGGER.error("TunnelManager: Failed to open tunnel for {}: {}", botId, error.getMessage());
                tunnels.remove(botId);
            }
        });
    }

    /**
     * Close a specific bot's tunnel.
     */
    public void closeTunnel(String botId) {
        TunnelClient client = tunnels.remove(botId);
        if (client != null) {
            client.disconnect();
        }
    }

    /**
     * Close all tunnels (e.g. on world leave).
     */
    public void closeAll() {
        for (TunnelClient client : tunnels.values()) {
            client.disconnect();
        }
        tunnels.clear();
        VoxelMindMod.LOGGER.info("TunnelManager: All tunnels closed");
    }

    /**
     * Get the tunnel for a bot (may be null if no tunnel exists).
     */
    public TunnelClient getTunnel(String botId) {
        return tunnels.get(botId);
    }

    /**
     * Check if any tunnel is currently active.
     */
    public boolean hasActiveTunnels() {
        return tunnels.values().stream().anyMatch(t -> t.getStatus() == TunnelStatus.READY);
    }

    /**
     * Get overall tunnel status for display.
     * Returns the "worst" status of all tunnels.
     */
    public TunnelStatus getOverallStatus() {
        if (tunnels.isEmpty()) return TunnelStatus.DISCONNECTED;
        boolean hasError = false;
        boolean hasConnecting = false;
        boolean hasReady = false;
        for (TunnelClient client : tunnels.values()) {
            switch (client.getStatus()) {
                case ERROR -> hasError = true;
                case CONNECTING, AUTHENTICATING -> hasConnecting = true;
                case READY -> hasReady = true;
            }
        }
        if (hasError) return TunnelStatus.ERROR;
        if (hasConnecting) return TunnelStatus.CONNECTING;
        if (hasReady) return TunnelStatus.READY;
        return TunnelStatus.DISCONNECTED;
    }
}
