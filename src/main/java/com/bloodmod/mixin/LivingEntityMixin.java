package com.bloodmod.mixin;

import com.bloodmod.BloodEntityAccess;
import com.bloodmod.BloodMod;
import com.bloodmod.BloodModClient;
import com.bloodmod.HitContext;
import com.bloodmod.ClientBloodBurstTask;
import com.bloodmod.ClientBloodParticleSpawner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin implements BloodEntityAccess {

    @Unique
    private float bloodmod$lastHealth = Float.NaN;

    @Unique
    private long bloodmod$lastDamageTime = 0L;

    @Unique
    private DamageSource bloodmod$recentSource = null;

    @Unique
    private int bloodmod$recentSourceTick = -1000;

    @Unique
    private int bloodmod$critTick = -1000;

    @Unique
    private HitContext bloodmod$lastContext = null;

    @Unique
    private int bloodmod$lastContextTick = -1000;

    @Override
    public long bloodmod$getLastDamageTime() {
        return bloodmod$lastDamageTime;
    }

    @Override
    public void bloodmod$markCrit() {
        bloodmod$critTick = ((LivingEntity) (Object) this).tickCount;
    }

    @Override
    public boolean bloodmod$critRecently() {
        int age = ((LivingEntity) (Object) this).tickCount - bloodmod$critTick;
        return age >= 0 && age <= 3;
    }

    @Unique
    private net.minecraft.world.phys.Vec3 bloodmod$woundLocal = null;

    @Override
    public void bloodmod$setWound(net.minecraft.world.phys.Vec3 world) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (world == null) {
            bloodmod$woundLocal = null;
            return;
        }
        net.minecraft.world.phys.Vec3 rel = world.subtract(self.position());
        bloodmod$woundLocal = rel.yRot((float) Math.toRadians(self.yBodyRot));
    }

    @Override
    public net.minecraft.world.phys.Vec3 bloodmod$wound() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (bloodmod$woundLocal == null) return null;
        return self.position().add(bloodmod$woundLocal.yRot((float) -Math.toRadians(self.yBodyRot)));
    }

    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void bloodmod$captureDamageSource(DamageSource source, CallbackInfo ci) {
        bloodmod$recentSource = source;
        bloodmod$recentSourceTick = ((LivingEntity) (Object) this).tickCount;
    }

    @Inject(method = "onSyncedDataUpdated", at = @At("HEAD"))
    private void onTrackedDataSet(EntityDataAccessor<?> data, CallbackInfo ci) {
        if (!com.bloodmod.Guard.ok(com.bloodmod.Guard.Part.HITS)) return;
        if (!com.bloodmod.RenderThread.on()) {
            com.bloodmod.RenderThread.run(() -> onTrackedDataSet(data, ci));
            return;
        }
        try {
            bloodmod$onHealthChanged();
        } catch (Throwable t) {
            com.bloodmod.Guard.fail(com.bloodmod.Guard.Part.HITS, "noticing a hit", t);
        }
    }

    @Unique
    private void bloodmod$onHealthChanged() {
        LivingEntity entity = (LivingEntity) (Object) this;

        if (!entity.level().isClientSide() || !(entity.level() instanceof ClientLevel clientWorld)) {
            return;
        }
        if (Minecraft.getInstance().isPaused()) {
            return;
        }

        float currentHealth = entity.getHealth();
        float previous = bloodmod$lastHealth;

        if (Float.isNaN(previous) || currentHealth >= previous) {
            bloodmod$lastHealth = currentHealth;
            return;
        }

        float damage = previous - currentHealth;
        bloodmod$lastHealth = currentHealth;

        if (entity.tickCount < 10 || currentHealth >= entity.getMaxHealth()) {
            return;
        }

        int sourceAge = entity.tickCount - bloodmod$recentSourceTick;
        boolean freshSource = bloodmod$recentSource != null && sourceAge >= 0 && sourceAge <= 2;
        if (freshSource) {
            if (!BloodMod.shouldBleedFrom(entity, bloodmod$recentSource)) {
                return;
            }
            if (BloodMod.shouldEntityBleed(entity)) {
                com.bloodmod.surface.EnvironmentalStains.onDamage(clientWorld, entity, bloodmod$recentSource, damage);
            }
        } else if (BloodMod.isEnvironmentalNoBleed(entity)) {
            return;
        }

        boolean playerDied = entity instanceof Player && currentHealth <= 0.0f && previous > 0.0f;
        long now = System.currentTimeMillis();
        if (!playerDied && now - bloodmod$lastDamageTime < BloodModClient.getConfig().damageCooldownMs()) {
            return;
        }
        bloodmod$lastDamageTime = now;

        var cfg = BloodModClient.getConfig();
        if (!cfg.globalEnabled() || !cfg.hitBurstEnabled()) return;

        if (entity instanceof Player player) {
            if (!cfg.playerBleed()) return;
            if (player.isCreative() || player.isSpectator()) return;
        }

        if (BloodMod.shouldEntityBleed(entity)) {
            BloodMod.LOGGER.debug("Spawning blood burst for {} with damage {}",
                    entity.getType().getDescriptionId(), damage);
            HitContext ctx = HitContext.of(entity, damage, freshSource ? bloodmod$recentSource : null,
                    bloodmod$critRecently(), previous);
            bloodmod$lastContext = ctx;
            bloodmod$lastContextTick = entity.tickCount;
            bloodmod$setWound(ctx.wound);
            BloodModClient.addBurstTask(new ClientBloodBurstTask(clientWorld, entity, ctx));
            if (playerDied && cfg.deathBurstEnabled()) {
                ClientBloodParticleSpawner.spawnBloodOnDeath(clientWorld, entity, ctx);
            }
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
        if (!com.bloodmod.Guard.ok(com.bloodmod.Guard.Part.HITS)) return;
        if (!com.bloodmod.RenderThread.on()) {
            com.bloodmod.RenderThread.run(() -> onDeath(damageSource, ci));
            return;
        }
        try {
            bloodmod$onDied(damageSource);
        } catch (Throwable t) {
            com.bloodmod.Guard.fail(com.bloodmod.Guard.Part.HITS, "noticing a death", t);
        }
    }

    @Unique
    private void bloodmod$onDied(DamageSource damageSource) {
        LivingEntity entity = (LivingEntity) (Object) this;

        if (!entity.level().isClientSide() || !(entity.level() instanceof ClientLevel clientWorld)) {
            return;
        }
        if (Minecraft.getInstance().isPaused()) {
            return;
        }

        if (!BloodMod.shouldBleedFrom(entity, damageSource)) {
            return;
        }

        var cfg = BloodModClient.getConfig();
        if (!cfg.globalEnabled() || !cfg.deathBurstEnabled()) return;

        if (entity instanceof Player player) {
            if (!cfg.playerBleed()) return;
            if (player.isCreative() || player.isSpectator()) return;
        }

        if (BloodMod.shouldEntityBleed(entity)) {
            HitContext ctx = bloodmod$lastContext != null && entity.tickCount - bloodmod$lastContextTick <= 2
                    ? bloodmod$lastContext
                    : HitContext.of(entity, Math.max(1.0f, bloodmod$lastHealth), damageSource, bloodmod$critRecently(),
                            Float.isNaN(bloodmod$lastHealth) ? entity.getMaxHealth() : bloodmod$lastHealth);
            ClientBloodParticleSpawner.spawnBloodOnDeath(clientWorld, entity, ctx);
        }
    }
}
