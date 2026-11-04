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

public class BloodSprayParticle extends BloodParticle {

    private static final float QUAD = 0.25f;
    private static final double HEADING_MARGIN = Math.toRadians(8);
    private static final double LENGTH_MARGIN = 0.07;

    private final SpriteSet sprites;
    private final int[] shown = {-1, -1};

    protected BloodSprayParticle(ClientLevel world, double x, double y, double z,
                                 double velX, double velY, double velZ,
                                 SpriteSet sprites, TextureAtlasSprite sprite, float sizeMultiplier,
                                 float red, float green, float blue) {
        super(world, x, y, z, velX, velY, velZ, sprite, sizeMultiplier, red, green, blue);
        this.sprites = sprites;
        this.xd = velX;
        this.yd = velY;
        this.zd = velZ;
        this.quadSize = QUAD * Math.max(0.6f, Math.min(1.3f, sizeMultiplier));
        this.alpha = 1.0f;
        this.lifetime = 70 + this.random.nextInt(20);
        this.setSize(0.02f, 0.02f);
        this.setSprite(sprites.get(0, 16));
        com.simpleblood.Tally.count("particle.spray");
    }

    private void alignToFlight(Camera camera) {
        double vx = xd, vy = yd, vz = zd;
        double speed2 = vx * vx + vy * vy + vz * vz;
        if (speed2 < 1.0e-7) return;
        //? if <=1.21.1 {
        /*org.joml.Vector3f up = camera.getUpVector();
        org.joml.Vector3f left = camera.getLeftVector();
        *///?} else {
        org.joml.Vector3fc up = camera.upVector();
        org.joml.Vector3fc left = camera.leftVector();
        //?}
        double sy = vx * up.x() + vy * up.y() + vz * up.z();
        double sx = -(vx * left.x() + vy * left.y() + vz * left.z());
        this.setSprite(sprites.get(pick(sx, sy, Math.sqrt(speed2), shown), 16));
    }

    public static int pick(double sx, double sy, double speed, int[] shown) {
        if (speed < 3.0e-4) return index(shown);
        double screen = Math.sqrt(sx * sx + sy * sy);
        double sideOn = screen / speed;
        if (shown[1] < 0) {
            shown[1] = lengthFor(sideOn, screen, 0);
        } else {
            int longer = lengthFor(sideOn, screen, LENGTH_MARGIN), shorter = lengthFor(sideOn, screen, -LENGTH_MARGIN);
            if (longer > shown[1]) shown[1] = longer;
            else if (shorter < shown[1]) shown[1] = shorter;
        }
        if (screen > 1.0e-5) {
            double angle = Math.atan2(sy, sx);
            if (shown[0] < 0) {
                shown[0] = nearestHeading(angle);
            } else {
                double off = Math.abs(Math.IEEEremainder(angle - shown[0] * Math.PI / 4, Math.PI * 2));
                if (off > Math.PI / 8 + HEADING_MARGIN) shown[0] = nearestHeading(angle);
            }
        }
        return index(shown);
    }

    private static int index(int[] shown) {
        return shown[1] <= 0 || shown[0] < 0 ? 0 : (shown[1] == 1 ? 1 : 9) + shown[0];
    }

    private static int lengthFor(double sideOn, double screen, double bias) {
        if (sideOn > 0.75 + bias && screen > 0.08 * (1 + 2 * bias)) return 2;
        if (sideOn > 0.35 + bias && screen > 0.02 * (1 + 2 * bias)) return 1;
        return 0;
    }

    private static int nearestHeading(double angle) {
        return (int) Math.round(angle / (Math.PI / 4)) & 7;
    }

    //? if <=1.21.1 {
    /*@Override
    public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer, Camera camera, float partialTick) {
        alignToFlight(camera);
        super.render(buffer, camera, partialTick);
    }
    *///?} elif <26.1 {
    /*@Override
    public void extract(net.minecraft.client.renderer.state.QuadParticleRenderState state, Camera camera, float partialTick) {
        alignToFlight(camera);
        super.extract(state, camera, partialTick);
    }
    *///?} else {
    @Override
    public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState state, Camera camera, float partialTick) {
        alignToFlight(camera);
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
                return new BloodSprayParticle(world, x, y, z, velX, velY, velZ, this.spriteProvider,
                        this.spriteProvider.get(0, 16), currentSizeMultiplier(), color.red, color.green, color.blue);
            } catch (Throwable t) {
                com.simpleblood.Guard.fail(com.simpleblood.Guard.Part.PARTICLES, "creating a spray drop", t);
                return null;
            }
        }
    }
}
