//? if 1.20.1 {
/*package com.simpleblood.particle;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

import java.util.ArrayList;
import java.util.List;

/^*
 * Our particles in water (blood clouds, sunken chips and flakes), drawn before the water
 * surface instead of with the other particles. Drawn after the water, as particles are, they
 * are hidden by the surface when seen from above. Drawn first, and without writing depth, they
 * are covered by the surface instead, which tints them like anything else in the water.
 * Shader packs also treat a see-through particle like a pane of tinted glass that replaces the
 * tint of the water behind it, so from below the light shafts beyond the surface glare through
 * a cloud drawn last; drawn first, the surface puts it back.
 ^/
public final class UnderwaterParticles {
    private UnderwaterParticles() {}

    private static final List<DrawnBeforeWater> PARTICLES = new ArrayList<>();

    static void add(DrawnBeforeWater particle) {
        PARTICLES.add(particle);
    }

    static void remove(DrawnBeforeWater particle) {
        PARTICLES.remove(particle);
    }

    private static List<DrawnBeforeWater> drawnNow() {
        if (PARTICLES.isEmpty()) return List.of();
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        PARTICLES.removeIf(p -> level == null || !p.isIn(level));
        List<DrawnBeforeWater> now = new ArrayList<>();
        for (DrawnBeforeWater p : PARTICLES) {
            if (p.drawnBeforeWater()) now.add(p);
        }
        return now;
    }

    /^*
     * From the level render hook that draws the blood on blocks, which runs before the water.
     * The pose is the level pose: on 1.20.1 it carries the camera rotation.
     ^/
    public static void draw(PoseStack.Pose pose) {
        List<DrawnBeforeWater> now = drawnNow();
        if (now.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        float partialTick = mc.getFrameTime();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        for (DrawnBeforeWater particle : now) particle.quadBeforeWater(buffer, pose, camera, partialTick);
        CloudType.TYPE.end(buffer, RenderSystem.getVertexSorting());
    }

    /^*
     * See-through entity drawing on the particle atlas that writes colour but not depth, so
     * the water surface behind a cloud is still drawn over it.
     ^/
    private static final class CloudType extends RenderType {
        static final RenderType TYPE = make();

        private CloudType(Runnable setup, Runnable clear) {
            super("simpleblood_underwater", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true, setup, clear);
        }

        private static RenderType make() {
            List<RenderStateShard> shards = List.of(
                    RENDERTYPE_ENTITY_TRANSLUCENT_SHADER,
                    new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_PARTICLES, false, false),
                    TRANSLUCENT_TRANSPARENCY,
                    LEQUAL_DEPTH_TEST,
                    NO_CULL,
                    LIGHTMAP,
                    OVERLAY,
                    COLOR_WRITE);
            return new CloudType(() -> shards.forEach(RenderStateShard::setupRenderState),
                    () -> shards.forEach(RenderStateShard::clearRenderState));
        }
    }
}
*///?}

