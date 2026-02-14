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

    @Unique
    private static long getDamageCooldown() {
        return BloodModClient.getConfig().damageCooldownMs();
    }

    @Inject(method = "onTrackedDataSet", at = @At("HEAD"))
    private void onTrackedDataSet(TrackedData<?> data, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity)(Object)this;

        if (!entity.getEntityWorld().isClient() || !(entity.getEntityWorld() instanceof ClientWorld clientWorld)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isPaused()) {
            return;
        }

        int entityId = entity.getId();
        float currentHealth = entity.getHealth();
        Float previous = lastHealth.get(entityId);

        if (previous == null || currentHealth >= previous) {

            lastHealth.put(entityId, currentHealth);
            return;
        }

        float damage = previous - currentHealth;

        long currentTime = System.currentTimeMillis();
        Long lastTime = lastDamageTime.get(entityId);

        if (lastTime != null && (currentTime - lastTime) < getDamageCooldown()) {
            lastHealth.put(entityId, currentHealth);
            return; 

        }

        lastDamageTime.put(entityId, currentTime);
        lastHealth.put(entityId, currentHealth);

        var cfg = BloodModClient.getConfig();

        if (!cfg.globalEnabled() || !cfg.hitBurstEnabled()) return;

        if (entity instanceof PlayerEntity player) {
            if (!cfg.playerBleed()) return;
            if (player.isCreative() || player.isSpectator()) return;
        }

        if (BloodMod.shouldEntityBleed(entity)) {
            BloodMod.LOGGER.debug("Spawning blood burst for entity {} (ID: {}) with damage {}",
                    entity.getType().getTranslationKey(), entityId, damage);
            BloodModClient.addBurstTask(new ClientBloodBurstTask(clientWorld, entity, damage));
        }
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity)(Object)this;

        if (!entity.getEntityWorld().isClient() || !(entity.getEntityWorld() instanceof ClientWorld clientWorld)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isPaused()) {
            return;
        }

        var cfg = BloodModClient.getConfig();

        if (!cfg.globalEnabled() || !cfg.deathBurstEnabled()) return;

        if (entity instanceof PlayerEntity player) {
            if (!cfg.playerBleed()) return;
            if (player.isCreative() || player.isSpectator()) return;
        }

        if (BloodMod.shouldEntityBleed(entity)) {
            ClientBloodParticleSpawner.spawnBloodOnDeath(clientWorld, entity);
        }

        int entityId = entity.getId();
        lastHealth.remove(entityId);
        lastDamageTime.remove(entityId);
    }
}