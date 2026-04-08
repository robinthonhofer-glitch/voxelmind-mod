package com.voxelmind.mod.gui;

import com.voxelmind.mod.api.BrainApiClient;
import com.voxelmind.mod.api.dto.BotInfo;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Screen for creating or editing a bot.
 */
public class BotConfigScreen extends Screen {
    private static final String[] PERSONALITIES = {
            "stoic", "anxious", "curious", "cheerful", "grumpy",
            "competitive", "gentle", "reckless", "methodical", "sarcastic"
    };
    private static final String[] PERSONALITY_LABELS = {
            "Stoic — Calm and rational",
            "Anxious — Cautious, easily startled",
            "Curious — Loves exploring and learning",
            "Cheerful — Optimistic and friendly",
            "Grumpy — Reluctant but reliable",
            "Competitive — Driven to excel",
            "Gentle — Peaceful and caring",
            "Reckless — Fearless risk-taker",
            "Methodical — Systematic planner",
            "Sarcastic — Witty and dry"
    };

    private final Screen parent;
    private final BotInfo existingBot; // null = create mode
    private TextFieldWidget nameField;
    private int selectedPersonality = 0;
    private String errorMsg = null;
    private boolean submitting = false;

    public BotConfigScreen(Screen parent, BotInfo existingBot) {
        super(Text.translatable(existingBot != null ? "gui.voxelmind.edit_bot" : "gui.voxelmind.create_bot"));
        this.parent = parent;
        this.existingBot = existingBot;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int y = 50;

        // Name field
        nameField = new TextFieldWidget(textRenderer, centerX - 100, y, 200, 20,
                Text.translatable("gui.voxelmind.bot_name"));
        nameField.setMaxLength(16);
        if (existingBot != null) {
            nameField.setText(existingBot.bot_name);
            // Pre-select existing personality
            for (int i = 0; i < PERSONALITIES.length; i++) {
                if (PERSONALITIES[i].equals(existingBot.personality_id)) {
                    selectedPersonality = i;
                    break;
                }
            }
        }
        addDrawableChild(nameField);

        // Cancel button
        int bottomY = this.height - 30;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> {
            close();
        }).dimensions(centerX - 110, bottomY, 100, 20).build());

        // Create/Save button
        String actionLabel = existingBot != null ? "Save" : "Create";
        addDrawableChild(ButtonWidget.builder(Text.literal(actionLabel), button -> {
            submit();
        }).dimensions(centerX + 10, bottomY, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int y = 20;

        // Title
        String titleText = existingBot != null ? "Edit Bot" : "Create Bot";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(titleText)
                .formatted(Formatting.GREEN, Formatting.BOLD), centerX, y, 0xFFFFFF);

        // Name label
        context.drawTextWithShadow(textRenderer, Text.literal("Name:").formatted(Formatting.GRAY),
                centerX - 100, 40, 0xAAAAAA);

        // Personality selector
        y = 80;
        context.drawTextWithShadow(textRenderer, Text.literal("Personality:").formatted(Formatting.GRAY),
                centerX - 100, y, 0xAAAAAA);
        y += 14;

        for (int i = 0; i < PERSONALITY_LABELS.length; i++) {
            boolean selected = i == selectedPersonality;
            boolean hovered = mouseX >= centerX - 100 && mouseX <= centerX + 100
                    && mouseY >= y && mouseY < y + 12;

            Formatting color = selected ? Formatting.GREEN : (hovered ? Formatting.WHITE : Formatting.GRAY);
            String prefix = selected ? "> " : "  ";

            context.drawTextWithShadow(textRenderer,
                    Text.literal(prefix + PERSONALITY_LABELS[i]).formatted(color),
                    centerX - 100, y, 0xFFFFFF);
            y += 13;
        }

        // Error message
        if (errorMsg != null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(errorMsg).formatted(Formatting.RED),
                    centerX, this.height - 50, 0xFF5555);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Personality selection
        int centerX = this.width / 2;
        int y = 94;
        for (int i = 0; i < PERSONALITY_LABELS.length; i++) {
            if (mouseX >= centerX - 100 && mouseX <= centerX + 100
                    && mouseY >= y && mouseY < y + 13) {
                selectedPersonality = i;
                return true;
            }
            y += 13;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void submit() {
        if (submitting) return;
        String name = nameField.getText().trim();
        if (name.isEmpty() || name.length() > 16) {
            errorMsg = "Name must be 1-16 characters.";
            return;
        }
        if (!name.matches("[a-zA-Z0-9_]+")) {
            errorMsg = "Name: only letters, numbers, and underscores.";
            return;
        }

        submitting = true;
        errorMsg = null;
        String personality = PERSONALITIES[selectedPersonality];

        if (existingBot != null) {
            // Update
            BrainApiClient.get().updateBot(existingBot.id, name, personality)
                    .thenRun(() -> client.execute(() -> client.setScreen(new VoxelMindScreen(null))))
                    .exceptionally(e -> {
                        errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                        submitting = false;
                        return null;
                    });
        } else {
            // Create — set owner_player_name to current player
            String ownerName = client.getSession().getUsername();
            BrainApiClient.get().createBot(name, personality, ownerName)
                    .thenRun(() -> client.execute(() -> client.setScreen(new VoxelMindScreen(null))))
                    .exceptionally(e -> {
                        errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                        submitting = false;
                        return null;
                    });
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
