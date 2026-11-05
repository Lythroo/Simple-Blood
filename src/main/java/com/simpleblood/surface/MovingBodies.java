package com.simpleblood.surface;

import com.simpleblood.SimpleBlood;
import com.simpleblood.SimpleBloodAPI;
import com.simpleblood.SimpleBloodAPI.MovingStructures;
import com.simpleblood.SimpleBloodAPI.MovingStructures.Pose;
import com.simpleblood.compat.SableSubLevels;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

public final class MovingBodies {
    private MovingBodies() {}

    static final class Source {
        final String name;
        final MovingStructures structures;
        final boolean external;
        final String hitCounter;
        private volatile boolean off;

        Source(String name, MovingStructures structures, boolean external) {
            this.name = name;
            this.structures = structures;
            this.external = external;
            this.hitCounter = name + ".hit";
        }

        List<? extends Pose> crossing(ClientLevel level, Vec3 from, Vec3 to) {
            List<? extends Pose> found = crossingAsGiven(level, from, to);
            if (!external || found.isEmpty()) return found;
            Pose[] guarded = new Pose[found.size()];
            for (int i = 0; i < guarded.length; i++) guarded[i] = guard(found.get(i));
            return List.of(guarded);
        }

        boolean crosses(ClientLevel level, Vec3 from, Vec3 to) {
            return !crossingAsGiven(level, from, to).isEmpty();
        }

        private List<? extends Pose> crossingAsGiven(ClientLevel level, Vec3 from, Vec3 to) {
            if (off) return List.of();
            try {
                List<? extends Pose> found = structures.crossing(level, from, to);
                return found == null ? List.of() : found;
            } catch (Throwable t) {
                fail(t);
                return List.of();
            }
        }

        Pose drawn(ClientLevel level, BlockPos pos, float partialTick) {
            if (off) return null;
            try {
                return guard(structures.drawn(level, pos, partialTick));
            } catch (Throwable t) {
                fail(t);
                return null;
            }
        }

        Pose logical(ClientLevel level, BlockPos pos) {
            if (off) return null;
            try {
                return guard(structures.logical(level, pos));
            } catch (Throwable t) {
                fail(t);
                return null;
            }
        }

        private Pose guard(Pose p) {
            return external && p != null ? new GuardedPose(this, p) : p;
        }

        void fail(Throwable t) {
            if (off) return;
            off = true;
            if (external) unregister(name, this);
            SimpleBlood.LOGGER.warn("Moving structures '{}' switched off after an error: blood no longer rides on them", name, t);
        }
    }

    private record GuardedPose(Source source, Pose pose) implements Pose {
        @Override
        public Vec3 toWorld(Vec3 plot) {
            try {
                return pose.toWorld(plot);
            } catch (Throwable t) {
                source.fail(t);
                return plot;
            }
        }

        @Override
        public Vec3 toPlot(Vec3 world) {
            try {
                return pose.toPlot(world);
            } catch (Throwable t) {
                source.fail(t);
                return world;
            }
        }

        @Override
        public Vec3 turn(Vec3 direction) {
            try {
                return pose.turn(direction);
            } catch (Throwable t) {
                source.fail(t);
                return direction;
            }
        }

        @Override
        public Vec3 lastToPlot(Vec3 world) {
            try {
                return pose.lastToPlot(world);
            } catch (Throwable t) {
                source.fail(t);
                return world;
            }
        }

        @Override
        public Vec3 lastToWorld(Vec3 plot) {
            try {
                return pose.lastToWorld(plot);
            } catch (Throwable t) {
                source.fail(t);
                return plot;
            }
        }
    }

    private static long frame = Long.MIN_VALUE;

    private static final Source CREATE = new Source("create", new MovingStructures() {
        @Override
        public List<? extends Pose> crossing(ClientLevel level, Vec3 from, Vec3 to) {
            return ContraptionRides.any() ? ContraptionRides.crossing(from, to) : List.of();
        }

        @Override
        public Pose drawn(ClientLevel level, BlockPos pos, float partialTick) {
            return ContraptionRides.isPlot(pos) ? ContraptionRides.drawn(pos, frame, partialTick) : null;
        }

        @Override
        public Pose logical(ClientLevel level, BlockPos pos) {
            return ContraptionRides.isPlot(pos) ? ContraptionRides.logical(pos) : null;
        }
    }, false);

    private static final Source SABLE = new Source("sable", new MovingStructures() {
        @Override
        public List<? extends Pose> crossing(ClientLevel level, Vec3 from, Vec3 to) {
            return SableSubLevels.present() ? SableSubLevels.crossing(level, from, to) : List.of();
        }

        @Override
        public Pose drawn(ClientLevel level, BlockPos pos, float partialTick) {
            return SableSubLevels.present() ? SableSubLevels.drawn(pos, frame, partialTick) : null;
        }

        @Override
        public Pose logical(ClientLevel level, BlockPos pos) {
            return SableSubLevels.present() ? SableSubLevels.logical(pos, level.getGameTime()) : null;
        }
    }, false);

    private static final Source[] BUILT_IN = {CREATE, SABLE};

    private static final Map<String, Source> EXTERNAL = new java.util.LinkedHashMap<>();
    private static volatile Source[] sources = BUILT_IN;

    public static void register(String name, MovingStructures structures) {
        synchronized (EXTERNAL) {
            EXTERNAL.put(name, new Source(name, structures, true));
            publish();
        }
    }

    public static void unregister(String name) {
        synchronized (EXTERNAL) {
            if (EXTERNAL.remove(name) != null) publish();
        }
    }

    private static void unregister(String name, Source source) {
        synchronized (EXTERNAL) {
            if (EXTERNAL.remove(name, source)) publish();
        }
    }

    private static void publish() {
        Source[] all = java.util.Arrays.copyOf(BUILT_IN, BUILT_IN.length + EXTERNAL.size());
        int n = BUILT_IN.length;
        for (Source s : EXTERNAL.values()) all[n++] = s;
        sources = all;
    }

    static Source[] sources() {
        return sources;
    }

    static boolean any() {
        return ContraptionRides.any() || SableSubLevels.present() || sources.length > BUILT_IN.length;
    }

    static boolean crossed(ClientLevel level, Vec3 from, Vec3 to) {
        if (!any()) return false;
        for (Source s : sources) if (s.crosses(level, from, to)) return true;
        return false;
    }

    static Pose drawn(ClientLevel level, BlockPos pos, long frame, float partialTick) {
        MovingBodies.frame = frame;
        for (Source s : sources) {
            Pose p = s.drawn(level, pos, partialTick);
            if (p != null) return p;
        }
        return null;
    }

    static Pose logical(ClientLevel level, BlockPos pos) {
        for (Source s : sources) {
            Pose p = s.logical(level, pos);
            if (p != null) return p;
        }
        return null;
    }
}
