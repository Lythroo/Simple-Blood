//? if fabric && 1.21.1 {
/*package com.bloodmod.fabric;

final class FabricPreviewHook {
    private FabricPreviewHook() {}

    static void register() {}
}
*///?}

//? if fabric && >=1.21.11 && <26.1 {
/*package com.bloodmod.fabric;

import com.bloodmod.gui.ScenePip;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;

final class FabricPreviewHook {
    private FabricPreviewHook() {}

    static void register() {
        SpecialGuiElementRegistry.register(ctx -> new ScenePip.Renderer(ctx.vertexConsumers()));
    }
}
*///?}

//? if fabric && >=26.1 && <26.2 {
package com.bloodmod.fabric;

import com.bloodmod.gui.ScenePip;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;

final class FabricPreviewHook {
    private FabricPreviewHook() {}

    static void register() {
        PictureInPictureRendererRegistry.register(ctx -> new ScenePip.Renderer(ctx.bufferSource()));
    }
}
//?}

//? if fabric && >=26.2 {
/*package com.bloodmod.fabric;

import com.bloodmod.gui.ScenePip;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;

final class FabricPreviewHook {
    private FabricPreviewHook() {}

    static void register() {
        PictureInPictureRendererRegistry.register(ctx -> new ScenePip.Renderer());
    }
}
*///?}
