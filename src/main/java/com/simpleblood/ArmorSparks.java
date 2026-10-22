package com.simpleblood;

import com.simpleblood.particle.BloodParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.IdentityHashMap;
import java.util.Map;

public final class ArmorSparks {

    private ArmorSparks() {}

    record Metal(BloodColor.Color colour) {
        static final Metal IRON = of(0.78f, 0.78f, 0.8f), CHAIN = of(0.58f, 0.6f, 0.64f), GOLD = of(0.98f, 0.8f, 0.3f),
                DIAMOND = of(0.42f, 0.88f, 0.85f), NETHERITE = of(0.3f, 0.27f, 0.28f), COPPER = of(0.86f, 0.5f, 0.32f),
                OTHER = of(0.7f, 0.7f, 0.72f);

        static Metal of(float r, float g, float b) {
            return new Metal(new BloodColor.Color(r, g, b));
        }

        static Metal ofRgb(int rgb) {
            return of(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        }
    }

    private static final String[] SOFT = {"leather", "turtle", "cloth", "wool", "hide", "fur", "wood", "stone", "bone"};
    private static final String[][] METALS = {
            {"netherite"}, {"diamond"}, {"golden", "gold"}, {"chainmail", "chain"}, {"copper"},
            {"iron"}, {"steel", "metal", "plate", "bronze", "silver", "titanium", "knight", "mace", "trident"}};
    private static final Metal[] FOR = {Metal.NETHERITE, Metal.DIAMOND, Metal.GOLD, Metal.CHAIN, Metal.COPPER, Metal.IRON, Metal.OTHER};

    private static final Map<Item, Metal> CACHE = new IdentityHashMap<>();
    private static final Metal SOFT_MARK = null;

    public static float hit(ClientLevel level, LivingEntity entity, HitContext ctx, DamageSource source) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || ctx == null) return 1f;
        if (ctx.weapon == HitContext.Weapon.EXPLOSION || ctx.weapon == HitContext.Weapon.FALL) return 1f;
        Vec3 at = ctx.wound != null ? ctx.wound : entity.position().add(0, entity.getBbHeight() * 0.6, 0);
        if (SimpleBloodAPI.hasMetalBody(entity)) {
            if (clash(source, ctx)) {
                com.simpleblood.Tally.count("armor.metalbody");
                Vec3 back = ctx.direction != null ? ctx.direction.scale(-1) : new Vec3(0, 1, 0);
                if (cfg.combat.armorSparks) sparks(level, at, back, ctx.crit, ctx.damage, level.getRandom());
                if (cfg.combat.armorClank) BloodSounds.armorClank(level, at, ctx.weapon == HitContext.Weapon.ARROW, false);
            }
            return 1f;
        }
        double height = (at.y - entity.getY()) / Math.max(0.1, entity.getBbHeight());

        Metal metal = metalOf(entity.getItemBySlot(slotAt(height)));
        if (metal == null) return 1f;

