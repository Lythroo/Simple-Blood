// Registers the settings screen's 3D diorama as a picture-in-picture GUI element on Fabric.
// The registry moved between game generations, so each variant is a complete top-level block
// (Stonecutter cannot nest commented-out blocks). 1.21.1 draws the scene directly and needs
// nothing here.

//? if fabric && <=1.21.1 {
/*package com.simpleblood.fabric;

final class FabricPreviewHook {
    private FabricPreviewHook() {}

    static void register() {}
}
*///?}

//? if fabric && >=1.21.11 && <26.1 {
/*package com.simpleblood.fabric;

import com.simpleblood.gui.ScenePip;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;

final class FabricPreviewHook {
    private FabricPreviewHook() {}

    static void register() {
        SpecialGuiElementRegistry.register(ctx -> new ScenePip.Renderer(ctx.vertexConsumers()));
    }
}
*///?}

//? if fabric && >=26.1 && <26.2 {
package com.simpleblood.fabric;

import com.simpleblood.gui.ScenePip;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;

final class FabricPreviewHook {
    private FabricPreviewHook() {}

    static void register() {
        PictureInPictureRendererRegistry.register(ctx -> new ScenePip.Renderer(ctx.bufferSource()));
    }
}
//?}

//? if fabric && >=26.2 {
/*package com.simpleblood.fabric;

import com.simpleblood.gui.ScenePip;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;

final class FabricPreviewHook {
    private FabricPreviewHook() {}

    static void register() {
        PictureInPictureRendererRegistry.register(ctx -> new ScenePip.Renderer());
    }
}
*///?}
