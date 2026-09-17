package com.bloodmod;

import com.bloodmod.BloodColor.Color;
import com.bloodmod.surface.BloodSurfaces;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

public final class BloodModAPI {

    private BloodModAPI() {}

    private static final Map<String, BloodSettings> customBloodRegistry = new java.util.concurrent.ConcurrentHashMap<>();

    private static final Set<ResourceKey<DamageType>> nonBleedingDamageTypes = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void registerEntityBlood(EntityType<?> entityType, BloodSettings settings) {
        registerEntityBlood(BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString(), settings);
    }

    public static void registerEntityBlood(Identifier entityId, BloodSettings settings) {
        registerEntityBlood(entityId.toString(), settings);
    }

    public static void registerEntityBlood(String entityId, BloodSettings settings) {
        if (entityId == null || settings == null) return;
        customBloodRegistry.put(entityId, settings);
        BloodMod.LOGGER.info("Registered custom blood settings for entity: {}", entityId);
    }

    public static BloodSettings getEntityBloodSettings(String entityId) {
        return entityId == null ? null : customBloodRegistry.get(entityId);
    }

    public static BloodSettings getEntityBloodSettings(LivingEntity entity) {
        return customBloodRegistry.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    public static boolean hasCustomSettings(String entityId) {
        return entityId != null && customBloodRegistry.containsKey(entityId);
    }

    public static void unregisterEntityBlood(String entityId) {
        if (entityId == null) return;
        customBloodRegistry.remove(entityId);
        BloodMod.LOGGER.info("Unregistered custom blood settings for entity: {}", entityId);
    }

    public static void clearAllRegistrations() {
        customBloodRegistry.clear();
        BloodMod.LOGGER.info("Cleared all custom blood registrations");
    }

    public static Set<String> getRegisteredEntities() {
        return Collections.unmodifiableSet(customBloodRegistry.keySet());
    }

    public static void addNonBleedingDamageType(ResourceKey<DamageType> type) {
        if (type != null) nonBleedingDamageTypes.add(type);
    }

    public static void removeNonBleedingDamageType(ResourceKey<DamageType> type) {
        if (type != null) nonBleedingDamageTypes.remove(type);
    }

    public static boolean isNonBleedingDamageType(DamageSource source) {
        if (nonBleedingDamageTypes.isEmpty()) return false;
        for (ResourceKey<DamageType> key : nonBleedingDamageTypes) {
            if (source.is(key)) return true;
        }
        return false;
    }

    public static boolean spawnHit(LivingEntity entity, float damage) {
        return Guard.call(Guard.Part.API, "spawnHit", () -> {
            if (entity == null || !(entity.level() instanceof ClientLevel level) || entity.isDeadOrDying() || damage <= 0) return false;
            BloodModConfig cfg = BloodModClient.getConfig();
            if (cfg == null || !cfg.globalEnabled() || !BloodMod.shouldEntityBleed(entity)) return false;
            BloodModClient.addBurstTask(new ClientBloodBurstTask(level, entity, damage));
            return true;
        }, false);
    }

    public static boolean spawnDeath(LivingEntity entity) {
        return Guard.call(Guard.Part.API, "spawnDeath", () -> {
            if (entity == null || !(entity.level() instanceof ClientLevel level)) return false;
            BloodModConfig cfg = BloodModClient.getConfig();
            if (cfg == null || !cfg.globalEnabled() || !BloodMod.shouldEntityBleed(entity)) return false;
            RenderThread.run(() -> Guard.run(Guard.Part.API, "spawnDeath", () -> ClientBloodParticleSpawner.spawnBloodOnDeath(level, entity)));
            return true;
        }, false);
    }

    public static boolean paint(Level level, Vec3 from, Vec3 to, int rgb, int pixels) {
        return Guard.call(Guard.Part.API, "paint", () -> {
            if (!(level instanceof ClientLevel client) || from == null || to == null) return false;
            BloodSurfaces.Hit hit = BloodSurfaces.trace(client, from, to);
            return hit != null && BloodSurfaces.deposit(client, hit, rgb & 0xFFFFFF, pixels, to.subtract(from)) != BloodSurfaces.NOTHING;
        }, false);
    }

    public static void clearSurfaceBlood() {
        Guard.run(Guard.Part.API, "clearSurfaceBlood", BloodSurfaces::clear);
    }

    public static int surfaceBloodCount() {
        return BloodSurfaces.tileCount();
    }

    public static final class BloodSettings {
        private Integer colorRGB = null;
        private Function<LivingEntity, Integer> colorProvider = null;
        private Boolean canBleed = true;
        private Boolean canDripAtLowHealth = true;
        private Boolean transformToStains = true;
        private BloodKind bloodKind = null;
        private Boolean leavesFootprints = null;
        private Boolean createsFogUnderwater = null;
        private Boolean particlesDespawnInWater = null;
        private Boolean bleedWhenAsphyxiating = null;
        private Float particleSizeMultiplier = null;
        private Float burstIntensityMultiplier = null;
        private Float dripIntensityMultiplier = null;
        private SoundEvent bloodSound = null;
        private Boolean soundEnabled = null;
        private BiPredicate<LivingEntity, DamageSource> bleedPredicate = null;

        public BloodSettings setColor(int rgb) {
            this.colorRGB = rgb;
            return this;
        }

        public BloodSettings setColor(int red, int green, int blue) {
            this.colorRGB = (red << 16) | (green << 8) | blue;
            return this;
        }

        public BloodSettings setColorProvider(Function<LivingEntity, Integer> provider) {
            this.colorProvider = provider;
            return this;
        }

        public BloodSettings setCanBleed(boolean canBleed) {
            this.canBleed = canBleed;
            return this;
        }

        public BloodSettings setCanDripAtLowHealth(boolean canDrip) {
            this.canDripAtLowHealth = canDrip;
            return this;
        }

        public BloodSettings setTransformToStains(boolean transform) {
            this.transformToStains = transform;
            return this;
        }

        public BloodSettings setCreatesFogUnderwater(boolean createsFog) {
            this.createsFogUnderwater = createsFog;
            return this;
        }

        public BloodSettings setParticlesDespawnInWater(boolean despawn) {
            this.particlesDespawnInWater = despawn;
            return this;
        }

        public BloodSettings setBleedWhenAsphyxiating(boolean bleed) {
            this.bleedWhenAsphyxiating = bleed;
            return this;
        }

        public BloodSettings setParticleSizeMultiplier(float multiplier) {
            this.particleSizeMultiplier = multiplier;
            return this;
        }

        public BloodSettings setBurstIntensityMultiplier(float multiplier) {
            this.burstIntensityMultiplier = multiplier;
            return this;
        }

        public BloodSettings setDripIntensityMultiplier(float multiplier) {
            this.dripIntensityMultiplier = multiplier;
            return this;
        }

        public BloodSettings setBloodSound(SoundEvent sound) {
            this.bloodSound = sound;
            return this;
        }

        public BloodSettings setSoundEnabled(boolean enabled) {
            this.soundEnabled = enabled;
            return this;
        }

        public BloodSettings setBleedPredicate(BiPredicate<LivingEntity, DamageSource> predicate) {
            this.bleedPredicate = predicate;
            return this;
        }

        public Color getColor() {
            return colorRGB == null ? null : new Color(colorRGB);
        }

        public Integer resolveColorRGB(LivingEntity entity) {
            if (colorProvider != null) {
                try {
                    Integer dynamic = colorProvider.apply(entity);
                    if (dynamic != null) return dynamic;
                } catch (Exception e) {
                    BloodMod.LOGGER.debug("Blood colour provider threw for {}: {}",
                            entity.getType().getDescriptionId(), e.getMessage());
                }
            }
            return colorRGB;
        }

        public Integer getColorRGB() { return colorRGB; }
        public Function<LivingEntity, Integer> getColorProvider() { return colorProvider; }
        public Boolean getCanBleed() { return canBleed; }
        public Boolean getCanDripAtLowHealth() { return canDripAtLowHealth; }
        public Boolean getTransformToStains() { return transformToStains; }

        public BloodSettings setBloodKind(BloodKind kind) {
            this.bloodKind = kind;
            return this;
        }

        public BloodSettings setLeavesFootprints(boolean leaves) {
            this.leavesFootprints = leaves;
            return this;
        }

        public BloodKind getBloodKind() { return bloodKind; }
        public Boolean getLeavesFootprints() { return leavesFootprints; }
        public Boolean getCreatesFogUnderwater() { return createsFogUnderwater; }
        public Boolean getParticlesDespawnInWater() { return particlesDespawnInWater; }
        public Boolean getBleedWhenAsphyxiating() { return bleedWhenAsphyxiating; }
        public Float getParticleSizeMultiplier() { return particleSizeMultiplier; }
        public Float getBurstIntensityMultiplier() { return burstIntensityMultiplier; }
        public Float getDripIntensityMultiplier() { return dripIntensityMultiplier; }
        public SoundEvent getBloodSound() { return bloodSound; }
        public Boolean getSoundEnabled() { return soundEnabled; }
        public BiPredicate<LivingEntity, DamageSource> getBleedPredicate() { return bleedPredicate; }
    }
}
