package com.bloodmod.surface;

import com.bloodmod.BloodModClient;
import com.bloodmod.BloodModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

public final class PreviewScene {

    public enum Kind { HIT, DRIP, DEATH, PUDDLE, FOOTPRINTS, WATER }

    public enum Aspect { DRIPS, SPLASH, DIRECTIONAL, SWEEP, EXIT, LOW_HEALTH }

    public record Block(BlockPos pos, BlockState state) {}

    public static final class Drop {
        public double x, y, z, vx, vy, vz;
        public int age, life;
        public float size;
        public float tone;
        public int kind;
        public int sprite;
        public int colour;
    }

    public static final class Fog {
        public double x, y, z, vx, vy, vz;
        public int age, life;
        public float size, alpha;
        public int sprite, colour;
    }

    public static final class Mob {
        public boolean present;
        public double x, y, z;
        public float yaw;
        public int hurtTicks;
        public int age;
        public float swing;
        public float amount;
        public boolean walking;
    }

    private static final List<PreviewScene> LIVE = new ArrayList<>();
    private static final int LIFETIME_DIVISOR = 4;

    public final Kind kind;
    private final IntSupplier colour;
    private final RandomSource rng = RandomSource.create(5);
    private final List<Block> blocks = new ArrayList<>();
    private final Map<BlockPos, Block> blockMap = new HashMap<>();
    private final Map<CanvasTile.Key, CanvasTile> tiles = new HashMap<>();
    private final List<Drop> drops = new ArrayList<>();
    private final List<Fog> fogs = new ArrayList<>();
    private final List<Run> runs = new ArrayList<>();
    public final Mob mob = new Mob();
    private int now;
    private int loopTick;
    private EnumSet<Aspect> show = EnumSet.allOf(Aspect.class);
    private long signature;
    private long lastRenderedMs;
    private int relaxCursor;

    public Vec3 camera = new Vec3(0.55, 0.42, 0.72).normalize();

    public PreviewScene(Kind kind, IntSupplier colour) {
        this.kind = kind;
        this.colour = colour;
        build();
        synchronized (LIVE) { LIVE.add(this); }
    }

    private void build() {
        blocks.clear();
        blockMap.clear();
        BlockState floor = Blocks.STONE_BRICKS.defaultBlockState();
        int length = kind == Kind.FOOTPRINTS ? 2 : 1;
        for (int x = -1; x <= 1; x++) {
            for (int z = -length; z <= length; z++) {
                add(new BlockPos(x, 0, z), floor);
            }
        }
        switch (kind) {
            case HIT, DEATH -> add(new BlockPos(-1, 1, -1), Blocks.COBBLESTONE.defaultBlockState());
            case PUDDLE -> add(new BlockPos(0, 1, -1), Blocks.OAK_PLANKS.defaultBlockState());
            default -> {}
        }
        mob.present = kind != Kind.PUDDLE && kind != Kind.WATER;
        mob.x = 0.5; mob.y = 1; mob.z = kind == Kind.FOOTPRINTS ? WALK_FROM : 0.5;
        mob.yaw = (float) Math.atan2(camera.x, camera.z);
        mob.walking = kind == Kind.FOOTPRINTS;
    }

    private void add(BlockPos pos, BlockState state) {
        Block b = new Block(pos, state);
        blocks.add(b);
        blockMap.put(pos, b);
    }

    public List<Block> blocks() { return blocks; }
    public boolean hasBlock(BlockPos pos) { return blockMap.containsKey(pos); }

    private final Map<Long, net.minecraft.client.renderer.texture.TextureAtlasSprite> sprites = new HashMap<>();

    public net.minecraft.client.renderer.texture.TextureAtlasSprite sprite(Block b, Direction face) {
        long key = b.pos().asLong() * 8 + face.ordinal();
        net.minecraft.client.renderer.texture.TextureAtlasSprite s = sprites.get(key);
        if (s == null) {
            try {
                s = BlockToneSampler.faceSprite(b.state(), face, rng);
            } catch (Throwable t) {
                return null;
            }
            sprites.put(key, s);
        }
        return s;
    }
    public List<Drop> drops() { return drops; }
    public List<Fog> fogs() { return fogs; }
    public java.util.Collection<CanvasTile> tiles() { return tiles.values(); }
    public int now() { return now; }
    public boolean hasWater() { return kind == Kind.WATER; }
    public double waterTop() { return 4.0; }
    public AABB bounds() { return kind == Kind.FOOTPRINTS ? new AABB(-1, 0, -2, 2, 3, 3) : new AABB(-1, 0, -1, 2, 3, 2); }

    public void touch() { lastRenderedMs = System.currentTimeMillis(); }

    public void show(EnumSet<Aspect> aspects) {
        if (show.equals(aspects)) return;
        show = EnumSet.copyOf(aspects);
        drops.clear();
        trigger();
    }

    private boolean shows(Aspect a) {
        return kind != Kind.HIT || show.contains(a);
    }

    public void trigger() {
        switch (kind) {
            case HIT, WATER, DEATH -> {
                loopTick = HIT_AT - 1;
                burstTicks = 0;
                burstTicksLeft = 0;
            }
            case DRIP -> { if (loopTick > HIT_AT) mob.hurtTicks = 6; else loopTick = HIT_AT - 1; }
            default -> {}
        }
    }

    public static void tickAll() {
        List<PreviewScene> copy;
        synchronized (LIVE) { copy = new ArrayList<>(LIVE); }
        long t = System.currentTimeMillis();
        for (PreviewScene s : copy) {
            if (t - s.lastRenderedMs < 1000) s.tick();
        }
    }

