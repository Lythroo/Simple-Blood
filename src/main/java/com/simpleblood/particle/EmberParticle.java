package com.simpleblood.particle;

import com.simpleblood.BloodColor;
import com.simpleblood.BloodSounds;
import com.simpleblood.Guard;
import com.simpleblood.RenderThread;
import com.simpleblood.SimpleBloodClient;
import com.simpleblood.SimpleBloodConfig;
import net.minecraft.client.particle.SingleQuadParticle;
//? if >1.21.1 {
import net.minecraft.client.particle.SingleQuadParticle.Layer;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

//? if <=1.21.1 {
/*public class EmberParticle extends net.minecraft.client.particle.TextureSheetParticle {
*///?} else {
public class EmberParticle extends SingleQuadParticle {
//?}

    private static final int FRAMES = 4;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float GRAVITY = 0.022f;

    private static final float HOT_R = 1.0f, HOT_G = 0.95f, HOT_B = 0.7f;
    private static final float GLOW_R = 0.55f, GLOW_G = 0.12f, GLOW_B = 0.05f;
    private static final float ASH_R = 0.27f, ASH_G = 0.23f, ASH_B = 0.2f;

    private final SpriteSet sprites;
    private final float red, green, blue;
    private int frame;
    private float flicker = 1f;
    private boolean resting;
    private boolean glowing = true;
    private boolean counted;

    protected EmberParticle(ClientLevel world, double x, double y, double z,
                            double velX, double velY, double velZ,
                            SpriteSet sprites, TextureAtlasSprite sprite,
                            float size, float red, float green, float blue) {
        //? if <=1.21.1 {
        /*super(world, x, y, z, velX, velY, velZ);
        this.setSprite(sprite);
        *///?} else {
        super(world, x, y, z, velX, velY, velZ, sprite);
        //?}
        BloodParticle.countBirth();
        this.counted = true;
        this.sprites = sprites;

        if (BloodParticle.ballisticSpawn()) {
            this.xd = velX;
            this.yd = velY;
            this.zd = velZ;
        } else {
            this.xd *= 1.2;
            this.zd *= 1.2;
            this.yd += 0.05 + random.nextFloat() * 0.06;
        }

        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        float lifeScale = cfg.particleLifetimeMultiplier() * cfg.kinds.pieceLifetimeMultiplier() * BloodParticle.nextLifeScale();
        this.lifetime = Math.max(10, (int) ((30 + random.nextInt(25)) * lifeScale));
        this.quadSize = 0.08f * (1f + random.nextFloat() * 0.35f) * size;
        this.setSize(0.05f, 0.05f);
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.frame = random.nextInt(FRAMES);
        this.alpha = 1.0f;
        this.gravity = 0f;
        this.hasPhysics = true;
        colourFor(0f);
        this.setSprite(sprites.get(frame, FRAMES - 1));
    }

    @Override
    public void remove() {
        if (counted) {
            counted = false;
            BloodParticle.countDeath();
        }
        super.remove();
    }

    @Override
    public void tick() {
        if (!Guard.ok(Guard.Part.PARTICLES)) {
            this.remove();
            return;
        }
        try {
            tickEmber();
        } catch (Throwable t) {
            Guard.fail(Guard.Part.PARTICLES, "an ember", t);
            this.remove();
        }
    }

    private void tickEmber() {
        this.xo = x;
        this.yo = y;
        this.zo = z;
        if (this.age++ >= this.lifetime) {
            if (glowing && random.nextInt(4) == 0) smoke();
            this.remove();
            return;
        }
        if (level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER)) {
            if (glowing) BloodSounds.fizzle(level, new Vec3(x, y, z));
            this.remove();
            return;
        }

        if (!resting) {
            yd -= GRAVITY * SimpleBloodClient.getConfig().particleGravityMultiplier();
            double wantY = yd;
            this.move(xd, yd, zd);
            xd *= 0.96;
            yd *= 0.96;
            zd *= 0.96;
            if (onGround) {
                if (wantY < -0.08) {
                    yd = -wantY * 0.25;
                    xd *= 0.5;
                    zd *= 0.5;
                } else {
                    resting = true;
                    xd = yd = zd = 0;
                }
            } else if (x == xo && y == yo && z == zo) {
                resting = true;
            }
        }

        float t = (float) age / lifetime;
        glowing = t < 0.82f;
        if (glowing && age % 2 == 0) {
            frame = (frame + 1 + random.nextInt(FRAMES - 1)) % FRAMES;
            flicker = 0.8f + random.nextFloat() * 0.2f;
        } else if (!glowing) {
            flicker = 1f;
        }
        this.setSprite(sprites.get(frame, FRAMES - 1));
        colourFor(t);

        if (t > 0.9f) this.alpha = Math.max(0f, (1f - t) / 0.1f);
    }

    private void colourFor(float t) {
        float r, g, b;
        if (t < 0.15f) {
            float k = 1f - t / 0.15f;
            r = lerp(red, HOT_R, 0.65f * k);
            g = lerp(green, HOT_G, 0.65f * k);
            b = lerp(blue, HOT_B, 0.65f * k);
        } else if (t < 0.55f) {
            r = red;
            g = green;
            b = blue;
        } else if (t < 0.8f) {
            float k = (t - 0.55f) / 0.25f;
            r = lerp(red, GLOW_R, k);
            g = lerp(green, GLOW_G, k);
            b = lerp(blue, GLOW_B, k);
        } else {
            float k = Math.min(1f, (t - 0.8f) / 0.1f);
            r = lerp(GLOW_R, ASH_R, k);
            g = lerp(GLOW_G, ASH_G, k);
            b = lerp(GLOW_B, ASH_B, k);
        }
        this.setColor(r * flicker, g * flicker, b * flicker);
    }

    private static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }

    private void smoke() {
        double px = x, py = y, pz = z;
        RenderThread.run(() -> Minecraft.getInstance().particleEngine.createParticle(
                ParticleTypes.SMOKE, px, py + 0.05, pz, 0.0, 0.02, 0.0));
    }

    //? if <26.1 {
    /*@Override
    protected int getLightColor(float partialTick) {
        return glowing ? FULL_BRIGHT : super.getLightColor(partialTick);
    }
    *///?} else {
    @Override
    protected int getLightCoords(float partialTick) {
        return glowing ? FULL_BRIGHT : super.getLightCoords(partialTick);
    }
    //?}

    //? if <=1.21.1 {
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

        public Factory(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        //? if <=1.21.1 {
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
                return new EmberParticle(world, x, y, z, velX, velY, velZ, sprites, sprites.get(0, 1),
                        size, color.red, color.green, color.blue);
            } catch (Throwable t) {
                Guard.fail(Guard.Part.PARTICLES, "creating an ember", t);
                return null;
            }
        }
    }
}
