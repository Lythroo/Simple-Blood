package com.simpleblood.surface;

import com.simpleblood.SimpleBloodClient;
import com.simpleblood.SimpleBloodConfig;
import com.simpleblood.BloodSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Bloody footprints. Anything that walks through fresh blood wets its feet and leaves a
 * trail of prints that fade out over the next few steps. Feet are pixel-art shapes on the
 * canvas (a 3x4 sole for people, a 3x3 hoof for cattle, a 2x2 dab for small animals), turned
 * to one of eight directions by rasterising the sole onto the pixel grid - never rotated
 * smoothly. Prints are a thin film that never flows, so they cannot start runs or puddles.
 *
 * A print is laid the moment a foot comes down, read straight off the entity's own walk
 * animation: vanilla swings every leg as {@code cos(walkPosition * 0.6662) * 1.4 * speed}, so
 * the foot lands where the leg reaches its forward extreme. That gives every creature its
 * true cadence - a strolling villager, a sprinting player and a baby zombie (whose animation
 * runs three times faster) all print at their own rhythm, about one print per 1.2 blocks for
 * anything walking on two legs and a diagonal pair at a time for anything on four.
 */
public final class Footprints {

    private Footprints() {}

    /** Per-entity walking state: where it was, how wet its feet are, which step it is on. */
    private static final class Feet {
        double lastX, lastZ;
        int lastStep = Integer.MIN_VALUE; // index of the last footfall seen in the walk cycle
        float wet;
        int colour;
        boolean powder;
        int lastSeen;
        int lastSquelch = -100;
    }

    private static final Map<Integer, Feet> FEET = new HashMap<>();
    private static ClientLevel trackedLevel;

    /** Vanilla's leg swing: phase per unit of walk-animation position. */
    static final float LEG_SWING = 0.6662f;
    /** ... and the iron golem's triangle-wave gait, half a period per footfall. */
    private static final float GOLEM_SWING = (float) (Math.PI / 6.5);

    /** Foot shape in canvas pixels: width across the step, length along it. */
    record Sole(int width, int length) {}