    public static void disposeAll() {
        List<PreviewScene> copy;
        synchronized (LIVE) { copy = new ArrayList<>(LIVE); LIVE.clear(); }
        for (PreviewScene s : copy) s.dispose();
    }

    public void dispose() {
        BloodCanvasAtlas atlas = BloodCanvasAtlas.exists() ? BloodCanvasAtlas.get() : null;
        for (CanvasTile t : tiles.values()) t.dispose(atlas);
        tiles.clear();
        drops.clear();
        fogs.clear();
        runs.clear();
        synchronized (LIVE) { LIVE.remove(this); }
    }

    private BloodModConfig cfg() {
        BloodModConfig c = BloodModClient.getConfig();
        return c == null ? new BloodModConfig() : c;
    }

    private long signature() {
        BloodModConfig c = cfg();
        long s = 17;
        s = s * 31 + c.general.bloodAmount;
        s = s * 31 + (c.particles.hitBurst ? 1 : 0) + (c.particles.lowHealthDrip ? 2 : 0);
        s = s * 31 + c.particles.particleSize;
        s = s * 31 + c.particles.particleLifetime;
        s = s * 31 + c.particles.particleGravity;
        s = s * 31 + c.particles.particleDrag;
        s = s * 31 + c.hitBurst.burstIntensity;
        s = s * 31 + c.hitBurst.burstDuration;
        s = s * 31 + c.hitBurst.burstSpread;
        s = s * 31 + c.lowHealth.dripFrequency;
        s = s * 31 + c.lowHealth.dripIntensity;
        s = s * 31 + (c.particles.deathBurst ? 1 : 0);
        s = s * 31 + c.deathBurst.deathIntensity;
        s = s * 31 + c.deathBurst.deathSpread;
        s = s * 31 + (c.surfaces.footprints ? 1 : 0);
        s = s * 31 + c.surfaces.footprintSteps;
        s = s * 31 + c.surfaces.opacity;
        s = s * 31 + c.surfaces.lifetimeSeconds;
        s = s * 31 + (c.directional.enabled ? 1 : 0) + (c.directional.weaponFlavour ? 2 : 0) + (c.directional.entrySpatter ? 4 : 0);
        s = s * 31 + c.directional.share;
        s = s * 31 + (c.surfaces.enabled ? 1 : 0) + (c.surfaces.wallsAndCeilings ? 2 : 0);
        s = s * 31 + c.surfaces.amount;
        s = s * 31 + c.surfaces.resolution;
        s = s * 31 + c.surfaces.highlightMode.hashCode();
        s = s * 31 + (c.underwater.transformToFog ? 1 : 0);
        s = s * 31 + c.underwater.fogSize;
        s = s * 31 + c.underwater.fogLifetime;
        s = s * 31 + c.underwater.fogOpacity;
        return s;
    }

    private void restart() {
        BloodCanvasAtlas atlas = BloodCanvasAtlas.exists() ? BloodCanvasAtlas.get() : null;
        for (CanvasTile t : tiles.values()) t.dispose(atlas);
        tiles.clear();
        drops.clear();
        fogs.clear();
        runs.clear();
        loopTick = 0;
        mob.hurtTicks = 0;
        puddleLaid = false;
        footWet = 0;
        lastStepIndex = Integer.MIN_VALUE;
        rng.setSeed(5);
        signature = signature();
    }

    private static final int HIT_AT = 8;

    public void tick() {
        if (signature != signature()) restart();
        now++;
        loopTick++;
        BloodModConfig c = cfg();
        mob.age++;
        if (mob.hurtTicks > 0) mob.hurtTicks--;
        if (!mob.walking) {
            mob.yaw = (float) Math.atan2(camera.x, camera.z);
            mob.amount += (0f - mob.amount) * 0.4f;
        }

        switch (kind) {
            case HIT -> {
                if (loopTick == HIT_AT) hit(c);
                if (loopTick > HIT_AT + burstTicks && loopTick < HIT_AT + 80 && c.particles.lowHealthDrip && shows(Aspect.LOW_HEALTH)) dripTick(c);
                if (loopTick >= HIT_AT + burstTicks) burstTicks = 0;
                else if (burstTicksLeft > 0) burstTick(c);
            }
            case WATER -> {
                if (loopTick == HIT_AT) clouds(c);
            }
            case DRIP -> {
                if (loopTick == HIT_AT) mob.hurtTicks = 6;
                if (loopTick > HIT_AT && c.particles.lowHealthDrip) dripTick(c);
            }
            case DEATH -> {
                if (loopTick == HIT_AT) death(c);
            }
            case PUDDLE -> {
                if (loopTick % 7 == 0) rainDrop(c);
                if (loopTick >= 400) loopTick = 0;
            }
            case FOOTPRINTS -> walkTick(c);
        }
        moveDrops(c);
        moveFogs();
        advanceRuns(c);
        if ((now & 3) == 0) relax(c);
        if ((now & 7) == 0) age();
        upload(c);
    }

