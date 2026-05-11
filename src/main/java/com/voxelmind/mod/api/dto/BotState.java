package com.voxelmind.mod.api.dto;

/**
 * Maps to BotLiveState from the Brain API (GET /bots/:id/state).
 */
public class BotState {
    public String botId;
    public String botName;
    public String status;   // "offline" | "spawning" | "online" | "error"
    /** Last disconnect reason, e.g. "unsupported_server" when a Fabric/modded server kicked the bot. */
    public String disconnectReason;
    public double health;
    public int maxHealth;
    public int food;
    public Position position;
    public String botState;          // "IDLE" | "WORKING"
    public ActiveTask activeTask;
    public CurrentCommand currentCommand;
    public String environment;

    public static class Position {
        public double x;
        public double y;
        public double z;
    }

    public static class ActiveTask {
        public String name;
        public String status;
    }

    public static class CurrentCommand {
        public String name;
    }

    /** Derives a human-readable activity string from available fields. */
    public String getActivity() {
        if (activeTask != null && activeTask.name != null && !activeTask.name.isBlank()) {
            return "Task: " + activeTask.name;
        }
        if (currentCommand != null && currentCommand.name != null && !currentCommand.name.isBlank()) {
            return "Command: " + currentCommand.name;
        }
        return "Idle";
    }
}
