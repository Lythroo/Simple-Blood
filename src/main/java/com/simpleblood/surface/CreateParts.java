package com.simpleblood.surface;

import com.simpleblood.SimpleBlood;
import com.simpleblood.compat.CreateKinetics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class CreateParts {
    private CreateParts() {}

    private static final double MAX_FACE = 1.0;

    record Face(Vec3 origin, Vec3 u, Vec3 v, double w, double h, Vec3 n, double[] tris, float[] uvs,
                boolean[] texels, int tw, int th, int pw, int ph) {
        double hit(Vec3 a, Vec3 b) {
            double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
            double denom = dx * n.x + dy * n.y + dz * n.z;
            if (denom >= -1.0e-9) return Double.NaN;
            double t = ((origin.x - a.x) * n.x + (origin.y - a.y) * n.y + (origin.z - a.z) * n.z) / denom;
            if (t < 0 || t > 1) return Double.NaN;
            return covers(a.x + dx * t, a.y + dy * t, a.z + dz * t) ? t : Double.NaN;
        }

        boolean covers(double x, double y, double z) {
            double px = x - origin.x, py = y - origin.y, pz = z - origin.z;
            return seen(px * u.x + py * u.y + pz * u.z, px * v.x + py * v.y + pz * v.z);
        }

        double off(double x, double y, double z) {
            return (x - origin.x) * n.x + (y - origin.y) * n.y + (z - origin.z) * n.z;
        }

        boolean[] mask(int pw, int ph) {
            boolean[] m = new boolean[pw * ph];
            for (int py = 0; py < ph; py++) {
                for (int px = 0; px < pw; px++) m[py * pw + px] = seen((px + 0.5) / pw * w, (py + 0.5) / ph * h);
            }
            return m;
        }

        boolean seen(double s, double t) {
            for (int i = 0, k = 0; i < tris.length; i += 6, k += 6) {
                double ax = tris[i], ay = tris[i + 1], bx = tris[i + 2], by = tris[i + 3], cx = tris[i + 4], cy = tris[i + 5];
                double det = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
                if (Math.abs(det) < 1.0e-12) continue;
                double l1 = ((by - cy) * (s - cx) + (cx - bx) * (t - cy)) / det;
                double l2 = ((cy - ay) * (s - cx) + (ax - cx) * (t - cy)) / det;
                double l3 = 1 - l1 - l2;
                if (l1 < -1.0e-6 || l2 < -1.0e-6 || l3 < -1.0e-6) continue;
                if (texels == null) return true;
                double tu = l1 * uvs[k] + l2 * uvs[k + 2] + l3 * uvs[k + 4];
                double tv = l1 * uvs[k + 1] + l2 * uvs[k + 3] + l3 * uvs[k + 5];
                int x = Math.max(0, Math.min(tw - 1, (int) Math.floor(tu * tw)));
                int y = Math.max(0, Math.min(th - 1, (int) Math.floor(tv * th)));
                return texels[y * tw + x];
            }
            return false;
        }
    }

    record Model(List<Face> faces, boolean partOnly, boolean multiBlock) {}

    private static final Model NONE = new Model(List.of(), false, false);
    private static final Map<BlockState, Model> CACHE = new ConcurrentHashMap<>();
    private static final Map<Block, Boolean> HELPERS = new ConcurrentHashMap<>();

    static Model of(BlockState state) {
        if (!CreateKinetics.present()) return null;
        Model m = CACHE.computeIfAbsent(state, CreateParts::build);
        return m == NONE ? null : m;
    }

    static void clear() {
        CACHE.clear();
    }

    static boolean isWheelHelper(BlockState state) {
        return CreateKinetics.present() && HELPERS.computeIfAbsent(state.getBlock(), b -> named(b, "WaterWheelStructuralBlock"));
    }

    private static Model build(BlockState state) {
        try {
            Block b = state.getBlock();
            if (named(b, "ShaftBlock") || named(b, "CogWheelBlock")) return model(BlockToneSampler.allQuads(state), IDENTITY, null, true, false);
            if (named(b, "EncasedFanBlock")) {
                return model(partial("ENCASED_FAN_INNER", state), toFace(facing(state).getOpposite()), null, false, false);
            }
            if (named(b, "EncasedShaftBlock") || named(b, "EncasedCogwheelBlock")) {
                Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
                BlockState shaft = createShaft(axis);
                if (shaft == null) return NONE;
                Direction top = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
                List<Direction> ends = new ArrayList<>(2);
                if (flag(state, "top_shaft", true)) ends.add(top);
                if (flag(state, "bottom_shaft", true)) ends.add(top.getOpposite());
                return ends.isEmpty() ? NONE : model(BlockToneSampler.allQuads(shaft), IDENTITY, ends, false, false);
            }
            if (named(b, "MillstoneBlock")) return model(partial("MILLSTONE_COG", state), IDENTITY, null, false, false);
            if (named(b, "DrillBlock")) return model(partial("DRILL_HEAD", state), toFace(facing(state)), null, false, false);
            if (named(b, "LargeWaterWheelBlock")) {
                Direction axis = Direction.fromAxisAndDirection(state.getValue(BlockStateProperties.AXIS), Direction.AxisDirection.POSITIVE);
                return model(wheel(state), toFaceVertical(axis), null, true, true);
            }
        } catch (Throwable t) {
            SimpleBlood.LOGGER.warn("Simple Blood: could not read the turning part of {} ({}); its blood holds still", state, t.toString());
        }
        return NONE;
    }

    private static boolean named(Block block, String simpleName) {
        for (Class<?> c = block.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getName().startsWith("com.simibubi.create.") && c.getSimpleName().equals(simpleName)) return true;
        }
        return false;
    }

    private static Direction facing(BlockState state) {
        return state.getValue(BlockStateProperties.FACING);
    }

    private static boolean flag(BlockState state, String name, boolean otherwise) {
        for (Property<?> p : state.getProperties()) {
            if (p.getName().equals(name) && state.getValue(p) instanceof Boolean v) return v;
        }
        return otherwise;
    }

    private static final ClassLoader LOADER = CreateParts.class.getClassLoader();

    private static List<BlockToneSampler.QuadInfo> partial(String name, BlockState state) throws ReflectiveOperationException {
        Object partial = Class.forName("com.simibubi.create.AllPartialModels", true, LOADER).getField(name).get(null);
        Object model = partial.getClass().getMethod("get").invoke(partial);
        return BlockToneSampler.bakedQuads(model, state);
    }

    private static List<BlockToneSampler.QuadInfo> wheel(BlockState state) throws ReflectiveOperationException {
        Class<?> variant = Class.forName("com.simibubi.create.content.kinetics.waterwheel.WaterWheelRenderer$Variant", true, LOADER);
        Object v = variant.getMethod("of", boolean.class, BlockState.class).invoke(null, true, state);
        return BlockToneSampler.bakedQuads(variant.getMethod("model").invoke(v), state);
    }

    private static BlockState createShaft(Direction.Axis axis) throws ReflectiveOperationException {
        Object entry = Class.forName("com.simibubi.create.AllBlocks", true, LOADER).getField("SHAFT").get(null);
        Object block = entry.getClass().getMethod("get").invoke(entry);
        return block instanceof Block b ? b.defaultBlockState().setValue(BlockStateProperties.AXIS, axis) : null;
    }

    private static final double[] IDENTITY = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    private static double[] toFace(Direction d) {
        return mul(rotY(horizontalAngle(d)), rotX(verticalAngle(d)));
    }

    private static double[] toFaceVertical(Direction d) {
        return mul(rotY(horizontalAngle(d)), rotX(verticalAngle(d) + 90));
    }

    private static double horizontalAngle(Direction d) {
        double a = d.toYRot();
        return d.getAxis() == Direction.Axis.X ? -a : a;
    }

    private static double verticalAngle(Direction d) {
        return d == Direction.UP ? -90 : d == Direction.DOWN ? 90 : 0;
    }

    private static double[] rotX(double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new double[]{1, 0, 0, 0, c, -s, 0, s, c};
    }

    private static double[] rotY(double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new double[]{c, 0, s, 0, 1, 0, -s, 0, c};
    }

    private static double[] mul(double[] a, double[] b) {
        double[] r = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
        }
        return r;
    }

    private static Vec3 turn(double[] m, Vec3 p) {
        double x = p.x - 0.5, y = p.y - 0.5, z = p.z - 0.5;
        return new Vec3(m[0] * x + m[1] * y + m[2] * z + 0.5, m[3] * x + m[4] * y + m[5] * z + 0.5, m[6] * x + m[7] * y + m[8] * z + 0.5);
    }

    private record Tri(Vec3 a, Vec3 b, Vec3 c, float[] uv, TextureAtlasSprite sprite, Vec3 n) {}

    private static Model model(List<BlockToneSampler.QuadInfo> quads, double[] orient, List<Direction> onlyEnds,
                               boolean partOnly, boolean multiBlock) {
        List<Tri> tris = new ArrayList<>();
        for (BlockToneSampler.QuadInfo q : quads) {
            Vec3[] c = new Vec3[4];
            for (int i = 0; i < 4; i++) c[i] = turn(orient, q.corners()[i]);
            addTri(tris, c, q, 0, 1, 2);
            addTri(tris, c, q, 0, 2, 3);
        }
        if (onlyEnds != null) tris.removeIf(t -> !atEnd(t, onlyEnds));
        if (tris.isEmpty()) return NONE;
        List<Face> faces = join(tris);
        return faces.isEmpty() ? NONE : new Model(List.copyOf(faces), partOnly, multiBlock);
    }

    private static void addTri(List<Tri> out, Vec3[] c, BlockToneSampler.QuadInfo q, int i, int j, int k) {
        Vec3 n = c[j].subtract(c[i]).cross(c[k].subtract(c[i]));
        if (n.lengthSqr() < 1.0e-12) return;
        float[] uv = {q.u()[i], q.v()[i], q.u()[j], q.v()[j], q.u()[k], q.v()[k]};
        double uvArea = Math.abs((uv[2] - uv[0]) * (uv[5] - uv[1]) - (uv[4] - uv[0]) * (uv[3] - uv[1]));
        if (uvArea < 1.0e-9) return;
        out.add(new Tri(c[i], c[j], c[k], uv, q.sprite(), n.normalize()));
    }

    private static boolean atEnd(Tri t, List<Direction> ends) {
        for (Direction d : ends) {
            Vec3 dn = new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
            if (t.n().dot(dn) < 0.99) continue;
            double plane = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
            double at = d.getAxis().choose(t.a().x, t.a().y, t.a().z);
            if (Math.abs(at - plane) < 1.0e-3) return true;
        }
        return false;
    }

    private record Mapping(Vec3 p0, double u0, double v0, Vec3 tu, Vec3 tv) {
        static Mapping of(Tri t) {
            Vec3 e1 = t.b().subtract(t.a()), e2 = t.c().subtract(t.a());
            float[] uv = t.uv();
            double du1 = uv[2] - uv[0], dv1 = uv[3] - uv[1], du2 = uv[4] - uv[0], dv2 = uv[5] - uv[1];
            double det = du1 * dv2 - du2 * dv1;
            if (Math.abs(det) < 1.0e-12) return null;
            Vec3 tu = e1.scale(dv2 / det).subtract(e2.scale(dv1 / det));
            Vec3 tv = e2.scale(du1 / det).subtract(e1.scale(du2 / det));
            double lu = tu.length(), lv = tv.length();
            if (lu < 1.0e-9 || lv < 1.0e-9 || Math.abs(tu.dot(tv)) > 0.02 * lu * lv) return null;
            return new Mapping(t.a(), uv[0], uv[1], tu, tv);
        }

        double[] uvAt(Vec3 p) {
            Vec3 d = p.subtract(p0);
            return new double[]{u0 + d.dot(tu) / tu.lengthSqr(), v0 + d.dot(tv) / tv.lengthSqr()};
        }

        boolean fits(Tri t) {
            float[] uv = t.uv();
            Vec3[] ps = {t.a(), t.b(), t.c()};
            for (int i = 0; i < 3; i++) {
                double[] at = uvAt(ps[i]);
                if (Math.abs(at[0] - uv[i * 2]) > 1.0e-3 || Math.abs(at[1] - uv[i * 2 + 1]) > 1.0e-3) return false;
            }
            return true;
        }
    }

    private static List<Face> join(List<Tri> tris) {
        Map<TextureAtlasSprite, boolean[]> texels = new IdentityHashMap<>();
        Map<TextureAtlasSprite, int[]> sizes = new IdentityHashMap<>();
        Map<String, List<List<Tri>>> byPlane = new HashMap<>();
        Map<List<Tri>, Mapping> mappings = new IdentityHashMap<>();
        List<List<Tri>> groups = new ArrayList<>();
        for (Tri t : tris) {
            String plane = Math.round(t.n().x * 500) + "," + Math.round(t.n().y * 500) + "," + Math.round(t.n().z * 500) + ","
                    + Math.round(t.n().dot(t.a()) * 1000) + "," + System.identityHashCode(t.sprite());
            List<List<Tri>> candidates = byPlane.computeIfAbsent(plane, k -> new ArrayList<>());
            Mapping own = Mapping.of(t);
            List<Tri> home = null;
            for (List<Tri> g : candidates) {
                Mapping m = mappings.get(g);
                boolean sameGrid = m == null ? own == null : own != null && m.fits(t);
                if (sameGrid && touches(g, t) && small(g, t)) { home = g; break; }
            }
            if (home == null) {
                home = new ArrayList<>();
                candidates.add(home);
                groups.add(home);
                if (own != null) mappings.put(home, own);
            }
            home.add(t);
        }
        List<Face> faces = new ArrayList<>(groups.size());
        for (List<Tri> g : groups) {
            Tri first = g.get(0);
            TextureAtlasSprite sprite = first.sprite();
            int[] size = sizes.computeIfAbsent(sprite, k -> new int[2]);
            boolean[] seen = texels.computeIfAbsent(sprite, k -> BlockToneSampler.opaqueTexels(k, size));
            int[] spriteSize = BlockToneSampler.spriteSize(sprite);
            int tw = Math.max(1, size[0] > 0 ? size[0] : spriteSize[0]), th = Math.max(1, size[1] > 0 ? size[1] : spriteSize[1]);
            Vec3 n = first.n();
            Mapping m = mappings.get(g);
            Vec3 origin, u, v;
            double w, h;
            int pw, ph;
            if (m != null) {
                double umin = Double.MAX_VALUE, umax = -Double.MAX_VALUE, vmin = Double.MAX_VALUE, vmax = -Double.MAX_VALUE;
                for (Tri t : g) {
                    for (int i = 0; i < 3; i++) {
                        umin = Math.min(umin, t.uv()[i * 2]); umax = Math.max(umax, t.uv()[i * 2]);
                        vmin = Math.min(vmin, t.uv()[i * 2 + 1]); vmax = Math.max(vmax, t.uv()[i * 2 + 1]);
                    }
                }
                int tu0 = (int) Math.floor(umin * tw + 1.0e-4), tu1 = (int) Math.ceil(umax * tw - 1.0e-4);
                int tv0 = (int) Math.floor(vmin * th + 1.0e-4), tv1 = (int) Math.ceil(vmax * th - 1.0e-4);
                if (tu1 <= tu0 || tv1 <= tv0) continue;
                u = m.tu().normalize();
                v = m.tv().normalize();
                boolean flipV = u.cross(v).dot(n) < 0;
                if (flipV) v = v.scale(-1);
                double vStart = (flipV ? tv1 : tv0) / (double) th;
                origin = m.p0().add(m.tu().scale(tu0 / (double) tw - m.u0())).add(m.tv().scale(vStart - m.v0()));
                w = (tu1 - tu0) * m.tu().length() / tw;
                h = (tv1 - tv0) * m.tv().length() / th;
                pw = tu1 - tu0;
                ph = tv1 - tv0;
            } else {
                u = first.b().subtract(first.a()).normalize();
                v = n.cross(u);
                double s0 = Double.MAX_VALUE, s1 = -Double.MAX_VALUE, q0 = Double.MAX_VALUE, q1 = -Double.MAX_VALUE;
                for (Tri t : g) {
                    for (Vec3 p : new Vec3[]{t.a(), t.b(), t.c()}) {
                        Vec3 d = p.subtract(first.a());
                        s0 = Math.min(s0, d.dot(u)); s1 = Math.max(s1, d.dot(u));
                        q0 = Math.min(q0, d.dot(v)); q1 = Math.max(q1, d.dot(v));
                    }
                }
                w = s1 - s0;
                h = q1 - q0;
                origin = first.a().add(u.scale(s0)).add(v.scale(q0));
                pw = (int) Math.round(w * 16);
                ph = (int) Math.round(h * 16);
            }
            if (w < 1.0e-3 || h < 1.0e-3 || pw < 1 || ph < 1) continue;
            double[] flat = new double[g.size() * 6];
            float[] uvs = new float[g.size() * 6];
            int j = 0;
            for (Tri t : g) {
                for (Vec3 p : new Vec3[]{t.a(), t.b(), t.c()}) {
                    Vec3 d = p.subtract(origin);
                    flat[j] = d.dot(u);
                    flat[j + 1] = d.dot(v);
                    j += 2;
                }
                System.arraycopy(t.uv(), 0, uvs, j - 6, 6);
            }
            faces.add(new Face(origin, u, v, w, h, n, flat, uvs, seen, tw, th, pw, ph));
        }
        return faces;
    }

    private static boolean touches(List<Tri> g, Tri t) {
        for (Tri o : g) {
            for (Vec3 p : new Vec3[]{o.a(), o.b(), o.c()}) {
                if (p.distanceToSqr(t.a()) < 1.0e-8 || p.distanceToSqr(t.b()) < 1.0e-8 || p.distanceToSqr(t.c()) < 1.0e-8) return true;
            }
        }
        return false;
    }

    private static boolean small(List<Tri> g, Tri t) {
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        List<Tri> all = new ArrayList<>(g);
        all.add(t);
        for (Tri o : all) {
            for (Vec3 p : new Vec3[]{o.a(), o.b(), o.c()}) {
                x0 = Math.min(x0, p.x); y0 = Math.min(y0, p.y); z0 = Math.min(z0, p.z);
                x1 = Math.max(x1, p.x); y1 = Math.max(y1, p.y); z1 = Math.max(z1, p.z);
            }
        }
        return x1 - x0 <= MAX_FACE + 1.0e-6 && y1 - y0 <= MAX_FACE + 1.0e-6 && z1 - z0 <= MAX_FACE + 1.0e-6;
    }
}