    private void clouds(BloodModConfig c) {
        int n = 2 + rng.nextInt(2);
        for (int i = 0; i < n; i++) {
            double x = 0.5 + (rng.nextDouble() - 0.5) * 1.1, y = 1.6 + rng.nextDouble() * 0.7, z = 0.5 + (rng.nextDouble() - 0.5) * 1.1;
            if (c.underwater.transformToFog) {
                Drop d = new Drop();
                d.x = x; d.y = y; d.z = z;
                d.vx = (rng.nextDouble() - 0.5) * 0.02; d.vy = -0.01; d.vz = (rng.nextDouble() - 0.5) * 0.02;
                d.colour = colour.getAsInt() & 0xFFFFFF;
                spawnFog(d, c);
                Fog f = fogs.get(fogs.size() - 1);
                f.size = (0.2f + rng.nextFloat() * 0.15f) * c.fogSizeMultiplier();
            } else {
                spawn(x, y, z, (rng.nextDouble() - 0.5) * 0.2, -0.3, (rng.nextDouble() - 0.5) * 0.2, 0);
            }
        }
    }

    private void death(BloodModConfig c) {
        mob.hurtTicks = 12;
        if (!c.particles.deathBurst) return;
        float size = 0.6f;
        float intensity = c.deathIntensityMultiplier();
        float spread = c.deathSpreadMultiplier();
        int drips = (int) (30 * size * intensity), splash = (int) (25 * size * intensity);
        double bx = mob.x, by = mob.y + 0.95, bz = mob.z;
        for (int i = 0; i < drips; i++) {
            double ox = (rng.nextDouble() - 0.5) * 0.6 * 1.8 * spread, oy = rng.nextDouble() * 1.95 * 0.8, oz = (rng.nextDouble() - 0.5) * 0.6 * 1.8 * spread;
            spawn(bx + ox, by + oy, bz + oz, (rng.nextDouble() - 0.5) * 0.4 * spread, -0.5 - rng.nextDouble() * 1.5, (rng.nextDouble() - 0.5) * 0.4 * spread, 0);
        }
        for (int i = 0; i < splash; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double r = (0.2 + rng.nextDouble() * 0.6 * 1.2) * spread;
            double sp = (0.3 + rng.nextDouble() * 0.4) * spread;
            spawn(bx + Math.cos(a) * r, by + (rng.nextDouble() - 0.3) * 1.95 * 0.6, bz + Math.sin(a) * r,
                    Math.cos(a) * sp, 0.1 + rng.nextDouble() * 0.2, Math.sin(a) * sp, 1);
        }
    }

    private static final double WALK_FROM = -1.55, WALK_TO = 2.55, WALK_SPEED = 0.08;
    private static final int TURN_TICKS = 14;
    private int walkDir = 1, turnTicks;
    private float turnFrom, turnTo;
    private int lastStepIndex = Integer.MIN_VALUE;
    private double footWet;
    private boolean puddleLaid;

    private void walkTick(BloodModConfig c) {
        if (!puddleLaid) {
            Block pool = blockMap.get(new BlockPos(0, 0, -1));
            if (pool != null && c.surfaces.enabled) {
                int n = Math.round(70 * (c.surfaces.resolution == 16 ? 3 : 1) / 3f * Math.max(0.3f, c.surfaceAmount()));
                stampAt(pool, Direction.UP, new Vec3(0.5, 1, -0.5), colour.getAsInt() & 0xFFFFFF, n, null);
            }
            puddleLaid = true;
            mob.z = WALK_FROM;
            walkDir = 1;
            turnTicks = 0;
            mob.yaw = 0f;
        }
        if (loopTick % 10 == 0 && c.surfaces.enabled) {
            Drop d = spawn(0.5 + (rng.nextDouble() - 0.5) * 0.6, 3.4, -0.5 + (rng.nextDouble() - 0.5) * 0.6, 0, -0.1, 0, 0);
            d.vx = 0; d.vz = 0; d.vy = -0.15;
        }
        double pz = mob.z;
        if (turnTicks > 0) {
            turnTicks--;
            float t = 1f - turnTicks / (float) TURN_TICKS;
            mob.yaw = turnFrom + (turnTo - turnFrom) * t;
        } else {
            mob.z += walkDir * WALK_SPEED;
            if (walkDir > 0 && mob.z >= WALK_TO) {
                mob.z = WALK_TO; walkDir = -1; turnTicks = TURN_TICKS; turnFrom = 0f; turnTo = (float) Math.PI;
            } else if (walkDir < 0 && mob.z <= WALK_FROM) {
                mob.z = WALK_FROM; walkDir = 1; turnTicks = TURN_TICKS; turnFrom = (float) Math.PI; turnTo = (float) (2 * Math.PI);
            }
        }
        mob.x = 0.5;
        float v = (float) Math.abs(mob.z - pz);
        mob.amount += (Math.min(v * 4f, 1f) - mob.amount) * 0.4f;
        mob.swing += mob.amount;

        int stepIndex = (int) Math.floor(mob.swing * Footprints.LEG_SWING / Math.PI);
        boolean landed = lastStepIndex != Integer.MIN_VALUE && stepIndex != lastStepIndex && v > 1e-4;
        lastStepIndex = stepIndex;
        if (!landed) return;
        boolean right = (stepIndex & 1) != 0;
        int res = c.surfaces.resolution == 16 ? 16 : 8;
        Footprints.Gait gait = Footprints.humanoid(res);
        double fx = Math.sin(mob.yaw), fz = Math.cos(mob.yaw);
        double rx = -fz, rz = fx;
        double reach = Footprints.reach(gait, mob.amount);
        double side = right ? gait.side() : -gait.side();
        Vec3 foot = new Vec3(mob.x + rx * side + fx * reach, 1.0, mob.z + rz * side + fz * reach);
        double ps = 1.0 / res;
        int found = 0;
        for (int[] cell : Footprints.rasterise(gait.sole(), fx, fz, 1)) {
            Vec3 p = foot.add(cell[0] * ps, 0, cell[1] * ps);
            Block b = blockMap.get(new BlockPos((int) Math.floor(p.x), 0, (int) Math.floor(p.z)));
            if (b == null) continue;
            CanvasTile tt = tile(b, Direction.UP, false);
            if (tt == null) continue;
            if (tt.wetDepthAt(tt.pixelX(p), tt.pixelY(p), now) > 0) found++;
        }
        if (found > 0) { footWet = Math.min(c.surfaces.footprintSteps, footWet + found * 0.75); return; }
        if (footWet <= 0 || !c.surfaces.footprints) return;
        float strength = (float) Math.min(1.0, footWet / Math.max(1, c.surfaces.footprintSteps));
        footWet -= 1;
        for (int[] cell : Footprints.fade(Footprints.rasterise(gait.sole(), fx, fz, 0), strength, rng)) {
            Vec3 p = foot.add(cell[0] * ps, 0, cell[1] * ps);
            Block b = blockMap.get(new BlockPos((int) Math.floor(p.x), 0, (int) Math.floor(p.z)));
            if (b == null) continue;
            CanvasTile tt = tile(b, Direction.UP, true);
            tt.paintPrint(tt.pixelX(p), tt.pixelY(p), colour.getAsInt() & 0xFFFFFF, now, lifetime(), rng, pattern(), false);
        }
    }

