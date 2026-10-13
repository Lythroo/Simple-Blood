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

/**
 * Blood that sticks to mobs (Puddles settings, "On mobs"). A hit paints the outside of the
 * mob's model where the blow landed; the blood rides along as the mob moves, runs down its
 * body, drips off it and dries, or washes off in water and rain.
 *
 * The body's shape is only known while the mob is drawn, so hits wait for the next frame,
 * and the running is worked out there too, once a tick. Canvases go to the surface blood's
 * atlas from the client tick, never from inside a render pass.
 */
public final class MobBlood {
    private MobBlood() {}

    private static final Map<Integer, MobCoat> COATS = new HashMap<>();
    private static final RandomSource RNG = RandomSource.create();
    /** Bloody mobs at once; the oldest beyond this are not painted. */
    private static final int MAX_COATS = 96;
    private static int now;
    private static ClientLevel lastLevel;

    private static SimpleBloodConfig.MobCoatSettings settings() {
        return SimpleBloodClient.getConfig().mobCoat;
    }

    public static boolean has(int entityId) {
        return COATS.containsKey(entityId);
    }

    /** A hit on a mob that bleeds: painted on its body the next time it is drawn. */
    public static void hit(LivingEntity entity, HitContext ctx) {
        splat(entity, ctx, false);
    }

    /** The killing blow: a bigger splash. */
    public static void death(LivingEntity entity, HitContext ctx) {
        splat(entity, ctx, true);
    }

    private static void splat(LivingEntity e, HitContext ctx, boolean death) {
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        if (cfg == null || !cfg.mobCoat.enabled) return;
        if (SimpleBlood.bloodKindOf(e) != BloodKind.LIQUID || !SimpleBlood.shouldEntityTransformToStains(e)) return;
        MobCoat coat = COATS.get(e.getId());
        if (coat == null) {
            if (COATS.size() >= MAX_COATS) return;
            coat = new MobCoat(e.getId(), colourOf(e));
            COATS.put(e.getId(), coat);
        }
        float amount = Math.max(0, cfg.mobCoat.amount) / 100f;
        if (amount <= 0) return;
        float damage = ctx != null ? ctx.damage : 4f;
        // Bigger marks for harder blows; the Amount setting scales them all.
        float size = Math.max(0.5f, Math.min(1.6f, 0.6f + damage / 10f)) * amount * (death ? 1.25f : 1f);
        int units = death || damage >= 8 ? 4 : damage >= 4 ? 3 : 2;
        HitContext.Weapon weapon = ctx != null && cfg.weaponFlavourEnabled() ? ctx.weapon : HitContext.Weapon.NONE;
        Vec3 base = e.position();
        Vec3 centre = base.add(0, e.getBbHeight() * 0.55, 0);
        if (ctx != null && ctx.direction != null && weapon != HitContext.Weapon.EXPLOSION && weapon != HitContext.Weapon.FALL) {
            Vec3 at = ctx.wound != null ? ctx.wound : centre.subtract(ctx.direction.scale(e.getBbWidth() * 0.5));
            coat.pending.add(new MobCoat.Splat(at.subtract(base), ctx.direction, weapon, size, units, now));
        } else {
            // Hurt all over (a fall, an explosion): blotches from a few sides at once.
            for (int i = 0; i < 3; i++) {
                double a = RNG.nextDouble() * Math.PI * 2;
                Vec3 dir = new Vec3(Math.cos(a), -0.3 + RNG.nextDouble() * 0.4, Math.sin(a)).normalize();
                Vec3 at = centre.subtract(dir.scale(e.getBbWidth() * 0.5));
                coat.pending.add(new MobCoat.Splat(at.subtract(base), dir, HitContext.Weapon.NONE, size * 0.7f, units, now));
            }
        }
    }

    /**
     * A badly hurt mob bleeding: with blood on mobs on and a wound on its body, the blood wells
     * up at the wound and runs down the body, and the drops fall from where it runs off.
     * @return false when there is no wound to bleed from (the caller drips the usual way)
     */
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

    // ------------------------------------------------------------------ drawing

    //? if >=1.21.11 {
    /** One mob being drawn: its faces gathered from its own model and the layers over it. */
    private static final class Frame {
        final int entityId;
        final Object state;            // the mob's render state: its layers bring the same one
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

    /**
     * From the blood layer, the first layer of a mob: takes its model's faces, and submits the
     * drawing of its blood. The drawing comes after every layer has been submitted, so by then
     * the layers over the body (wool, a drowned's outer layer, clothes) have added their faces
     * too, and the blood goes on the outermost one the blow meets: wool soaks it up.
     */
    public static void submit(int entityId, Object state, net.minecraft.client.model.geom.ModelPart root, PoseStack pose,
                              net.minecraft.client.renderer.SubmitNodeCollector collector, int light) {
        frame = null;
        if (!COATS.containsKey(entityId)) return;
        // A shader pack draws every mob once more from the sun for its shadows: in that view
        // the faces are not where the hit landed, and the blood casts no shadow worth drawing.
        if (com.simpleblood.compat.ShaderPacks.inShadowPass()) return;
        Frame f = new Frame(entityId, state, SurfaceRenderer.cameraPos(), light);
        ModelFacesOfMob.collect(root, pose, f.camera, f.faces, f.cubes);
        frame = f;
        collector.submitCustomGeometry(new PoseStack(),
                (net.minecraft.client.renderer.rendertype.RenderType) SurfaceRenderer.renderType(),
                (p, vc) -> com.simpleblood.Guard.run(com.simpleblood.Guard.Part.SURFACES, "drawing blood on a mob",
                        () -> drawFrame(f, p, vc)));
    }

