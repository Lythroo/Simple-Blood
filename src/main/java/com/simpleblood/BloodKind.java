package com.simpleblood;

public enum BloodKind {
    LIQUID, DEBRIS, EMBER, POWDER, BONE, METAL, WOOD, SPIRIT, WIND;

    public static BloodKind parse(String s) {
        if (s == null) return LIQUID;
        switch (s.trim().toLowerCase()) {
            case "debris": return DEBRIS;
            case "ember": return EMBER;
            case "powder": case "snow": return POWDER;
            case "bone": return BONE;
            case "metal": return METAL;
            case "wood": return WOOD;
            case "spirit": return SPIRIT;
            case "wind": return WIND;
            default: return LIQUID;
        }
    }

    public String key() {
        return name().toLowerCase();
    }

    public boolean paintsSurfaces() {
        return this == LIQUID || this == POWDER;
    }

    public boolean hasPieces() {
        return this == EMBER || this == BONE || this == METAL || this == WOOD || this == SPIRIT || this == WIND;
    }

    public boolean flows() {
        return this == LIQUID;
    }
}
