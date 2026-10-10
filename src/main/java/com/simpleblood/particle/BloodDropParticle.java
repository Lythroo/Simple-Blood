package com.simpleblood.particle;

import com.simpleblood.BloodColor;
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

    //? if 1.20.1 {
    /*@Override
    public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer, net.minecraft.client.Camera camera, float partialTick) {
        LegacyQuads.draw(buffer, camera, LegacyQuads.uprightTowards(camera), false,
                net.minecraft.util.Mth.lerp(partialTick, xo, x), net.minecraft.util.Mth.lerp(partialTick, yo, y),
                net.minecraft.util.Mth.lerp(partialTick, zo, z), getQuadSize(partialTick),
                getU0(), getU1(), getV0(), getV1(), rCol, gCol, bCol, alpha, getLightColor(partialTick));
    }
    *///?} else {
    @Override
    public SingleQuadParticle.FacingCameraMode getFacingCameraMode() {
        return SingleQuadParticle.FacingCameraMode.LOOKAT_Y;
    }
    //?}

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
                //? if <=1.21.1 {
                /*TextureAtlasSprite sprite = this.spriteProvider.get(world.getRandom());
                *///?} else {
                TextureAtlasSprite sprite = this.spriteProvider.get(random);
                //?}
                return new BloodDropParticle(world, x, y, z, velX, velY, velZ, sprite,
                        color.red, color.green, color.blue);
            } catch (Throwable t) {
                com.simpleblood.Guard.fail(com.simpleblood.Guard.Part.PARTICLES, "creating a falling drop", t);
                return null;
            }
        }
    }
}
