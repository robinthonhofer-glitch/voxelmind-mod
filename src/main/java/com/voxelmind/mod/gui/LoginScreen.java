package com.voxelmind.mod.gui;

import com.voxelmind.mod.auth.AuthManager;
import com.voxelmind.mod.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Login prompt screen — shown when user is not authenticated.
 */
public class LoginScreen extends Screen {
    private final Screen parent;

    public LoginScreen(Screen parent) {
        super(Text.translatable("gui.voxelmind.login"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        AuthManager auth = AuthManager.get();

        if (auth.getState() == AuthManager.State.LOGGING_IN) {
            // Cancel button while waiting
            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
                auth.cancelLogin();
                close();
            }).dimensions(centerX - 50, centerY + 20, 100, 20).build());
        } else {
            // Login button
            addDrawableChild(ButtonWidget.builder(Text.literal("Login with Browser"), button -> {
                auth.startLogin();
                client.setScreen(new LoginScreen(parent)); // Refresh to show waiting state
            }).dimensions(centerX - 80, centerY, 160, 20).build());

            // Back button
            addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
                close();
            }).dimensions(centerX - 50, centerY + 30, 100, 20).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Title
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("VoxelMind").formatted(Formatting.GREEN, Formatting.BOLD),
                centerX, centerY - 60, 0xFFFFFF);

        AuthManager auth = AuthManager.get();
        AuthManager.State state = auth.getState();

        if (state == AuthManager.State.LOGGING_IN) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Waiting for browser login...").formatted(Formatting.YELLOW),
                    centerX, centerY - 20, 0xFFFF55);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Check your browser.").formatted(Formatting.GRAY),
                    centerX, centerY - 6, 0xAAAAAA);
        } else if (state == AuthManager.State.ERROR) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(auth.getErrorMessage()).formatted(Formatting.RED),
                    centerX, centerY - 20, 0xFF5555);
        } else if (state == AuthManager.State.LOGGED_IN) {
            // Redirect to main screen
            client.execute(() -> client.setScreen(new VoxelMindScreen(parent)));
        } else {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Sign in to summon AI companions.").formatted(Formatting.GRAY),
                    centerX, centerY - 20, 0xAAAAAA);
        }
    }

    @Override
    public void tick() {
        super.tick();
        // Check if login completed while on this screen
        if (ModConfig.isLoggedIn() && AuthManager.get().getState() == AuthManager.State.LOGGED_IN) {
            client.execute(() -> client.setScreen(new VoxelMindScreen(parent)));
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
