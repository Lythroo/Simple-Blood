package com.simpleblood.surface;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

final class Wading {

    private Wading() {}

    private static final float TRAMPLE = 0.10f;
    private static final int MAX_SPLASH = 6;
    private static final double SPRINT = 0.28;
    private static final double SLAM = 0.6;

    interface Ground {
        CanvasTile topTile(Vec3 p, boolean create);
    }

    interface Splash {
        void drops(List<double[]> drops, int colour, int born, int life);
    }

    record Look(int lifetime, boolean pattern, int splashPercent, boolean wakes, int tramplePercent) {}

    record Step(int displaced, int colour, boolean tacky) {}

    static Step stepIn(Ground ground, Splash splash, Vec3 foot, List<int[]> sole, List<int[]> rim,
                       double vx, double vz, double impact, double ps, Look look, int now, RandomSource rng) {
        int[] src = new int[3];
        int pressed = 0, displaced = 0, colour = 0;
        long bornSum = 0, lifeSum = 0;
        CanvasTile centre = null;
        int centreX = 0, centreY = 0;
        for (int[] c : sole) {
            Vec3 p = foot.add(c[0] * ps, 0, c[1] * ps);
            CanvasTile t = ground.topTile(p, false);
            if (t == null) continue;
            Vec3 hp = new Vec3(p.x, t.oy, p.z);
            int px = t.pixelX(hp), py = t.pixelY(hp);
            int out = t.tread(px, py, now, TRAMPLE * Math.max(0, look.tramplePercent()) / 100f, src);
            if (out < 0) continue;
            pressed++;
            displaced += out;
            colour = src[0];
            bornSum += src[1];
            lifeSum += src[2];
            if (centre == null || (c[0] == 0 && c[1] == 0)) {
                centre = t;
                centreX = px;
                centreY = py;
            }
        }
        if (pressed == 0) return null;
        int born = (int) (bornSum / pressed), life = (int) Math.max(1, lifeSum / pressed);
        boolean tacky = (now - born) / (float) life > 0.3f;
        com.simpleblood.Tally.count("wading.step");
        double speed = Math.sqrt(vx * vx + vz * vz);
        double dirX = speed > 1.0e-4 ? vx / speed : 0, dirZ = speed > 1.0e-4 ? vz / speed : 0;
        float pace = (float) Math.min(1.0, speed / SPRINT);
        float slam = (float) Math.min(1.0, Math.max(0, impact) / SLAM);
        if (displaced == 0) {
            if (look.wakes()) wake(ground, foot, centre.res, 0, pace, slam, now);
            return new Step(0, colour, tacky);
        }

        int most = MAX_SPLASH + Math.round(MAX_SPLASH * slam);
        float splashShare = tacky ? 0f : Math.min(0.85f, 0.12f + 0.45f * pace + 0.6f * slam) * Math.max(0, look.splashPercent()) / 100f;
        int flying = Math.min(most, Math.min(displaced, roundRandom(displaced * splashShare, rng)));
        int smear = pace < 0.15f ? 0 : roundRandom((displaced - flying) * 0.5f * pace * (1f - slam), rng);
        int ring = displaced - flying - smear;

        double k = 2.5 * pace * (1.0 - 0.7 * slam);
        double[] weight = new double[rim.size()];
        double total = 0;
        for (int i = 0; i < weight.length; i++) {
            int[] r = rim.get(i);
            double len = Math.sqrt(r[0] * r[0] + r[1] * r[1]);
            double cos = len > 0 ? (r[0] * dirX + r[1] * dirZ) / len : 0;
            weight[i] = Math.max(0, 0.3 + k * cos) / Math.max(1, len);
            total += weight[i];
        }

        int lost = 0;
        for (int u = 0; u < ring && weight.length > 0; u++) {
            int[] r = rim.get(pick(weight, total, rng));
            if (!put(ground, foot, r[0], r[1], ps, colour, born, life, 0, now, look, rng)) lost++;
        }

        if (smear > 0) {
            double start = 1;
            for (int[] c : sole) start = Math.max(start, c[0] * dirX + c[1] * dirZ + 1);
            int length = 2 + Math.round(4 * pace);
            for (int u = 0; u < smear; u++) {
                double along = start + rng.nextInt(length);
                double side = rng.nextFloat() < 0.25f ? (rng.nextBoolean() ? 1 : -1) : 0;
                int ox = (int) Math.round(dirX * along - dirZ * side), oz = (int) Math.round(dirZ * along + dirX * side);
                if (!put(ground, foot, ox, oz, ps, colour, born, life, 1, now, look, rng)) lost++;
            }
        }

        flying += Math.min(most - flying, lost);
        if (flying > 0 && !rim.isEmpty()) {
            List<double[]> drops = new ArrayList<>(flying);
            for (int n = 0; n < flying; n++) {
                int[] r = rim.get(pick(weight, total, rng));
                double len = Math.max(0.5, Math.sqrt(r[0] * r[0] + r[1] * r[1]));
                double out = 0.03 + 0.04 * rng.nextDouble() + 0.05 * pace + 0.06 * slam;
                double up = 0.035 + (0.025 + 0.035 * pace + 0.08 * slam) * rng.nextDouble();
                drops.add(new double[]{foot.x + r[0] * ps, foot.y + 0.03, foot.z + r[1] * ps,
                        r[0] / len * out + dirX * speed * 0.5, up, r[1] / len * out + dirZ * speed * 0.5});
            }
            splash.drops(drops, colour, born, life);
        }
        if (look.wakes()) wake(ground, foot, centre.res, displaced, pace, slam, now);
        return new Step(displaced, colour, tacky);
    }

    private static void wake(Ground ground, Vec3 foot, int res, int displaced, float pace, float slam, int now) {
        float strength = Math.min(1f, 0.55f + 0.04f * displaced + 0.1f * pace + 0.4f * slam);
        float reach = (6f + 6f * slam) * res / 16f;
        double r = reach / res;
        CanvasTile[] seen = new CanvasTile[9];
        int n = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                CanvasTile t = ground.topTile(foot.add(i * r, 0, j * r), false);
                if (t == null) continue;
                boolean dup = false;
                for (int k = 0; k < n; k++) dup |= seen[k] == t;
                if (dup) continue;
                seen[n++] = t;
                Vec3 at = new Vec3(foot.x, t.oy, foot.z);
                t.addWake(t.pixelX(at), t.pixelY(at), now, strength, reach);
            }
        }
    }

    private static boolean put(Ground ground, Vec3 foot, int ox, int oz, double ps, int colour, int born, int life,
                               int thin, int now, Look look, RandomSource rng) {
        Vec3 p = foot.add(ox * ps, 0, oz * ps);
        CanvasTile t = ground.topTile(p, true);
        if (t == null) return false;
        Vec3 hp = new Vec3(p.x, t.oy, p.z);
        return t.carry(t.pixelX(hp), t.pixelY(hp), colour, born, life, thin, now, look.lifetime(), rng, look.pattern());
    }

    private static int pick(double[] weight, double total, RandomSource rng) {
        if (total <= 0) return rng.nextInt(weight.length);
        double x = rng.nextDouble() * total;
        for (int i = 0; i < weight.length; i++) {
            x -= weight[i];
            if (x < 0) return i;
        }
        return weight.length - 1;
    }

    private static int roundRandom(float v, RandomSource rng) {
        int n = (int) v;
        return rng.nextFloat() < v - n ? n + 1 : n;
    }
}
