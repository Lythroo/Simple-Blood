package com.simpleblood.mob;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ModelShapes {
    private ModelShapes() {}

    private static final Map<EntityType<?>, Map<Long, Object>> GROWN = new HashMap<>();
    private static final Map<EntityType<?>, Map<Long, Object>> BABY = new HashMap<>();
    private static final Map<EntityType<?>, Map<Long, List<Object>>> GROWN_FORMS = new HashMap<>();
    private static final Map<EntityType<?>, Map<Long, List<Object>>> BABY_FORMS = new HashMap<>();

    static boolean wanted(int entityId) {
        if (!MobBlood.RAGDOLL_MODELS) return false;
        ClientLevel level = Minecraft.getInstance().level;
        Entity e = level == null ? null : level.getEntity(entityId);
        return e instanceof LivingEntity living && !mapOf(living.isBaby()).containsKey(e.getType());
    }

    static void note(int entityId, List<ModelFacesOfMob.Face> faces) {
        ClientLevel level = Minecraft.getInstance().level;
        if (!(level != null && level.getEntity(entityId) instanceof LivingEntity living)) return;
        Map<EntityType<?>, Map<Long, Object>> byType = mapOf(living.isBaby());
        if (byType.containsKey(living.getType())) return;
        Map<Long, Object> shapes = new HashMap<>();
        Map<Long, List<Object>> forms = new HashMap<>();
        for (ModelFacesOfMob.Face f : faces) {
            if (f.shape != 0) shapes.putIfAbsent(f.shape, f.key);
            if (f.form != 0) noteForm(forms, f.form, f.key);
            if (f.texForm != 0) noteForm(forms, f.texForm, f.key);
        }
        if (shapes.isEmpty()) return;
        byType.put(living.getType(), shapes);
        (living.isBaby() ? BABY_FORMS : GROWN_FORMS).put(living.getType(), forms);
        com.simpleblood.Tally.count("shapes.model");
    }

    static Map<Long, Object> of(EntityType<?> type, boolean baby) {
        Map<Long, Object> shapes = type == null ? null : mapOf(baby).get(type);
        return shapes == null ? Map.of() : shapes;
    }

    static Map<Long, List<Object>> formsOf(EntityType<?> type, boolean baby) {
        Map<Long, List<Object>> forms = type == null ? null : (baby ? BABY_FORMS : GROWN_FORMS).get(type);
        return forms == null ? Map.of() : forms;
    }

    static void noteForm(Map<Long, List<Object>> forms, long form, Object key) {
        List<Object> keys = forms.get(form);
        if (keys == null) {
            keys = new java.util.ArrayList<>(1);
            forms.put(form, keys);
        }
        for (Object k : keys) if (k == key) return;
        keys.add(key);
    }

    private static Map<EntityType<?>, Map<Long, Object>> mapOf(boolean baby) {
        return baby ? BABY : GROWN;
    }
}