//? if 1.21.1 {
/*package com.simpleblood.particle;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

import java.util.ArrayList;
import java.util.List;

/^*
 * Our particles in water (blood clouds, sunken chips and flakes), drawn before the water
 * surface instead of with the other particles. Drawn after the water, as particles are, they
 * are hidden by the surface when seen from above. Drawn first, and without writing depth, they
 * are covered by the surface instead, which tints them like anything else in the water.
 * Shader packs also treat a see-through particle like a pane of tinted glass that replaces the
 * tint of the water behind it, so from below the light shafts beyond the surface glare through
 * a cloud drawn last; drawn first, the surface puts it back.
 ^/
public final class UnderwaterParticles {
    private UnderwaterParticles() {}

    private static final List<DrawnBeforeWater> PARTICLES = new ArrayList<>();

    static void add(DrawnBeforeWater particle) {
        PARTICLES.add(particle);
    }

    static void remove(DrawnBeforeWater particle) {
        PARTICLES.remove(particle);
    }

    private static List<DrawnBeforeWater> drawnNow() {
        if (PARTICLES.isEmpty()) return List.of();
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        PARTICLES.removeIf(p -> level == null || !p.isIn(level));
        List<DrawnBeforeWater> now = new ArrayList<>();
        for (DrawnBeforeWater p : PARTICLES) {
            if (p.drawnBeforeWater()) now.add(p);
        }
        return now;
    }

    /^* From the level render hook that draws the blood on blocks, which runs before the water. ^/
    public static void draw(PoseStack.Pose pose) {
        List<DrawnBeforeWater> now = drawnNow();
        if (now.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        for (DrawnBeforeWater particle : now) particle.quadBeforeWater(buffer, pose, camera, partialTick);
        MeshData mesh = buffer.build();
        if (mesh != null) CloudType.TYPE.draw(mesh);
    }

    /^*
     * See-through entity drawing on the particle atlas that writes colour but not depth, so
     * the water surface behind a cloud is still drawn over it.
     ^/
    private static final class CloudType extends RenderType {
        static final RenderType TYPE = make();

        private CloudType(Runnable setup, Runnable clear) {
            super("simpleblood_underwater", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true, setup, clear);
        }

        private static RenderType make() {
            List<RenderStateShard> shards = List.of(
                    RENDERTYPE_ENTITY_TRANSLUCENT_SHADER,
                    new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_PARTICLES, false, false),
                    TRANSLUCENT_TRANSPARENCY,
                    LEQUAL_DEPTH_TEST,
                    NO_CULL,
                    LIGHTMAP,
                    OVERLAY,
                    COLOR_WRITE);
            return new CloudType(() -> shards.forEach(RenderStateShard::setupRenderState),
                    () -> shards.forEach(RenderStateShard::clearRenderState));
        }
    }
}
*///?}

//? if >=1.21.11 && <26.1 {
/*package com.simpleblood.particle;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/^*
 * Our particles in water, drawn before the water surface instead of with the particles (see
 * the 1.21.1 variant of this class for why). Here with a copy of the game pipeline for
 * see-through entities that does not write depth, which Iris is told to draw with the pack
 * program for see-through entities.
 ^/
public final class UnderwaterParticles {
    private UnderwaterParticles() {}

    private static final List<DrawnBeforeWater> PARTICLES = new ArrayList<>();
    private static RenderType type;

    static void add(DrawnBeforeWater particle) {
        PARTICLES.add(particle);
    }

    static void remove(DrawnBeforeWater particle) {
        PARTICLES.remove(particle);
    }

    private static List<DrawnBeforeWater> drawnNow() {
        if (PARTICLES.isEmpty()) return List.of();
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        PARTICLES.removeIf(p -> level == null || !p.isIn(level));
        List<DrawnBeforeWater> now = new ArrayList<>();
        for (DrawnBeforeWater p : PARTICLES) {
            if (p.drawnBeforeWater()) now.add(p);
        }
        return now;
    }

    /^* From the level render hook that draws the blood on blocks, which runs before the water. ^/
    public static void draw(PoseStack.Pose pose) {
        List<DrawnBeforeWater> now = drawnNow();
        if (now.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        for (DrawnBeforeWater particle : now) particle.quadBeforeWater(buffer, pose, camera, partialTick);
        MeshData mesh = buffer.build();
        if (mesh != null) type().draw(mesh);
    }

    private static RenderType type() {
        if (type == null) {
            RenderPipeline pipeline = withoutDepthWrite(RenderPipelines.ENTITY_TRANSLUCENT,
                    com.simpleblood.Ids.mod("pipeline/underwater"));
            com.simpleblood.compat.ShaderPacks.drawAsTranslucentEntity(pipeline);
            type = com.simpleblood.mixin.RenderTypeInvoker.simpleblood$create("simpleblood_underwater",
                    RenderSetup.builder(pipeline)
                            .withTexture("Sampler0", TextureAtlas.LOCATION_PARTICLES)
                            .useLightmap()
                            .useOverlay()
                            .createRenderSetup());
        }
        return type;
    }

    /^* {@code source} as it is, except that it writes no depth. ^/
    private static RenderPipeline withoutDepthWrite(RenderPipeline source, Identifier location) {
        RenderPipeline.Builder b = RenderPipeline.builder()
                .withLocation(location)
                .withVertexShader(source.getVertexShader())
                .withFragmentShader(source.getFragmentShader());
        source.getShaderDefines().values().forEach((name, value) -> {
            try {
                b.withShaderDefine(name, Integer.parseInt(value));
            } catch (NumberFormatException notInt) {
                b.withShaderDefine(name, Float.parseFloat(value));
            }
        });
        source.getShaderDefines().flags().forEach(b::withShaderDefine);
        source.getSamplers().forEach(b::withSampler);
        for (RenderPipeline.UniformDescription u : source.getUniforms()) {
            if (u.textureFormat() != null) b.withUniform(u.name(), u.type(), u.textureFormat());
            else b.withUniform(u.name(), u.type());
        }
        b.withVertexFormat(source.getVertexFormat(), source.getVertexFormatMode());
        source.getBlendFunction().ifPresentOrElse(b::withBlend, b::withoutBlend);
        return b.withCull(source.isCull())
                .withDepthTestFunction(source.getDepthTestFunction())
                .withPolygonMode(source.getPolygonMode())
                .withColorWrite(source.isWriteColor(), source.isWriteAlpha())
                .withColorLogic(source.getColorLogic())
                .withDepthBias(source.getDepthBiasScaleFactor(), source.getDepthBiasConstant())
                .withDepthWrite(false)
                .build();
    }
}
*///?}

