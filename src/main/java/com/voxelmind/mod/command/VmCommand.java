package com.voxelmind.mod.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.voxelmind.mod.api.BrainApiClient;
import com.voxelmind.mod.api.dto.BotInfo;
import com.voxelmind.mod.auth.AuthManager;
import com.voxelmind.mod.config.ModConfig;
import com.voxelmind.mod.gui.VoxelMindScreen;
import com.voxelmind.mod.lan.LanManager;
import com.voxelmind.mod.lan.UpnpManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
                    // /vm status
                    .then(ClientCommandManager.literal("status").executes(ctx -> {
                        LanManager lan = LanManager.get();
                        msg(ctx.getSource(), "--- VoxelMind Status ---", Formatting.GREEN);
                        msg(ctx.getSource(), "  Login: " + (ModConfig.isLoggedIn() ? "Yes" : "No"),
                                ModConfig.isLoggedIn() ? Formatting.GREEN : Formatting.RED);
                        msg(ctx.getSource(), "  Brain: " + ModConfig.getBrainUrl(), Formatting.GRAY);
                        msg(ctx.getSource(), "  LAN: " + (lan.isLanOpen()
                                ? "Open (port " + lan.getActivePort() + ")"
                                : "Closed"), Formatting.GRAY);
                        if (lan.getServerAddress() != null) {
                            msg(ctx.getSource(), "  Server: " + lan.getServerAddress(), Formatting.AQUA);
                        }
                        msg(ctx.getSource(), "  UPnP: " + (UpnpManager.isAvailable() ? "Available" : "Not available"), Formatting.GRAY);

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
                    // /vm lan
                    .then(ClientCommandManager.literal("lan").executes(ctx -> {
                        LanManager lan = LanManager.get();
                        if (lan.isLanOpen()) {
                            msg(ctx.getSource(), "LAN already open on port " + lan.getActivePort(), Formatting.YELLOW);
                        } else if (lan.isSingleplayer()) {
                            boolean success = lan.openLan();
                            if (success) {
                                msg(ctx.getSource(), "LAN opened on port " + lan.getActivePort(), Formatting.GREEN);
                                if (lan.getServerAddress() != null) {
                                    msg(ctx.getSource(), "Server address: " + lan.getServerAddress(), Formatting.AQUA);
                                }
                            } else {
                                msg(ctx.getSource(), "Failed to open LAN.", Formatting.RED);
                            }
                        } else {
                            msg(ctx.getSource(), "LAN only available in singleplayer.", Formatting.RED);
                        }
                        return 1;
                    }))
            );
        });
    }

    // ─── Helpers ───

    private static void spawnByName(Object source, String name) {
        LanManager lan = LanManager.get();
        String address = lan.getServerAddress();

        if (address == null) {
            if (lan.isSingleplayer() && !lan.isLanOpen()) {
                msg(source, "Open LAN first: /vm lan", Formatting.RED);
            } else {
                msg(source, "Cannot detect server address.", Formatting.RED);
            }
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

            // Parse host:port from address
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

    private static void error(Object source, Throwable e) {
        String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        msg(source, message != null ? message : "Unknown error", Formatting.RED);
    }
}
