//? if >=26.1 && <26.2 {
package com.simpleblood.particle;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;

final class CloudPipeline {
    private CloudPipeline() {}

    private static RenderPipeline pipeline;

    static RenderPipeline get() {
        if (pipeline == null) {
            pipeline = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(com.simpleblood.Ids.mod("pipeline/underwater"))
                    .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withSampler("Sampler1")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
                    .build();
        }
        return pipeline;
    }
}
//?}

//? if >=26.2 && <26.3 {
/*package com.simpleblood.particle;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;

/^*
 * The game pipeline for see-through entities ({@code RenderPipelines.ENTITY_TRANSLUCENT}) rebuilt
 * for {@link UnderwaterParticles}: the same, except that it writes no depth.
 ^/
final class CloudPipeline {
    private CloudPipeline() {}

    private static RenderPipeline pipeline;

    static RenderPipeline get() {
        if (pipeline == null) {
            pipeline = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(com.simpleblood.Ids.mod("pipeline/underwater"))
                    .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
                    .build();
        }
        return pipeline;
    }
}
*///?}

//? if >=26.3 {
/*package com.simpleblood.particle;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;

/^*
 * The game pipeline for see-through entities ({@code RenderPipelines.ENTITY_TRANSLUCENT}) rebuilt
 * for {@link UnderwaterParticles}: the same, except that it writes no depth.
 ^/
final class CloudPipeline {
    private CloudPipeline() {}

    private static RenderPipeline pipeline;

    static RenderPipeline get() {
        if (pipeline == null) {
            pipeline = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                    .withLocation(com.simpleblood.Ids.mod("pipeline/underwater"))
                    .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
                    .build();
        }
        return pipeline;
    }
}
*///?}