    private int burstTicks, burstTicksLeft;
    private static final float DAMAGE = 6f, MAX_HEALTH = 20f;

    private float severity() {
        return Math.min(1f, 0.6f * Math.min(1f, DAMAGE / 20f) + 0.4f * Math.min(1f, DAMAGE / MAX_HEALTH));
    }

    private Vec3 wound() {
        Vec3 facing = new Vec3(Math.sin(mob.yaw), 0, Math.cos(mob.yaw));
        return new Vec3(mob.x, mob.y + 1.15, mob.z).add(facing.scale(0.32));
    }

    private void hit(BloodModConfig c) {
        mob.hurtTicks = 10;
        if (!c.particles.hitBurst) return;
        float t = Math.min(DAMAGE / 20f, 1f);
        int ticks = 3 + (int) (13 * t);
        burstTicks = Math.max(1, (int) (ticks * c.burstDurationMultiplier()));
        burstTicksLeft = burstTicks;
        if (c.directional.enabled && c.directional.weaponFlavour && kind == Kind.HIT && shows(Aspect.SWEEP)) sweep(c);
    }

    private void burstTick(BloodModConfig c) {
        burstTicksLeft--;
        float intensity = c.burstIntensityMultiplier();
        float spreadFactor = Math.min(DAMAGE / 10f, 2f) * c.burstSpreadMultiplier();
        int totalDrips = (int) (Math.min(1 + (int) (DAMAGE * 1.2f), 15) * intensity);
        int totalSplash = (int) (Math.min(1 + (int) (DAMAGE * 1.8f), 23) * intensity);
        int drips = Math.max(1, totalDrips / burstTicks);
        int splash = Math.max(1, totalSplash / burstTicks);
        Vec3 w = wound();
        double patch = 0.06 + 0.6 * 0.12;
        Vec3 d = camera;
        Vec3 side = new Vec3(-d.z, 0, d.x).normalize();
        if (shows(Aspect.DRIPS)) {
            for (int i = 0; i < drips; i++) {
                double vx = (rng.nextDouble() - 0.5) * 0.2 * spreadFactor;
                double vy = -1.2 - rng.nextDouble() * 0.8 * spreadFactor;
                double vz = (rng.nextDouble() - 0.5) * 0.2 * spreadFactor;
                spawn(w.x + (rng.nextDouble() - 0.5) * 2 * patch, w.y + (rng.nextDouble() - 0.5) * 2 * patch, w.z + (rng.nextDouble() - 0.5) * 2 * patch, vx, vy, vz, 0);
            }
        }
        int directional = 0;
        if (c.directional.enabled && shows(Aspect.DIRECTIONAL)) {
            directional = Math.round(splash * c.directionalShare());
            float speed = 0.28f + 0.45f * severity();
            for (int i = 0; i < directional; i++) {
                double s = speed * (0.6 + rng.nextDouble() * 0.8);
                Vec3 v = d.scale(s).add(side.scale((rng.nextDouble() - 0.5) * 2 * 0.35 * s)).add(0, (rng.nextDouble() - 0.5) * 2 * 0.30 * s + 0.06, 0);
                Vec3 p = w.add(side.scale((rng.nextDouble() - 0.5) * 0.15)).add(0, (rng.nextDouble() - 0.5) * 0.15, 0);
                spawn(p.x, p.y, p.z, v.x, v.y, v.z, i % 3 != 0 ? 1 : 0);
            }
        }
        if (c.directional.enabled && c.directional.entrySpatter && shows(Aspect.EXIT) && burstTicksLeft == burstTicks - 1) {
            int through = 2 + (int) (severity() * 3);
            float sp = 0.10f + 0.16f * severity();
            Vec3 back = camera.scale(-1);
            Vec3 exit = new Vec3(mob.x, w.y, mob.z).add(back.scale(0.32));
            for (int i = 0; i < through; i++) {
                Vec3 v = back.scale(sp * (0.5 + rng.nextDouble())).add(side.scale((rng.nextDouble() - 0.5) * sp)).add(0, rng.nextDouble() * 0.05, 0);
                spawn(exit.x, exit.y + (rng.nextDouble() - 0.5) * 0.2, exit.z, v.x, v.y, v.z, 0);
            }
        }
        if (shows(Aspect.SPLASH)) {
            for (int i = 0; i < splash - directional; i++) {
                double a = rng.nextDouble() * Math.PI * 2;
                double s = (0.1 + rng.nextDouble() * 0.15) * spreadFactor;
                double vx = Math.cos(a) * s * 0.5, vz = Math.sin(a) * s * 0.5;
                double vy = (-0.8 - rng.nextDouble() * 0.6) * spreadFactor;
                spawn(w.x + (rng.nextDouble() - 0.5) * 2 * patch, w.y + (rng.nextDouble() - 0.5) * 2 * patch, w.z + (rng.nextDouble() - 0.5) * 2 * patch, vx, vy, vz, 1);
            }
        }
    }

