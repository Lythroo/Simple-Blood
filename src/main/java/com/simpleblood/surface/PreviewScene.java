package com.simpleblood.surface;

import com.simpleblood.Lang;

import com.simpleblood.SimpleBloodClient;
import com.simpleblood.SimpleBloodConfig;
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

    public enum Kind {
        HIT, DRIP, DEATH, PUDDLE, FOOTPRINTS, WATER, ARMOR, GIBLETS, WEAPONS,
        BONE, METAL, WOOD, EMBER, SPIRIT, WIND, GLOW, RAIN, DRYING, COAT, COAT_DRYING, MIX;

        public boolean pieces() {
            return this == BONE || this == METAL || this == WOOD || this == EMBER || this == SPIRIT || this == WIND;
        }

        public boolean blocksOnly() {
            return this == PUDDLE || this == WATER || this == RAIN || this == DRYING || this == MIX;
        }
    }

    public enum Aspect { DRIPS, SPLASH, DIRECTIONAL, SWEEP, EXIT, LOW_HEALTH }

    public record Block(BlockPos pos, BlockState state) {}

    public static final class Drop {
        public double x, y, z, vx, vy, vz;
        public int age, life;
        public float size;
        public float tone;
        public int kind;
        public boolean single;
        public final int[] shown = {-1, -1};
        public boolean powder;
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
        public boolean armored;
        public int deathTicks;
        public double vx, vy, vz;
    }

    public static final class Piece {
        public static final int SPARK = 0, FLAKE = 1, GIBLET = 2, CHIP = 3, DUST = 4, SPLINTER = 5, EMBER = 6, SPIRIT = 7, WIND = 8;
        private static final int[] TUMBLE = {0, 2, 3, 4, 5, 4, 3, 2};

        public double x, y, z, vx, vy, vz;
        public int type, age, life, shape, restFrame;
        public float size, gravity, drag = 0.95f;
        public int colour;
        public int blood;
        public boolean resting;
        public boolean collides = true;
        public boolean glints;
        public float alphaScale = 1f;

        public String texture() {
            int frac8 = Math.min(7, age * 8 / Math.max(1, life));
            return switch (type) {
                case SPARK -> "armor_spark_" + Math.min(3, age * 4 / Math.max(1, life));
                case FLAKE -> "metal_flake_" + shape + "_" + (resting ? restFrame : TUMBLE[(age / 2) % TUMBLE.length]);
                case CHIP -> "bone_chip_" + shape + "_" + (resting ? restFrame : (age / 2) % 4);
                case DUST -> "bone_dust_" + frac8;
                case SPLINTER -> "wood_splinter_" + shape + "_" + (resting ? restFrame : (age / 2) % 4);
                case EMBER -> "ember_" + (resting ? restFrame : (age / 2) % 4);
                case SPIRIT -> "spirit_spark_" + Math.min(5, age * 6 / Math.max(1, life));
                case WIND -> "wind_puff_" + frac8;
                default -> "giblet_" + shape + "_" + (resting ? restFrame : (age / 3) % 4);
            };
        }

        public int tint() {
            float t = age / (float) Math.max(1, life);
            if (type == SPARK) {
                int g = Math.round(255 * (0.97f - 0.42f * t)), b = Math.round(255 * (0.75f - 0.62f * t));
                return 0xFF0000 | (g << 8) | b;
            }
            if (type == EMBER) {
                if (t < 0.25f) return lerp(0xFFF2B3, colour, t / 0.25f);
                if (t < 0.65f) return lerp(colour, 0x8C1F0D, (t - 0.25f) / 0.4f);
                return lerp(0x8C1F0D, 0x453A33, Math.min(1f, (t - 0.65f) / 0.2f));
            }
            if (type == FLAKE && glints) {
                boolean face = resting ? restFrame == 0 : TUMBLE[(age / 2) % TUMBLE.length] == 0;
                if (face && (resting ? age % 30 < 3 : (age / 2) % 3 == 0)) return lerp(colour, 0xFFFFFF, 0.6f);
            }
            return colour;
        }

        private static int lerp(int a, int b, float t) {
            t = Math.max(0f, Math.min(1f, t));
            int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
            int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
            int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
            return (r << 16) | (g << 8) | bl;
        }

        public int alpha() {
            float left = life - age;
            if (type == SPARK) {
                float t = age / (float) Math.max(1, life);
                return t > 0.7f ? Math.round(255 * (1f - (t - 0.7f) / 0.3f)) : 255;
            }
            if (type == DUST || type == WIND) {
                float t = age / (float) Math.max(1, life);
                return Math.round(255 * alphaScale * (t < 0.6f ? 0.8f : 0.8f * (1f - (t - 0.6f) / 0.4f)));
            }
            return left < 15 ? Math.round(255 * left / 15f) : 255;
        }

        public boolean bright() {
            return type == SPARK || type == SPIRIT || (type == EMBER && age < life * 0.8f);
        }
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
    private final List<Drop> born = new ArrayList<>();
    private boolean movingDrops;
    private final List<Fog> fogs = new ArrayList<>();
    private final List<Piece> pieces = new ArrayList<>();
    private final List<RunSim.Run> runs = new ArrayList<>();
    public final Mob mob = new Mob();
    public int figure;
    public static final int ZOMBIE = 0, YOU = 1, OTHER = 2;
    public com.simpleblood.BloodKind bloodKind;
    public com.simpleblood.HitContext.Weapon fixedWeapon;
    public String fixedItem;
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
            case HIT, DEATH, WEAPONS, GLOW -> add(new BlockPos(-1, 1, -1), Blocks.COBBLESTONE.defaultBlockState());
            case PUDDLE, RAIN, DRYING -> add(new BlockPos(0, 1, -1), Blocks.OAK_PLANKS.defaultBlockState());
            default -> {}
        }
        mob.present = !kind.blocksOnly();
        mob.armored = kind == Kind.ARMOR;
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

    public void bleeds(com.simpleblood.BloodKind k) {
        if (k == bloodKind) return;
        bloodKind = k;
        restart();
    }
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
    public List<Piece> pieces() { return pieces; }
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
            case HIT, WATER, GIBLETS, ARMOR, WEAPONS, GLOW, BONE, METAL, WOOD, EMBER, SPIRIT, WIND, COAT, COAT_DRYING -> {
                loopTick = HIT_AT - 1;
                burstTicks = 0;
                burstTicksLeft = 0;
            }
            case DEATH -> {
                if (mob.present && mob.deathTicks == 0) loopTick = HIT_AT - 1;
            }
            case RAIN, DRYING -> loopTick = 0;
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
        pieces.clear();
        runs.clear();
        if (coat != null) coat.dispose();
        coat = null;
        synchronized (LIVE) { LIVE.remove(this); }
    }

    private SimpleBloodConfig override;

    public void config(SimpleBloodConfig config) {
        override = config;
    }

    private SimpleBloodConfig cfg() {
        if (override != null) return override;
        SimpleBloodConfig c = SimpleBloodClient.getConfig();
        return c == null ? new SimpleBloodConfig() : c;
    }

    private long signature() {
        SimpleBloodConfig c = cfg();
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
        s = s * 31 + (c.surfaces.corpsePools ? 1 : 0);
        s = s * 31 + (c.gore.giblets ? 1 : 0) + (c.gore.gibletTrails ? 2 : 0);
        s = s * 31 + c.gore.gibletAmount;
        s = s * 31 + (c.combat.armorSparks ? 1 : 0) + (c.combat.armorShards ? 2 : 0) + (c.combat.armorClank ? 4 : 0);
        s = s * 31 + c.combat.armorBleed;
        SimpleBloodConfig.KindSettings k = c.kinds;
        s = s * 31 + k.boneAmount + k.boneDustOpacity * 7 + k.metalAmount * 13 + k.woodAmount * 17 + k.resinShare * 19;
        s = s * 31 + k.emberAmount + k.sparkAmount * 7 + k.windAmount * 13 + k.windOpacity * 17 + k.pieceLifetime * 19;
        s = s * 31 + (k.metalGlint ? 1 : 0) + (k.emberSmoke ? 2 : 0) + (k.glow ? 4 : 0) + (c.surfaces.rainWashes ? 8 : 0);
        s = s * 31 + c.mobCoat.amount + c.mobCoat.lifetimeSeconds * 7 + (c.mobCoat.runs ? 1 : 0) * 13 + (c.mobCoat.drips ? 1 : 0) * 17
                + (c.mobCoat.enabled ? 1 : 0) * 19;
        return s;
    }

    private void restart() {
        BloodCanvasAtlas atlas = BloodCanvasAtlas.exists() ? BloodCanvasAtlas.get() : null;
        for (CanvasTile t : tiles.values()) t.dispose(atlas);
        tiles.clear();
        drops.clear();
        fogs.clear();
        pieces.clear();
        runs.clear();
        loopTick = 0;
        mob.hurtTicks = 0;
        mob.deathTicks = 0;
        mob.present = !kind.blocksOnly();
        mob.x = 0.5;
        mob.y = 1;
        mob.z = kind == Kind.FOOTPRINTS ? WALK_FROM : 0.5;
        pool = null;
        realBurst = null;
        woundHeight = 1.15;
        burstShare = 1f;
        if (coat != null) coat.dispose();
        coat = null;
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
        SimpleBloodConfig c = cfg();
        mob.age++;
        if (mob.hurtTicks > 0) mob.hurtTicks--;
        if (!mob.walking) {
            mob.yaw = (float) Math.atan2(camera.x, camera.z);
            mob.amount += (0f - mob.amount) * 0.4f;
        }

        realTick();
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
            case DEATH -> deathTick(c);
            case ARMOR -> armorTick(c);
            case WEAPONS -> weaponTick(c);
            case BONE, METAL, WOOD, EMBER, SPIRIT, WIND -> {
                if (loopTick == HIT_AT) {
                    mob.hurtTicks = 10;
                    com.simpleblood.BloodKind which = switch (kind) {
                        case BONE -> com.simpleblood.BloodKind.BONE;
                        case METAL -> com.simpleblood.BloodKind.METAL;
                        case WOOD -> com.simpleblood.BloodKind.WOOD;
                        case EMBER -> com.simpleblood.BloodKind.EMBER;
                        case SPIRIT -> com.simpleblood.BloodKind.SPIRIT;
                        default -> com.simpleblood.BloodKind.WIND;
                    };
                    if (!realPieces(which)) throwPieces(c);
                }
                if (loopTick >= HIT_AT + 50) loopTick = HIT_AT - 1;
            }
            case GLOW -> {
                if (loopTick == HIT_AT) hit(c);
                if (burstTicksLeft > 0) burstTick(c);
                if (loopTick >= HIT_AT + 50) loopTick = HIT_AT - 1;
            }
            case RAIN -> rainTick(c);
            case COAT -> coatTick(c);
            case COAT_DRYING -> coatDryingTick(c);
            case DRYING -> dryingTick(c);
            case GIBLETS -> {
                if (loopTick == HIT_AT) {
                    hit(c);
                    giblets(c, 1 + rng.nextInt(2));
                }
                if (burstTicksLeft > 0) burstTick(c);
                if (loopTick >= HIT_AT + 55) loopTick = HIT_AT - 1;
            }
            case PUDDLE -> {
                if (loopTick % 7 == 0) rainDrop(c);
                if (loopTick >= 400) loopTick = 0;
            }
            case MIX -> {
                if (loopTick == 1) mixPuddles();
                if (loopTick > 1 && loopTick < 120 && loopTick % 2 == 0) mixDrop((loopTick / 2) % 2 == 0);
                if (loopTick >= 200) restart();
            }
            case FOOTPRINTS -> walkTick(c);
        }
        moveDrops(c);
        movePieces(c);
        moveFogs();
        advanceRuns(c);
        if ((now & 3) == 0) relax(c);
        if ((now & 7) == 0) age();
        upload(c);
    }

    private void clouds(SimpleBloodConfig c) {
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

    private void death(SimpleBloodConfig c) {
        throwing = true;
        try {
            deathSpawns(c);
        } finally {
            throwing = false;
        }
    }

    private void deathSpawns(SimpleBloodConfig c) {
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

    private boolean onArmor;

    private void armorTick(SimpleBloodConfig c) {
        if (loopTick == HIT_AT) {
            onArmor = !onArmor;
            woundHeight = onArmor ? 1.2 : 0.45;
            burstShare = onArmor ? Math.max(0, Math.min(100, c.combat.armorBleed)) / 100f : 1f;
            mob.hurtTicks = 10;
            if (c.particles.hitBurst && realArmorHit(onArmor)) {
                if (onArmor) com.simpleblood.Tally.count("preview.armor");
                return;
            }
            hit(c);
            if (onArmor) {
                Vec3 at = wound();
                if (c.combat.armorSparks) sparks(at, camera);
                if (c.combat.armorShards) flakes(at, camera, 0xC7C7CC);
                if (c.combat.armorClank) clank();
            }
        }
        if (burstTicksLeft > 0) burstTick(c);
        if (loopTick >= HIT_AT + 45) loopTick = HIT_AT - 1;
    }

    private void sparks(Vec3 at, Vec3 back) {
        int n = 3 + rng.nextInt(3);
        for (int i = 0; i < n; i++) {
            Vec3 dir = back.add((rng.nextDouble() - 0.5) * 1.6, 0.2 + rng.nextDouble() * 0.6, (rng.nextDouble() - 0.5) * 1.6).normalize();
            Piece p = piece(Piece.SPARK, at, dir.scale(0.08 + rng.nextDouble() * 0.1));
            p.life = 5 + rng.nextInt(6);
            p.size = 0.05f * (0.8f + rng.nextFloat() * 0.4f);
            p.gravity = 0.035f;
            p.drag = 0.9f;
        }
    }

    private void flakes(Vec3 at, Vec3 back, int metal) {
        int n = 1 + rng.nextInt(2);
        for (int i = 0; i < n; i++) {
            Vec3 dir = back.add((rng.nextDouble() - 0.5) * 1.2, 0.4 + rng.nextDouble() * 0.5, (rng.nextDouble() - 0.5) * 1.2).normalize();
            Piece p = piece(Piece.FLAKE, at, dir.scale(0.06 + rng.nextDouble() * 0.06));
            p.shape = rng.nextInt(3);
            p.restFrame = rng.nextBoolean() ? 0 : 5;
            p.life = 60 + rng.nextInt(20);
            p.size = 0.045f * (1f + rng.nextFloat() * 0.3f);
            p.gravity = 0.04f;
            p.colour = metal;
        }
    }

    private void clank() {
        SimpleBloodConfig c = cfg();
        if (!c.audio.soundEnabled || c.audio.muted(com.simpleblood.BloodSounds.Preview.ARMOR.name())) return;
        float volume = 0.35f * c.soundVolumeMultiplier() * Math.max(0, c.audio.armorVolume) / 100f;
        if (volume <= 0) return;
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.CHAIN_HIT, (0.85f + rng.nextFloat() * 0.3f) * c.soundPitchMultiplier(), volume));
    }

    private int weapon = -1;
    private static final String[] WEAPONS = {"simpleblood.preview.a_sword", "simpleblood.preview.an_axe", "simpleblood.preview.a_mace", "simpleblood.preview.an_arrow", "simpleblood.preview.a_spear", "simpleblood.preview.a_fist"};
    private static final com.simpleblood.HitContext.Weapon[] WEAPON_KINDS = {com.simpleblood.HitContext.Weapon.BLADE,
            com.simpleblood.HitContext.Weapon.AXE, com.simpleblood.HitContext.Weapon.MACE, com.simpleblood.HitContext.Weapon.ARROW,
            com.simpleblood.HitContext.Weapon.SPEAR, com.simpleblood.HitContext.Weapon.OTHER};

    private void weaponTick(SimpleBloodConfig c) {
        if (loopTick == HIT_AT) {
            int fixed = fixedWeapon == null ? -1 : java.util.Arrays.asList(WEAPON_KINDS).indexOf(fixedWeapon);
            weapon = fixed >= 0 ? fixed : (weapon + 1) % (WEAPONS.length - 1);
            woundHeight = 1.15;
            mob.hurtTicks = 10;
            if (c.particles.hitBurst && realHit(WEAPON_KINDS[weapon])) {
                if (loopTick >= HIT_AT + 45) loopTick = HIT_AT - 1;
                return;
            }
            hit(c);
            if (c.directional.enabled && c.directional.weaponFlavour) {
                switch (weapon) {
                    case 0 -> sweep(c);
                    case 1 -> axe(c);
                    case 2 -> mace(c);
                    case 3 -> arrow(c);
                    default -> spear(c);
                }
            }
        }
        if (burstTicksLeft > 0) burstTick(c);
        if (loopTick >= HIT_AT + 45) loopTick = HIT_AT - 1;
    }

    private void axe(SimpleBloodConfig c) {
        Vec3 w = wound();
        for (int i = 0; i < 8; i++) {
            spawn(w.x + (rng.nextDouble() - 0.5) * 0.2, w.y, w.z + (rng.nextDouble() - 0.5) * 0.2,
                    camera.x * 0.2 + (rng.nextDouble() - 0.5) * 0.2, -1.4, camera.z * 0.2 + (rng.nextDouble() - 0.5) * 0.2, 1);
        }
        pour(new Vec3(mob.x, 1, mob.z).add(camera.x * 0.45, 0, camera.z * 0.45), Math.round(14 * c.surfaceAmount()));
    }

    private void mace(SimpleBloodConfig c) {
        Vec3 at = new Vec3(mob.x, 1, mob.z).add(camera.x * 0.3, 0, camera.z * 0.3);
        pour(at, Math.round(20 * c.surfaceAmount()));
        for (int i = 0; i < 10; i++) {
            double a = i / 10.0 * Math.PI * 2 + rng.nextDouble() * 0.4;
            Drop d = spawn(at.x, 1.15, at.z, 0, 0, 0, 1);
            d.vx = Math.cos(a) * 0.14; d.vy = 0.12 + rng.nextDouble() * 0.06; d.vz = Math.sin(a) * 0.14;
        }
        Vec3 w = wound();
        for (int i = 0; i < 6; i++) {
            Drop d = spawn(w.x, w.y, w.z, 0, 0, 0, 0);
            d.vx = (rng.nextDouble() - 0.5) * 0.08; d.vy = 0.35 + rng.nextDouble() * 0.15; d.vz = (rng.nextDouble() - 0.5) * 0.08;
        }
    }

    private void arrow(SimpleBloodConfig c) {
        Vec3 back = camera.scale(-1);
        Vec3 exit = new Vec3(mob.x, wound().y, mob.z).add(back.scale(0.32));
        for (int i = 0; i < 7; i++) {
            Vec3 v = back.scale(0.18 + rng.nextDouble() * 0.12).add((rng.nextDouble() - 0.5) * 0.06, rng.nextDouble() * 0.05, (rng.nextDouble() - 0.5) * 0.06);
            Drop d = spawn(exit.x, exit.y + (rng.nextDouble() - 0.5) * 0.1, exit.z, 0, 0, 0, i < 2 ? 2 : 1);
            d.vx = v.x; d.vy = v.y; d.vz = v.z;
        }
    }

    private void spear(SimpleBloodConfig c) {
        Vec3 back = camera.scale(-1);
        Vec3 w = wound();
        Vec3 exit = new Vec3(mob.x, w.y, mob.z).add(back.scale(0.32));
        for (int i = 0; i < 11; i++) {
            Vec3 v = back.scale(0.2 + rng.nextDouble() * 0.16).add((rng.nextDouble() - 0.5) * 0.05, 0.02 + rng.nextDouble() * 0.05, (rng.nextDouble() - 0.5) * 0.05);
            Drop d = spawn(exit.x, exit.y + (rng.nextDouble() - 0.5) * 0.1, exit.z, 0, 0, 0, i < 4 ? 2 : 1);
            d.vx = v.x; d.vy = v.y; d.vz = v.z;
        }
        for (int i = 0; i < 6; i++) {
            Drop d = spawn(w.x + (rng.nextDouble() - 0.5) * 0.06, w.y, w.z + (rng.nextDouble() - 0.5) * 0.06, 0, 0, 0, 2);
            d.vx = camera.x * 0.04; d.vy = -0.03; d.vz = camera.z * 0.04;
        }
    }

    private void throwPieces(SimpleBloodConfig c) {
        SimpleBloodConfig.KindSettings k = c.kinds;
        float life = k.pieceLifetimeMultiplier();
        Vec3 w = wound();
        com.simpleblood.BloodKind which = switch (kind) {
            case BONE -> com.simpleblood.BloodKind.BONE;
            case METAL -> com.simpleblood.BloodKind.METAL;
            case WOOD -> com.simpleblood.BloodKind.WOOD;
            case EMBER -> com.simpleblood.BloodKind.EMBER;
            case SPIRIT -> com.simpleblood.BloodKind.SPIRIT;
            default -> com.simpleblood.BloodKind.WIND;
        };
        int n = Math.round(8 * k.amount(which) * c.masterAmount());
        for (int i = 0; i < n; i++) {
            Vec3 dir = camera.add((rng.nextDouble() - 0.5) * 1.8, 0.3 + rng.nextDouble() * 0.7, (rng.nextDouble() - 0.5) * 1.8).normalize();
            switch (kind) {
                case BONE -> {
                    if (i % 2 == 0) {
                        Piece p = piece(Piece.CHIP, w, dir.scale(0.08 + rng.nextDouble() * 0.08));
                        p.shape = rng.nextInt(4); p.restFrame = rng.nextInt(4); p.life = Math.round((60 + rng.nextInt(20)) * life);
                        p.size = 0.06f; p.gravity = 0.04f; p.colour = 0xE6DFC8;
                    } else {
                        Piece p = piece(Piece.DUST, w, dir.scale(0.02 + rng.nextDouble() * 0.03));
                        p.life = Math.round((25 + rng.nextInt(15)) * life); p.size = 0.16f; p.gravity = -0.001f; p.drag = 0.9f;
                        p.collides = false; p.colour = 0xE6DFC8; p.alphaScale = k.boneDustOpacity / 100f;
                    }
                }
                case METAL -> {
                    Piece p = piece(Piece.FLAKE, w, dir.scale(0.07 + rng.nextDouble() * 0.07));
                    p.shape = rng.nextInt(3); p.restFrame = rng.nextBoolean() ? 0 : 5; p.life = Math.round((70 + rng.nextInt(30)) * life);
                    p.size = 0.06f; p.gravity = 0.04f; p.colour = 0xA8A8AC; p.glints = k.metalGlint;
                }
                case WOOD -> {
                    if (rng.nextInt(100) < k.resinShare) {
                        Drop d = spawn(w.x, w.y, w.z, dir.x, dir.y - 0.5, dir.z, rng.nextInt(3) == 0 ? 1 : 0);
                        d.colour = 0xE07A2E;
                    } else {
                        Piece p = piece(Piece.SPLINTER, w, dir.scale(0.08 + rng.nextDouble() * 0.08));
                        p.shape = rng.nextInt(4); p.restFrame = rng.nextInt(4); p.life = Math.round((60 + rng.nextInt(20)) * life);
                        p.size = 0.06f; p.gravity = 0.04f; p.colour = 0x7A5A3E;
                    }
                }
                case EMBER -> {
                    Piece p = piece(Piece.EMBER, w, dir.scale(0.06 + rng.nextDouble() * 0.06).add(0, 0.05, 0));
                    p.restFrame = rng.nextInt(4); p.life = Math.round((40 + rng.nextInt(25)) * life);
                    p.size = 0.045f; p.gravity = 0.022f; p.drag = 0.96f; p.colour = 0xFFA62B;
                    if (k.emberSmoke && rng.nextInt(5) == 0) {
                        Piece smoke = piece(Piece.DUST, w, new Vec3(0, 0.03, 0));
                        smoke.life = Math.round(40 * life); smoke.size = 0.14f; smoke.gravity = -0.002f; smoke.drag = 0.95f;
                        smoke.collides = false; smoke.colour = 0x4A4A4A; smoke.alphaScale = 0.6f;
                    }
                }
                case SPIRIT -> {
                    Piece p = piece(Piece.SPIRIT, w, dir.scale(0.03 + rng.nextDouble() * 0.04));
                    p.life = Math.round((16 + rng.nextInt(10)) * life); p.size = 0.07f; p.gravity = -0.002f; p.drag = 0.9f;
                    p.collides = false; p.colour = 0xB8F2FF;
                }
                default -> {
                    if (i % 2 == 1) continue;
                    Piece p = piece(Piece.WIND, w, dir.scale(0.04 + rng.nextDouble() * 0.04));
                    p.life = Math.round((20 + rng.nextInt(10)) * life); p.size = 0.26f; p.gravity = 0f; p.drag = 0.92f;
                    p.collides = false; p.colour = 0xD6E6FF; p.alphaScale = k.windOpacity / 100f;
                }
            }
        }
    }

    public boolean dark() {
        return kind == Kind.GLOW;
    }

    public boolean bloodGlows() {
        return kind == Kind.GLOW && cfg().kinds.glow;
    }

    private com.simpleblood.mob.PreviewCoat coat;
    private int coatBlow = -1;
    private static final com.simpleblood.HitContext.Weapon[] COAT_WEAPONS = {
            com.simpleblood.HitContext.Weapon.BLADE, com.simpleblood.HitContext.Weapon.MACE,
            com.simpleblood.HitContext.Weapon.AXE, com.simpleblood.HitContext.Weapon.ARROW,
            com.simpleblood.HitContext.Weapon.SPEAR};

    public com.simpleblood.mob.PreviewCoat coat() { return coat; }

    public int coatLifetime() {
        if (kind == Kind.COAT_DRYING) return timeLapse(cfg().mobCoat.lifetimeSeconds);
        return Math.max(60, Math.max(1, cfg().mobCoat.lifetimeSeconds) * 20 / LIFETIME_DIVISOR);
    }

    static int timeLapse(int seconds) {
        double f = Math.log(Math.max(5, Math.min(600, seconds)) / 5.0) / Math.log(120.0);
        return (int) Math.round(40 + f * 360);
    }

    private String lasted(String what, int shownTicks, int seconds) {
        int since = Math.max(0, kind == Kind.COAT_DRYING ? loopTick - HIT_AT : loopTick);
        int real = (int) Math.min(seconds, Math.round(since / (double) Math.max(1, shownTicks) * seconds));
        return Lang.t("simpleblood.preview.lasted", what, real, seconds);
    }

    private void coatDryingTick(SimpleBloodConfig c) {
        if (coat == null) coat = new com.simpleblood.mob.PreviewCoat(colour.getAsInt() & 0xFFFFFF);
        if (loopTick == HIT_AT) {
            coat.dispose();
            coat = new com.simpleblood.mob.PreviewCoat(colour.getAsInt() & 0xFFFFFF);
            woundHeight = 1.15;
            hit(c);
            float amount = Math.max(0.5f, Math.max(0, c.mobCoat.amount) / 100f);
            Vec3 w = wound();
            coat.hit(w.add(camera.scale(0.35)), camera.scale(-1), com.simpleblood.HitContext.Weapon.BLADE, 1.2f * amount, 3, colour.getAsInt() & 0xFFFFFF);
        }
        if (burstTicksLeft > 0) burstTick(c);
        coat.tick(coatLifetime(), c.surfaces.opacity / 100f);
        coat.takeDrips((at, col) -> {
            Drop d = spawn(at.x, at.y, at.z, 0, -0.05, 0, 3);
            d.colour = col;
        });
        if (loopTick >= HIT_AT + coatLifetime() + 30) loopTick = HIT_AT - 1;
    }

    private void coatTick(SimpleBloodConfig c) {
        if (coat == null) coat = new com.simpleblood.mob.PreviewCoat(colour.getAsInt() & 0xFFFFFF);
        if (loopTick == HIT_AT) {
            coatBlow = (coatBlow + 1) % COAT_WEAPONS.length;
            woundHeight = 0.85 + rng.nextDouble() * 0.7;
            hit(c);
            float amount = Math.max(0, c.mobCoat.amount) / 100f;
            if (amount > 0) {
                Vec3 w = wound();
                com.simpleblood.HitContext.Weapon weapon = c.directional.weaponFlavour ? COAT_WEAPONS[coatBlow] : com.simpleblood.HitContext.Weapon.NONE;
                coat.hit(w.add(camera.scale(0.35)), camera.scale(-1), weapon, 1.2f * amount, 3, colour.getAsInt() & 0xFFFFFF);
            }
        }
        if (burstTicksLeft > 0) burstTick(c);
        if (c.particles.lowHealthDrip && loopTick > HIT_AT && loopTick % 12 == 0) coat.seep(1);
        coat.tick(coatLifetime(), c.surfaces.opacity / 100f);
        coat.takeDrips((at, col) -> {
            Drop d = spawn(at.x, at.y, at.z, 0, -0.05, 0, 3);
            d.colour = col;
        });
        if (loopTick >= HIT_AT + 60) loopTick = HIT_AT - 1;
    }

    public final List<Vec3> rain = new ArrayList<>();

    private void rainTick(SimpleBloodConfig c) {
        if (loopTick == 1) layPuddle(c);
        for (int i = 0; i < 3; i++) {
            rain.add(new Vec3(-1 + rng.nextDouble() * 3, 3.4 + rng.nextDouble() * 0.6, -1 + rng.nextDouble() * 3));
        }
        rain.replaceAll(v -> v.add(0, -0.35, 0));
        rain.removeIf(v -> v.y < 1.0);
        if (c.surfaces.rainWashes && (now & 1) == 0) age();
        if (loopTick >= 240) loopTick = 0;
    }

    private void dryingTick(SimpleBloodConfig c) {
        if (loopTick == 1) layPuddle(c);
        if (loopTick >= lifetime() + 40) loopTick = 0;
    }

    private void layPuddle(SimpleBloodConfig c) {
        if (!c.surfaces.enabled) return;
        int n = Math.round(60 * (c.surfaces.resolution == 16 ? 3 : 1) / 3f * Math.max(0.3f, c.surfaceAmount()));
        pour(new Vec3(0.5, 1, 0.55), n);
        pour(new Vec3(0.15, 1, 0.9), n / 3);
    }

    public String caption() {
        SimpleBloodConfig c = cfg();
        return switch (kind) {
            case HIT -> Lang.t("simpleblood.preview.a_hit");
            case DRIP -> Lang.t("simpleblood.preview.badly_hurt");
            case DEATH -> mob.present ? Lang.t("simpleblood.preview.a_killing_blow") : Lang.t("simpleblood.preview.gone_to_dust");
            case PUDDLE -> Lang.t("simpleblood.preview.drops_landing_on_blocks");
            case MIX -> c.surfaces.mixing ? Lang.t("simpleblood.preview.two_kinds_of_blood_meeting") : Lang.t("simpleblood.preview.two_kinds_of_blood_mixing");
            case FOOTPRINTS -> Lang.t("simpleblood.preview.walking_through_blood");
            case WATER -> Lang.t("simpleblood.preview.bleeding_under_water");
            case ARMOR -> onArmor ? Lang.t("simpleblood.preview.on_the_iron_chestplate") : Lang.t("simpleblood.preview.on_the_bare_leg");
            case GIBLETS -> c.gore.giblets ? Lang.t("simpleblood.preview.a_hard_hit") : Lang.t("simpleblood.preview.a_hard_hit_giblets_are");
            case WEAPONS -> weapon < 0 ? "" : Lang.t(WEAPONS[weapon]) + (c.directional.weaponFlavour ? "" : Lang.t("simpleblood.preview.flavour_is_off"));
            case BONE -> Lang.t("simpleblood.preview.bone_like_a_skeleton");
            case METAL -> Lang.t("simpleblood.preview.metal_like_an_iron_golem");
            case WOOD -> Lang.t("simpleblood.preview.wood_like_the_creaking");
            case EMBER -> Lang.t("simpleblood.preview.embers_like_a_blaze");
            case SPIRIT -> Lang.t("simpleblood.preview.sparks_like_an_allay");
            case WIND -> Lang.t("simpleblood.preview.wind_like_a_breeze");
            case GLOW -> c.kinds.glow ? Lang.t("simpleblood.preview.glow_squid_ink_in_the") : Lang.t("simpleblood.preview.in_the_dark_glow_is");
            case RAIN -> c.surfaces.rainWashes ? Lang.t("simpleblood.preview.rain_washing_it_away") : Lang.t("simpleblood.preview.rain_washing_is_off");
            case DRYING -> lasted(Lang.t("simpleblood.preview.drying"), lifetime(), c.surfaces.lifetimeSeconds);
            case COAT_DRYING -> lasted(Lang.t("simpleblood.preview.drying_on_the_body"), coatLifetime(), c.mobCoat.lifetimeSeconds);
            case COAT -> c.mobCoat.enabled ? Lang.t("simpleblood.preview.blood_that_stays_on_the") : Lang.t("simpleblood.preview.blood_on_mobs_is_off");
        };
    }

    private void giblets(SimpleBloodConfig c, int n) {
        if (!c.gore.giblets && kind != Kind.GIBLETS) return;
        n = Math.max(1, Math.round(n * Math.max(0, c.gore.gibletAmount) / 100f));
        int blood = colour.getAsInt() & 0xFFFFFF;
        int flesh = com.simpleblood.GibletColours.forBlood(blood);
        double towards = Math.atan2(camera.z, camera.x);
        Vec3 from = wound();
        for (int i = 0; i < n; i++) {
            double a = towards + (rng.nextDouble() - 0.5) * Math.toRadians(170);
            double out = 0.06 + rng.nextDouble() * 0.1;
            Piece p = piece(Piece.GIBLET, from, new Vec3(Math.cos(a) * out, 0.12 + rng.nextDouble() * 0.12, Math.sin(a) * out));
            p.shape = rng.nextInt(4);
            p.restFrame = rng.nextInt(4);
            p.life = 90 + rng.nextInt(30);
            p.size = 0.085f * (1f + rng.nextFloat() * 0.3f);
            p.gravity = 0.03f;
            p.drag = 0.98f;
            p.colour = flesh;
            p.blood = blood;
        }
    }

    private static int mix(int a, int b) {
        int r = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)) / 2;
        int g = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)) / 2;
        int bl = ((a & 0xFF) + (b & 0xFF)) / 2;
        return (r << 16) | (g << 8) | bl;
    }

    private Piece piece(int type, Vec3 at, Vec3 v) {
        Piece p = new Piece();
        p.type = type;
        p.x = at.x; p.y = at.y; p.z = at.z;
        p.vx = v.x; p.vy = v.y; p.vz = v.z;
        pieces.add(p);
        return p;
    }

    private void movePieces(SimpleBloodConfig c) {
        Iterator<Piece> it = pieces.iterator();
        while (it.hasNext()) {
            Piece p = it.next();
            if (++p.age >= p.life || p.y < -1.5) {
                it.remove();
                continue;
            }
            if (p.resting) continue;
            double px = p.x, py = p.y, pz = p.z;
            p.x += p.vx; p.y += p.vy; p.z += p.vz;
            p.vy -= p.gravity;
            p.vx *= p.drag; p.vy *= p.drag; p.vz *= p.drag;
            if (p.type == Piece.GIBLET && c.gore.gibletTrails && p.age % 3 == 0) {
                Drop d = spawn(p.x, p.y, p.z, 0, -0.1, 0, 0);
                d.vx = 0; d.vz = 0; d.vy = -0.05;
                d.colour = p.blood;
            }
            boolean gone = false;
            for (Block b : p.collides ? blocks : List.<Block>of()) {
                if (!new AABB(b.pos()).contains(p.x, p.y, p.z)) continue;
                double top = b.pos().getY() + 1;
                if (py >= top - 0.05 && p.vy < 0) {
                    if (p.type == Piece.SPARK) { gone = true; break; }
                    p.y = top + 0.01;
                    p.vx = p.vy = p.vz = 0;
                    p.resting = true;
                    if (p.type == Piece.GIBLET && c.surfaces.enabled && !blockMap.containsKey(b.pos().above())) {
                        int px2 = Math.max(1, Math.round(5 * c.surfaceAmount() * (c.surfaces.resolution >= 16 ? 3f : 1f)));
                        stampAt(b, Direction.UP, new Vec3(p.x, top, p.z), p.blood, px2, new Vec3(0, -1, 0));
                    }
                } else {
                    p.x = px; p.z = pz;
                    p.vx *= -0.3; p.vz *= -0.3;
                }
                break;
            }
            if (gone) it.remove();
        }
    }

    private static final class Pool {
        Vec3 middle;
        List<Vec3> outline = new ArrayList<>();
        int bled;
    }

    private Pool pool;

    private void deathTick(SimpleBloodConfig c) {
        if (loopTick == HIT_AT && mob.present && mob.deathTicks == 0) {
            woundHeight = 1.15;
            if (!c.particles.deathBurst || !realDeath()) {
                death(c);
                giblets(c, 3 + rng.nextInt(2));
            }
            mob.deathTicks = 1;
            mob.vx = -camera.x * 0.1;
            mob.vz = -camera.z * 0.1;
            mob.vy = 0.22;
        }
        if (mob.deathTicks > 0) {
            mob.deathTicks++;
            mob.x += mob.vx; mob.y += mob.vy; mob.z += mob.vz;
            mob.vy -= 0.06;
            boolean down = mob.y <= 1;
            if (down) {
                mob.y = 1;
                mob.vy = 0;
                mob.vx *= 0.4;
                mob.vz *= 0.4;
            }
            if (down && mob.deathTicks >= 8 && c.surfaces.enabled && c.surfaces.corpsePools) {
                if (pool == null) settlePool();
                bleedPool(c);
            }
            if (mob.deathTicks >= 20) {
                mob.present = false;
                mob.deathTicks = 0;
                pool = null;
            }
        }
        if (!mob.present && loopTick >= HIT_AT + 75) {
            mob.present = true;
            mob.x = 0.5; mob.y = 1; mob.z = 0.5;
            loopTick = HIT_AT - 1;
        }
    }

    public Vec3 lyingDirection() {
        return new Vec3(Math.cos(mob.yaw), 0, -Math.sin(mob.yaw));
    }

    private void settlePool() {
        pool = new Pool();
        Vec3 head = lyingDirection(), side = new Vec3(-head.z, 0, head.x);
        for (double along = 0.1; along <= 1.8; along += 0.25) {
            for (int s = -1; s <= 1; s += 2) {
                pool.outline.add(new Vec3(mob.x, 1, mob.z).add(head.scale(along)).add(side.scale(s * 0.25)));
            }
        }
        pool.middle = new Vec3(mob.x, 1, mob.z).add(head.scale(0.9));
        pour(pool.middle, 10);
    }

    private void bleedPool(SimpleBloodConfig c) {
        double reach = Math.min(1.15, 0.35 + pool.bled * 0.08);
        for (int k = 0; k < 3; k++) {
            Vec3 to = pool.outline.get(rng.nextInt(pool.outline.size()));
            double f = reach * (0.75 + rng.nextDouble() * 0.3);
            Vec3 at = pool.middle.add(to.subtract(pool.middle).scale(f));
            pour(at, Math.round((6 + rng.nextInt(5)) * 0.85f));
        }
        pour(pool.middle, 3);
        pool.bled++;
    }

    private void powderAt(Vec3 at, int pixels) {
        Block b = blockMap.get(new BlockPos((int) Math.floor(at.x), 0, (int) Math.floor(at.z)));
        if (b == null || blockMap.containsKey(b.pos().above())) return;
        CanvasTile tile = tile(b, Direction.UP, true);
        Vec3 p = new Vec3(at.x, 1, at.z);
        tile.stampPowder(clamp(tile.pixelX(p), tile.pw), clamp(tile.pixelY(p), tile.ph), pixels, colour.getAsInt() & 0xFFFFFF, now, lifetime(), rng, new ArrayList<>());
    }

    private void pour(Vec3 at, int pixels) {
        Block b = blockMap.get(new BlockPos((int) Math.floor(at.x), 0, (int) Math.floor(at.z)));
        if (b == null || blockMap.containsKey(b.pos().above())) return;
        stampAt(b, Direction.UP, new Vec3(at.x, 1, at.z), colour.getAsInt() & 0xFFFFFF, pixels, new Vec3(0, -1, 0));
    }

    private static final double WALK_FROM = -1.55, WALK_TO = 2.55, WALK_SPEED = 0.08;
    private static final int TURN_TICKS = 14;
    private int walkDir = 1, turnTicks;
    private float turnFrom, turnTo;
    private int lastStepIndex = Integer.MIN_VALUE;
    private double footWet;
    private boolean puddleLaid;

    private void walkTick(SimpleBloodConfig c) {
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
        if (found > 0) {
            footWet = Math.min(c.surfaces.footprintSteps, footWet + found * 0.75);
            if (c.surfaces.wading) {
                List<int[]> sole = Footprints.rasterise(gait.sole(), fx, fz, 0);
                Wading.stepIn(ground, this::splashDrops, foot, sole, Footprints.ring(sole, Footprints.rasterise(gait.sole(), fx, fz, 2)),
                        0, mob.z - pz, 0, ps, new Wading.Look(lifetime(), pattern(), c.surfaces.wadeSplash, c.surfaces.wakes, c.surfaces.trample), now, rng);
            }
            return;
        }
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

    private final Wading.Ground ground = (p, create) -> {
        Block b = blockMap.get(new BlockPos((int) Math.floor(p.x), 0, (int) Math.floor(p.z)));
        return b == null ? null : tile(b, Direction.UP, create);
    };

    private void splashDrops(List<double[]> thrown, int rgb, int born, int life) {
        SimpleBloodConfig c = cfg();
        for (double[] t : thrown) {
            Drop d = spawn(t[0], t[1], t[2], t[3], t[4], t[5], 4);
            d.size = 0.25f * 0.6f;
            d.life = 40;
            d.single = true;
            d.colour = rgb;
        }
    }

    private int burstTicks, burstTicksLeft;
    private static final float DAMAGE = 6f, MAX_HEALTH = 20f;

    private float severity() {
        return Math.min(1f, 0.6f * Math.min(1f, DAMAGE / 20f) + 0.4f * Math.min(1f, DAMAGE / MAX_HEALTH));
    }

    private double woundHeight = 1.15;
    private float burstShare = 1f;

    private Vec3 wound() {
        Vec3 facing = new Vec3(Math.sin(mob.yaw), 0, Math.cos(mob.yaw));
        return new Vec3(mob.x, mob.y + woundHeight, mob.z).add(facing.scale(0.32));
    }

    private void hit(SimpleBloodConfig c) {
        mob.hurtTicks = 10;
        if (!c.particles.hitBurst) return;
        if ((kind == Kind.HIT || kind == Kind.GIBLETS || kind == Kind.GLOW) && realHit(null)) return;
        realKind = null;
        float t = Math.min(DAMAGE / 20f, 1f);
        int ticks = 3 + (int) (13 * t);
        burstTicks = Math.max(1, (int) (ticks * c.burstDurationMultiplier()));
        burstTicksLeft = burstTicks;
        if (c.directional.enabled && c.directional.weaponFlavour && kind == Kind.HIT && shows(Aspect.SWEEP)) sweep(c);
    }

    private com.simpleblood.ClientBloodBurstTask realBurst;
    private static net.minecraft.world.entity.LivingEntity actor;
    private static Object actorLevel;
    private static final int STAND_IN_ID = -424242;

    private net.minecraft.world.entity.LivingEntity actor() {
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return null;
        if (actor == null || actorLevel != level) {
            try {
                //? if <=1.21.1 {
                /*actor = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
                *///?} elif <26.2 {
                actor = net.minecraft.world.entity.EntityType.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
                //?} else {
                /*actor = (net.minecraft.world.entity.LivingEntity) net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
                *///?}
                actorLevel = level;
            } catch (Throwable t) {
                actor = null;
            }
            if (actor == null) return null;
            actor.setId(STAND_IN_ID);
        }
        actor.setPos(mob.x, mob.y, mob.z);
        actor.setYRot((float) Math.toDegrees(-mob.yaw));
        actor.yBodyRot = actor.getYRot();
        return actor;
    }

    private boolean realHit(com.simpleblood.HitContext.Weapon weapon) {
        net.minecraft.world.entity.LivingEntity a = actor();
        if (a == null) return false;
        if (!kind.pieces()) realKind = bloodKind;
        if (weapon == null) woundHeight = 1.15;
        Vec3 w = wound();
        if (a instanceof com.simpleblood.BloodEntityAccess acc) acc.simpleblood$setWound(w);
        String item = kind == Kind.WEAPONS ? fixedItem : null;
        com.simpleblood.HitContext ctx = com.simpleblood.HitContext.replay(a, DAMAGE, camera.scale(-1).normalize(),
                weapon == null ? com.simpleblood.HitContext.Weapon.BLADE : weapon, item, false, 1f, w, false);
        com.simpleblood.Tally.count("preview.real");
        runReal(() -> realBurst = new com.simpleblood.ClientBloodBurstTask(
                (net.minecraft.client.multiplayer.ClientLevel) a.level(), a, ctx).scaled(burstShare));
        burstTicksLeft = 0;
        burstTicks = 20;
        return true;
    }

    private void realTick() {
        if (realBurst == null) return;
        actor();
        runReal(() -> {
            if (!realBurst.tick()) realBurst = null;
        });
    }

    private void runReal(Runnable code) {
        com.simpleblood.BloodKind k = shownKind();
        com.simpleblood.Capture.run(sink, cfg(), () -> {
            com.simpleblood.BloodKind was = com.simpleblood.Capture.kind;
            com.simpleblood.Capture.kind = k;
            try {
                code.run();
            } finally {
                com.simpleblood.Capture.kind = was;
            }
        });
    }

    private com.simpleblood.BloodKind shownKind() {
        return realKind != null ? realKind : bloodKind;
    }

    private final com.simpleblood.Capture.Sink sink = new com.simpleblood.Capture.Sink() {
        @Override
        public void particle(net.minecraft.core.particles.ParticleOptions type, double x, double y, double z,
                             double vx, double vy, double vz, int rgb, boolean exact) {
            int drop = type == com.simpleblood.BloodParticles.BLOOD_DRIP ? 0 : type == com.simpleblood.BloodParticles.BLOOD_SPLASH ? 1
                    : type == com.simpleblood.BloodParticles.BLOOD_STREAK ? 2 : type == com.simpleblood.BloodParticles.BLOOD_DROP ? 3
                    : type == com.simpleblood.BloodParticles.BLOOD_SPRAY ? 4 : -1;
            if (drop >= 0) {
                boolean shown = switch (com.simpleblood.Capture.part) {
                    case DRIPS -> shows(Aspect.DRIPS);
                    case SPLASH -> shows(Aspect.SPLASH);
                    case DIRECTIONAL -> shows(Aspect.DIRECTIONAL);
                    case EXIT -> shows(Aspect.EXIT);
                    case FLAVOUR -> shows(Aspect.SWEEP);
                    default -> true;
                };
                if (!shown) return;
                throwing = com.simpleblood.particle.BloodParticle.spawnsThrown();
                Drop d;
                try {
                    d = spawn(x, y, z, vx, vy, vz, drop);
                    if (exact && drop < 2) { d.vx = vx; d.vy = vy; d.vz = vz; if (throwing) thrown(d); }
                } finally {
                    throwing = false;
                }
                if (com.simpleblood.Capture.kind == com.simpleblood.BloodKind.WOOD) d.colour = 0xE07A2E;
                com.simpleblood.Tally.count("preview.drop");
                return;
            }
            capturedPiece(type, new Vec3(x, y, z), new Vec3(vx, vy, vz), rgb, exact);
        }

        @Override
        public BloodSurfaces.Hit trace(Vec3 from, Vec3 to) {
            BloodSurfaces.Hit best = null;
            double bestD = Double.MAX_VALUE;
            for (Block b : blocks) {
                java.util.Optional<Vec3> clip = new AABB(b.pos()).clip(from, to);
                if (clip.isEmpty()) continue;
                double d = clip.get().distanceToSqr(from);
                if (d >= bestD) continue;
                Vec3 dir = to.subtract(from);
                bestD = d;
                best = new BloodSurfaces.Hit(b.pos(), faceOf(b.pos(), clip.get(), dir.x, dir.y, dir.z), clip.get());
            }
            return best;
        }

        @Override
        public void paint(BloodSurfaces.Hit hit, int pixels) {
            if (com.simpleblood.Capture.kind == com.simpleblood.BloodKind.POWDER) {
                powderAt(hit.point(), Math.max(1, pixels / 3));
                return;
            }
            pour(hit.point(), Math.max(1, pixels / 3));
        }
    };

    private void capturedPiece(net.minecraft.core.particles.ParticleOptions type, Vec3 at, Vec3 v, int rgb, boolean exact) {
        SimpleBloodConfig.KindSettings k = cfg().kinds;
        float life = k.pieceLifetimeMultiplier();
        Vec3 sent = exact ? v : jitter(v);
        int own = colour.getAsInt() & 0xFFFFFF;
        boolean player = figure != ZOMBIE;
        if (type == com.simpleblood.BloodParticles.ARMOR_SPARK) {
            Piece p = piece(Piece.SPARK, at, v);
            p.life = 5 + rng.nextInt(6); p.size = 0.05f * (0.8f + rng.nextFloat() * 0.4f); p.gravity = 0.035f; p.drag = 0.9f;
        } else if (type == com.simpleblood.BloodParticles.GIBLET) {
            int blood = colour.getAsInt() & 0xFFFFFF;
            Piece p = piece(Piece.GIBLET, at, v);
            p.shape = rng.nextInt(4); p.restFrame = rng.nextInt(4); p.life = 90 + rng.nextInt(30);
            p.size = 0.085f * (1f + rng.nextFloat() * 0.3f); p.gravity = 0.03f; p.drag = 0.98f;
            p.colour = com.simpleblood.GibletColours.forBlood(blood); p.blood = blood;
        } else if (type == com.simpleblood.BloodParticles.METAL_FLAKE) {
            Piece p = piece(Piece.FLAKE, at, sent);
            p.shape = rng.nextInt(3); p.restFrame = rng.nextBoolean() ? 0 : 5; p.life = Math.round((70 + rng.nextInt(30)) * life);
            p.size = 0.06f; p.gravity = 0.04f; p.glints = k.metalGlint;
            p.colour = com.simpleblood.Capture.kind == null ? rgb : player ? own : 0xA8A8AC;
        } else if (type == com.simpleblood.BloodParticles.BONE_CHIP) {
            Piece p = piece(Piece.CHIP, at, sent);
            p.shape = rng.nextInt(4); p.restFrame = rng.nextInt(4); p.life = Math.round((60 + rng.nextInt(20)) * life);
            p.size = 0.06f; p.gravity = 0.04f; p.colour = player ? own : 0xE6DFC8;
        } else if (type == com.simpleblood.BloodParticles.BONE_DUST) {
            boolean powder = com.simpleblood.Capture.kind == com.simpleblood.BloodKind.POWDER;
            Piece p = piece(Piece.DUST, at, sent.scale(0.3));
            p.life = Math.round((25 + rng.nextInt(15)) * life); p.size = 0.16f; p.gravity = -0.001f; p.drag = 0.9f;
            p.collides = false; p.colour = player ? own : powder ? 0xF4F7FA : 0xE6DFC8;
            p.alphaScale = powder ? 1f : k.boneDustOpacity / 100f;
        } else if (type == com.simpleblood.BloodParticles.WOOD_SPLINTER) {
            Piece p = piece(Piece.SPLINTER, at, sent);
            p.shape = rng.nextInt(4); p.restFrame = rng.nextInt(4); p.life = Math.round((60 + rng.nextInt(20)) * life);
            p.size = 0.06f; p.gravity = 0.04f; p.colour = player ? own : 0x7A5A3E;
        } else if (type == com.simpleblood.BloodParticles.EMBER) {
            Piece p = piece(Piece.EMBER, at, sent.add(0, 0.05, 0));
            p.restFrame = rng.nextInt(4); p.life = Math.round((40 + rng.nextInt(25)) * life);
            p.size = 0.045f; p.gravity = 0.022f; p.drag = 0.96f; p.colour = 0xFFA62B;
        } else if (type == com.simpleblood.BloodParticles.SPIRIT_SPARK) {
            Piece p = piece(Piece.SPIRIT, at, sent.scale(0.5));
            p.life = Math.round((16 + rng.nextInt(10)) * life); p.size = 0.07f; p.gravity = -0.002f; p.drag = 0.9f;
            p.collides = false; p.colour = 0xB8F2FF;
        } else if (type == com.simpleblood.BloodParticles.WIND_PUFF) {
            Piece p = piece(Piece.WIND, at, sent.scale(0.6));
            p.life = Math.round((20 + rng.nextInt(10)) * life); p.size = 0.26f; p.gravity = 0f; p.drag = 0.92f;
            p.collides = false; p.colour = 0xD6E6FF; p.alphaScale = k.windOpacity / 100f;
        } else if (type == net.minecraft.core.particles.ParticleTypes.SMOKE) {
            Piece p = piece(Piece.DUST, at, new Vec3(0, 0.03, 0));
            p.life = Math.round(40 * life); p.size = 0.14f; p.gravity = -0.002f; p.drag = 0.95f;
            p.collides = false; p.colour = 0x4A4A4A; p.alphaScale = 0.6f;
        }
    }

    private Vec3 jitter(Vec3 v) {
        double jx = v.x + (rng.nextDouble() * 2 - 1) * 0.4, jy = v.y + (rng.nextDouble() * 2 - 1) * 0.4, jz = v.z + (rng.nextDouble() * 2 - 1) * 0.4;
        double mag = (rng.nextDouble() + rng.nextDouble() + 1) * 0.15;
        double len = Math.sqrt(jx * jx + jy * jy + jz * jz);
        if (len < 1e-6) len = 1;
        return new Vec3(jx / len * mag * 0.4, jy / len * mag * 0.4 + 0.1, jz / len * mag * 0.4);
    }

    private com.simpleblood.BloodKind realKind;

    private static net.minecraft.world.entity.LivingEntity attacker;

    private boolean realDeath() {
        net.minecraft.world.entity.LivingEntity a = actor();
        if (a == null) return false;
        Vec3 w = wound();
        if (a instanceof com.simpleblood.BloodEntityAccess acc) acc.simpleblood$setWound(w);
        com.simpleblood.HitContext ctx = com.simpleblood.HitContext.replay(a, 20f, camera.scale(-1).normalize(), com.simpleblood.HitContext.Weapon.BLADE, false, 1f, w, false);
        com.simpleblood.Tally.count("preview.real");
        runReal(() -> com.simpleblood.ClientBloodParticleSpawner.spawnBloodOnDeath(
                (net.minecraft.client.multiplayer.ClientLevel) a.level(), a, ctx));
        return true;
    }

    private boolean realDrip(SimpleBloodConfig c) {
        net.minecraft.world.entity.LivingEntity a = actor();
        if (a == null) return false;
        a.setHealth(Math.max(1f, a.getMaxHealth() * c.lowHealthThreshold() * 0.5f));
        Vec3 w = wound();
        if (a instanceof com.simpleblood.BloodEntityAccess acc) acc.simpleblood$setWound(w);
        runReal(() -> com.simpleblood.ClientBloodParticleSpawner.spawnBloodForLowHealth(
                (net.minecraft.client.multiplayer.ClientLevel) a.level(), a, c));
        a.setHealth(a.getMaxHealth());
        return true;
    }

    private boolean realArmorHit(boolean onArmor) {
        net.minecraft.world.entity.LivingEntity a = actor();
        if (a == null) return false;
        net.minecraft.client.multiplayer.ClientLevel level = (net.minecraft.client.multiplayer.ClientLevel) a.level();
        try {
            if (attacker == null || attacker.level() != level) {
                //? if <=1.21.1 {
                /*attacker = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
                *///?} elif <26.2 {
                attacker = net.minecraft.world.entity.EntityType.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
                //?} else {
                /*attacker = (net.minecraft.world.entity.LivingEntity) net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
                *///?}
                if (attacker == null) return false;
                attacker.setId(STAND_IN_ID - 1);
                attacker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
            }
        } catch (Throwable t) {
            return false;
        }
        a.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
        a.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
        Vec3 w = wound();
        if (a instanceof com.simpleblood.BloodEntityAccess acc) acc.simpleblood$setWound(w);
        attacker.setPos(a.getX() + camera.x * 2, a.getY(), a.getZ() + camera.z * 2);
        com.simpleblood.HitContext ctx = com.simpleblood.HitContext.replay(a, DAMAGE, camera.scale(-1).normalize(), com.simpleblood.HitContext.Weapon.BLADE, false, 1f, w, false);
        net.minecraft.world.damagesource.DamageSource source = level.damageSources().mobAttack(attacker);
        float[] share = {1f};
        com.simpleblood.Tally.count("preview.real");
        runReal(() -> share[0] = com.simpleblood.ArmorSparks.hit(level, a, ctx, source));
        runReal(() -> realBurst = new com.simpleblood.ClientBloodBurstTask(level, a, ctx).scaled(share[0]));
        burstTicksLeft = 0;
        burstTicks = 20;
        a.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
        a.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.item.ItemStack.EMPTY);
        return true;
    }

    private boolean realPieces(com.simpleblood.BloodKind which) {
        if (actor() == null) return false;
        realKind = which;
        return realHit(com.simpleblood.HitContext.Weapon.BLADE);
    }

    private void burstTick(SimpleBloodConfig c) {
        throwing = true;
        try {
            burstSpawns(c);
        } finally {
            throwing = false;
        }
    }

    private void burstSpawns(SimpleBloodConfig c) {
        burstTicksLeft--;
        float intensity = c.burstIntensityMultiplier() * burstShare;
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

    private void sweep(SimpleBloodConfig c) {
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

    private void dripTick(SimpleBloodConfig c) {
        if (realDrip(c)) return;
        if (shownKind() == com.simpleblood.BloodKind.POWDER) return;
        int chance = Math.max(1, (int) (6 / c.dripFrequencyMultiplier()));
        if (rng.nextInt(chance) != 0) return;
        int count = (int) (2 * c.dripIntensityMultiplier());
        Vec3 w = wound();
        for (int i = 0; i < count; i++) {
            spawn(w.x + (rng.nextDouble() - 0.5) * 0.16, w.y + (rng.nextDouble() - 0.5) * 0.2, w.z + (rng.nextDouble() - 0.5) * 0.16,
                    (rng.nextDouble() - 0.5) * 0.1, -1.5 - rng.nextDouble() * 0.5, (rng.nextDouble() - 0.5) * 0.1, 0);
        }
    }

    private static final int OTHER_BLOOD = 0x2FB8C4;

    private void mixPuddles() {
        Block floor = null;
        for (Block b : blocks) if (b.pos().equals(BlockPos.ZERO)) floor = b;
        if (floor == null) return;
        int own = colour.getAsInt() & 0xFFFFFF;
        for (int layer = 0; layer < 3; layer++) {
            stampAt(floor, Direction.UP, new Vec3(0.34, 1, 0.5), own, 34, null);
            stampAt(floor, Direction.UP, new Vec3(0.66, 1, 0.5), OTHER_BLOOD, 34, null);
        }
    }

    private void mixDrop(boolean own) {
        double x = 0.5 + (rng.nextDouble() - 0.5) * 0.12, z = 0.5 + (rng.nextDouble() - 0.5) * 0.25;
        Drop d = spawn(x, 1.45, z, 0, -0.1, 0, 0);
        d.vx = 0; d.vz = 0; d.vy = -0.2;
        if (!own) d.colour = OTHER_BLOOD;
    }

    private void rainDrop(SimpleBloodConfig c) {
        boolean onStep = rng.nextInt(3) != 0;
        double x = 0.5 + (onStep ? 0 : (rng.nextDouble() - 0.5) * 1.6) + (rng.nextDouble() - 0.5) * 0.5;
        double z = (onStep ? -0.5 : 0.7) + (rng.nextDouble() - 0.5) * 0.5;
        Drop d = spawn(x, 3.6, z, 0, -0.1, 0, rng.nextInt(4) == 0 ? 1 : 0);
        d.vx = 0; d.vz = 0; d.vy = -0.15;
    }

    private boolean throwing;

    private static void thrown(Drop d) {
        float speed = com.simpleblood.particle.BloodParticle.throwSpeedNow(false);
        float up = com.simpleblood.particle.BloodParticle.throwUpNow(false);
        d.vx = com.simpleblood.particle.BloodParticle.thrown(d.vx, false, speed, up);
        d.vy = com.simpleblood.particle.BloodParticle.thrown(d.vy, true, speed, up);
        d.vz = com.simpleblood.particle.BloodParticle.thrown(d.vz, false, speed, up);
    }

    private Drop spawn(double x, double y, double z, double vx, double vy, double vz, int kind) {
        SimpleBloodConfig c = cfg();
        Drop d = new Drop();
        d.x = x; d.y = y; d.z = z;
        if (kind < 2) {
            double jx = vx + (rng.nextDouble() * 2 - 1) * 0.4, jy = vy + (rng.nextDouble() * 2 - 1) * 0.4, jz = vz + (rng.nextDouble() * 2 - 1) * 0.4;
            double mag = (rng.nextDouble() + rng.nextDouble() + 1) * 0.15;
            double len = Math.sqrt(jx * jx + jy * jy + jz * jz);
            if (len < 1e-6) len = 1;
            vx = jx / len * mag * 0.4; vy = jy / len * mag * 0.4 + 0.1; vz = jz / len * mag * 0.4;
        }
        if (this.kind == Kind.WATER) { vx *= 0.3; vy *= 0.3; vz *= 0.3; }
        d.vx = vx; d.vy = vy; d.vz = vz;
        if (kind <= 2 && throwing) thrown(d);
        d.life = (int) ((40 + rng.nextInt(20) - 10) * c.particleLifetimeMultiplier());
        d.size = (0.08f + rng.nextFloat() * 0.08f) * c.particleSizeMultiplier();
        if (kind == 2) d.size = (0.34f + rng.nextFloat() * 0.16f) * c.chunkyStreakScale();
        if (kind == 3) { d.size = 0.02f; d.life = 200; }
        if (kind <= 1) d.size *= 0.5f;
        if (kind == 4) { d.size = 0.25f * 0.85f; d.life = 80; }
        d.tone = 0.9f + rng.nextFloat() * 0.2f;
        d.kind = kind;
        d.colour = this.kind == Kind.GLOW ? 0x35D6C8 : colour.getAsInt() & 0xFFFFFF;
        com.simpleblood.BloodKind bleeds = com.simpleblood.Capture.kind != null ? com.simpleblood.Capture.kind : shownKind();
        if (bleeds == com.simpleblood.BloodKind.POWDER) {
            d.size *= 0.6f;
            d.powder = true;
        }
        (movingDrops ? born : drops).add(d);
        return d;
    }

    private void moveDrops(SimpleBloodConfig c) {
        movingDrops = true;
        try {
            moveEachDrop(c);
        } finally {
            movingDrops = false;
            drops.addAll(born);
            born.clear();
        }
    }

    private void moveEachDrop(SimpleBloodConfig c) {
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

    private void spawnFog(Drop d, SimpleBloodConfig c) {
        Fog f = new Fog();
        f.x = d.x; f.y = d.y; f.z = d.z;
        f.vx = d.vx * 0.05 + (rng.nextDouble() - 0.5) * 0.02; f.vy = d.vy * 0.05 - 0.008; f.vz = d.vz * 0.05 + (rng.nextDouble() - 0.5) * 0.02;
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
            f.vx = f.vx * 0.97 + (rng.nextDouble() - 0.5) * 0.008 + (rng.nextDouble() - 0.5) * 0.002;
            f.vz = f.vz * 0.97 + (rng.nextDouble() - 0.5) * 0.008 + (rng.nextDouble() - 0.5) * 0.002;
            f.vy = f.vy * 0.99 - 0.0005;
            f.x += f.vx; f.y += f.vy; f.z += f.vz;
            f.vx *= 0.98; f.vy *= 0.98; f.vz *= 0.98;
            f.age += 2;
            if (f.y > waterTop() - 0.1) f.y = waterTop() - 0.1;
            if (f.y < 1.15) { f.y = 1.15; f.vy = 0; }
            if (f.age >= f.life) it.remove();
        }
    }

    private CanvasTile tile(Block b, Direction face, boolean create) {
        CanvasTile.Key key = new CanvasTile.Key(b.pos().asLong(), face, 0);
        CanvasTile t = tiles.get(key);
        if (t == null && create) {
            SimpleBloodConfig.SurfaceSettings s = cfg().surfaces;
            t = new CanvasTile(key, b.pos(), face, b.state(), new AABB(0, 0, 0, 1, 1, 1), s.resolution == 16 ? 16 : 8, now);
            if ("block".equals(s.highlightMode)) t.setBaseTones(BlockToneSampler.sample(t, rng));
            tiles.put(key, t);
        }
        return t;
    }

    private int lifetime() {
        if (kind == Kind.DRYING) return timeLapse(cfg().surfaces.lifetimeSeconds);
        return Math.max(60, cfg().surfaces.lifetimeSeconds * 20 / LIFETIME_DIVISOR);
    }

    private boolean pattern() { return !"block".equals(cfg().surfaces.highlightMode); }

    private void land(Block b, Direction face, Vec3 point, Drop d, Vec3 travel, SimpleBloodConfig c) {
        if (!c.surfaces.enabled) return;
        if (!c.surfaces.wallsAndCeilings && face != Direction.UP) return;
        if (blockMap.containsKey(b.pos().relative(face))) return;
        int pixels;
        if (d.powder) {
            float areaScale = c.surfaces.resolution >= 16 ? 3.0f : 1.0f;
            int n = Math.max(1, Math.round((1 + rng.nextInt(2)) * c.surfaceAmount() * areaScale));
            CanvasTile tile = tile(b, face, true);
            tile.stampPowder(clamp(tile.pixelX(point), tile.pw), clamp(tile.pixelY(point), tile.ph), n, d.colour, now, lifetime(), rng, new ArrayList<>());
            return;
        }
        if (d.kind == 3 || d.single) {
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

    private boolean startRun(CanvasTile side, int column, int colour, CanvasTile source, int sx, int sy) {
        RunSim.Run r = runSim.inColumn(side, column);
        if (r != null) {
            if (r.source != null) return false;
            r.feedFrom(source, sx, sy);
            r.remaining = Math.min(r.remaining + 4, side.ph);
            return true;
        }
        if (runs.size() > 40) return false;
        int budget = runSim.pour(source, sx, sy);
        if (budget == 0) return false;
        runSim.add(new RunSim.Run(side, column, 0, budget, 1, colour, source, sx, sy));
        side.paintThin(column, 0, colour, now, lifetime(), rng, pattern(), 1);
        return true;
    }

    private void advanceRuns(SimpleBloodConfig c) {
        Iterator<RunSim.Run> it = runs.iterator();
        while (it.hasNext()) {
            RunSim.Run r = it.next();
            if (!runSim.due(r)) continue;
            runSim.schedule(r);
            if (r.remaining-- <= 0) { it.remove(); continue; }
            runSim.drink(r, r.row);
            r.row++;
            if (r.row < r.tile.ph) {
                r.tile.paintThin(r.column, r.row, r.colour, now, lifetime(), rng, pattern(), RunSim.thin(r.row, r.tile.res));
            } else {
                Vec3 p = r.tile.pixelCenter(r.column, r.tile.ph - 1);
                BlockPos below = r.tile.pos.below().relative(r.tile.face);
                Block floor = blockMap.get(below);
                if (floor != null && !blockMap.containsKey(below.above())) {
                    Vec3 at = new Vec3(p.x + r.tile.face.getStepX() * 0.03, below.getY() + 1, p.z + r.tile.face.getStepZ() * 0.03);
                    stampAt(floor, Direction.UP, at, r.colour, Math.max(1, r.volume), new Vec3(0, -1, 0));
                } else {
                    Drop d = spawn(p.x + r.tile.face.getStepX() * 0.03, p.y - 0.05, p.z + r.tile.face.getStepZ() * 0.03, 0, -0.05, 0, 3);
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

    private final RunSim<RunSim.Run> runSim = new RunSim<>(runs, flow, tiles::containsValue, rng, () -> now);

    private void relax(SimpleBloodConfig c) {
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

    private void upload(SimpleBloodConfig c) {
        if (tiles.isEmpty()) return;
        BloodCanvasAtlas atlas = BloodCanvasAtlas.get();
        float opacity = c.surfaces.opacity / 100f;
        for (CanvasTile t : tiles.values()) {
            if (t.hasRipples()) t.dirty = true;
            if (t.dirty || t.slot < 0) t.refresh(atlas, now, opacity, flow);
        }
    }

    public void drawTiles(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, int light, float shade) {
        for (CanvasTile t : tiles.values()) {
            if (t.slot < 0 || t.isEmpty()) continue;
            int grey = Math.round(255 * shade * (t.custom ? 0.9f : SurfaceRenderer.faceShade(t.face)));
            SurfaceRenderer.drawTile(t, pose, vc, cam, Vec3.ZERO, light, 0.004, grey);
        }
    }
}
