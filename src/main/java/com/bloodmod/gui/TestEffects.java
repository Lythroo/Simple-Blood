package com.bloodmod.gui;

import com.bloodmod.BloodColor;
import com.bloodmod.BloodKind;
import com.bloodmod.BloodModClient;
import com.bloodmod.BloodModConfig;
import com.bloodmod.BloodParticles;
import com.bloodmod.ClientBloodParticleSpawner;
import com.bloodmod.particle.BloodParticle;
import com.bloodmod.surface.BloodSurfaces;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class TestEffects {

    private TestEffects() {}

    private static BlockHitResult target() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        HitResult hit = mc.player.pick(8.0, 0f, false);
        return hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b : null;
    }

    private static boolean begin() {
        Minecraft mc = Minecraft.getInstance();
        BloodModConfig cfg = BloodModClient.getConfig();
        if (mc.player == null || cfg == null) return false;
        BloodParticle.setCurrentBloodColor(BloodColor.getBloodColor(mc.player));
        BloodParticle.setShouldTransformToFog(true);
        BloodParticle.setShouldDespawnInWater(false);
        BloodParticle.setEntitySizeMultiplier(1.0f);
        BloodParticle.setCurrentKind(BloodKind.LIQUID);
        return true;
    }

    public static void drops() {
        BlockHitResult hit = target();
        if (hit == null || !begin()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        RandomSource rng = level.getRandom();
        Vec3 p = hit.getLocation();
        Vec3 n = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
        Vec3 origin = p.add(n.scale(0.05));
        for (int i = 0; i < 14; i++) {
            Vec3 at = origin.add((rng.nextDouble() - 0.5) * 0.5, 0.5 + rng.nextDouble() * 0.5, (rng.nextDouble() - 0.5) * 0.5);
            Vec3 v = new Vec3((rng.nextDouble() - 0.5) * 0.06, -0.3 - rng.nextDouble() * 0.4, (rng.nextDouble() - 0.5) * 0.06);
            ClientBloodParticleSpawner.emit(level, i % 3 == 0 ? BloodParticles.BLOOD_SPLASH : BloodParticles.BLOOD_DRIP,
                    at.x, at.y, at.z, v.x, v.y, v.z);
        }
    }

    public static void burst() {
        BlockHitResult hit = target();
        if (hit == null || !begin()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        RandomSource rng = level.getRandom();
        Vec3 p = hit.getLocation();
        Vec3 n = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
        Vec3 c = p.add(n.scale(0.6)).add(0, 0.3, 0);
        Vec3 dir = mc.player.getViewVector(1f);
        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
        float amount = BloodModClient.getConfig().masterAmount();
        int count = Math.round(26 * amount);
        for (int i = 0; i < count; i++) {
            double a = (rng.nextDouble() - 0.5) * 1.6;
            Vec3 d = dir.scale(Math.cos(a)).add(side.scale(Math.sin(a))).normalize();
            double s = 0.15 + rng.nextDouble() * 0.35;
            Vec3 v = d.scale(s).add(0, 0.05 + rng.nextDouble() * 0.12, 0);
            Vec3 at = c.add((rng.nextDouble() - 0.5) * 0.3, (rng.nextDouble() - 0.5) * 0.3, (rng.nextDouble() - 0.5) * 0.3);
            ClientBloodParticleSpawner.emit(level, i % 2 == 0 ? BloodParticles.BLOOD_SPLASH : BloodParticles.BLOOD_DRIP,
                    at.x, at.y, at.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < Math.round(5 * amount); i++) {
            double a = (rng.nextDouble() - 0.5) * 1.2;
            Vec3 d = dir.scale(Math.cos(a)).add(side.scale(Math.sin(a))).normalize();
            Vec3 v = d.scale(0.45 + rng.nextDouble() * 0.3).add(0, 0.03, 0);
            ClientBloodParticleSpawner.emit(level, BloodParticles.BLOOD_STREAK, c.x, c.y, c.z, v.x, v.y, v.z);
        }
    }

    public static void clearBlood() {
        BloodSurfaces.clear();
    }

    public static boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null;
    }
}
