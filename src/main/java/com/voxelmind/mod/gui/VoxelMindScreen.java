package com.voxelmind.mod.gui;

import com.voxelmind.mod.api.BrainApiClient;
import com.voxelmind.mod.api.dto.AgentStatus;
import com.voxelmind.mod.api.dto.BotInfo;
import com.voxelmind.mod.api.dto.ProfileInfo;
import com.voxelmind.mod.auth.AuthManager;
import com.voxelmind.mod.config.ModConfig;
import com.voxelmind.mod.lan.LanManager;
import com.voxelmind.mod.tunnel.TunnelManager;
import com.voxelmind.mod.tunnel.TunnelStatus;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Main VoxelMind GUI screen — shows bot list, server status, sparks, and controls.
 */
public class VoxelMindScreen extends Screen {
    private static final int BOT_ROW_HEIGHT = 50;
    private static final int PANEL_WIDTH = 320;
    private static final String PRICING_URL = "https://voxel-mind.com/pricing";
    private static final String ACCOUNT_URL = "https://voxel-mind.com/account";

    private final Screen parent;
    private List<BotInfo> bots = new ArrayList<>();
    private AgentStatus agentStatus = null;
    private ProfileInfo profile = null;
    private boolean loading = true;
    private String errorMsg = null;
    private String statusMsg = null; // transient info ("Spawning Bob...")
    private int scrollOffset = 0;

    /** Bot IDs with a pending spawn/despawn request — rendered as "..." button. */
    private final Set<String> pendingBotIds = new HashSet<>();

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

        // Bottom button bar: Create | Feedback | Account | Logout
        int bottomY = this.height - 30;
        int centerX = this.width / 2;
        int panelLeft = centerX - PANEL_WIDTH / 2;

