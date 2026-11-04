package com.simpleblood;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WeaponKinds {

    private WeaponKinds() {}

    public record Kind(HitContext.Weapon weapon, How how) {}

    public enum How {
        EPIC_FIGHT("simpleblood.weapon_how.epic_fight"), TAG("simpleblood.weapon_how.item_tag"), TYPE("simpleblood.weapon_how.item_type"), ATTACK("simpleblood.weapon_how.attack_style"), NAME("simpleblood.weapon_how.its_name"), NONE("simpleblood.weapon_how.no_match");

        public final String label;

        How(String label) { this.label = label; }
    }

    private static final Map<Item, Kind> KNOWN = new ConcurrentHashMap<>();

    private static final String[][] TAGS = {
            {"SPEAR", "minecraft:spears", "c:tools/spear", "c:tools/spears", "c:tools/trident", "c:tools/tridents"},
            {"MACE", "minecraft:maces", "c:tools/mace", "c:tools/maces"},
            {"ARROW", "c:tools/bow", "c:tools/bows", "c:tools/crossbow", "c:tools/crossbows", "c:tools/ranged_weapon", "c:tools/ranged_weapons"},
            {"BLADE", "minecraft:swords", "c:tools/sword", "c:tools/swords"},
            {"AXE", "minecraft:axes", "c:tools/axe", "c:tools/axes"},
    };

    public static Kind of(Item item) {
        Kind known = KNOWN.get(item);
        if (known != null) return known;
        Kind found = find(item);
        if (found == null) return new Kind(HitContext.Weapon.OTHER, How.NONE);
        KNOWN.put(item, found);
        return found;
    }

    public static void forget() {
        KNOWN.clear();
    }

    private static Kind find(Item item) {
        ItemStack stack;
        try {
            stack = new ItemStack(item);
        } catch (Throwable t) {
            return null;
        }
        try {
            HitContext.Weapon epic = com.simpleblood.compat.EpicFightWeapons.of(stack);
            if (epic != null) return new Kind(epic, How.EPIC_FIGHT);
        } catch (Throwable ignored) {
        }
        try {
            for (String[] row : TAGS) {
                for (int i = 1; i < row.length; i++) {
                    if (stack.is(TagKey.create(Registries.ITEM, Ids.parse(row[i])))) return new Kind(HitContext.Weapon.valueOf(row[0]), How.TAG);
                }
            }
        } catch (Throwable ignored) {
        }
        //? if >=1.21.1 {
        if (item instanceof net.minecraft.world.item.MaceItem) return new Kind(HitContext.Weapon.MACE, How.TYPE);
        //?}
        if (item instanceof net.minecraft.world.item.TridentItem) return new Kind(HitContext.Weapon.SPEAR, How.TYPE);
        if (item instanceof net.minecraft.world.item.ProjectileWeaponItem) return new Kind(HitContext.Weapon.ARROW, How.TYPE);
        //? if <26.3 {
        if (item instanceof net.minecraft.world.item.AxeItem) return new Kind(HitContext.Weapon.AXE, How.TYPE);
        //?}
        //? if <1.21.11 {
        /*if (item instanceof net.minecraft.world.item.SwordItem) return new Kind(HitContext.Weapon.BLADE, How.TYPE);
        *///?}
        //? if >=1.21.11 {
        if (stack.has(net.minecraft.core.component.DataComponents.KINETIC_WEAPON)
                || stack.has(net.minecraft.core.component.DataComponents.PIERCING_WEAPON)) return new Kind(HitContext.Weapon.SPEAR, How.ATTACK);
        //?}
        if (stack.getMaxStackSize() > 1) return new Kind(HitContext.Weapon.OTHER, How.NONE);
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (id.contains("spear") || id.contains("lance") || id.contains("trident") || id.contains("javelin")
                || id.equals("pike") || id.endsWith("_pike")) return new Kind(HitContext.Weapon.SPEAR, How.NAME);
        if (id.endsWith("sword") || id.contains("blade") || id.contains("dagger") || id.contains("katana")
                || id.contains("rapier") || id.contains("saber") || id.contains("cutlass") || id.contains("scythe")) return new Kind(HitContext.Weapon.BLADE, How.NAME);
        if (id.endsWith("_axe") || id.equals("axe") || id.contains("battleaxe") || id.contains("cleaver") || id.contains("halberd")
                || id.contains("tomahawk")) return new Kind(HitContext.Weapon.AXE, How.NAME);
        if (id.contains("mace") || id.contains("hammer") || id.contains("club") || id.contains("flail")) return new Kind(HitContext.Weapon.MACE, How.NAME);
        return new Kind(HitContext.Weapon.OTHER, How.NONE);
    }
}
