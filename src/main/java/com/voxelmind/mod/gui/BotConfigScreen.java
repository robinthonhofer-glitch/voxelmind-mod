package com.voxelmind.mod.gui;

import com.voxelmind.mod.api.BrainApiClient;
import com.voxelmind.mod.api.dto.BotInfo;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
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

    /** Ordered chat mode values. Index cycles on button click. */
    private static final String[] CHAT_MODES = { "public", "whisper", "mixed" };
    private static final String[] CHAT_MODE_LABELS = { "Public Chat", "Whisper only", "Both (adv.)" };

    private final Screen parent;
    private final BotInfo existingBot; // null = create mode
    private TextFieldWidget nameField;
    private int selectedPersonality = 0;
    private int selectedChatMode = 0; // index into CHAT_MODES
    // PVP toggles (NEW-1). Both default to false; attackOwner can only be true while pvp is true.
    private boolean allowPvp = false;
    private boolean allowAttackOwner = false;
    private ButtonWidget pvpButton;
    private ButtonWidget attackOwnerButton;
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
        // Suggestion shows in light grey while the field is empty so first-time
        // users don't ship a bot called "Player" or leave it blank. Picks a
        // rotating example from a small list keyed by the day of the year so
        // we don't always nudge towards the same name.
        nameField.setSuggestion(Text.translatable("gui.voxelmind.bot_name.suggestion").getString());
        if (existingBot != null) {
            nameField.setText(existingBot.bot_name);
            for (int i = 0; i < PERSONALITIES.length; i++) {
                if (PERSONALITIES[i].equals(existingBot.personality_id)) {
                    selectedPersonality = i;
                    break;
                }
            }
            // Restore chat mode from existing bot
            String existingChatMode = existingBot.getChatMode();
            for (int i = 0; i < CHAT_MODES.length; i++) {
                if (CHAT_MODES[i].equals(existingChatMode)) {
                    selectedChatMode = i;
                    break;
                }
            }
            // Restore PVP toggles
            allowPvp = existingBot.allow_pvp;
            allowAttackOwner = existingBot.allow_attack_owner && allowPvp;
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

        // Chat mode cycling button — small, in right column directly under the OCEAN sliders.
        int personalityListBottom = COLUMNS_TOP + 14 + PERSONALITIES.length * PERSONALITY_ROW_H;
        int blurbBottom = personalityListBottom + 4 + 10; // left column ends here (personality list + blurb)
        int sliderColBottom = sliderTop + 5 * (SLIDER_H + SLIDER_GAP);

        int chatModeY = sliderColBottom + 6;
        int chatModeH = 16;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Chat: " + CHAT_MODE_LABELS[selectedChatMode]),
                button -> {
                    selectedChatMode = (selectedChatMode + 1) % CHAT_MODES.length;
                    button.setMessage(Text.literal("Chat: " + CHAT_MODE_LABELS[selectedChatMode]));
                })
                .dimensions(rightColX, chatModeY, COL_WIDTH, chatModeH).build());

        // PVP toggle row — clicking flips allowPvp. When PVP turns off we force
        // attackOwner off too so the UI matches the server-side guard.
        int pvpY = chatModeY + chatModeH + 2;
        pvpButton = ButtonWidget.builder(
                Text.literal(pvpLabel()),
                button -> {
                    allowPvp = !allowPvp;
                    if (!allowPvp) allowAttackOwner = false;
                    refreshPvpButtons();
                })
                .dimensions(rightColX, pvpY, COL_WIDTH, chatModeH).build();
        addDrawableChild(pvpButton);

        // Attack-owner toggle. Disabled when PVP is off (no point). Going from
        // OFF→ON pops a vanilla ConfirmScreen so a misclick can't end with the
        // bot beating the player to death on the next aggro.
        int attackOwnerY = pvpY + chatModeH + 2;
        attackOwnerButton = ButtonWidget.builder(
                Text.literal(attackOwnerLabel()),
                button -> {
                    if (!allowPvp) return; // disabled state — no-op
                    if (allowAttackOwner) {
                        allowAttackOwner = false;
                        refreshPvpButtons();
                    } else {
                        client.setScreen(new ConfirmScreen(
                                confirmed -> {
                                    if (confirmed) {
                                        allowAttackOwner = true;
                                    }
                                    refreshPvpButtons();
                                    client.setScreen(this);
                                },
                                Text.literal("Allow your bot to attack you?"),
                                Text.literal("With this on, your bot may kill you. Are you sure?")));
                    }
                })
                .dimensions(rightColX, attackOwnerY, COL_WIDTH, chatModeH).build();
        attackOwnerButton.active = allowPvp;
        addDrawableChild(attackOwnerButton);

        // Cancel/Save buttons — below whichever column ends lower (left blurb vs right toggle stack)
        int contentBottom = Math.max(blurbBottom, attackOwnerY + chatModeH);
        int bottomY = Math.min(contentBottom + 12, this.height - 30);

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> close())
                .dimensions(centerX - 110, bottomY, 100, 20).build());

        String actionLabel = existingBot != null ? "Save" : "Create";
        addDrawableChild(ButtonWidget.builder(Text.literal(actionLabel), button -> submit())
                .dimensions(centerX + 10, bottomY, 100, 20).build());
    }

    private String pvpLabel() {
        return "PVP (attack players): " + (allowPvp ? "ON" : "OFF");
    }

    private String attackOwnerLabel() {
        if (!allowPvp) return "Attack owner: --";
        return "Attack owner: " + (allowAttackOwner ? "ON" : "OFF");
    }

    private void refreshPvpButtons() {
        if (pvpButton != null) pvpButton.setMessage(Text.literal(pvpLabel()));
        if (attackOwnerButton != null) {
            attackOwnerButton.setMessage(Text.literal(attackOwnerLabel()));
            attackOwnerButton.active = allowPvp;
        }
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

        // Character counter — shown to the right of the name field
        int nameLen = nameField.getText().length();
        String counter = nameLen + "/16";
        Formatting counterColor = nameLen >= 16 ? Formatting.RED
                : (nameLen >= 13 ? Formatting.YELLOW : Formatting.GRAY);
        int nameFieldX = leftColX + 40;
        int nameFieldW = (rightColX + COL_WIDTH) - nameFieldX;
        context.drawTextWithShadow(textRenderer,
                Text.literal(counter).formatted(counterColor),
                nameFieldX + nameFieldW + 4, NAME_Y + 6, 0xAAAAAA);

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


        // Error message — just above the cancel/save row.
        // Recompute bottomY using the same formula as init() to keep render in sync.
        int personalityListBottom2 = COLUMNS_TOP + 14 + PERSONALITIES.length * PERSONALITY_ROW_H;
        int blurbBottom2 = personalityListBottom2 + 4 + 10;
        int sliderColBottom2 = (COLUMNS_TOP + 14) + 5 * (SLIDER_H + SLIDER_GAP);
        int chatModeY2 = sliderColBottom2 + 6;
        int pvpY2 = chatModeY2 + 16 + 2;
        int attackOwnerY2 = pvpY2 + 16 + 2;
        int contentBottom2 = Math.max(blurbBottom2, attackOwnerY2 + 16);
        int bottomY2 = Math.min(contentBottom2 + 12, this.height - 30);

        if (errorMsg != null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(errorMsg).formatted(Formatting.RED),
                    centerX, bottomY2 - 12, 0xFF5555);
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

        // Self-collision check: bot can't have the same name as the player —
        // server would kick the bot with multiplayer.disconnect.name_taken on every spawn.
        String currentMcName = client.getSession() != null ? client.getSession().getUsername() : null;
        if (currentMcName != null && name.equalsIgnoreCase(currentMcName)) {
            errorMsg = "Bot can't share your Minecraft name — you'd kick it on every join.";
            return;
        }

        submitting = true;
        errorMsg = null;
        String personality = PERSONALITIES[selectedPersonality];
        String chatMode = CHAT_MODES[selectedChatMode];

        if (existingBot != null) {
            // Chain: personality → chat-mode → pvp toggles → close.
            BrainApiClient.get().updateBot(existingBot.id, name, personality)
                    .thenCompose(updated -> BrainApiClient.get().updateBotChatMode(updated.id, chatMode))
                    .thenCompose(updated -> BrainApiClient.get().updateBotPvp(updated.id, allowPvp, allowAttackOwner))
                    .thenRun(() -> client.execute(() -> client.setScreen(new VoxelMindScreen(null))))
                    .exceptionally(e -> {
                        errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                        submitting = false;
                        return null;
                    });
        } else {
            String ownerName = client.getSession() != null ? client.getSession().getUsername() : null;
            if (ownerName == null || ownerName.isEmpty()) {
                errorMsg = "Minecraft username not available. Please restart Minecraft.";
                submitting = false;
                return;
            }
            BrainApiClient.get().createBot(name, personality, ownerName, chatMode, allowPvp, allowAttackOwner)
                    .thenAccept(createdBot -> client.execute(() -> client.setScreen(new BotCapabilitiesScreen(createdBot))))
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
