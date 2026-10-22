package com.simpleblood.particle;

import com.simpleblood.Guard;
import com.simpleblood.SimpleBloodClient;
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

//? if <=1.21.1 {
/*public class ArmorSparkParticle extends net.minecraft.client.particle.TextureSheetParticle {
*///?} else {
public class ArmorSparkParticle extends SingleQuadParticle {
//?}

    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float GRAVITY = 0.035f;

    private final SpriteSet sprites;
    private boolean counted;

    protected ArmorSparkParticle(ClientLevel world, double x, double y, double z,
                                 double velX, double velY, double velZ,
                                 SpriteSet sprites, TextureAtlasSprite sprite) {
        //? if <=1.21.1 {
        /*super(world, x, y, z, velX, velY, velZ);
        this.setSprite(sprite);
        *///?} else {
        super(world, x, y, z, velX, velY, velZ, sprite);
        //?}
        com.simpleblood.Tally.count("particle.spark");
        BloodParticle.countBirth();
        this.counted = true;
        this.sprites = sprites;
        this.xd = velX;
        this.yd = velY;
        this.zd = velZ;
        this.lifetime = 5 + random.nextInt(6);
        this.quadSize = 0.05f * (0.8f + random.nextFloat() * 0.4f);
        this.setSize(0.02f, 0.02f);
        this.alpha = 1.0f;
        this.gravity = 0f;
        this.hasPhysics = true;
        colour(0f);
        this.setSprite(sprites.get(0, lifetime));
    }

    private void colour(float t) {
        this.setColor(1.0f, 0.97f - 0.42f * t, 0.75f - 0.62f * t);
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
            if (this.age++ >= this.lifetime || this.onGround) {
                this.remove();
                return;
            }
            yd -= GRAVITY * SimpleBloodClient.getConfig().particleGravityMultiplier();
            this.move(xd, yd, zd);
            xd *= 0.9;
            yd *= 0.9;
            zd *= 0.9;
            float t = (float) age / lifetime;
            colour(t);
            this.alpha = t > 0.7f ? 1f - (t - 0.7f) / 0.3f : 1f;
            this.setSprite(sprites.get(age, lifetime));
        } catch (Throwable t) {
            Guard.fail(Guard.Part.PARTICLES, "an armor spark", t);
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
                return new ArmorSparkParticle(world, x, y, z, velX, velY, velZ, sprites, sprites.get(0, 1));
            } catch (Throwable t) {
                Guard.fail(Guard.Part.PARTICLES, "creating an armor spark", t);
                return null;
            }
        }
    }
}
