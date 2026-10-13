package com.simpleblood;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Everything the client can tell about one hit, computed once and read by every spawner:
 * how hard it was, where it came from, whether it was a critical, and what kind of weapon
 * did it. All of it is derived from data the client already has (the damage event packet,
 * the hurt animation, the attacker's held item) so it stays 100% client-side.
 */
public final class HitContext {

    /** Broad weapon families that change how blood leaves the body. */
    public enum Weapon { NONE, BLADE, AXE, MACE, ARROW, EXPLOSION, FALL, OTHER }

    public final LivingEntity entity;
    public final float damage;
    /** 0..1: mixes absolute damage (a 10-damage hit is big on anything) with the share of max health lost. */
    public final float severity;
    /** Unit vector the blow travelled along (attacker to victim), or null when unknown. */
    public final Vec3 direction;
    /** Where the blow came from, or null. */
    public final Vec3 sourcePos;
    public final boolean crit;
    public final Weapon weapon;
    /** The hit killed with more than twice the health that was left. */
    public final boolean overkill;
    /** Attack charge (0..1) of the swing, known for the local player's own hits; 1 otherwise. */
    public final float charge;
    /**
     * Where on the body the blow landed, in world space: the crosshair hit for your own
     * swings, the arrow's position for projectiles, the near side of the hitbox otherwise.
     * Null when the whole body is hurt at once (falls, explosions, unknown sources).
     */
    public final Vec3 wound;
    /**
     * A mace smash (the falling blow that throws block debris everywhere). Known for sure for
     * your own hits; for anyone else it is read off the damage they dealt, since a smash lands
     * at least the mace's 6 plus 4 per block of the 1.5 blocks it needs to fall.
     */
    public final boolean smash;
    private static final float SMASH_DAMAGE = 11.0f;

    // The local player's last swing: target id, charge, where the crosshair hit, time. Read
    // by of() when the damage packet for that target arrives a tick later.
    private static int lastChargeTarget = -1;
    private static float lastCharge = 1.0f;
    private static Vec3 lastHitPoint = null;
    private static boolean lastSmash = false;
    private static long lastChargeMs = 0;

    public static void rememberLocalCharge(int targetId, float charge) {
        rememberLocalCharge(targetId, charge, null, false);
    }

    /** @param smash the swing was a mace smash (falling from 1.5 blocks or more) */
    public static void rememberLocalCharge(int targetId, float charge, Vec3 hitPoint, boolean smash) {
        lastChargeTarget = targetId;
        lastCharge = charge;
        lastHitPoint = hitPoint;
        lastSmash = smash;
        lastChargeMs = System.currentTimeMillis();
    }

    private HitContext(LivingEntity entity, float damage, float severity, Vec3 direction, Vec3 sourcePos,
                       boolean crit, Weapon weapon, boolean overkill, float charge, Vec3 wound, boolean smash) {
        this.entity = entity;
        this.damage = damage;
        this.severity = severity;
        this.direction = direction;
        this.sourcePos = sourcePos;
        this.crit = crit;
        this.weapon = weapon;
        this.overkill = overkill;
        this.charge = charge;
        this.wound = wound;
        this.smash = smash;
    }

    /**
     * @param source   the damage source if a fresh one was captured, else null
     * @param crit     whether a critical-hit animation arrived for this entity just now
     * @param healthBefore the entity's health before the hit (for overkill detection)
     */
    public static HitContext of(LivingEntity entity, float damage, DamageSource source, boolean crit, float healthBefore) {
        float max = Math.max(1.0f, entity.getMaxHealth());
        float severity = Math.min(1.0f, 0.6f * Math.min(1.0f, damage / 20.0f) + 0.4f * Math.min(1.0f, damage / max));
        if (crit) severity = Math.min(1.0f, severity * 1.3f);

        Weapon weapon = weaponOf(source);
        Vec3 sourcePos = null;
        Vec3 direction = null;
        Vec3 center = entity.position().add(0, entity.getBbHeight() * 0.5, 0);

        if (source != null) {
            sourcePos = source.getSourcePosition();
            if (sourcePos == null && source.getDirectEntity() != null) {
                sourcePos = source.getDirectEntity().position();
            }
            if (sourcePos != null) {
                Vec3 d = center.subtract(sourcePos);
                // Projectiles fly nearly level; melee comes from eye height, so flatten a bit.
                d = new Vec3(d.x, d.y * 0.35, d.z);
                if (d.lengthSqr() > 0.01) direction = d.normalize();
            }
        }
        if (direction == null && entity.hurtTime > 0) {
            // Fallback: the hurt animation carries the angle the blow came from, relative to
            // the entity's own yaw. Convert it back into a world-space direction.
            double angle = Math.toRadians(entity.getYRot() + entity.getHurtDir());
            Vec3 towardAttacker = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            if (towardAttacker.lengthSqr() > 0.5) direction = towardAttacker.scale(-1).normalize();
        }
        if (weapon == Weapon.EXPLOSION || weapon == Weapon.FALL) {
            direction = null; // these spray everywhere / flat, not along a line
        }
        boolean overkill = damage >= healthBefore * 2.0f && damage >= healthBefore + 4.0f && healthBefore > 0;

        // Charge and the exact hit point: only known for the local player's own melee swing.
        float charge = 1.0f;
        Vec3 wound = null;
        boolean smash = false;
        Entity attacker = source != null ? source.getEntity() : null;
        Entity direct = source != null ? source.getDirectEntity() : null;
        boolean ownSwing = attacker instanceof Player p && p.isLocalPlayer() && direct == attacker;
        if (ownSwing && lastChargeTarget == entity.getId() && System.currentTimeMillis() - lastChargeMs <= 400) {
            charge = Math.max(0.0f, Math.min(1.0f, lastCharge));
            if (lastHitPoint != null) wound = clampToBody(entity, lastHitPoint);
            smash = weapon == Weapon.MACE && lastSmash;
        } else if (weapon == Weapon.MACE && !ownSwing) {
            smash = damage >= SMASH_DAMAGE; // someone else's mace: only the damage tells
        }
        if (wound == null && weapon != Weapon.EXPLOSION && weapon != Weapon.FALL) {
            if (direct != null && direct != attacker) {
                // A projectile: the client's copy sits right about where it struck.
                wound = clampToBody(entity, direct.position());
            } else if (direction != null) {
                // The near side of the body, at the height the blow came from.
                double hitY = center.y;
                if (attacker instanceof LivingEntity la) hitY = la.getEyeY() - 0.25;
                else if (sourcePos != null) hitY = sourcePos.y;
                Vec3 near = center.subtract(direction.scale(entity.getBbWidth() * 0.5));
                wound = clampToBody(entity, new Vec3(near.x, hitY, near.z));
            }
        }
        return new HitContext(entity, damage, severity, direction, sourcePos, crit, weapon, overkill, charge, wound, smash);
    }

    /** Pulls a point just inside the entity's hitbox (blood starts on the skin, not in the air). */
    private static Vec3 clampToBody(LivingEntity entity, Vec3 p) {
        double hw = entity.getBbWidth() * 0.5, h = entity.getBbHeight();
        double cx = entity.getX(), cz = entity.getZ(), y0 = entity.getY();
        double inset = Math.min(0.06, hw * 0.3);
        double x = Math.max(cx - hw + inset, Math.min(cx + hw - inset, p.x));
        double z = Math.max(cz - hw + inset, Math.min(cz + hw - inset, p.z));
        double y = Math.max(y0 + h * 0.12, Math.min(y0 + h * 0.92, p.y));
        return new Vec3(x, y, z);
    }

    private static Weapon weaponOf(DamageSource source) {
        if (source == null) return Weapon.NONE;
        if (source.is(DamageTypes.FALL)) return Weapon.FALL;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return Weapon.EXPLOSION;
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return Weapon.ARROW;

        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity living) {
            ItemStack held = living.getMainHandItem();
            if (!held.isEmpty()) {
                // Match by id so modded weapons and every game version behave alike.
                String id = BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
                if (id.endsWith("sword") || id.contains("blade") || id.contains("dagger") || id.contains("katana")) return Weapon.BLADE;
                if (id.endsWith("_axe") || id.equals("axe") || id.contains("battleaxe") || id.contains("cleaver")) return Weapon.AXE;
                if (id.contains("mace") || id.contains("hammer") || id.contains("club")) return Weapon.MACE;
            }
            return Weapon.OTHER;
        }
        return Weapon.OTHER;
    }

    /** Point on the near side of the hitbox where the blow enters (along -direction). */
    public Vec3 entryPoint() {
        return boundaryPoint(-1);
    }

    /** Point on the far side of the hitbox where blood exits (along +direction). */
    public Vec3 exitPoint() {
        return boundaryPoint(1);
    }

    private Vec3 boundaryPoint(int sign) {
        Vec3 center = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
        if (direction == null) return center;
        double hw = entity.getBbWidth() * 0.5, hh = entity.getBbHeight() * 0.5;
        double t = Double.MAX_VALUE;
        if (Math.abs(direction.x) > 1e-6) t = Math.min(t, hw / Math.abs(direction.x));
        if (Math.abs(direction.y) > 1e-6) t = Math.min(t, hh / Math.abs(direction.y));
        if (Math.abs(direction.z) > 1e-6) t = Math.min(t, hw / Math.abs(direction.z));
        if (t == Double.MAX_VALUE) return center;
        return center.add(direction.scale(sign * t));
    }
}