    private void sweep(BloodModConfig c) {
        int count = Math.round((3 + severity() * 6) * c.masterAmount());
        float speed = 0.20f + 0.16f * severity();
        Vec3 d = camera;
        Vec3 side = new Vec3(-d.z, 0, d.x).normalize();
        Vec3 w = wound();
        for (int i = 0; i < count; i++) {
            double a = (rng.nextDouble() * 2 - 1) * Math.toRadians(60);
            Vec3 dir = d.scale(Math.cos(a)).add(side.scale(Math.sin(a))).normalize();
            double s = speed * (0.7 + rng.nextDouble() * 0.6);
            Vec3 v = dir.scale(s).add(0, 0.01 + rng.nextDouble() * 0.03, 0);
            Vec3 p = w.add(side.scale((rng.nextDouble() - 0.5) * 0.36)).add(0, (rng.nextDouble() - 0.5) * 0.25, 0);
            Drop dr = spawn(p.x, p.y, p.z, v.x, v.y, v.z, 2);
            dr.vx = v.x; dr.vy = v.y; dr.vz = v.z;
        }
    }

    private void dripTick(BloodModConfig c) {
        int chance = Math.max(1, (int) (6 / c.dripFrequencyMultiplier()));
        if (rng.nextInt(chance) != 0) return;
        int count = (int) (2 * c.dripIntensityMultiplier());
        Vec3 w = wound();
        for (int i = 0; i < count; i++) {
            spawn(w.x + (rng.nextDouble() - 0.5) * 0.16, w.y + (rng.nextDouble() - 0.5) * 0.2, w.z + (rng.nextDouble() - 0.5) * 0.16,
                    (rng.nextDouble() - 0.5) * 0.1, -1.5 - rng.nextDouble() * 0.5, (rng.nextDouble() - 0.5) * 0.1, 0);
        }
    }

    private void rainDrop(BloodModConfig c) {
        boolean onStep = rng.nextInt(3) != 0;
        double x = 0.5 + (onStep ? 0 : (rng.nextDouble() - 0.5) * 1.6) + (rng.nextDouble() - 0.5) * 0.5;
        double z = (onStep ? -0.5 : 0.7) + (rng.nextDouble() - 0.5) * 0.5;
        Drop d = spawn(x, 3.6, z, 0, -0.1, 0, rng.nextInt(4) == 0 ? 1 : 0);
        d.vx = 0; d.vz = 0; d.vy = -0.15;
    }

    private Drop spawn(double x, double y, double z, double vx, double vy, double vz, int kind) {
        BloodModConfig c = cfg();
        Drop d = new Drop();
        d.x = x; d.y = y; d.z = z;
        if (kind != 2 && kind != 3) {
            double jx = vx + (rng.nextDouble() * 2 - 1) * 0.4, jy = vy + (rng.nextDouble() * 2 - 1) * 0.4, jz = vz + (rng.nextDouble() * 2 - 1) * 0.4;
            double mag = (rng.nextDouble() + rng.nextDouble() + 1) * 0.15;
            double len = Math.sqrt(jx * jx + jy * jy + jz * jz);
            if (len < 1e-6) len = 1;
            vx = jx / len * mag * 0.4; vy = jy / len * mag * 0.4 + 0.1; vz = jz / len * mag * 0.4;
        }
        if (this.kind == Kind.WATER) { vx *= 0.3; vy *= 0.3; vz *= 0.3; }
        d.vx = vx; d.vy = vy; d.vz = vz;
        d.life = (int) ((40 + rng.nextInt(20) - 10) * c.particleLifetimeMultiplier());
        d.size = (0.08f + rng.nextFloat() * 0.08f) * c.particleSizeMultiplier();
        if (kind == 2) d.size = (0.34f + rng.nextFloat() * 0.16f) * c.chunkyStreakScale();
        if (kind == 3) { d.size = 0.02f; d.life = 200; }
        d.tone = 0.9f + rng.nextFloat() * 0.2f;
        d.kind = kind;
        d.sprite = rng.nextInt(kind == 1 ? 3 : 4);
        d.colour = colour.getAsInt() & 0xFFFFFF;
        drops.add(d);
        return d;
    }

