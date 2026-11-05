package com.simpleblood.particle;

import com.simpleblood.BloodColor;
import com.simpleblood.Guard;
import com.simpleblood.SimpleBloodClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
//? if >1.21.1 {
import net.minecraft.client.particle.SingleQuadParticle.Layer;
//?}
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;

//? if <=1.21.1 {
/*public class BloodMistParticle extends net.minecraft.client.particle.TextureSheetParticle {
*///?} else {
public class BloodMistParticle extends SingleQuadParticle {
//?}

    private static final int LIFE = 25, LIFE_SPREAD = 15;
    private static final float START_ALPHA = 0.6f;
    private static final float START_SIZE = 0.7f;
    private static final float HOLD = 0.5f;
    private static final float DRAG = 0.82f;
    private static final float SINK = 0.0003f;

    private static final ThreadLocal<Float> nextSize = ThreadLocal.withInitial(() -> 1.0f);

    public static void setSize(float across) {
        nextSize.set(across);
    }

    private final float fullSize;
    private boolean counted;

    protected BloodMistParticle(ClientLevel world, double x, double y, double z, double velX, double velY, double velZ,
                                TextureAtlasSprite sprite, float across, float red, float green, float blue) {
        //? if <=1.21.1 {
        /*super(world, x, y, z, velX, velY, velZ);
        this.setSprite(sprite);
        *///?} else {
        super(world, x, y, z, velX, velY, velZ, sprite);
        //?}
        BloodParticle.countBirth();
        this.counted = true;
        this.xd = velX;
        this.yd = velY;
        this.zd = velZ;
        this.lifetime = Math.max(8, (int) ((LIFE + random.nextInt(LIFE_SPREAD)) * SimpleBloodClient.getConfig().particleLifetimeMultiplier()));
        this.fullSize = across * 0.5f * (0.85f + random.nextFloat() * 0.3f);
        this.quadSize = fullSize * START_SIZE;
        this.setSize(0.1f, 0.1f);
        float shade = 0.92f + random.nextFloat() * 0.12f;
        this.setColor(Math.min(1f, red * shade), Math.min(1f, green * shade), Math.min(1f, blue * shade));
        this.alpha = START_ALPHA;
        this.gravity = 0f;
        this.hasPhysics = true;
    }

    private volatile boolean ticked;

    private void putBack() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == this.level) mc.particleEngine.add(this);
    }

    @Override
    public void remove() {
        if (!ticked && !this.removed && com.simpleblood.compat.AsyncParticlesTicks.evictingSpawns()
                && com.simpleblood.compat.AsyncParticlesTicks.hold(this::putBack)) {
            return;
        }
        if (counted) {
            counted = false;
            BloodParticle.countDeath();
        }
        super.remove();
    }

    @Override
    public void tick() {
        ticked = true;
        if (!Guard.ok(Guard.Part.PARTICLES)) {
            this.remove();
            return;
        }
        try {
            drift();
        } catch (Throwable t) {
            Guard.fail(Guard.Part.PARTICLES, "a wisp of blood mist", t);
            this.remove();
        }
    }

    private void drift() {
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
        yd -= SINK * SimpleBloodClient.getConfig().particleGravityMultiplier();
        this.move(xd, yd, zd);
        xd *= DRAG;
        yd *= DRAG;
        zd *= DRAG;
        float life = (float) age / lifetime;
        this.quadSize = fullSize * (START_SIZE + (1f - START_SIZE) * (float) Math.sqrt(life));
        this.alpha = life < HOLD ? START_ALPHA : START_ALPHA * (1f - (life - HOLD) / (1f - HOLD));
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

        private static final int VARIANTS = 4;
        private final SpriteSet sprites;

        public Factory(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        //? if <=1.21.1 {
        /*public Particle createParticle(SimpleParticleType type, ClientLevel world,
                                       double x, double y, double z,
                                       double velX, double velY, double velZ) {
            RandomSource random = world.getRandom();
        *///?} else {
        public Particle createParticle(SimpleParticleType type, ClientLevel world,
                                       double x, double y, double z,
                                       double velX, double velY, double velZ,
                                       RandomSource random) {
        //?}
            if (!Guard.ok(Guard.Part.PARTICLES)) return null;
            try {
                BloodColor.Color color = BloodParticle.currentBloodColor();
                TextureAtlasSprite sprite = sprites.get(random.nextInt(VARIANTS), VARIANTS - 1);
                return new BloodMistParticle(world, x, y, z, velX, velY, velZ, sprite, nextSize.get(),
                        color.red, color.green, color.blue);
            } catch (Throwable t) {
                Guard.fail(Guard.Part.PARTICLES, "creating blood mist", t);
                return null;
            }
        }
    }
}
