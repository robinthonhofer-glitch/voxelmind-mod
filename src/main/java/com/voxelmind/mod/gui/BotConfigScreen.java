package com.voxelmind.mod.gui;

import com.voxelmind.mod.api.BrainApiClient;
import com.voxelmind.mod.api.dto.BotInfo;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Screen for creating or editing a bot.
 *
 * Layout (two columns side-by-side so it fits in a small window):
 *   ┌──────────────── Title ────────────────┐
 *   │  Name: [____________________]          │
 *   │                                        │
 *   │  Personality:       OCEAN Traits:      │
 *   │  > Stoic            [O] ▬▬▬▬▬ 40       │
 *   │    Anxious          [C] ▬▬▬▬▬ 80       │
 *   │    Curious          [E] ▬▬▬▬▬ 30       │
 *   │    ...              [A] ▬▬▬▬▬ 55       │
 *   │    Sarcastic        [N] ▬▬▬▬▬ 15       │
 *   │                                        │
 *   │      [Cancel]          [Create]        │
 *   └────────────────────────────────────────┘
 *
 * NOTE: OCEAN values are a UI-only preview for now. The backend only consumes
 * personality_id (the preset) — custom slider values are sent but currently ignored.
 */
public class BotConfigScreen extends Screen {
    private static final String[] PERSONALITIES = {
            "stoic", "anxious", "curious", "cheerful", "grumpy",
            "competitive", "gentle", "reckless", "methodical", "sarcastic"
    };
    private static final String[] PERSONALITY_LABELS = {
            "Stoic", "Anxious", "Curious", "Cheerful", "Grumpy",
            "Competitive", "Gentle", "Reckless", "Methodical", "Sarcastic"
    };
    private static final String[] PERSONALITY_BLURBS = {
            "Calm, measured, efficient",
            "Cautious, easily startled",
            "Explores and learns",
            "Optimistic and friendly",
            "Reluctant but reliable",
            "Driven to excel",
            "Peaceful and caring",
            "Fearless risk-taker",
            "Systematic planner",
            "Witty and dry",
    };

    /** Per-preset OCEAN defaults [O, C, E, A, N], 0-100. */
    private static final int[][] OCEAN_DEFAULTS = {
            { 40, 80, 30, 55, 15 }, // stoic
            { 50, 70, 35, 60, 90 }, // anxious
            { 95, 60, 70, 65, 40 }, // curious
            { 70, 60, 85, 85, 20 }, // cheerful
            { 30, 75, 40, 25, 55 }, // grumpy
            { 55, 80, 75, 35, 50 }, // competitive
            { 60, 65, 50, 95, 35 }, // gentle
            { 80, 20, 85, 45, 25 }, // reckless
            { 45, 95, 35, 55, 20 }, // methodical
            { 65, 60, 65, 30, 50 }, // sarcastic
    };

    private static final String[] OCEAN_LABELS = {
            "Openness", "Conscientiousness", "Extraversion", "Agreeableness", "Neuroticism"
    };

    // ── Layout constants (two columns) ─────────────────────────────────
    private static final int TITLE_Y = 12;
    private static final int NAME_Y = 32;
    private static final int COLUMNS_TOP = 68;
    private static final int COL_WIDTH = 150;
    private static final int COL_GAP = 20;
    /** Height of one row in the personality list. */
    private static final int PERSONALITY_ROW_H = 12;
    /** Height of one slider. */
    private static final int SLIDER_H = 18;
    /** Vertical gap between sliders. */
    private static final int SLIDER_GAP = 2;

    private final Screen parent;
    private final BotInfo existingBot; // null = create mode
    private TextFieldWidget nameField;
    private int selectedPersonality = 0;
    private String errorMsg = null;
    private boolean submitting = false;

    /** Current slider values, kept in sync with the widgets. */
    private final int[] oceanValues = new int[5];
    private final OceanSlider[] oceanSliders = new OceanSlider[5];

    // Cached geometry from init() so mouseClicked can hit-test correctly
    private int leftColX;
    private int rightColX;

    public BotConfigScreen(Screen parent, BotInfo existingBot) {
        super(Text.translatable(existingBot != null ? "gui.voxelmind.edit_bot" : "gui.voxelmind.create_bot"));
        this.parent = parent;
        this.existingBot = existingBot;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;

        // Two columns centered around centerX
        leftColX = centerX - COL_WIDTH - COL_GAP / 2;
        rightColX = centerX + COL_GAP / 2;

        // Name field — full width across both columns, so labels fit comfortably
        int nameFieldX = leftColX + 40;
        int nameFieldW = (rightColX + COL_WIDTH) - nameFieldX;
        nameField = new TextFieldWidget(textRenderer, nameFieldX, NAME_Y, nameFieldW, 20,
                Text.translatable("gui.voxelmind.bot_name"));
        nameField.setMaxLength(16);
        if (existingBot != null) {
            nameField.setText(existingBot.bot_name);
            for (int i = 0; i < PERSONALITIES.length; i++) {
                if (PERSONALITIES[i].equals(existingBot.personality_id)) {
                    selectedPersonality = i;
                    break;
                }
            }
        }
        addDrawableChild(nameField);

        // Seed OCEAN values from the currently selected preset
        applyPresetToValues(selectedPersonality);

        // OCEAN sliders in right column
        int sliderTop = COLUMNS_TOP + 14; // below "Personality Traits" label
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            int sy = sliderTop + i * (SLIDER_H + SLIDER_GAP);
            oceanSliders[i] = new OceanSlider(
                    rightColX, sy, COL_WIDTH, SLIDER_H,
                    OCEAN_LABELS[i], oceanValues[i],
                    value -> oceanValues[idx] = value);
            addDrawableChild(oceanSliders[i]);
        }