    private void moveDrops(BloodModConfig c) {
        double gravity = 0.04 * c.particleGravityMultiplier();
        double drag = 0.98 * c.particleDragMultiplier();
        boolean fog = kind == Kind.WATER && c.underwater.transformToFog;
        Iterator<Drop> it = drops.iterator();
        while (it.hasNext()) {
            Drop d = it.next();
            if (kind == Kind.WATER && d.y < waterTop() && Math.abs(d.x) < 1.5 && Math.abs(d.z) < 1.5) {
                if (fog) { spawnFog(d, c); it.remove(); continue; }
                d.vx *= 0.70; d.vy *= 0.70; d.vz *= 0.70;
            }
            double px = d.x, py = d.y, pz = d.z;
            double wantX = d.vx, wantY = d.vy, wantZ = d.vz;
            d.x += d.vx; d.y += d.vy; d.z += d.vz;
            if (d.kind == 3) { d.vy -= 0.02; }
            else { d.vy -= gravity; d.vx *= drag; d.vy *= drag; d.vz *= drag; }
            d.age++;
            Block hitBlock = null;
            Direction hitFace = null;
            Vec3 hitPoint = null;
            for (Block b : blocks) {
                AABB box = new AABB(b.pos()).inflate(d.size * 0.5);
                if (!box.contains(d.x, d.y, d.z)) continue;
                Vec3 from = new Vec3(px, py, pz), to = new Vec3(d.x, d.y, d.z);
                java.util.Optional<Vec3> clip = new AABB(b.pos()).clip(from, to);
                Vec3 p = clip.orElse(to);
                hitFace = faceOf(b.pos(), p, wantX, wantY, wantZ);
                hitBlock = b;
                hitPoint = p;
                break;
            }
            if (hitBlock != null) {
                land(hitBlock, hitFace, hitPoint, d, new Vec3(wantX, wantY, wantZ), c);
                it.remove();
                continue;
            }
            if (d.age >= d.life || d.y < -1.5) it.remove();
        }
    }

