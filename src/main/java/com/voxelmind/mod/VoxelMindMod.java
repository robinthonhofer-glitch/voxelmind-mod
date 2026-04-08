package com.voxelmind.mod;

import com.voxelmind.mod.config.ModConfig;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VoxelMindMod implements ModInitializer {
    public static final String MOD_ID = "voxelmind";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("VoxelMind Companion loading...");
        ModConfig.load();
        LOGGER.info("VoxelMind Companion ready. Use /vm or press V to open.");
    }
}
