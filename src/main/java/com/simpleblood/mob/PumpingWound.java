package com.simpleblood.mob;

import com.simpleblood.BloodParticles;
import com.simpleblood.ClientBloodParticleSpawner;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class PumpingWound {

    private static final int FIRST_GUSH = 30;
    private static final int BEAT_TICKS = 4;
    public static final float DRY = 0.02f;
    private static final double SPRAY_CONE = Math.toRadians(50);
    private static final double CARRY = 1.0;
    private static final RandomSource RNG = RandomSource.create();

    private final long since;
    private long nextBeat;

    public PumpingWound(long since) {
        this.since = since;
        this.nextBeat = since + FIRST_GUSH;
    }

    public long since() {
        return since;
    }

    public boolean pump(ClientLevel level, Vec3 at, Vec3 dir, Vec3 carried, float pressure, long now, int percent, float jet) {
        if (pressure <= DRY || percent <= 0) return false;
        long age = now - since;
        float life = (float) Math.sqrt(Math.min(1f, pressure));
        long intoBeat = now - nextBeat;
        if (intoBeat >= BEAT_TICKS) {
            nextBeat = now + 8 + Math.round((1f - life) * 10);
            intoBeat = now - nextBeat;
        }
        int count;
        double speed;
        if (age < FIRST_GUSH) {
            float gush = 1f - age / (float) FIRST_GUSH;
            count = scaled(RNG.nextFloat() < 0.4f + 0.6f * gush ? 3 : 2, percent);
            speed = (0.10 + 0.14 * gush) * (0.35 + 0.65 * life);
        } else if (intoBeat >= 0) {
            count = scaled(life > 0.5f ? 3 : 2, Math.round(percent * life));
            speed = 0.07 + 0.15 * life;
        } else {
            count = RNG.nextFloat() < 0.55f * life ? scaled(1, percent) : 0;
            speed = 0.025 + 0.02 * life;
        }
        if (count <= 0) return true;
        speed *= jet;
        Vec3 side = Math.abs(dir.y) < 0.9 ? dir.cross(new Vec3(0, 1, 0)).normalize() : dir.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 over = dir.cross(side);
        for (int k = 0; k < count; k++) {
            double tilt = SPRAY_CONE * RNG.nextDouble(), round = RNG.nextDouble() * Math.PI * 2;
            double along = Math.cos(tilt), across = Math.sin(tilt);
            double v = speed * (0.85 + 0.3 * RNG.nextDouble());
            double cx = Math.cos(round) * across, cy = Math.sin(round) * across;
            ClientBloodParticleSpawner.emit(level, BloodParticles.BLOOD_SPRAY, at.x, at.y, at.z,
                    (dir.x * along + side.x * cx + over.x * cy) * v + carried.x * CARRY,
                    (dir.y * along + side.y * cx + over.y * cy) * v + carried.y * CARRY,
                    (dir.z * along + side.z * cx + over.z * cy) * v + carried.z * CARRY);
        }
        return true;
    }

    public static int scaled(int base, int percent) {
        float f = base * Math.max(0, percent) / 100f;
        int n = (int) f;
        return RNG.nextFloat() < f - n ? n + 1 : n;
    }
}
