package com.voxelmind.mod.spawn;

import com.google.gson.JsonObject;
import com.voxelmind.mod.VoxelMindMod;
import com.voxelmind.mod.api.BrainApiClient;
import com.voxelmind.mod.command.VmCommand;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Schedules a deferred health-check after a bot spawn attempt.
 *
 * 12 seconds after spawn, polls GET /bots/:id/state. If the bot is not
 * "online" by then, an in-chat warning is shown with the most likely
 * cause (online-mode=true). A telemetry event is also fired.
 *
 * Design decisions:
 * - Uses a single shared ScheduledExecutorService with daemon threads so it
 *   doesn't prevent JVM exit.
 * - Failure-tolerant: any exception in the check is swallowed and logged
 *   at WARN level only — this must never crash the client.
 * - Does NOT touch the MC main thread directly; sends messages via
 *   client.execute() which the existing msg() pattern also uses.
 */
public class SpawnCheckScheduler {

    /** Delay before the deferred status check fires (milliseconds). */
    private static final long CHECK_DELAY_MS = 12_000;

    private static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "voxelmind-spawn-check");
                t.setDaemon(true);
                return t;
            });

    /**
     * Schedules a deferred online-check for the given bot.
     *
     * Call this immediately after a successful spawn API call returns (i.e. inside
     * the thenRun / whenComplete success path). The check fires once after
     * {@value CHECK_DELAY_MS} ms and is entirely self-contained — no cleanup needed.
     *
     * @param botId   Brain bot ID used for GET /bots/:id/state
     * @param botName Human-readable name for the chat message
     */
    public static ScheduledFuture<?> scheduleCheck(String botId, String botName) {
        return EXECUTOR.schedule(() -> runCheck(botId, botName), CHECK_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    // ─── Internal ───────────────────────────────────────────────────────────────

    private static void runCheck(String botId, String botName) {
        try {
            BrainApiClient.get().getBotState(botId).thenAccept(state -> {
                if (state == null) {
                    VoxelMindMod.LOGGER.warn("SpawnCheck [{}]: state response was null", botName);
                    return;
                }

                String status = state.status != null ? state.status : "unknown";

                if (!"online".equalsIgnoreCase(status)) {
                    VoxelMindMod.LOGGER.warn(
                            "SpawnCheck [{}]: bot status '{}' after {}ms — showing hint",
                            botName, status, CHECK_DELAY_MS);

                    sendHintToPlayer(botName, status);
                    sendTelemetry(botId, status);
                } else {
                    VoxelMindMod.LOGGER.info("SpawnCheck [{}]: bot is online — all good", botName);
                }
            }).exceptionally(e -> {
                // Brain unreachable or token expired — silent, never propagate to user
                VoxelMindMod.LOGGER.warn("SpawnCheck [{}]: state poll failed — {}", botName,
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                return null;
            });
        } catch (Exception e) {
            // Defensive outer catch — this method must never throw
            VoxelMindMod.LOGGER.warn("SpawnCheck [{}]: unexpected error — {}", botName, e.getMessage());
        }
    }

    private static void sendHintToPlayer(String botName, String lastStatus) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player == null) return;

            client.player.sendMessage(
                    Text.literal("").formatted(Formatting.WHITE)
                            .append(Text.literal("[VoxelMind] ").formatted(Formatting.DARK_GREEN))
                            .append(Text.literal("⚠ " + botName + " doesn't seem to be connected.")
                                    .formatted(Formatting.YELLOW)),
                    false);

            client.player.sendMessage(
                    Text.literal("").formatted(Formatting.WHITE)
                            .append(Text.literal("[VoxelMind] ").formatted(Formatting.DARK_GREEN))
                            .append(Text.literal("Most likely cause: server online-mode=true.")
                                    .formatted(Formatting.GOLD)),
                    false);

            client.player.sendMessage(
                    Text.literal("").formatted(Formatting.WHITE)
                            .append(Text.literal("[VoxelMind] ").formatted(Formatting.DARK_GREEN))
                            .append(Text.literal("Fix: set 'online-mode=false' in server.properties, or use a singleplayer world.")
                                    .formatted(Formatting.GRAY)),
                    false);

            client.player.sendMessage(
                    Text.literal("").formatted(Formatting.WHITE)
                            .append(Text.literal("[VoxelMind] ").formatted(Formatting.DARK_GREEN))
                            .append(Text.literal("More info: ")
                                    .formatted(Formatting.GRAY))
                            .append(Text.literal("https://voxel-mind.com/help/online-mode")
                                    .formatted(Formatting.AQUA)),
                    false);
        });
    }

    private static void sendTelemetry(String botId, String lastStatus) {
        try {
            JsonObject data = new JsonObject();
            data.addProperty("bot_id", botId);
            data.addProperty("last_status", lastStatus);
            data.addProperty("check_delay_ms", CHECK_DELAY_MS);
            data.add("status_snapshot", VmCommand.collectStatusSnapshot());

            BrainApiClient.get().sendTelemetry("bot_spawn_check_failed", "warn", data, botId);
        } catch (Exception e) {
            // Telemetry must never throw — already fire-and-forget in BrainApiClient,
            // but guard the snapshot collection too
            VoxelMindMod.LOGGER.warn("SpawnCheck telemetry error: {}", e.getMessage());
        }
    }
}