//? if >=26.1 {
package com.simpleblood.particle;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

import java.util.ArrayList;
import java.util.List;

public final class UnderwaterParticles {
    private UnderwaterParticles() {}

    private static final List<DrawnBeforeWater> PARTICLES = new ArrayList<>();
    private static RenderType type;

    static void add(DrawnBeforeWater particle) {
        PARTICLES.add(particle);
    }

    static void remove(DrawnBeforeWater particle) {
        PARTICLES.remove(particle);
    }

    private static List<DrawnBeforeWater> drawnNow() {
        if (PARTICLES.isEmpty()) return List.of();
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        PARTICLES.removeIf(p -> level == null || !p.isIn(level));
        List<DrawnBeforeWater> now = new ArrayList<>();
        for (DrawnBeforeWater p : PARTICLES) {
            if (p.drawnBeforeWater()) now.add(p);
        }
        return now;
    }

    public static void submit(OrderedSubmitNodeCollector collector) {
        List<DrawnBeforeWater> now = drawnNow();
        if (now.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Camera camera = com.simpleblood.surface.SurfaceRenderer.camera();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        collector.submitCustomGeometry(new PoseStack(), type(), (pose, consumer) ->
                com.simpleblood.Guard.run(com.simpleblood.Guard.Part.PARTICLES, "drawing blood under water", () -> {
                    for (DrawnBeforeWater particle : now) particle.quadBeforeWater(consumer, pose, camera, partialTick);
                }));
    }

    private static RenderType type() {
        if (type == null) {
            com.simpleblood.compat.ShaderPacks.drawAsTranslucentEntity(CloudPipeline.get());
            type = com.simpleblood.mixin.RenderTypeInvoker.simpleblood$create("simpleblood_underwater",
                    RenderSetup.builder(CloudPipeline.get())
                            .withTexture("Sampler0", TextureAtlas.LOCATION_PARTICLES)
                            .useLightmap()
                            .useOverlay()
                            .sortOnUpload()
                            .createRenderSetup());
        }
        return type;
    }
}
//?}
