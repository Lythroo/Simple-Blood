package com.simpleblood.mob;

import com.simpleblood.BloodColor;
import com.simpleblood.BloodKind;
import com.simpleblood.HitContext;
import com.simpleblood.SimpleBlood;
import com.simpleblood.SimpleBloodClient;
import com.simpleblood.SimpleBloodConfig;
import com.simpleblood.particle.BloodParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class BulletWounds {

    private BulletWounds() {}

    public static final float DROPS = 0.4f;
    public static final float JET = 1.3f;
    public static final float DROP_SIZE = 0.6f;

    private static final int PUMP_TICKS = 60;
    private static final int MAX_WOUNDS = 32;
    private static final long MS_PER_TICK = 50;
    private static final long PIN_SPARE_MS = 1_000;
    private static final int FULL_PERCENT = 100;

    private record Wound(int mob, WoundAnchors.Pin pin, PumpingWound pumping) {}

    private static final List<Wound> WOUNDS = new ArrayList<>();
    private static ClientLevel lastLevel;

    private static boolean enabled() {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        return cfg != null && cfg.globalEnabled() && cfg.hitBurstEnabled();
    }

    public static void hit(LivingEntity mob, HitContext ctx) {
        if (ctx.weapon != HitContext.Weapon.ARROW || ctx.wound == null || !enabled()) return;
        if (!(mob.level() instanceof ClientLevel level) || WOUNDS.size() >= MAX_WOUNDS) return;
        if (SimpleBlood.bloodKindOf(mob) != BloodKind.LIQUID || !SimpleBlood.hasFleshBlood(mob)) return;
        WoundAnchors.Pin pin = WoundAnchors.pin(mob.getId(), ctx.wound, PUMP_TICKS * MS_PER_TICK + PIN_SPARE_MS);
        if (pin == null) return;
        WOUNDS.add(new Wound(mob.getId(), pin, new PumpingWound(level.getGameTime())));
        com.simpleblood.Tally.count("living.shot");
    }

    public static void tick(ClientLevel level) {
        if (level != lastLevel) {
            lastLevel = level;
            WOUNDS.clear();
        }
        if (WOUNDS.isEmpty()) return;
        if (!enabled()) {
            clear();
            return;
        }
        long now = level.getGameTime();
        int percent = Math.round(FULL_PERCENT * DROPS * SimpleBloodClient.getConfig().hitBurst.getBurstIntensityMultiplier());
        Iterator<Wound> it = WOUNDS.iterator();
        while (it.hasNext()) {
            Wound w = it.next();
            long age = now - w.pumping().since();
            if (age > PUMP_TICKS || !(level.getEntity(w.mob()) instanceof LivingEntity mob) || !mob.isAlive()) {
                WoundAnchors.unpin(w.mob(), w.pin());
                it.remove();
                continue;
            }
            Vec3 at = w.pin().at(), facing = w.pin().facing();
            if (at == null || facing == null) continue;
            prepare(mob);
            try {
                w.pumping().pump(level, at, facing, Vec3.ZERO, 1f - age / (float) PUMP_TICKS, now, percent, JET);
                com.simpleblood.Tally.count("living.shot.pump");
            } finally {
                BloodParticle.setBallistic(false, 1.0f);
            }
        }
    }

    private static void prepare(LivingEntity mob) {
        BloodParticle.setCurrentBloodColor(BloodColor.getBloodColor(mob));
        BloodParticle.setCurrentKind(SimpleBlood.bloodKindOf(mob));
        BloodParticle.setStreaks(true);
        BloodParticle.setGlowing(SimpleBlood.bloodGlows(mob));
        boolean stains = SimpleBlood.shouldEntityTransformToStains(mob);
        BloodParticle.setPaintsSurfaces(stains);
        BloodParticle.setShouldTransformToFog(stains);
        BloodParticle.setShouldDespawnInWater(false);
        BloodParticle.setEntitySizeMultiplier(SimpleBlood.getParticleSizeMultiplier(mob) * DROP_SIZE);
        BloodParticle.setBallistic(true, 1.0f);
    }

    public static void clear() {
        for (Wound w : WOUNDS) WoundAnchors.unpin(w.mob(), w.pin());
        WOUNDS.clear();
    }

    public static Vec3 atForTests(int mobId) {
        for (int i = WOUNDS.size() - 1; i >= 0; i--) {
            if (WOUNDS.get(i).mob() == mobId) return WOUNDS.get(i).pin().at();
        }
        return null;
    }
}
