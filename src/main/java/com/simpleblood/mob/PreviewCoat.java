package com.simpleblood.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simpleblood.HitContext;
import com.simpleblood.surface.BloodCanvasAtlas;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.BiConsumer;

public final class PreviewCoat {

    private final MobCoat coat;
    private final RandomSource rng = RandomSource.create(11);
    private int now;

    public PreviewCoat(int colour) {
        this.coat = new MobCoat(-1, colour);
    }

    public void hit(Vec3 at, Vec3 dir, HitContext.Weapon weapon, float size, int units, int colour) {
        coat.pending.add(new MobCoat.Splat(at, dir, weapon, size, units, now, colour == 0 ? 0x010101 : colour, false));
    }

    public void seep(int units) {
        if (coat.woundFace != null) coat.seep = Math.min(MobFace.MAX_DEPTH, coat.seep + units);
    }

    public void takeDrips(BiConsumer<Vec3, Integer> out) {
        for (MobCoat.Drip d : coat.drips) out.accept(d.at(), d.colour() & 0xFFFFFF);
        coat.drips.clear();
    }

    public void tick(int lifetime, float opacity) {
        now++;
        coat.bleed(now, lifetime, rng);
        BloodCanvasAtlas atlas = BloodCanvasAtlas.get();
        coat.age(now, atlas);
        for (MobFace f : coat.faces.values()) {
            if (f.dirty || f.slot < 0 || (now + f.hashCode()) % 20 == 0) f.refresh(atlas, now, opacity);
        }
    }

    public Object prepare(ModelPart root, PoseStack pose, int lifetime, boolean runs, boolean drips, int light) {
        List<ModelFacesOfMob.Face> faces = ModelFacesOfMob.collectModel(root, pose);
        if (!coat.pending.isEmpty()) {
            for (MobCoat.Splat sp : coat.pending) coat.paint(sp, faces, now, lifetime, rng);
            coat.pending.clear();
        }
        if (coat.lastSim != now) {
            coat.lastSim = now;
            coat.flow(faces, now, lifetime, rng, runs, drips);
        }
        return MobBlood.quadsOf(coat, faces, light, now);
    }

    @SuppressWarnings("unchecked")
    public void draw(Object quads, PoseStack.Pose pose, VertexConsumer vc) {
        if (quads instanceof List<?> list && !list.isEmpty()) MobBlood.draw((List<MobBlood.Quad>) list, pose, vc);
    }

    public void dispose() {
        coat.dispose(BloodCanvasAtlas.exists() ? BloodCanvasAtlas.get() : null);
    }
}
