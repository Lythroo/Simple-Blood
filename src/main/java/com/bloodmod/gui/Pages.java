package com.bloodmod.gui;

import com.bloodmod.BloodMod;
import com.bloodmod.BloodModConfig;
import com.bloodmod.surface.BloodSurfaces;
import com.bloodmod.surface.PreviewScene;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class Pages {

    private Pages() {}

    public interface Host {
        void openPicker(int current, IntConsumer apply);
        BloodModConfig config();
        void rebuildPage();
        void refreshList();
        void toast(String text);
    }

    public record Page(String title, String hint, boolean pane, Consumer<Builder> build) {}

    private static final String[] KIND_KEYS = {"liquid", "debris", "ember", "powder"};
    private static final String[] KIND_LABELS = {"Liquid", "Debris", "Ember", "Powder"};

    public static final class Builder {
        public final Widget.ScrollPanel panel;
        public final Widget.Container header;
        public final Host host;
        public final BloodModConfig cfg;
        public final BloodModConfig defaults = new BloodModConfig();
        private final boolean listOnly;
        private int cursor = 8;
        private int headerCursor = 6;
        private int rowIndex;
        private final int rowW;

        public Builder(Widget.ScrollPanel panel, Widget.Container header, Host host, boolean listOnly) {
            this.panel = panel;
            this.header = header;
            this.host = host;
            this.listOnly = listOnly;
            this.cfg = host.config();
            this.rowW = panel.w - 14;
            this.controlW = rowW < 250 ? 90 : 120;
        }

        private final int controlW;

        public int headerHeight() {
            return headerCursor > 6 ? headerCursor + 2 : 0;
        }

        public void fixed(Widget w, int height) {
            if (listOnly) return;
            w.x = header.x + 8;
            w.y = header.y + headerCursor;
            header.add(w);
            headerCursor += height + 6;
        }

        private PreviewScene.Kind kind;
        private IntSupplier kindColour;
        private java.util.EnumSet<PreviewScene.Aspect> aspects = java.util.EnumSet.allOf(PreviewScene.Aspect.class);

        public void preview(PreviewScene.Kind kind, IntSupplier colour) {
            this.kind = kind;
            this.kindColour = colour;
            this.aspects = java.util.EnumSet.allOf(PreviewScene.Aspect.class);
        }

        public void preview(PreviewScene.Kind kind, IntSupplier colour, PreviewScene.Aspect first, PreviewScene.Aspect... rest) {
            this.kind = kind;
            this.kindColour = colour;
            this.aspects = java.util.EnumSet.of(first, rest);
        }

        public void heading(String text) {
            Widgets.Heading h = new Widgets.Heading(text);
            h.x = 8;
            h.y = cursor + (cursor <= 8 ? 0 : 10);
            h.w = rowW - 16;
            panel.add(h);
            cursor = h.y + h.h + 2;
            rowIndex = 0;
        }

        public void note(String text) {
            Widgets.Label l = new Widgets.Label(text);
            l.colour = Widgets.TEXT_DIM;
            l.x = 12;
            l.y = cursor + 2;
            l.w = rowW - 24;
            panel.add(l);
            cursor = l.y + 13;
        }

        public void widget(Widget w, int height) {
            w.x = 8;
            w.y = cursor;
            panel.add(w);
            cursor += height + 8;
        }

        private void row(String name, String tip, Widget control, Supplier<Boolean> isDefault, Runnable reset) {
            Widgets.Row r = new Widgets.Row(name, null, control, isDefault, reset);
            if (kind != null) {
                r.previewKind = kind;
                r.previewColour = kindColour;
                r.previewText = tip;
                r.previewAspects = aspects;
            } else if (tip != null && !tip.isEmpty()) {
                r.tooltip = List.of(tip);
            }
            r.x = 0;
            r.y = cursor;
            r.w = rowW;
            r.even = (rowIndex++ & 1) == 0;
            panel.add(r);
            cursor += r.h;
        }

        public void bool(String name, String tip, Supplier<Boolean> get, Consumer<Boolean> set, boolean def) {
            row(name, tip, new Widgets.Toggle(get, set), () -> get.get() == def, () -> set.accept(def));
        }

        public void slider(String name, String tip, int min, int max, int step, String unit,
                           IntSupplier get, IntConsumer set, int def) {
            Widgets.Slider s = new Widgets.Slider(min, max, step, get, set).unit(unit);
            s.w = controlW;
            row(name, tip, s, () -> get.getAsInt() == def, () -> set.accept(def));
        }

        public void choice(String name, String tip, String[] keys, String[] labels,
                           Supplier<String> get, Consumer<String> set, String def) {
            Widgets.Cycle c = new Widgets.Cycle(keys, labels, get, set);
            c.w = controlW;
            row(name, tip, c, () -> def.equals(get.get()), () -> set.accept(def));
        }

        public void colour(String name, String tip, IntSupplier get, IntConsumer set, int def) {
            Widgets.Swatch s = new Widgets.Swatch(get, null);
            s.onClick = () -> host.openPicker(get.getAsInt(), set);
            s.w = Math.min(110, controlW);
            row(name, tip, s, () -> (get.getAsInt() & 0xFFFFFF) == (def & 0xFFFFFF), () -> set.accept(def));
        }

        public void action(String name, String tip, String button, Runnable run) {
            Widgets.Button b = new Widgets.Button(button, run);
            b.w = controlW;
            row(name, tip, b, null, null);
        }

        public void gap(int px) { cursor += px; }
    }

    public static List<Page> all() {
        List<Page> pages = new ArrayList<>();
        pages.add(new Page("General", "The big switches and your colour", true, Pages::general));
        pages.add(new Page("Drops", "Flying blood: bursts, drips, physics", true, Pages::particles));
        pages.add(new Page("Puddles", "Blood that sticks to blocks", true, Pages::surfaces));
        pages.add(new Page("Hits", "Which way it flies, and weapons", true, Pages::hits));
        pages.add(new Page("Water & Sound", "Clouds under water, noises", true, Pages::waterSound));
        pages.add(new Page("Mobs", "Who bleeds what", false, Pages::mobs));
        pages.add(new Page("Modded", "Mobs from other mods", false, Pages::modded));
        pages.add(new Page("Presets", "Pick a look, one click", true, Pages::presets));
        return pages;
    }

    private static IntSupplier playerColour(BloodModConfig c) {
        return () -> c.player.clientPlayerBloodColor;
    }

    private static void general(Builder b) {
        BloodModConfig c = b.cfg, d = b.defaults;
        b.preview(PreviewScene.Kind.HIT, playerColour(c));
        b.bool("Blood", "The big switch. Off means no blood at all.", () -> c.general.modEnabled, v -> c.general.modEnabled = v, d.general.modEnabled);
        b.slider("How much", "Turns every burst, drip and puddle up or down at once.", 10, 300, 5, "%",
                () -> c.general.bloodAmount, v -> c.general.bloodAmount = v, d.general.bloodAmount);
        b.slider("Drop cap", "Drops allowed in the air at once. The rest just do not happen.", 100, 6000, 100, "",
                () -> c.general.particleBudget, v -> c.general.particleBudget = v, d.general.particleBudget);
        b.bool("Players bleed", "You and other players bleed too. Not in creative or spectator.", () -> c.player.playerBleed, v -> c.player.playerBleed = v, d.player.playerBleed);
        b.preview(null, null);
        b.colour("Your blood", "Your colour. The previews use it too.", () -> c.player.clientPlayerBloodColor, v -> c.player.clientPlayerBloodColor = v, d.player.clientPlayerBloodColor);
        b.colour("Other players", "Everyone else.", () -> c.player.otherPlayersBloodColor, v -> c.player.otherPlayersBloodColor = v, d.player.otherPlayersBloodColor);
        if (TestEffects.inWorld()) {
            b.heading("Try it");
            b.action("Drip on what you are looking at", "Aim at a block and hit the button. The game keeps running behind this screen.", "Drip", TestEffects::drops);
            b.action("Big splat away from you", "Aim at a block and hit the button.", "Spray", TestEffects::burst);
            b.action("Mop it all up", "Every puddle, stain and footprint, gone.", "Clear", () -> { TestEffects.clearBlood(); b.host.toast("All clean"); });
        }
    }

    private static void particles(Builder b) {
        BloodModConfig c = b.cfg, d = b.defaults;
        b.preview(PreviewScene.Kind.HIT, playerColour(c), PreviewScene.Aspect.DRIPS, PreviewScene.Aspect.SPLASH, PreviewScene.Aspect.DIRECTIONAL);
        b.bool("Hit burst", "A spray whenever something gets hurt.", () -> c.particles.hitBurst, v -> c.particles.hitBurst = v, d.particles.hitBurst);
        b.preview(PreviewScene.Kind.DEATH, playerColour(c));
        b.bool("Death burst", "One big spray when something dies.", () -> c.particles.deathBurst, v -> c.particles.deathBurst = v, d.particles.deathBurst);
        b.preview(PreviewScene.Kind.DRIP, playerColour(c));
        b.bool("Dripping wounds", "Hurt mobs keep dripping for a while.", () -> c.particles.lowHealthDrip, v -> c.particles.lowHealthDrip = v, d.particles.lowHealthDrip);
        b.preview(PreviewScene.Kind.HIT, playerColour(c), PreviewScene.Aspect.DRIPS, PreviewScene.Aspect.SPLASH);
        b.heading("Drops");
        b.slider("Size", "How big each drop is.", 50, 300, 10, "%", () -> c.particles.particleSize, v -> c.particles.particleSize = v, d.particles.particleSize);
        b.slider("Lifetime", "How long a drop hangs around in the air.", 10, 200, 10, "%", () -> c.particles.particleLifetime, v -> c.particles.particleLifetime = v, d.particles.particleLifetime);
        b.slider("Gravity", "How fast drops fall.", 50, 200, 10, "%", () -> c.particles.particleGravity, v -> c.particles.particleGravity = v, d.particles.particleGravity);
        b.slider("Drag", "Air slows drops down. More drag, shorter flights.", 0, 200, 10, "%", () -> c.particles.particleDrag, v -> c.particles.particleDrag = v, d.particles.particleDrag);
        b.heading("Hit burst");
        b.slider("Amount", "Drops per hit.", 20, 300, 10, "%", () -> c.hitBurst.burstIntensity, v -> c.hitBurst.burstIntensity = v, d.hitBurst.burstIntensity);
        b.slider("Duration", "How long the spray keeps coming.", 30, 200, 10, "%", () -> c.hitBurst.burstDuration, v -> c.hitBurst.burstDuration = v, d.hitBurst.burstDuration);
        b.slider("Spread", "How wide it sprays.", 30, 200, 10, "%", () -> c.hitBurst.burstSpread, v -> c.hitBurst.burstSpread = v, d.hitBurst.burstSpread);
        b.slider("Cooldown", "Breather between bursts on the same mob.", 0, 500, 25, " ms", () -> c.hitBurst.damageCooldown, v -> c.hitBurst.damageCooldown = v, d.hitBurst.damageCooldown);
        b.preview(PreviewScene.Kind.DEATH, playerColour(c));
        b.heading("Death burst");
        b.slider("Amount", "Drops on death.", 20, 300, 10, "%", () -> c.deathBurst.deathIntensity, v -> c.deathBurst.deathIntensity = v, d.deathBurst.deathIntensity);
        b.slider("Spread", "How far it flies.", 30, 200, 10, "%", () -> c.deathBurst.deathSpread, v -> c.deathBurst.deathSpread = v, d.deathBurst.deathSpread);
        b.preview(PreviewScene.Kind.DRIP, playerColour(c));
        b.heading("Dripping");
        b.slider("Starts below", "Health left when the dripping starts.", 10, 90, 5, "%", () -> c.lowHealth.threshold, v -> c.lowHealth.threshold = v, d.lowHealth.threshold);
        b.slider("How often", "Drips per second, roughly.", 20, 500, 10, "%", () -> c.lowHealth.dripFrequency, v -> c.lowHealth.dripFrequency = v, d.lowHealth.dripFrequency);
        b.slider("How much", "Drops per drip.", 20, 300, 10, "%", () -> c.lowHealth.dripIntensity, v -> c.lowHealth.dripIntensity = v, d.lowHealth.dripIntensity);
        b.slider("Lasts", "Dripping stops this long after the last hit.", 3, 120, 1, " s", () -> c.lowHealth.dripDurationSeconds, v -> c.lowHealth.dripDurationSeconds = v, d.lowHealth.dripDurationSeconds);
    }

    private static void surfaces(Builder b) {
        BloodModConfig c = b.cfg, d = b.defaults;
        b.preview(PreviewScene.Kind.PUDDLE, playerColour(c));
        b.bool("Puddles", "Blood sticks to blocks where drops land.", () -> c.surfaces.enabled, v -> { c.surfaces.enabled = v; if (!v) BloodSurfaces.clear(); }, d.surfaces.enabled);
        b.choice("Pixel size", "Same size as block pixels, or chunky double-size ones.", new String[]{"16", "8"}, new String[]{"Normal", "Chunky"},
                () -> Integer.toString(c.surfaces.resolution), v -> { c.surfaces.resolution = Integer.parseInt(v); BloodSurfaces.clear(); }, Integer.toString(d.surfaces.resolution));
        b.choice("Shading", "Light and dark spots follow the block underneath, or a flat pattern.", new String[]{"block", "pattern"}, new String[]{"Follow block", "Pattern"},
                () -> c.surfaces.highlightMode, v -> c.surfaces.highlightMode = v, d.surfaces.highlightMode);
        b.slider("Amount", "How much each drop paints when it lands.", 25, 300, 5, "%", () -> c.surfaces.amount, v -> c.surfaces.amount = v, d.surfaces.amount);
        b.slider("Opacity", "How see-through the blood is.", 20, 100, 2, "%", () -> c.surfaces.opacity, v -> c.surfaces.opacity = v, d.surfaces.opacity);
        b.slider("Lasts", "Seconds until it has dried away.", 5, 600, 5, " s", () -> c.surfaces.lifetimeSeconds, v -> c.surfaces.lifetimeSeconds = v, d.surfaces.lifetimeSeconds);
        b.bool("Walls and ceilings", "Blood on walls and ceilings too. Needed for runs down block sides.", () -> c.surfaces.wallsAndCeilings, v -> c.surfaces.wallsAndCeilings = v, d.surfaces.wallsAndCeilings);
        b.bool("Detailed shapes", "Fences, stairs, signs and plants get blood on the wood you see, not the hitbox. Off is a bit cheaper.",
                () -> c.surfaces.detailedShapes, v -> { c.surfaces.detailedShapes = v; BloodSurfaces.clear(); }, d.surfaces.detailedShapes);
        b.preview(PreviewScene.Kind.FOOTPRINTS, playerColour(c));
        b.bool("Footprints", "Walk through fresh blood, leave a trail.", () -> c.surfaces.footprints, v -> c.surfaces.footprints = v, d.surfaces.footprints);
        b.slider("Trail length", "Steps until the feet are clean again.", 2, 16, 1, "", () -> c.surfaces.footprintSteps, v -> c.surfaces.footprintSteps = v, d.surfaces.footprintSteps);
        b.preview(null, null);
        b.bool("Rain washes", "Rain clears blood faster.", () -> c.surfaces.rainWashes, v -> c.surfaces.rainWashes = v, d.surfaces.rainWashes);
        b.slider("Max painted faces", "Block faces that can hold blood at once. The oldest go first.", 128, 3000, 64, "", () -> c.surfaces.maxTiles, v -> c.surfaces.maxTiles = v, d.surfaces.maxTiles);
        b.slider("Render distance", "Blood further away than this is not drawn.", 16, 128, 4, " blocks", () -> c.surfaces.renderDistance, v -> c.surfaces.renderDistance = v, d.surfaces.renderDistance);
    }

    private static void hits(Builder b) {
        BloodModConfig c = b.cfg, d = b.defaults;
        b.preview(PreviewScene.Kind.HIT, playerColour(c), PreviewScene.Aspect.DIRECTIONAL);
        b.bool("Directional", "Blood flies back toward whatever did the hitting.", () -> c.directional.enabled, v -> c.directional.enabled = v, d.directional.enabled);
        b.preview(PreviewScene.Kind.HIT, playerColour(c), PreviewScene.Aspect.DIRECTIONAL, PreviewScene.Aspect.SPLASH);
        b.slider("Share", "How much of a burst follows the blow. The rest goes everywhere.", 0, 100, 5, "%", () -> c.directional.share, v -> c.directional.share = v, d.directional.share);
        b.preview(PreviewScene.Kind.HIT, playerColour(c), PreviewScene.Aspect.SWEEP);
        b.bool("Weapon flavour", "Swords fling streaks, maces slam, arrows punch through.", () -> c.directional.weaponFlavour, v -> c.directional.weaponFlavour = v, d.directional.weaponFlavour);
        b.preview(PreviewScene.Kind.HIT, playerColour(c), PreviewScene.Aspect.EXIT);
        b.bool("Exit spray", "A bit comes out the other side.", () -> c.directional.entrySpatter, v -> c.directional.entrySpatter = v, d.directional.entrySpatter);
    }

    private static void waterSound(Builder b) {
        BloodModConfig c = b.cfg, d = b.defaults;
        b.preview(PreviewScene.Kind.WATER, playerColour(c));
        b.bool("Blood clouds", "Under water, drops turn into drifting clouds.", () -> c.underwater.transformToFog, v -> c.underwater.transformToFog = v, d.underwater.transformToFog);
        b.slider("Cloud size", "How big the clouds get.", 30, 300, 10, "%", () -> c.underwater.fogSize, v -> c.underwater.fogSize = v, d.underwater.fogSize);
        b.slider("Cloud lifetime", "How long they hang around.", 30, 200, 10, "%", () -> c.underwater.fogLifetime, v -> c.underwater.fogLifetime = v, d.underwater.fogLifetime);
        b.slider("Cloud opacity", "How thick they look.", 30, 200, 10, "%", () -> c.underwater.fogOpacity, v -> c.underwater.fogOpacity = v, d.underwater.fogOpacity);
        b.preview(null, null);
        b.heading("Sound");
        b.bool("Sounds", "All the noises the mod makes.", () -> c.audio.soundEnabled, v -> c.audio.soundEnabled = v, d.audio.soundEnabled);
        b.slider("Volume", "Louder or quieter.", 0, 200, 5, "%", () -> c.audio.soundVolume, v -> c.audio.soundVolume = v, d.audio.soundVolume);
        b.slider("Pitch", "Higher or lower.", 50, 150, 5, "%", () -> c.audio.soundPitch, v -> c.audio.soundPitch = v, d.audio.soundPitch);
        b.bool("Landing sounds", "Little taps and plinks when drops land.", () -> c.audio.landingSounds, v -> c.audio.landingSounds = v, d.audio.landingSounds);
        b.bool("Footstep sounds", "Squelch.", () -> c.audio.footstepSounds, v -> c.audio.footstepSounds = v, d.audio.footstepSounds);
    }

    public static String mobFilter = "";

    private static void mobs(Builder b) {
        BloodModConfig c = b.cfg;
        Widgets.TextField search = new Widgets.TextField();
        search.hint = "Find a mob...";
        search.set(mobFilter);
        search.w = Math.min(220, b.rowW - 8);
        search.onChange = s -> { mobFilter = s; b.host.refreshList(); };
        b.fixed(search, 20);
        mobHeader(b, false);
        List<String> ids = new ArrayList<>(c.vanillaEntities.entities.keySet());
        ids.sort(String::compareTo);
        String f = mobFilter.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (String id : ids) {
            if (!BloodMod.vanillaMobExists(id)) continue;
            if (!f.isEmpty() && !id.contains(f) && !pretty(id).toLowerCase(Locale.ROOT).contains(f)) continue;
            BloodModConfig.VanillaEntities.EntitySettings s = c.vanillaEntities.entities.get(id);
            mobRow(b, id, () -> s.bloodColor, v -> s.bloodColor = v, () -> s.kind, v -> s.kind = v,
                    () -> s.enabled, v -> s.enabled = v, () -> s.canDripAtLowHealth, v -> s.canDripAtLowHealth = v,
                    () -> s.transformToStains, v -> s.transformToStains = v, null);
            shown++;
        }
        if (shown == 0) b.note("Nothing found.");
    }

    public static String newModdedId = "";

    private static void modded(Builder b) {
        BloodModConfig c = b.cfg;
        Widget.Container addRow = new Widget.Container();
        addRow.w = b.rowW; addRow.h = 20;
        Widgets.TextField id = new Widgets.TextField();
        id.hint = "modid:mob_name";
        id.set(newModdedId);
        id.onChange = s -> newModdedId = s;
        id.x = 0; id.y = 0; id.w = Math.min(220, b.rowW - 70); id.h = 20;
        Runnable addAction = () -> {
            String v = newModdedId.trim();
            if (!v.contains(":") || v.endsWith(":")) { b.host.toast("Needs to look like modid:mob_name"); return; }
            if (c.moddedEntities.customEntities.containsKey(v)) { b.host.toast("Already on the list"); return; }
            c.moddedEntities.customEntities.put(v, new BloodModConfig.ModdedEntities.ModdedEntitySettings());
            BloodMod.LOGGER.info("Added modded entity via config UI: {}", v);
            newModdedId = "";
            b.host.rebuildPage();
        };
        id.onEnter = addAction;
        Widgets.Button add = new Widgets.Button("Add", addAction);
        add.x = id.w + 4; add.y = 0; add.w = 50; add.h = 20;
        add.tooltip = List.of("Mobs from other mods bleed plain red unless you add them here.");
        addRow.add(id); addRow.add(add);
        b.fixed(new Offset(addRow), 20);
        if (c.moddedEntities.customEntities.isEmpty()) {
            b.gap(6);
            b.note("Nothing here yet.");
            return;
        }
        mobHeader(b, true);
        List<String> ids = new ArrayList<>(c.moddedEntities.customEntities.keySet());
        ids.sort(String::compareTo);
        for (String eid : ids) {
            BloodModConfig.ModdedEntities.ModdedEntitySettings s = c.moddedEntities.customEntities.get(eid);
            mobRow(b, eid, () -> s.bloodColor, v -> s.bloodColor = v, () -> s.kind, v -> s.kind = v,
                    () -> s.enabled, v -> s.enabled = v, () -> s.canDripAtLowHealth, v -> s.canDripAtLowHealth = v,
                    () -> s.transformToStains, v -> s.transformToStains = v,
                    () -> { c.moddedEntities.customEntities.remove(eid); b.host.rebuildPage(); });
        }
    }

    private static final int MOB_ROW_H = 26, SWATCH_W = 82, KIND_W = 66, CHECK_W = 17, CHECK_GAP = 27;

    private static int[] mobColumns(int x, int w, boolean removable) {
        int right = x + Math.min(w, 540) - 10 - (removable ? 22 : 0);
        int pool = right - CHECK_W;
        int drip = pool - CHECK_GAP;
        int on = drip - CHECK_GAP;
        int kind = on - 12 - KIND_W;
        int swatch = kind - 8 - SWATCH_W;
        return new int[]{swatch, kind, on, drip, pool};
    }

    private static void mobHeader(Builder b, boolean removable) {
        Widget.Container head = new Widget.Container() {
            @Override
            protected void draw(Gfx g, int mx, int my, float dt) {
                int[] col = mobColumns(x, w, removable);
                g.text("Mob", x + 10, y + 4, Widgets.TEXT_DIM);
                g.text("Colour", col[0] + 2, y + 4, Widgets.TEXT_DIM);
                g.text("Kind", col[1] + 2, y + 4, Widgets.TEXT_DIM);
                g.textCentered("On", col[2] + CHECK_W / 2, y + 4, Widgets.TEXT_DIM);
                g.textCentered("Drip", col[3] + CHECK_W / 2, y + 4, Widgets.TEXT_DIM);
                g.textCentered("Pool", col[4] + CHECK_W / 2, y + 4, Widgets.TEXT_DIM);
                g.fill(x, y + 15, x + w, y + 16, 0x30FFFFFF);
            }
        };
        head.w = b.rowW; head.h = 18;
        head.tooltip = List.of("On: bleeds", "Drip: keeps dripping when hurt", "Pool: leaves puddles and clouds");
        b.widget(head, 18);
    }

    private static void mobRow(Builder b, String id, IntSupplier colGet, IntConsumer colSet,
                               Supplier<String> kindGet, Consumer<String> kindSet,
                               Supplier<Boolean> onGet, Consumer<Boolean> onSet,
                               Supplier<Boolean> dripGet, Consumer<Boolean> dripSet,
                               Supplier<Boolean> stainGet, Consumer<Boolean> stainSet,
                               Runnable remove) {
        MobRow row = new MobRow(b, id, colGet, colSet, kindGet, kindSet, onGet, onSet, dripGet, dripSet, stainGet, stainSet, remove);
        row.x = 0;
        row.y = b.cursor;
        row.w = b.rowW;
        row.even = (b.rowIndex++ & 1) == 0;
        b.panel.add(row);
        b.cursor += row.h;
    }

    private static final class MobRow extends Widget.Container {
        final String id;
        final Widgets.Swatch swatch;
        final Widgets.Cycle kind;
        final Widgets.Check on, drip, stain;
        final Widgets.Button remove;
        final Supplier<Boolean> onGet;
        boolean even;

        MobRow(Builder b, String id, IntSupplier colGet, IntConsumer colSet, Supplier<String> kindGet, Consumer<String> kindSet,
               Supplier<Boolean> onGet, Consumer<Boolean> onSet, Supplier<Boolean> dripGet, Consumer<Boolean> dripSet,
               Supplier<Boolean> stainGet, Consumer<Boolean> stainSet, Runnable removeAction) {
            this.id = id;
            this.onGet = onGet;
            this.h = MOB_ROW_H;
            swatch = add(new Widgets.Swatch(colGet, null));
            swatch.onClick = () -> b.host.openPicker(colGet.getAsInt(), colSet);
            swatch.w = SWATCH_W; swatch.h = 18;
            swatch.tooltip = List.of("Colour");
            kind = add(new Widgets.Cycle(KIND_KEYS, KIND_LABELS, kindGet, kindSet));
            kind.w = KIND_W; kind.h = 18;
            kind.tooltip = List.of("Liquid: drips and pools", "Debris: dry bits, no puddles", "Ember: glows, fizzles in water", "Powder: flat piles, like snow");
            on = add(new Widgets.Check(onGet, onSet));
            on.tooltip = List.of("Bleeds");
            drip = add(new Widgets.Check(dripGet, dripSet));
            drip.tooltip = List.of("Keeps dripping when hurt");
            stain = add(new Widgets.Check(stainGet, stainSet));
            stain.tooltip = List.of("Leaves puddles and clouds");
            if (removeAction != null) {
                remove = add(new Widgets.Button("x", removeAction));
                remove.w = 14; remove.h = 14;
                remove.tooltip = List.of("Remove");
            } else {
                remove = null;
            }
        }

        void layout() {
            int[] col = mobColumns(x, w, remove != null);
            int cy = y + (h - 18) / 2;
            swatch.x = col[0]; swatch.y = cy;
            kind.x = col[1]; kind.y = cy;
            on.x = col[2]; on.y = cy;
            drip.x = col[3]; drip.y = cy;
            stain.x = col[4]; stain.y = cy;
            if (remove != null) { remove.x = col[4] + CHECK_W + 8; remove.y = y + (h - 14) / 2; }
        }

        @Override
        public void render(Gfx g, int mx, int my, float dt) {
            layout();
            super.render(g, mx, my, dt);
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            g.fill(x, y, x + w, y + h, even ? 0x12FFFFFF : 0);
            if (hover > 0.01f) g.fill(x, y, x + w, y + h, Gfx.alpha(0xFFFFFFFF, 0.05f * hover));
            boolean enabled = onGet.get();
            g.textClipped(pretty(id), x + 10, y + (h - 8) / 2, swatch.x - x - 16, enabled ? Widgets.TEXT : Widgets.TEXT_DIM);
            super.draw(g, mx, my, dt);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            layout();
            return super.mouseClicked(mx, my, button);
        }
    }

    static String pretty(String id) {
        String ns = "", path = id;
        int colon = id.indexOf(':');
        if (colon >= 0) { ns = id.substring(0, colon); path = id.substring(colon + 1); }
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return ns.isEmpty() ? sb.toString() : ns + ": " + sb;
    }

    private static void presets(Builder b) {
        b.preview(PreviewScene.Kind.HIT, playerColour(b.cfg));
        for (Presets.Preset p : Presets.ALL) {
            b.action(p.name(), p.description(), "Apply", () -> {
                p.apply().accept(b.cfg);
                BloodSurfaces.clear();
                b.host.toast(p.name() + " applied");
                b.host.rebuildPage();
            });
        }
        b.preview(null, null);
        b.heading("Start over");
        b.action("Everything back to how it came", "All settings, plus mob colours and kinds.", "Reset all", () -> {
            BloodModConfig d = new BloodModConfig();
            d.vanillaEntities.initializeDefaults();
            Presets.defaults(b.cfg);
            b.cfg.general.modEnabled = true;
            b.cfg.player = d.player;
            b.cfg.vanillaEntities = d.vanillaEntities;
            BloodSurfaces.clear();
            b.host.toast("Back to defaults");
            b.host.rebuildPage();
        });
    }

    static final class Offset extends Widget.Container {
        private final Widget.Container inner;
        private final List<int[]> rel = new ArrayList<>();

        Offset(Widget.Container inner) {
            this.inner = inner;
            this.w = inner.w;
            this.h = inner.h;
            for (Widget c : inner.children()) rel.add(new int[]{c.x, c.y});
            add(inner);
        }

        private void place() {
            inner.x = x;
            inner.y = y;
            List<Widget> kids = inner.children();
            for (int i = 0; i < kids.size(); i++) {
                kids.get(i).x = x + rel.get(i)[0];
                kids.get(i).y = y + rel.get(i)[1];
            }
        }

        @Override
        public void render(Gfx g, int mx, int my, float dt) {
            place();
            super.render(g, mx, my, dt);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) { place(); return super.mouseClicked(mx, my, button); }

        @Override
        public Widget hovered(double mx, double my) { place(); return super.hovered(mx, my); }
    }
}
