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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;

//? if <=1.21.1 {
/*public class PuffParticle extends net.minecraft.client.particle.TextureSheetParticle {
*///?} else {
public class PuffParticle extends SingleQuadParticle {
//?}

    private final SpriteSet sprites;
    private final boolean wind;
    private final float startAlpha;
    private boolean counted;

    protected PuffParticle(ClientLevel world, double x, double y, double z,
                           double velX, double velY, double velZ,
                           SpriteSet sprites, TextureAtlasSprite sprite, boolean wind,
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
        this.wind = wind;

        if (BloodParticle.ballisticSpawn()) {
            this.xd = velX * 0.35;
            this.yd = velY * 0.35;
            this.zd = velZ * 0.35;
        } else {
            this.xd *= wind ? 0.6 : 0.45;
            this.yd = this.yd * 0.3 + (wind ? 0.03 : 0.012);
            this.zd *= wind ? 0.6 : 0.45;
        }

        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        this.lifetime = Math.max(8, (int) ((wind ? 12 + random.nextInt(6) : 14 + random.nextInt(8))
                * cfg.particleLifetimeMultiplier()));
        this.quadSize = (wind ? 0.24f : 0.30f) * (1f + random.nextFloat() * 0.2f) * size;
        this.setSize(0.1f, 0.1f);

        float shade = 0.94f + random.nextFloat() * 0.1f;
        this.setColor(Math.min(1f, red * shade), Math.min(1f, green * shade), Math.min(1f, blue * shade));
        float thickness = (wind ? cfg.kinds.windOpacity : cfg.kinds.boneDustOpacity) / 100.0f;
        this.startAlpha = Math.min(1f, (wind ? 0.75f : 0.6f) * thickness);
        this.alpha = startAlpha;
        this.gravity = 0f;
        this.hasPhysics = true;
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
            tickPuff();
        } catch (Throwable t) {
            Guard.fail(Guard.Part.PARTICLES, wind ? "a gust of wind" : "a puff of bone dust", t);
            this.remove();
        }
    }

    private void tickPuff() {
        this.xo = x;
        this.yo = y;
        this.zo = z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        if (level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER)) {
            this.remove();
            return;
        }
        float gravity = SimpleBloodClient.getConfig().particleGravityMultiplier();
        yd += (wind ? 0.0015f : -0.0016f) * gravity;
        this.move(xd, yd, zd);
        float drag = wind ? 0.9f : 0.86f;
        xd *= drag;
        yd *= drag;
        zd *= drag;

        this.setSprite(sprites.get(age, lifetime));
        float lifeFraction = 1.0f - (float) age / lifetime;
        if (lifeFraction < 0.3f) {
            this.alpha = startAlpha * Math.max(0f, lifeFraction / 0.3f);
        }
    }

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
        private final boolean wind;

        public Factory(SpriteSet sprites, boolean wind) {
            this.sprites = sprites;
            this.wind = wind;
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
                return new PuffParticle(world, x, y, z, velX, velY, velZ, sprites, sprites.get(0, 1), wind,
                        size, color.red, color.green, color.blue);
            } catch (Throwable t) {
                Guard.fail(Guard.Part.PARTICLES, wind ? "creating a gust of wind" : "creating bone dust", t);
                return null;
            }
        }
    }
}
