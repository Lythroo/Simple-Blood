package com.simpleblood;

import java.util.ArrayList;
import java.util.List;

/** What the loader knows: which loader this is, the mod's own version, the other mods present. */
public final class Platform {

    private Platform() {}

    /** The running game version, e.g. {@code 1.21.11}. */
    public static String minecraftVersion() {
        //? if <=1.21.1 {
        /*return net.minecraft.SharedConstants.getCurrentVersion().getName();
        *///?} else {
        return net.minecraft.SharedConstants.getCurrentVersion().name();
        //?}
    }

    //? if fabric {
    public static String loader() {
        return "Fabric";
    }

    /** This mod's version as the loader sees it, e.g. {@code 1.0.0+1.21.11}. */
    public static String modVersion() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(SimpleBlood.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    /** Ids of every loaded mod, in loader order. */
    public static List<String> modIds() {
        List<String> ids = new ArrayList<>();
        for (net.fabricmc.loader.api.ModContainer c : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) {
            ids.add(c.getMetadata().getId());
        }
        return ids;
    }

    /** A mod's display name ({@code alexsmobs} -> its own "Alex's Mobs"), or the id when it has none. */
    public static String modName(String id) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(id)
                .map(c -> c.getMetadata().getName()).orElse(id);
    }
    //?} elif 1.20.1 {
    /*public static String loader() {
        return "NeoForge";
    }

    // 1.20.1 (Forge API): the same ModList under the Forge package.
    public static String modVersion() {
        return net.minecraftforge.fml.ModList.get().getModContainerById(SimpleBlood.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
    }

    public static List<String> modIds() {
        List<String> ids = new ArrayList<>();
        for (var info : net.minecraftforge.fml.ModList.get().getMods()) {
            ids.add(info.getModId());
        }
        return ids;
    }

    public static String modName(String id) {
        return net.minecraftforge.fml.ModList.get().getModContainerById(id)
                .map(c -> c.getModInfo().getDisplayName()).orElse(id);
    }
    *///?} else {
    /*public static String loader() {
        return "NeoForge";
    }

    public static String modVersion() {
        return net.neoforged.fml.ModList.get().getModContainerById(SimpleBlood.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
    }

    public static List<String> modIds() {
        List<String> ids = new ArrayList<>();
        for (var info : net.neoforged.fml.ModList.get().getMods()) {
            ids.add(info.getModId());
        }
        return ids;
    }

    public static String modName(String id) {
        return net.neoforged.fml.ModList.get().getModContainerById(id)
                .map(c -> c.getModInfo().getDisplayName()).orElse(id);
    }
    *///?}
}
