package com.simpleblood.compat;

import com.simpleblood.Platform;
import com.simpleblood.SimpleBlood;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CreateKinetics {
    private CreateKinetics() {}

    private enum Kind {
        STILL,
        WHOLE,
        FACING_FACE,
        MESH
    }

    public static final double FLING_RPM = 64;

    public static final class Part {
        private final Object be;
        private final Kind kind;
        public final Vec3 axis;
        public final Vec3 centre;
        private final Direction facing;
        private final boolean fan;
        private long frame = Long.MIN_VALUE;
        private double angle;

        private Part(Object be, Kind kind, Vec3 axis, Vec3 centre, Direction facing) {
            this.be = be;
            this.kind = kind;
            this.axis = axis;
            this.centre = centre;
            this.facing = facing;
            this.fan = be.getClass().getSimpleName().equals("EncasedFanBlockEntity");
        }

        public boolean turns(Direction face, boolean custom) {
            return kind == Kind.WHOLE || (kind == Kind.FACING_FACE && !custom && face == facing);
        }

        public boolean alive(Object current) {
            return current == be && !((BlockEntity) be).isRemoved();
        }

        public double angle(long frame, float partialTick) {
            if (frame != this.frame) {
                this.frame = frame;
                angle = angleNow(partialTick);
            }
            return angle;
        }

        public double angleNow(float partialTick) {
            try {
                if (kind == Kind.FACING_FACE) {
                    return Math.toRadians((float) interpolatedAngle.invoke(be, partialTick - 1));
                }
                if (fan) {
                    float time = (float) renderTime.invoke(null, ((BlockEntity) be).getLevel());
                    return (time * fanSpeed() * 3 / 10 % 360) / 180 * Math.PI;
                }
                Object axisOf = rotationAxisOf.invoke(null, be);
                return (float) angleForBe.invoke(null, be, ((BlockEntity) be).getBlockPos(), axisOf);
            } catch (Throwable t) {
                return 0;
            }
        }

        public double rpm() {
            try {
                if (kind == Kind.FACING_FACE) return (float) angularSpeed.invoke(be) * 20 * 60 / 360.0;
                if (fan) return fanSpeed();
                return (float) speed.invoke(be);
            } catch (Throwable t) {
                return 0;
            }
        }

        private float fanSpeed() throws Throwable {
            float s = (float) speed.invoke(be) * 5;
            if (s > 0) return Math.max(80, Math.min(1280, s));
            if (s < 0) return Math.max(-1280, Math.min(-80, s));
            return 0;
        }
    }

    private static boolean looked;
    private static Class<?> kineticBe, bearingBe;
    private static Method speed, angleForBe, rotationAxisOf, interpolatedAngle, angularSpeed, renderTime, wheelMaster;
    private static final Map<Block, Kind> KINDS = new ConcurrentHashMap<>();

    public static boolean present() {
        if (!looked) lookUp();
        return angleForBe != null;
    }

    private static synchronized void lookUp() {
        if (looked) return;
        if (Platform.modIds().contains("create")) {
            try {
                ClassLoader cl = CreateKinetics.class.getClassLoader();
                kineticBe = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity", false, cl);
                Class<?> renderer = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer", false, cl);
                bearingBe = Class.forName("com.simibubi.create.content.contraptions.bearing.IBearingBlockEntity", false, cl);
                speed = kineticBe.getMethod("getSpeed");
                rotationAxisOf = renderer.getMethod("getRotationAxisOf", kineticBe);
                angleForBe = renderer.getMethod("getAngleForBe", kineticBe, net.minecraft.core.BlockPos.class, Direction.Axis.class);
                interpolatedAngle = bearingBe.getMethod("getInterpolatedAngle", float.class);
                angularSpeed = Class.forName("com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity", false, cl)
                        .getMethod("getAngularSpeed");
                renderTime = Class.forName("net.createmod.catnip.animation.AnimationTickHolder", false, cl)
                        .getMethod("getRenderTime", net.minecraft.world.level.LevelAccessor.class);
                wheelMaster = Class.forName("com.simibubi.create.content.kinetics.waterwheel.WaterWheelStructuralBlock", false, cl)
                        .getMethod("getMaster", net.minecraft.world.level.BlockGetter.class, net.minecraft.core.BlockPos.class, BlockState.class);
                SimpleBlood.LOGGER.info("Create found: blood on its shafts, cogs and bearings turns with them");
            } catch (Throwable t) {
                angleForBe = null;
                SimpleBlood.LOGGER.warn("Create found, but not a version Simple Blood knows; blood on its spinning parts holds still ({})", t.toString());
            }
        }
        looked = true;
    }

    private static final String[] WHOLE_BLOCKS = {
            "com.simibubi.create.content.kinetics.simpleRelays.AbstractShaftBlock",
            "com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlock",
            "com.simibubi.create.content.kinetics.gantry.GantryShaftBlock"};
    private static final String[] WHOLE_BLOCK_ENTITIES = {
            "dev.eriksonn.aeronautics.content.blocks.propeller.small.BasePropellerBlockEntity",
            "dev.simulated_team.simulated.content.blocks.auger_shaft.AugerShaftBlockEntity"};

    public static boolean spinsWhole(BlockState state) {
        if (!present()) return false;
        return KINDS.computeIfAbsent(state.getBlock(), b -> kindOfBlock(b.getClass())) == Kind.WHOLE;
    }

    public static net.minecraft.core.BlockPos wheelMaster(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos, BlockState state) {
        try {
            return present() ? (net.minecraft.core.BlockPos) wheelMaster.invoke(null, level, pos, state) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Kind kindOfBlock(Class<?> c) {
        return extendsAny(c, WHOLE_BLOCKS) ? Kind.WHOLE : Kind.STILL;
    }

    private static boolean extendsAny(Class<?> c, String[] names) {
        for (; c != null && c != Object.class; c = c.getSuperclass()) {
            for (String n : names) if (n.equals(c.getName())) return true;
        }
        return false;
    }

    public static Part part(BlockState state, Object be, Vec3 centre) {
        return part(state, be, centre, false);
    }

    public static Part part(BlockState state, Object be, Vec3 centre, boolean mesh) {
        if (!present() || be == null || !kineticBe.isInstance(be)) return null;
        try {
            Kind kind = mesh ? Kind.MESH : KINDS.computeIfAbsent(state.getBlock(), b -> kindOfBlock(b.getClass()));
            Direction facing = null;
            if (kind == Kind.STILL) {
                if (extendsAny(be.getClass(), WHOLE_BLOCK_ENTITIES)) {
                    kind = Kind.WHOLE;
                } else if (bearingBe.isInstance(be) && angularSpeed.getDeclaringClass().isInstance(be)
                        && state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                    kind = Kind.FACING_FACE;
                    facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
                } else {
                    return null;
                }
            }
            Direction.Axis a = facing != null ? facing.getAxis() : (Direction.Axis) rotationAxisOf.invoke(null, be);
            if (a == null) return null;
            Vec3 axis = new Vec3(a == Direction.Axis.X ? 1 : 0, a == Direction.Axis.Y ? 1 : 0, a == Direction.Axis.Z ? 1 : 0);
            return new Part(be, kind, axis, centre, facing);
        } catch (Throwable t) {
            return null;
        }
    }
}
