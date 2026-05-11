package com.voxelmind.mod.api.dto;

public class BotInfo {
    public String id;
    public String bot_name;
    public String personality_id;
    public String status;
    public String owner_player_name;
    /** Chat channel policy: "public", "whisper", or "mixed". Default "public". */
    public String chat_mode;

    public boolean isOnline() {
        return "online".equals(status) || "spawning".equals(status);
    }

    /** Returns the resolved chat mode, defaulting to "public" if null or unrecognised. */
    public String getChatMode() {
        if ("whisper".equals(chat_mode) || "mixed".equals(chat_mode)) return chat_mode;
        return "public";
    }
}
