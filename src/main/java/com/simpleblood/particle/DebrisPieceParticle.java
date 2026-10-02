package com.simpleblood.particle;

import com.simpleblood.BloodColor;
import com.simpleblood.SimpleBloodClient;
import com.simpleblood.SimpleBloodConfig;
import com.simpleblood.BloodSounds;
import com.simpleblood.Guard;
import net.minecraft.client.particle.SingleQuadParticle;
//? if >1.21.1 {
import net.minecraft.client.particle.SingleQuadParticle.Layer;
//?}
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

//? if 1.21.1 {
/*public class DebrisPieceParticle extends net.minecraft.client.particle.TextureSheetParticle {
*///?} else {
public class DebrisPieceParticle extends SingleQuadParticle {
//?}

    private static final int CHIP_SHAPES = 4, CHIP_TURNS = 4;
    private static final int FLAKE_SHAPES = 3, FLAKE_FRAMES = 6;
    private static final int MAX_LAYERS = 4;

    private static final java.util.Set<DebrisPieceParticle> LYING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void forgetLying() {
        LYING.clear();
    }
    private static final int FACE = 0, GLINT = 1, BACK = 5;
    private static final int[] TUMBLE = {0, 2, 3, 4, 5, 4, 3, 2};

    private static final float GRAVITY = 0.045f;
    private static final float WATER_SINK = 0.007f;
    private static final float WATER_DRAG = 0.75f;

    private static final float BARK_R = 0x4A / 255f, BARK_G = 0x3E / 255f, BARK_B = 0x34 / 255f;

    private final SpriteSet sprites;
    private final com.simpleblood.BloodKind kind;
    private final boolean metal;
    private final boolean liesFlat;
    private final int shape;
    private final float red, green, blue;
    private final float bounce;
    private float phase;
    private float spinRate;
    private int lastFrame = -1;
    private int glintTicks;
    private boolean resting;
    private int restFrame;
    private float lift;
    private org.joml.Quaternionf flat;
    private int layer;
    private int bounces;
    private boolean landed;
    private boolean counted;

    protected DebrisPieceParticle(ClientLevel world, double x, double y, double z,
                                  double velX, double velY, double velZ,
                                  SpriteSet sprites, TextureAtlasSprite sprite, com.simpleblood.BloodKind kind,
                                  float size, float red, float green, float blue) {
        //? if 1.21.1 {
        /*super(world, x, y, z, velX, velY, velZ);
        this.setSprite(sprite);
        *///?} else {
        super(world, x, y, z, velX, velY, velZ, sprite);
        //?}
        BloodParticle.countBirth();
        this.counted = true;
        this.sprites = sprites;
        this.kind = kind;
        this.metal = kind == com.simpleblood.BloodKind.METAL;
        this.liesFlat = kind != com.simpleblood.BloodKind.BONE;
        boolean wood = kind == com.simpleblood.BloodKind.WOOD;

        if (BloodParticle.ballisticSpawn()) {
            this.xd = velX;
            this.yd = velY;
            this.zd = velZ;
        } else {
            this.yd += 0.04 + random.nextFloat() * 0.05;
        }

        this.shape = random.nextInt(metal ? FLAKE_SHAPES : CHIP_SHAPES);
        this.phase = random.nextInt(metal ? TUMBLE.length : CHIP_TURNS);
        this.spinRate = (metal ? 0.55f + random.nextFloat() * 0.6f
                : wood ? 0.4f + random.nextFloat() * 0.45f
                : 0.3f + random.nextFloat() * 0.4f)
                * (random.nextBoolean() ? 1 : -1);
        this.bounce = metal ? 0.3f : wood ? 0.36f : 0.42f;

        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        float lifeScale = cfg.particleLifetimeMultiplier() * BloodParticle.nextLifeScale();
        this.lifetime = Math.max(10, (int) (((metal ? 70 : 60) + random.nextInt(30)) * lifeScale));

        this.quadSize = (metal || wood ? 0.10f : 0.09f) * (1f + random.nextFloat() * 0.3f) * size;
        this.setSize(0.08f, 0.08f);
        if (wood) {
            red = red * 0.45f + BARK_R * 0.55f;
            green = green * 0.45f + BARK_G * 0.55f;
            blue = blue * 0.45f + BARK_B * 0.55f;
        }
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.setColor(red, green, blue);
        this.alpha = 1.0f;
        this.gravity = 0f;
        this.hasPhysics = true;
        pickSprite();
    }

