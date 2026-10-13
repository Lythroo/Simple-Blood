package com.simpleblood.gui;

import com.simpleblood.SimpleBlood;
import com.simpleblood.SimpleBloodConfig;
import com.simpleblood.SkinColours;
import com.simpleblood.surface.BloodSurfaces;
import com.simpleblood.surface.PreviewScene;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import java.util.Map;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The content of every tab: a live 3D diorama of the blood engine on top, short option rows
 * underneath. Explanations live in tooltips, not on the page.
 */
public final class Pages {

    private Pages() {}

    /** What a page needs from the screen. */
    public interface Host {
        void openPicker(int current, IntConsumer apply);
        /** The picker with a note under it about what the colour is for. */
        void openPicker(int current, IntConsumer apply, String note);
        SimpleBloodConfig config();
        /** Rebuilds the whole page (header and list). */
        void rebuildPage();
        /** Rebuilds only the scrolling list; fixed header widgets (search fields) keep focus. */
        void refreshList();
        void toast(String text);
    }

    /**
     * A tab. {@code group} is the sidebar heading it sits under, {@code pane} whether the
     * diorama pane sits beside its list, {@code searchable} whether the settings search looks
     * through it (the mob tables have a search of their own).
     */
    public record Page(String group, String title, String hint, boolean pane, boolean searchable, Consumer<Builder> build) {}

    private static final String[] KIND_KEYS = {"liquid", "debris", "ember", "powder", "bone", "metal", "wood", "spirit", "wind"};
    private static final String[] KIND_LABELS = {"Liquid", "Debris", "Ember", "Powder", "Bone", "Metal", "Wood", "Spirit", "Wind"};

    // ================================================================== builder

    /** Lays rows into a scroll panel from top to bottom. */
    public static final class Builder {
        public final Widget.ScrollPanel panel;
        public final Widget.Container header;
        public final Host host;
        public final SimpleBloodConfig cfg;
        public final SimpleBloodConfig defaults = new SimpleBloodConfig();
        private final boolean listOnly;
        private int cursor = 8;
        private int headerCursor = 6;
        // Searching: only rows whose name, tip, section or page holds the text are laid out,
        // each group under a heading naming the page and section it lives on.
        private String filter;
        private String filterPage, filterSection;
        private boolean filterShown;
        int found;
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

        /** Control width, narrower when the list is narrow. */
        private final int controlW;

        /** Height the fixed header widgets take above the list. */
        public int headerHeight() {
            return headerCursor > 6 ? headerCursor + 2 : 0;
        }

        /**
         * Places a widget above the scrolling list, where it stays put while the list is
         * rebuilt (search boxes, add fields). Ignored when only the list is being refreshed.
         */
        public void fixed(Widget w, int height) {
            if (listOnly || filter != null) return;
            w.x = header.x + 8;
            w.y = header.y + headerCursor;
            header.add(w);
            headerCursor += height + 6;
        }

        /** The diorama every following row shows while hovered (null: none). */
        private PreviewScene.Kind kind;
        private IntSupplier kindColour;
        private java.util.EnumSet<PreviewScene.Aspect> aspects = java.util.EnumSet.allOf(PreviewScene.Aspect.class);

        /** Settings the following rows' diorama plays with instead of the live ones (null: live). */
        private SimpleBloodConfig previewConfig;

        /** Following rows preview this scene, playing every part of a hit. */
        public void preview(PreviewScene.Kind kind, IntSupplier colour) {
            this.kind = kind;
            this.kindColour = colour;
            this.aspects = java.util.EnumSet.allOf(PreviewScene.Aspect.class);
        }

        /** Following rows' diorama plays with these settings instead of the live ones (null: live). */
        public void previewWith(SimpleBloodConfig config) {
            this.previewConfig = config;
        }

        /** Following rows preview this scene, playing only these parts of a hit. */
        public void preview(PreviewScene.Kind kind, IntSupplier colour, PreviewScene.Aspect first, PreviewScene.Aspect... rest) {
            this.kind = kind;
            this.kindColour = colour;
            this.aspects = java.util.EnumSet.of(first, rest);
        }

        /** Lays out only the rows matching {@code text} from now on (the settings search). */
        public void search(String text) {
            filter = text.toLowerCase(Locale.ROOT);
        }

        /** The page whose rows come next while searching. */
        void searchPage(String title) {
            filterPage = title;
            filterSection = null;
            filterShown = false;
        }

        /** Whether this row is shown; the first one shown under a page or section brings its heading. */
        private boolean admit(String name, String tip) {
            if (filter == null) return true;
            String hay = (name + " " + (tip == null ? "" : tip) + " " + (filterSection == null ? "" : filterSection) + " " + filterPage)
                    .toLowerCase(Locale.ROOT);
            if (!hay.contains(filter)) return false;
            if (!filterShown) {
                filterShown = true;
                addHeading(filterSection == null || filterSection.equals(filterPage) ? filterPage : filterPage + " \u00b7 " + filterSection);
            }
            found++;
            return true;
        }

        public void heading(String text) {
            if (filter != null) {
                filterSection = text;
                filterShown = false;
                return;
            }
            addHeading(text);
        }

        private void addHeading(String text) {
            Widgets.Heading h = new Widgets.Heading(text);
            h.x = 8;
            h.y = cursor + (cursor <= 8 ? 0 : 10);
            h.w = rowW - 16;
            panel.add(h);
            cursor = h.y + h.h + 2;
            rowIndex = 0;
        }

