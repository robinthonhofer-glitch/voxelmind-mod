package com.voxelmind.mod;

import com.voxelmind.mod.command.VmCommand;
import com.voxelmind.mod.config.ModConfig;
import com.voxelmind.mod.event.WorldEventHandler;
import com.voxelmind.mod.gui.VoxelMindScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class VoxelMindClient implements ClientModInitializer {
    private static KeyBinding openGuiKey;
    private static KeyBinding toggleDevKey;

    private static final String PROD_BRAIN_URL = "https://brain.voxel-mind.com";
    private static final String DEV_BRAIN_URL = "https://brain-dev.voxel-mind.com";

    @Override
    public void onInitializeClient() {
        // Keybind: V to open VoxelMind GUI
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.voxelmind.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.voxelmind"
        ));

        // Keybind: F8 to toggle Dev/Prod brain
        toggleDevKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.voxelmind.toggle_dev",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "category.voxelmind"
        ));

        // Tick handler for keybinds
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new VoxelMindScreen(null));
                }
            }

            while (toggleDevKey.wasPressed()) {
                String current = ModConfig.getBrainUrl();
                boolean switchToDev = current.equals(PROD_BRAIN_URL);
                String newUrl = switchToDev ? DEV_BRAIN_URL : PROD_BRAIN_URL;
                ModConfig.setBrainUrl(newUrl);

                if (client.player != null) {
                    String label = switchToDev ? "DEV" : "PROD";
                    Formatting color = switchToDev ? Formatting.YELLOW : Formatting.GREEN;
                    client.player.sendMessage(
                            Text.literal("VoxelMind: Switched to " + label + " (" + newUrl + ")")
                                    .formatted(color),
                            true // actionbar
                    );
                }
                VoxelMindMod.LOGGER.info("Brain URL toggled to: {}", newUrl);
            }
        });

        // Register /vm commands
        VmCommand.register();

        // World event handlers (auto-LAN, cleanup)
        WorldEventHandler.register();

        VoxelMindMod.LOGGER.info("VoxelMind client initialized.");
    }
}
