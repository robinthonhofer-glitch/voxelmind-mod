package com.voxelmind.mod.lan;

import com.voxelmind.mod.VoxelMindMod;
import com.voxelmind.mod.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.GameMode;

/**
 * Manages LAN state: detecting singleplayer, opening/closing LAN, tracking port.
 */
public class LanManager {
    private static final LanManager INSTANCE = new LanManager();
    private boolean lanOpen = false;
    private int activePort = 0;
    private String serverAddress = null;

    public static LanManager get() { return INSTANCE; }

    public boolean isLanOpen() { return lanOpen; }
    public int getActivePort() { return activePort; }

    /**
     * Returns the address that the Brain should use to connect to this server.
     * For singleplayer: public IP (UPnP) or LAN IP + port
     * For multiplayer: the server address from ServerInfo
     */
    public String getServerAddress() {
        return serverAddress;
    }

    public boolean isSingleplayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.isIntegratedServerRunning();
    }

    /**
     * Open LAN on the integrated server with the configured fixed port.
     */
    public boolean openLan() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isIntegratedServerRunning()) {
            VoxelMindMod.LOGGER.warn("Cannot open LAN — not in singleplayer.");
            return false;
        }
        if (lanOpen) {
            VoxelMindMod.LOGGER.info("LAN already open on port {}", activePort);
            return true;
        }

        var server = client.getServer();
        if (server == null) return false;

        // openToLan will be intercepted by our Mixin to use the fixed port
        boolean success = server.openToLan(GameMode.SURVIVAL, false, ModConfig.getLanPort());
        if (success) {
            VoxelMindMod.LOGGER.info("LAN opened on port {}", activePort);
        } else {
            VoxelMindMod.LOGGER.error("Failed to open LAN.");
        }
        return success;
    }

    /**
     * Called by Mixin when LAN is actually opened — sets state.
     */
    public void onLanOpened(int port) {
        this.activePort = port;
        this.lanOpen = true;

        // Try UPnP if configured
        if (ModConfig.isAutoUpnp()) {
            UpnpManager.tryOpenPort(port);
            String externalIp = UpnpManager.getExternalIP();
            if (externalIp != null) {
                this.serverAddress = externalIp + ":" + port;
                VoxelMindMod.LOGGER.info("UPnP: External address = {}", serverAddress);
            } else {
                // Fallback: LAN IP
                this.serverAddress = getLocalIp() + ":" + port;
                VoxelMindMod.LOGGER.info("UPnP not available. LAN address = {}", serverAddress);
            }
        } else {
            this.serverAddress = getLocalIp() + ":" + port;
        }
    }

    /**
     * Detect the server address for multiplayer.
     */
    public void detectMultiplayerAddress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getCurrentServerEntry() != null) {
            this.serverAddress = client.getCurrentServerEntry().address;
            this.lanOpen = false;
            VoxelMindMod.LOGGER.info("Multiplayer server: {}", serverAddress);
        }
    }

    /**
     * Cleanup on world leave.
     */
    public void onWorldLeave() {
        if (lanOpen && activePort > 0) {
            UpnpManager.closePort(activePort);
        }
        lanOpen = false;
        activePort = 0;
        serverAddress = null;
    }

    private String getLocalIp() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }
}