        /** A line of dim text, wrapped onto as many lines as the list is too narrow for. */
        public void note(String text) {
            if (filter != null) return;
            int width = rowW - 24;
            net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
            StringBuilder line = new StringBuilder();
            int y = cursor + 2;
            for (String word : text.split(" ")) {
                String tried = line.length() == 0 ? word : line + " " + word;
                if (line.length() > 0 && font.width(tried) > width) {
                    y = noteLine(line.toString(), y, width);
                    line.setLength(0);
                    line.append(word);
                } else {
                    line.setLength(0);
                    line.append(tried);
                }
            }
            if (line.length() > 0) y = noteLine(line.toString(), y, width);
            cursor = y + 3;
        }

        private int noteLine(String text, int y, int width) {
            Widgets.Label l = new Widgets.Label(text);
            l.colour = Widgets.TEXT_DIM;
            l.x = 12;
            l.y = y;
            l.w = width;
            panel.add(l);
            return y + 10;
        }

        /** A widget across the list's width, between rows. */
        public void wide(Widget w) {
            if (filter != null) return;
            w.x = 8;
            w.y = cursor + 3;
            w.w = rowW - 16;
            panel.add(w);
            cursor = w.y + w.h + 5;
        }

        public void widget(Widget w, int height) {
            if (filter != null) return;
            w.x = 8;
            w.y = cursor;
            panel.add(w);
            cursor += height + 8;
        }

        private void row(String name, String tip, Widget control, Supplier<Boolean> isDefault, Runnable reset) {
            if (!admit(name, tip)) return;
            Widgets.Row r = new Widgets.Row(name, null, control, isDefault, reset);
            if (kind != null) {
                r.previewKind = kind;
                r.previewColour = kindColour;
                r.previewText = tip;
                r.previewAspects = aspects;
                r.previewConfig = previewConfig;
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
            colour(name, tip, null, get, set, def);
        }

        /** A colour whose picker carries {@code pickerNote} under the grid. */
        public void colour(String name, String tip, String pickerNote, IntSupplier get, IntConsumer set, int def) {
            Widgets.Swatch s = new Widgets.Swatch(get, null);
            s.onClick = () -> host.openPicker(get.getAsInt(), set, pickerNote);
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

    // ================================================================== pages

    public static List<Page> all() {
        List<Page> pages = new ArrayList<>();
        pages.add(new Page("Basics", "Overview", "The big switch, how much, and trying it out", true, true, Pages::overview));
        pages.add(new Page("Basics", "Presets", "Pick a look, one click", true, true, Pages::presets));
        pages.add(new Page("Basics", "Your colour", "What you bleed, and showing it to others", true, true, Pages::colour));
        pages.add(new Page("Blood", "Bleeding", "When blood comes out: hits, deaths, wounds, giblets", true, true, Pages::bleeding));
        pages.add(new Page("Blood", "Drops", "How drops fly, and which way", true, true, Pages::drops));
        pages.add(new Page("Blood", "Puddles", "Blood on blocks, footprints", true, true, Pages::puddles));
        pages.add(new Page("Blood", "Bodies", "Blood on mobs" + (com.simpleblood.compat.PhysicsModRagdolls.available() ? ", ragdolls" : ""), false, true, Pages::bodies));
        pages.add(new Page("Blood", "Water", "Clouds under water", true, true, Pages::water));
        pages.add(new Page("Blood", "Sound", "Splats, drips, squelches", false, true, Pages::sound));
        pages.add(new Page("Mobs", "Who bleeds", "Which mob bleeds what", false, false, Pages::mobs));
        pages.add(new Page("Mobs", "Modded", "Mobs from other mods", false, false, Pages::modded));
        pages.add(new Page("Mobs", "Kinds", "Bone, metal, wood, embers, sparks, wind, glow", false, true, Pages::kinds));
        pages.add(new Page("Extras", "Shaders", "For Iris shader packs", true, true, Pages::shaders));
        pages.add(new Page("Extras", "Performance", "Limits for slower machines", false, true, Pages::performance));
        pages.add(new Page("Extras", "Interface", "The menu button", false, true, Pages::interfacePage));
        return pages;
    }

    /** The settings search: every searchable page's matching rows, under their page's name. */
    public static void search(Builder b, List<Page> pages, String text) {
        b.search(text);
        for (Page p : pages) {
            if (!p.searchable()) continue;
            b.searchPage(p.title());
            b.preview(null, null);
            p.build().accept(b);
        }
        b.preview(null, null);
        if (b.found == 0) {
            b.filter = null; // say so, in a plain note
            b.note("Nothing found for \"" + text.trim() + "\".");
        }
    }

    private static IntSupplier playerColour(SimpleBloodConfig c) {
        return () -> c.player.clientPlayerBloodColor;
    }

    private static void overview(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.preview(PreviewScene.Kind.HIT, playerColour(c));
        b.bool("Blood", "The big switch. Off means no blood at all.", () -> c.general.modEnabled, v -> c.general.modEnabled = v, d.general.modEnabled);
        b.slider("How much", "Turns every burst, drip and puddle up or down at once.", 10, 300, 5, "%",
                () -> c.general.bloodAmount, v -> c.general.bloodAmount = v, d.general.bloodAmount);
        b.preview(null, null);
        b.note("Everything else is grouped on the left. Looking for one setting? Type in the box above the pages.");
        if (TestEffects.inWorld()) {
            b.heading("Try it");
            b.action("Drip on what you are looking at", "Aim at a block and hit the button. The game keeps running behind this screen.", "Drip", TestEffects::drops);
            b.action("Big splat away from you", "Aim at a block and hit the button.", "Spray", TestEffects::burst);
            b.action("Mop it all up", "Every puddle, stain and footprint, gone.", "Clear", () -> { TestEffects.clearBlood(); b.host.toast("All clean"); });
        }
    }

    private static void colour(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.heading("Your blood colour");
        b.preview(PreviewScene.Kind.HIT, playerColour(c));
        b.colour("Your blood", "The colour you bleed on your screen. Other players only see it once it is in your skin: see Upload below.",
                "Only you see this colour. To show it to other players, press Upload under it afterwards.",
                () -> c.player.clientPlayerBloodColor, v -> c.player.clientPlayerBloodColor = v, d.player.clientPlayerBloodColor);
        b.wide(new Widgets.Notice(() -> ownColourText(c), () -> ownColourTone(c), SkinColours::ownColour));
        b.preview(null, null);
        b.action("Show it to other players", "Puts your colour in a corner of your skin nobody sees and makes that your Minecraft skin, like changing it on minecraft.net. "
                        + "Everyone with Simple Blood then sees you bleed your colour. Asks first.",
                "Upload", () -> confirmUpload(b));
        b.action("Or do it yourself", "Saves that skin as a file and opens its folder. Upload it on minecraft.net or in the launcher.",
                "Save file", () -> SkinColours.putInOwnSkin(c.player.clientPlayerBloodColor, false, result -> skinDone(b, result)));
        b.action("Take it out of your skin", "Clears the hidden corner again and makes that your Minecraft skin. Others go back to their own colour for you. Asks first.",
                "Remove", () -> confirmRemove(b));

        b.heading("Other players");
        b.preview(PreviewScene.Kind.HIT, () -> c.player.otherPlayersBloodColor);
        b.colour("Other players", "What other players bleed on your screen, when their skin carries no colour of its own.",
                () -> c.player.otherPlayersBloodColor, v -> c.player.otherPlayersBloodColor = v, d.player.otherPlayersBloodColor);
        b.preview(null, null);
        b.bool("Colours from skins", "Players who put their colour in their skin (with Simple Blood) bleed that colour. Off: everyone bleeds the colour above.",
                () -> c.player.colourFromSkins, v -> c.player.colourFromSkins = v, d.player.colourFromSkins);
    }

    /** What other players see of your colour, for the note under it. */
    private static String ownColourText(SimpleBloodConfig c) {
        int carried = SkinColours.ownColour();
        int mine = c.player.clientPlayerBloodColor & 0xFFFFFF;
        if (carried == SkinColours.READING) return "Checking your skin...";
        if (carried == mine) {
            return SkinColours.justUploaded()
                    ? "Uploaded. Players with Simple Blood see this colour once they rejoin."
                    : "Other players see this colour too. It is in your skin.";
        }
        if (carried == SkinColours.NONE && SkinColours.justUploaded()) {
            return "Taken out of your skin. Players see their own colour for you once they rejoin.";
        }
        if (carried == SkinColours.NONE) {
            return "Only you see this colour. Others see whatever they picked for other players. Press Upload below to share yours.";
        }
        return String.format("Other players still see the colour in your skin, #%06X (left). Press Upload below to send the new one.", carried);
    }

    private static int ownColourTone(SimpleBloodConfig c) {
        int carried = SkinColours.ownColour();
        if (carried == SkinColours.READING) return Widgets.Notice.QUIET;
        return carried == (c.player.clientPlayerBloodColor & 0xFFFFFF) ? Widgets.Notice.GOOD : Widgets.Notice.WARN;
    }

    /** Asks before changing the player's Minecraft skin, then uploads. */
    private static void confirmUpload(Builder b) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen back = Screens.current(mc);
        Screens.open(mc, new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
            Screens.open(mc, back);
            if (yes) SkinColours.putInOwnSkin(b.cfg.player.clientPlayerBloodColor, true, result -> skinDone(b, result));
        }, net.minecraft.network.chat.Component.literal("Change your Minecraft skin?"),
                net.minecraft.network.chat.Component.literal("This uploads your current skin with your blood colour in a hidden corner and makes it your skin, "
                        + "like changing it on minecraft.net. It looks the same. A copy is also saved in the simpleblood folder.")));
    }