    /** From a layer drawing another model over a mob (its own state): that model's faces join the mob's. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void layerModel(net.minecraft.client.model.Model<?> model, Object state, PoseStack pose) {
        Frame f = frame;
        if (f == null || f.state != state) return;
        // A layer's model is posed when it is drawn, after this: pose it now, the same way.
        ((net.minecraft.client.model.Model) model).setupAnim(state);
        ModelFacesOfMob.collect(model.root(), pose, f.camera, f.faces, f.cubes);
    }

    private static void drawFrame(Frame f, PoseStack.Pose pose, VertexConsumer vc) {
        if (frame == f) frame = null;
        MobCoat coat = COATS.get(f.entityId);
        if (coat == null || f.faces.isEmpty()) return;
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
        List<ModelFacesOfMob.Face> posed = ModelFacesOfMob.collectCaught(camera);
        MobCoat coat = COATS.get(entityId);
        if (coat == null || invisible || posed.isEmpty() || com.simpleblood.compat.ShaderPacks.inShadowPass()) return;
        List<Quad> quads = prepare(coat, posed, camera, light);
        if (quads.isEmpty()) return;
        draw(quads, new PoseStack().last(), buffers.getBuffer((net.minecraft.client.renderer.RenderType) SurfaceRenderer.renderType()));
    }
    *///?}

    /** One face's quad ready to draw: corners, canvas tile, light, normal. */
    private record Quad(float[] p, float[] u, float[] v, int light, float nx, float ny, float nz) {}

    private static List<Quad> prepare(MobCoat coat, List<ModelFacesOfMob.Face> posed, Vec3 camera, int light) {
        SimpleBloodConfig.MobCoatSettings s = settings();
        int lifetime = Math.max(1, s.lifetimeSeconds) * 20;
        coat.lastSeen = now;
        if (!coat.pending.isEmpty()) {
            ClientLevel level = Minecraft.getInstance().level;
            Entity e = level == null ? null : level.getEntity(coat.entityId);
            if (e != null) {
                for (MobCoat.Splat sp : coat.pending) {
                    if (now - sp.made() > 40) continue; // never drawn in time: let it go
                    MobCoat.Splat placed = new MobCoat.Splat(sp.at().add(e.position()), sp.dir(), sp.weapon(), sp.size(), sp.units(), sp.made());
                    coat.paint(placed, posed, now, lifetime, RNG);
                }
            }
            coat.pending.clear();
        }
        if (coat.lastSim != now) {
            coat.lastSim = now;
            coat.flow(posed, now, lifetime, RNG, s.runs, s.drips);
        }
        List<Quad> quads = new ArrayList<>();
        float texel = BloodCanvasAtlas.texel();
        for (ModelFacesOfMob.Face f : posed) {
            MobFace canvas = coat.faces.get(f.key);
            if (canvas == null) continue;
            canvas.lastSeen = now;
            if (canvas.count == 0 || canvas.slot < 0) continue;
            // Just off the skin, a little more at a distance where depth precision drops.
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
            quads.add(new Quad(p, u, v, lit, f.nx, f.ny, f.nz));
        }
        return quads;
    }

    private static void draw(List<Quad> quads, PoseStack.Pose pose, VertexConsumer vc) {
        for (Quad q : quads) {
            for (int k = 0; k < 4; k++) {
                vertex(vc, pose, q.p()[k * 3], q.p()[k * 3 + 1], q.p()[k * 3 + 2], q.u()[k], q.v()[k], q.light(), q.nx(), q.ny(), q.nz());
            }
        }
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int light, float nx, float ny, float nz) {
        //? if 1.20.1 {
        /*vc.vertex(pose.pose(), x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
        *///?} else {
        vc.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
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

    // ------------------------------------------------------------------ ticking

    /** Ages, washes and uploads the blood, and lets the drops that ran off fall. */
    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            clear();
            lastLevel = level;
        }
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
                clear();
                return;
            }
            boolean rainWashes = SimpleBloodClient.getConfig().surfaces.rainWashes;
            Iterator<MobCoat> it = COATS.values().iterator();
            while (it.hasNext()) {
                MobCoat coat = it.next();
                Entity e = level.getEntity(coat.entityId);
                if (e == null || e.isRemoved()) {
                    coat.dispose(atlas);
                    it.remove();
                    continue;
                }
                coat.wash = e.isUnderWater() ? 40 : e.isInWater() ? 12 : rainWashes && e.isInWaterOrRain() ? 3 : 0; // not in water here, so rain
                coat.bleed(now, Math.max(1, settings().lifetimeSeconds) * 20, RNG);
                coat.age(now, atlas);
                fall(level, coat);
                if (coat.isEmpty() && now - coat.lastSeen > 100) {
                    coat.dispose(atlas);
                    it.remove();
                }
            }
        }
        for (MobCoat coat : COATS.values()) {
            for (MobFace f : coat.faces.values()) {
                // Drying darkens the blood over time: redraw now and then even when nothing moved.
                if (f.dirty || f.slot < 0 || (now + f.hashCode()) % 20 == 0) f.refresh(atlas, now, opacity);
            }
        }
    }

    /** The drops that ran off a mob this tick fall as blood drops, which land and stain. */
    private static void fall(ClientLevel level, MobCoat coat) {
        if (coat.drips.isEmpty()) return;
        try {
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

    /** All blood off every mob (a new world, /simpleblood clear, the switch turned off). */
    public static void clear() {
        if (COATS.isEmpty()) return;
        BloodCanvasAtlas atlas = BloodCanvasAtlas.exists() ? BloodCanvasAtlas.get() : null;
        for (MobCoat coat : COATS.values()) coat.dispose(atlas);
        COATS.clear();
    }
}
