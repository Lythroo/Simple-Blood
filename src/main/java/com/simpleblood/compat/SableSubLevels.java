package com.simpleblood.compat;

import com.simpleblood.Platform;
import com.simpleblood.SimpleBlood;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SableSubLevels {
    private SableSubLevels() {}

    public record Pose(Object pose, Object last) implements BodyPose {
        public Pose(Object pose) {
            this(pose, null);
        }

        @Override
        public Vec3 toWorld(Vec3 plot) {
            try {
                return (Vec3) toWorld.invoke(pose, plot);
            } catch (Throwable t) {
                return plot;
            }
        }

        @Override
        public Vec3 toPlot(Vec3 world) {
            try {
                return (Vec3) toPlot.invoke(pose, world);
            } catch (Throwable t) {
                return world;
            }
        }

        @Override
        public Vec3 lastToPlot(Vec3 world) {
            if (last == null) return toPlot(world);
            try {
                return (Vec3) toPlot.invoke(last, world);
            } catch (Throwable t) {
                return toPlot(world);
            }
        }

        @Override
        public Vec3 lastToWorld(Vec3 plot) {
            if (last == null) return toWorld(plot);
            try {
                return (Vec3) toWorld.invoke(last, plot);
            } catch (Throwable t) {
                return toWorld(plot);
            }
        }

        @Override
        public Vec3 turn(Vec3 dir) {
            try {
                return (Vec3) turn.invoke(pose, dir);
            } catch (Throwable t) {
                return dir;
            }
        }
    }

    private static boolean looked;
    private static Object companion;
    private static Method getAllIntersecting, getContainingClient, renderPose, logicalPose, toWorld, toPlot, turn;
    private static Constructor<?> box, copyPose;

    public static boolean present() {
        if (!looked) lookUp();
        return companion != null;
    }

    private static synchronized void lookUp() {
        if (looked) return;
        if (Platform.modIds().contains("sable")) {
            try {
                ClassLoader cl = SableSubLevels.class.getClassLoader();
                Class<?> api = Class.forName("dev.ryanhcode.sable.companion.SableCompanion", false, cl);
                Class<?> boxC = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3dc", false, cl);
                Class<?> boxImpl = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3d", false, cl);
                Class<?> poseC = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc", false, cl);
                Class<?> access = Class.forName("dev.ryanhcode.sable.companion.SubLevelAccess", false, cl);
                Class<?> clientAccess = Class.forName("dev.ryanhcode.sable.companion.ClientSubLevelAccess", false, cl);
                companion = api.getField("INSTANCE").get(null);
                getAllIntersecting = api.getMethod("getAllIntersecting", Level.class, boxC);
                getContainingClient = api.getMethod("getContainingClient", int.class, int.class);
                renderPose = clientAccess.getMethod("renderPose", float.class);
                logicalPose = access.getMethod("logicalPose");
                copyPose = Class.forName("dev.ryanhcode.sable.companion.math.Pose3d", false, cl).getConstructor(poseC);
                toWorld = poseC.getMethod("transformPosition", Vec3.class);
                toPlot = poseC.getMethod("transformPositionInverse", Vec3.class);
                turn = poseC.getMethod("transformNormal", Vec3.class);
                box = boxImpl.getConstructor(double.class, double.class, double.class, double.class, double.class, double.class);
                SimpleBlood.LOGGER.info("Sable found: blood sticks to its sub-levels and moves with them");
            } catch (Throwable t) {
                companion = null;
                SimpleBlood.LOGGER.warn("Sable found, but not a version Simple Blood knows; its sub-levels take no blood ({})", t.toString());
            }
        }
        looked = true;
    }

    private static final double REACH = 1.5;

    public static List<Pose> crossing(Level level, Vec3 from, Vec3 to) {
        if (!present()) return List.of();
        try {
            Object b = box.newInstance(Math.min(from.x, to.x) - REACH, Math.min(from.y, to.y) - REACH, Math.min(from.z, to.z) - REACH,
                    Math.max(from.x, to.x) + REACH, Math.max(from.y, to.y) + REACH, Math.max(from.z, to.z) + REACH);
            if (!(getAllIntersecting.invoke(companion, level, b) instanceof Iterable<?> found)) return List.of();
            List<Pose> out = new ArrayList<>(2);
            long tick = level.getGameTime();
            for (Object subLevel : found) {
                Object pose = logicalPose.invoke(subLevel);
                if (pose != null) out.add(seen(subLevel, pose, tick));
            }
            return out;
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static final class Seen {
        long tick;
        Object now, before;
    }

    private static final Map<Object, Seen> SEEN = new java.util.WeakHashMap<>();

    private static synchronized Pose seen(Object subLevel, Object pose, long tick) throws ReflectiveOperationException {
        Seen s = SEEN.computeIfAbsent(subLevel, k -> new Seen());
        if (s.now == null || s.tick != tick) {
            Object copy = copyPose.newInstance(pose);
            s.before = s.now != null && s.tick == tick - 1 ? s.now : copy;
            s.now = copy;
            s.tick = tick;
        }
        return new Pose(s.now, s.before);
    }

    private static final Map<Long, Pose> FRAME = new HashMap<>();
    private static final Pose NONE = new Pose(null);
    private static long frameKey = Long.MIN_VALUE;

    public static Pose drawn(BlockPos pos, long frame, float partialTick) {
        if (!present()) return null;
        if (frame != frameKey) {
            FRAME.clear();
            frameKey = frame;
        }
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        Pose p = FRAME.computeIfAbsent(((long) cx << 32) ^ (cz & 0xFFFFFFFFL), k -> {
            try {
                Object subLevel = getContainingClient.invoke(companion, cx, cz);
                Object pose = subLevel == null ? null : renderPose.invoke(subLevel, partialTick);
                return pose == null ? NONE : new Pose(pose);
            } catch (Throwable t) {
                return NONE;
            }
        });
        return p == NONE ? null : p;
    }

    private static final Map<Long, Pose> TICK = new HashMap<>();
    private static long tickKey = Long.MIN_VALUE;

    public static Pose logical(BlockPos pos, long gameTime) {
        if (!present()) return null;
        if (gameTime != tickKey) {
            TICK.clear();
            tickKey = gameTime;
        }
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        Pose p = TICK.computeIfAbsent(((long) cx << 32) ^ (cz & 0xFFFFFFFFL), k -> {
            try {
                Object subLevel = getContainingClient.invoke(companion, cx, cz);
                Object pose = subLevel == null ? null : logicalPose.invoke(subLevel);
                return pose == null ? NONE : seen(subLevel, pose, gameTime);
            } catch (Throwable t) {
                return NONE;
            }
        });
        return p == NONE ? null : p;
    }
}