    /** Asks before taking the colour out of the player's Minecraft skin, then uploads. */
    private static void confirmRemove(Builder b) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen back = Screens.current(mc);
        Screens.open(mc, new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
            Screens.open(mc, back);
            if (yes) SkinColours.putInOwnSkin(SkinColours.NONE, true, result -> skinDone(b, result));
        }, net.minecraft.network.chat.Component.literal("Take your blood colour out of your skin?"),
                net.minecraft.network.chat.Component.literal("This clears the hidden corner of your current skin and makes that your skin, "
                        + "like changing it on minecraft.net. It looks the same. A copy is also saved in the simpleblood folder.")));
    }

    private static void skinDone(Builder b, SkinColours.Result result) {
        switch (result) {
            case SAVED -> { SkinColours.showSavedSkin(); b.host.toast("Saved. Now upload it as your skin."); }
            case UPLOADED -> b.host.toast("Uploaded. Others see it after rejoining.");
            case NO_COLOUR -> b.host.toast("Your skin has no colour in it");
            case ALREADY_THERE -> b.host.toast("Your skin already has this colour");
            case NOT_LOGGED_IN -> b.host.toast("Not logged in to a Minecraft account");
            case TOO_SOON -> b.host.toast("Too many tries. Wait a minute.");
            case FAILED -> b.host.toast("Could not change your skin");
        }
    }

    private static void bleeding(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.heading("Hits");
        b.preview(PreviewScene.Kind.HIT, playerColour(c), PreviewScene.Aspect.DRIPS, PreviewScene.Aspect.SPLASH, PreviewScene.Aspect.DIRECTIONAL);
        b.bool("Hit burst", "A spray whenever something gets hurt.", () -> c.particles.hitBurst, v -> c.particles.hitBurst = v, d.particles.hitBurst);
        b.preview(PreviewScene.Kind.HIT, playerColour(c), PreviewScene.Aspect.DRIPS, PreviewScene.Aspect.SPLASH);
        b.slider("Amount", "Drops per hit.", 20, 300, 10, "%", () -> c.hitBurst.burstIntensity, v -> c.hitBurst.burstIntensity = v, d.hitBurst.burstIntensity);
        b.slider("Duration", "How long the spray keeps coming.", 30, 200, 10, "%", () -> c.hitBurst.burstDuration, v -> c.hitBurst.burstDuration = v, d.hitBurst.burstDuration);
        b.slider("Spread", "How wide it sprays.", 30, 200, 10, "%", () -> c.hitBurst.burstSpread, v -> c.hitBurst.burstSpread = v, d.hitBurst.burstSpread);
        b.slider("Cooldown", "Breather between bursts on the same mob.", 0, 500, 25, " ms", () -> c.hitBurst.damageCooldown, v -> c.hitBurst.damageCooldown = v, d.hitBurst.damageCooldown);
        b.preview(PreviewScene.Kind.HIT, playerColour(c));
        b.bool("Players bleed", "You and other players bleed too. Not in creative or spectator.", () -> c.player.playerBleed, v -> c.player.playerBleed = v, d.player.playerBleed);

        b.heading("Deaths");
        b.preview(PreviewScene.Kind.DEATH, playerColour(c));
        b.bool("Death burst", "One big spray when something dies.", () -> c.particles.deathBurst, v -> c.particles.deathBurst = v, d.particles.deathBurst);
        b.slider("Amount", "Drops on death.", 20, 300, 10, "%", () -> c.deathBurst.deathIntensity, v -> c.deathBurst.deathIntensity = v, d.deathBurst.deathIntensity);
        b.slider("Spread", "How far it flies.", 30, 200, 10, "%", () -> c.deathBurst.deathSpread, v -> c.deathBurst.deathSpread = v, d.deathBurst.deathSpread);

        b.heading("Dripping wounds");
        b.preview(PreviewScene.Kind.DRIP, playerColour(c));
        b.bool("Dripping wounds", "Hurt mobs keep dripping for a while.", () -> c.particles.lowHealthDrip, v -> c.particles.lowHealthDrip = v, d.particles.lowHealthDrip);
        b.slider("Starts below", "Health left when the dripping starts.", 10, 90, 5, "%", () -> c.lowHealth.threshold, v -> c.lowHealth.threshold = v, d.lowHealth.threshold);
        b.slider("How often", "Drips per second, roughly.", 20, 500, 10, "%", () -> c.lowHealth.dripFrequency, v -> c.lowHealth.dripFrequency = v, d.lowHealth.dripFrequency);
        b.slider("How much", "Drops per drip.", 20, 300, 10, "%", () -> c.lowHealth.dripIntensity, v -> c.lowHealth.dripIntensity = v, d.lowHealth.dripIntensity);
        b.slider("Lasts", "Dripping stops this long after the last hit.", 3, 120, 1, " s", () -> c.lowHealth.dripDurationSeconds, v -> c.lowHealth.dripDurationSeconds = v, d.lowHealth.dripDurationSeconds);

        b.heading("Giblets");
        b.preview(null, null);
        b.bool("Giblets", "Chunks of meat fly off mobs that bleed: now and then on a hard hit, a handful when they die. Off by default.",
                () -> c.gore.giblets, v -> c.gore.giblets = v, d.gore.giblets);
        b.slider("Amount", "How many chunks fly off.", 10, 300, 10, "%", () -> c.gore.gibletAmount, v -> c.gore.gibletAmount = v, d.gore.gibletAmount);
        b.bool("Blood trail", "Chunks bleed as they fly and splash where they land.", () -> c.gore.gibletTrails, v -> c.gore.gibletTrails = v, d.gore.gibletTrails);
    }

    private static void drops(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.heading("Flight");
        b.preview(PreviewScene.Kind.HIT, playerColour(c), PreviewScene.Aspect.DRIPS, PreviewScene.Aspect.SPLASH);
        b.slider("Size", "How big each drop is.", 50, 300, 10, "%", () -> c.particles.particleSize, v -> c.particles.particleSize = v, d.particles.particleSize);
        b.slider("Lifetime", "How long a drop hangs around in the air.", 10, 200, 10, "%", () -> c.particles.particleLifetime, v -> c.particles.particleLifetime = v, d.particles.particleLifetime);
        b.slider("Gravity", "How fast drops fall.", 50, 200, 10, "%", () -> c.particles.particleGravity, v -> c.particles.particleGravity = v, d.particles.particleGravity);
        b.slider("Drag", "Air slows drops down. More drag, shorter flights.", 0, 200, 10, "%", () -> c.particles.particleDrag, v -> c.particles.particleDrag = v, d.particles.particleDrag);

        b.heading("Direction");
        b.preview(PreviewScene.Kind.HIT, playerColour(c), PreviewScene.Aspect.DIRECTIONAL);
        b.bool("Directional", "Blood flies back toward whatever did the hitting.", () -> c.directional.enabled, v -> c.directional.enabled = v, d.directional.enabled);
        b.preview(PreviewScene.Kind.HIT, playerColour(c), PreviewScene.Aspect.DIRECTIONAL, PreviewScene.Aspect.SPLASH);
        b.slider("Share", "How much of a burst follows the blow. The rest goes everywhere.", 0, 100, 5, "%", () -> c.directional.share, v -> c.directional.share = v, d.directional.share);
        b.preview(PreviewScene.Kind.HIT, playerColour(c), PreviewScene.Aspect.SWEEP);
        b.bool("Weapon flavour", "Swords fling streaks, maces slam, arrows punch through. Also shapes blood on mobs.", () -> c.directional.weaponFlavour, v -> c.directional.weaponFlavour = v, d.directional.weaponFlavour);
        b.preview(PreviewScene.Kind.HIT, playerColour(c), PreviewScene.Aspect.EXIT);
        b.bool("Exit spray", "A bit comes out the other side.", () -> c.directional.entrySpatter, v -> c.directional.entrySpatter = v, d.directional.entrySpatter);
    }

    private static void puddles(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
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
        b.preview(null, null);
        b.bool("Rain washes", "Rain clears blood faster, on blocks and on mobs.", () -> c.surfaces.rainWashes, v -> c.surfaces.rainWashes = v, d.surfaces.rainWashes);

        b.heading("Footprints");
        b.preview(PreviewScene.Kind.FOOTPRINTS, playerColour(c));
        b.bool("Footprints", "Walk through fresh blood, leave a trail.", () -> c.surfaces.footprints, v -> c.surfaces.footprints = v, d.surfaces.footprints);
        b.slider("Trail length", "Steps until the feet are clean again.", 2, 16, 1, "", () -> c.surfaces.footprintSteps, v -> c.surfaces.footprintSteps = v, d.surfaces.footprintSteps);
    }

    private static void bodies(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.heading("Blood on mobs");
        b.bool("Blood on mobs", "A hit leaves blood on the mob's body, shaped by the weapon. It moves with the mob, runs down and drips off; a badly hurt mob bleeds from the wound. Off by default.",
                () -> c.mobCoat.enabled, v -> c.mobCoat.enabled = v, d.mobCoat.enabled);
        b.slider("Amount", "How much of each hit stays on the body.", 0, 300, 10, "%", () -> c.mobCoat.amount, v -> c.mobCoat.amount = v, d.mobCoat.amount);
        b.slider("Lasts", "Seconds until it has dried away. Water washes it off sooner, rain too.", 5, 300, 5, " s",
                () -> c.mobCoat.lifetimeSeconds, v -> c.mobCoat.lifetimeSeconds = v, d.mobCoat.lifetimeSeconds);
        b.bool("Runs down", "Heavy blood runs down the body.", () -> c.mobCoat.runs, v -> c.mobCoat.runs = v, d.mobCoat.runs);
        b.bool("Drips off", "Blood drips off the body and stains the ground below.", () -> c.mobCoat.drips, v -> c.mobCoat.drips = v, d.mobCoat.drips);
        if (com.simpleblood.compat.PhysicsModRagdolls.available()) {
            b.heading("Physics Mod ragdolls");
            physicsMod(b);
        }
    }

    private static void water(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.preview(PreviewScene.Kind.WATER, playerColour(c));
        b.bool("Blood clouds", "Under water, drops turn into drifting clouds.", () -> c.underwater.transformToFog, v -> c.underwater.transformToFog = v, d.underwater.transformToFog);
        b.slider("Cloud size", "How big the clouds get.", 30, 300, 10, "%", () -> c.underwater.fogSize, v -> c.underwater.fogSize = v, d.underwater.fogSize);
        b.slider("Cloud lifetime", "How long they hang around.", 30, 200, 10, "%", () -> c.underwater.fogLifetime, v -> c.underwater.fogLifetime = v, d.underwater.fogLifetime);
        b.slider("Cloud opacity", "How thick they look.", 30, 200, 10, "%", () -> c.underwater.fogOpacity, v -> c.underwater.fogOpacity = v, d.underwater.fogOpacity);
    }

    private static void sound(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.bool("Sounds", "All the noises the mod makes.", () -> c.audio.soundEnabled, v -> c.audio.soundEnabled = v, d.audio.soundEnabled);
        b.slider("Volume", "Louder or quieter.", 0, 200, 5, "%", () -> c.audio.soundVolume, v -> c.audio.soundVolume = v, d.audio.soundVolume);
        b.slider("Pitch", "Higher or lower.", 50, 150, 5, "%", () -> c.audio.soundPitch, v -> c.audio.soundPitch = v, d.audio.soundPitch);
        b.bool("Landing sounds", "Little taps and plinks when drops land.", () -> c.audio.landingSounds, v -> c.audio.landingSounds = v, d.audio.landingSounds);
        b.bool("Footstep sounds", "Squelch.", () -> c.audio.footstepSounds, v -> c.audio.footstepSounds = v, d.audio.footstepSounds);
    }

    private static void shaders(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.note("These only matter with an Iris shader pack on. Wet, shiny puddles need the pack's labPBR setting on.");
        b.preview(PreviewScene.Kind.WATER, playerColour(c));
        b.slider("Clouds with shaders", "With a shader pack on, clouds are drawn darker and denser so they sit in the water. This sets how thick they are then.",
                10, 200, 10, "%", () -> c.underwater.shaderCloudOpacity, v -> c.underwater.shaderCloudOpacity = v, d.underwater.shaderCloudOpacity);
        b.preview(PreviewScene.Kind.PUDDLE, playerColour(c));
        b.bool("Skip swaying blocks", "With a shader pack on, leaves, grass, plants, vines and lanterns take no blood: the pack sways them, and blood on them would stay put. Drops fall on through.",
                () -> c.surfaces.skipSwayingBlocks, v -> c.surfaces.skipSwayingBlocks = v, d.surfaces.skipSwayingBlocks);
    }

    private static void performance(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.note("For slower machines. The Performance preset sets all of these at once.");
        b.slider("Drop cap", "Drops allowed in the air at once. The rest just do not happen.", 100, 6000, 100, "",
                () -> c.general.particleBudget, v -> c.general.particleBudget = v, d.general.particleBudget);
        b.slider("Max painted faces", "Block faces that can hold blood at once. The oldest go first.", 128, 3000, 64, "", () -> c.surfaces.maxTiles, v -> c.surfaces.maxTiles = v, d.surfaces.maxTiles);
        b.slider("Render distance", "Blood further away than this is not drawn.", 16, 128, 4, " blocks", () -> c.surfaces.renderDistance, v -> c.surfaces.renderDistance = v, d.surfaces.renderDistance);
    }

    private static void interfacePage(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.bool("Menu button", "The Blood button on the title and pause screens. Without it, type /simpleblood to get here.",
                () -> c.general.menuButton, v -> c.general.menuButton = v, d.general.menuButton);
    }

    // ------------------------------------------------------------------ kinds

    private static void kinds(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        SimpleBloodConfig.KindSettings k = c.kinds, dk = d.kinds;
        b.note("Mobs with nothing to bleed throw what they are made of. Which mob throws what is set under Who bleeds.");
        b.heading("Bone");
        b.slider("Amount", "Chips and dust per hit (skeletons, strays, bogged).", 0, 300, 10, "%", () -> k.boneAmount, v -> k.boneAmount = v, dk.boneAmount);
        b.slider("Dust", "How thick the puffs of bone dust look.", 10, 200, 10, "%", () -> k.boneDustOpacity, v -> k.boneDustOpacity = v, dk.boneDustOpacity);
        b.heading("Metal");
        b.slider("Amount", "Flakes per hit (golems).", 0, 300, 10, "%", () -> k.metalAmount, v -> k.metalAmount = v, dk.metalAmount);
        b.bool("Glint", "Flakes flash as they turn into the light, and twinkle where they lie.", () -> k.metalGlint, v -> k.metalGlint = v, dk.metalGlint);
        b.heading("Wood");
        b.slider("Amount", "Splinters per hit (the creaking).", 0, 300, 10, "%", () -> k.woodAmount, v -> k.woodAmount = v, dk.woodAmount);
        b.slider("Resin", "Share of drops that stay resin, which pools like blood.", 0, 100, 5, "%", () -> k.resinShare, v -> k.resinShare = v, dk.resinShare);
        b.heading("Embers");
        b.slider("Amount", "Embers per hit (blazes, magma cubes).", 0, 300, 10, "%", () -> k.emberAmount, v -> k.emberAmount = v, dk.emberAmount);
        b.bool("Smoke", "A wisp of smoke now and then among the embers.", () -> k.emberSmoke, v -> k.emberSmoke = v, dk.emberSmoke);
        b.heading("Sparks");
        b.slider("Amount", "Sparks per hit (allays, vexes).", 0, 300, 10, "%", () -> k.sparkAmount, v -> k.sparkAmount = v, dk.sparkAmount);
        b.heading("Wind");
        b.slider("Amount", "Gusts per hit (breezes).", 0, 300, 10, "%", () -> k.windAmount, v -> k.windAmount = v, dk.windAmount);
        b.slider("Thickness", "How thick the gusts look.", 10, 200, 10, "%", () -> k.windOpacity, v -> k.windOpacity = v, dk.windOpacity);
        b.heading("Pieces");
        b.slider("Lifetime", "How long chips, flakes, splinters, embers and sparks last.", 25, 300, 25, "%", () -> k.pieceLifetime, v -> k.pieceLifetime = v, dk.pieceLifetime);
        b.heading("Glowing blood");
        b.bool("Glow", "Blood marked Glow in the Mobs table (glow squid ink) glows in the dark.", () -> k.glow, v -> k.glow = v, dk.glow);
        b.slider("Cloud thickness", "How thick glowing clouds under water look.", 10, 200, 10, "%", () -> k.glowCloudOpacity, v -> k.glowCloudOpacity = v, dk.glowCloudOpacity);
    }

    // ------------------------------------------------------------------ physics mod

    private static void physicsMod(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        SimpleBloodConfig.PhysicsModSettings p = c.physicsMod, dp = d.physicsMod;
        b.bool("Ragdolls bleed", "Physics Mod ragdolls keep bleeding, splash where they land and smear the ground.",
                () -> c.general.physicsModRagdolls, v -> c.general.physicsModRagdolls = v, d.general.physicsModRagdolls);
        b.slider("Bleed time", "How long a ragdoll keeps bleeding. It bleeds less and less over this time.", 1, 30, 1, "s",
                () -> p.bleedSeconds, v -> p.bleedSeconds = v, dp.bleedSeconds);
        b.slider("Drips", "How much limbs drip, more while they are thrown about.", 0, 300, 10, "%", () -> p.drips, v -> p.drips = v, dp.drips);
        b.slider("Splashes", "Drops thrown up where a limb lands hard.", 0, 300, 10, "%", () -> p.splashes, v -> p.splashes = v, dp.splashes);
        b.heading("Stains");
        b.bool("Stains", "A stain where a limb lands hard, on the floor, a wall or the ceiling.", () -> p.stains, v -> p.stains = v, dp.stains);
        b.slider("Stain size", "How big those stains are. Harder hits stain more.", 25, 300, 25, "%", () -> p.stainSize, v -> p.stainSize = v, dp.stainSize);
        b.slider("Whole ragdolls", "Share of hard landings of a whole ragdoll that stain. Broken ragdolls and pieces always do.", 0, 100, 5, "%",
                () -> p.wholeRagdollStains, v -> p.wholeRagdollStains = v, dp.wholeRagdollStains);
        b.heading("Smears");
        b.bool("Smears", "Limbs sliding along the ground leave a trail.", () -> p.smears, v -> p.smears = v, dp.smears);
        b.slider("How often", "How often a sliding limb smears.", 10, 200, 10, "%", () -> p.smearAmount, v -> p.smearAmount = v, dp.smearAmount);
        b.heading("Pieces");
        b.bool("Pieces bleed", "Mobs that break into blocky or fractured pieces bleed too, not just ragdolls.", () -> p.pieces, v -> p.pieces = v, dp.pieces);
    }

    // ------------------------------------------------------------------ mobs

    /** Search text, kept across rebuilds. */
    public static String mobFilter = "";

    private static void mobs(Builder b) {
        SimpleBloodConfig c = b.cfg;
        com.simpleblood.MobTable.sync(c); // every mob this game has, none it does not
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
            if (!SimpleBlood.vanillaMobExists(id)) continue; // not in this version of the game
            if (!f.isEmpty() && !id.contains(f) && !pretty(id).toLowerCase(Locale.ROOT).contains(f)) continue;
            SimpleBloodConfig.VanillaEntities.EntitySettings s = c.vanillaEntities.entities.get(id);
            mobRow(b, id, () -> s.bloodColor, v -> s.bloodColor = v, () -> s.kind, v -> s.kind = v,
                    () -> s.enabled, v -> s.enabled = v, () -> s.canDripAtLowHealth, v -> s.canDripAtLowHealth = v,
                    () -> s.transformToStains, v -> s.transformToStains = v,
                    () -> s.glows, v -> s.glows = v, null);
            shown++;
        }
        if (shown == 0) b.note("Nothing found.");
    }

    /** Modded page: every mob your other mods add, grouped by mod, plus entries for mods that are not here. */
    public static String newModdedId = "";
    public static String moddedFilter = "";

    private static void modded(Builder b) {
        SimpleBloodConfig c = b.cfg;
        SimpleBloodConfig.ModdedEntities m = c.moddedEntities;
        Widget.Container addRow = new Widget.Container();
        addRow.w = b.rowW; addRow.h = 20;
        Widgets.TextField find = new Widgets.TextField();
        find.hint = "Find a mob...";
        find.set(moddedFilter);
        find.onChange = v -> { moddedFilter = v; b.host.refreshList(); };
        find.x = 0; find.y = 0; find.w = Math.min(160, (b.rowW - 70) / 2); find.h = 20;
        Widgets.TextField id = new Widgets.TextField();
        id.hint = "modid:mob_name";
        id.set(newModdedId);
        id.onChange = v -> newModdedId = v;
        id.x = find.w + 8; id.y = 0; id.w = Math.min(160, (b.rowW - 70) / 2); id.h = 20;
        id.tooltip = List.of("A mob that is not listed below (one spawned by a command, say): type its id and press Add.");
        Runnable addAction = () -> {
            String v = newModdedId.trim();
            if (!v.contains(":") || v.endsWith(":")) { b.host.toast("Needs to look like modid:mob_name"); return; }
            if (m.customEntities.containsKey(v)) { b.host.toast("Already on the list"); return; }
            m.editable(v);
            SimpleBlood.LOGGER.info("Added modded entity via config UI: {}", v);
            newModdedId = "";
            b.host.rebuildPage();
        };
        id.onEnter = addAction;
        Widgets.Button add = new Widgets.Button("Add", addAction);
        add.x = id.x + id.w + 4; add.y = 0; add.w = 50; add.h = 20;
        addRow.add(find); addRow.add(id); addRow.add(add);
        b.fixed(new Offset(addRow), 20);

        Map<String, List<Identifier>> byMod = com.simpleblood.MobTable.moddedByMod();
        java.util.Set<String> here = new java.util.HashSet<>();
        for (List<Identifier> ids : byMod.values()) for (Identifier eid : ids) here.add(eid.toString());
        List<String> missing = new ArrayList<>();
        for (String eid : m.customEntities.keySet()) if (!here.contains(eid)) missing.add(eid);
        missing.sort(String::compareTo);
        if (byMod.isEmpty() && missing.isEmpty()) {
            b.gap(6);
            b.note("No other mod adds mobs. When one does, its mobs show up here on their own.");
            return;
        }
        b.note("Every mob your other mods add shows up here, with a first guess at its blood from its name. Change anything and it is kept; x goes back to the guess.");
        String f = moddedFilter.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (Map.Entry<String, List<Identifier>> mod : byMod.entrySet()) {
            boolean headed = false;
            for (Identifier eid : mod.getValue()) {
                String full = eid.toString();
                if (!f.isEmpty() && !full.contains(f) && !pretty(eid.getPath()).toLowerCase(Locale.ROOT).contains(f)) continue;
                if (!headed) {
                    b.heading(com.simpleblood.Platform.modName(mod.getKey()));
                    mobHeader(b, true);
                    headed = true;
                }
                moddedRow(b, m, full, eid.getPath());
                shown++;
            }
        }
        if (!missing.isEmpty()) {
            boolean headed = false;
            for (String eid : missing) {
                if (!f.isEmpty() && !eid.contains(f)) continue;
                if (!headed) {
                    b.heading("Not installed");
                    b.note("Set up for mods that are not here right now. Kept for when they are back.");
                    mobHeader(b, true);
                    headed = true;
                }
                moddedRow(b, m, eid, eid);
                shown++;
            }
        }
        if (shown == 0) b.note("Nothing found.");
    }

    /**
     * One modded mob. A guessed mob shows the guess; changing anything makes it a real
     * entry, and x takes the entry away again (back to the guess, or off the list).
     */
    private static void moddedRow(Builder b, SimpleBloodConfig.ModdedEntities m, String eid, String shownAs) {
        Supplier<SimpleBloodConfig.ModdedEntities.ModdedEntitySettings> now = () -> {
            SimpleBloodConfig.ModdedEntities.ModdedEntitySettings s = m.effective(eid);
            return s != null ? s : m.editable(eid);
        };
        Runnable remove = m.isGuess(eid) ? null : () -> { m.customEntities.remove(eid); b.host.rebuildPage(); };
        mobRow(b, shownAs, () -> now.get().bloodColor, v -> m.editable(eid).bloodColor = v,
                () -> now.get().kind, v -> m.editable(eid).kind = v,
                () -> now.get().enabled, v -> m.editable(eid).enabled = v,
                () -> now.get().canDripAtLowHealth, v -> m.editable(eid).canDripAtLowHealth = v,
                () -> now.get().transformToStains, v -> m.editable(eid).transformToStains = v,
                () -> now.get().glows, v -> m.editable(eid).glows = v,
                remove);
    }

    // Mob table geometry, shared by the header and the rows so the labels sit over the boxes.
    private static final int MOB_ROW_H = 26, SWATCH_W = 82, KIND_W = 66, CHECK_W = 17, CHECK_GAP = 27;

    /**
     * Left edges of the columns of a mob row: swatch, kind, on, drip, pool, glow. The controls hug
     * the right edge but never drift more than a comfortable reading distance from the names
     * on a very wide screen.
     */
    private static int[] mobColumns(int x, int w, boolean removable) {
        int right = x + Math.min(w, 540) - 10 - (removable ? 22 : 0);
        int glow = right - CHECK_W;
        int pool = glow - CHECK_GAP;
        int drip = pool - CHECK_GAP;
        int on = drip - CHECK_GAP;
        int kind = on - 12 - KIND_W;
        int swatch = kind - 8 - SWATCH_W;
        return new int[]{swatch, kind, on, drip, pool, glow};
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
                g.textCentered("Glow", col[5] + CHECK_W / 2, y + 4, Widgets.TEXT_DIM);
                g.fill(x, y + 15, x + w, y + 16, 0x30FFFFFF);
            }
        };
        head.w = b.rowW; head.h = 18;
        head.tooltip = List.of("On: bleeds", "Drip: keeps dripping when hurt", "Pool: leaves puddles and clouds",
                "Glow: glows in the dark");
        b.widget(head, 18);
    }

    private static void mobRow(Builder b, String id, IntSupplier colGet, IntConsumer colSet,
                               Supplier<String> kindGet, Consumer<String> kindSet,
                               Supplier<Boolean> onGet, Consumer<Boolean> onSet,
                               Supplier<Boolean> dripGet, Consumer<Boolean> dripSet,
                               Supplier<Boolean> stainGet, Consumer<Boolean> stainSet,
                               Supplier<Boolean> glowGet, Consumer<Boolean> glowSet,
                               Runnable remove) {
        MobRow row = new MobRow(b, id, colGet, colSet, kindGet, kindSet, onGet, onSet, dripGet, dripSet, stainGet, stainSet,
                glowGet, glowSet, remove);
        row.x = 0;
        row.y = b.cursor;
        row.w = b.rowW;
        row.even = (b.rowIndex++ & 1) == 0;
        b.panel.add(row);
        b.cursor += row.h;
    }

    /** One mob: name, colour swatch, kind, and four checkboxes (bleeds / drips / stains / glows). */
    private static final class MobRow extends Widget.Container {
        final String id;
        final Widgets.Swatch swatch;
        final Widgets.Cycle kind;
        final Widgets.Check on, drip, stain, glow;
        final Widgets.Button remove;
        final Supplier<Boolean> onGet;
        boolean even;

        MobRow(Builder b, String id, IntSupplier colGet, IntConsumer colSet, Supplier<String> kindGet, Consumer<String> kindSet,
               Supplier<Boolean> onGet, Consumer<Boolean> onSet, Supplier<Boolean> dripGet, Consumer<Boolean> dripSet,
               Supplier<Boolean> stainGet, Consumer<Boolean> stainSet,
               Supplier<Boolean> glowGet, Consumer<Boolean> glowSet, Runnable removeAction) {
            this.id = id;
            this.onGet = onGet;
            this.h = MOB_ROW_H;
            swatch = add(new Widgets.Swatch(colGet, null));
            swatch.onClick = () -> b.host.openPicker(colGet.getAsInt(), colSet);
            swatch.w = SWATCH_W; swatch.h = 18;
            swatch.tooltip = List.of("Colour");
            kind = add(new Widgets.Cycle(KIND_KEYS, KIND_LABELS, kindGet, kindSet));
            kind.w = KIND_W; kind.h = 18;
            kind.tooltip = List.of("Liquid: drips and pools", "Debris: dry bits, no puddles", "Ember: embers that cool to ash, hiss in water",
                    "Powder: flat piles, like snow", "Bone: dust puffs and spinning chips", "Metal: tumbling flakes that glint",
                    "Wood: splinters and pooling resin", "Spirit: glowing sparks that float away", "Wind: little gusts");
            on = add(new Widgets.Check(onGet, onSet));
            on.tooltip = List.of("Bleeds");
            drip = add(new Widgets.Check(dripGet, dripSet));
            drip.tooltip = List.of("Keeps dripping when hurt");
            stain = add(new Widgets.Check(stainGet, stainSet));
            stain.tooltip = List.of("Leaves puddles and clouds");
            glow = add(new Widgets.Check(glowGet, glowSet));
            glow.tooltip = List.of("Glows in the dark (fresh puddles too)");
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
            glow.x = col[5]; glow.y = cy;
            if (remove != null) { remove.x = col[5] + CHECK_W + 8; remove.y = y + (h - 14) / 2; }
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

    /** "zombie_villager" -> "Zombie Villager", "mod:thing" -> "mod: Thing". */
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
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String current = gson.toJson(b.cfg);
        for (Presets.Preset p : Presets.ALL) {
            // Hovering a preset plays the diorama the way it would look with that preset.
            SimpleBloodConfig look = gson.fromJson(current, SimpleBloodConfig.class);
            p.apply().accept(look);
            b.previewWith(look);
            b.action(p.name(), p.description(), "Apply", () -> {
                p.apply().accept(b.cfg);
                BloodSurfaces.clear();
                b.host.toast(p.name() + " applied");
                b.host.rebuildPage();
            });
        }
        b.previewWith(null);
        b.preview(null, null);
    }

    /**
     * Wraps a container so its children's positions stay relative to it while the scroll
     * panel moves it around: the panel only ever moves the wrapper.
     */
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
