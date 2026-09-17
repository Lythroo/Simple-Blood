package com.bloodmod;

public enum BloodKind {
    LIQUID, DEBRIS, EMBER, POWDER;

    public static BloodKind parse(String s) {
        if (s == null) return LIQUID;
        switch (s.trim().toLowerCase()) {
            case "debris": return DEBRIS;
            case "ember": return EMBER;
            case "powder": case "snow": return POWDER;
            default: return LIQUID;
        }
    }

    public String key() {
        return name().toLowerCase();
    }

    public boolean paintsSurfaces() {
        return this == LIQUID || this == POWDER;
    }

    public boolean flows() {
        return this == LIQUID;
    }
}