        if (clash(source, ctx)) {
            com.simpleblood.Tally.count("armor.clash");
            Vec3 off = at;
            boolean projectile = ctx.weapon == HitContext.Weapon.ARROW;
            RandomSource rng = level.getRandom();
            Vec3 back = ctx.direction != null ? ctx.direction.scale(-1) : new Vec3(0, 1, 0);
            if (cfg.combat.armorSparks) sparks(level, off, back, ctx.crit, ctx.damage, rng);
            if (cfg.combat.armorShards) shards(level, off, back, metal, rng);
            if (cfg.combat.armorClank) BloodSounds.armorClank(level, off, projectile, metal == Metal.NETHERITE);
        }
        return Math.max(0, Math.min(100, cfg.combat.armorBleed)) / 100f;
    }

    private static EquipmentSlot slotAt(double height) {
        return height > 0.75 ? EquipmentSlot.HEAD : height > 0.45 ? EquipmentSlot.CHEST
                : height > 0.18 ? EquipmentSlot.LEGS : EquipmentSlot.FEET;
    }

    private static boolean clash(DamageSource source, HitContext ctx) {
        if (ctx.weapon == HitContext.Weapon.ARROW) return true;
        if (source == null) return false;
        Entity attacker = source.getEntity();
        return attacker instanceof LivingEntity holder && source.getDirectEntity() == attacker
                && metalOf(holder.getMainHandItem()) != null;
    }

    public static void strike(ClientLevel level, Vec3 at, Vec3 towards, boolean projectile) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.globalEnabled()) return;
        Vec3 back = towards == null || towards.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : towards.normalize();
        if (cfg.combat.armorSparks) sparks(level, at, back, false, 6f, level.getRandom());
        if (cfg.combat.armorClank) BloodSounds.armorClank(level, at, projectile, false);
    }

    private static void sparks(ClientLevel level, Vec3 at, Vec3 back, boolean crit, float damage, RandomSource rng) {
        if (level.getFluidState(BlockPos.containing(at)).is(FluidTags.WATER)) return;
        int count = 3 + rng.nextInt(2) + (crit ? 2 : 0) + (int) Math.min(2, damage / 6f);
        for (int i = 0; i < count; i++) {
            Vec3 dir = back.add((rng.nextDouble() - 0.5) * 1.6, 0.2 + rng.nextDouble() * 0.6, (rng.nextDouble() - 0.5) * 1.6).normalize();
            double speed = 0.08 + rng.nextDouble() * 0.1;
            ClientBloodParticleSpawner.emitPlain(level, BloodParticles.ARMOR_SPARK, at.x, at.y, at.z, dir.x * speed, dir.y * speed, dir.z * speed);
        }
    }

    private static void shards(ClientLevel level, Vec3 at, Vec3 back, Metal metal, RandomSource rng) {
        int count = 1 + rng.nextInt(2);
        BloodParticle.setCurrentBloodColor(metal.colour);
        BloodParticle.setCurrentKind(BloodKind.METAL);
        BloodParticle.setGlowing(false);
        BloodParticle.setPaintsSurfaces(false);
        BloodParticle.setShouldTransformToFog(false);
        BloodParticle.setEntitySizeMultiplier(0.45f);
        BloodParticle.setBallistic(true, 0.7f);
        try {
            for (int i = 0; i < count; i++) {
                Vec3 dir = back.add((rng.nextDouble() - 0.5) * 1.2, 0.4 + rng.nextDouble() * 0.5, (rng.nextDouble() - 0.5) * 1.2).normalize();
                double speed = 0.06 + rng.nextDouble() * 0.06;
                ClientBloodParticleSpawner.emit(level, BloodParticles.BLOOD_DRIP, at.x, at.y, at.z, dir.x * speed, dir.y * speed, dir.z * speed);
            }
        } finally {
            BloodParticle.setBallistic(false, 1.0f);
            BloodParticle.setShouldTransformToFog(true);
            BloodParticle.setCurrentKind(BloodKind.LIQUID);
        }
    }

    static Metal metalOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Item item = stack.getItem();
        if (CACHE.containsKey(item)) return CACHE.get(item);
        Metal metal = SOFT_MARK;
        String fullId = BuiltInRegistries.ITEM.getKey(item).toString();
        Integer registered = SimpleBloodAPI.itemHardness(fullId);
        if (registered != null) {
            return registered < 0 ? null : Metal.ofRgb(registered);
        }
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        boolean soft = false;
        for (String s : SOFT) soft |= id.contains(s);
        if (!soft) {
            outer:
            for (int i = 0; i < METALS.length; i++) {
                for (String word : METALS[i]) {
                    if (id.contains(word)) {
                        metal = FOR[i];
                        break outer;
                    }
                }
            }
        }
        CACHE.put(item, metal);
        return metal;
    }
}
