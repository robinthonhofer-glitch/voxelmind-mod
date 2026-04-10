package com.voxelmind.mod.gui;

import com.voxelmind.mod.api.BrainApiClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Feedback Screen — user writes free-text feedback about their bot.
 * Submits to POST /feedback on the Brain.
 */
public class FeedbackScreen extends Screen {
    private static final int MAX_CHARS = 1000;

    private final Screen parent;
    /** Optional — if set, the feedback is associated with a specific bot. */
    private final String botId;

    private EditBoxWidget textBox;
    private ButtonWidget sendButton;
    private String statusMsg = null;
    private boolean submitting = false;
    private boolean sent = false;
    /** Render ticks left before auto-closing after a successful send. -1 = not closing. */
    private int closeCountdown = -1;

    public FeedbackScreen(Screen parent, String botId) {
        super(Text.literal("Feedback"));
        this.parent = parent;
        this.botId = botId;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int boxWidth = 300;
        int boxLeft = centerX - boxWidth / 2;
        int boxTop = 80;
        int boxHeight = this.height - boxTop - 70;

        textBox = new EditBoxWidget(
                textRenderer, boxLeft, boxTop, boxWidth, boxHeight,
                Text.literal("Feedback"),
                Text.literal(""));
        textBox.setMaxLength(MAX_CHARS);
        addDrawableChild(textBox);
        setInitialFocus(textBox);

        int btnY = this.height - 30;

        // Cancel
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(centerX - 110, btnY, 100, 20).build());

        // Send
        sendButton = ButtonWidget.builder(Text.literal("Send Feedback"), b -> submit())
                .dimensions(centerX + 10, btnY, 100, 20).build();
        addDrawableChild(sendButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;

        // Title
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Help us improve!").formatted(Formatting.GREEN, Formatting.BOLD),
                centerX, 20, 0xFFFFFF);

        // Subtitle
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Tell us what your bot did \u2014 good or bad.").formatted(Formatting.GRAY),
                centerX, 38, 0xAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Every bit of feedback makes the AI smarter.").formatted(Formatting.GRAY),
                centerX, 50, 0xAAAAAA);

        // Char counter (top-right of text box)
        String text = textBox != null ? textBox.getText() : "";
        String counter = text.length() + " / " + MAX_CHARS;
        int counterX = centerX + 150 - textRenderer.getWidth(counter);
        context.drawTextWithShadow(textRenderer,
                Text.literal(counter).formatted(
                        text.length() >= MAX_CHARS ? Formatting.RED : Formatting.DARK_GRAY),
                counterX, 68, 0x777777);

        // Status line (submitting / sent / error)
        if (statusMsg != null) {
            Formatting color = sent ? Formatting.GREEN
                    : (submitting ? Formatting.YELLOW : Formatting.RED);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(statusMsg).formatted(color),
                    centerX, this.height - 50, 0xFFFFFF);
        }

        // Auto-close countdown after successful send (~20 render ticks ≈ 1s at 20fps)
        if (closeCountdown > 0) {
            closeCountdown--;
            if (closeCountdown == 0) {
                close();
            }
        }
    }

    private void submit() {
        if (submitting || sent) return;
        String text = textBox.getText().trim();
        if (text.isEmpty()) {
            statusMsg = "Please write some feedback first.";
            return;
        }
        if (text.length() > MAX_CHARS) {
            statusMsg = "Feedback too long (max " + MAX_CHARS + " chars).";
            return;
        }
        submitting = true;
        statusMsg = "Sending...";
        sendButton.active = false;

        BrainApiClient.get().sendFeedback(text, botId)
                .whenComplete((ok, err) -> client.execute(() -> {
                    submitting = false;
                    if (err != null) {
                        String raw = err.getCause() != null ? err.getCause().getMessage() : err.getMessage();
                        statusMsg = "Failed: " + (raw != null ? raw : "unknown error");
                        sendButton.active = true;
                    } else {
                        sent = true;
                        statusMsg = "Thanks! Feedback sent.";
                        // Auto-close after ~30 render ticks so the user sees the confirmation
                        closeCountdown = 30;
                    }
                }));
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
