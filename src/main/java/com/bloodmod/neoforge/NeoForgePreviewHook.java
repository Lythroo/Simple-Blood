//? if neoforge && 1.21.1 {
/*package com.bloodmod.neoforge;

import net.neoforged.bus.api.IEventBus;

final class NeoForgePreviewHook {
    private NeoForgePreviewHook() {}

    static void register(IEventBus modBus) {}
}
*///?}

//? if neoforge && >=1.21.11 && <26.2 {
/*package com.bloodmod.neoforge;

import com.bloodmod.gui.ScenePip;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

final class NeoForgePreviewHook {
    private NeoForgePreviewHook() {}

    static void register(IEventBus modBus) {
        modBus.addListener(RegisterPictureInPictureRenderersEvent.class,
                e -> e.register(ScenePip.State.class, ScenePip.Renderer::new));
    }
}
*///?}

//? if neoforge && >=26.2 {
/*package com.bloodmod.neoforge;

import com.bloodmod.gui.ScenePip;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

final class NeoForgePreviewHook {
    private NeoForgePreviewHook() {}

    static void register(IEventBus modBus) {
        modBus.addListener(RegisterPictureInPictureRenderersEvent.class,
                e -> e.register(ScenePip.State.class, ScenePip.Renderer::new));
    }
}
*///?}
