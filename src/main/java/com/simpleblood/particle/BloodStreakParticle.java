package com.simpleblood.particle;

import com.simpleblood.BloodColor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class BloodStreakParticle extends BloodParticle {

    static final FlightSprites FLIGHT = new FlightSprites(16,
            new double[]{0.3, 0.5, 0.7, 0.82}, new double[]{0.05, 0.12, 0.22, 0.34});

    protected BloodStreakParticle(ClientLevel world, double x, double y, double z,
                                  double velX, double velY, double velZ,
                                  SpriteSet sprites, TextureAtlasSprite sprite, float sizeMultiplier,
                                  float red, float green, float blue) {
        super(world, x, y, z, velX, velY, velZ, sprite, sizeMultiplier, red, green, blue);
        double[] carry = carriedMotion();
        this.xd = velX;
        this.yd = velY;
        this.zd = velZ;
        throwHarder();
        this.xd += carry[0];
        this.yd += carry[1];
        this.zd += carry[2];
        this.quadSize = (0.34f + this.random.nextFloat() * 0.16f) * sizeMultiplier
                * com.simpleblood.SimpleBloodClient.getConfig().chunkyStreakScale();
        this.lifetime = 36 + this.random.nextInt(12);
        this.splash = true;
        this.landingScale = 2.4f;
        this.setSize(0.05f, 0.05f);
        showFlight(sprites, FLIGHT);
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
                TextureAtlasSprite sprite = this.spriteProvider.get(0, FLIGHT.sprites() - 1);
                return new BloodStreakParticle(world, x, y, z, velX, velY, velZ, this.spriteProvider, sprite,
                        currentSizeMultiplier(), color.red, color.green, color.blue);
            } catch (Throwable t) {
                com.simpleblood.Guard.fail(com.simpleblood.Guard.Part.PARTICLES, "creating a blood streak", t);
                return null;
            }
        }
    }
}
