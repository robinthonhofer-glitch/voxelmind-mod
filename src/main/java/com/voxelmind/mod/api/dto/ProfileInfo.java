package com.voxelmind.mod.api.dto;

/**
 * User profile returned by GET /profile on the Brain.
 * Used for sparks display, account info, and MC username sync.
 */
public class ProfileInfo {
    public String email;
    public String display_name;
    public String role;
    public String subscription_tier;
    public int sparks_remaining;
    public String mc_username;

    /** Maximum sparks for the current tier — used only for the progress bar UI. */
    public int maxSparksForTier() {
        if (subscription_tier == null) return 500;
        return switch (subscription_tier) {
            case "starter" -> 3000;
            case "pro" -> 10000;
            case "ultra" -> 30000;
            default -> 500; // free
        };
    }
}
