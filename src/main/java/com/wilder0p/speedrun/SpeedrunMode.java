package com.wilder0p.speedrun;

import java.util.Locale;

public enum SpeedrunMode {
    CLASSIC("classic", "Any%"),
    HORROR("horror", "Horror Any%");

    private final String id;
    private final String display;

    SpeedrunMode(String id, String display) {
        this.id = id;
        this.display = display;
    }

    public String id() { return id; }
    public String display() { return display; }

    public static SpeedrunMode fromArg(String raw) {
        if (raw == null || raw.isBlank()) return CLASSIC;
        String s = raw.toLowerCase(Locale.ROOT);
        for (SpeedrunMode mode : values()) {
            if (mode.id.equals(s) || mode.name().equalsIgnoreCase(s)) return mode;
        }
        return null;
    }
}
