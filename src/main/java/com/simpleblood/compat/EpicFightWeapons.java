package com.simpleblood.compat;

import com.simpleblood.HitContext.Weapon;
import com.simpleblood.SimpleBlood;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

public final class EpicFightWeapons {

    private EpicFightWeapons() {}

    private static boolean looked;
    private static Method capability;
    private static Method category;
    private static Method empty;

    private static void lookUp() {
        looked = true;
        try {
            ClassLoader cl = EpicFightWeapons.class.getClassLoader();
            Class<?> caps = Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities", false, cl);
            Class<?> item = Class.forName("yesman.epicfight.world.capabilities.item.CapabilityItem", false, cl);
            capability = caps.getMethod("getItemStackCapability", ItemStack.class);
            category = item.getMethod("getWeaponCategory");
            empty = item.getMethod("isEmpty");
            SimpleBlood.LOGGER.info("Epic Fight found: its weapons wound by their kind");
        } catch (ClassNotFoundException e) {
        } catch (Throwable t) {
            capability = null;
            SimpleBlood.LOGGER.warn("Epic Fight found, but not a version Simple Blood knows; its weapons wound as swords ({})", t.toString());
        }
    }

    public static Weapon of(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if ("epicfight".equals(id.getNamespace())) {
            Weapon own = own(id.getPath());
            if (own != null) return own;
        }
        String cat = category(stack);
        return cat == null ? null : byCategory(cat);
    }

    public static String category(ItemStack stack) {
        if (!looked) lookUp();
        if (capability == null) return null;
        try {
            Object cap = capability.invoke(null, stack);
            if (cap == null || (Boolean) empty.invoke(cap)) return null;
            Object cat = category.invoke(cap);
            if (cat == null) return null;
            return cat instanceof Enum<?> e ? e.name() : cat.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Weapon own(String path) {
        if (path.endsWith("_greatsword")) return Weapon.AXE;
        if (path.endsWith("_spear")) return Weapon.SPEAR;
        if (path.endsWith("_dagger") || path.endsWith("_longsword") || path.endsWith("_tachi")
                || path.equals("uchigatana") || path.equals("bokken")) return Weapon.BLADE;
        if (path.equals("glove")) return Weapon.OTHER;
        return null;
    }

    static Weapon byCategory(String name) {
        String n = name.toUpperCase(java.util.Locale.ROOT);
        if (n.equals("NOT_WEAPON") || n.equals("SHIELD") || n.equals("RANGED")
                || n.equals("HOE") || n.equals("PICKAXE") || n.equals("SHOVEL")) return null;
        if (n.equals("FIST")) return null;
        if (n.contains("GLOVE") || n.contains("GAUNTLET") || n.contains("KNUCKLE")) return Weapon.OTHER;
        if (n.contains("SPEAR") || n.contains("TRIDENT") || n.contains("LANCE") || n.contains("PIKE") || n.contains("JAVELIN")) return Weapon.SPEAR;
        if (n.contains("GREATSWORD") || n.contains("CLAYMORE") || n.contains("AXE") || n.contains("HALBERD")
                || n.contains("CLEAVER")) return Weapon.AXE;
        if (n.contains("MACE") || n.contains("HAMMER") || n.contains("CLUB") || n.contains("FLAIL")) return Weapon.MACE;
        if (n.equals("SWORD")) return null;
        if (n.contains("SWORD") || n.contains("TACHI") || n.contains("GATANA") || n.contains("KATANA") || n.contains("DAGGER")
                || n.contains("KNIFE") || n.contains("BLADE") || n.contains("RAPIER") || n.contains("SABER")
                || n.contains("SCYTHE")) return Weapon.BLADE;
        return null;
    }
}