    /**
     * How a body walks: its sole, how many legs, where the feet sit relative to the body
     * (across and along, in blocks) and how far a leg reaches when it swings.
     */
    record Gait(Sole sole, boolean quadruped, double side, double hind, double front, double legLength, float swing) {}

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (level == null || cfg == null) {
            FEET.clear();
            return;
        }
        if (level != trackedLevel) {
            FEET.clear();
            trackedLevel = level;
        }
        if (!cfg.surfaces.enabled || !cfg.surfaces.footprints || BloodSurfaces.tileCount() == 0 && FEET.isEmpty()) {
            if (!FEET.isEmpty()) FEET.clear();
            return;
        }
        int now = BloodSurfaces.now();
        double range = cfg.surfaces.renderDistance;
        Vec3 eye = mc.player != null ? mc.player.position() : Vec3.ZERO;

        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity entity) || entity.isRemoved()) continue;
            if (entity.isPassenger() || entity.isSpectator() || entity.isNoGravity()) continue;
            if (!com.simpleblood.SimpleBlood.leavesFootprints(entity)) continue;
            if (entity.distanceToSqr(eye) > range * range) continue;
            step(level, entity, cfg, now);
        }

        // Forget entities that went away.
        if ((now & 63) == 0) {
            Iterator<Feet> it = FEET.values().iterator();
            while (it.hasNext()) {
                if (now - it.next().lastSeen > 100) it.remove();
            }
        }
    }

    private static void step(ClientLevel level, LivingEntity entity, SimpleBloodConfig cfg, int now) {
        Feet feet = FEET.get(entity.getId());
        double x = entity.getX(), z = entity.getZ();
        if (feet == null) {
            feet = new Feet();
            feet.lastX = x;
            feet.lastZ = z;
            FEET.put(entity.getId(), feet);
        }
        feet.lastSeen = now;
        double dx = x - feet.lastX, dz = z - feet.lastZ;
        feet.lastX = x;
        feet.lastZ = z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        Gait gait = gaitFor(entity, cfg.surfaces.resolution == 16 ? 16 : 8);

        // Where in the walk cycle the legs are: a footfall is every half period. The first
        // sighting (and a teleport) only records the phase, so nothing prints out of thin air.
        int stepIndex = (int) Math.floor(entity.walkAnimation.position() * gait.swing() / Math.PI);
        if (feet.lastStep == Integer.MIN_VALUE || dist > 4.0) {
            feet.lastStep = stepIndex;
            return;
        }
        if (stepIndex == feet.lastStep) return;
        feet.lastStep = stepIndex;
        if (!entity.onGround() || dist < 0.002) return;
        if (entity.isInWater() || entity.isInLava()) {
            feet.wet = 0; // washed (or burnt) off
            return;
        }

        // Legs swing along the body, whichever way it is actually moving (strafing included).
        float yaw = (float) Math.toRadians(entity.yBodyRot);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // forward
        double rx = -fz, rz = fx;                         // the body's right-hand side
        boolean right = (stepIndex & 1) != 0;             // odd half-periods: the right leg is forward
        double reach = reach(gait, entity.walkAnimation.speed());
        List<Vec3> down = new ArrayList<>(2);
        if (gait.quadruped()) {
            // Diagonal pairs move together: right hind with left front, then the other two.
            double s = gait.side();
            down.add(new Vec3(x + rx * (right ? s : -s) - fx * gait.hind() + fx * reach, entity.getY(), z + rz * (right ? s : -s) - fz * gait.hind() + fz * reach));
            down.add(new Vec3(x + rx * (right ? -s : s) + fx * gait.front() + fx * reach, entity.getY(), z + rz * (right ? -s : s) + fz * gait.front() + fz * reach));
        } else {
            double s = right ? gait.side() : -gait.side();
            down.add(new Vec3(x + rx * s + fx * reach, entity.getY(), z + rz * s + fz * reach));
        }

        int res = cfg.surfaces.resolution == 16 ? 16 : 8;
        double ps = 1.0 / res;
        Map<BlockPos, CanvasTile> cache = new HashMap<>(4);
        List<int[]> cells = rasterise(gait.sole(), fx, fz, 0);
        List<int[]> around = rasterise(gait.sole(), fx, fz, 1);
        RandomSource rng = level.getRandom();

        for (Vec3 foot : down) {
            // 1. Is there fresh blood under the foot? Then the foot gets wet (and takes a little).
            int found = 0, taken = 0, colour = 0;
            int powderCount = 0;
            float ageSum = 0;
            for (int[] c : around) {
                Vec3 p = foot.add(c[0] * ps, 0, c[1] * ps);
                CanvasTile t = topTile(level, p, cache, false);
                if (t == null) continue;
                int px = t.pixelX(hitPoint(t, p)), py = t.pixelY(hitPoint(t, p));
                int d = t.wetDepthAt(px, py, now);
                if (d <= 0) continue;
                if (found == 0) colour = t.colourAt(px, py);
                found++;
                if (t.powderAt(px, py)) powderCount++;
                ageSum += t.ageAt(px, py, now);
                if (taken < 2 && d >= 2 && t.scoop(px, py)) taken++;
            }
            if (found > 0) {
                float max = cfg.surfaces.footprintSteps;
                float before = feet.wet;
                feet.wet = Math.min(max, feet.wet + Math.min(max, found * 0.75f));
                feet.colour = colour;
                feet.powder = powderCount * 2 > found;
                if (feet.wet > before + 0.5f && now - feet.lastSquelch > 8 && found >= 2) {
                    feet.lastSquelch = now;
                    // Blood stops splashing well before it stops sticking: past a third of its
                    // life it tacks underfoot instead.
                    BloodSounds.squelch(level, foot, Math.min(1f, found / 6f), ageSum / found > 0.3f, feet.powder);
                }
                continue; // no print inside the puddle itself
            }
            if (feet.wet <= 0) continue;

            // 2. Dry ground under a wet foot: leave a print, fainter with every step.
            float max = cfg.surfaces.footprintSteps;
            float strength = Math.min(1f, feet.wet / max);
            feet.wet -= gait.quadruped() ? 0.5f : 1f; // four feet share the same trail length
            int lifetime = Math.max(40, cfg.surfaces.lifetimeSeconds * 20 / 2);
            boolean pattern = !"block".equals(cfg.surfaces.highlightMode);
            for (int[] c : fade(cells, strength, rng)) {
                Vec3 p = foot.add(c[0] * ps, 0, c[1] * ps);
                CanvasTile t = topTile(level, p, cache, true);
                if (t == null) continue;
                Vec3 hp = hitPoint(t, p);
                t.paintPrint(t.pixelX(hp), t.pixelY(hp), feet.colour, now, lifetime, rng, pattern, feet.powder);
            }
        }
    }

    /**
     * How far ahead of the hip a foot comes down: the leg's forward extreme is
     * {@code 1.4 * swingAmount} radians, and the model's feet slide back during the stance,
     * so the print sits about halfway along the visible reach.
     */
    static double reach(Gait gait, float swingAmount) {
        double angle = Math.min(1.4, 1.4 * Math.max(0f, swingAmount));
        return Math.max(0.04, Math.min(0.45, gait.legLength() * Math.sin(angle) * 0.5));
    }

    /** The cells of a print at this strength: random ones drop out as the foot dries, the middle lasts longest. */
    static List<int[]> fade(List<int[]> cells, float strength, RandomSource rng) {
        int want = Math.max(1, Math.round(cells.size() * Math.min(1f, strength)));
        List<int[]> chosen = new ArrayList<>(cells);
        while (chosen.size() > want) {
            int worst = 0;
            double worstScore = -1;
            for (int i = 0; i < chosen.size(); i++) {
                int[] c = chosen.get(i);
                double score = (c[0] * c[0] + c[1] * c[1]) * (0.6 + rng.nextDouble());
                if (score > worstScore) { worstScore = score; worst = i; }
            }
            chosen.remove(worst);
        }
        return chosen;
    }

    /**
     * Body plan by bounding box. People and other tall, narrow things walk on two legs
     * (humanoid legs sit 1.9 px either side of the middle and are 12 px long); wide or low
     * bodies are taken for quadrupeds with the legs near the body's corners; tiny things get
     * a dab. Soles are sized for 16 px per block and halved for 8.
     */
    static Gait gaitFor(LivingEntity entity, int res) {
        float w = entity.getBbWidth(), h = entity.getBbHeight();
        Gait g;
        if (entity instanceof Player || (w <= 0.75f && h >= 1.4f)) {
            g = new Gait(new Sole(3, 4), false, 0.12, 0, 0, 0.75, LEG_SWING);
        } else if (w < 0.45f) {
            g = new Gait(new Sole(2, 2), false, w * 0.2, 0, 0, h * 0.4, LEG_SWING);
        } else if (w >= 1.6f || (w >= 0.85f && h < 2.0f) || h < 1.4f) {
            Sole sole = w < 0.7f ? new Sole(2, 2) : w < 1.2f ? new Sole(3, 3) : new Sole(4, 4);
            g = new Gait(sole, true, w * 0.28, w * 0.42, w * 0.38, h * 0.5, LEG_SWING);
        } else {
            // Big bipeds: iron golems, wardens. The golem stomps to a slower triangle wave.
            boolean golem = w >= 1.2f && h >= 2.4f;
            g = new Gait(new Sole(4, 5), false, w * 0.22, 0, 0, h * 0.4, golem ? GOLEM_SWING : LEG_SWING);
        }
        if (res < 16) {
            Sole s = g.sole();
            g = new Gait(new Sole(Math.max(1, (s.width() + 1) / 2), Math.max(1, (s.length() + 1) / 2)),
                    g.quadruped(), g.side(), g.hind(), g.front(), g.legLength(), g.swing());
        }
        return g;
    }

    /** The gait of a plain humanoid (the settings-screen zombie). */
    static Gait humanoid(int res) {
        Sole s = res < 16 ? new Sole(2, 2) : new Sole(3, 4);
        return new Gait(s, false, 0.12, 0, 0, 0.75, LEG_SWING);
    }

    /**
     * Pixel cells of a sole pointing along (fx, fz), snapped to the nearest of eight
     * directions and rasterised onto the grid: straight soles are plain rectangles, diagonal
     * ones the classic staircase. {@code grow} widens the shape by that many pixels (for
     * sampling the ground under and around the foot).
     */
    static List<int[]> rasterise(Sole sole, double fx, double fz, int grow) {
        // Snap to 8 directions.
        int sx = Math.abs(fx) < 0.3827 ? 0 : (fx > 0 ? 1 : -1);
        int sz = Math.abs(fz) < 0.3827 ? 0 : (fz > 0 ? 1 : -1);
        if (sx == 0 && sz == 0) sx = 1;
        boolean diagonal = sx != 0 && sz != 0;
        double n = diagonal ? Math.sqrt(2) : 1;
        double ux = sx / n, uz = sz / n;   // along the step
        double rx = uz, rz = -ux;          // across it
        int L = sole.length() + grow * 2, W = sole.width() + grow * 2;
        // Even sizes sit between pixel rows; diagonals need a little slack or they come out
        // as a checkerboard.
        double sa = !diagonal && (L & 1) == 0 ? 0.5 : 0, sb = !diagonal && (W & 1) == 0 ? 0.5 : 0;
        double eps = diagonal ? 0.2 : 0.3;
        List<int[]> out = new ArrayList<>();
        int R = Math.max(L, W);
        for (int i = -R; i <= R; i++) {
            for (int j = -R; j <= R; j++) {
                double a = i * ux + j * uz - sa, b = i * rx + j * rz - sb;
                if (Math.abs(a) <= (L - 1) / 2.0 + eps && Math.abs(b) <= (W - 1) / 2.0 + eps) {
                    out.add(new int[]{i, j});
                }
            }
        }
        return out;
    }

    /** The world point on the tile's plane straight under {@code p}. */
    private static Vec3 hitPoint(CanvasTile t, Vec3 p) {
        return new Vec3(p.x, t.oy, p.z);
    }

    /**
     * The top-face canvas tile under a world point, remembered per block for the rest of
     * this step. With {@code create} false only existing tiles are returned.
     */
    private static CanvasTile topTile(ClientLevel level, Vec3 p, Map<BlockPos, CanvasTile> cache, boolean create) {
        BlockPos key = BlockPos.containing(p.x, p.y - 0.02, p.z);
        CanvasTile cached = cache.get(key);
        if (cached != null || (!create && cache.containsKey(key))) return cached;
        BloodSurfaces.Hit hit = BloodSurfaces.trace(level, p.add(0, 0.25, 0), p.add(0, -0.45, 0));
        CanvasTile t = null;
        if (hit != null && hit.face() == Direction.UP && Math.abs(hit.point().y - p.y) < 0.35) {
            t = BloodSurfaces.topTile(level, hit, create);
            if (t != null && (t.custom || t.face != Direction.UP)) t = null;
        }
        cache.put(key, t);
        return t;
    }

    public static void clear() {
        FEET.clear();
    }
}
