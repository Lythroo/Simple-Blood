package com.simpleblood;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MobTable {
    private MobTable() {}

    private static final Set<String> NOT_MOBS = Set.of("player", "armor_stand", "mannequin");

    private static List<Identifier> living;
    private static boolean synced;

    public static List<Identifier> livingIds() {
        if (living == null) {
            List<Identifier> out = new ArrayList<>();
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                try {
                    if (!DefaultAttributes.hasSupplier(type)) continue;
                } catch (Throwable t) {
                    continue;
                }
                Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (id != null && !(id.getNamespace().equals("minecraft") && NOT_MOBS.contains(id.getPath()))) out.add(id);
            }
            out.sort((a, b) -> a.toString().compareTo(b.toString()));
            living = out;
        }
        return living;
    }

    public static Map<String, List<Identifier>> moddedByMod() {
        Map<String, List<Identifier>> out = new java.util.TreeMap<>();
        for (Identifier id : livingIds()) {
            if (id.getNamespace().equals("minecraft")) continue;
            out.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(id);
        }
        return out;
    }

    public static void sync(SimpleBloodConfig cfg) {
        if (synced || cfg == null) return;
        synced = true;
        Set<String> here = new HashSet<>();
        for (Identifier id : livingIds()) {
            if (id.getNamespace().equals("minecraft")) here.add(id.getPath());
        }
        if (here.isEmpty()) {
            synced = false;
            return;
        }
        int dropped = 0, added = 0;
        java.util.Iterator<String> it = cfg.vanillaEntities.entities.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            if (!here.contains(key) && !NOT_MOBS.contains(key)) {
                it.remove();
                dropped++;
            }
        }
        for (String path : here) {
            if (cfg.vanillaEntities.entities.containsKey(path)) continue;
            SimpleBloodConfig.ModdedEntities.ModdedEntitySettings g = guess(Ids.vanilla(path));
            SimpleBloodConfig.VanillaEntities.EntitySettings s = new SimpleBloodConfig.VanillaEntities.EntitySettings(
                    g.enabled, g.canDripAtLowHealth, g.transformToStains, g.bloodColor, g.getKind());
            s.glows = g.glows;
            cfg.vanillaEntities.entities.put(path, s);
            added++;
        }
        if (dropped > 0 || added > 0) {
            SimpleBlood.LOGGER.info("Mob table matched to this game: {} mobs it does not have left out, {} new ones guessed", dropped, added);
        }
    }

    private record Rule(String[] words, String[] stems, BloodKind kind, int colour, boolean drips, boolean stains) {}

    private static Rule rule(String words, String stems, BloodKind kind, int colour, boolean drips, boolean stains) {
        return new Rule(words.split(" "), stems.isEmpty() ? new String[0] : stems.split(" "), kind, colour, drips, stains);
    }

    private static final Rule[] RULES = {
            rule("skeleton skeletal bone bones skull lich", "skelet skull", BloodKind.BONE, 0xC9C2A8, false, false),
            rule("golem robot construct automaton mech android drone turret sentinel clockwork", "golem robot automaton", BloodKind.METAL, 0xC9C7C1, false, false),
            rule("blaze fire flame flaming magma lava inferno infernal ember cinder pyro molten", "blaze fire flame magma lava infern cinder pyro molten", BloodKind.EMBER, 0xF5A623, false, false),
            rule("ghost spirit wraith specter spectre wisp soul vex allay fairy pixie phantasm apparition banshee", "ghost spirit wraith spectr phantasm banshee", BloodKind.SPIRIT, 0x9FB6DC, false, false),
            rule("breeze wind storm tornado gust whirlwind cloud", "breeze storm tornado whirlwind", BloodKind.WIND, 0xC8ECF2, false, false),
            rule("snow frost ice icy frozen glacial yeti", "snow frost frozen glacia", BloodKind.POWDER, 0xEDF4FA, false, true),
            rule("treant ent tree wood wooden bark dryad creaking stump", "treant dryad creaking", BloodKind.WOOD, 0xD98B34, false, true),
            rule("stone rock crystal gem gargoyle statue boulder obsidian", "gargoyle crystal boulder obsidian", BloodKind.DEBRIS, 0x8A8A8A, false, false),
            rule("slime ooze blob jelly gel goo", "slime", BloodKind.LIQUID, 0x7FCF4E, false, true),
            rule("zombie zombified undead ghoul mummy revenant husk drowned draugr corpse", "zomb undead ghoul mummy revenant draugr", BloodKind.LIQUID, 0x4A1414, true, true),
            rule("ender end void shulker", "enderm shulker", BloodKind.LIQUID, 0x5E1C82, true, true),
            rule("spider beetle ant bug insect scorpion mantis wasp bee mosquito tick termite worm crawler centipede firefly fly moth butterfly dragonfly", "spider beetle insect scorpion mantis mosquito termite centipede", BloodKind.LIQUID, 0x3B7A2A, true, true),
            rule("squid octopus kraken cuttlefish ink", "squid octopus kraken cuttlefish", BloodKind.LIQUID, 0x1A1A2A, true, true),
            rule("piglin hoglin boar", "piglin hoglin", BloodKind.LIQUID, 0x8A1A1A, true, true),
            rule("sculk warden", "sculk", BloodKind.LIQUID, 0x0F4B58, true, true),
    };

    public static SimpleBloodConfig.ModdedEntities.ModdedEntitySettings guess(Identifier id) {
        String[] words = id.getPath().toLowerCase(Locale.ROOT).split("[_/.\\-]+");
        Rule match = null;
        for (int pass = 0; pass < 2 && match == null; pass++) {
            for (Rule r : RULES) {
                for (String w : pass == 0 ? r.words() : r.stems()) {
                    for (String word : words) {
                        if (pass == 0 ? word.equals(w) : word.contains(w)) {
                            match = r;
                            break;
                        }
                    }
                    if (match != null) break;
                }
                if (match != null) break;
            }
        }
        SimpleBloodConfig.ModdedEntities.ModdedEntitySettings s = match == null
                ? new SimpleBloodConfig.ModdedEntities.ModdedEntitySettings(true, true, true, 0x7A0F0F)
                : new SimpleBloodConfig.ModdedEntities.ModdedEntitySettings(true, match.drips(), match.stains(), match.colour());
        if (match != null) s.kind = match.kind().key();
        for (String word : words) {
            if (word.equals("glow") || word.equals("glowing") || word.equals("luminous") || word.equals("radiant")) s.glows = true;
        }
        return s;
    }
}
