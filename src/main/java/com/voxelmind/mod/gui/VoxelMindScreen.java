package com.voxelmind.mod.gui;

import com.voxelmind.mod.api.BrainApiClient;
import com.voxelmind.mod.api.dto.AgentStatus;
import com.voxelmind.mod.api.dto.BotInfo;
import com.voxelmind.mod.api.dto.ProfileInfo;
import com.voxelmind.mod.auth.AuthManager;
import com.voxelmind.mod.config.ModConfig;
import com.voxelmind.mod.lan.LanManager;
import com.voxelmind.mod.spawn.SpawnCheckScheduler;
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

        // Create Bot (left) — opens Mode Selection first
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voxelmind.create_bot"), button -> {
            client.setScreen(new BotModeSelectScreen(this));
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
        y += 28; // status row height — must be tall enough for the 3-line sparks block

        // Sparks display (right column, anchored to statusTop)
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
            int totalBots = bots.size();
            boolean canScrollUp = scrollOffset > 0;
            boolean canScrollDown = scrollOffset + maxVisible < totalBots;

            // ▲ scroll indicator — above first row
            if (canScrollUp) {
                String upArrow = "▲";
                int arrowX = centerX - textRenderer.getWidth(upArrow) / 2;
                boolean upHovered = mouseX >= arrowX - 4 && mouseX <= arrowX + textRenderer.getWidth(upArrow) + 4
                        && mouseY >= y - 11 && mouseY <= y - 1;
                context.drawTextWithShadow(textRenderer,
                        Text.literal(upArrow).formatted(upHovered ? Formatting.WHITE : Formatting.GRAY),
                        arrowX, y - 11, 0xFFFFFF);
            }

            // Scroll position counter e.g. "2/5"
            if (totalBots > maxVisible) {
                String counter = (scrollOffset + 1) + "/" + totalBots;
                context.drawTextWithShadow(textRenderer,
                        Text.literal(counter).formatted(Formatting.DARK_GRAY),
                        panelRight - textRenderer.getWidth(counter), botAreaTop - 11, 0x888888);
            }

            for (int i = scrollOffset; i < bots.size() && i < scrollOffset + maxVisible; i++) {
                renderBotRow(context, bots.get(i), panelLeft, panelRight, y, mouseX, mouseY);
                y += BOT_ROW_HEIGHT;
            }

            // ▼ scroll indicator — below last visible row
            if (canScrollDown) {
                String downArrow = "▼";
                int arrowX = centerX - textRenderer.getWidth(downArrow) / 2;
                boolean downHovered = mouseX >= arrowX - 4 && mouseX <= arrowX + textRenderer.getWidth(downArrow) + 4
                        && mouseY >= y + 1 && mouseY <= y + 11;
                context.drawTextWithShadow(textRenderer,
                        Text.literal(downArrow).formatted(downHovered ? Formatting.WHITE : Formatting.GRAY),
                        arrowX, y + 1, 0xFFFFFF);
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

        // top+0: "[Get More]" link — right-aligned
        String link = current == 0 ? "Out of sparks — Get More" : "[Get More]";
        Formatting linkColor = current == 0 ? Formatting.RED : Formatting.AQUA;
        boolean hovered = mouseX >= right - textRenderer.getWidth(link)
                && mouseX <= right
                && mouseY >= top && mouseY <= top + 10;
        if (hovered) linkColor = Formatting.WHITE;
        context.drawTextWithShadow(textRenderer,
                Text.literal(link).formatted(linkColor),
                right - textRenderer.getWidth(link), top, 0xFFFFFF);

        // top+10: ⚡ 247 / 500 Sparks — right-aligned
        String label = "\u26A1 " + current + " / " + max + " Sparks";
        Formatting labelColor = current == 0 ? Formatting.RED
                : (current < max / 5 ? Formatting.YELLOW : Formatting.WHITE);
        context.drawTextWithShadow(textRenderer,
                Text.literal(label).formatted(labelColor),
                right - textRenderer.getWidth(label), top + 10, 0xFFFFFF);

        // top+20: progress bar — right-aligned, fits within 28px status area
        int barW = 80;
        int barX = right - barW;
        int barY = top + 20;
        int barH = 4;
        context.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
        int fillW = max > 0 ? (barW * current) / max : 0;
        int fillColor = current == 0 ? 0xFFAA0000
                : (current < max / 5 ? 0xFFFFAA00 : 0xFF55FF55);
        context.fill(barX, barY, barX + fillW, barY + barH, fillColor);
    }

    private boolean isSparksLinkClicked(double mouseX, double mouseY) {
        int centerX = this.width / 2;
        int right = centerX + PANEL_WIDTH / 2;
        int top = 40; // title (20) + 20
        String link = (profile != null && profile.sparks_remaining == 0)
                ? "Out of sparks — Get More" : "[Get More]";
        int linkW = textRenderer.getWidth(link);
        return mouseX >= right - linkW && mouseX <= right
                && mouseY >= top && mouseY <= top + 10;
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

        // Action buttons
        int btnY = y + 23;
        int btnH = 16;

        // Primary action: Spawn / Despawn — filled colored button
        int primaryBtnW = 58;
        int primaryBtnX = right - primaryBtnW - 6;
        boolean primaryHovered = mouseX >= primaryBtnX && mouseX <= primaryBtnX + primaryBtnW
                && mouseY >= btnY && mouseY < btnY + btnH;

        if (pending) {
            // Gray background, yellow "..." text
            int bgColor = primaryHovered ? 0xFF555533 : 0xFF444422;
            context.fill(primaryBtnX, btnY, primaryBtnX + primaryBtnW, btnY + btnH, bgColor);
            context.fill(primaryBtnX, btnY, primaryBtnX + primaryBtnW, btnY + 1, 0xFF666644);
            context.fill(primaryBtnX, btnY + btnH - 1, primaryBtnX + primaryBtnW, btnY + btnH, 0xFF333311);
            String pendingLabel = "...";
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(pendingLabel).formatted(Formatting.YELLOW),
                    primaryBtnX + primaryBtnW / 2, btnY + (btnH - 8) / 2, 0xFFFF55);
        } else if (bot.isOnline()) {
            // Red background for Despawn
            int bgColor = primaryHovered ? 0xFF9A3D3D : 0xFF7A2D2D;
            context.fill(primaryBtnX, btnY, primaryBtnX + primaryBtnW, btnY + btnH, bgColor);
            context.fill(primaryBtnX, btnY, primaryBtnX + primaryBtnW, btnY + 1, 0xFFBB5555);
            context.fill(primaryBtnX, btnY + btnH - 1, primaryBtnX + primaryBtnW, btnY + btnH, 0xFF551111);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Despawn").formatted(Formatting.WHITE),
                    primaryBtnX + primaryBtnW / 2, btnY + (btnH - 8) / 2, 0xFFFFFF);
        } else {
            // Green background for Spawn
            int bgColor = primaryHovered ? 0xFF3D9A3D : 0xFF2D7A2D;
            context.fill(primaryBtnX, btnY, primaryBtnX + primaryBtnW, btnY + btnH, bgColor);
            context.fill(primaryBtnX, btnY, primaryBtnX + primaryBtnW, btnY + 1, 0xFF55BB55);
            context.fill(primaryBtnX, btnY + btnH - 1, primaryBtnX + primaryBtnW, btnY + btnH, 0xFF115511);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Spawn").formatted(Formatting.WHITE),
                    primaryBtnX + primaryBtnW / 2, btnY + (btnH - 8) / 2, 0xFFFFFF);
        }

        // Secondary: [Edit] — text only, less prominent
        context.drawTextWithShadow(textRenderer, Text.literal("[Edit]").formatted(Formatting.YELLOW),
                primaryBtnX - textRenderer.getWidth("[Edit]") - 6, btnY + (btnH - 8) / 2, 0xFFFF55);

        // Secondary: [Delete] — text only, only when offline
        if (!bot.isOnline() && !pending) {
            context.drawTextWithShadow(textRenderer, Text.literal("[Delete]").formatted(Formatting.DARK_RED),
                    left + 8, btnY + (btnH - 8) / 2, 0xAA0000);
        }

        // Secondary: [Monitor] — text only, only when online
        if (bot.isOnline() && !pending) {
            context.drawTextWithShadow(textRenderer, Text.literal("[Monitor]").formatted(Formatting.AQUA),
                    left + 8, btnY + (btnH - 8) / 2, 0x55FFFF);
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

            // Bot list area starts below header: title(y=20) + y+=20 + y+=28 (status row) + y+=6 (separator)
            int headerY = 20 + 20 + 28 + 6; // = 74
            if (statusMsg != null) headerY += 12;

            int botAreaBottom = this.height - 40;
            int maxVisible = (botAreaBottom - headerY) / BOT_ROW_HEIGHT;

            // ▲ up arrow click
            if (scrollOffset > 0) {
                String upArrow = "▲";
                int arrowX = centerX - textRenderer.getWidth(upArrow) / 2;
                if (mouseX >= arrowX - 4 && mouseX <= arrowX + textRenderer.getWidth(upArrow) + 4
                        && mouseY >= headerY - 11 && mouseY <= headerY - 1) {
                    scrollOffset--;
                    return true;
                }
            }

            // ▼ down arrow click
            if (scrollOffset + maxVisible < bots.size()) {
                int downArrowY = headerY + maxVisible * BOT_ROW_HEIGHT;
                String downArrow = "▼";
                int arrowX = centerX - textRenderer.getWidth(downArrow) / 2;
                if (mouseX >= arrowX - 4 && mouseX <= arrowX + textRenderer.getWidth(downArrow) + 4
                        && mouseY >= downArrowY + 1 && mouseY <= downArrowY + 11) {
                    scrollOffset++;
                    return true;
                }
            }

            int y = headerY;
            for (int i = scrollOffset; i < bots.size() && i < scrollOffset + maxVisible; i++) {
                int rowTop = y;
                int rowBottom = y + BOT_ROW_HEIGHT;

                if (mouseY >= rowTop && mouseY < rowBottom) {
                    BotInfo bot = bots.get(i);
                    int btnY = rowTop + 23;
                    int btnH = 16;

                    if (pendingBotIds.contains(bot.id)) return true; // ignore clicks while pending

                    // Spawn / Despawn filled button area
                    int primaryBtnW = 58;
                    int primaryBtnX = panelRight - primaryBtnW - 6;
                    if (mouseX >= primaryBtnX && mouseX <= primaryBtnX + primaryBtnW
                            && mouseY >= btnY && mouseY < btnY + btnH) {
                        if (bot.isOnline()) {
                            despawnBot(bot);
                        } else {
                            spawnBot(bot);
                        }
                        return true;
                    }

                    // [Edit] button area — text to the left of the primary button
                    int editW = textRenderer.getWidth("[Edit]");
                    int editX = primaryBtnX - editW - 6;
                    if (mouseX >= editX && mouseX <= editX + editW
                            && mouseY >= btnY && mouseY < btnY + btnH) {
                        client.setScreen(new BotConfigScreen(this, bot));
                        return true;
                    }

                    // [Delete] button area (only when offline)
                    int deleteW = textRenderer.getWidth("[Delete]");
                    if (!bot.isOnline() && mouseX >= panelLeft + 8 && mouseX <= panelLeft + 8 + deleteW
                            && mouseY >= btnY && mouseY < btnY + btnH) {
                        BrainApiClient.get().deleteBot(bot.id).thenRun(this::loadData)
                                .exceptionally(e -> {
                                    this.errorMsg = friendlyError(e);
                                    return null;
                                });
                        return true;
                    }

                    // [Monitor] button area (only when online)
                    int monitorW = textRenderer.getWidth("[Monitor]");
                    if (bot.isOnline() && mouseX >= panelLeft + 8 && mouseX <= panelLeft + 8 + monitorW
                            && mouseY >= btnY && mouseY < btnY + btnH) {
                        client.setScreen(new BotMonitorScreen(this, bot));
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
                            } else {
                                SpawnCheckScheduler.scheduleCheck(bot.id, bot.bot_name);
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
                        } else {
                            SpawnCheckScheduler.scheduleCheck(bot.id, bot.bot_name);
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
        if (lower.contains("fabric loader") || lower.contains("fabric api") || lower.contains("requires fabric"))
            return "Server requires Fabric Loader/API — bots can't join modded servers. Use a vanilla server.";
        if (lower.contains("name_taken") || lower.contains("name_collision") || lower.contains("name is already") || lower.contains("name already in use"))
            return "That name is already taken on the server. If you named the bot after yourself, that's why — rename and try again.";
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
