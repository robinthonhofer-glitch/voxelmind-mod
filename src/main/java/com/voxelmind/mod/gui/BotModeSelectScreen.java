package com.voxelmind.mod.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Mode selection screen shown when the user clicks "New Bot".
 * Three modes: Companion (active), Survivor (coming soon), NPC (coming soon).
 */
public class BotModeSelectScreen extends Screen {

    private static final int CARD_W = 100;
    private static final int CARD_H = 88;
    private static final int CARD_GAP = 5;
    private static final int TOTAL_CARDS_W = 3 * CARD_W + 2 * CARD_GAP; // 320

    private final Screen parent;
    private String tooltipMsg = null;

    private static final String[] MODE_NAMES = {"Companion", "Survivor", "NPC"};
    private static final String[] MODE_ICONS = {"[C]", "[S]", "[N]"};
    private static final String[] MODE_DESC_LINE1 = {
            "Follows you,",
            "Plays Minecraft",
            "AI character for"
    };
    private static final String[] MODE_DESC_LINE2 = {
            "helps build,",
            "for itself.",
            "your server."
    };
    private static final String[] MODE_DESC_LINE3 = {
            "talks back.",
            "Just watch.",
            "Set a role."
    };
    private static final boolean[] MODE_ACTIVE = {true, false, false};
    private static final String[] MODE_TOOLTIP = {
            null,
            "Coming Soon \u2014 autonomous survival AI",
            "Coming Soon \u2014 configurable NPC for your server"
    };

    public BotModeSelectScreen(Screen parent) {
        super(Text.literal("Select Mode"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int bottomY = this.height - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), btn -> client.setScreen(parent))
                .dimensions(this.width / 2 - 40, bottomY, 80, 20).build());
    }

    private int cardsLeft() {
        return this.width / 2 - TOTAL_CARDS_W / 2;
    }

    private int cardsTop() {
        return 54;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int cx = this.width / 2;
        tooltipMsg = null;

        // Header
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Choose a Mode").formatted(Formatting.GREEN, Formatting.BOLD),
                cx, 18, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("What kind of bot do you want to create?").formatted(Formatting.GRAY),
                cx, 32, 0xAAAAAA);

        int left = cardsLeft();
        int top = cardsTop();

        for (int i = 0; i < 3; i++) {
            int cardX = left + i * (CARD_W + CARD_GAP);
            renderCard(context, i, cardX, top, mouseX, mouseY);
        }

        // Tooltip for coming-soon cards
        if (tooltipMsg != null) {
            int tipY = top + CARD_H + 10;
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(tooltipMsg).formatted(Formatting.GOLD),
                    cx, tipY, 0xFFAA55);
        }
    }

    private void renderCard(DrawContext ctx, int idx, int cardX, int cardY, int mouseX, int mouseY) {
        boolean active = MODE_ACTIVE[idx];
        boolean hovered = mouseX >= cardX && mouseX < cardX + CARD_W
                && mouseY >= cardY && mouseY < cardY + CARD_H;

        // Background
        int bg = !active ? 0xFF111111 : (hovered ? 0xFF2A5C2A : 0xFF1A3D1A);
        ctx.fill(cardX, cardY, cardX + CARD_W, cardY + CARD_H, bg);

        // Border (1px on all sides)
        int border = active ? (hovered ? 0xFF55FF55 : 0xFF336633) : 0xFF2A2A2A;
        ctx.fill(cardX, cardY, cardX + CARD_W, cardY + 1, border);
        ctx.fill(cardX, cardY + CARD_H - 1, cardX + CARD_W, cardY + CARD_H, border);
        ctx.fill(cardX, cardY, cardX + 1, cardY + CARD_H, border);
        ctx.fill(cardX + CARD_W - 1, cardY, cardX + CARD_W, cardY + CARD_H, border);

        int cx = cardX + CARD_W / 2;

        // Icon
        Formatting iconFmt = active ? (hovered ? Formatting.GREEN : Formatting.WHITE) : Formatting.DARK_GRAY;
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(MODE_ICONS[idx]).formatted(iconFmt, Formatting.BOLD),
                cx, cardY + 10, 0xFFFFFF);

        // Mode name
        Formatting nameFmt = active ? (hovered ? Formatting.GREEN : Formatting.WHITE) : Formatting.DARK_GRAY;
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal(MODE_NAMES[idx]).formatted(nameFmt, Formatting.BOLD),
                cx, cardY + 24, 0xFFFFFF);

        if (!active) {
            // Coming Soon badge
            String badge = "Coming Soon";
            int bw = textRenderer.getWidth(badge) + 6;
            int bx = cx - bw / 2;
            int by = cardY + 38;
            ctx.fill(bx, by, bx + bw, by + 11, 0xFF442200);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(badge).formatted(Formatting.GOLD),
                    cx, by + 2, 0xFFAA55);

            if (hovered && MODE_TOOLTIP[idx] != null) {
                tooltipMsg = MODE_TOOLTIP[idx];
            }
        } else {
            // 3-line description
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(MODE_DESC_LINE1[idx]).formatted(Formatting.GRAY), cx, cardY + 40, 0xAAAAAA);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(MODE_DESC_LINE2[idx]).formatted(Formatting.GRAY), cx, cardY + 52, 0xAAAAAA);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(MODE_DESC_LINE3[idx]).formatted(Formatting.GRAY), cx, cardY + 64, 0xAAAAAA);

            // "Select" hint when hovered
            if (hovered) {
                ctx.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("[ Click to select ]").formatted(Formatting.GREEN),
                        cx, cardY + 76, 0x55FF55);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        int left = cardsLeft();
        int top = cardsTop();

        for (int i = 0; i < 3; i++) {
            int cardX = left + i * (CARD_W + CARD_GAP);
            if (mouseX >= cardX && mouseX < cardX + CARD_W
                    && mouseY >= top && mouseY < top + CARD_H) {
                if (MODE_ACTIVE[i]) {
                    client.setScreen(new BotConfigScreen(this, null));
                }
                // Coming soon — do nothing on click
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
