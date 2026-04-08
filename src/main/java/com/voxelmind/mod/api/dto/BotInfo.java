package com.voxelmind.mod.api.dto;

public class BotInfo {
    public String id;
    public String bot_name;
    public String personality_id;
    public String status;
    public String owner_player_name;

    public boolean isOnline() {
        return "online".equals(status) || "spawning".equals(status);
    }
}
