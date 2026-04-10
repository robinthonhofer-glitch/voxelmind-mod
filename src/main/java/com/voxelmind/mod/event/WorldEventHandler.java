package com.voxelmind.mod.event;

import com.voxelmind.mod.VoxelMindMod;
import com.voxelmind.mod.config.ModConfig;
import com.voxelmind.mod.lan.LanManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Handles world join/leave events for auto-LAN and cleanup.
 */
public class WorldEventHandler {

    public static void register() {
        // On join — detect server type
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            LanManager lan = LanManager.get();
            if (lan.isSingleplayer()) {
                VoxelMindMod.LOGGER.info("Joined singleplayer world.");
                if (ModConfig.isAutoOpenLan() && !lan.isLanOpen()) {
                    // Auto-open LAN on next tick (server not ready yet during JOIN)
                    client.execute(() -> {
                        boolean success = lan.openLan();
                        if (success) {
                            VoxelMindMod.LOGGER.info("Auto-opened LAN on port {}", lan.getActivePort());
                        }
                    });
                }
            } else {
                lan.detectMultiplayerAddress();
            }
        });

        // On disconnect — cleanup tunnels
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LanManager.get().onWorldLeave();
            VoxelMindMod.LOGGER.info("World left — cleaned up LAN/tunnels.");
        });
    }
}
