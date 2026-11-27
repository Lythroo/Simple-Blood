package com.simpleblood.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simpleblood.compat.PixelGore;
import com.simpleblood.geom.VoxelGrid;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VoxelBodyCoats {
    private VoxelBodyCoats() {}

    private static final boolean[] NONE_CUT = new boolean[0];
    private static final boolean[] ALL_CUT = new boolean[0];

    private static final class Masks {
        Object revision;
        final Map<Object, boolean[]> byFace = new HashMap<>();
    }

    private static final Map<Integer, Masks> MASKS = new HashMap<>();

    static void tick(ClientLevel level) {
        ModelFacesOfMob.keepLocal = PixelGore.available();
        if (MASKS.isEmpty()) return;
        if (level == null) {
            MASKS.clear();
            return;
        }
        MASKS.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    static void maskLiving(int entityId, List<ModelFacesOfMob.Face> faces) {
        if (faces.isEmpty() || !ModelFacesOfMob.keepLocal) return;
        ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        Entity mob = level == null ? null : level.getEntity(entityId);
        PixelGore.Body body = PixelGore.livingBody(mob);
        if (body == null) return;
        Masks masks = masksOf(entityId, body);
        int cut = 0;
        for (ModelFacesOfMob.Face f : faces) {
            if (f.modelCube == null) continue;
            PixelGore.Cut c = body.cutOf(f.modelCube);
            if (c == null || c == PixelGore.Cut.WHOLE) continue;
            boolean[] mask = maskOf(masks, f, c);
            if (mask == NONE_CUT) continue;
            f.gone = mask == ALL_CUT ? allOf(masks, f) : mask;
            cut++;
        }
        if (cut > 0) com.simpleblood.Tally.count("coat.voxel.living.cut");
    }

    private static boolean[] allOf(Masks masks, ModelFacesOfMob.Face f) {
        return masks.byFace.computeIfAbsent(new AllCut(f.key), k -> {
            boolean[] all = new boolean[f.pw * f.ph];
            java.util.Arrays.fill(all, true);
            return all;
        });
    }

    private record AllCut(Object key) {}

    private static Masks masksOf(int entityId, PixelGore.Body body) {
        Masks m = MASKS.computeIfAbsent(entityId, k -> new Masks());
        if (m.revision != body.revision()) {
            m.revision = body.revision();
            m.byFace.clear();
        }
        return m;
    }

    private static boolean[] maskOf(Masks masks, ModelFacesOfMob.Face f, PixelGore.Cut cut) {
        boolean[] known = masks.byFace.get(f.key);
        if (known != null) return known;
        boolean[] mask = cut(f, cut);
        masks.byFace.put(f.key, mask);
        return mask;
    }

    private static boolean[] cut(ModelFacesOfMob.Face f, PixelGore.Cut cut) {
        if (cut == PixelGore.Cut.NOTHING) return ALL_CUT;
        VoxelGrid grid = cut.grid();
        if (grid == null || f.local == null) return NONE_CUT;
        float[] l = f.local;
        boolean[] gone = new boolean[f.pw * f.ph];
        int count = 0;
        for (int y = 0; y < f.ph; y++) {
            for (int x = 0; x < f.pw; x++) {
                double[] ab = f.edgeWeights((x + 0.5) / f.pw, (y + 0.5) / f.ph);
                double a = ab[0], b = ab[1];
                int cell = grid.cellAt(l[0] + (l[3] - l[0]) * a + (l[9] - l[0]) * b,
                        l[1] + (l[4] - l[1]) * a + (l[10] - l[1]) * b,
                        l[2] + (l[5] - l[2]) * a + (l[11] - l[2]) * b);
                if (cut.present(cell)) continue;
                gone[y * f.pw + x] = true;
                count++;
            }
        }
        return count == 0 ? NONE_CUT : count == gone.length ? ALL_CUT : gone;
    }

    public static boolean any() {
        return PixelGore.anyCorpse();
    }

    public static void render(ClientLevel level, Vec3 camera, PoseStack.Pose pose, VertexConsumer vc) {
        if (!any()) return;
        PixelGore.corpses(level, camera, (remainsId, mobId, body) -> {
            MobCoat coat = MobBlood.coatOf(mobId);
            return coat == null ? null : new Corpse(level, camera, pose, vc, coat, masksOf(remainsId, body));
        });
    }

    private static final class Corpse implements PixelGore.CubeSink {
        private final ClientLevel level;
        private final Vec3 camera;
        private final PoseStack.Pose pose;
        private final VertexConsumer vc;
        private final MobCoat coat;
        private final Masks masks;
        private final List<ModelFacesOfMob.Face> faces = new ArrayList<>();
        private int cubes;
        private double sx, sy, sz;

        Corpse(ClientLevel level, Vec3 camera, PoseStack.Pose pose, VertexConsumer vc, MobCoat coat, Masks masks) {
            this.level = level;
            this.camera = camera;
            this.pose = pose;
            this.vc = vc;
            this.coat = coat;
            this.masks = masks;
        }

        @Override
        public void cube(Object modelCube, org.joml.Matrix4f m, org.joml.Matrix3f n, Object texture, PixelGore.Cut cut) {
            int from = faces.size();
            ModelFacesOfMob.cubeFaces(modelCube, cubes++, m, n, camera, faces);
            MobTextures.Alpha alpha = MobTextures.of(texture);
            for (int i = faces.size() - 1; i >= from; i--) {
                ModelFacesOfMob.Face f = faces.get(i);
                f.alpha = alpha;
                boolean[] mask = cut == PixelGore.Cut.WHOLE ? NONE_CUT : maskOf(masks, f, cut);
                if (mask == ALL_CUT) {
                    faces.remove(i);
                    continue;
                }
                f.gone = mask == NONE_CUT ? null : mask;
                sx += f.wx(0);
                sy += f.wy(0);
                sz += f.wz(0);
            }
        }

        @Override
        public void done() {
            if (faces.isEmpty()) return;
            int n = faces.size();
            int light = com.simpleblood.surface.SurfaceRenderer.lightAt(level, BlockPos.containing(sx / n, sy / n + 0.2, sz / n));
            com.simpleblood.Tally.count("coat.voxel.corpse");
            MobBlood.drawPosed(coat, faces, null, pose, vc, camera, light);
        }
    }

    public static long cutTexelsForTests(int mobId) {
        MobCoat coat = MobBlood.coatOf(mobId);
        if (coat == null) return 0;
        long n = 0;
        for (List<ModelFacesOfMob.Face> piece : coat.ragdollPieces.values()) {
            for (ModelFacesOfMob.Face f : piece) {
                if (f.gone == null) continue;
                for (boolean g : f.gone) if (g) n++;
            }
        }
        return n;
    }

    static void clear() {
        MASKS.clear();
        PixelGore.clear();
    }
}
