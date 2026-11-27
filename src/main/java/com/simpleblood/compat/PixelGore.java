package com.simpleblood.compat;

import com.simpleblood.SimpleBlood;
import com.simpleblood.geom.OrientedBox;
import com.simpleblood.geom.VoxelGrid;
import com.simpleblood.mob.RagdollBodies;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PixelGore {

    private PixelGore() {}

    public static final String MOD = "pixelgore";

    private static final String ROOT = "com.pixelg.gore.";
    private static final int DEATH_MEMORY = 200;
    private static final int MATCH_TICKS = 40;

    private static boolean looked;
    private static boolean on;
    private static Class<?> remainsClass;
    private static Field remainsSource, remainsScale, remainsData;
    private static Class<?> dataClass;
    private static Field dataBody, dataList, dataShown;
    private static Method listItems, itemEntry, itemSegment;
    private static Method bodyPeek, bodyDamaged, bodyPresence;
    private static Field bodyRevision, bodyMeshes;
    private static Method meshRevision;
    private static Field bodyPlan;
    private static Field planEntries, planBody, cubeOffsets;
    private static Field entryCube, entryShape, entryPlanCube, entryCarrier, entryVanilla, entryTexture, entryRest;
    private static Method anchored;
    private static Field shapeNx, shapeNy, shapeNz, shapeMinX, shapeMinY, shapeMinZ, shapeMaxX, shapeMaxY, shapeMaxZ;
    private static Method bitsWords, bitsSize;
    private static Method poseIndexOf, posePositions, poseRotations;

    public static boolean available() {
        if (!looked) {
            looked = true;
            if (com.simpleblood.Platform.modIds().contains(MOD)) lookUp();
        }
        return on;
    }

    private static void lookUp() {
        try {
            ClassLoader cl = PixelGore.class.getClassLoader();
            remainsClass = Class.forName(ROOT + "entity.RemainsEntity", false, cl);
            remainsSource = remainsClass.getField("sourceUuid");
            remainsScale = remainsClass.getField("scale");
            remainsData = remainsClass.getField("clientData");
            dataClass = Class.forName(ROOT + "client.render.RemainsRenderer$Data", false, cl);
            dataBody = open(dataClass.getDeclaredField("body"));
            dataList = open(dataClass.getDeclaredField("list"));
            dataShown = open(dataClass.getDeclaredField("lastDisplayed"));
            listItems = open(Class.forName(ROOT + "client.render.RemainsRenderer$DrawList", false, cl).getDeclaredMethod("items"));
            Class<?> item = Class.forName(ROOT + "client.render.RemainsRenderer$Item", false, cl);
            itemEntry = open(item.getDeclaredMethod("entry"));
            itemSegment = open(item.getDeclaredMethod("segment"));
            Class<?> body = Class.forName(ROOT + "client.body.ClientBody", false, cl);
            bodyPeek = body.getMethod("peek", Entity.class);
            bodyDamaged = body.getMethod("isDamaged");
            bodyPresence = body.getMethod("presence");
            bodyPlan = body.getField("clientPlan");
            bodyRevision = body.getField("revision");
            bodyMeshes = open(body.getDeclaredField("meshes"));
            meshRevision = open(Class.forName(ROOT + "client.body.ClientBody$CachedMesh", false, cl).getDeclaredMethod("revision"));
            Class<?> plan = Class.forName(ROOT + "client.capture.ClientPlan", false, cl);
            planEntries = plan.getField("entries");
            planBody = plan.getField("plan");
            cubeOffsets = Class.forName(ROOT + "body.BodyPlan", false, cl).getField("cubeOffset");
            Class<?> entry = Class.forName(ROOT + "client.capture.ClientPlan$Entry", false, cl);
            entryCube = entry.getField("cube");
            entryShape = entry.getField("shape");
            entryPlanCube = entry.getField("planCube");
            entryCarrier = entry.getField("carrier");
            entryVanilla = entry.getField("vanilla");
            entryTexture = entry.getField("texture");
            entryRest = entry.getField("restBodyFromLocal");
            Class<?> bits = Class.forName(ROOT + "body.VoxelBits", false, cl);
            anchored = Class.forName(ROOT + "client.capture.Carriers", false, cl).getMethod("anchored", entry, bits);
            Class<?> shape = Class.forName(ROOT + "client.model.CubeShape", false, cl);
            shapeNx = shape.getField("nx");
            shapeNy = shape.getField("ny");
            shapeNz = shape.getField("nz");
            shapeMinX = shape.getField("minX");
            shapeMinY = shape.getField("minY");
            shapeMinZ = shape.getField("minZ");
            shapeMaxX = shape.getField("maxX");
            shapeMaxY = shape.getField("maxY");
            shapeMaxZ = shape.getField("maxZ");
            bitsWords = bits.getMethod("toLongArray");
            bitsSize = bits.getMethod("size");
            Class<?> pose = Class.forName(ROOT + "net.PoseData", false, cl);
            poseIndexOf = pose.getMethod("indexOf", int.class);
            posePositions = pose.getMethod("positions");
            poseRotations = pose.getMethod("rotations");
            if (remainsSource.getType() != UUID.class || remainsScale.getType() != float.class || entryPlanCube.getType() != int.class
                    || entryCarrier.getType() != int[].class || entryVanilla.getType() != boolean.class || entryRest.getType() != Matrix4f.class
                    || cubeOffsets.getType() != int[].class || shapeNx.getType() != int.class || shapeMinX.getType() != float.class
                    || !Map.class.isAssignableFrom(planEntries.getType()) || bitsWords.getReturnType() != long[].class
                    || posePositions.getReturnType() != double[].class || poseRotations.getReturnType() != float[].class) {
                throw new NoSuchFieldException("its fields have other types");
            }
            on = true;
            SimpleBlood.LOGGER.info("PixelGore found: blood stays on its voxel bodies and corpses, and its corpses bleed");
        } catch (Throwable t) {
            on = false;
            SimpleBlood.LOGGER.warn("PixelGore found, but not a version Simple Blood knows; its bodies and corpses take no blood ({})", t.toString());
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T open(T member) {
        member.setAccessible(true);
        return member;
    }

    private static void disable(Throwable t) {
        if (!on) return;
        on = false;
        BODIES.clear();
        CORPSES.clear();
        DYING.clear();
        SimpleBlood.LOGGER.warn("PixelGore bodies not readable any more; they take no blood from now on ({})", t.toString());
    }

    public static final class Cut {
        public static final Cut WHOLE = new Cut(null, null, 0, null);
        public static final Cut NOTHING = new Cut(null, null, 0, null);

        private final VoxelGrid grid;
        private final long[] words;
        private final int offset;
        private final int[] carrier;

        private Cut(VoxelGrid grid, long[] words, int offset, int[] carrier) {
            this.grid = grid;
            this.words = words;
            this.offset = offset;
            this.carrier = carrier;
        }

        public VoxelGrid grid() {
            return grid;
        }

        public boolean present(int cell) {
            if (this == WHOLE) return true;
            if (this == NOTHING || cell < 0) return false;
            int bit;
            if (carrier != null) {
                if (cell >= carrier.length || carrier[cell] < 0) return false;
                bit = carrier[cell];
            } else {
                bit = offset + cell;
            }
            int word = bit >>> 6;
            return word < words.length && (words[word] & (1L << bit)) != 0;
        }
    }

    public static final class Body {
        private final Object revision;
        private final Map<?, ?> entries;
        private final long[] words;
        private final int[] offsets;
        private final Object bits;
        private final boolean living;
        private final Map<Object, Cut> cuts = new IdentityHashMap<>();

        private Body(Object revision, Map<?, ?> entries, long[] words, int[] offsets, Object bits, boolean living) {
            this.revision = revision;
            this.entries = entries;
            this.words = words;
            this.offsets = offsets;
            this.bits = bits;
            this.living = living;
        }

        public Object revision() {
            return revision;
        }

        public Cut cutOf(Object modelCube) {
            Object entry = entries.get(modelCube);
            return entry == null ? null : cutOfEntry(entry);
        }

        Cut cutOfEntry(Object entry) {
            Cut known = cuts.get(entry);
            if (known != null) return known;
            Cut cut;
            try {
                cut = read(entry);
            } catch (Throwable t) {
                disable(t);
                cut = Cut.WHOLE;
            }
            cuts.put(entry, cut);
            return cut;
        }

        private Cut read(Object entry) throws ReflectiveOperationException {
            if (entryVanilla.getBoolean(entry)) return (Boolean) anchored.invoke(null, entry, bits) ? Cut.WHOLE : Cut.NOTHING;
            Object shape = entryShape.get(entry);
            if (shape == null) return living ? Cut.WHOLE : Cut.NOTHING;
            VoxelGrid grid = new VoxelGrid(shapeMinX.getFloat(shape), shapeMinY.getFloat(shape), shapeMinZ.getFloat(shape),
                    shapeMaxX.getFloat(shape), shapeMaxY.getFloat(shape), shapeMaxZ.getFloat(shape),
                    shapeNx.getInt(shape), shapeNy.getInt(shape), shapeNz.getInt(shape));
            int planCube = entryPlanCube.getInt(entry);
            if (planCube >= 0) return planCube < offsets.length ? new Cut(grid, words, offsets[planCube], null) : Cut.NOTHING;
            int[] carrier = (int[]) entryCarrier.get(entry);
            if (carrier == null) return living ? Cut.WHOLE : Cut.NOTHING;
            return new Cut(grid, words, 0, carrier);
        }
    }

    private static final Map<Integer, Body> BODIES = new HashMap<>();

    public static Body livingBody(Entity mob) {
        if (!available() || mob == null) return null;
        try {
            Object body = bodyPeek.invoke(null, mob);
            if (body == null || !(Boolean) bodyDamaged.invoke(body) || !drawnAsVoxels(body)) return null;
            return bodyOf(mob.getId(), body, true);
        } catch (Throwable t) {
            disable(t);
            return null;
        }
    }

    private static boolean drawnAsVoxels(Object clientBody) throws ReflectiveOperationException {
        int revision = bodyRevision.getInt(clientBody);
        if (!(bodyMeshes.get(clientBody) instanceof Map<?, ?> meshes)) return false;
        for (Object mesh : meshes.values()) {
            if (mesh != null && (Integer) meshRevision.invoke(mesh) == revision) return true;
        }
        return false;
    }

    private static Body bodyOf(int entityId, Object clientBody, boolean living) throws ReflectiveOperationException {
        Object plan = bodyPlan.get(clientBody);
        Object bits = plan == null ? null : bodyPresence.invoke(clientBody);
        if (bits == null) return null;
        Body known = BODIES.get(entityId);
        if (known != null && known.bits == bits) return known;
        Object bodyPlanOf = planBody.get(plan);
        if (!(planEntries.get(plan) instanceof Map<?, ?> entries) || bodyPlanOf == null) return null;
        long[] words = (long[]) bitsWords.invoke(bits);
        Body b = new Body(words, entries, words, (int[]) cubeOffsets.get(bodyPlanOf), bits, living);
        BODIES.put(entityId, b);
        return b;
    }

    private static final class Corpse {
        int mobId = -1;
        int looks;
        Object boxesFor;
        int[] segments;
        float[] boxes;
    }

    private static final Map<Integer, Corpse> CORPSES = new HashMap<>();
    private record Dying(int id, long seen) {}

    private static final Map<UUID, Dying> DYING = new HashMap<>();

    public static void tick(ClientLevel level) {
        if (level == null || !available()) {
            CORPSES.clear();
            BODIES.clear();
            DYING.clear();
            return;
        }
        try {
            long now = level.getGameTime();
            List<Entity> found = new ArrayList<>();
            for (Entity e : level.entitiesForRendering()) {
                if (remainsClass.isInstance(e)) found.add(e);
                else if (e instanceof LivingEntity living && living.isDeadOrDying()) DYING.put(e.getUUID(), new Dying(e.getId(), now));
            }
            DYING.values().removeIf(d -> now - d.seen() > DEATH_MEMORY || now < d.seen());
            java.util.Set<Integer> here = new java.util.HashSet<>();
            for (Entity e : found) {
                here.add(e.getId());
                Corpse c = CORPSES.computeIfAbsent(e.getId(), k -> new Corpse());
                if (c.mobId >= 0 || c.looks++ >= MATCH_TICKS) continue;
                Dying d = remainsSource.get(e) instanceof UUID source ? DYING.get(source) : null;
                if (d != null) c.mobId = d.id();
            }
            CORPSES.keySet().retainAll(here);
            BODIES.keySet().removeIf(id -> !here.contains(id) && level.getEntity(id) == null);
        } catch (Throwable t) {
            disable(t);
        }
    }

    public static int mobOf(int remainsId) {
        Corpse c = CORPSES.get(remainsId);
        return c == null ? -1 : c.mobId;
    }

    public static boolean anyCorpse() {
        for (Corpse c : CORPSES.values()) if (c.mobId >= 0) return true;
        return false;
    }

    public interface CubeSink {
        void cube(Object modelCube, Matrix4f pose, Matrix3f normal, Object texture, Cut cut);

        void done();
    }

    public interface CorpseSink {
        CubeSink corpse(int remainsId, int mobId, Body body);
    }

    private static final Quaternionf TURN = new Quaternionf();

    public static void corpses(ClientLevel level, Vec3 camera, CorpseSink sink) {
        if (CORPSES.isEmpty() || !available()) return;
        try {
            for (Map.Entry<Integer, Corpse> e : CORPSES.entrySet()) {
                Corpse c = e.getValue();
                if (c.mobId < 0) continue;
                Entity remains = level.getEntity(e.getKey());
                Object data = remains == null ? null : remainsData.get(remains);
                if (!dataClass.isInstance(data)) continue;
                Object list = dataList.get(data), shown = dataShown.get(data), clientBody = dataBody.get(data);
                if (list == null || shown == null || clientBody == null) continue;
                Body body = bodyOf(e.getKey(), clientBody, false);
                if (body == null) continue;
                CubeSink cubes = sink.corpse(e.getKey(), c.mobId, body);
                if (cubes == null) continue;
                float scale = remainsScale.getFloat(remains) / 16f;
                double[] at = (double[]) posePositions.invoke(shown);
                float[] turn = (float[]) poseRotations.invoke(shown);
                for (Object item : (List<?>) listItems.invoke(list)) {
                    int i = (Integer) poseIndexOf.invoke(shown, (Integer) itemSegment.invoke(item));
                    if (i < 0 || i * 3 + 2 >= at.length || i * 4 + 3 >= turn.length) continue;
                    Object entry = itemEntry.invoke(item);
                    Cut cut = body.cutOfEntry(entry);
                    if (cut == Cut.NOTHING) continue;
                    Matrix4f pose = new Matrix4f()
                            .translation((float) (at[i * 3] - camera.x), (float) (at[i * 3 + 1] - camera.y), (float) (at[i * 3 + 2] - camera.z))
                            .rotate(TURN.set(turn[i * 4], turn[i * 4 + 1], turn[i * 4 + 2], turn[i * 4 + 3]))
                            .scale(scale)
                            .mul((Matrix4f) entryRest.get(entry))
                            .scale(16f);
                    cubes.cube(entryCube.get(entry), pose, pose.normal(new Matrix3f()), entryTexture.get(entry), cut);
                }
                cubes.done();
            }
        } catch (Throwable t) {
            disable(t);
        }
    }

    static void bodies(ClientLevel level, List<RagdollBodies.Body> out) {
        forEachPosed(level, (remainsId, pose, c, at, turn, scale) -> {
            OrientedBox[] boxes = new OrientedBox[c.segments.length];
            Vector3f mid = new Vector3f();
            for (int k = 0; k < c.segments.length; k++) {
                int i = (Integer) poseIndexOf.invoke(pose, c.segments[k]);
                if (i < 0 || i * 3 + 2 >= at.length || i * 4 + 3 >= turn.length) continue;
                float[] b = c.boxes;
                int o = k * 6;
                TURN.set(turn[i * 4], turn[i * 4 + 1], turn[i * 4 + 2], turn[i * 4 + 3]);
                TURN.transform(mid.set((b[o] + b[o + 3]) / 2 * scale, (b[o + 1] + b[o + 4]) / 2 * scale, (b[o + 2] + b[o + 5]) / 2 * scale));
                try {
                    boxes[k] = new OrientedBox(at[i * 3] + mid.x, at[i * 3 + 1] + mid.y, at[i * 3 + 2] + mid.z, TURN.x, TURN.y, TURN.z, TURN.w,
                            (b[o + 3] - b[o]) / 2 * scale, (b[o + 4] - b[o + 1]) / 2 * scale, (b[o + 5] - b[o + 2]) / 2 * scale);
                } catch (IllegalArgumentException e) {
                    boxes[k] = null;
                }
            }
            RagdollBodies.Body b = RagdollBodies.body(MOD, remainsId, null, c.mobId, boxes);
            if (b != null) out.add(b);
        });
    }

    static void collect(ClientLevel level, Map<OtherRagdolls.Key, OtherRagdolls.Body> out) {
        forEachPosed(level, (remainsId, pose, c, at, turn, scale) -> {
            Vec3[] parts = new Vec3[c.segments.length];
            Vector3f mid = new Vector3f();
            for (int k = 0; k < c.segments.length; k++) {
                int i = (Integer) poseIndexOf.invoke(pose, c.segments[k]);
                if (i < 0 || i * 3 + 2 >= at.length || i * 4 + 3 >= turn.length) continue;
                float[] b = c.boxes;
                int o = k * 6;
                TURN.set(turn[i * 4], turn[i * 4 + 1], turn[i * 4 + 2], turn[i * 4 + 3]);
                TURN.transform(mid.set((b[o] + b[o + 3]) / 2 * scale, (b[o + 1] + b[o + 4]) / 2 * scale, (b[o + 2] + b[o + 5]) / 2 * scale));
                parts[k] = new Vec3(at[i * 3] + mid.x, at[i * 3 + 1] + mid.y, at[i * 3 + 2] + mid.z);
            }
            Entity mob = level.getEntity(c.mobId);
            out.put(new OtherRagdolls.Key(MOD, remainsId), new OtherRagdolls.Body(parts, mob instanceof LivingEntity l ? l : null));
        });
    }

    private interface PosedVisitor {
        void corpse(int remainsId, Object pose, Corpse c, double[] at, float[] turn, float scale) throws ReflectiveOperationException;
    }

    private static void forEachPosed(ClientLevel level, PosedVisitor visit) {
        if (level == null || CORPSES.isEmpty() || !available()) return;
        try {
            Iterator<Map.Entry<Integer, Corpse>> it = CORPSES.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Corpse> e = it.next();
                Corpse c = e.getValue();
                if (c.mobId < 0) continue;
                Entity remains = level.getEntity(e.getKey());
                Object data = remains == null ? null : remainsData.get(remains);
                if (!dataClass.isInstance(data)) continue;
                Object list = dataList.get(data), shown = dataShown.get(data);
                if (list == null || shown == null) continue;
                if (c.boxesFor != list) segmentBoxes(c, list);
                if (c.segments.length == 0) continue;
                visit.corpse(e.getKey(), shown, c, (double[]) posePositions.invoke(shown), (float[]) poseRotations.invoke(shown),
                        remainsScale.getFloat(remains) / 16f);
            }
        } catch (Throwable t) {
            disable(t);
        }
    }

    private static void segmentBoxes(Corpse c, Object list) throws ReflectiveOperationException {
        Map<Integer, float[]> bySegment = new java.util.TreeMap<>();
        Vector3f p = new Vector3f();
        for (Object item : (List<?>) listItems.invoke(list)) {
            Object entry = itemEntry.invoke(item), shape = entryShape.get(entry);
            if (shape == null) continue;
            Matrix4f rest = (Matrix4f) entryRest.get(entry);
            float[] box = bySegment.computeIfAbsent((Integer) itemSegment.invoke(item),
                    k -> new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE});
            float[] lo = {shapeMinX.getFloat(shape), shapeMinY.getFloat(shape), shapeMinZ.getFloat(shape)};
            float[] hi = {shapeMaxX.getFloat(shape), shapeMaxY.getFloat(shape), shapeMaxZ.getFloat(shape)};
            for (int corner = 0; corner < 8; corner++) {
                rest.transformPosition((corner & 1) == 0 ? lo[0] : hi[0], (corner & 2) == 0 ? lo[1] : hi[1], (corner & 4) == 0 ? lo[2] : hi[2], p);
                box[0] = Math.min(box[0], p.x);
                box[1] = Math.min(box[1], p.y);
                box[2] = Math.min(box[2], p.z);
                box[3] = Math.max(box[3], p.x);
                box[4] = Math.max(box[4], p.y);
                box[5] = Math.max(box[5], p.z);
            }
        }
        c.segments = new int[bySegment.size()];
        c.boxes = new float[bySegment.size() * 6];
        int k = 0;
        for (Map.Entry<Integer, float[]> s : bySegment.entrySet()) {
            c.segments[k] = s.getKey();
            System.arraycopy(s.getValue(), 0, c.boxes, k * 6, 6);
            k++;
        }
        c.boxesFor = list;
    }

    public static void clear() {
        CORPSES.clear();
        BODIES.clear();
        DYING.clear();
    }
}
