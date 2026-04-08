package com.voxelmind.mod.mixin;

import com.voxelmind.mod.config.ModConfig;
import com.voxelmind.mod.lan.LanManager;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Overrides the LAN port to use the configured fixed port instead of a random one.
 * MC's openToLan picks a random port — this mixin replaces it with ModConfig.getLanPort().
 */
@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {

    @ModifyVariable(
            method = "openToLan",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private int voxelmind$fixLanPort(int port) {
        int fixedPort = ModConfig.getLanPort();
        LanManager.get().onLanOpened(fixedPort);
        return fixedPort;
    }
}
