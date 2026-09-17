package com.bloodmod.surface;

import com.bloodmod.BloodModClient;
import com.bloodmod.BloodModConfig;
import com.bloodmod.BloodSounds;
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

public final class Footprints {

    private Footprints() {}

    private static final class Feet {
        double lastX, lastZ;
        int lastStep = Integer.MIN_VALUE;
        float wet;
        int colour;
        boolean powder;
        int lastSeen;
        int lastSquelch = -100;
    }

    private static final Map<Integer, Feet> FEET = new HashMap<>();
    private static ClientLevel trackedLevel;

    static final float LEG_SWING = 0.6662f;
    private static final float GOLEM_SWING = (float) (Math.PI / 6.5);

    record Sole(int width, int length) {}

    record Gait(Sole sole, boolean quadruped, double side, double hind, double front, double legLength, float swing) {}

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        BloodModConfig cfg = BloodModClient.getConfig();
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
            if (!com.bloodmod.BloodMod.leavesFootprints(entity)) continue;
            if (entity.distanceToSqr(eye) > range * range) continue;
            step(level, entity, cfg, now);
        }

        if ((now & 63) == 0) {
            Iterator<Feet> it = FEET.values().iterator();
            while (it.hasNext()) {
                if (now - it.next().lastSeen > 100) it.remove();
            }
        }
    }

    private static void step(ClientLevel level, LivingEntity entity, BloodModConfig cfg, int now) {
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

        int stepIndex = (int) Math.floor(entity.walkAnimation.position() * gait.swing() / Math.PI);
        if (feet.lastStep == Integer.MIN_VALUE || dist > 4.0) {
            feet.lastStep = stepIndex;
            return;
        }
        if (stepIndex == feet.lastStep) return;
        feet.lastStep = stepIndex;
        if (!entity.onGround() || dist < 0.002) return;
        if (entity.isInWater() || entity.isInLava()) {
            feet.wet = 0;
            return;
        }

        float yaw = (float) Math.toRadians(entity.yBodyRot);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -fz, rz = fx;
        boolean right = (stepIndex & 1) != 0;
        double reach = reach(gait, entity.walkAnimation.speed());
        List<Vec3> down = new ArrayList<>(2);
        if (gait.quadruped()) {
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
            int found = 0, taken = 0, colour = 0;
            int powderCount = 0;
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
                    BloodSounds.squelch(level, foot, Math.min(1f, found / 6f));
                }
                continue;
            }
            if (feet.wet <= 0) continue;

            float max = cfg.surfaces.footprintSteps;
            float strength = Math.min(1f, feet.wet / max);
            feet.wet -= gait.quadruped() ? 0.5f : 1f;
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

    static double reach(Gait gait, float swingAmount) {
        double angle = Math.min(1.4, 1.4 * Math.max(0f, swingAmount));
        return Math.max(0.04, Math.min(0.45, gait.legLength() * Math.sin(angle) * 0.5));
    }

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

    static Gait humanoid(int res) {
        Sole s = res < 16 ? new Sole(2, 2) : new Sole(3, 4);
        return new Gait(s, false, 0.12, 0, 0, 0.75, LEG_SWING);
    }

    static List<int[]> rasterise(Sole sole, double fx, double fz, int grow) {
        int sx = Math.abs(fx) < 0.3827 ? 0 : (fx > 0 ? 1 : -1);
        int sz = Math.abs(fz) < 0.3827 ? 0 : (fz > 0 ? 1 : -1);
        if (sx == 0 && sz == 0) sx = 1;
        boolean diagonal = sx != 0 && sz != 0;
        double n = diagonal ? Math.sqrt(2) : 1;
        double ux = sx / n, uz = sz / n;
        double rx = uz, rz = -ux;
        int L = sole.length() + grow * 2, W = sole.width() + grow * 2;
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

    private static Vec3 hitPoint(CanvasTile t, Vec3 p) {
        return new Vec3(p.x, t.oy, p.z);
    }

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