        // Cancel / Save buttons — below the taller of the two columns
        int personalityColBottom = COLUMNS_TOP + 14 + PERSONALITIES.length * PERSONALITY_ROW_H;
        int sliderColBottom = sliderTop + 5 * (SLIDER_H + SLIDER_GAP);
        int contentBottom = Math.max(personalityColBottom, sliderColBottom);

        // Clamp button row so it never goes off-screen on tiny windows
        int bottomY = Math.min(contentBottom + 12, this.height - 30);

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(centerX - 110, bottomY, 100, 20).build());

        String actionLabel = existingBot != null ? "Save" : "Create";
        addDrawableChild(ButtonWidget.builder(Text.literal(actionLabel), button -> submit())
                .dimensions(centerX + 10, bottomY, 100, 20).build());
    }

    private void applyPresetToValues(int idx) {
        int[] src = OCEAN_DEFAULTS[idx];
        for (int i = 0; i < 5; i++) oceanValues[i] = src[i];
        // Push to sliders if they already exist
        if (oceanSliders[0] != null) {
            for (int i = 0; i < 5; i++) oceanSliders[i].setIntValue(oceanValues[i]);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;

        // Title
        String titleText = existingBot != null ? "Edit Bot" : "Create Bot";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(titleText)
                .formatted(Formatting.GREEN, Formatting.BOLD), centerX, TITLE_Y, 0xFFFFFF);

        // Name label (left of the field)
        context.drawTextWithShadow(textRenderer, Text.literal("Name:").formatted(Formatting.GRAY),
                leftColX, NAME_Y + 6, 0xAAAAAA);

        // ── Left column: Personality list ──
        context.drawTextWithShadow(textRenderer, Text.literal("Personality:").formatted(Formatting.GRAY),
                leftColX, COLUMNS_TOP, 0xAAAAAA);

        int listTop = COLUMNS_TOP + 14;
        for (int i = 0; i < PERSONALITY_LABELS.length; i++) {
            int ry = listTop + i * PERSONALITY_ROW_H;
            boolean selected = i == selectedPersonality;
            boolean hovered = mouseX >= leftColX && mouseX < leftColX + COL_WIDTH
                    && mouseY >= ry && mouseY < ry + PERSONALITY_ROW_H;

            int bg = selected ? 0x4055FF55 : (hovered ? 0x30FFFFFF : 0x00000000);
            if (bg != 0) {
                context.fill(leftColX, ry, leftColX + COL_WIDTH, ry + PERSONALITY_ROW_H, bg);
            }

            Formatting color = selected ? Formatting.GREEN : (hovered ? Formatting.WHITE : Formatting.GRAY);
            String prefix = selected ? "> " : "  ";
            context.drawTextWithShadow(textRenderer,
                    Text.literal(prefix + PERSONALITY_LABELS[i]).formatted(color),
                    leftColX + 2, ry + 2, 0xFFFFFF);
        }

        // Blurb for the currently selected personality, right below the list
        int blurbY = listTop + PERSONALITIES.length * PERSONALITY_ROW_H + 4;
        context.drawTextWithShadow(textRenderer,
                Text.literal(PERSONALITY_BLURBS[selectedPersonality]).formatted(Formatting.DARK_GRAY),
                leftColX, blurbY, 0x888888);

        // ── Right column: OCEAN label (sliders render themselves as widgets) ──
        context.drawTextWithShadow(textRenderer, Text.literal("Personality Traits").formatted(Formatting.GRAY),
                rightColX, COLUMNS_TOP, 0xAAAAAA);

        // Subtle note about custom tuning
        String note = "(preset-driven)";
        context.drawTextWithShadow(textRenderer,
                Text.literal(note).formatted(Formatting.DARK_GRAY),
                rightColX + COL_WIDTH - textRenderer.getWidth(note), COLUMNS_TOP, 0x666666);

        // Error message — just above the button row (if any)
        if (errorMsg != null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(errorMsg).formatted(Formatting.RED),
                    centerX, this.height - 50, 0xFF5555);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Personality list selection (left column)
        int listTop = COLUMNS_TOP + 14;
        for (int i = 0; i < PERSONALITY_LABELS.length; i++) {
            int ry = listTop + i * PERSONALITY_ROW_H;
            if (mouseX >= leftColX && mouseX < leftColX + COL_WIDTH
                    && mouseY >= ry && mouseY < ry + PERSONALITY_ROW_H) {
                if (selectedPersonality != i) {
                    selectedPersonality = i;
                    applyPresetToValues(i);
                }
                return true;
            }
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
            BrainApiClient.get().updateBot(existingBot.id, name, personality)
                    .thenRun(() -> client.execute(() -> client.setScreen(new VoxelMindScreen(null))))
                    .exceptionally(e -> {
                        errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                        submitting = false;
                        return null;
                    });
        } else {
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

    /**
     * Simple 0-100 slider with a label prefix ("Openness: 75").
     */
    private static class OceanSlider extends SliderWidget {
        private final String label;
        private final java.util.function.IntConsumer onChange;

        OceanSlider(int x, int y, int width, int height, String label,
                    int initialValue, java.util.function.IntConsumer onChange) {
            super(x, y, width, height, Text.literal(label + ": " + initialValue), initialValue / 100.0);
            this.label = label;
            this.onChange = onChange;
            updateMessage();
        }

        void setIntValue(int v) {
            this.value = Math.max(0, Math.min(100, v)) / 100.0;
            updateMessage();
            onChange.accept(v);
        }

        @Override
        protected void updateMessage() {
            int v = (int) Math.round(this.value * 100);
            setMessage(Text.literal(label + ": " + v));
        }

        @Override
        protected void applyValue() {
            int v = (int) Math.round(this.value * 100);
            onChange.accept(v);
        }
    }
}
