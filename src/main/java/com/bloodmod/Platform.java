package com.bloodmod;

import java.util.ArrayList;
import java.util.List;

public final class Platform {

    private Platform() {}

    public static String minecraftVersion() {
        //? if 1.21.1 {
        /*return net.minecraft.SharedConstants.getCurrentVersion().getName();
        *///?} else {
        return net.minecraft.SharedConstants.getCurrentVersion().name();
        //?}
    }

    //? if fabric {
    public static String loader() {
        return "Fabric";
    }

    public static String modVersion() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(BloodMod.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    public static List<String> modIds() {
        List<String> ids = new ArrayList<>();
        for (net.fabricmc.loader.api.ModContainer c : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) {
            ids.add(c.getMetadata().getId());
        }
        return ids;
    }
    //?} else {
    /*public static String loader() {
        return "NeoForge";
    }

    public static String modVersion() {
        return net.neoforged.fml.ModList.get().getModContainerById(BloodMod.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
    }

    public static List<String> modIds() {
        List<String> ids = new ArrayList<>();
        for (var info : net.neoforged.fml.ModList.get().getMods()) {
            ids.add(info.getModId());
        }
        return ids;
    }
    *///?}
}
