package com.voxelmind.mod.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.voxelmind.mod.api.BrainApiClient;
import com.voxelmind.mod.api.dto.BotInfo;
import com.voxelmind.mod.auth.AuthManager;
import com.voxelmind.mod.config.ModConfig;
import com.voxelmind.mod.gui.VoxelMindScreen;
import com.voxelmind.mod.lan.LanManager;
import com.voxelmind.mod.tunnel.TunnelManager;
import com.voxelmind.mod.tunnel.TunnelStatus;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.net.URI;

/**
 * Client-side /vm command tree.
 */
public class VmCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("vm")
                    // /vm — open GUI
                    .executes(ctx -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        client.execute(() -> client.setScreen(new VoxelMindScreen(null)));
                        return 1;
                    })
                    // /vm login
                    .then(ClientCommandManager.literal("login").executes(ctx -> {
                        AuthManager.get().startLogin();
                        msg(ctx.getSource(), "Opening browser for login...", Formatting.YELLOW);
                        return 1;
                    }))
                    // /vm logout
                    .then(ClientCommandManager.literal("logout").executes(ctx -> {
                        AuthManager.get().logout();
                        msg(ctx.getSource(), "Logged out.", Formatting.GRAY);
                        return 1;
                    }))
                    // /vm list
                    .then(ClientCommandManager.literal("list").executes(ctx -> {
                        if (!requireLogin(ctx.getSource())) return 0;
                        BrainApiClient.get().listBots().thenAccept(bots -> {
                            if (bots.isEmpty()) {
                                msg(ctx.getSource(), "No bots. Create one with /vm or the GUI.", Formatting.GRAY);
                                return;
                            }
                            msg(ctx.getSource(), "--- Your Bots ---", Formatting.GREEN);
                            for (BotInfo bot : bots) {
                                Formatting color = bot.isOnline() ? Formatting.GREEN : Formatting.GRAY;
                                msg(ctx.getSource(), String.format("  %s (%s) [%s]",
                                        bot.bot_name, bot.personality_id, bot.status), color);
                            }
                        }).exceptionally(e -> {
                            error(ctx.getSource(), e);
                            return null;
                        });
                        return 1;
                    }))
                    // /vm spawn <name>
                    .then(ClientCommandManager.literal("spawn")
                            .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                    .executes(ctx -> {
                                        if (!requireLogin(ctx.getSource())) return 0;
                                        String name = StringArgumentType.getString(ctx, "name");
                                        spawnByName(ctx.getSource(), name);
                                        return 1;
                                    })))
                    // /vm stop <name>
                    .then(ClientCommandManager.literal("stop")
                            .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                    .executes(ctx -> {
                                        if (!requireLogin(ctx.getSource())) return 0;
                                        String name = StringArgumentType.getString(ctx, "name");
                                        despawnByName(ctx.getSource(), name);
                                        return 1;
                                    })))
                    // /vm stopall
                    .then(ClientCommandManager.literal("stopall").executes(ctx -> {
                        if (!requireLogin(ctx.getSource())) return 0;
                        BrainApiClient.get().listBots().thenAccept(bots -> {
                            int count = 0;
                            for (BotInfo bot : bots) {
                                if (bot.isOnline()) {
                                    BrainApiClient.get().despawnBot(bot.id);
                                    count++;
                                }
                            }
                            msg(ctx.getSource(), "Despawning " + count + " bot(s)...", Formatting.YELLOW);
                        }).exceptionally(e -> {
                            error(ctx.getSource(), e);
                            return null;
                        });
                        return 1;
                    }))
                    // /vm recharge — open pricing page in browser
                    .then(ClientCommandManager.literal("recharge").executes(ctx -> {
                        openBrowser(ctx.getSource(), "https://voxel-mind.com/pricing");
                        return 1;
                    }))
                    // /vm account — open account page in browser
                    .then(ClientCommandManager.literal("account").executes(ctx -> {
                        openBrowser(ctx.getSource(), "https://voxel-mind.com/account");
                        return 1;
                    }))
                    // /vm feedback — open feedback screen
                    .then(ClientCommandManager.literal("feedback").executes(ctx -> {
                        if (!requireLogin(ctx.getSource())) return 0;
                        MinecraftClient client = MinecraftClient.getInstance();
                        client.execute(() -> client.setScreen(
                                new com.voxelmind.mod.gui.FeedbackScreen(null, null)));
                        return 1;
                    }))
                    // /vm status
                    .then(ClientCommandManager.literal("status").executes(ctx -> {
                        LanManager lan = LanManager.get();
                        msg(ctx.getSource(), "--- VoxelMind Status ---", Formatting.GREEN);
                        msg(ctx.getSource(), "  Login: " + (ModConfig.isLoggedIn() ? "Yes" : "No"),
                                ModConfig.isLoggedIn() ? Formatting.GREEN : Formatting.RED);
                        msg(ctx.getSource(), "  Brain: " + ModConfig.getBrainUrl(), Formatting.GRAY);
                        msg(ctx.getSource(), "  Relay: " + ModConfig.getRelayUrl(), Formatting.GRAY);
                        msg(ctx.getSource(), "  LAN: " + (lan.isLanOpen()
                                ? "Open (port " + lan.getActivePort() + ")"
                                : "Closed"), Formatting.GRAY);
                        if (lan.getServerAddress() != null) {
                            msg(ctx.getSource(), "  Server: " + lan.getServerAddress(), Formatting.AQUA);
                        }

                        // Tunnel status
                        TunnelStatus tunnelStatus = TunnelManager.get().getOverallStatus();
                        Formatting tunnelColor = switch (tunnelStatus) {
                            case READY -> Formatting.GREEN;
                            case CONNECTING, AUTHENTICATING -> Formatting.YELLOW;
                            case ERROR -> Formatting.RED;
                            default -> Formatting.GRAY;
                        };
                        msg(ctx.getSource(), "  Tunnel: " + tunnelStatus.name(), tunnelColor);

                        if (ModConfig.isLoggedIn()) {
                            BrainApiClient.get().getAgentStatus().thenAccept(status -> {
                                msg(ctx.getSource(), "  Agent: " + (status.connected ? "Connected" : "Disconnected"),
                                        status.connected ? Formatting.GREEN : Formatting.RED);
                            }).exceptionally(e -> {
                                msg(ctx.getSource(), "  Agent: Unknown (Brain unreachable)", Formatting.RED);
                                return null;
                            });
                        }
                        return 1;
                    }))
            );
        });
    }

    // ─── Helpers ───

    private static void spawnByName(Object source, String name) {
        LanManager lan = LanManager.get();

        if (lan.isSingleplayer()) {
            // Singleplayer: ensure LAN open, then tunnel
            if (!lan.isLanOpen()) {
                msg(source, "Opening LAN...", Formatting.YELLOW);
                boolean opened = lan.openLan();
                if (!opened) {
                    msg(source, "Failed to open LAN.", Formatting.RED);
                    return;
                }
            }

            // Find the bot first, then open tunnel
            BrainApiClient.get().listBots().thenAccept(bots -> {
                BotInfo found = null;
                for (BotInfo bot : bots) {
                    if (bot.bot_name.equalsIgnoreCase(name)) {
                        found = bot;
                        break;
                    }
                }
                if (found == null) {
                    msg(source, "Bot '" + name + "' not found. Use /vm list.", Formatting.RED);
                    return;
                }
                final BotInfo target = found;
                if (target.isOnline()) {
                    msg(source, target.bot_name + " is already online.", Formatting.YELLOW);
                    return;
                }

                // Telemetry: snapshot what the client sees at spawn time
                try {
                    JsonObject telemetryData = new JsonObject();
                    telemetryData.addProperty("bot_name", target.bot_name);
                    telemetryData.addProperty("mode", "singleplayer_tunnel");
                    telemetryData.add("status_snapshot", collectStatusSnapshot());
                    BrainApiClient.get().sendTelemetry("spawn_attempt", "info", telemetryData, target.id);
                } catch (Exception ignored) {}

                msg(source, "Opening tunnel for " + target.bot_name + "...", Formatting.YELLOW);
                int localPort = lan.getActivePort();
                TunnelManager.get().openTunnel(target.id, localPort).thenAccept(tunnel -> {
                    msg(source, "Spawning " + target.bot_name + " via tunnel...", Formatting.YELLOW);
                    BrainApiClient.get().spawnBot(target.id, tunnel.getRelayHost(), tunnel.getTunnelPort()).thenRun(() -> {
                        msg(source, target.bot_name + " is joining!", Formatting.GREEN);
                    }).exceptionally(e -> {
                        error(source, e);
                        return null;
                    });
                }).exceptionally(e -> {
                    msg(source, "Tunnel failed: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), Formatting.RED);
                    return null;
                });
            }).exceptionally(e -> {
                error(source, e);
                return null;
            });
        } else {
            // Multiplayer: direct spawn (no tunnel)
            String address = lan.getServerAddress();
            if (address == null) {
                msg(source, "Cannot detect server address.", Formatting.RED);
                return;
            }

            BrainApiClient.get().listBots().thenAccept(bots -> {
                BotInfo found = null;
                for (BotInfo bot : bots) {
                    if (bot.bot_name.equalsIgnoreCase(name)) {
                        found = bot;
                        break;
                    }
                }
                if (found == null) {
                    msg(source, "Bot '" + name + "' not found. Use /vm list.", Formatting.RED);
                    return;
                }
                final BotInfo target = found;
                if (target.isOnline()) {
                    msg(source, target.bot_name + " is already online.", Formatting.YELLOW);
                    return;
                }

                // Telemetry: snapshot what the client sees at spawn time
                try {
                    JsonObject telemetryData = new JsonObject();
                    telemetryData.addProperty("bot_name", target.bot_name);
                    telemetryData.addProperty("mode", "multiplayer_direct");
                    telemetryData.add("status_snapshot", collectStatusSnapshot());
                    BrainApiClient.get().sendTelemetry("spawn_attempt", "info", telemetryData, target.id);
                } catch (Exception ignored) {}

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

                msg(source, "Spawning " + target.bot_name + " on " + address + "...", Formatting.YELLOW);
                BrainApiClient.get().spawnBot(target.id, host, port).thenRun(() -> {
                    msg(source, target.bot_name + " is joining!", Formatting.GREEN);
                }).exceptionally(e -> {
                    error(source, e);
                    return null;
                });
            }).exceptionally(e -> {
                error(source, e);
                return null;
            });
        }
    }

    private static void despawnByName(Object source, String name) {
        BrainApiClient.get().listBots().thenAccept(bots -> {
            BotInfo found = null;
            for (BotInfo bot : bots) {
                if (bot.bot_name.equalsIgnoreCase(name)) {
                    found = bot;
                    break;
                }
            }
            if (found == null) {
                msg(source, "Bot '" + name + "' not found.", Formatting.RED);
                return;
            }
            final BotInfo target = found;
            if (!target.isOnline()) {
                msg(source, target.bot_name + " is already offline.", Formatting.GRAY);
                return;
            }

            msg(source, "Despawning " + target.bot_name + "...", Formatting.YELLOW);
            BrainApiClient.get().despawnBot(target.id).thenRun(() -> {
                msg(source, target.bot_name + " despawned.", Formatting.GREEN);
                // Close tunnel for this bot
                TunnelManager.get().closeTunnel(target.id);
            }).exceptionally(e -> {
                error(source, e);
                return null;
            });
        }).exceptionally(e -> {
            error(source, e);
            return null;
        });
    }

    private static boolean requireLogin(Object source) {
        if (!ModConfig.isLoggedIn()) {
            msg(source, "Not logged in. Use /vm login first.", Formatting.RED);
            return false;
        }
        return true;
    }

    private static void msg(Object source, String text, Formatting color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.execute(() -> client.player.sendMessage(
                    Text.literal("[VoxelMind] ").formatted(Formatting.DARK_GREEN)
                            .append(Text.literal(text).formatted(color)),
                    false));
        }
    }

    private static void openBrowser(Object source, String url) {
        try {
            Util.getOperatingSystem().open(URI.create(url));
            msg(source, "Opening " + url, Formatting.AQUA);
        } catch (Exception e) {
            msg(source, "Could not open browser. Visit: " + url, Formatting.RED);
        }
    }

    private static void error(Object source, Throwable e) {
        String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        msg(source, message != null ? message : "Unknown error", Formatting.RED);
    }

    /**
     * Collects the same state data shown by /vm status as a JsonObject.
     * Re-used by both /vm status display and spawn_attempt telemetry,
     * so both always produce the same structure.
     *
     * Note: agent-connected is NOT included here because it requires an async
     * Brain call — including it would either block or require a callback.
     * The Brain itself knows agent status better than we do anyway.
     */
    public static JsonObject collectStatusSnapshot() {
        LanManager lan = LanManager.get();
        TunnelStatus tunnelStatus = TunnelManager.get().getOverallStatus();

        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("logged_in", ModConfig.isLoggedIn());
        snapshot.addProperty("brain_url", ModConfig.getBrainUrl());
        snapshot.addProperty("relay_url", ModConfig.getRelayUrl());
        snapshot.addProperty("lan_open", lan.isLanOpen());
        snapshot.addProperty("lan_port", lan.getActivePort());
        snapshot.addProperty("server_address", lan.getServerAddress() != null ? lan.getServerAddress() : "");
        snapshot.addProperty("tunnel_status", tunnelStatus.name());
        snapshot.addProperty("is_singleplayer", lan.isSingleplayer());
        return snapshot;
    }
}
