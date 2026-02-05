package com.bloodmod.mixin;

import com.bloodmod.BloodMod;
import com.bloodmod.BloodModClient;
import com.bloodmod.ClientBloodBurstTask;
import com.bloodmod.ClientBloodParticleSpawner;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Unique
    private static final Map<Integer, Long> lastDamageTime = new HashMap<>();

    @Unique
    private static final Map<Integer, Float> lastHealth = new HashMap<>();

    /**
     * Get damage cooldown from config
     */
    @Unique
    private static long getDamageCooldown() {
        return BloodModClient.getConfig().damageCooldownMs();
    }

    /**
     * Detect when entity's tracked data changes (including health).
     * This is called on the client when the server sends entity data updates.
     * We use this to detect when health decreases, which indicates damage.
     */
    @Inject(method = "onTrackedDataSet", at = @At("HEAD"))
    private void onTrackedDataSet(TrackedData<?> data, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity)(Object)this;

        // Only run on client side
        if (!entity.getEntityWorld().isClient() || !(entity.getEntityWorld() instanceof ClientWorld clientWorld)) {
            return;
        }

        // FIXED: Instead of checking data ID (which can change), we check every tracked data update
        // and compare the current health with our stored value. This is more robust.

        // Skip if game is paused (singleplayer only)
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isPaused()) {
            return;
        }

        int entityId = entity.getId();
        float currentHealth = entity.getHealth();
        Float previous = lastHealth.get(entityId);

        // Only process if health decreased (damage taken)
        if (previous == null || currentHealth >= previous) {
            // Health increased or stayed same - just update and return
            lastHealth.put(entityId, currentHealth);
            return;
        }

        // Health decreased - entity took damage!
        float damage = previous - currentHealth;

        // Spam prevention: Check if this entity was damaged recently (using config cooldown)
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastDamageTime.get(entityId);

        if (lastTime != null && (currentTime - lastTime) < getDamageCooldown()) {
            lastHealth.put(entityId, currentHealth);
            return; // Too soon, skip this damage event
        }

        lastDamageTime.put(entityId, currentTime);
        lastHealth.put(entityId, currentHealth);

        var cfg = BloodModClient.getConfig();

        // Check if mod is enabled and hit burst is enabled
        if (!cfg.globalEnabled() || !cfg.hitBurstEnabled()) return;

        // Check player settings
        if (entity instanceof PlayerEntity player) {
            if (!cfg.playerBleed()) return;
            if (player.isCreative() || player.isSpectator()) return;
        }

        // Only spawn blood for entities that should bleed
        if (BloodMod.shouldEntityBleed(entity)) {
            BloodMod.LOGGER.debug("Spawning blood burst for entity {} (ID: {}) with damage {}",
                    entity.getType().getTranslationKey(), entityId, damage);
            BloodModClient.addBurstTask(new ClientBloodBurstTask(clientWorld, entity, damage));
        }
    }

    /**
     * Detect when entity dies (client-side).
     * Triggers death burst effect.
     */
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity)(Object)this;

        // Only run on client side
        if (!entity.getEntityWorld().isClient() || !(entity.getEntityWorld() instanceof ClientWorld clientWorld)) {
            return;
        }

        // Skip if game is paused (singleplayer only)
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isPaused()) {
            return;
        }

        var cfg = BloodModClient.getConfig();

        // Check if mod is enabled and death burst is enabled
        if (!cfg.globalEnabled() || !cfg.deathBurstEnabled()) return;

        // Check player settings
        if (entity instanceof PlayerEntity player) {
            if (!cfg.playerBleed()) return;
            if (player.isCreative() || player.isSpectator()) return;
        }

        // Only spawn blood for entities that should bleed
        if (BloodMod.shouldEntityBleed(entity)) {
            ClientBloodParticleSpawner.spawnBloodOnDeath(clientWorld, entity);
        }

        // Clean up tracking maps for this entity
        int entityId = entity.getId();
        lastHealth.remove(entityId);
        lastDamageTime.remove(entityId);
    }
}