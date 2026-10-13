package com.simpleblood.mixin;

import com.simpleblood.BloodEntityAccess;
import com.simpleblood.SimpleBlood;
import com.simpleblood.SimpleBloodClient;
import com.simpleblood.HitContext;
import com.simpleblood.ClientBloodBurstTask;
import com.simpleblood.ClientBloodParticleSpawner;
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

/**
 * Client-side hit/death detection.
 *
 * Per-entity tracking is stored in {@code @Unique} instance fields rather than static
 * maps keyed by entity id. Because each {@link LivingEntity} carries its own mixin
 * state, the data is garbage-collected with the entity - no manual cleanup and no
 * unbounded growth when entities unload or change dimensions.
 *
 * Damage is detected from the synced health value (which carries the amount, for burst
 * scaling), while {@code handleDamageEvent} - fired just before the health sync - gives
 * us the {@link DamageSource}. Correlating the two lets a fish bleed when it is *hit*
 * out of water, yet stay dry when it is merely drowning / suffocating / drying out.
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin implements BloodEntityAccess {

    @Unique
    private float simpleblood$lastHealth = Float.NaN;

    @Unique
    private long simpleblood$lastDamageTime = 0L;

    @Unique
    private DamageSource simpleblood$recentSource = null;

    @Unique
    private int simpleblood$recentSourceTick = -1000;

    @Unique
    private int simpleblood$critTick = -1000;

    @Unique
    private HitContext simpleblood$lastContext = null;

    @Unique
    private int simpleblood$lastContextTick = -1000;

    @Override
    public long simpleblood$getLastDamageTime() {
        return simpleblood$lastDamageTime;
    }

    @Override
    public void simpleblood$markCrit() {
        simpleblood$critTick = ((LivingEntity) (Object) this).tickCount;
    }

    @Override
    public boolean simpleblood$critRecently() {
        int age = ((LivingEntity) (Object) this).tickCount - simpleblood$critTick;
        return age >= 0 && age <= 3;
    }

    // The wound is kept in body space (relative to the feet, unrotated by the body yaw) so
    // it stays on the same patch of skin while the mob walks and turns.
    @Unique
    private net.minecraft.world.phys.Vec3 simpleblood$woundLocal = null;

    @Override
    public void simpleblood$setWound(net.minecraft.world.phys.Vec3 world) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (world == null) {
            simpleblood$woundLocal = null;
            return;
        }
        net.minecraft.world.phys.Vec3 rel = world.subtract(self.position());
        simpleblood$woundLocal = rel.yRot((float) Math.toRadians(self.yBodyRot));
    }

    @Override
    public net.minecraft.world.phys.Vec3 simpleblood$wound() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (simpleblood$woundLocal == null) return null;
        return self.position().add(simpleblood$woundLocal.yRot((float) -Math.toRadians(self.yBodyRot)));
    }

    /**
     * Capture the damage source client-side. This fires when the server's damage event
     * packet arrives (for the hurt animation/sound), just before the health sync.
     */
    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void simpleblood$captureDamageSource(DamageSource source, CallbackInfo ci) {
        simpleblood$recentSource = source;
        simpleblood$recentSourceTick = ((LivingEntity) (Object) this).tickCount;
    }

    /**
     * Detect a health decrease and emit a hit burst. Comparing the tracked health value
     * is more robust than watching a specific data-accessor id.
     *
     * Runs inside vanilla's packet handling, so nothing in here may throw: an error is
     * caught, reported, and hit detection is switched off.
     */
    @Inject(method = "onSyncedDataUpdated", at = @At("HEAD"))
    private void onTrackedDataSet(EntityDataAccessor<?> data, CallbackInfo ci) {
        if (!com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.HITS)) return;
        if (!com.simpleblood.RenderThread.on()) {
            // Some mod handed us entity data off the render thread: everything we do belongs there.
            com.simpleblood.RenderThread.run(() -> onTrackedDataSet(data, ci));
            return;
        }
        try {
            simpleblood$onHealthChanged();
        } catch (Throwable t) {
            com.simpleblood.Guard.fail(com.simpleblood.Guard.Part.HITS, "noticing a hit", t);
        }
    }

    @Unique
    private void simpleblood$onHealthChanged() {
        LivingEntity entity = (LivingEntity) (Object) this;

        if (!entity.level().isClientSide() || !(entity.level() instanceof ClientLevel clientWorld)) {
            return;
        }
        if (Minecraft.getInstance().isPaused()) {
            return;
        }

        float currentHealth = entity.getHealth();
        float previous = simpleblood$lastHealth;

        // First observation or health did not drop: just record and bail.
        if (Float.isNaN(previous) || currentHealth >= previous) {
            simpleblood$lastHealth = currentHealth;
            return;
        }

        float damage = previous - currentHealth;
        simpleblood$lastHealth = currentHealth;

        // Ignore health "drops" that are really the entity's health initialising on
        // spawn (staged max-health sync, e.g. horses/donkeys/llamas). Genuine damage
        // always leaves the entity below its max health.
        if (entity.tickCount < 10 || currentHealth >= entity.getMaxHealth()) {
            return;
        }

        // Prefer the authoritative DamageSource captured from handleDamageEvent so a
        // combat hit still bleeds even out of water. Fall back to the positional
        // heuristic only when no fresh damage event is available.
        int sourceAge = entity.tickCount - simpleblood$recentSourceTick;
        boolean freshSource = simpleblood$recentSource != null && sourceAge >= 0 && sourceAge <= 2;
        if (freshSource) {
            if (!SimpleBlood.shouldBleedFrom(entity, simpleblood$recentSource)) {
                return; // asphyxiation / suppressed damage type - no blood
            }
            // Blood on whatever did the hurting: stalagmites, cactus, berry bushes.
            if (SimpleBlood.shouldEntityBleed(entity)) {
                com.simpleblood.surface.EnvironmentalStains.onDamage(clientWorld, entity, simpleblood$recentSource, damage);
            }
        } else if (SimpleBlood.isEnvironmentalNoBleed(entity)) {
            return;
        }

        // Spam prevention using the configured cooldown. A player's killing blow is never
        // held back by it: that one has to show.
        boolean playerDied = entity instanceof Player && currentHealth <= 0.0f && previous > 0.0f;
        long now = System.currentTimeMillis();
        if (!playerDied && now - simpleblood$lastDamageTime < SimpleBloodClient.getConfig().damageCooldownMs()) {
            return;
        }
        simpleblood$lastDamageTime = now;

        var cfg = SimpleBloodClient.getConfig();
        if (!cfg.globalEnabled() || !cfg.hitBurstEnabled()) return;

        if (entity instanceof Player player) {
            if (!cfg.playerBleed()) return;
            if (player.isCreative() || player.isSpectator()) return;
        }

        if (SimpleBlood.shouldEntityBleed(entity)) {
            SimpleBlood.LOGGER.debug("Spawning blood burst for {} with damage {}",
                    entity.getType().getDescriptionId(), damage);
            HitContext ctx = HitContext.of(entity, damage, freshSource ? simpleblood$recentSource : null,
                    simpleblood$critRecently(), previous);
            simpleblood$lastContext = ctx;
            simpleblood$lastContextTick = entity.tickCount;
            simpleblood$setWound(ctx.wound); // later drips come from here too
            SimpleBloodClient.addBurstTask(new ClientBloodBurstTask(clientWorld, entity, ctx));
            if (!playerDied) com.simpleblood.Giblets.hit(clientWorld, entity, ctx); // a death throws its own
            if (!playerDied) com.simpleblood.mob.MobBlood.hit(entity, ctx);
            // The client never calls die() for players (its own or anyone else's), so a
            // player's killing blow is recognised here instead: health gone, death burst.
            if (playerDied && cfg.deathBurstEnabled()) {
                ClientBloodParticleSpawner.spawnBloodOnDeath(clientWorld, entity, ctx);
            }
        }
    }

    /**
     * Client-side death detection: triggers the death burst.
     */
    @Inject(method = "die", at = @At("HEAD"))
    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
        if (!com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.HITS)) return;
        if (!com.simpleblood.RenderThread.on()) {
            com.simpleblood.RenderThread.run(() -> onDeath(damageSource, ci));
            return;
        }
        try {
            simpleblood$onDied(damageSource);
        } catch (Throwable t) {
            com.simpleblood.Guard.fail(com.simpleblood.Guard.Part.HITS, "noticing a death", t);
        }
    }

    @Unique
    private void simpleblood$onDied(DamageSource damageSource) {
        LivingEntity entity = (LivingEntity) (Object) this;

        if (!entity.level().isClientSide() || !(entity.level() instanceof ClientLevel clientWorld)) {
            return;
        }
        if (Minecraft.getInstance().isPaused()) {
            return;
        }

        // No death blood from suffocation / drowning / drying out / suppressed types.
        if (!SimpleBlood.shouldBleedFrom(entity, damageSource)) {
            return;
        }

        var cfg = SimpleBloodClient.getConfig();
        if (!cfg.globalEnabled() || !cfg.deathBurstEnabled()) return;

        if (entity instanceof Player player) {
            if (!cfg.playerBleed()) return;
            if (player.isCreative() || player.isSpectator()) return;
        }

        if (SimpleBlood.shouldEntityBleed(entity)) {
            // Reuse the killing blow's context when it is fresh, so the death spray follows it.
            HitContext ctx = simpleblood$lastContext != null && entity.tickCount - simpleblood$lastContextTick <= 2
                    ? simpleblood$lastContext
                    : HitContext.of(entity, Math.max(1.0f, simpleblood$lastHealth), damageSource, simpleblood$critRecently(),
                            Float.isNaN(simpleblood$lastHealth) ? entity.getMaxHealth() : simpleblood$lastHealth);
            ClientBloodParticleSpawner.spawnBloodOnDeath(clientWorld, entity, ctx);
        }
    }
}
