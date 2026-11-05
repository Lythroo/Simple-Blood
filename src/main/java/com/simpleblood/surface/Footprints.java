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
        double lastY;
        boolean airborne;
        float falling;
        boolean ownView;
    }

    private static final Map<Integer, Feet> FEET = new HashMap<>();
    private static ClientLevel trackedLevel;

    static final float LEG_SWING = 0.6662f;
    private static final float GOLEM_SWING = (float) (Math.PI / 6.5);

    record Sole(int width, int length) {}

    record Gait(Sole sole, boolean quadruped, double side, double hind, double front, double legLength, float swing) {}

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (level == null || cfg == null) {
            FEET.clear();
            LANDINGS.clear();
            trackedLevel = null;
            return;
        }
        if (level != trackedLevel) {
            FEET.clear();
            LANDINGS.clear();
            trackedLevel = level;
        }
        landSplashes(level, cfg);
        if (!cfg.surfaces.enabled || !cfg.surfaces.footprints && !cfg.surfaces.wading || BloodSurfaces.tileCount() == 0 && FEET.isEmpty()) {
            if (!FEET.isEmpty()) FEET.clear();
            return;
        }
        int now = BloodSurfaces.now();
        double range = cfg.surfaces.renderDistance;
        Vec3 eye = mc.player != null ? mc.player.position() : Vec3.ZERO;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson() && cfg.surfaces.viewBobSteps && !firstPersonModel();

        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity entity) || entity.isRemoved()) continue;
            if (entity.isPassenger() || entity.isSpectator() || entity.isNoGravity()) continue;
            if (!com.simpleblood.SimpleBlood.leavesFootprints(entity)) continue;
            if (entity.distanceToSqr(eye) > range * range) continue;
            step(level, entity, cfg, now, firstPerson && entity == mc.player);
        }

        if ((now & 63) == 0) {
            Iterator<Feet> it = FEET.values().iterator();
            while (it.hasNext()) {
                if (now - it.next().lastSeen > 100) it.remove();
            }
        }
    }

    private static final double LANDING = 0.2;

    private static void step(ClientLevel level, LivingEntity entity, SimpleBloodConfig cfg, int now, boolean ownView) {
        Feet feet = FEET.get(entity.getId());
        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        if (feet == null) {
            feet = new Feet();
            feet.lastX = x;
            feet.lastY = y;
            feet.lastZ = z;
            FEET.put(entity.getId(), feet);
        }
        feet.lastSeen = now;
        double dx = x - feet.lastX, dy = y - feet.lastY, dz = z - feet.lastZ;
        feet.lastX = x;
        feet.lastY = y;
        feet.lastZ = z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        int res = cfg.surfaces.resolution == 16 ? 16 : 8;
        Gait gait = gaitFor(entity, res);

        boolean grounded = entity.onGround();
        double impact = 0;
        if (!grounded) {
            if (Math.abs(dy) > 4.0) feet.falling = 0;
            else if (dy < 0) feet.falling = Math.max(feet.falling, (float) -dy);
        } else {
            if (feet.airborne) impact = feet.falling;
            feet.falling = 0;
        }
        feet.airborne = !grounded;
        boolean landed = impact > LANDING;

        int stepIndex = ownView && entity instanceof net.minecraft.client.player.AbstractClientPlayer player
                ? (int) Math.floor(walked(player))
                : (int) Math.floor(entity.walkAnimation.position() * gait.swing() / Math.PI);
        boolean stepped = false;
        if (feet.lastStep == Integer.MIN_VALUE || dist > 4.0 || feet.ownView != ownView) {
            feet.lastStep = stepIndex;
        } else if (stepIndex != feet.lastStep) {
            feet.lastStep = stepIndex;
            stepped = grounded && dist >= 0.002;
        }
        feet.ownView = ownView;
        if (!stepped && !landed) return;
        if (entity.isInWater() || entity.isInLava()) {
            feet.wet = 0;
            return;
        }

        float yaw = (float) Math.toRadians(entity.yBodyRot);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -fz, rz = fx;
        List<Vec3> down = new ArrayList<>(4);
        double s = gait.side();
        if (landed) {
            if (gait.quadruped()) {
                for (double side : new double[]{s, -s}) {
                    down.add(new Vec3(x + rx * side - fx * gait.hind(), y, z + rz * side - fz * gait.hind()));
                    down.add(new Vec3(x + rx * side + fx * gait.front(), y, z + rz * side + fz * gait.front()));
                }
            } else {
                down.add(new Vec3(x + rx * s, y, z + rz * s));
                down.add(new Vec3(x - rx * s, y, z - rz * s));
            }
        } else {
            boolean right = (stepIndex & 1) != 0;
            double reach = reach(gait, entity.walkAnimation.speed());
            if (gait.quadruped()) {
                down.add(new Vec3(x + rx * (right ? s : -s) - fx * gait.hind() + fx * reach, y, z + rz * (right ? s : -s) - fz * gait.hind() + fz * reach));
                down.add(new Vec3(x + rx * (right ? -s : s) + fx * gait.front() + fx * reach, y, z + rz * (right ? -s : s) + fz * gait.front() + fz * reach));
            } else {
                double side = right ? s : -s;
                down.add(new Vec3(x + rx * side + fx * reach, y, z + rz * side + fz * reach));
            }
        }

        double ps = 1.0 / res;
        Map<BlockPos, CanvasTile> cache = new HashMap<>(4);
        List<int[]> cells = rasterise(gait.sole(), fx, fz, 0);
        List<int[]> around = rasterise(gait.sole(), fx, fz, 1);
        List<int[]> rim = null;
        RandomSource rng = level.getRandom();
        Wading.Ground ground = (p, create) -> topTile(level, p, cache, create);
        Wading.Look look = new Wading.Look(Math.max(40, cfg.surfaces.lifetimeSeconds * 20),
                !"block".equals(cfg.surfaces.highlightMode), cfg.surfaces.wadeSplash, cfg.surfaces.wakes, cfg.surfaces.trample);

        for (Vec3 foot : down) {
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
                if (!feet.powder) com.simpleblood.mob.MobBlood.steppedIn(entity, foot, colour, found);
                int squeezed = 0;
                if (cfg.surfaces.wading && !feet.powder) {
                    if (rim == null) rim = ring(cells, rasterise(gait.sole(), fx, fz, 2));
                    Wading.Step wade = Wading.stepIn(ground, (drops, c, born, life) -> splashInWorld(level, drops, c, born, life, cfg),
                            foot, cells, rim, dx, dz, impact, ps, look, now, rng);
                    if (wade != null) squeezed = wade.displaced();
                }
                if ((squeezed > 0 || landed || feet.wet > before + 0.5f && found >= 2) && now - feet.lastSquelch > 4) {
                    feet.lastSquelch = now;
                    com.simpleblood.Tally.count("footprint.wet");
                    float loud = Math.min(1f, (found + squeezed) / 8f + (float) impact);
                    BloodSounds.squelch(level, foot, loud, ageSum / found > 0.3f, feet.powder);
                }
                continue;
            }
            if (feet.wet <= 0 || !cfg.surfaces.footprints) continue;

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

    private static float walked(net.minecraft.client.player.AbstractClientPlayer player) {
        //? if <1.21.11 {
        /*return player.walkDist;
        *///?} else {
        return player.avatarState().getInterpolatedWalkDistance(1f);
        //?}
    }

    private record Landing(int tick, Vec3 at, int colour, int born, int life) {}

    private static final List<Landing> LANDINGS = new ArrayList<>();

    private static void splashInWorld(ClientLevel level, List<double[]> drops, int colour, int born, int life, SimpleBloodConfig cfg) {
        float gravity = 0.04f * cfg.particleGravityMultiplier();
        float drag = Math.max(0f, 1f - 0.02f * cfg.particleDragMultiplier());
        int now = BloodSurfaces.now();
        com.simpleblood.particle.BloodParticle.resetSpawnState();
        try {
            com.simpleblood.particle.BloodParticle.setCurrentBloodColor(new com.simpleblood.BloodColor.Color(colour));
            com.simpleblood.particle.BloodParticle.setCurrentKind(com.simpleblood.BloodKind.LIQUID);
            com.simpleblood.particle.BloodParticle.setEntitySizeMultiplier(0.55f);
            com.simpleblood.particle.BloodParticle.setBallistic(true, 0.7f);
            com.simpleblood.particle.BloodParticle.setPaintsSurfaces(false);
            com.simpleblood.particle.BloodParticle.setVanishOnLanding(true);
            for (double[] d : drops) {
                com.simpleblood.ClientBloodParticleSpawner.emit(level, com.simpleblood.BloodParticles.BLOOD_SPRAY, d[0], d[1], d[2], d[3], d[4], d[5]);
                double x = d[0], y = d[1], z = d[2], vx = d[3], vy = d[4], vz = d[5], floor = d[1] - 0.03;
                int t = 0;
                while (t < 60) {
                    vy -= gravity;
                    vx *= drag;
                    vy *= drag;
                    vz *= drag;
                    x += vx;
                    y += vy;
                    z += vz;
                    t++;
                    if (vy < 0 && y <= floor) break;
                }
                if (LANDINGS.size() < 512) LANDINGS.add(new Landing(now + t, new Vec3(x, floor, z), colour, born, life));
            }
        } finally {
            com.simpleblood.particle.BloodParticle.resetSpawnState();
        }
    }

    private static void landSplashes(ClientLevel level, SimpleBloodConfig cfg) {
        if (LANDINGS.isEmpty()) return;
        if (!cfg.surfaces.enabled) {
            LANDINGS.clear();
            return;
        }
        int now = BloodSurfaces.now();
        Map<BlockPos, CanvasTile> cache = new HashMap<>(4);
        RandomSource rng = level.getRandom();
        int lifetime = Math.max(40, cfg.surfaces.lifetimeSeconds * 20);
        boolean pattern = !"block".equals(cfg.surfaces.highlightMode);
        Iterator<Landing> it = LANDINGS.iterator();
        while (it.hasNext()) {
            Landing l = it.next();
            if (now < l.tick() && l.tick() - now < 200) continue;
            it.remove();
            CanvasTile t = topTile(level, l.at(), cache, true);
            if (t == null) continue;
            Vec3 hp = hitPoint(t, l.at());
            t.carry(t.pixelX(hp), t.pixelY(hp), l.colour(), l.born(), l.life(), 0, now, lifetime, rng, pattern);
        }
    }

    private static Boolean firstPersonModel;

    public static boolean firstPersonModel() {
        if (firstPersonModel == null) {
            boolean found = false;
            for (String id : com.simpleblood.Platform.modIds()) found |= id.equals("firstperson") || id.equals("firstpersonmodel");
            firstPersonModel = found;
        }
        return firstPersonModel;
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

    static List<int[]> ring(List<int[]> sole, List<int[]> wide) {
        java.util.Set<Long> in = new java.util.HashSet<>();
        for (int[] c : sole) in.add(((long) c[0] << 32) | (c[1] & 0xFFFFFFFFL));
        List<int[]> out = new ArrayList<>();
        for (int[] c : wide) if (!in.contains(((long) c[0] << 32) | (c[1] & 0xFFFFFFFFL))) out.add(c);
        return out;
    }

    static Vec3 hitPoint(CanvasTile t, Vec3 p) {
        return new Vec3(p.x, t.oy, p.z);
    }

    static CanvasTile topTile(ClientLevel level, Vec3 p, Map<BlockPos, CanvasTile> cache, boolean create) {
        BlockPos key = BlockPos.containing(p.x, p.y - 0.02, p.z);
        CanvasTile cached = cache.get(key);
        if (cached != null) {
            Vec3 on = hitPoint(cached, p);
            if (cached.inside(cached.pixelX(on), cached.pixelY(on))) return cached;
        } else if (!create && cache.containsKey(key)) {
            return null;
        }
        BloodSurfaces.Hit hit = BloodSurfaces.trace(level, p.add(0, 0.25, 0), p.add(0, -0.45, 0));
        CanvasTile t = null;
        if (hit != null && hit.face() == Direction.UP && Math.abs(hit.point().y - p.y) < 0.35) {
            t = BloodSurfaces.topTile(level, hit, create);
            if (t != null && (t.custom || t.face != Direction.UP)) t = null;
        }
        if (t != null || cached == null) cache.put(key, t);
        return t;
    }

    public static void washAround(ClientLevel level, Vec3 at, double radius) {
        for (Map.Entry<Integer, Feet> e : FEET.entrySet()) {
            net.minecraft.world.entity.Entity entity = level.getEntity(e.getKey());
            if (entity != null && entity.position().distanceToSqr(at) <= radius * radius) e.getValue().wet = 0;
        }
    }

    public static void clear() {
        FEET.clear();
        LANDINGS.clear();
    }
}