    private static Direction faceOf(BlockPos pos, Vec3 p, double vx, double vy, double vz) {
        double lx = p.x - pos.getX(), ly = p.y - pos.getY(), lz = p.z - pos.getZ();
        double eps = 0.02;
        if (ly >= 1 - eps && vy < 0) return Direction.UP;
        if (ly <= eps && vy > 0) return Direction.DOWN;
        if (lx <= eps && vx > 0) return Direction.WEST;
        if (lx >= 1 - eps && vx < 0) return Direction.EAST;
        if (lz <= eps && vz > 0) return Direction.NORTH;
        if (lz >= 1 - eps && vz < 0) return Direction.SOUTH;
        double[] dist = {1 - ly, ly, lz, 1 - lz, lx, 1 - lx};
        Direction[] dirs = {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        int best = 0;
        for (int i = 1; i < 6; i++) if (dist[i] < dist[best]) best = i;
        return dirs[best];
    }

    private void spawnFog(Drop d, BloodModConfig c) {
        Fog f = new Fog();
        f.x = d.x; f.y = d.y; f.z = d.z;
        f.vx = d.vx * 0.4; f.vy = d.vy * 0.2; f.vz = d.vz * 0.4;
        f.life = (int) ((120 + rng.nextInt(20) - 10) * c.fogLifetimeMultiplier());
        f.size = (0.08f + rng.nextFloat() * 0.27f) * c.fogSizeMultiplier();
        f.alpha = 0.25f * c.fogOpacityMultiplier();
        f.sprite = rng.nextInt(8);
        f.colour = d.colour;
        fogs.add(f);
    }

    private void moveFogs() {
        Iterator<Fog> it = fogs.iterator();
        while (it.hasNext()) {
            Fog f = it.next();
            f.x += f.vx; f.y += f.vy; f.z += f.vz;
            f.vx *= 0.97; f.vz *= 0.97; f.vy = f.vy * 0.99 + 0.0006;
            f.age++;
            if (f.y > waterTop() - 0.1) f.y = waterTop() - 0.1;
            if (f.age >= f.life) it.remove();
        }
    }

    private CanvasTile tile(Block b, Direction face, boolean create) {
        CanvasTile.Key key = new CanvasTile.Key(b.pos().asLong(), face, 0);
        CanvasTile t = tiles.get(key);
        if (t == null && create) {
            BloodModConfig.SurfaceSettings s = cfg().surfaces;
            t = new CanvasTile(key, b.pos(), face, b.state(), new AABB(0, 0, 0, 1, 1, 1), s.resolution == 16 ? 16 : 8, now);
            if ("block".equals(s.highlightMode)) t.setBaseTones(BlockToneSampler.sample(t, rng));
            tiles.put(key, t);
        }
        return t;
    }

    private int lifetime() {
        return Math.max(60, cfg().surfaces.lifetimeSeconds * 20 / LIFETIME_DIVISOR);
    }

    private boolean pattern() { return !"block".equals(cfg().surfaces.highlightMode); }

    private void land(Block b, Direction face, Vec3 point, Drop d, Vec3 travel, BloodModConfig c) {
        if (!c.surfaces.enabled) return;
        if (!c.surfaces.wallsAndCeilings && face != Direction.UP) return;
        if (blockMap.containsKey(b.pos().relative(face))) return;
        int pixels;
        if (d.kind == 3) {
            pixels = 1;
        } else {
            int base = 1 + rng.nextInt(2) + (d.size > 0.22f ? 1 : 0);
            if (d.kind == 1 || d.kind == 2) base += 2 + rng.nextInt(2);
            float areaScale = c.surfaces.resolution >= 16 ? 3.0f : 1.0f;
            float landing = d.kind == 2 ? 2.4f : 1f;
            pixels = Math.max(1, Math.round(base * c.surfaceAmount() * areaScale * landing));
        }
        stampAt(b, face, point, d.colour, pixels, travel);
    }

    private void stampAt(Block b, Direction face, Vec3 point, int colour, int pixels, Vec3 travel) {
        CanvasTile tile = tile(b, face, true);
        int px = clamp(tile.pixelX(point), tile.pw), py = clamp(tile.pixelY(point), tile.ph);
        double bu = 0, bv = 0;
        if (travel != null && travel.lengthSqr() > 1.0e-6) {
            Vec3 t = travel.normalize();
            bu = t.x * tile.ux + t.y * tile.uy + t.z * tile.uz;
            bv = t.x * tile.vx + t.y * tile.vy + t.z * tile.vz;
        }
        List<int[]> spill = new ArrayList<>();
        tile.stamp(px, py, pixels, colour, now, lifetime(), rng, pattern(), spill, bu, bv);
        for (int[] cell : spill) spillCell(tile, cell[0], cell[1], colour);
        if (face == Direction.UP) {
            int started = 0;
            for (int dx = -2; dx <= 2 && started < 2; dx++) {
                for (int dy = -2; dy <= 2 && started < 2; dy++) {
                    int ex = px + dx, ey = py + dy;
                    if (!tile.filled(ex, ey)) continue;
                    boolean onEdge = ex == 0 || ey == 0 || ex == tile.pw - 1 || ey == tile.ph - 1;
                    if (!onEdge) continue;
                    int ox = ex == 0 ? -1 : (ex == tile.pw - 1 ? tile.pw : ex);
                    int oy = ey == 0 ? -1 : (ey == tile.ph - 1 ? tile.ph : ey);
                    if (spillCell(tile, ox, oy, colour)) started++;
                }
            }
        }
    }

    private boolean spillCell(CanvasTile tile, int cx, int cy, int colour) {
        Direction edge = edgeDirection(tile, cx, cy);
        if (edge == null) return false;
        Vec3 p = edgePoint(tile, cx, cy, edge);
        BlockPos npos = tile.pos.relative(edge);
        Block neighbour = blockMap.get(npos);
        if (neighbour != null && tile.face != Direction.UP) return false;
        if (neighbour != null) {
            CanvasTile n = tile(neighbour, tile.face, true);
            n.paint(clamp(n.pixelX(p), n.pw), clamp(n.pixelY(p), n.ph), colour, now, lifetime(), rng, pattern());
            return false;
        }
        if (tile.face == Direction.UP && cfg().surfaces.wallsAndCeilings) {
            Block self = blockMap.get(tile.pos);
            if (self == null) return false;
            CanvasTile side = tile(self, edge, true);
            int column = clamp(side.pixelX(p), side.pw);
            return startRun(side, column, colour, tile, clamp(cx, tile.pw), clamp(cy, tile.ph));
        }
        if (tile.face.getAxis().isHorizontal() && edge == Direction.DOWN) {
            Drop d = spawn(p.x, p.y - 0.02, p.z, 0, -0.05, 0, 3);
            d.colour = colour;
            return true;
        }
        return false;
    }

    private static Direction edgeDirection(CanvasTile tile, int px, int py) {
        double dx, dy, dz;
        if (px < 0)              { dx = -tile.ux; dy = -tile.uy; dz = -tile.uz; }
        else if (px >= tile.pw)  { dx = tile.ux;  dy = tile.uy;  dz = tile.uz; }
        else if (py < 0)         { dx = -tile.vx; dy = -tile.vy; dz = -tile.vz; }
        else if (py >= tile.ph)  { dx = tile.vx;  dy = tile.vy;  dz = tile.vz; }
        else return null;
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        if (ax >= ay && ax >= az) return dx > 0 ? Direction.EAST : Direction.WEST;
        if (ay >= az) return dy > 0 ? Direction.UP : Direction.DOWN;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static Vec3 edgePoint(CanvasTile tile, int px, int py, Direction edgeDir) {
        int ex = clamp(px, tile.pw), ey = clamp(py, tile.ph);
        double step = 0.5 / tile.res;
        return tile.pixelCenter(ex, ey).add(edgeDir.getStepX() * step, edgeDir.getStepY() * step, edgeDir.getStepZ() * step);
    }

    private static int clamp(int v, int max) { return v < 0 ? 0 : Math.min(max - 1, v); }

    private static final class Run {
        CanvasTile side;
        int column, row, remaining, interval, nextTick, colour, volume;
        CanvasTile source;
        int sourceX, sourceY;
    }

    private boolean startRun(CanvasTile side, int column, int colour, CanvasTile source, int sx, int sy) {
        for (Run r : runs) {
            if (r.side == side && r.column == column) {
                if (r.source != null) return false;
                r.source = source; r.sourceX = sx; r.sourceY = sy;
                r.remaining = Math.min(r.remaining + 4, side.ph);
                return true;
            }
        }
        if (runs.size() > 40) return false;
        CanvasTile.Body body = CanvasTile.body(source, sx, sy, flow, 6);
        int mass = Math.max(1, body.mass());
        int pouring = 0;
        for (Run r : runs) if (r.source != null && body.contains(r.source)) pouring++;
        if (pouring > 0 && mass < 32 * side.res * side.res / 256 * (pouring + 1)) return false;
        Run r = new Run();
        r.side = side;
        r.column = column;
        r.row = 0;
        r.remaining = Math.max(2, Math.min(3 * side.res, mass / 2));
        r.volume = 1;
        r.interval = Math.max(2, (6 + rng.nextInt(4)) * 8 / side.res);
        r.nextTick = now + r.interval;
        r.colour = colour;
        r.source = source; r.sourceX = sx; r.sourceY = sy;
        side.paintThin(column, 0, colour, now, lifetime(), rng, pattern(), 1);
        runs.add(r);
        return true;
    }

    private void advanceRuns(BloodModConfig c) {
        Iterator<Run> it = runs.iterator();
        while (it.hasNext()) {
            Run r = it.next();
            if (now < r.nextTick) continue;
            r.nextTick = now + r.interval;
            if (r.remaining-- <= 0) { it.remove(); continue; }
            if (r.source != null) {
                if (!tiles.containsValue(r.source)
                        || !CanvasTile.body(r.source, r.sourceX, r.sourceY, flow, 6).draw(r.source.pixelCenter(r.sourceX, r.sourceY), rng)) {
                    r.source = null;
                    r.remaining = Math.min(r.remaining, Math.max(1, r.side.res / 4));
                } else if ((r.row & 1) == 1) {
                    r.volume++;
                }
            }
            r.row++;
            if (r.row < r.side.ph) {
                int thin = 1 + Math.min(2, r.row / Math.max(1, r.side.res / 2));
                r.side.paintThin(r.column, r.row, r.colour, now, lifetime(), rng, pattern(), thin);
            } else {
                Vec3 p = r.side.pixelCenter(r.column, r.side.ph - 1);
                BlockPos below = r.side.pos.below().relative(r.side.face);
                Block floor = blockMap.get(below);
                if (floor != null && !blockMap.containsKey(below.above())) {
                    Vec3 at = new Vec3(p.x + r.side.face.getStepX() * 0.03, below.getY() + 1, p.z + r.side.face.getStepZ() * 0.03);
                    stampAt(floor, Direction.UP, at, r.colour, Math.max(1, r.volume), new Vec3(0, -1, 0));
                } else {
                    Drop d = spawn(p.x + r.side.face.getStepX() * 0.03, p.y - 0.05, p.z + r.side.face.getStepZ() * 0.03, 0, -0.05, 0, 3);
                    d.colour = r.colour;
                }
                it.remove();
            }
        }
    }

    private final CanvasTile.FlowContext flow = new CanvasTile.FlowContext() {
        @Override
        public boolean filledAcross(CanvasTile tile, int px, int py) {
            int[] xy = new int[2];
            CanvasTile n = across(tile, px, py, xy, false);
            return n != null && n.filled(xy[0], xy[1]);
        }

        @Override
        public CanvasTile across(CanvasTile tile, int px, int py, int[] xy, boolean create) {
            Direction edge = edgeDirection(tile, px, py);
            if (edge == null) return null;
            Block neighbour = blockMap.get(tile.pos.relative(edge));
            if (neighbour == null || blockMap.containsKey(neighbour.pos().relative(tile.face))) return null;
            CanvasTile n = tile(neighbour, tile.face, create);
            if (n == null) return null;
            Vec3 p = edgePoint(tile, px, py, edge);
            xy[0] = clamp(n.pixelX(p), n.pw);
            xy[1] = clamp(n.pixelY(p), n.ph);
            return n;
        }

        @Override
        public boolean canDrain(CanvasTile tile, int px, int py) {
            if (tile.face != Direction.UP || !cfg().surfaces.wallsAndCeilings) return false;
            Direction edge = edgeDirection(tile, px, py);
            return edge != null && edge.getAxis().isHorizontal() && !blockMap.containsKey(tile.pos.relative(edge));
        }

        @Override
        public boolean drain(CanvasTile tile, int px, int py, int colour) {
            Direction edge = edgeDirection(tile, px, py);
            if (edge == null) return false;
            Block self = blockMap.get(tile.pos);
            if (self == null) return false;
            CanvasTile side = tile(self, edge, true);
            Vec3 p = edgePoint(tile, px, py, edge);
            return startRun(side, clamp(side.pixelX(p), side.pw), colour, tile, clamp(px, tile.pw), clamp(py, tile.ph));
        }
    };

    private void relax(BloodModConfig c) {
        CanvasTile[] all = tiles.values().toArray(new CanvasTile[0]);
        if (all.length == 0) return;
        int done = 0;
        for (int k = 0; k < all.length && done < 12; k++) {
            CanvasTile t = all[(relaxCursor + k) % all.length];
            if (!t.active || t.face != Direction.UP) continue;
            done++;
            if (!t.relax(flow, rng, now, lifetime(), pattern())) t.active = false;
        }
        relaxCursor = (relaxCursor + Math.max(1, done)) % Math.max(1, all.length);
    }

    private void age() {
        Iterator<CanvasTile> it = tiles.values().iterator();
        BloodCanvasAtlas atlas = BloodCanvasAtlas.exists() ? BloodCanvasAtlas.get() : null;
        while (it.hasNext()) {
            CanvasTile t = it.next();
            if (t.age(now, rng)) t.dirty = true;
            if (t.isEmpty()) {
                t.dispose(atlas);
                it.remove();
            }
        }
    }

    private void upload(BloodModConfig c) {
        if (tiles.isEmpty()) return;
        BloodCanvasAtlas atlas = BloodCanvasAtlas.get();
        float opacity = c.surfaces.opacity / 100f;
        for (CanvasTile t : tiles.values()) {
            if (t.hasRipples()) t.dirty = true;
            if (t.dirty || t.slot < 0) t.refresh(atlas, now, opacity, flow);
        }
    }

    public void drawTiles(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, int light) {
        for (CanvasTile t : tiles.values()) {
            if (t.slot < 0 || t.isEmpty()) continue;
            int grey = Math.round(255 * (t.custom ? 0.9f : SurfaceRenderer.faceShade(t.face)));
            SurfaceRenderer.drawTile(t, pose, vc, cam, Vec3.ZERO, light, 0.004, grey);
        }
    }
}
