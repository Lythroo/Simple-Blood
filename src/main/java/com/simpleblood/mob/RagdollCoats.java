//? if >=1.21.1 {
package com.simpleblood.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simpleblood.surface.SurfaceRenderer;
import net.minecraft.client.model.geom.ModelPart;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class RagdollCoats {
    private RagdollCoats() {}

    private static int drawing = -1;
    //? if >=1.21.11 {
    private static final List<ModelFacesOfMob.Face> faces = new ArrayList<>();
    private static final Set<ModelPart> walked = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final int[] cubes = {0};
    //?}

    public static boolean catching() {
        return drawing >= 0;
    }

    public static void begin(int mobId) {
        drawing = -1;
        com.simpleblood.Tally.count(mobId < 0 ? "ragdoll.drawn.unknown" : "ragdoll.drawn");
        if (mobId < 0 || !MobBlood.has(mobId) || com.simpleblood.compat.ShaderPacks.inShadowPass()) return;
        com.simpleblood.Tally.count("ragdoll.drawn.bloody");
        if (!com.simpleblood.Guard.ok(com.simpleblood.Guard.Part.SURFACES)) return;
        drawing = mobId;
        //? if >=1.21.11 {
        faces.clear();
        walked.clear();
        cubes[0] = 0;
        //?} else {
        /*ModelFacesOfMob.startCatching();
        *///?}
    }

    //? if >=1.21.11 {
    public static void rendering(ModelPart part, PoseStack stack) {
        if (drawing < 0 || walked.contains(part)) return;
        com.simpleblood.Tally.count("ragdoll.part");
        try {
            PoseStack copy = new PoseStack();
            copy.last().pose().set(stack.last().pose());
            copy.last().normal().set(stack.last().normal());
            ModelFacesOfMob.collectDrawn(part, copy, SurfaceRenderer.cameraPos(), faces, cubes, walked);
        } catch (Throwable t) {
            drawing = -1;
            com.simpleblood.Guard.fail(com.simpleblood.Guard.Part.SURFACES, "reading a ragdoll model", t);
        }
    }
    //?}

    public static void finish(Object buffers, int light) {
        int id = drawing;
        drawing = -1;
        //? if >=1.21.11 {
        if (id < 0 || faces.isEmpty()) return;
        List<ModelFacesOfMob.Face> drawn = new ArrayList<>(faces);
        faces.clear();
        walked.clear();
        //?} else {
        /*if (id < 0) return;
        List<ModelFacesOfMob.Face> drawn = ModelFacesOfMob.collectCaught(SurfaceRenderer.cameraPos());
        if (drawn.isEmpty()) return;
        *///?}
        com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on a ragdoll", () -> {
            VertexConsumer vc = bufferOf(buffers);
            if (vc != null) MobBlood.drawOnRagdoll(id, drawn, new PoseStack().last(), vc, light);
            else com.simpleblood.Tally.count("ragdoll.nobuffer");
        });
    }

    //? if >=1.21.11 {
    public static void beginEntity(Object state) {
        if (drawing >= 0 || !(state instanceof MobRenderState s)) return;
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null || s.simpleblood$entityId() < 0) return;
        net.minecraft.world.entity.Entity e = level.getEntity(s.simpleblood$entityId());
        if (e != null && e.getClass().getName().startsWith("de.sarocesch")) com.simpleblood.Tally.count("ragdoll.sarosEntity");
        int player = com.simpleblood.compat.OtherRagdolls.saroPlayerOf(e);
        if (player >= 0) {
            com.simpleblood.Tally.count("ragdoll.saros");
            begin(player);
            entityDrawn = drawing >= 0;
        }
    }

    private static boolean entityDrawn;

    public static void finishEntity(net.minecraft.client.renderer.SubmitNodeCollector collector, int light) {
        if (!entityDrawn) return;
        entityDrawn = false;
        int id = drawing;
        drawing = -1;
        if (id < 0 || faces.isEmpty()) return;
        List<ModelFacesOfMob.Face> drawn = new ArrayList<>(faces);
        faces.clear();
        walked.clear();
        collector.submitCustomGeometry(new PoseStack(),
                (net.minecraft.client.renderer.rendertype.RenderType) SurfaceRenderer.renderType(),
                (p, vc) -> com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on a ragdoll",
                        () -> MobBlood.drawOnRagdoll(id, drawn, p, vc, light)));
    }
    //?}

    private static final java.util.Map<Class<?>, Method> GET_BUFFER = new java.util.HashMap<>();

    private static VertexConsumer bufferOf(Object buffers) {
        if (buffers == null) return null;
        Class<?> type = buffers.getClass();
        if (!GET_BUFFER.containsKey(type)) {
            Method found = null;
            Class<?> lookIn = type;
            //? if <26.1 {
            /*            if (net.minecraft.client.renderer.MultiBufferSource.class.isAssignableFrom(type)) lookIn = net.minecraft.client.renderer.MultiBufferSource.class;
            *///?}
            for (Method m : lookIn.getMethods()) {
                if (m.getName().equals("getBuffer") && m.getParameterCount() == 1 && VertexConsumer.class.isAssignableFrom(m.getReturnType())) {
                    found = m;
                    break;
                }
            }
            GET_BUFFER.put(type, found);
        }
        Method getBuffer = GET_BUFFER.get(type);
        if (getBuffer == null) return null;
        try {
            return (VertexConsumer) getBuffer.invoke(buffers, SurfaceRenderer.renderType());
        } catch (ReflectiveOperationException | RuntimeException e) {
            GET_BUFFER.put(type, null);
            return null;
        }
    }

    private static final java.util.Map<Class<?>, java.util.function.ToIntFunction<Object>> MOB_OF = new java.util.HashMap<>();

    public static int mobOf(Object drawn) {
        if (drawn == null) return -1;
        java.util.function.ToIntFunction<Object> read = MOB_OF.computeIfAbsent(drawn.getClass(), RagdollCoats::reader);
        if (read == null) return -1;
        try {
            return read.applyAsInt(drawn);
        } catch (Throwable t) {
            MOB_OF.put(drawn.getClass(), o -> -1);
            return -1;
        }
    }

    private static java.util.function.ToIntFunction<Object> reader(Class<?> type) {
        for (String name : new String[]{"originalEntityId", "sourceEntityId"}) {
            try {
                Field f = type.getDeclaredField(name);
                if (f.getType() != int.class) continue;
                f.setAccessible(true);
                return o -> {
                    try {
                        return f.getInt(o);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                };
            } catch (NoSuchFieldException ignored) {
            }
        }
        try {
            Method m = type.getMethod("getId");
            return o -> {
                try {
                    return (Integer) m.invoke(o);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            };
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
//?}
