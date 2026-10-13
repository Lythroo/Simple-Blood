package com.simpleblood.particle;

import com.simpleblood.BloodColor;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/**
 * A long drop of blood thrown by a sweeping blow. Eight pixel-art teardrop sprites (one per
 * 45 degrees, head first) stand in for rotation: each frame the one closest to the particle's
 * on-screen direction of travel is used, so it always looks like crisp pixel art and never
 * a smoothly rotated texture. Lands like a splash.
 */
public class BloodStreakParticle extends BloodParticle {

    private final SpriteSet sprites;

    protected BloodStreakParticle(ClientLevel world, double x, double y, double z,
                                  double velX, double velY, double velZ,
                                  SpriteSet sprites, TextureAtlasSprite sprite, float sizeMultiplier,
                                  float red, float green, float blue) {
        super(world, x, y, z, velX, velY, velZ, sprite, sizeMultiplier, red, green, blue);
        this.sprites = sprites;
        // The vanilla constructor jitters the velocity; a streak must fly exactly where aimed.
        this.xd = velX;
        this.yd = velY;
        this.zd = velZ;
        this.quadSize = (0.34f + this.random.nextFloat() * 0.16f) * sizeMultiplier
                * com.simpleblood.SimpleBloodClient.getConfig().chunkyStreakScale();
        this.lifetime = 36 + this.random.nextInt(12);
        this.splash = true;
        this.landingScale = 2.4f; // a long drop is a lot of blood: it lands as a proper puddle
        this.setSize(0.05f, 0.05f);
    }

    /** Picks the sprite whose orientation is closest to the velocity as seen from the camera. */
    private void alignToVelocity(Camera camera) {
        double vx = x - xo, vy = y - yo, vz = z - zo;
        if (vx * vx + vy * vy + vz * vz < 1.0e-6) return; // resting: keep the last sprite
        //? if <=1.21.1 {
        /*org.joml.Vector3f up = camera.getUpVector();
        org.joml.Vector3f left = camera.getLeftVector();
        *///?} else {
        org.joml.Vector3fc up = camera.upVector();
        org.joml.Vector3fc left = camera.leftVector();
        //?}
        float sy = (float) (vx * up.x() + vy * up.y() + vz * up.z());
        float sx = -(float) (vx * left.x() + vy * left.y() + vz * left.z());
        double angle = Math.atan2(sy, sx);
        if (angle < 0) angle += Math.PI * 2;
        int orientation = (int) Math.round(angle / (Math.PI / 4)) & 7; // 8 headings, head first
        this.setSprite(sprites.get(orientation, 7));         // 8 frames: index * 7 / 7
    }

    //? if <=1.21.1 {
    /*@Override
    public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer, Camera camera, float partialTick) {
        alignToVelocity(camera);
        super.render(buffer, camera, partialTick);
    }
    *///?} elif <26.1 {
    /*@Override
    public void extract(net.minecraft.client.renderer.state.QuadParticleRenderState state, Camera camera, float partialTick) {
        alignToVelocity(camera);
        super.extract(state, camera, partialTick);
    }
    *///?} else {
    @Override
    public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState state, Camera camera, float partialTick) {
        alignToVelocity(camera);
        super.extract(state, camera, partialTick);
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
                return new BloodStreakParticle(world, x, y, z, velX, velY, velZ, this.spriteProvider, sprite,
                        currentSizeMultiplier(), color.red, color.green, color.blue);
            } catch (Throwable t) {
                com.simpleblood.Guard.fail(com.simpleblood.Guard.Part.PARTICLES, "creating a blood streak", t);
                return null;
            }
        }
    }
}
