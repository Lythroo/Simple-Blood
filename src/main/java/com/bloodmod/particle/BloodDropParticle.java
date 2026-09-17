package com.bloodmod.particle;

import com.bloodmod.BloodColor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class BloodDropParticle extends BloodParticle {

    private static final float DROP_QUAD_SIZE = 0.25f;

    protected BloodDropParticle(ClientLevel world, double x, double y, double z,
                                double velX, double velY, double velZ,
                                TextureAtlasSprite sprite,
                                float red, float green, float blue) {
        super(world, x, y, z, velX, velY, velZ, sprite, 1.0f, red, green, blue);
        this.xd = 0;
        this.yd = velY;
        this.zd = 0;
        this.quadSize = DROP_QUAD_SIZE;
        this.alpha = 1.0f;
        this.lifetime = 200;
        this.setSize(0.02f, 0.02f);
    }

    @Override
    public SingleQuadParticle.FacingCameraMode getFacingCameraMode() {
        return SingleQuadParticle.FacingCameraMode.LOOKAT_Y;
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet spriteProvider;

        public Factory(SpriteSet spriteProvider) {
            this.spriteProvider = spriteProvider;
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
            if (!com.bloodmod.Guard.ok(com.bloodmod.Guard.Part.PARTICLES)) return null;
            try {
                BloodColor.Color color = currentColor();
                //? if 1.21.1 {
                /*TextureAtlasSprite sprite = this.spriteProvider.get(world.getRandom());
                *///?} else {
                TextureAtlasSprite sprite = this.spriteProvider.get(random);
                //?}
                return new BloodDropParticle(world, x, y, z, velX, velY, velZ, sprite,
                        color.red, color.green, color.blue);
            } catch (Throwable t) {
                com.bloodmod.Guard.fail(com.bloodmod.Guard.Part.PARTICLES, "creating a falling drop", t);
                return null;
            }
        }
    }
}
