package com.voxelmind.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.voxelmind.mod.VoxelMindMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent mod configuration stored in .minecraft/config/voxelmind.json
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("voxelmind.json");

    private static ModConfig instance = new ModConfig();

    // Config fields
    private String brainUrl = "https://brain-dev.voxel-mind.com";
    private String accessToken = "";
    private String refreshToken = "";
    private int lanPort = 25565;
    private boolean autoOpenLan = false;
    private boolean autoUpnp = true;

    // Getters
    public static String getBrainUrl() { return instance.brainUrl; }
    public static String getAccessToken() { return instance.accessToken; }
    public static String getRefreshToken() { return instance.refreshToken; }
    public static int getLanPort() { return instance.lanPort; }
    public static boolean isAutoOpenLan() { return instance.autoOpenLan; }
    public static boolean isAutoUpnp() { return instance.autoUpnp; }

    // Setters (auto-save)
    public static void setAccessToken(String token) {
        instance.accessToken = token;
        save();
    }

    public static void setRefreshToken(String token) {
        instance.refreshToken = token;
        save();
    }

    public static void setTokens(String access, String refresh) {
        instance.accessToken = access;
        instance.refreshToken = refresh;
        save();
    }

    public static void setBrainUrl(String url) {
        instance.brainUrl = url;
        save();
    }

    public static void setLanPort(int port) {
        instance.lanPort = port;
        save();
    }

    public static void setAutoOpenLan(boolean auto) {
        instance.autoOpenLan = auto;
        save();
    }

    public static void setAutoUpnp(boolean auto) {
        instance.autoUpnp = auto;
        save();
    }

    public static void clearTokens() {
        instance.accessToken = "";
        instance.refreshToken = "";
        save();
    }

    public static boolean isLoggedIn() {
        return !instance.accessToken.isEmpty();
    }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                instance = GSON.fromJson(json, ModConfig.class);
                if (instance == null) instance = new ModConfig();
                VoxelMindMod.LOGGER.info("Config loaded from {}", CONFIG_PATH);
            } else {
                save(); // Create default config
                VoxelMindMod.LOGGER.info("Created default config at {}", CONFIG_PATH);
            }
        } catch (IOException e) {
            VoxelMindMod.LOGGER.error("Failed to load config: {}", e.getMessage());
            instance = new ModConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(instance));
        } catch (IOException e) {
            VoxelMindMod.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }
}
