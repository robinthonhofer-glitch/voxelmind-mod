package com.voxelmind.mod.gui;

import com.voxelmind.mod.api.BrainApiClient;
import com.voxelmind.mod.api.dto.BotInfo;
import com.voxelmind.mod.lan.LanManager;
import com.voxelmind.mod.tunnel.TunnelManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Shown after a bot is created. Summarises what the bot can do, then lets the user spawn it directly.
 */
public class BotCapabilitiesScreen extends Screen {

    private static final String[] CAPABILITY_ICONS  = {"\u2665", "\u2693", "\u2302", "\u2694", "\u25CE"};
    private static final String[] CAPABILITY_TEXTS  = {
            "Talks naturally \u2014 responds to chat, has a personality",
            "Gathers resources \u2014 wood, food, stone on request",
            "Builds shelters \u2014 basic structures to survive the night",
            "Fights back \u2014 defends itself from mobs",
            "Monitor it \u2014 see what it's doing in real time after spawning"
    };
    private static final Formatting[] ICON_COLORS = {
            Formatting.RED, Formatting.AQUA, Formatting.YELLOW, Formatting.WHITE, Formatting.GREEN
    };

    private final BotInfo bot;
    private boolean spawning = false;
    private String statusMsg = null;
    private String errorMsg  = null;

    public BotCapabilitiesScreen(BotInfo bot) {
        super(Text.literal("Bot Ready"));
        this.bot = bot;
    }

    @Override
    protected void init() {
        super.init();
        int bottomY = this.height - 30;
        int cx = this.width / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("\u2190 Back"),
                btn -> client.setScreen(new VoxelMindScreen(null)))
                .dimensions(cx - 120, bottomY, 80, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Spawn Bot \u25BA").formatted(Formatting.GREEN),
                btn -> spawnAndClose())
                .dimensions(cx + 10, bottomY, 110, 20).build());
    }

    private void spawnAndClose() {
        if (spawning) return;
        spawning = true;
        errorMsg = null;

        LanManager lan = LanManager.get();

        if (lan.isSingleplayer()) {
            if (!lan.isLanOpen()) {
                boolean opened = lan.openLan();
                if (!opened) {
                    errorMsg = "Failed to open LAN. Try saving the world first.";
                    spawning = false;
                    return;
                }
            }
            statusMsg = "Opening tunnel...";
            int localPort = lan.getActivePort();
            TunnelManager.get().openTunnel(bot.id, localPort).thenAccept(tunnel -> {
                statusMsg = "Spawning " + bot.bot_name + "...";
                BrainApiClient.get().spawnBot(bot.id, tunnel.getRelayHost(), tunnel.getTunnelPort())
                        .whenComplete((ok, err) -> {
                            spawning = false;
                            statusMsg = null;
                            if (err != null) {
                                errorMsg = friendlySpawnError(err);
                            } else {
                                client.execute(() -> client.setScreen(null));
                            }
                        });
            }).exceptionally(e -> {
                spawning = false;
                statusMsg = null;
                errorMsg = "Tunnel failed: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                return null;
            });
        } else {
            String address = lan.getServerAddress();
            if (address == null) {
                errorMsg = "No server detected.";
                spawning = false;
                return;
            }
            String host;
            int port;
            if (address.contains(":")) {
                String[] parts = address.split(":");
                host  = parts[0];
                port  = Integer.parseInt(parts[1]);
            } else {
                host = address;
                port = 25565;
            }
            statusMsg = "Spawning " + bot.bot_name + "...";
            BrainApiClient.get().spawnBot(bot.id, host, port)
                    .whenComplete((ok, err) -> {
                        spawning = false;
                        statusMsg = null;
                        if (err != null) {
                            errorMsg = friendlySpawnError(err);
                        } else {
                            client.execute(() -> client.setScreen(null));
                        }
                    });
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int y = 22;

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Bot Created!").formatted(Formatting.GREEN, Formatting.BOLD), cx, y, 0xFFFFFF);
        y += 16;

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("What can your bot do?").formatted(Formatting.WHITE), cx, y, 0xFFFFFF);
        y += 20;

        int panelW    = 280;
        int panelLeft = cx - panelW / 2;
        int panelRight = cx + panelW / 2;
        context.fill(panelLeft, y, panelRight, y + 1, 0xFF444444);
        y += 10;

        for (int i = 0; i < CAPABILITY_TEXTS.length; i++) {
            context.drawTextWithShadow(textRenderer,
                    Text.literal(CAPABILITY_ICONS[i]).formatted(ICON_COLORS[i]),
                    panelLeft + 4, y + 1, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer,
                    Text.literal(CAPABILITY_TEXTS[i]).formatted(Formatting.WHITE),
                    panelLeft + 18, y + 1, 0xFFFFFF);
            y += 18;
        }

        y += 6;
        context.fill(panelLeft, y, panelRight, y + 1, 0xFF444444);
        y += 10;

        if (statusMsg != null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(statusMsg).formatted(Formatting.YELLOW), cx, y, 0xFFFF55);
        } else if (errorMsg != null) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(errorMsg).formatted(Formatting.RED), cx, y, 0xFF5555);
        } else {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Ready to go! Hit Spawn Bot to start.").formatted(Formatting.WHITE), cx, y, 0xFFFFFF);
        }
    }

    private static String friendlySpawnError(Throwable e) {
        String raw = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        if (raw == null) return "Spawn failed: unknown error";
        String lower = raw.toLowerCase();
        if (lower.contains("connection refused"))   return "Connection refused \u2014 is the server running?";
        if (lower.contains("econnreset"))           return "Connection dropped \u2014 check online-mode=false";
        if (lower.contains("kick"))                 return "Bot was kicked \u2014 check online-mode=false";
        if (lower.contains("timeout"))              return "Timeout \u2014 server not reachable";
        if (lower.contains("no agent"))             return "VoxelMind server is starting up, try again.";
        if (lower.contains("out of sparks"))        return "Out of sparks. Get more from Pricing.";
        return "Spawn failed: " + raw;
    }

    @Override
    public void close() {
        client.setScreen(new VoxelMindScreen(null));
    }
}
