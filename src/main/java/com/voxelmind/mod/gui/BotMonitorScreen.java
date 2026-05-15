package com.voxelmind.mod.gui;

import com.voxelmind.mod.api.BrainApiClient;
import com.voxelmind.mod.api.dto.BotInfo;
import com.voxelmind.mod.api.dto.BotState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Live monitor screen for an online bot.
 * Auto-refreshes every ~2 seconds (40 game ticks).
 */
public class BotMonitorScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int REFRESH_TICKS = 40;

    private final Screen parent;
    private final BotInfo bot;

    private BotState state = null;
    private String errorMsg = null;
    private boolean loading = true;
    private int ticksSinceRefresh = 0;
    private long lastUpdatedMs = 0;

    public BotMonitorScreen(Screen parent, BotInfo bot) {
        super(Text.literal("Monitor: " + bot.bot_name));
        this.parent = parent;
        this.bot = bot;
    }

    @Override
    protected void init() {
        super.init();
        int bottomY = this.height - 28;
        int cx = this.width / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("\u2190 Back"), btn -> client.setScreen(parent))
                .dimensions(cx - 90, bottomY, 80, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), btn -> fetchState())
                .dimensions(cx + 10, bottomY, 80, 20).build());

        fetchState();
    }

    @Override
    public void tick() {
        super.tick();
        ticksSinceRefresh++;
        if (ticksSinceRefresh >= REFRESH_TICKS) {
            ticksSinceRefresh = 0;
            fetchState();
        }
    }

    private void fetchState() {
        loading = true;
        errorMsg = null;
        BrainApiClient.get().getBotState(bot.id)
                .thenAccept(s -> {
                    this.state = s;
                    this.loading = false;
                    this.lastUpdatedMs = System.currentTimeMillis();
                })
                .exceptionally(e -> {
                    String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    this.errorMsg = msg != null ? msg : "Could not load bot state";
                    this.loading = false;
                    return null;
                });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int panelLeft = cx - PANEL_W / 2;
        int panelRight = cx + PANEL_W / 2;
        int y = 18;

        // Title
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Monitor").formatted(Formatting.GREEN, Formatting.BOLD),
                cx, y, 0xFFFFFF);
        y += 14;

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(bot.bot_name).formatted(Formatting.WHITE, Formatting.BOLD),
                cx, y, 0xFFFFFF);
        y += 18;

        context.fill(panelLeft, y, panelRight, y + 1, 0xFF444444);
        y += 10;

        if (loading && state == null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Loading...").formatted(Formatting.GRAY), cx, y + 10, 0xAAAAAA);
            return;
        }

        if (errorMsg != null && state == null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(errorMsg).formatted(Formatting.RED), cx, y + 10, 0xFF5555);
            return;
        }

        // ── Status ──
        String liveStatus = (state != null && state.status != null) ? state.status : bot.status;
        Formatting statusColor = switch (liveStatus != null ? liveStatus : "") {
            case "online" -> Formatting.GREEN;
            case "spawning" -> Formatting.YELLOW;
            default -> Formatting.DARK_GRAY;
        };
        String statusLabel = liveStatus != null ? switch (liveStatus) {
            case "online" -> "Online";
            case "spawning" -> "Spawning...";
            case "offline" -> "Offline";
            default -> liveStatus;
        } : "Unknown";
        renderRow(context, panelLeft, y, "Status", statusLabel, statusColor);
        y += 20;

        if (state != null) {
            // ── HP bar ──
            int hp = (int) Math.round(state.health);
            int hpMax = state.maxHealth > 0 ? state.maxHealth : 20;
            renderLabeledBar(context, panelLeft, panelRight, y,
                    "HP", hp, hpMax, 0xFF4A0000, 0xFFFF4444, Formatting.RED);
            y += 20;

            // ── Hunger bar ──
            int hunger = Math.max(0, Math.min(20, state.food));
            renderLabeledBar(context, panelLeft, panelRight, y,
                    "Hunger", hunger, 20, 0xFF442200, 0xFFFF8800, Formatting.GOLD);
            y += 20;

            // ── Current Activity ──
            renderRow(context, panelLeft, y, "Activity", state.getActivity(), Formatting.AQUA);
            y += 20;

            // ── Position ──
            if (state.position != null) {
                String pos = String.format("%.0f, %.0f, %.0f", state.position.x, state.position.y, state.position.z);
                renderRow(context, panelLeft, y, "Position", pos, Formatting.GRAY);
                y += 20;
            }

        }

        context.fill(panelLeft, y, panelRight, y + 1, 0xFF333333);
        y += 8;

        // Footer: last updated + loading indicator
        if (lastUpdatedMs > 0) {
            String updatedText = "Updated " + ((System.currentTimeMillis() - lastUpdatedMs) / 1000) + "s ago";
            context.drawTextWithShadow(textRenderer,
                    Text.literal(updatedText).formatted(Formatting.DARK_GRAY),
                    panelLeft, y, 0x666666);
        }
        if (loading) {
            String refreshing = "Refreshing...";
            context.drawTextWithShadow(textRenderer,
                    Text.literal(refreshing).formatted(Formatting.DARK_GRAY),
                    panelRight - textRenderer.getWidth(refreshing), y, 0x666666);
        }
    }

    private void renderRow(DrawContext ctx, int left, int y, String label, String value, Formatting valueColor) {
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(label + ": ").formatted(Formatting.GRAY),
                left, y, 0xAAAAAA);
        int labelW = textRenderer.getWidth(label + ": ");
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(value).formatted(valueColor),
                left + labelW, y, 0xFFFFFF);
    }

    private void renderLabeledBar(DrawContext ctx, int left, int right, int y,
                                   String label, int current, int max,
                                   int bgColor, int fillColor, Formatting textColor) {
        int barW = 100;
        int barH = 6;
        int labelW = textRenderer.getWidth(label + ": ");

        ctx.drawTextWithShadow(textRenderer,
                Text.literal(label + ": ").formatted(Formatting.GRAY), left, y, 0xAAAAAA);

        String valueText = current + "/" + max;
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(valueText).formatted(textColor), left + labelW, y, 0xFFFFFF);

        int barX = right - barW;
        int barY = y + (textRenderer.fontHeight - barH) / 2;
        ctx.fill(barX, barY, barX + barW, barY + barH, bgColor);
        int fillW = max > 0 ? (barW * current) / max : 0;
        ctx.fill(barX, barY, barX + fillW, barY + barH, fillColor);
        ctx.fill(barX, barY, barX + barW, barY + 1, 0xFF555555);
        ctx.fill(barX, barY + barH - 1, barX + barW, barY + barH, 0xFF222222);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