        // Create Bot (left)
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voxelmind.create_bot"), button -> {
            client.setScreen(new BotConfigScreen(this, null));
        }).dimensions(panelLeft, bottomY, 80, 20).build());

        // Feedback
        addDrawableChild(ButtonWidget.builder(Text.literal("Feedback"), button -> {
            client.setScreen(new FeedbackScreen(this, null));
        }).dimensions(panelLeft + 84, bottomY, 70, 20).build());

        // Account
        addDrawableChild(ButtonWidget.builder(Text.literal("Account"), button -> {
            openBrowser(ACCOUNT_URL);
        }).dimensions(panelLeft + 158, bottomY, 70, 20).build());

        // Logout (right)
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voxelmind.logout"), button -> {
            AuthManager.get().logout();
            client.setScreen(new LoginScreen(parent));
        }).dimensions(panelLeft + PANEL_WIDTH - 60, bottomY, 60, 20).build());
    }

    private void loadData() {
        loading = true;
        errorMsg = null;

        BrainApiClient api = BrainApiClient.get();

        api.listBots().thenAccept(result -> {
            this.bots = result;
            this.loading = false;
        }).exceptionally(e -> {
            this.errorMsg = friendlyError(e);
            this.loading = false;
            return null;
        });

        api.getAgentStatus().thenAccept(status -> {
            this.agentStatus = status;
        }).exceptionally(e -> null);

        // Profile for sparks display — also triggers MC username auto-sync
        api.getProfile().thenAccept(p -> {
            this.profile = p;
            syncMcUsernameIfNeeded(p);
        }).exceptionally(e -> null);
    }

    /**
     * If the profile's mc_username doesn't match the current MC session username,
     * push the current name to the Brain silently. Idempotent.
     */
    private void syncMcUsernameIfNeeded(ProfileInfo p) {
        if (p == null || client == null || client.getSession() == null) return;
        String current = client.getSession().getUsername();
        if (current == null || current.isEmpty()) return;
        if (current.equals(p.mc_username)) return;

        BrainApiClient.get().updateMcUsername(current)
                .thenRun(() -> {
                    if (p != null) p.mc_username = current;
                })
                .exceptionally(e -> null); // silent — not user-facing
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

        // ─── Status bar (left) + Sparks (right) ───
        int statusTop = y;

        // Server / tunnel status (left column)
        LanManager lan = LanManager.get();
        if (lan.isSingleplayer()) {
            TunnelStatus tunnelStatus = TunnelManager.get().getOverallStatus();
            String serverText;
            Formatting serverColor;
            switch (tunnelStatus) {
                case READY -> {
                    serverText = "Server: localhost:" + lan.getActivePort() + " via tunnel";
                    serverColor = Formatting.GREEN;
                }
                case CONNECTING, AUTHENTICATING -> {
                    serverText = "Tunnel: Connecting...";
                    serverColor = Formatting.YELLOW;
                }
                case ERROR -> {
                    serverText = "Tunnel: Error";
                    serverColor = Formatting.RED;
                }
                default -> {
                    serverText = lan.isLanOpen()
                            ? "Singleplayer (LAN open, tunnel idle)"
                            : "Singleplayer (LAN closed)";
                    serverColor = Formatting.GRAY;
                }
            }
            context.drawTextWithShadow(textRenderer, Text.literal(serverText).formatted(serverColor),
                    panelLeft, y, 0xFFFFFF);
        } else {
            String serverText = lan.getServerAddress() != null
                    ? "Server: " + lan.getServerAddress()
                    : "No server detected";
            context.drawTextWithShadow(textRenderer, Text.literal(serverText).formatted(Formatting.GRAY),
                    panelLeft, y, 0xAAAAAA);
        }
        y += 12;

        String agentText = agentStatus != null
                ? (agentStatus.connected ? "Agent: Connected" : "Agent: Disconnected")
                : "Agent: ...";
        Formatting agentColor = agentStatus != null && agentStatus.connected ? Formatting.GREEN : Formatting.RED;
        context.drawTextWithShadow(textRenderer, Text.literal(agentText).formatted(agentColor),
                panelLeft, y, 0xFFFFFF);
        y += 16;

        // Sparks display (right column, same Y as status bar)
        renderSparks(context, panelRight, statusTop, mouseX, mouseY);

        // Separator
        context.fill(panelLeft, y, panelRight, y + 1, 0xFF444444);
        y += 6;

        // Transient status message (e.g. "Spawning Bob...")
        if (statusMsg != null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(statusMsg).formatted(Formatting.YELLOW),
                    centerX, y, 0xFFFF55);
            y += 12;
        }

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

    /**
     * Render sparks as "⚡ 247/500" with a progress bar below, right-aligned
     * to panelRight. When sparks=0, render a "Get More Sparks" text link.
     */
    private void renderSparks(DrawContext context, int right, int top, int mouseX, int mouseY) {
        if (profile == null) {
            String loadingText = "Sparks: ...";
            context.drawTextWithShadow(textRenderer,
                    Text.literal(loadingText).formatted(Formatting.GRAY),
                    right - textRenderer.getWidth(loadingText), top, 0xAAAAAA);
            return;
        }

        int current = Math.max(0, profile.sparks_remaining);
        int max = Math.max(current, profile.maxSparksForTier());

        // Line 1: ⚡ 247 / 500 Sparks
        String label = "\u26A1 " + current + " / " + max + " Sparks";
        Formatting labelColor = current == 0 ? Formatting.RED
                : (current < max / 5 ? Formatting.YELLOW : Formatting.WHITE);
        int labelWidth = textRenderer.getWidth(label);
        context.drawTextWithShadow(textRenderer,
                Text.literal(label).formatted(labelColor),
                right - labelWidth, top, 0xFFFFFF);

        // Line 2: progress bar (80 wide)
        int barW = 80;
        int barX = right - barW;
        int barY = top + 12;
        int barH = 4;
        context.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
        int fillW = max > 0 ? (barW * current) / max : 0;
        int fillColor = current == 0 ? 0xFFAA0000
                : (current < max / 5 ? 0xFFFFAA00 : 0xFF55FF55);
        context.fill(barX, barY, barX + fillW, barY + barH, fillColor);

        // Line 3: "Get More" link (clickable text)
        String link = current == 0 ? "Out of sparks — Get More" : "[Get More]";
        Formatting linkColor = current == 0 ? Formatting.RED : Formatting.AQUA;
        boolean hovered = mouseX >= right - textRenderer.getWidth(link)
                && mouseX <= right
                && mouseY >= barY + 6 && mouseY <= barY + 18;
        if (hovered) linkColor = Formatting.WHITE;
        context.drawTextWithShadow(textRenderer,
                Text.literal(link).formatted(linkColor),
                right - textRenderer.getWidth(link), barY + 8, 0xFFFFFF);
    }

    private boolean isSparksLinkClicked(double mouseX, double mouseY) {
        int centerX = this.width / 2;
        int right = centerX + PANEL_WIDTH / 2;
        int top = 40; // title (20) + 20
        int barY = top + 12;
        String link = (profile != null && profile.sparks_remaining == 0)
                ? "Out of sparks — Get More" : "[Get More]";
        int linkW = textRenderer.getWidth(link);
        return mouseX >= right - linkW && mouseX <= right
                && mouseY >= barY + 8 && mouseY <= barY + 20;
    }

    private void renderBotRow(DrawContext context, BotInfo bot, int left, int right, int y,
                              int mouseX, int mouseY) {
        // Background
        boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + BOT_ROW_HEIGHT - 2;
        context.fill(left, y, right, y + BOT_ROW_HEIGHT - 2, hovered ? 0x40FFFFFF : 0x20FFFFFF);

        // Bot name + personality
        // NOTE: getWidth() counts BOLD chars correctly when the Text has the BOLD style,
        // plus a 4-pixel gap to prevent the last bold char from touching "(personality)".
        Text nameText = Text.literal(bot.bot_name).formatted(Formatting.WHITE, Formatting.BOLD);
        context.drawTextWithShadow(textRenderer, nameText, left + 8, y + 6, 0xFFFFFF);
        int nameWidth = textRenderer.getWidth(nameText);
        context.drawTextWithShadow(textRenderer, Text.literal("(" + bot.personality_id + ")").formatted(Formatting.GRAY),
                left + 8 + nameWidth + 4, y + 6, 0xAAAAAA);

        // Status badge
        boolean pending = pendingBotIds.contains(bot.id);
        String statusText;
        Formatting statusColor;
        if (pending) {
            statusText = "[working]";
            statusColor = Formatting.YELLOW;
        } else {
            statusText = "[" + bot.status + "]";
            statusColor = bot.isOnline() ? Formatting.GREEN : Formatting.GRAY;
        }
        context.drawTextWithShadow(textRenderer, Text.literal(statusText).formatted(statusColor),
                right - textRenderer.getWidth(statusText) - 8, y + 6, 0xFFFFFF);

        // Action buttons (rendered as text, click detection in mouseClicked)
        int btnY = y + 24;
        if (pending) {
            context.drawTextWithShadow(textRenderer, Text.literal("[...]").formatted(Formatting.YELLOW),
                    right - 60, btnY, 0xFFFF55);
        } else if (bot.isOnline()) {
            context.drawTextWithShadow(textRenderer, Text.literal("[Despawn]").formatted(Formatting.RED),
                    right - 60, btnY, 0xFF5555);
        } else {
            context.drawTextWithShadow(textRenderer, Text.literal("[Spawn]").formatted(Formatting.GREEN),
                    right - 60, btnY, 0x55FF55);
        }
        context.drawTextWithShadow(textRenderer, Text.literal("[Edit]").formatted(Formatting.YELLOW),
                right - 110, btnY, 0xFFFF55);
        if (!bot.isOnline() && !pending) {
            context.drawTextWithShadow(textRenderer, Text.literal("[Delete]").formatted(Formatting.DARK_RED),
                    left + 8, btnY, 0xAA0000);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Buttons (Logout, Create, LAN) get priority
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Sparks "Get More" link
        if (button == 0 && isSparksLinkClicked(mouseX, mouseY)) {
            openBrowser(PRICING_URL);
            return true;
        }

        if (button == 0 && !bots.isEmpty()) {
            int centerX = this.width / 2;
            int panelLeft = centerX - PANEL_WIDTH / 2;
            int panelRight = centerX + PANEL_WIDTH / 2;

            // Bot list area starts below header (title 20 + status block ~36 + separator 7
            //   + optional statusMsg line 12)
            int y = 20 + 20 + 12 + 16 + 7;
            if (statusMsg != null) y += 12;

            for (int i = scrollOffset; i < bots.size(); i++) {
                int rowTop = y;
                int rowBottom = y + BOT_ROW_HEIGHT;

                if (mouseY >= rowTop && mouseY < rowBottom) {
                    BotInfo bot = bots.get(i);
                    int btnY = rowTop + 24;

                    if (pendingBotIds.contains(bot.id)) return true; // ignore clicks while pending

                    // [Spawn] / [Despawn] button area
                    if (mouseX >= panelRight - 60 && mouseX <= panelRight - 8
                            && mouseY >= btnY && mouseY <= btnY + 12) {
                        if (bot.isOnline()) {
                            despawnBot(bot);
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

                    // [Delete] button area (only when offline)
                    if (!bot.isOnline() && mouseX >= panelLeft + 8 && mouseX <= panelLeft + 60
                            && mouseY >= btnY && mouseY <= btnY + 12) {
                        BrainApiClient.get().deleteBot(bot.id).thenRun(this::loadData)
                                .exceptionally(e -> {
                                    this.errorMsg = friendlyError(e);
                                    return null;
                                });
                        return true;
                    }
                }
                y += BOT_ROW_HEIGHT;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount > 0 && scrollOffset > 0) scrollOffset--;
        if (verticalAmount < 0 && scrollOffset < bots.size() - 1) scrollOffset++;
        return true;
    }

    private void spawnBot(BotInfo bot) {
        LanManager lan = LanManager.get();
        pendingBotIds.add(bot.id);
        errorMsg = null;
        statusMsg = "Spawning " + bot.bot_name + "...";

        if (lan.isSingleplayer()) {
            // Singleplayer: ensure LAN is open, then use tunnel
            if (!lan.isLanOpen()) {
                boolean opened = lan.openLan();
                if (!opened) {
                    pendingBotIds.remove(bot.id);
                    statusMsg = null;
                    errorMsg = "Failed to open LAN. Try saving the world first.";
                    return;
                }
            }

            statusMsg = "Opening tunnel for " + bot.bot_name + "...";
            int localPort = lan.getActivePort();
            TunnelManager.get().openTunnel(bot.id, localPort).thenAccept(tunnel -> {
                statusMsg = "Spawning " + bot.bot_name + " via tunnel...";
                BrainApiClient.get().spawnBot(bot.id, tunnel.getRelayHost(), tunnel.getTunnelPort())
                        .whenComplete((ok, err) -> {
                            pendingBotIds.remove(bot.id);
                            statusMsg = null;
                            if (err != null) {
                                this.errorMsg = friendlySpawnError(err);
                            }
                            loadData();
                        });
            }).exceptionally(e -> {
                pendingBotIds.remove(bot.id);
                statusMsg = null;
                this.errorMsg = "Tunnel failed: " + friendlyError(e);
                return null;
            });
        } else {
            // Multiplayer: direct connection (no tunnel)
            String address = lan.getServerAddress();
            if (address == null) {
                pendingBotIds.remove(bot.id);
                statusMsg = null;
                errorMsg = "No server detected.";
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
            BrainApiClient.get().spawnBot(bot.id, host, port)
                    .whenComplete((ok, err) -> {
                        pendingBotIds.remove(bot.id);
                        statusMsg = null;
                        if (err != null) {
                            this.errorMsg = friendlySpawnError(err);
                        }
                        loadData();
                    });
        }
    }

    private void despawnBot(BotInfo bot) {
        pendingBotIds.add(bot.id);
        errorMsg = null;
        statusMsg = "Stopping " + bot.bot_name + "...";
        BrainApiClient.get().despawnBot(bot.id)
                .whenComplete((ok, err) -> {
                    pendingBotIds.remove(bot.id);
                    statusMsg = null;
                    if (err != null) {
                        this.errorMsg = "Despawn failed: " + friendlyError(err);
                    } else {
                        // Close tunnel for this bot (noop if none)
                        TunnelManager.get().closeTunnel(bot.id);
                    }
                    loadData();
                });
    }

    /**
     * Turn raw error messages (connection refused, kicked, timeout, ECONNRESET,
     * "Out of sparks", etc.) into user-friendly one-liners specifically for spawn.
     */
    private static String friendlySpawnError(Throwable e) {
        String raw = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        if (raw == null) return "Spawn failed: unknown error";
        String lower = raw.toLowerCase();
        if (lower.contains("econnrefused") || lower.contains("connection refused"))
            return "Connection refused — is the server running?";
        if (lower.contains("econnreset") || lower.contains("socket closed"))
            return "Connection dropped — check online-mode=false on the server";
        if (lower.contains("kick") || lower.contains("whitelist"))
            return "Bot was kicked — check online-mode=false and whitelist";
        if (lower.contains("timeout"))
            return "Timeout — server not reachable";
        if (lower.contains("no agent connected") || lower.contains("starting up"))
            return "VoxelMind server is starting up, try again in a moment.";
        if (lower.contains("out of sparks") || lower.contains("sparks_remaining"))
            return "Out of sparks. Get more from the Pricing page.";
        if (lower.contains("simulation not running"))
            return "Simulation not running — start it on voxel-mind.com first.";
        return "Spawn failed: " + raw;
    }

    private static String friendlyError(Throwable e) {
        String raw = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        return raw != null ? raw : "Unknown error";
    }

    private void openBrowser(String url) {
        try {
            Util.getOperatingSystem().open(URI.create(url));
        } catch (Exception e) {
            errorMsg = "Could not open browser. Visit: " + url;
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
