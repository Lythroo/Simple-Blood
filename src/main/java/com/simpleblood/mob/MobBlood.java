package com.simpleblood.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simpleblood.BloodColor;
import com.simpleblood.BloodKind;
import com.simpleblood.BloodParticles;
import com.simpleblood.ClientBloodParticleSpawner;
import com.simpleblood.HitContext;
import com.simpleblood.SimpleBlood;
import com.simpleblood.SimpleBloodClient;
import com.simpleblood.SimpleBloodConfig;
import com.simpleblood.particle.BloodParticle;
import com.simpleblood.surface.BloodCanvasAtlas;
import com.simpleblood.surface.SurfaceRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class MobBlood {
    private MobBlood() {}

    private static final Map<Integer, MobCoat> COATS = new HashMap<>();
    private static final boolean RAGDOLL_MODELS = com.simpleblood.Platform.modIds().contains("ragdollified")
            || com.simpleblood.Platform.modIds().contains("saros_ragdoll");
    private static final RandomSource RNG = RandomSource.create();
    private static final int MAX_COATS = 96;
    private static int now;
    private static ClientLevel lastLevel;

    private static SimpleBloodConfig.MobCoatSettings settings() {
        return SimpleBloodClient.getConfig().mobCoat;
    }

    static MobCoat coatOf(int entityId) {
        return COATS.get(entityId);
    }

    static int now() {
        return now;
    }

    static void drawPosed(MobCoat coat, List<ModelFacesOfMob.Face> posed, float[] fade, PoseStack.Pose pose, VertexConsumer vc, Vec3 camera, int light) {
        coat.onRagdoll = now;
        com.simpleblood.Tally.count("coat.ragdoll");
        List<Quad> quads = prepare(coat, posed, fade, camera, light);
        if (!quads.isEmpty()) draw(quads, pose, vc);
    }

    public static boolean has(int entityId) {
        return COATS.containsKey(entityId);
    }

    public static void hit(LivingEntity entity, HitContext ctx) {
        splat(entity, ctx, false);
    }

    public static void death(LivingEntity entity, HitContext ctx) {
        splat(entity, ctx, true);
        MobCoat coat = COATS.get(entity.getId());
        if (coat != null) coat.onRagdoll = now;
    }

    private static void splat(LivingEntity e, HitContext ctx, boolean death) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.mobCoat.enabled || !com.simpleblood.SimpleBloodAPI.takesBloodOnBody(e)) return;
        if (SimpleBlood.bloodKindOf(e) != BloodKind.LIQUID || !SimpleBlood.shouldEntityTransformToStains(e)) return;
        MobCoat coat = COATS.get(e.getId());
        if (coat == null) {
            if (COATS.size() >= MAX_COATS) return;
            coat = new MobCoat(e.getId(), colourOf(e));
            COATS.put(e.getId(), coat);
        }
        float amount = Math.max(0, cfg.mobCoat.amount) / 100f;
        if (amount <= 0) return;
        com.simpleblood.Tally.count("coat.splat");
        coat.lastPos = e.position();
        float damage = ctx != null ? ctx.damage : 4f;
        float size = Math.max(0.5f, Math.min(1.6f, 0.6f + damage / 10f)) * amount * (death ? 1.25f : 1f);
        int units = death || damage >= 8 ? 4 : damage >= 4 ? 3 : 2;
        HitContext.Weapon weapon = ctx != null && cfg.weaponFlavourEnabled() ? ctx.style : HitContext.Weapon.NONE;
        Vec3 base = e.position();
        Vec3 centre = base.add(0, e.getBbHeight() * 0.55, 0);
        if (ctx != null && ctx.direction != null && weapon != HitContext.Weapon.EXPLOSION && weapon != HitContext.Weapon.FALL) {
            Vec3 at = ctx.wound != null ? ctx.wound : centre.subtract(ctx.direction.scale(e.getBbWidth() * 0.5));
            if (weapon == HitContext.Weapon.SPEAR && (damage >= 5 || ctx.crit || death)) {
                Vec3 exit = ctx.exitPoint();
                exit = new Vec3(exit.x, at.y, exit.z);
                coat.pending.add(new MobCoat.Splat(exit.subtract(base), ctx.direction.scale(-1), HitContext.Weapon.NONE,
                        size * 0.6f, units, now));
                com.simpleblood.Tally.count("coat.exit");
            }
            coat.pending.add(new MobCoat.Splat(at.subtract(base), ctx.direction, weapon, size, units, now));
        } else {
            for (int i = 0; i < 3; i++) {
                double a = RNG.nextDouble() * Math.PI * 2;
                Vec3 dir = new Vec3(Math.cos(a), -0.3 + RNG.nextDouble() * 0.4, Math.sin(a)).normalize();
                Vec3 at = centre.subtract(dir.scale(e.getBbWidth() * 0.5));
                coat.pending.add(new MobCoat.Splat(at.subtract(base), dir, HitContext.Weapon.NONE, size * 0.7f, units, now));
            }
        }
    }

    public static int coatPixels(int entityId) {
        MobCoat coat = COATS.get(entityId);
        if (coat == null) return 0;
        int n = 0;
        for (MobFace f : coat.faces.values()) n += f.count;
        return n;
    }

    public static List<String> describeCoat(int entityId) {
        MobCoat coat = COATS.get(entityId);
        List<String> out = new ArrayList<>();
        if (coat == null) return out;
        ClientLevel level = Minecraft.getInstance().level;
        Entity e = level == null ? null : level.getEntity(entityId);
        Vec3 feet = e == null ? Vec3.ZERO : e.position();
        for (Map.Entry<Object, MobFace> m : coat.faces.entrySet()) {
            if (m.getValue().count == 0) continue;
            ModelFacesOfMob.Face drawn = null;
            if (coat.lastFaces != null) for (ModelFacesOfMob.Face f : coat.lastFaces) if (f.key.equals(m.getKey())) { drawn = f; break; }
            if (drawn == null) {
                out.add(String.format(java.util.Locale.ROOT, "%3d px  not drawn  (%s)", m.getValue().count, m.getKey()));
                continue;
            }
            Vec3 c = drawn.at(0.5, 0.5);
            Vec3 g = new Vec3(drawn.wx(1) - drawn.wx(0), drawn.wy(1) - drawn.wy(0), drawn.wz(1) - drawn.wz(0))
                    .cross(new Vec3(drawn.wx(3) - drawn.wx(0), drawn.wy(3) - drawn.wy(0), drawn.wz(3) - drawn.wz(0))).normalize();
            out.add(String.format(java.util.Locale.ROOT, "%3d px  at (%+.2f %+.2f %+.2f)  facing (%+.2f %+.2f %+.2f)  wound (%+.2f %+.2f %+.2f)  %dx%d%s",
                    m.getValue().count, c.x - feet.x, c.y - feet.y, c.z - feet.z, drawn.wnx, drawn.wny, drawn.wnz,
                    g.x, g.y, g.z, drawn.pw, drawn.ph, ""));
            StringBuilder corners = new StringBuilder("        corners");
            for (int k = 0; k < 4; k++) {
                corners.append(String.format(java.util.Locale.ROOT, " (%+.3f %+.3f %+.3f | %.4f %.4f)",
                        drawn.wx(k) - feet.x, drawn.wy(k) - feet.y, drawn.wz(k) - feet.z, drawn.u[k], drawn.v[k]));
            }
            out.add(corners.toString());
        }
        return out;
    }

    public static int coatStrokes(int entityId) {
        MobCoat coat = COATS.get(entityId);
        return coat == null ? 0 : coat.strokes;
    }

    public static int coatUnits(int entityId) {
        MobCoat coat = COATS.get(entityId);
        if (coat == null) return 0;
        int n = 0;
        for (MobFace f : coat.faces.values()) {
            for (int y = 0; y < f.ph; y++) for (int x = 0; x < f.pw; x++) n += f.depthAt(x, y);
        }
        return n;
    }

    public static int corpseCount() {
        return CORPSES.size();
    }

    public static boolean watching(int entityId) {
        return COATS.containsKey(entityId) || WoundAnchors.wants(entityId) || CORPSES.containsKey(entityId);
    }

    private static final class Corpse {
        final long since;
        final int colour;
        Vec3 pos;
        float width, height;
        List<Vec3> low;
        List<Vec3> seeds;
        int bled;

        Corpse(LivingEntity e, int colour) {
            this.since = System.currentTimeMillis();
            this.colour = colour;
            this.pos = e.position();
            this.width = e.getBbWidth();
            this.height = e.getBbHeight();
        }
    }

    private static final Map<Integer, Corpse> CORPSES = new HashMap<>();

    public static void corpse(LivingEntity e) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.surfaces.enabled || !cfg.surfaces.corpsePools) return;
        if (SimpleBlood.bloodKindOf(e) != BloodKind.LIQUID || !SimpleBlood.shouldEntityTransformToStains(e)) return;
        if (!com.simpleblood.SimpleBloodAPI.leavesPool(e)) return;
        if (CORPSES.size() < 64) CORPSES.put(e.getId(), new Corpse(e, colourOf(e)));
    }

    public static void poolNow(LivingEntity e) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.surfaces.enabled || !cfg.surfaces.corpsePools) return;
        if (!(e.level() instanceof ClientLevel level)) return;
        Corpse c = new Corpse(e, colourOf(e));
        settle(level, c);
        for (int i = 0; i < 12; i++) {
            c.bled = i;
            bleed(level, c);
        }
    }

    private static void lieDown(int entityId, List<ModelFacesOfMob.Face> faces) {
        Corpse c = CORPSES.get(entityId);
        if (c == null || faces.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(entityId) instanceof LivingEntity body) || body.deathTime < 6) return;
        double floor = body.getY();
        Map<Long, Vec3> low = new HashMap<>();
        for (ModelFacesOfMob.Face f : faces) {
            for (int k = 0; k < 4; k++) {
                if (f.wy(k) > floor + 0.35) continue;
                long cell = ((long) Math.floor(f.wx(k) * 8) << 32) ^ ((long) Math.floor(f.wz(k) * 8) & 0xffffffffL);
                low.putIfAbsent(cell, new Vec3(f.wx(k), floor, f.wz(k)));
            }
        }
        if (!low.isEmpty()) c.low = new ArrayList<>(low.values());
    }

    private static void tickCorpses(ClientLevel level) {
        if (CORPSES.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Corpse>> it = CORPSES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Corpse> entry = it.next();
            Corpse c = entry.getValue();
            if (!(level.getEntity(entry.getKey()) instanceof LivingEntity body) || body.isRemoved()) {
                if (c.seeds == null) {
                    settle(level, c);
                    for (int i = 0; i < 3; i++) bleed(level, c);
                }
                it.remove();
                continue;
            }
            c.pos = body.position();
            if (c.seeds == null) {
                if ((body.onGround() && body.deathTime >= 8) || now - c.since > 3000) settle(level, c);
                continue;
            }
            bleed(level, c);
        }
    }

    private static void settle(ClientLevel level, Corpse c) {
        com.simpleblood.Tally.count("corpse.settle");
        List<Vec3> points = c.low != null ? c.low : List.of();
        double cx = c.pos.x, cz = c.pos.z;
        if (!points.isEmpty()) {
            cx = 0;
            cz = 0;
            for (Vec3 p : points) { cx += p.x; cz += p.z; }
            cx /= points.size();
            cz /= points.size();
        }
        c.seeds = new ArrayList<>(List.of(new Vec3(cx, c.pos.y, cz)));
        pour(level, c.seeds.get(0), c.colour, 10);
    }

    private static void bleed(ClientLevel level, Corpse c) {
        com.simpleblood.Tally.count("corpse.bleed");
        float size = Math.max(0.6f, Math.min(1.8f, c.width * c.height));
        Vec3 middle = c.seeds.get(0);
        List<Vec3> points = c.low != null ? c.low : List.of();
        double reach = Math.min(1.15, 0.35 + c.bled * 0.08);
        double radius = 0.35 + c.width * 0.6;
        for (int k = 0; k < 3; k++) {
            Vec3 to;
            if (!points.isEmpty()) {
                to = points.get(RNG.nextInt(points.size()));
            } else {
                double a = RNG.nextDouble() * Math.PI * 2;
                to = middle.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            }
            double f = reach * (0.75 + RNG.nextDouble() * 0.3);
            Vec3 at = new Vec3(middle.x + (to.x - middle.x) * f, middle.y, middle.z + (to.z - middle.z) * f);
            pour(level, at, c.colour, Math.round((6 + RNG.nextInt(5)) * size * 0.8f));
        }
        pour(level, middle, c.colour, 3);
        c.bled++;
    }

    private static void pour(ClientLevel level, Vec3 at, int colour, int pixels) {
        net.minecraft.core.BlockPos ground = net.minecraft.core.BlockPos.containing(at.x, at.y - 0.05, at.z);
        net.minecraft.world.phys.shapes.VoxelShape shape = level.getBlockState(ground).getCollisionShape(level, ground);
        com.simpleblood.surface.BloodSurfaces.Hit hit = !shape.isEmpty()
                ? new com.simpleblood.surface.BloodSurfaces.Hit(ground, net.minecraft.core.Direction.UP,
                        new Vec3(at.x, ground.getY() + shape.max(net.minecraft.core.Direction.Axis.Y), at.z))
                : com.simpleblood.surface.BloodSurfaces.trace(level, at.add(0, 0.4, 0), at.add(0, -0.8, 0));
        if (hit != null) com.simpleblood.surface.BloodSurfaces.deposit(level, hit, colour, Math.max(1, pixels), new Vec3(0, -1, 0));
    }

    public static void steppedIn(LivingEntity e, Vec3 foot, int colour, int wetPixels) {
        if (!catchesDrops() || !com.simpleblood.SimpleBloodAPI.takesBloodOnBody(e)) return;
        com.simpleblood.Tally.count("feet.stained");
        Vec3 centre = e.position();
        Vec3 out = new Vec3(foot.x - centre.x, 0, foot.z - centre.z);
        if (out.lengthSqr() < 1.0e-4) {
            double a = RNG.nextDouble() * Math.PI * 2;
            out = new Vec3(Math.cos(a), 0, Math.sin(a));
        }
        out = out.normalize();
        int splats = wetPixels >= 6 ? 2 : 1;
        for (int i = 0; i < splats; i++) {
            Vec3 side = new Vec3(-out.z, 0, out.x).scale((RNG.nextDouble() - 0.5) * 0.2);
            Vec3 at = foot.add(side).add(0, 0.04 + RNG.nextDouble() * Math.min(0.3, e.getBbHeight() * 0.15), 0);
            landed(e, at, out.scale(-1), colour == 0 ? 0x010101 : colour, 0.7f);
        }
    }

    private static boolean inWaterCauldron(ClientLevel level, Entity e) {
        return level.getBlockState(e.blockPosition()).is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON);
    }

    public static void washAround(ClientLevel level, Vec3 at, double radius, int ticks) {
        if (COATS.isEmpty()) return;
        for (MobCoat coat : COATS.values()) {
            Entity e = level.getEntity(coat.entityId);
            if (e != null && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(at) <= radius * radius) {
                com.simpleblood.Tally.count("coat.washed");
                coat.rinsed = Math.max(coat.rinsed, ticks);
            }
        }
    }

    public static boolean catchesDrops() {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        return cfg != null && cfg.mobCoat.enabled && cfg.mobCoat.amount > 0;
    }

    public static void landed(LivingEntity e, Vec3 at, Vec3 dir, int colour, float size) {
        if (!catchesDrops() || !com.simpleblood.SimpleBloodAPI.takesBloodOnBody(e)) return;
        com.simpleblood.Tally.count("coat.landed");
        MobCoat coat = COATS.get(e.getId());
        if (coat == null) {
            if (COATS.size() >= MAX_COATS) return;
            coat = new MobCoat(e.getId(), colourOf(e));
            COATS.put(e.getId(), coat);
        }
        if (coat.pending.size() >= 48) return;
        float amount = settings().amount / 100f;
        Vec3 from = at.subtract(dir.scale(0.3)).subtract(e.position());
        coat.pending.add(new MobCoat.Splat(from, dir, HitContext.Weapon.NONE, size * amount, 2, now, colour, true));
    }

    public static boolean seep(LivingEntity e, int units) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.mobCoat.enabled) return false;
        MobCoat coat = COATS.get(e.getId());
        if (coat == null || coat.woundFace == null) return false;
        coat.seep = Math.min(MobFace.MAX_DEPTH, coat.seep + units);
        return true;
    }

    private static int colourOf(LivingEntity e) {
        BloodColor.Color c = BloodColor.getBloodColor(e);
        int rgb = ((int) (c.red * 255) << 16) | ((int) (c.green * 255) << 8) | (int) (c.blue * 255);
        return SimpleBlood.bloodGlows(e) ? rgb | BloodColor.GLOW_BIT : rgb;
    }

    //? if >=1.21.11 {
    private static final class Frame {
        final int entityId;
        final Object state;
        final Vec3 camera;
        final int light;
        final List<ModelFacesOfMob.Face> faces = new ArrayList<>();
        final int[] cubes = new int[1];

        Frame(int entityId, Object state, Vec3 camera, int light) {
            this.entityId = entityId;
            this.state = state;
            this.camera = camera;
            this.light = light;
        }
    }

    private static Frame frame;

    public static void submit(int entityId, Object state, net.minecraft.client.model.geom.ModelPart root, PoseStack pose,
                              net.minecraft.client.renderer.SubmitNodeCollector collector, int light, Object texture) {
        frame = null;
        boolean coated = COATS.containsKey(entityId);
        if (!coated && !WoundAnchors.wants(entityId) && !CORPSES.containsKey(entityId)) return;
        if (com.simpleblood.compat.ShaderPacks.inShadowPass()) return;
        Frame f = new Frame(entityId, state, SurfaceRenderer.cameraPos(), light);
        com.simpleblood.compat.EmfPoses.pose(state, root);
        ModelFacesOfMob.collect(root, pose, f.camera, f.faces, f.cubes);
        textured(f.faces, 0, texture);
        WoundAnchors.posed(entityId, f.faces);
        lieDown(entityId, f.faces);
        if (!coated) return;
        if (ModelFacesOfMob.shaped) COATS.get(entityId).noteShapes(f.faces);
        frame = f;
        collector.submitCustomGeometry(new PoseStack(),
                (net.minecraft.client.renderer.rendertype.RenderType) SurfaceRenderer.renderType(),
                (p, vc) -> com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on a mob",
                        () -> drawFrame(f, p, vc)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void layerModel(net.minecraft.client.model.Model<?> model, Object state, PoseStack pose, Object texture) {
        Frame f = frame;
        if (f == null || f.state != state) return;
        ((net.minecraft.client.model.Model) model).setupAnim(state);
        com.simpleblood.compat.EmfPoses.pose(state, model.root());
        int from = f.faces.size();
        ModelFacesOfMob.collect(model.root(), pose, f.camera, f.faces, f.cubes);
        textured(f.faces, from, texture);
    }

    private static void textured(List<ModelFacesOfMob.Face> faces, int from, Object texture) {
        MobTextures.Alpha alpha = MobTextures.of(texture);
        if (alpha == null) return;
        for (int i = from; i < faces.size(); i++) faces.get(i).alpha = alpha;
    }

    static void drawOnRagdoll(int entityId, List<ModelFacesOfMob.Face> drawn, PoseStack.Pose pose, VertexConsumer vc, int light) {
        MobCoat coat = COATS.get(entityId);
        if (coat == null || coat.shapes.isEmpty()) return;
        List<ModelFacesOfMob.Face> posed = new ArrayList<>(drawn.size());
        for (ModelFacesOfMob.Face face : drawn) {
            Object key = face.shape == 0 ? null : coat.shapes.get(face.shape);
            if (key == null) continue;
            face.key = key;
            posed.add(face);
        }
        if (posed.isEmpty()) return;
        com.simpleblood.Tally.count("coat.ragdoll");
        coat.onRagdoll = now;
        List<Quad> quads = prepare(coat, posed, SurfaceRenderer.cameraPos(), light);
        if (!quads.isEmpty()) draw(quads, pose, vc);
    }

    private static void drawFrame(Frame f, PoseStack.Pose pose, VertexConsumer vc) {
        if (frame == f) frame = null;
        MobCoat coat = COATS.get(f.entityId);
        if (coat == null || f.faces.isEmpty()) return;
        coat.lastFaces = f.faces;
        List<Quad> quads = prepare(coat, f.faces, f.camera, f.light);
        if (!quads.isEmpty()) draw(quads, pose, vc);
    }
    //?}

    //? if <=1.21.1 {
    /*/^*
     * Draws the blood on a mob once it and its layers are drawn, over the faces caught on the
     * way (its own model, wool, a drowned's outer layer, clothes, armour): the blood goes on
     * the outermost one the blow meets.
     ^/
    public static void render(int entityId, boolean invisible, net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        Vec3 camera = SurfaceRenderer.cameraPos();
        // Drawn without the game's model parts (Epic Fight's animated meshes): no faces, so no
        // blood on the body (a known issue); its blood on the ground and on weapons is unchanged.
        List<ModelFacesOfMob.Face> posed = ModelFacesOfMob.collectCaught(camera);
        if (!com.simpleblood.compat.ShaderPacks.inShadowPass()) {
            WoundAnchors.posed(entityId, posed);
            lieDown(entityId, posed);
        }
        MobCoat coat = COATS.get(entityId);
        if (coat == null || invisible || posed.isEmpty() || com.simpleblood.compat.ShaderPacks.inShadowPass()) return;
        coat.lastFaces = posed;
        List<Quad> quads = prepare(coat, posed, camera, light);
        if (quads.isEmpty()) return;
        draw(quads, new PoseStack().last(), buffers.getBuffer((net.minecraft.client.renderer.RenderType) SurfaceRenderer.renderType()));
    }
    *///?}

    record Quad(float[] p, float[] u, float[] v, int light, float nx, float ny, float nz, int alpha) {}

    private static List<Quad> prepare(MobCoat coat, List<ModelFacesOfMob.Face> posed, Vec3 camera, int light) {
        return prepare(coat, posed, null, camera, light);
    }

    private static List<Quad> prepare(MobCoat coat, List<ModelFacesOfMob.Face> posed, float[] fade, Vec3 camera, int light) {
        SimpleBloodConfig.MobCoatSettings s = settings();
        int lifetime = Math.max(1, s.lifetimeSeconds) * 20;
        coat.lastSeen = now;
        if (!coat.pending.isEmpty()) {
            ClientLevel level = Minecraft.getInstance().level;
            Entity e = level == null ? null : level.getEntity(coat.entityId);
            Vec3 origin = e != null ? e.position() : coat.lastPos;
            if (origin != null) {
                for (MobCoat.Splat sp : coat.pending) {
                    if (now - sp.made() > 40) continue;
                    MobCoat.Splat placed = new MobCoat.Splat(sp.at().add(origin), sp.dir(), sp.weapon(), sp.size(), sp.units(), sp.made(), sp.colour(), sp.drop());
                    coat.paint(placed, posed, now, lifetime, RNG);
                }
            }
            coat.pending.clear();
        }
        if (coat.lastSim != now) {
            coat.lastSim = now;
            coat.flow(posed, now, lifetime, RNG, s.runs, s.drips);
        }
        return quadsOf(coat, posed, fade, light, now);
    }

    static List<Quad> quadsOf(MobCoat coat, List<ModelFacesOfMob.Face> posed, int light, int stamp) {
        return quadsOf(coat, posed, null, light, stamp);
    }

    static List<Quad> quadsOf(MobCoat coat, List<ModelFacesOfMob.Face> posed, float[] fade, int light, int stamp) {
        List<Quad> quads = new ArrayList<>();
        float texel = BloodCanvasAtlas.texel();
        for (int fi = 0; fi < posed.size(); fi++) {
            ModelFacesOfMob.Face f = posed.get(fi);
            MobFace canvas = coat.faces.get(f.key);
            if (canvas == null) continue;
            canvas.lastSeen = stamp;
            if (canvas.count == 0 || canvas.slot < 0) continue;
            int alpha = fade == null ? 255 : Math.round(255 * Math.max(0f, Math.min(1f, fade[fi])));
            if (alpha <= 0) continue;
            double cx = f.p[0], cy = f.p[1], cz = f.p[2];
            float lift = 0.002f + (float) Math.sqrt(cx * cx + cy * cy + cz * cz) * 0.0001f;
            float[] p = new float[12];
            for (int k = 0; k < 4; k++) {
                p[k * 3] = f.p[k * 3] + f.nx * lift;
                p[k * 3 + 1] = f.p[k * 3 + 1] + f.ny * lift;
                p[k * 3 + 2] = f.p[k * 3 + 2] + f.nz * lift;
            }
            float u0 = BloodCanvasAtlas.u0(canvas.slot), v0 = BloodCanvasAtlas.v0(canvas.slot);
            float[] u = new float[4], v = new float[4];
            for (int k = 0; k < 4; k++) {
                u[k] = u0 + f.s[k] * canvas.pw * texel;
                v[k] = v0 + f.t[k] * canvas.ph * texel;
            }
            int lit = canvas.glow > 0 ? withBlockLight(light, canvas.glow) : light;
            quads.add(new Quad(p, u, v, lit, f.nx, f.ny, f.nz, alpha));
        }
        return quads;
    }

    static void draw(List<Quad> quads, PoseStack.Pose pose, VertexConsumer vc) {
        for (Quad q : quads) {
            for (int k = 0; k < 4; k++) {
                vertex(vc, pose, q.p()[k * 3], q.p()[k * 3 + 1], q.p()[k * 3 + 2], q.u()[k], q.v()[k], q.light(), q.nx(), q.ny(), q.nz(), q.alpha());
            }
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int light, float nx, float ny, float nz, int alpha) {
        //? if 1.20.1 {
        /*vc.vertex(pose.pose(), x, y, z)
                .color(255, 255, 255, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
        *///?} else {
        vc.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
        //?}
    }

    private static int withBlockLight(int packed, int level) {
        int block = (packed >> 4) & 0xF, sky = (packed >> 20) & 0xF;
        return (Math.max(block, level) << 4) | (sky << 20);
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            clear();
            lastLevel = level;
        }
        if (level != null) tickCorpses(level);
        if (level != null) noteBodies(level);
        RigidCoats.tick();
        ModelFacesOfMob.shaped = RAGDOLL_MODELS;
        if (level == null || COATS.isEmpty()) return;
        //? if 1.20.1 {
        /*boolean still = mc.isPaused();
        *///?} else {
        boolean still = mc.isPaused() || !level.tickRateManager().runsNormally();
        //?}
        BloodCanvasAtlas atlas = BloodCanvasAtlas.get();
        float opacity = SimpleBloodClient.getConfig().surfaces.opacity / 100f;
        if (!still) {
            now++;
            if (!settings().enabled) {
                clearCoats();
                return;
            }
            boolean rainWashes = SimpleBloodClient.getConfig().surfaces.rainWashes;
            Iterator<MobCoat> it = COATS.values().iterator();
            while (it.hasNext()) {
                MobCoat coat = it.next();
                Entity e = level.getEntity(coat.entityId);
                boolean onRagdoll = now - coat.onRagdoll < 40;
                if ((e == null || e.isRemoved()) && !onRagdoll) {
                    coat.dispose(atlas);
                    it.remove();
                    continue;
                }
                if (e == null || e.isRemoved()) coat.wash = 0;
                else coat.wash = e.isUnderWater() ? 40 : e.isInWater() || inWaterCauldron(level, e) ? 12 : rainWashes && e.isInWaterOrRain() ? 3 : 0;
                if (coat.rinsed > 0) {
                    coat.rinsed--;
                    coat.wash = Math.max(coat.wash, 40);
                }
                coat.bleed(now, Math.max(1, settings().lifetimeSeconds) * 20, RNG);
                coat.age(now, atlas);
                fall(level, coat, e);
                if (coat.isEmpty() && now - coat.lastSeen > 100) {
                    coat.dispose(atlas);
                    it.remove();
                }
            }
        }
        for (MobCoat coat : COATS.values()) {
            for (MobFace f : coat.faces.values()) {
                if (f.dirty || f.slot < 0 || (now + f.hashCode()) % 20 == 0) f.refresh(atlas, now, opacity);
            }
        }
    }

    private static void fall(ClientLevel level, MobCoat coat, Entity e) {
        if (coat.drips.isEmpty()) return;
        try {
            if (e != null && !e.isRemoved()) BloodParticle.carryMotionOf(e);
            for (MobCoat.Drip d : coat.drips) {
                int c = d.colour();
                BloodParticle.setCurrentBloodColor(new BloodColor.Color(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f));
                BloodParticle.setCurrentKind(BloodKind.LIQUID);
                BloodParticle.setGlowing((c & BloodColor.GLOW_BIT) != 0);
                BloodParticle.setPaintsSurfaces(true);
                BloodParticle.setShouldTransformToFog(true);
                BloodParticle.setBallistic(true, 1.0f);
                ClientBloodParticleSpawner.emit(level, BloodParticles.BLOOD_DROP, d.at().x, d.at().y - 0.02, d.at().z, 0, -0.02, 0);
            }
        } finally {
            BloodParticle.resetSpawnState();
            coat.drips.clear();
        }
    }

    public record Body(LivingEntity entity, net.minecraft.world.phys.AABB box) {}

    private static volatile List<Body> bodies = List.of();
    private static volatile boolean bodiesWanted;

    public static List<Body> bodies() {
        bodiesWanted = true;
        return bodies;
    }

    private static void noteBodies(ClientLevel level) {
        Entity player = Minecraft.getInstance().player;
        if (!bodiesWanted || player == null || !catchesDrops()) {
            if (!bodies.isEmpty()) bodies = List.of();
            return;
        }
        List<Body> near = new ArrayList<>();
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof LivingEntity living && !e.isRemoved() && e.distanceToSqr(player) < 64 * 64) {
                near.add(new Body(living, e.getBoundingBox()));
            }
        }
        bodies = near;
    }

    public static void clear() {
        bodies = List.of();
        RigidCoats.clear();
        WoundAnchors.clear();
        CORPSES.clear();
        clearCoats();
    }

    private static void clearCoats() {
        if (COATS.isEmpty()) return;
        BloodCanvasAtlas atlas = BloodCanvasAtlas.exists() ? BloodCanvasAtlas.get() : null;
        for (MobCoat coat : COATS.values()) coat.dispose(atlas);
        COATS.clear();
    }
}
