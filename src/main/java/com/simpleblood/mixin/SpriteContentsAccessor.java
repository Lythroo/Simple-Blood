package com.simpleblood.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the CPU-side pixel data of a stitched sprite so the surface engine can read the
 * luminance of the block texture underneath a blood pixel ("follow the block" highlights).
 */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
    @Accessor("originalImage")
    NativeImage simpleblood$originalImage();
}
