package com.simpleblood;

/**
 * What comes out of an entity when it is hurt. The kind decides which effects apply:
 *
 * <ul>
 *   <li>{@link #LIQUID}: real blood (or ink, slime, sap...). Puddles, flow, runs, fog in
 *       water, sweeping streaks and slam craters.</li>
 *   <li>{@link #DEBRIS}: solid bits (bone dust, iron flakes, wood splinters). Chunks fly and
 *       come to rest, nothing pools, nothing runs, nothing clouds the water.</li>
 *   <li>{@link #EMBER}: fire creatures (blaze, magma cube). Embers that leave white-hot,
 *       flicker, cool to ash and hiss out in water, with the odd wisp of smoke.</li>
 *   <li>{@link #POWDER}: snow. Leaves flat piles on surfaces that never flow and melt away;
 *       particles vanish in water.</li>
 *   <li>{@link #BONE}: skeletons. Puffs of bone dust and little bone chips that spin, bounce
 *       and settle.</li>
 *   <li>{@link #METAL}: golems. Flakes that tumble, catch the light and clink as they land.</li>
 *   <li>{@link #WOOD}: the creaking. Splinters that tumble and lie where they land, with
 *       drops of resin (in the mob's colour) that pool like blood.</li>
 *   <li>{@link #SPIRIT}: allays, vexes. Glowing sparks that twinkle and float away.</li>
 *   <li>{@link #WIND}: breezes. Little gusts that curl open and blow upward.</li>
 * </ul>
 */
public enum BloodKind {
    LIQUID, DEBRIS, EMBER, POWDER, BONE, METAL, WOOD, SPIRIT, WIND;

    /** Config-file spelling ("liquid", "debris", ..., "wood", "spirit", "wind"); unknown = liquid. */
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

    /** Whether this kind paints surfaces at all. */
    public boolean paintsSurfaces() {
        return this == LIQUID || this == POWDER;
    }

    /** Whether this kind throws its own particles (embers, chips, flakes, splinters, sparks, gusts) instead of drops. */
    public boolean hasPieces() {
        return this == EMBER || this == BONE || this == METAL || this == WOOD || this == SPIRIT || this == WIND;
    }

    /** Whether the painted pixels behave as a liquid (flow, runs, rim shading, ripples). */
    public boolean flows() {
        return this == LIQUID;
    }
}
