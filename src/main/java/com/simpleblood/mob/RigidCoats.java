package com.simpleblood.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class RigidCoats {
    private RigidCoats() {}

    public interface Limb {
        Vec3 position(float partialTick);

        Quaternionf rotation(float partialTick);

        default Vector3f scale() {
            return null;
        }

        default List<float[]> quads() {
            return List.of();
        }

        default float fade(float partialTick) {
            return 1f;
        }
    }

    private record Pinned(ModelFacesOfMob.Face face, int limb, float[] corners, Vector3f normal) {}

    private record Ragdoll(int entityId, List<Limb> limbs, List<Pinned> faces) {}

    private static final Map<Integer, Ragdoll> RAGDOLLS = new HashMap<>();
    private static final float LIFT = 0.01f;
    private static final float UV_EPSILON = 1.0e-4f;
    private static boolean reported;

    public static void attach(int entityId, List<Limb> limbs) {
        MobCoat coat = MobBlood.coatOf(entityId);
        if (coat == null || limbs.isEmpty()) return;
        int n = limbs.size();
        Vec3[] at = new Vec3[n];
        Quaternionf[] turn = new Quaternionf[n], inverse = new Quaternionf[n];
        Vector3f[] scale = new Vector3f[n];
        List<List<float[]>> quads = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Limb l = limbs.get(i);
            at[i] = l.position(1f);
            turn[i] = l.rotation(1f);
            inverse[i] = turn[i] == null ? null : new Quaternionf(turn[i]).conjugate();
            scale[i] = l.scale();
            quads.add(at[i] == null || turn[i] == null ? List.of() : l.quads());
        }
        List<Pinned> pinned = new ArrayList<>();
        if (coat.lastFaces == null || coat.lastFaces.isEmpty()) {
            pinned = fromMeshes(quads);
            com.simpleblood.Tally.add("coat.rigid.fresh", pinned.size());
            if (pinned.isEmpty()) return;
            com.simpleblood.Tally.count("coat.rigid");
            RAGDOLLS.put(entityId, new Ragdoll(entityId, limbs, pinned));
            coat.onRagdoll = MobBlood.now();
            return;
        }
        int bloody = 0, found = 0;
        for (ModelFacesOfMob.Face f : coat.lastFaces) {
            if (coat.faces.get(f.key) != null) bloody++;
            double cx = (f.w[0] + f.w[3] + f.w[6] + f.w[9]) / 4, cy = (f.w[1] + f.w[4] + f.w[7] + f.w[10]) / 4, cz = (f.w[2] + f.w[5] + f.w[8] + f.w[11]) / 4;
            Vector3f faceNormal = new Vector3f(f.wnx, f.wny, f.wnz);
            Pinned p = onMesh(f, cx, cy, cz, faceNormal, at, turn, scale, quads);
            if (p != null) {
                if (coat.faces.get(f.key) != null) found++;
                pinned.add(p);
                continue;
            }
            p = nearest(f, cx, cy, cz, faceNormal, at, inverse, scale);
            if (p != null) pinned.add(p);
        }
        if (!reported && bloody > 0) {
            reported = true;
            com.simpleblood.SimpleBlood.LOGGER.info("Physics Mod ragdoll: {} of {} bloody faces found on its meshes ({} faces carried in all)",
                    found, bloody, pinned.size());
            if (found == 0) {
                ModelFacesOfMob.Face f = coat.lastFaces.get(0);
                float[] q = null;
                for (List<float[]> l : quads) if (!l.isEmpty()) { q = l.get(0); break; }
                com.simpleblood.SimpleBlood.LOGGER.info("  a face's corners {} {} / {} {}; a mesh quad's {}", f.u[0], f.v[0], f.u[2], f.v[2],
                        q == null ? "none (no meshes read)" : q[3] + " " + q[4] + " / " + q[13] + " " + q[14]);
            }
        }
        com.simpleblood.Tally.add("coat.rigid.mesh", found);
        if (pinned.isEmpty()) return;
        com.simpleblood.Tally.count("coat.rigid");
        RAGDOLLS.put(entityId, new Ragdoll(entityId, limbs, pinned));
        coat.onRagdoll = MobBlood.now();
    }

    private static Pinned onMesh(ModelFacesOfMob.Face f, double cx, double cy, double cz, Vector3f faceNormal,
                                 Vec3[] at, Quaternionf[] turn, Vector3f[] scale, List<List<float[]>> quads) {
        Pinned best = null;
        double bestD = Double.MAX_VALUE;
        int[] order = new int[4];
        for (int i = 0; i < quads.size(); i++) {
            for (float[] q : quads.get(i)) {
                if (!sameCorners(f, q, order)) continue;
                float[] corners = new float[12];
                Vector3f c = new Vector3f();
                double mx = 0, my = 0, mz = 0;
                for (int k = 0; k < 4; k++) {
                    int j = order[k] * 5;
                    corners[k * 3] = q[j];
                    corners[k * 3 + 1] = q[j + 1];
                    corners[k * 3 + 2] = q[j + 2];
                    toWorld(corners, k, at[i], turn[i], scale[i], c);
                    mx += c.x / 4;
                    my += c.y / 4;
                    mz += c.z / 4;
                }
                double d = (mx - cx) * (mx - cx) + (my - cy) * (my - cy) + (mz - cz) * (mz - cz);
                if (d >= bestD) continue;
                Vector3f e1 = new Vector3f(corners[3] - corners[0], corners[4] - corners[1], corners[5] - corners[2]);
                Vector3f e3 = new Vector3f(corners[9] - corners[0], corners[10] - corners[1], corners[11] - corners[2]);
                Vector3f normal = e1.cross(e3);
                if (normal.lengthSquared() < 1e-12f) continue;
                normal.normalize();
                if (turn[i].transform(new Vector3f(normal)).dot(faceNormal) < 0) normal.negate();
                bestD = d;
                best = new Pinned(f, i, corners, normal);
            }
        }
        return best;
    }

    private static List<Pinned> fromMeshes(List<List<float[]>> quads) {
        List<Pinned> out = new ArrayList<>();
        for (int i = 0; i < quads.size(); i++) {
            for (float[] q : quads.get(i)) {
                float[] corners = new float[12];
                for (int k = 0; k < 4; k++) {
                    corners[k * 3] = q[k * 5];
                    corners[k * 3 + 1] = q[k * 5 + 1];
                    corners[k * 3 + 2] = q[k * 5 + 2];
                }
                Vector3f e1 = new Vector3f(corners[3] - corners[0], corners[4] - corners[1], corners[5] - corners[2]);
                Vector3f e3 = new Vector3f(corners[9] - corners[0], corners[10] - corners[1], corners[11] - corners[2]);
                Vector3f normal = new Vector3f(e1).cross(e3);
                if (normal.lengthSquared() < 1e-12f) continue;
                normal.normalize();
                Vector3f mid = new Vector3f((corners[0] + corners[3] + corners[6] + corners[9]) / 4,
                        (corners[1] + corners[4] + corners[7] + corners[10]) / 4, (corners[2] + corners[5] + corners[8] + corners[11]) / 4);
                if (mid.lengthSquared() > 1e-8f && normal.dot(mid) < 0) normal.negate();
                float umin = Math.min(Math.min(q[3], q[8]), Math.min(q[13], q[18])), umax = Math.max(Math.max(q[3], q[8]), Math.max(q[13], q[18]));
                float vmin = Math.min(Math.min(q[4], q[9]), Math.min(q[14], q[19])), vmax = Math.max(Math.max(q[4], q[9]), Math.max(q[14], q[19]));
                if (umax - umin < 1e-6f || vmax - vmin < 1e-6f) continue;
                ModelFacesOfMob.Face f = new ModelFacesOfMob.Face(new Object(), out.size());
                for (int k = 0; k < 4; k++) {
                    f.u[k] = q[k * 5 + 3];
                    f.v[k] = q[k * 5 + 4];
                    f.s[k] = (f.u[k] - umin) / (umax - umin);
                    f.t[k] = (f.v[k] - vmin) / (vmax - vmin);
                }
                f.pw = Math.max(1, Math.min(16, Math.round(Math.max(e1.length(), 1e-3f) * 16)));
                f.ph = Math.max(1, Math.min(16, Math.round(Math.max(e3.length(), 1e-3f) * 16)));
                if (Math.abs(f.s[1] - f.s[0]) < Math.abs(f.t[1] - f.t[0])) { int w = f.pw; f.pw = f.ph; f.ph = w; }
                out.add(new Pinned(f, i, corners, normal));
            }
        }
        return out;
    }

    private static boolean sameCorners(ModelFacesOfMob.Face f, float[] q, int[] order) {
        int used = 0;
        for (int k = 0; k < 4; k++) {
            order[k] = -1;
            for (int j = 0; j < 4; j++) {
                if ((used & (1 << j)) != 0) continue;
                if (Math.abs(q[j * 5 + 3] - f.u[k]) < UV_EPSILON && Math.abs(q[j * 5 + 4] - f.v[k]) < UV_EPSILON) {
                    order[k] = j;
                    used |= 1 << j;
                    break;
                }
            }
            if (order[k] < 0) return false;
        }
        return true;
    }

    private static Pinned nearest(ModelFacesOfMob.Face f, double cx, double cy, double cz, Vector3f faceNormal,
                                  Vec3[] at, Quaternionf[] inverse, Vector3f[] scale) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < at.length; i++) {
            if (at[i] == null || inverse[i] == null) continue;
            double d = at[i].distanceToSqr(cx, cy, cz);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        if (best < 0 || bestD > 4) return null;
        float[] corners = new float[12];
        Vector3f v = new Vector3f();
        for (int k = 0; k < 4; k++) {
            v.set((float) (f.w[k * 3] - at[best].x), (float) (f.w[k * 3 + 1] - at[best].y), (float) (f.w[k * 3 + 2] - at[best].z));
            inverse[best].transform(v);
            if (scale[best] != null) v.div(nonZero(scale[best].x), nonZero(scale[best].y), nonZero(scale[best].z));
            corners[k * 3] = v.x;
            corners[k * 3 + 1] = v.y;
            corners[k * 3 + 2] = v.z;
        }
        return new Pinned(f, best, corners, inverse[best].transform(new Vector3f(faceNormal)));
    }

    private static float nonZero(float s) {
        return Math.abs(s) < 1e-6f ? 1f : s;
    }

    private static void toWorld(float[] corners, int k, Vec3 at, Quaternionf turn, Vector3f scale, Vector3f out) {
        out.set(corners[k * 3], corners[k * 3 + 1], corners[k * 3 + 2]);
        if (scale != null) out.mul(scale);
        turn.transform(out);
        out.add((float) at.x, (float) at.y, (float) at.z);
    }

    public static void tick() {
        if (RAGDOLLS.isEmpty()) return;
        Iterator<Ragdoll> it = RAGDOLLS.values().iterator();
        while (it.hasNext()) {
            Ragdoll r = it.next();
            MobCoat coat = MobBlood.coatOf(r.entityId);
            boolean alive = false;
            for (Limb l : r.limbs) if (l.position(1f) != null) alive = true;
            if (coat == null || !alive) {
                it.remove();
                continue;
            }
            coat.onRagdoll = MobBlood.now();
        }
    }

    public static void render(ClientLevel level, Vec3 camera, PoseStack.Pose pose, VertexConsumer vc, float partialTick) {
        if (RAGDOLLS.isEmpty()) return;
        for (Ragdoll r : RAGDOLLS.values()) {
            MobCoat coat = MobBlood.coatOf(r.entityId);
            if (coat == null) continue;
            int n = r.limbs.size();
            Vec3[] at = new Vec3[n];
            Quaternionf[] turn = new Quaternionf[n];
            Vector3f[] scale = new Vector3f[n];
            float[] limbFade = new float[n];
            for (int i = 0; i < n; i++) {
                at[i] = r.limbs.get(i).position(partialTick);
                turn[i] = at[i] == null ? null : r.limbs.get(i).rotation(partialTick);
                scale[i] = r.limbs.get(i).scale();
                limbFade[i] = at[i] == null ? 0f : r.limbs.get(i).fade(partialTick);
            }
            List<ModelFacesOfMob.Face> posed = new ArrayList<>(r.faces.size());
            float[] fade = new float[r.faces.size()];
            Vector3f c = new Vector3f();
            double sx = 0, sy = 0, sz = 0;
            for (Pinned p : r.faces) {
                Vec3 o = at[p.limb];
                Quaternionf q = turn[p.limb];
                if (o == null || q == null) continue;
                Vector3f nrm = q.transform(new Vector3f(p.normal));
                double[] world = new double[12];
                for (int k = 0; k < 4; k++) {
                    toWorld(p.corners, k, o, q, scale[p.limb], c);
                    world[k * 3] = c.x + nrm.x * LIFT;
                    world[k * 3 + 1] = c.y + nrm.y * LIFT;
                    world[k * 3 + 2] = c.z + nrm.z * LIFT;
                }
                fade[posed.size()] = limbFade[p.limb];
                if (limbFade[p.limb] <= 0f) com.simpleblood.Tally.count("coat.ragdoll.hidden");
                else if (limbFade[p.limb] < 1f) com.simpleblood.Tally.count("coat.ragdoll.fading");
                posed.add(p.face.moved(world, nrm, camera));
                sx += o.x;
                sy += o.y;
                sz += o.z;
            }
            if (posed.isEmpty()) continue;
            int light = com.simpleblood.surface.SurfaceRenderer.lightAt(level, BlockPos.containing(sx / posed.size(), sy / posed.size() + 0.2, sz / posed.size()));
            MobBlood.drawPosed(coat, posed, fade, pose, vc, camera, light);
        }
    }

    public static void clear() {
        RAGDOLLS.clear();
    }
}
