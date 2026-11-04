package com.simpleblood.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simpleblood.Platform;
import com.simpleblood.SimpleBlood;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class CreateContraptions {
    private CreateContraptions() {}

    private static boolean looked;
    private static Class<?> entityClass;
    private static Method getContraption, getBlocks, applyLocalTransforms, blockEntityClientSide, transformApply, transformTurn;
    private static final Map<Class<?>, Method> MAKE_TRANSFORM = new HashMap<>();

    public static boolean present() {
        if (!looked) lookUp();
        return entityClass != null;
    }

    private static synchronized void lookUp() {
        if (looked) return;
        if (Platform.modIds().contains("create")) {
            try {
                ClassLoader cl = CreateContraptions.class.getClassLoader();
                Class<?> e = Class.forName("com.simibubi.create.content.contraptions.AbstractContraptionEntity", false, cl);
                Class<?> contraption = Class.forName("com.simibubi.create.content.contraptions.Contraption", false, cl);
                Class<?> transform = Class.forName("com.simibubi.create.content.contraptions.StructureTransform", false, cl);
                getContraption = e.getMethod("getContraption");
                applyLocalTransforms = e.getMethod("applyLocalTransforms", PoseStack.class, float.class);
                getBlocks = contraption.getMethod("getBlocks");
                blockEntityClientSide = contraption.getMethod("getBlockEntityClientSide", BlockPos.class);
                transformApply = transform.getMethod("apply", Vec3.class);
                transformTurn = transform.getMethod("applyWithoutOffsetUncentered", Vec3.class);
                entityClass = e;
                SimpleBlood.LOGGER.info("Create found: blood rides along on its contraptions");
            } catch (Throwable t) {
                entityClass = null;
                SimpleBlood.LOGGER.warn("Create found, but not a version Simple Blood knows; blood on its contraptions drips off ({})", t.toString());
            }
        }
        looked = true;
    }

    public static boolean is(Entity e) {
        return present() && entityClass.isInstance(e);
    }

    public static Map<BlockPos, BlockState> blocks(Entity e) {
        try {
            Object c = getContraption.invoke(e);
            if (c == null || !(getBlocks.invoke(c) instanceof Map<?, ?> blocks) || blocks.isEmpty()) return null;
            Map<BlockPos, BlockState> out = new HashMap<>(blocks.size() * 2);
            for (Map.Entry<?, ?> b : blocks.entrySet()) {
                if (b.getKey() instanceof BlockPos p && b.getValue() instanceof StructureTemplate.StructureBlockInfo info) {
                    out.put(p.immutable(), info.state());
                }
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object blockEntity(Entity e, BlockPos local) {
        try {
            Object c = getContraption.invoke(e);
            return c == null ? null : blockEntityClientSide.invoke(c, local);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean pose(Entity e, float partialTick, BlockPos plotBase, RigidPose out) {
        try {
            PoseStack ps = new PoseStack();
            applyLocalTransforms.invoke(e, ps, partialTick);
            org.joml.Matrix4f m = ps.last().pose();
            Vec3 at = e.getPosition(partialTick);
            ROWS[0] = m.m00(); ROWS[1] = m.m10(); ROWS[2] = m.m20();
            ROWS[3] = m.m01(); ROWS[4] = m.m11(); ROWS[5] = m.m21();
            ROWS[6] = m.m02(); ROWS[7] = m.m12(); ROWS[8] = m.m22();
            out.set(ROWS, at.x + m.m30(), at.y + m.m31(), at.z + m.m32(), plotBase.getX(), plotBase.getY(), plotBase.getZ());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static final double[] ROWS = new double[9];

    public record PutBack(Object transform) {
        public Vec3 point(Vec3 local) {
            try {
                return (Vec3) transformApply.invoke(transform, local);
            } catch (Throwable t) {
                return null;
            }
        }

        public Vec3 turn(Vec3 dir) {
            try {
                return (Vec3) transformTurn.invoke(transform, dir);
            } catch (Throwable t) {
                return null;
            }
        }
    }

    public static PutBack putBack(Entity e) {
        try {
            Method m = MAKE_TRANSFORM.computeIfAbsent(e.getClass(), CreateContraptions::findMakeTransform);
            Object t = m == null ? null : m.invoke(e);
            return t == null ? null : new PutBack(t);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method findMakeTransform(Class<?> c) {
        for (; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod("makeStructureTransform");
                if (java.lang.reflect.Modifier.isAbstract(m.getModifiers())) continue;
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
}
