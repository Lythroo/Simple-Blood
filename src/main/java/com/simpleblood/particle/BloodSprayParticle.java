package com.simpleblood.particle;

import com.simpleblood.BloodColor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class BloodSprayParticle extends BloodParticle {

    private static final float QUAD = 0.25f;
    static final FlightSprites FLIGHT = new FlightSprites(8, new double[]{0.35, 0.75}, new double[]{0.06, 0.2});

    static final int SPRITES = FLIGHT.sprites();

    protected BloodSprayParticle(ClientLevel world, double x, double y, double z,
                                 double velX, double velY, double velZ,
                                 SpriteSet sprites, TextureAtlasSprite sprite, float sizeMultiplier,
                                 float red, float green, float blue) {
        super(world, x, y, z, velX, velY, velZ, sprite, sizeMultiplier, red, green, blue);
        this.xd = velX;
        this.yd = velY;
        this.zd = velZ;
        this.quadSize = QUAD * Math.max(0.6f, Math.min(1.3f, sizeMultiplier));
        this.alpha = 1.0f;
        this.lifetime = 70 + this.random.nextInt(20);
        this.setSize(0.02f, 0.02f);
        showFlight(sprites);
        com.simpleblood.Tally.count("particle.spray");
    }

    public static int pick(double sx, double sy, double speed, int[] shown) {
        return FLIGHT.pick(sx, sy, speed, shown);
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet spriteProvider;

        public Factory(SpriteSet spriteProvider) {
            this.spriteProvider = spriteProvider;
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
            if (!com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.PARTICLES)) return null;
            try {
                BloodColor.Color color = currentColor();
                return new BloodSprayParticle(world, x, y, z, velX, velY, velZ, this.spriteProvider,
                        this.spriteProvider.get(0, 16), currentSizeMultiplier(), color.red, color.green, color.blue);
            } catch (Throwable t) {
                com.simpleblood.Guard.fail(com.simpleblood.Guard.Part.PARTICLES, "creating a spray drop", t);
                return null;
            }
        }
    }
}