    @Override
    public void remove() {
        if (counted) {
            counted = false;
            BloodParticle.countDeath();
        }
        LYING.remove(this);
        super.remove();
    }

    @Override
    public void tick() {
        if (!Guard.ok(Guard.Part.PARTICLES)) {
            this.remove();
            return;
        }
        try {
            tickPiece();
        } catch (Throwable t) {
            Guard.fail(Guard.Part.PARTICLES, "a bone chip, metal flake or splinter moving", t);
            this.remove();
        }
    }

    private void tickPiece() {
        this.xo = x;
        this.yo = y;
        this.zo = z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();

        if (resting) {
            if (age % 10 == 0) {
                BlockPos below = BlockPos.containing(x, y - lift - 0.05, z);
                if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                    resting = false;
                    flat = null;
                    layer = 0;
                    LYING.remove(this);
                    this.setPos(x, y - lift, z);
                    lift = 0;
                    yd = -0.05;
                }
            }
        } else {
            boolean water = level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER);
            yd -= (water ? WATER_SINK : GRAVITY) * cfg.particleGravityMultiplier();
            if (water && yd < -0.06) yd = -0.06;

            double wantX = xd, wantY = yd, wantZ = zd;
            this.move(xd, yd, zd);

            if (xd == 0 && wantX != 0) xd = -wantX * 0.3;
            if (zd == 0 && wantZ != 0) zd = -wantZ * 0.3;

            float drag = water ? WATER_DRAG : Math.min(1f, (metal ? 0.96f : 0.98f) * cfg.particleDragMultiplier());
            xd *= drag;
            yd *= drag;
            zd *= drag;

            if (onGround) {
                if (!landed) {
                    landed = true;
                    if (!water) BloodSounds.pieceLanded(level, new Vec3(x, y, z), kind);
                }
                if (!water && wantY < -0.07 && bounces < 2) {
                    yd = -wantY * bounce;
                    xd *= 0.6;
                    zd *= 0.6;
                    spinRate *= 0.6f;
                    bounces++;
                } else {
                    settle();
                }
            } else if (x == xo && y == yo && z == zo && (wantX != 0 || wantY != 0 || wantZ != 0)) {
                settle();
            }

