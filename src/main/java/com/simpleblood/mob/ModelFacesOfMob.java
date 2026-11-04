package com.simpleblood.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simpleblood.mixin.ModelPartAccessor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModelFacesOfMob {
    private ModelFacesOfMob() {}

    private static final Set<String> SKIP = Set.of("hat_rim");

    static final class Face {
        Object key;
        long shape;
        final int cube;
        final float[] p = new float[12];
        final double[] w = new double[12];
        final float[] s = new float[4], t = new float[4];
        final float[] u = new float[4], v = new float[4];
        MobTextures.Alpha alpha;
        float nx, ny, nz;
        float wnx, wny, wnz;
        int pw, ph;
        boolean holds(double a, double b) {
            return a >= 0 && a <= 1 && b >= 0 && b <= 1;
        }

        Face(Object key, int cube) {
            this.key = key;
            this.cube = cube;
        }

        Face moved(double[] world, org.joml.Vector3f normal, Vec3 camera) {
            Face f = new Face(key, cube);
            f.shape = shape;
            f.pw = pw;
            f.ph = ph;
            f.alpha = alpha;
            System.arraycopy(world, 0, f.w, 0, 12);
            for (int k = 0; k < 4; k++) {
                f.p[k * 3] = (float) (world[k * 3] - camera.x);
                f.p[k * 3 + 1] = (float) (world[k * 3 + 1] - camera.y);
                f.p[k * 3 + 2] = (float) (world[k * 3 + 2] - camera.z);
                f.s[k] = s[k];
                f.t[k] = t[k];
                f.u[k] = u[k];
                f.v[k] = v[k];
            }
            f.nx = f.wnx = normal.x;
            f.ny = f.wny = normal.y;
            f.nz = f.wnz = normal.z;
            return f;
        }

        double wx(int k) { return w[k * 3]; }
        double wy(int k) { return w[k * 3 + 1]; }
        double wz(int k) { return w[k * 3 + 2]; }

        Vec3 at(double fx, double fy) {
            double[] ab = edgeWeights(fx, fy);
            double a = ab[0], b = ab[1];
            return new Vec3(wx(0) + (wx(1) - wx(0)) * a + (wx(3) - wx(0)) * b,
                    wy(0) + (wy(1) - wy(0)) * a + (wy(3) - wy(0)) * b,
                    wz(0) + (wz(1) - wz(0)) * a + (wz(3) - wz(0)) * b);
        }

        double[] edgeWeights(double fx, double fy) {
            double s1 = s[1] - s[0], s3 = s[3] - s[0], t1 = t[1] - t[0], t3 = t[3] - t[0];
            double det = s1 * t3 - s3 * t1;
            if (Math.abs(det) < 1e-9) return new double[]{0, 0};
            double dx = fx - s[0], dy = fy - t[0];
            return new double[]{(dx * t3 - s3 * dy) / det, (s1 * dy - dx * t1) / det};
        }

        boolean drawnAt(double a, double b) {
            if (alpha == null) return true;
            return alpha.at(u[0] + (u[1] - u[0]) * a + (u[3] - u[0]) * b, v[0] + (v[1] - v[0]) * a + (v[3] - v[0]) * b);
        }

        double sAt(double a, double b) { return s[0] + (s[1] - s[0]) * a + (s[3] - s[0]) * b; }
        double tAt(double a, double b) { return t[0] + (t[1] - t[0]) * a + (t[3] - t[0]) * b; }

        double[] locate(double x, double y, double z) {
            double e1x = wx(1) - wx(0), e1y = wy(1) - wy(0), e1z = wz(1) - wz(0);
            double e3x = wx(3) - wx(0), e3y = wy(3) - wy(0), e3z = wz(3) - wz(0);
            double qx = x - wx(0), qy = y - wy(0), qz = z - wz(0);
            double a11 = e1x * e1x + e1y * e1y + e1z * e1z, a13 = e1x * e3x + e1y * e3y + e1z * e3z, a33 = e3x * e3x + e3y * e3y + e3z * e3z;
            double det = a11 * a33 - a13 * a13;
            if (det < 1e-12) return null;
            double b1 = qx * e1x + qy * e1y + qz * e1z, b3 = qx * e3x + qy * e3y + qz * e3z;
            double a = (b1 * a33 - b3 * a13) / det, b = (a11 * b3 - a13 * b1) / det;
            double off = qx * wnx + qy * wny + qz * wnz;
            return new double[]{a, b, off};
        }
    }

    private static void addPolygon(Object key, int cube, float[] v, float mnx, float mny, float mnz,
                                   Matrix4f m, Matrix3f nm, Quaternionf toWorld, Vec3 camera, List<Face> out) {
        float umin = Float.MAX_VALUE, umax = -Float.MAX_VALUE, vmin = Float.MAX_VALUE, vmax = -Float.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            umin = Math.min(umin, v[k * 5 + 3]);
            umax = Math.max(umax, v[k * 5 + 3]);
            vmin = Math.min(vmin, v[k * 5 + 4]);
            vmax = Math.max(vmax, v[k * 5 + 4]);
        }
        if (umax - umin < 1e-6f || vmax - vmin < 1e-6f) return;
        float lenU = 0, lenV = 0;
        for (int k = 0; k < 4; k++) {
            int a = k * 5, b = ((k + 1) % 4) * 5;
            float dx = v[a] - v[b], dy = v[a + 1] - v[b + 1], dz = v[a + 2] - v[b + 2];
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (Math.abs(v[a + 3] - v[b + 3]) > Math.abs(v[a + 4] - v[b + 4])) lenU = Math.max(lenU, len);
            else lenV = Math.max(lenV, len);
        }
        if (lenU < 0.5f || lenV < 0.5f) return;
        Vector3f n = nm.transform(new Vector3f(mnx, mny, mnz));
        if (n.lengthSquared() < 1e-8f) return;
        n.normalize();
        Face f = new Face(key, cube);
        f.pw = Math.max(1, Math.min(16, Math.round(lenU)));
        f.ph = Math.max(1, Math.min(16, Math.round(lenV)));
        Vector3f p = new Vector3f();
        for (int k = 0; k < 4; k++) {
            m.transformPosition(v[k * 5] / 16f, v[k * 5 + 1] / 16f, v[k * 5 + 2] / 16f, p);
            f.p[k * 3] = p.x;
            f.p[k * 3 + 1] = p.y;
            f.p[k * 3 + 2] = p.z;
            Vector3f w = toWorld == null ? new Vector3f(p) : toWorld.transform(new Vector3f(p));
            f.w[k * 3] = w.x + camera.x;
            f.w[k * 3 + 1] = w.y + camera.y;
            f.w[k * 3 + 2] = w.z + camera.z;
            f.s[k] = (v[k * 5 + 3] - umin) / (umax - umin);
            f.t[k] = (v[k * 5 + 4] - vmin) / (vmax - vmin);
            f.u[k] = v[k * 5 + 3];
            f.v[k] = v[k * 5 + 4];
        }
        f.nx = n.x;
        f.ny = n.y;
        f.nz = n.z;
        Vector3f wn = toWorld == null ? n : toWorld.transform(new Vector3f(n));
        f.wnx = wn.x;
        f.wny = wn.y;
        f.wnz = wn.z;
        out.add(f);
    }

    static boolean shaped;

    //? if >=1.21.11 {
    static void collect(ModelPart root, PoseStack pose, Vec3 camera, List<Face> out, int[] cubes) {
        walk(root, pose, camera, out, cubes, null);
    }

    static void collectDrawn(ModelPart part, PoseStack pose, Vec3 camera, List<Face> out, int[] cubes, java.util.Set<ModelPart> walked) {
        boolean was = shaped;
        shaped = true;
        try {
            walk(part, pose, camera, out, cubes, walked);
        } finally {
            shaped = was;
        }
    }

    private static void walk(ModelPart part, PoseStack pose, Vec3 camera, List<Face> out, int[] cubes, java.util.Set<ModelPart> walked) {
        if (!part.visible) return;
        if (walked != null && !walked.add(part)) return;
        ModelPartAccessor acc = (ModelPartAccessor) (Object) part;
        List<ModelPart.Cube> own = acc.simpleblood$cubes();
        Map<String, ModelPart> children = acc.simpleblood$children();
        if (own.isEmpty() && children.isEmpty()) return;
        pose.pushPose();
        part.translateAndRotate(pose);
        if (!part.skipDraw) {
            PoseStack.Pose last = pose.last();
            for (ModelPart.Cube cube : own) {
                int id = cubes[0]++;
                for (ModelPart.Polygon polygon : cube.polygons) {
                    ModelPart.Vertex[] vs = polygon.vertices();
                    if (vs.length != 4) continue;
                    float[] v = new float[20];
                    for (int k = 0; k < 4; k++) {
                        v[k * 5] = vs[k].x();
                        v[k * 5 + 1] = vs[k].y();
                        v[k * 5 + 2] = vs[k].z();
                        v[k * 5 + 3] = vs[k].u();
                        v[k * 5 + 4] = vs[k].v();
                    }
                    int before = out.size();
                    addPolygon(polygon, id, v, polygon.normal().x(), polygon.normal().y(), polygon.normal().z(),
                            last.pose(), last.normal(), null, camera, out);
                    if (shaped && out.size() > before) out.get(before).shape = shapeOf(part, v);
                }
            }
        }
        for (Map.Entry<String, ModelPart> child : children.entrySet()) {
            if (!SKIP.contains(child.getKey())) walk(child.getValue(), pose, camera, out, cubes, walked);
        }
        pose.popPose();
    }

    private static long shapeOf(ModelPart part, float[] v) {
        net.minecraft.client.model.geom.PartPose rest = part.getInitialPose();
        long h = 1125899906842597L;
        h = 31 * h + Float.floatToIntBits(rest.x());
        h = 31 * h + Float.floatToIntBits(rest.y());
        h = 31 * h + Float.floatToIntBits(rest.z());
        for (float f : v) h = 31 * h + Float.floatToIntBits(f);
        return h == 0 ? 1 : h;
    }
    //?}

    //? if <=1.21.1 {
    /*    private static final List<Object[]> CAUGHT = new ArrayList<>();
    private static final Set<ModelPart> SKIPPED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static boolean catching;
    private static final Set<ModelPart> WALKED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /^* From the mob's renderer: its model and layers are about to be drawn. ^/
    public static void startCatching() {
        CAUGHT.clear();
        SKIPPED.clear();
        WALKED.clear();
        catching = true;
    }

    /^*
     * From the model part mixin: a part is about to be drawn with everything under it. The tree
     * is walked here the way vanilla draws it, instead of catching the cubes as they are drawn:
     * Sodium (and Embeddium) cancel the part's render() and draw the whole tree themselves, so
     * neither compile() nor the children's render() is ever called there.
     ^/
    public static void rendering(ModelPart part, PoseStack stack) {
        if (!catching || WALKED.contains(part)) return;
        // Called from the head of the game's own ModelPart.render: a failure here must not
        try {
            PoseStack copy = new PoseStack();
            copy.last().pose().set(stack.last().pose());
            copy.last().normal().set(stack.last().normal());
            walk(part, copy);
        } catch (Throwable t) {
            catching = false;
            com.simpleblood.Guard.fail(com.simpleblood.Guard.Part.SURFACES, "reading the model of a mob", t);
        }
    }

    private static void walk(ModelPart part, PoseStack stack) {
        if (!part.visible || !WALKED.add(part)) return;
        com.simpleblood.mixin.ModelPartAccessor parts = (com.simpleblood.mixin.ModelPartAccessor) (Object) part;
        Map<String, ModelPart> children = parts.simpleblood$children();
        drawing(part, children);
        stack.pushPose();
        part.translateAndRotate(stack);
        if (!part.skipDraw) caught(part, stack.last(), parts.simpleblood$cubes());
        for (ModelPart child : children.values()) walk(child, stack);
        stack.popPose();
    }

    public static void stopCatching() {
        catching = false;
    }

    public static boolean catching() {
        return catching;
    }

    /^* A part is being drawn, with its children by name. ^/
    private static void drawing(ModelPart part, Map<String, ModelPart> children) {
        if (!catching || children.isEmpty()) return;
        boolean skipped = SKIPPED.contains(part);
        for (Map.Entry<String, ModelPart> child : children.entrySet()) {
            if (skipped || SKIP.contains(child.getKey())) SKIPPED.add(child.getValue());
        }
    }

    /^* A drawn part's cubes and the pose they are drawn with. ^/
    private static void caught(ModelPart part, PoseStack.Pose pose, List<ModelPart.Cube> cubes) {
        if (cubes.isEmpty() || SKIPPED.contains(part)) return;
        CAUGHT.add(new Object[]{new Matrix4f(pose.pose()), new Matrix3f(pose.normal()), cubes});
    }

    /^* The faces caught while the mob was drawn (this frame). ^/
    @SuppressWarnings("unchecked")
    static List<Face> collectCaught(Vec3 camera) {
        catching = false;
        List<Face> out = new ArrayList<>();
        int id = 0;
        Quaternionf toWorld = viewToWorld();
        for (Object[] c : CAUGHT) {
            Matrix4f m = (Matrix4f) c[0];
            Matrix3f n = (Matrix3f) c[1];
            for (ModelPart.Cube cube : (List<ModelPart.Cube>) c[2]) {
                if (!addPolygons(cube, id, m, n, toWorld, camera, out)) addBox(cube, id, m, n, toWorld, camera, out);
                id++;
            }
        }
        CAUGHT.clear();
        return out;
    }

    // These versions keep a cube's polygons to themselves; they are read by the types of their
    private static java.lang.reflect.Field fPolygons, fVertices, fNormal, fPos, fU, fV;
    private static boolean looked, readable;

    private static boolean addPolygons(ModelPart.Cube cube, int id, Matrix4f m, Matrix3f n, Quaternionf toWorld, Vec3 camera, List<Face> out) {
        if (!looked) {
            looked = true;
            try {
                for (java.lang.reflect.Field f : ModelPart.Cube.class.getDeclaredFields()) {
                    if (f.getType().isArray() && !f.getType().getComponentType().isPrimitive()) fPolygons = f;
                }
                Class<?> polygon = fPolygons.getType().getComponentType();
                for (java.lang.reflect.Field f : polygon.getDeclaredFields()) {
                    if (f.getType().isArray()) fVertices = f;
                    else if (f.getType() == Vector3f.class) fNormal = f;
                }
                Class<?> vertex = fVertices.getType().getComponentType();
                for (java.lang.reflect.Field f : vertex.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (f.getType() == Vector3f.class) fPos = f;
                    else if (f.getType() == float.class) {
                        if (fU == null) fU = f;
                        else if (fV == null) fV = f;
                    }
                }
                for (java.lang.reflect.Field f : new java.lang.reflect.Field[]{fPolygons, fVertices, fNormal, fPos, fU, fV}) f.setAccessible(true);
                readable = true;
            } catch (Throwable t) {
                com.simpleblood.SimpleBlood.LOGGER.warn("Blood on mobs: model faces not readable, using box sides", t);
                readable = false;
            }
        }
        if (!readable) return false;
        try {
            for (Object polygon : (Object[]) fPolygons.get(cube)) {
                Object[] vs = (Object[]) fVertices.get(polygon);
                if (vs.length != 4) continue;
                float[] v = new float[20];
                for (int k = 0; k < 4; k++) {
                    Vector3f pos = (Vector3f) fPos.get(vs[k]);
                    v[k * 5] = pos.x;
                    v[k * 5 + 1] = pos.y;
                    v[k * 5 + 2] = pos.z;
                    v[k * 5 + 3] = fU.getFloat(vs[k]);
                    v[k * 5 + 4] = fV.getFloat(vs[k]);
                }
                Vector3f normal = (Vector3f) fNormal.get(polygon);
                addPolygon(polygon, id, v, normal.x, normal.y, normal.z, m, n, toWorld, camera, out);
            }
            return true;
        } catch (Throwable t) {
            readable = false;
            return false;
        }
    }

    private static final int[][][] SIDES = {
            {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},
            {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}},
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
            {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}},
            {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}},
            {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
    };
    private static final float[][] SIDE_NORMALS = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
    private static final float[][] SIDE_UV = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    private static void addBox(ModelPart.Cube cube, int id, Matrix4f m, Matrix3f n, Quaternionf toWorld, Vec3 camera, List<Face> out) {
        float[] lo = {cube.minX, cube.minY, cube.minZ}, hi = {cube.maxX, cube.maxY, cube.maxZ};
        for (int side = 0; side < 6; side++) {
            float[] v = new float[20];
            for (int k = 0; k < 4; k++) {
                for (int a = 0; a < 3; a++) v[k * 5 + a] = SIDES[side][k][a] == 0 ? lo[a] : hi[a];
                v[k * 5 + 3] = SIDE_UV[k][0];
                v[k * 5 + 4] = SIDE_UV[k][1];
            }
            addPolygon(new CubeSide(cube, side), id, v, SIDE_NORMALS[side][0], SIDE_NORMALS[side][1], SIDE_NORMALS[side][2],
                    m, n, toWorld, camera, out);
        }
    }
    *///?}

    static List<Face> collectModel(ModelPart root, PoseStack pose) {
        List<Face> out = new ArrayList<>();
        int[] cubes = {0};
        //? if >=1.21.11 {
        walk(root, pose, Vec3.ZERO, out, cubes, null);
        //?} else {
        /*walkModel(root, pose, out, cubes);
        *///?}
        return out;
    }

    //? if <=1.21.1 {
    /*private static void walkModel(ModelPart part, PoseStack stack, List<Face> out, int[] cubes) {
        if (!part.visible) return;
        com.simpleblood.mixin.ModelPartAccessor parts = (com.simpleblood.mixin.ModelPartAccessor) (Object) part;
        stack.pushPose();
        part.translateAndRotate(stack);
        if (!part.skipDraw) {
            Matrix4f m = stack.last().pose();
            Matrix3f n = stack.last().normal();
            for (ModelPart.Cube cube : parts.simpleblood$cubes()) {
                int id = cubes[0]++;
                if (!addPolygons(cube, id, m, n, null, Vec3.ZERO, out)) addBox(cube, id, m, n, null, Vec3.ZERO, out);
            }
        }
        for (Map.Entry<String, ModelPart> child : parts.simpleblood$children().entrySet()) {
            if (!SKIP.contains(child.getKey())) walkModel(child.getValue(), stack, out, cubes);
        }
        stack.popPose();
    }
    *///?}

    record CubeSide(Object cube, int side) {}

    private static Quaternionf viewToWorld() {
        //? if 1.20.1 {
        /*net.minecraft.client.Camera cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        return new Quaternionf().rotateX((float) Math.toRadians(cam.getXRot()))
                .rotateY((float) Math.toRadians(cam.getYRot() + 180f)).conjugate();
        *///?} else {
        return null;
        //?}
    }
}
