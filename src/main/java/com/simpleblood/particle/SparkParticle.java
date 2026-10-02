package com.simpleblood.particle;

import com.simpleblood.BloodColor;
import com.simpleblood.SimpleBloodClient;
import com.simpleblood.SimpleBloodConfig;
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
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

//? if 1.21.1 {
/*public class SparkParticle extends net.minecraft.client.particle.TextureSheetParticle {
*///?} else {
public class SparkParticle extends SingleQuadParticle {
//?}

    private static final int FULL_BRIGHT = 0xF000F0;

    private final SpriteSet sprites;
    private boolean counted;

    protected SparkParticle(ClientLevel world, double x, double y, double z,
                            double velX, double velY, double velZ,
                            SpriteSet sprites, TextureAtlasSprite sprite,
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

        if (BloodParticle.ballisticSpawn()) {
            this.xd = velX * 0.4;
            this.yd = velY * 0.4;
            this.zd = velZ * 0.4;
        } else {
            this.xd *= 0.5;
            this.yd = this.yd * 0.3 + 0.03;
            this.zd *= 0.5;
        }

        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        this.lifetime = Math.max(6, (int) ((12 + random.nextInt(10)) * cfg.particleLifetimeMultiplier()));
        this.quadSize = 0.11f * (1f + random.nextFloat() * 0.4f) * size;
        this.setSize(0.05f, 0.05f);
        float pale = 0.3f;
        this.setColor(red + (1f - red) * pale, green + (1f - green) * pale, blue + (1f - blue) * pale);
        this.alpha = 1.0f;
        this.gravity = 0f;
        this.hasPhysics = false;
        this.setSprite(sprites.get(0, lifetime));
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
            this.xo = x;
            this.yo = y;
            this.zo = z;
            if (this.age++ >= this.lifetime) {
                this.remove();
                return;
            }
            yd += 0.002 * SimpleBloodClient.getConfig().particleGravityMultiplier();
            this.move(xd, yd, zd);
            xd *= 0.9;
            yd *= 0.9;
            zd *= 0.9;
            this.setSprite(sprites.get(age, lifetime));
        } catch (Throwable t) {
            Guard.fail(Guard.Part.PARTICLES, "a spirit spark", t);
            this.remove();
        }
    }

    //? if <26.1 {
    /*@Override
    protected int getLightColor(float partialTick) {
        return FULL_BRIGHT;
    }
    *///?} else {
    @Override
    protected int getLightCoords(float partialTick) {
        return FULL_BRIGHT;
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

        public Factory(SpriteSet sprites) {
            this.sprites = sprites;
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
                return new SparkParticle(world, x, y, z, velX, velY, velZ, sprites, sprites.get(0, 1),
                        size, color.red, color.green, color.blue);
            } catch (Throwable t) {
                Guard.fail(Guard.Part.PARTICLES, "creating a spirit spark", t);
                return null;
            }
        }
    }
}