            if (!resting) {
                double speed = Math.sqrt(xd * xd + yd * yd + zd * zd);
                phase += spinRate * (float) Math.min(1.0, speed * 6.0 + 0.2);
            }
        }

        pickSprite();

        float lifeFraction = 1.0f - (float) age / lifetime;
        if (lifeFraction < 0.25f) {
            this.alpha = Math.max(0f, lifeFraction / 0.25f);
        }
    }

    private void settle() {
        resting = true;
        xd = yd = zd = 0;
        if (metal) restFrame = random.nextInt(3) == 0 ? BACK : FACE;
        if (onGround && liesFlat) {
            org.joml.Quaternionf lie = new org.joml.Quaternionf()
                    .rotationY((float) (random.nextInt(4) * Math.PI / 2))
                    .rotateX((float) (-Math.PI / 2));
            float tiltLift = 0f;
            DebrisPieceParticle under = flakeUnder();
            if (under != null) {
                layer = Math.min(MAX_LAYERS, under.layer + 1);
                double dx = x - under.x, dz = z - under.z;
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len < 1.0e-4) {
                    double a = random.nextDouble() * Math.PI * 2;
                    dx = Math.cos(a);
                    dz = Math.sin(a);
                    len = 1;
                }
                dx /= len;
                dz /= len;
                float tilt = (float) Math.toRadians(10 + random.nextInt(9));
                lie = new org.joml.Quaternionf().rotationAxis(-tilt, (float) -dz, 0f, (float) dx).mul(lie);
                tiltLift = quadSize * 0.6f * (float) Math.sin(tilt);
            }
            flat = lie;
            lift = 0.008f + random.nextFloat() * 0.006f + layer * 0.012f + tiltLift;
            LYING.add(this);
            this.setPos(x, y + lift, z);
            this.xo = x;
            this.yo = y;
            this.zo = z;
        } else if (onGround) {
            lift = quadSize * 0.3f;
            this.setPos(x, y + lift, z);
            this.xo = x;
            this.yo = y;
            this.zo = z;
        }
    }

    private DebrisPieceParticle flakeUnder() {
        DebrisPieceParticle under = null;
        for (DebrisPieceParticle o : LYING) {
            if (o == this || o.level != level || o.removed || o.metal != metal) continue;
            double dx = x - o.x, dz = z - o.z;
            double reach = (quadSize + o.quadSize) * 0.6;
            if (dx * dx + dz * dz > reach * reach) continue;
            if (Math.abs(y - (o.y - o.lift)) > 0.1) continue;
            if (under == null || o.layer > under.layer) under = o;
        }
        return under;
    }

    private void pickSprite() {
        if (metal) {
            int frame = resting ? restFrame : TUMBLE[Math.floorMod((int) Math.floor(phase), TUMBLE.length)];
            if (!resting) {
                if (frame == FACE && lastFrame != FACE && random.nextFloat() < 0.4f) glintTicks = 2;
            } else if (frame == FACE && glintTicks == 0 && random.nextInt(90) == 0) {
                glintTicks = 3;
            }
            lastFrame = frame;
            boolean glint = frame == FACE && glintTicks > 0;
            if (glintTicks > 0) glintTicks--;
            float shine = glint ? 0.6f : frame == FACE ? 0.12f : 0f;
            this.setColor(red + (1f - red) * shine, green + (1f - green) * shine, blue + (1f - blue) * shine);
            int index = shape * FLAKE_FRAMES + (glint ? GLINT : frame);
            this.setSprite(sprites.get(index, FLAKE_SHAPES * FLAKE_FRAMES - 1));
        } else {
            int index = shape * CHIP_TURNS + Math.floorMod((int) Math.floor(phase), CHIP_TURNS);
            this.setSprite(sprites.get(index, CHIP_SHAPES * CHIP_TURNS - 1));
        }
    }

    //? if 1.21.1 {
    /*@Override
    public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer, net.minecraft.client.Camera camera, float partialTick) {
        if (flat != null) renderRotatedQuad(buffer, camera, flat, partialTick);
        else super.render(buffer, camera, partialTick);
    }
    *///?} elif <26.1 {
    /*@Override
    public void extract(net.minecraft.client.renderer.state.QuadParticleRenderState state, net.minecraft.client.Camera camera, float partialTick) {
        if (flat != null) extractRotatedQuad(state, camera, flat, partialTick);
        else super.extract(state, camera, partialTick);
    }
    *///?} else {
    @Override
    public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState state, net.minecraft.client.Camera camera, float partialTick) {
        if (flat != null) extractRotatedQuad(state, camera, flat, partialTick);
        else super.extract(state, camera, partialTick);
    }
    //?}

    //? if 1.21.1 {
    /*@Override
    public net.minecraft.client.particle.ParticleRenderType getRenderType() {
        return net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
    *///?} else {
    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
    //?}

    public static class Factory implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;
        private final com.simpleblood.BloodKind kind;

        public Factory(SpriteSet sprites, com.simpleblood.BloodKind kind) {
            this.sprites = sprites;
            this.kind = kind;
        }

        @Override
        //? if 1.21.1 {
        /*public Particle createParticle(SimpleParticleType type, ClientLevel world,
                                       double x, double y, double z,
                                       double velX, double velY, double velZ) {
        *///?} else {
        public Particle createParticle(SimpleParticleType type, ClientLevel world,
                                       double x, double y, double z,
                                       double velX, double velY, double velZ,
                                       RandomSource random) {
        //?}
            if (!Guard.ok(Guard.Part.PARTICLES)) return null;
            try {
                BloodColor.Color color = BloodParticle.currentBloodColor();
                float size = SimpleBloodClient.getConfig().pieceSizeMultiplier() * BloodParticle.currentSizeMultiplier();
                return new DebrisPieceParticle(world, x, y, z, velX, velY, velZ, sprites, sprites.get(0, 1), kind,
                        size, color.red, color.green, color.blue);
            } catch (Throwable t) {
                Guard.fail(Guard.Part.PARTICLES, "creating a " + kind.key() + " piece", t);
                return null;
            }
        }
    }
}
