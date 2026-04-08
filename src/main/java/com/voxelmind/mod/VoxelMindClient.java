package com.voxelmind.mod;

import com.voxelmind.mod.command.VmCommand;
import com.voxelmind.mod.event.WorldEventHandler;
import com.voxelmind.mod.gui.VoxelMindScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class VoxelMindClient implements ClientModInitializer {
    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        // Keybind: V to open VoxelMind GUI
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.voxelmind.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.voxelmind"
        ));

        // Tick handler for keybind
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new VoxelMindScreen(null));
                }
            }
        });

        // Register /vm commands
        VmCommand.register();

        // World event handlers (auto-LAN, cleanup)
        WorldEventHandler.register();

        VoxelMindMod.LOGGER.info("VoxelMind client initialized.");
    }
}
