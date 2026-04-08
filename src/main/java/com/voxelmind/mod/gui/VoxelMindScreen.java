package com.voxelmind.mod.gui;

import com.voxelmind.mod.api.BrainApiClient;
import com.voxelmind.mod.api.dto.AgentStatus;
import com.voxelmind.mod.api.dto.BotInfo;
import com.voxelmind.mod.auth.AuthManager;
import com.voxelmind.mod.config.ModConfig;
import com.voxelmind.mod.lan.LanManager;
import com.voxelmind.mod.lan.UpnpManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Main VoxelMind GUI screen — shows bot list, server status, and controls.
 */
public class VoxelMindScreen extends Screen {
    private static final int BOT_ROW_HEIGHT = 50;
    private static final int PANEL_WIDTH = 320;

    private final Screen parent;
    private List<BotInfo> bots = new ArrayList<>();
    private AgentStatus agentStatus = null;
    private boolean loading = true;
    private String errorMsg = null;
    private int scrollOffset = 0;

    public VoxelMindScreen(Screen parent) {
        super(Text.translatable("gui.voxelmind.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        if (!ModConfig.isLoggedIn()) {
            client.setScreen(new LoginScreen(parent));
            return;
        }

        // Refresh data
        loadData();

        // Bottom buttons
        int bottomY = this.height - 30;
        int centerX = this.width / 2;

        // Create Bot button
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voxelmind.create_bot"), button -> {
            client.setScreen(new BotConfigScreen(this, null));
        }).dimensions(centerX - PANEL_WIDTH / 2, bottomY, 100, 20).build());

        // LAN button (singleplayer only)
        LanManager lan = LanManager.get();
        if (lan.isSingleplayer()) {
            String lanLabel = lan.isLanOpen()
                    ? "LAN: " + lan.getActivePort()
                    : "Open LAN";
            addDrawableChild(ButtonWidget.builder(Text.literal(lanLabel), button -> {
                if (!lan.isLanOpen()) {
                    lan.openLan();
                    client.setScreen(new VoxelMindScreen(parent)); // Refresh
                }
            }).dimensions(centerX - 50, bottomY, 100, 20).build());
        }

        // Logout button
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voxelmind.logout"), button -> {
            AuthManager.get().logout();
            client.setScreen(new LoginScreen(parent));
        }).dimensions(centerX + PANEL_WIDTH / 2 - 60, bottomY, 60, 20).build());
    }

    private void loadData() {
        loading = true;
        errorMsg = null;

        BrainApiClient api = BrainApiClient.get();

        api.listBots().thenAccept(result -> {
            this.bots = result;
            this.loading = false;
        }).exceptionally(e -> {
            this.errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            this.loading = false;
            return null;
        });

        api.getAgentStatus().thenAccept(status -> {
            this.agentStatus = status;
        }).exceptionally(e -> null);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;
        int panelRight = centerX + PANEL_WIDTH / 2;
        int y = 20;

        // Title
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("VoxelMind Companion")
                .formatted(Formatting.GREEN, Formatting.BOLD), centerX, y, 0xFFFFFF);
        y += 20;

        // Status bar
        LanManager lan = LanManager.get();
        String serverText = lan.getServerAddress() != null
                ? "Server: " + lan.getServerAddress()
                : lan.isSingleplayer()
                ? "Singleplayer (LAN " + (lan.isLanOpen() ? "open" : "closed") + ")"
                : "No server detected";
        context.drawTextWithShadow(textRenderer, Text.literal(serverText).formatted(Formatting.GRAY),
                panelLeft, y, 0xAAAAAA);
        y += 12;

        String agentText = agentStatus != null
                ? (agentStatus.connected ? "Agent: Connected" : "Agent: Disconnected")
                : "Agent: ...";
        Formatting agentColor = agentStatus != null && agentStatus.connected ? Formatting.GREEN : Formatting.RED;
        context.drawTextWithShadow(textRenderer, Text.literal(agentText).formatted(agentColor),
                panelLeft, y, 0xFFFFFF);
        y += 16;

        // Separator
        context.fill(panelLeft, y, panelRight, y + 1, 0xFF444444);
        y += 6;

        // Bot list
        if (loading) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Loading...").formatted(Formatting.GRAY),
                    centerX, y + 20, 0xAAAAAA);
        } else if (errorMsg != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(errorMsg).formatted(Formatting.RED),
                    centerX, y + 20, 0xFF5555);
        } else if (bots.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("No bots yet. Create one below!").formatted(Formatting.GRAY),
                    centerX, y + 20, 0xAAAAAA);
        } else {
            int botAreaTop = y;
            int botAreaBottom = this.height - 40;
            int maxVisible = (botAreaBottom - botAreaTop) / BOT_ROW_HEIGHT;

            for (int i = scrollOffset; i < bots.size() && i < scrollOffset + maxVisible; i++) {
                renderBotRow(context, bots.get(i), panelLeft, panelRight, y, mouseX, mouseY);
                y += BOT_ROW_HEIGHT;
            }
        }
    }

    private void renderBotRow(DrawContext context, BotInfo bot, int left, int right, int y,
                              int mouseX, int mouseY) {
        // Background
        boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + BOT_ROW_HEIGHT - 2;
        context.fill(left, y, right, y + BOT_ROW_HEIGHT - 2, hovered ? 0x40FFFFFF : 0x20FFFFFF);

        // Bot name + personality
        context.drawTextWithShadow(textRenderer, Text.literal(bot.bot_name).formatted(Formatting.WHITE, Formatting.BOLD),
                left + 8, y + 6, 0xFFFFFF);
        context.drawTextWithShadow(textRenderer, Text.literal("(" + bot.personality_id + ")").formatted(Formatting.GRAY),
                left + 8 + textRenderer.getWidth(bot.bot_name + " "), y + 6, 0xAAAAAA);

        // Status badge
        Formatting statusColor = bot.isOnline() ? Formatting.GREEN : Formatting.GRAY;
        String statusText = "[" + bot.status + "]";
        context.drawTextWithShadow(textRenderer, Text.literal(statusText).formatted(statusColor),
                right - textRenderer.getWidth(statusText) - 8, y + 6, 0xFFFFFF);

        // Action buttons (rendered as text, click detection in mouseClicked)
        int btnY = y + 24;
        if (bot.isOnline()) {
            context.drawTextWithShadow(textRenderer, Text.literal("[Despawn]").formatted(Formatting.RED),
                    right - 60, btnY, 0xFF5555);
        } else {
            context.drawTextWithShadow(textRenderer, Text.literal("[Spawn]").formatted(Formatting.GREEN),
                    right - 60, btnY, 0x55FF55);
        }
        context.drawTextWithShadow(textRenderer, Text.literal("[Edit]").formatted(Formatting.YELLOW),
                right - 110, btnY, 0xFFFF55);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !bots.isEmpty()) {
            int centerX = this.width / 2;
            int panelLeft = centerX - PANEL_WIDTH / 2;
            int panelRight = centerX + PANEL_WIDTH / 2;
            int y = 74; // Start of bot list area

            for (int i = scrollOffset; i < bots.size(); i++) {
                int rowTop = y;
                int rowBottom = y + BOT_ROW_HEIGHT;

                if (mouseY >= rowTop && mouseY < rowBottom) {
                    BotInfo bot = bots.get(i);
                    int btnY = rowTop + 24;

                    // [Spawn] / [Despawn] button area
                    if (mouseX >= panelRight - 60 && mouseX <= panelRight - 8
                            && mouseY >= btnY && mouseY <= btnY + 12) {
                        if (bot.isOnline()) {
                            BrainApiClient.get().despawnBot(bot.id).thenRun(this::loadData);
                        } else {
                            spawnBot(bot);
                        }
                        return true;
                    }

                    // [Edit] button area
                    if (mouseX >= panelRight - 110 && mouseX <= panelRight - 65
                            && mouseY >= btnY && mouseY <= btnY + 12) {
                        client.setScreen(new BotConfigScreen(this, bot));
                        return true;
                    }
                }
                y += BOT_ROW_HEIGHT;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount > 0 && scrollOffset > 0) scrollOffset--;
        if (verticalAmount < 0 && scrollOffset < bots.size() - 1) scrollOffset++;
        return true;
    }

    private void spawnBot(BotInfo bot) {
        LanManager lan = LanManager.get();
        String address = lan.getServerAddress();
        if (address == null) {
            this.errorMsg = lan.isSingleplayer()
                    ? "Open LAN first (button below or /vm lan)"
                    : "No server detected.";
            return;
        }
        String host;
        int port;
        if (address.contains(":")) {
            String[] parts = address.split(":");
            host = parts[0];
            port = Integer.parseInt(parts[1]);
        } else {
            host = address;
            port = 25565;
        }
        BrainApiClient.get().spawnBot(bot.id, host, port).thenRun(this::loadData)
                .exceptionally(e -> {
                    this.errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                    return null;
                });
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
