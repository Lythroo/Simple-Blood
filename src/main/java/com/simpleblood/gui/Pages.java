package com.simpleblood.gui;

import com.simpleblood.Lang;

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

public final class Pages {

    private Pages() {}

    public interface Host {
        void openPicker(int current, IntConsumer apply);
        void openPicker(int current, IntConsumer apply, String note);
        SimpleBloodConfig config();
        void rebuildPage();
        void refreshList();
        void toast(String text);
        void openPopup(Widget.Container panel);
        void closePopup();
        int screenWidth();
        int screenHeight();
    }

    public record Page(String group, String title, String hint, boolean pane, boolean searchable, Consumer<Builder> build) {}

    private static final String[] KIND_KEYS = {"liquid", "debris", "ember", "powder", "bone", "metal", "wood", "spirit", "wind"};
    private static final String[] KIND_LABELS = {"simpleblood.gui.labels.blood", "simpleblood.gui.labels.debris", "simpleblood.gui.labels.ember", "simpleblood.gui.labels.powder", "simpleblood.gui.labels.bone", "simpleblood.gui.labels.metal", "simpleblood.gui.labels.wood", "simpleblood.gui.labels.spirit", "simpleblood.gui.labels.wind"};

    public static final class Builder {
        public final Widget.ScrollPanel panel;
        public final Widget.Container header;
        public final Host host;
        public final SimpleBloodConfig cfg;
        public final SimpleBloodConfig defaults = new SimpleBloodConfig();
        private final boolean listOnly;
        private int cursor = 4;
        private int headerCursor = 6;
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

        private final int controlW;

        public int headerHeight() {
            return headerCursor > 6 ? headerCursor + 2 : 0;
        }

        public void fixed(Widget w, int height) {
            if (listOnly || filter != null) return;
            w.x = header.x + 8;
            w.y = header.y + headerCursor;
            header.add(w);
            headerCursor += height + 6;
        }

        private PreviewScene.Kind kind;
        private IntSupplier kindColour;
        private java.util.EnumSet<PreviewScene.Aspect> aspects = java.util.EnumSet.allOf(PreviewScene.Aspect.class);

        private SimpleBloodConfig previewConfig;
        private int figure;
        private Supplier<com.simpleblood.BloodKind> bloodKind;
        private com.simpleblood.HitContext.Weapon weapon;
        private String weaponItem;

        public void weapon(com.simpleblood.HitContext.Weapon weapon, String item) {
            this.weapon = weapon;
            this.weaponItem = item;
        }

        public void bloodKind(Supplier<com.simpleblood.BloodKind> kind) {
            this.bloodKind = kind;
        }

        public void figure(int figure) {
            this.figure = figure;
        }

        public void preview(PreviewScene.Kind kind, IntSupplier colour) {
            this.kind = kind;
            this.kindColour = colour;
            this.aspects = java.util.EnumSet.allOf(PreviewScene.Aspect.class);
        }

        public void previewWith(SimpleBloodConfig config) {
            this.previewConfig = config;
        }

        public void preview(PreviewScene.Kind kind, IntSupplier colour, PreviewScene.Aspect first, PreviewScene.Aspect... rest) {
            this.kind = kind;
            this.kindColour = colour;
            this.aspects = java.util.EnumSet.of(first, rest);
        }

        public void search(String text) {
            filter = text.toLowerCase(Locale.ROOT);
        }

        void searchPage(String title) {
            filterPage = title;
            filterSection = null;
            filterShown = false;
        }

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
            h.x = 10;
            h.y = cursor + (cursor <= 4 ? 0 : 8);
            h.w = rowW - 20;
            panel.add(h);
            cursor = h.y + h.h + 2;
            rowIndex = 0;
        }

        public void note(String text) {
            if (filter != null) return;
            int width = rowW - 20;
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
            l.x = 10;
            l.y = y;
            l.w = width;
            panel.add(l);
            return y + 10;
        }

        public void wide(Widget w) {
            if (filter != null) return;
            w.x = 10;
            w.y = cursor + 3;
            w.w = rowW - 20;
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
            row(name, tip, control, null, isDefault, reset);
        }

        private Widgets.Row lastRow;

        private void row(String name, String tip, Widget control, Widget side, Supplier<Boolean> isDefault, Runnable reset) {
            lastRow = null;
            if (!admit(name, tip)) return;
            Widgets.Row r = new Widgets.Row(name, tip, control, isDefault, reset);
            if (side != null) r.side(side);
            r.previewText = tip;
            if (kind != null && cfg.general.livePreviews) {
                r.previewKind = kind;
                r.previewColour = kindColour;
                r.previewAspects = aspects;
                r.previewConfig = previewConfig;
                r.previewFigure = figure;
                r.previewBloodKind = bloodKind;
                r.previewWeapon = weapon;
                r.previewItem = weaponItem;
            }
            r.x = 0;
            r.y = cursor;
            r.w = rowW;
            r.even = (rowIndex++ & 1) == 0;
            panel.add(r);
            lastRow = r;
            cursor += r.h;
        }

        public void bool(String name, String tip, Supplier<Boolean> get, Consumer<Boolean> set, boolean def) {
            row(name, tip, new Widgets.Toggle(get, set), () -> get.get() == def, () -> set.accept(def));
        }

        public void slider(String name, String tip, int min, int max, int step, String unit,
                           IntSupplier get, IntConsumer set, int def) {
            Widgets.Slider s = new Widgets.Slider(min, max, step, get, set).unit(unit);
            s.w = controlW + valueRoom(s);
            row(name, tip, s, () -> get.getAsInt() == def, () -> set.accept(def));
        }

        public void soundSlider(String name, String tip, com.simpleblood.BloodSounds.Preview sound,
                                IntSupplier get, IntConsumer set, int def) {
            Widgets.Slider s = new Widgets.Slider(0, 200, 5, get, set).unit("%");
            s.w = controlW - 24 + valueRoom(s);
            Widgets.PlayButton play = new Widgets.PlayButton(() -> com.simpleblood.BloodSounds.togglePreview(sound),
                    () -> cfg.audio.soundEnabled, () -> com.simpleblood.BloodSounds.previewPlaying() == sound,
                    com.simpleblood.BloodSounds::previewSounding, com.simpleblood.BloodSounds::previewTick);
            play.tooltip = List.of(Lang.t("simpleblood.gui.page.play_it_over_and_over"), Lang.t("simpleblood.gui.page.the_panel_on_the_right"));
            String key = sound.name();
            if (cfg.audio.muted == null) cfg.audio.muted = new java.util.ArrayList<>();
            Widgets.Check on = new Widgets.Check(() -> !cfg.audio.muted(key), v -> {
                if (v) cfg.audio.muted.remove(key);
                else if (!cfg.audio.muted(key)) cfg.audio.muted.add(key);
            });
            on.tooltip = List.of(Lang.t("simpleblood.gui.page.this_sound_on_or_off"));
            row(name, tip, s, new Widgets.Pair(on, play), () -> get.getAsInt() == def && !cfg.audio.muted(key),
                    () -> { set.accept(def); cfg.audio.muted.remove(key); });
        }

        public void choice(String name, String tip, String[] keys, String[] labels,
                           Supplier<String> get, Consumer<String> set, String def) {
            Widget c = keys.length <= 2 ? new Widgets.Segmented(keys, labels, get, set) : new Widgets.Dropdown(keys, labels, get, set);
            c.w = controlW;
            row(name, tip, c, () -> def.equals(get.get()), () -> set.accept(def));
        }

        public void colour(String name, String tip, IntSupplier get, IntConsumer set, int def) {
            colour(name, tip, null, get, set, def);
        }

        public void colour(String name, String tip, String pickerNote, IntSupplier get, IntConsumer set, int def) {
            Widgets.Swatch s = new Widgets.Swatch(get, null);
            s.onClick = () -> host.openPicker(get.getAsInt(), set, pickerNote);
            s.w = Math.min(110, controlW);
            row(name, tip, s, () -> (get.getAsInt() & 0xFFFFFF) == (def & 0xFFFFFF), () -> set.accept(def));
        }

        public void action(String name, String tip, String button, Runnable run) {
            Widgets.Button b = new Widgets.Button(button, run);
            b.w = Math.min(controlW, net.minecraft.client.Minecraft.getInstance().font.width(button) + 24);
            row(name, tip, b, null, null);
        }

        public void gap(int px) { cursor += px; }

        public void icon(net.minecraft.world.item.ItemStack stack) {
            if (lastRow != null) lastRow.icon = stack;
        }

        private static int valueRoom(Widgets.Slider s) {
            net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
            return Math.max(font.width(s.format.apply(s.min)), font.width(s.format.apply(s.max))) + 6;
        }
    }

    public static List<Page> all() {
        List<Page> pages = new ArrayList<>();
        pages.add(new Page(Lang.t("simpleblood.gui.all.home"), Lang.t("simpleblood.gui.all.overview"), Lang.t("simpleblood.gui.all.the_big_switch_how_much"), true, true, Pages::overview));
        pages.add(new Page(Lang.t("simpleblood.gui.all.home"), Lang.t("simpleblood.gui.all.presets"), Lang.t("simpleblood.gui.all.pick_a_look_one_click"), true, true, Pages::presets));
        pages.add(new Page(Lang.t("simpleblood.gui.all.blood"), Lang.t("simpleblood.gui.all.bleeding"), Lang.t("simpleblood.gui.all.when_blood_comes_out_hits"), true, true, Pages::bleeding));
        pages.add(new Page(Lang.t("simpleblood.gui.all.blood"), Lang.t("simpleblood.gui.all.drops"), Lang.t("simpleblood.gui.all.how_drops_fly_and_which"), true, true, Pages::drops));
        pages.add(new Page(Lang.t("simpleblood.gui.all.blood"), Lang.t("simpleblood.gui.all.giblets"), Lang.t("simpleblood.gui.all.chunks_that_fly_off"), true, true, Pages::giblets));
        pages.add(new Page(Lang.t("simpleblood.gui.all.blood"), Lang.t("simpleblood.gui.all.colour_and_type"), Lang.t("simpleblood.gui.all.what_you_bleed_and_how"), true, true, Pages::colour));
        pages.add(new Page(Lang.t("simpleblood.gui.all.world"), Lang.t("simpleblood.gui.all.puddles"), Lang.t("simpleblood.gui.all.blood_on_blocks"), true, true, Pages::puddles));
        pages.add(new Page(Lang.t("simpleblood.gui.all.world"), Lang.t("simpleblood.gui.all.footprints"), Lang.t("simpleblood.gui.all.trails_through_fresh_blood"), true, true, Pages::footprints));
        pages.add(new Page(Lang.t("simpleblood.gui.all.world"), Lang.t("simpleblood.gui.all.weather"), Lang.t("simpleblood.gui.all.rain_and_heat"), true, true, Pages::weather));
        pages.add(new Page(Lang.t("simpleblood.gui.all.bodies"), Lang.t("simpleblood.gui.all.blood_on_mobs"), Lang.t("simpleblood.gui.all.blood_that_sticks_to_bodies"), true, true, Pages::bodies));
        pages.add(new Page(Lang.t("simpleblood.gui.all.bodies"), Lang.t("simpleblood.gui.all.ragdolls"), Lang.t("simpleblood.gui.all.physics_mod_ragdollified_saro_s"), true, true, Pages::ragdolls));
        pages.add(new Page(Lang.t("simpleblood.gui.all.combat"), Lang.t("simpleblood.gui.all.weapons"), Lang.t("simpleblood.gui.all.the_wound_each_weapon_makes"), true, true, Pages::wounds));
        pages.add(new Page(Lang.t("simpleblood.gui.all.combat"), Lang.t("simpleblood.gui.all.armor"), Lang.t("simpleblood.gui.all.sparks_shards_and_a_clank"), true, true, Pages::weapons));
        pages.add(new Page(Lang.t("simpleblood.gui.all.combat"), Lang.t("simpleblood.gui.all.damage_types"), Lang.t("simpleblood.gui.all.what_kinds_of_damage_draw"), true, true, Pages::damageTypes));
        pages.add(new Page(Lang.t("simpleblood.gui.all.creatures"), Lang.t("simpleblood.gui.all.mobs"), Lang.t("simpleblood.gui.all.which_mob_bleeds_what"), false, false, Pages::mobs));
        pages.add(new Page(Lang.t("simpleblood.gui.all.creatures"), Lang.t("simpleblood.gui.all.modded_mobs"), Lang.t("simpleblood.gui.all.mobs_from_other_mods"), false, false, Pages::modded));
        pages.add(new Page(Lang.t("simpleblood.gui.all.creatures"), Lang.t("simpleblood.gui.all.materials"), Lang.t("simpleblood.gui.all.bone_metal_wood_embers_sparks"), true, true, Pages::kinds));
        pages.add(new Page(Lang.t("simpleblood.gui.all.effects"), Lang.t("simpleblood.gui.all.water"), Lang.t("simpleblood.gui.all.clouds_under_water"), true, true, Pages::water));
        pages.add(new Page(Lang.t("simpleblood.gui.all.effects"), Lang.t("simpleblood.gui.all.sound"), Lang.t("simpleblood.gui.all.splats_drips_squelches"), true, true, Pages::sound));
        pages.add(new Page(Lang.t("simpleblood.gui.all.effects"), Lang.t("simpleblood.gui.all.shaders"), Lang.t("simpleblood.gui.all.for_iris_shader_packs"), true, true, Pages::shaders));
        pages.add(new Page(Lang.t("simpleblood.gui.all.advanced"), Lang.t("simpleblood.gui.all.performance"), Lang.t("simpleblood.gui.all.limits_for_slower_machines"), true, true, Pages::performance));
        pages.add(new Page(Lang.t("simpleblood.gui.all.advanced"), Lang.t("simpleblood.gui.all.interface"), Lang.t("simpleblood.gui.all.this_screen_and_the_menu"), true, true, Pages::interfacePage));
        pages.add(new Page(Lang.t("simpleblood.gui.all.advanced"), Lang.t("simpleblood.gui.all.backup"), Lang.t("simpleblood.gui.all.save_share_and_reset"), true, true, Pages::backup));
        return pages;
    }

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
            b.filter = null;
            b.note(Lang.t("simpleblood.gui.search.nothing_found_for", text.trim()));
        }
    }

    private static IntSupplier playerColour(SimpleBloodConfig c) {
        return () -> c.player.clientPlayerBloodColor;
    }

    private static IntSupplier mobColour(SimpleBloodConfig c) {
        return () -> {
            SimpleBloodConfig.VanillaEntities.EntitySettings z = c.vanillaEntities.entities.get("zombie");
            return z != null ? z.bloodColor : 0x660303;
        };
    }

    private static void overview(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.preview(PreviewScene.Kind.HIT, mobColour(c));
        b.bool(Lang.t("simpleblood.gui.overview.blood"), Lang.t("simpleblood.gui.overview.off_means_no_blood_at"), () -> c.general.modEnabled, v -> c.general.modEnabled = v, d.general.modEnabled);
        b.slider(Lang.t("simpleblood.gui.overview.how_much"), Lang.t("simpleblood.gui.overview.scales_all_blood_at_once"), 10, 300, 5, "%",
                () -> c.general.bloodAmount, v -> c.general.bloodAmount = v, d.general.bloodAmount);
        b.preview(null, null);
        if (TestEffects.inWorld()) {
            b.heading(Lang.t("simpleblood.gui.overview.try_it"));
            b.action(Lang.t("simpleblood.gui.overview.drip_on_what_you_are"), Lang.t("simpleblood.gui.overview.aim_at_a_block_then"), Lang.t("simpleblood.gui.overview.drip"), TestEffects::drops);
            b.action(Lang.t("simpleblood.gui.overview.big_splat_away_from_you"), Lang.t("simpleblood.gui.overview.aim_at_a_block_then"), Lang.t("simpleblood.gui.overview.spray"), TestEffects::burst);
            b.action(Lang.t("simpleblood.gui.overview.mop_it_all_up"), Lang.t("simpleblood.gui.overview.removes_every_puddle_and_stain"), Lang.t("simpleblood.gui.overview.clear"), () -> { TestEffects.clearBlood(); b.host.toast(Lang.t("simpleblood.gui.overview.all_clean")); });
        }
    }

    private static void colour(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.heading(Lang.t("simpleblood.gui.colour.you"));
        b.figure(PreviewScene.YOU);
        b.bloodKind(() -> com.simpleblood.BloodKind.parse(c.player.clientPlayerBloodKind));
        b.preview(PreviewScene.Kind.HIT, playerColour(c));
        b.colour(Lang.t("simpleblood.gui.colour.your_colour"), Lang.t("simpleblood.gui.colour.what_you_bleed"), Lang.t("simpleblood.gui.colour.only_you_see_this_until"),
                () -> c.player.clientPlayerBloodColor, v -> c.player.clientPlayerBloodColor = v, d.player.clientPlayerBloodColor);
        b.choice(Lang.t("simpleblood.gui.colour.your_type"), Lang.t("simpleblood.gui.colour.blood_bone_metal_embers_snow"),
                KIND_KEYS, Lang.all(KIND_LABELS), () -> c.player.clientPlayerBloodKind, v -> c.player.clientPlayerBloodKind = v, d.player.clientPlayerBloodKind);
        b.bloodKind(null);
        b.figure(PreviewScene.ZOMBIE);
        b.preview(null, null);
        b.heading(Lang.t("simpleblood.gui.colour.showing_them_to_others"));
        b.wide(new Widgets.Notice(() -> ownColourText(c), () -> ownColourTone(c), SkinColours::ownColour));
        b.action(Lang.t("simpleblood.gui.colour.upload"), Lang.t("simpleblood.gui.colour.saves_your_colour_and_type"), Lang.t("simpleblood.gui.colour.upload"), () -> confirmUpload(b));
        b.action(Lang.t("simpleblood.gui.colour.save_as_file"), Lang.t("simpleblood.gui.colour.to_upload_the_skin_yourself"),
                Lang.t("simpleblood.gui.colour.save_file"), () -> SkinColours.putInOwnSkin(c.player.clientPlayerBloodColor, false, result -> skinDone(b, result)));
        b.action(Lang.t("simpleblood.gui.colour.take_them_out"), Lang.t("simpleblood.gui.colour.removes_your_colour_and_type"), Lang.t("simpleblood.gui.colour.remove"), () -> confirmRemove(b));

        b.heading(Lang.t("simpleblood.gui.colour.other_players"));
        b.figure(PreviewScene.OTHER);
        b.preview(PreviewScene.Kind.HIT, () -> c.player.otherPlayersBloodColor);
        b.colour(Lang.t("simpleblood.gui.colour.other_players"), Lang.t("simpleblood.gui.colour.for_players_with_nothing_saved"),
                () -> c.player.otherPlayersBloodColor, v -> c.player.otherPlayersBloodColor = v, d.player.otherPlayersBloodColor);
        b.figure(PreviewScene.ZOMBIE);
        b.preview(null, null);
        b.bool(Lang.t("simpleblood.gui.colour.colours_from_skins"), Lang.t("simpleblood.gui.colour.use_the_colour_and_type"),
                () -> c.player.colourFromSkins, v -> c.player.colourFromSkins = v, d.player.colourFromSkins);
    }

    private static String ownColourText(SimpleBloodConfig c) {
        int carried = SkinColours.ownColour();
        int mine = c.player.clientPlayerBloodColor & 0xFFFFFF;
        if (carried == SkinColours.READING) return Lang.t("simpleblood.gui.own_colour_text.checking_your_skin");
        if (carried == mine && !ownKindShown(c)) return Lang.t("simpleblood.gui.own_colour_text.others_see_your_colour_not");
        if (carried == mine) return SkinColours.justUploaded() ? Lang.t("simpleblood.gui.own_colour_text.uploaded_others_see_it_once") : Lang.t("simpleblood.gui.own_colour_text.others_see_your_colour_and");
        if (carried == SkinColours.NONE && SkinColours.justUploaded()) return Lang.t("simpleblood.gui.own_colour_text.taken_out_others_see_it");
        if (carried == SkinColours.NONE) return Lang.t("simpleblood.gui.own_colour_text.only_you_see_your_colour");
        return Lang.t("simpleblood.gui.own_colour_text.others_still_see", String.format(Locale.ROOT, "#%06X", carried));
    }

    private static int ownColourTone(SimpleBloodConfig c) {
        int carried = SkinColours.ownColour();
        if (carried == SkinColours.READING) return Widgets.Notice.QUIET;
        return carried == (c.player.clientPlayerBloodColor & 0xFFFFFF) && ownKindShown(c) ? Widgets.Notice.GOOD : Widgets.Notice.WARN;
    }

    private static boolean ownKindShown(SimpleBloodConfig c) {
        String carried = SkinColours.ownKind();
        return (carried == null ? "liquid" : carried).equals(c.player.clientPlayerBloodKind);
    }

    private static void confirmUpload(Builder b) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen back = Screens.current(mc);
        Screens.open(mc, new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
            Screens.open(mc, back);
            if (yes) SkinColours.putInOwnSkin(b.cfg.player.clientPlayerBloodColor, true, result -> skinDone(b, result));
        }, net.minecraft.network.chat.Component.literal(Lang.t("simpleblood.gui.confirm_upload.change_your_minecraft_skin")),
                net.minecraft.network.chat.Component.literal(Lang.t("simpleblood.gui.confirm_upload.text"))));
    }

    private static void confirmRemove(Builder b) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen back = Screens.current(mc);
        Screens.open(mc, new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
            Screens.open(mc, back);
            if (yes) SkinColours.putInOwnSkin(SkinColours.NONE, true, result -> skinDone(b, result));
        }, net.minecraft.network.chat.Component.literal(Lang.t("simpleblood.gui.confirm_remove.take_your_blood_colour_out")),
                net.minecraft.network.chat.Component.literal(Lang.t("simpleblood.gui.confirm_remove.text"))));
    }

    private static void skinDone(Builder b, SkinColours.Result result) {
        switch (result) {
            case SAVED -> { SkinColours.showSavedSkin(); b.host.toast(Lang.t("simpleblood.gui.skin_done.saved_now_upload_it_as")); }
            case UPLOADED -> b.host.toast(Lang.t("simpleblood.gui.skin_done.uploaded_others_see_it_after"));
            case NO_COLOUR -> b.host.toast(Lang.t("simpleblood.gui.skin_done.your_skin_has_no_colour"));
            case ALREADY_THERE -> b.host.toast(Lang.t("simpleblood.gui.skin_done.your_skin_already_has_this"));
            case NOT_LOGGED_IN -> b.host.toast(Lang.t("simpleblood.gui.skin_done.not_logged_in_to_a"));
            case TOO_SOON -> b.host.toast(Lang.t("simpleblood.gui.skin_done.too_many_tries_wait_a"));
            case FAILED -> b.host.toast(Lang.t("simpleblood.gui.skin_done.could_not_change_your_skin"));
        }
    }

    private static void bleeding(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.heading(Lang.t("simpleblood.gui.bleeding.hits"));
        b.preview(PreviewScene.Kind.HIT, mobColour(c), PreviewScene.Aspect.DRIPS, PreviewScene.Aspect.SPLASH, PreviewScene.Aspect.DIRECTIONAL);
        b.bool(Lang.t("simpleblood.gui.bleeding.hit_burst"), Lang.t("simpleblood.gui.bleeding.blood_sprays_when_something_is"), () -> c.particles.hitBurst, v -> c.particles.hitBurst = v, d.particles.hitBurst);
        b.preview(PreviewScene.Kind.HIT, mobColour(c), PreviewScene.Aspect.DRIPS, PreviewScene.Aspect.SPLASH);
        b.slider(Lang.t("simpleblood.gui.bleeding.amount"), Lang.t("simpleblood.gui.bleeding.drops_per_hit"), 20, 300, 10, "%", () -> c.hitBurst.burstIntensity, v -> c.hitBurst.burstIntensity = v, d.hitBurst.burstIntensity);
        b.slider(Lang.t("simpleblood.gui.bleeding.duration"), Lang.t("simpleblood.gui.bleeding.how_long_each_spray_lasts"), 30, 200, 10, "%", () -> c.hitBurst.burstDuration, v -> c.hitBurst.burstDuration = v, d.hitBurst.burstDuration);
        b.slider(Lang.t("simpleblood.gui.bleeding.spread"), Lang.t("simpleblood.gui.bleeding.how_wide_it_sprays"), 30, 200, 10, "%", () -> c.hitBurst.burstSpread, v -> c.hitBurst.burstSpread = v, d.hitBurst.burstSpread);
        b.preview(null, null);
        b.slider(Lang.t("simpleblood.gui.bleeding.cooldown"), Lang.t("simpleblood.gui.bleeding.pause_between_sprays_on_one"), 0, 500, 25, Lang.t("simpleblood.gui.bleeding.ms"), () -> c.hitBurst.damageCooldown, v -> c.hitBurst.damageCooldown = v, d.hitBurst.damageCooldown);
        b.bool(Lang.t("simpleblood.gui.bleeding.players_bleed"), Lang.t("simpleblood.gui.bleeding.players_bleed_too_except_in"), () -> c.player.playerBleed, v -> c.player.playerBleed = v, d.player.playerBleed);

        b.heading(Lang.t("simpleblood.gui.bleeding.deaths"));
        b.preview(PreviewScene.Kind.DEATH, mobColour(c));
        b.bool(Lang.t("simpleblood.gui.bleeding.death_burst"), Lang.t("simpleblood.gui.bleeding.a_big_spray_on_death"), () -> c.particles.deathBurst, v -> c.particles.deathBurst = v, d.particles.deathBurst);
        b.slider(Lang.t("simpleblood.gui.bleeding.amount"), Lang.t("simpleblood.gui.bleeding.drops_on_death"), 20, 300, 10, "%", () -> c.deathBurst.deathIntensity, v -> c.deathBurst.deathIntensity = v, d.deathBurst.deathIntensity);
        b.slider(Lang.t("simpleblood.gui.bleeding.spread"), Lang.t("simpleblood.gui.bleeding.how_far_it_flies"), 30, 200, 10, "%", () -> c.deathBurst.deathSpread, v -> c.deathBurst.deathSpread = v, d.deathBurst.deathSpread);
        b.bool(Lang.t("simpleblood.gui.bleeding.pool_under_the_body"), Lang.t("simpleblood.gui.bleeding.a_pool_spreads_where_the"),
                () -> c.surfaces.corpsePools, v -> c.surfaces.corpsePools = v, d.surfaces.corpsePools);

        b.heading(Lang.t("simpleblood.gui.bleeding.dripping_wounds"));
        b.preview(PreviewScene.Kind.DRIP, mobColour(c));
        b.bool(Lang.t("simpleblood.gui.bleeding.dripping_wounds"), Lang.t("simpleblood.gui.bleeding.hurt_mobs_keep_dripping_for"), () -> c.particles.lowHealthDrip, v -> c.particles.lowHealthDrip = v, d.particles.lowHealthDrip);
        b.preview(null, null);
        b.slider(Lang.t("simpleblood.gui.bleeding.starts_below"), Lang.t("simpleblood.gui.bleeding.health_left_when_the_dripping"), 10, 90, 5, "%", () -> c.lowHealth.threshold, v -> c.lowHealth.threshold = v, d.lowHealth.threshold);
        b.preview(PreviewScene.Kind.DRIP, mobColour(c));
        b.slider(Lang.t("simpleblood.gui.bleeding.how_often"), Lang.t("simpleblood.gui.bleeding.drips_per_second_roughly"), 20, 500, 10, "%", () -> c.lowHealth.dripFrequency, v -> c.lowHealth.dripFrequency = v, d.lowHealth.dripFrequency);
        b.slider(Lang.t("simpleblood.gui.bleeding.how_much"), Lang.t("simpleblood.gui.bleeding.drops_per_drip"), 20, 300, 10, "%", () -> c.lowHealth.dripIntensity, v -> c.lowHealth.dripIntensity = v, d.lowHealth.dripIntensity);
        b.preview(null, null);
        b.slider(Lang.t("simpleblood.gui.bleeding.lasts"), Lang.t("simpleblood.gui.bleeding.stops_this_long_after_the"), 3, 120, 1, Lang.t("simpleblood.unit.seconds"), () -> c.lowHealth.dripDurationSeconds, v -> c.lowHealth.dripDurationSeconds = v, d.lowHealth.dripDurationSeconds);

    }

    private static void giblets(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.preview(PreviewScene.Kind.GIBLETS, mobColour(c));
        b.bool(Lang.t("simpleblood.gui.giblets.giblets"), Lang.t("simpleblood.gui.giblets.meat_chunks_fly_off_on"),
                () -> c.gore.giblets, v -> c.gore.giblets = v, d.gore.giblets);
        b.slider(Lang.t("simpleblood.gui.giblets.amount"), Lang.t("simpleblood.gui.giblets.how_many_chunks_fly_off"), 10, 300, 10, "%", () -> c.gore.gibletAmount, v -> c.gore.gibletAmount = v, d.gore.gibletAmount);
        b.bool(Lang.t("simpleblood.gui.giblets.blood_trail"), Lang.t("simpleblood.gui.giblets.chunks_bleed_and_splash_on"), () -> c.gore.gibletTrails, v -> c.gore.gibletTrails = v, d.gore.gibletTrails);
    }

    private static void damage(Builder b, String name, String tip, String... entries) {
        java.util.List<String> list = b.cfg.general.noBleedDamageTypes;
        b.bool(name, tip, () -> {
            for (String e : entries) if (!list.contains(e)) return true;
            return false;
        }, on -> {
            if (on) list.removeAll(java.util.List.of(entries));
            else for (String e : entries) if (!list.contains(e)) list.add(e);
            b.host.refreshList();
        }, true);
    }

    private record DamageGroup(String name, String tip, String... entries) {}

    private static final DamageGroup[] DAMAGE_GROUPS = {
            new DamageGroup("simpleblood.gui.labels.falls", "simpleblood.gui.labels.hitting_the_ground_too_hard", "minecraft:fall"),
            new DamageGroup("simpleblood.gui.labels.fire_and_lava", "simpleblood.gui.labels.burning_lava_magma_campfires", "#minecraft:is_fire"),
            new DamageGroup("simpleblood.gui.labels.explosions", "simpleblood.gui.labels.creepers_tnt_beds_in_the", "#minecraft:is_explosion"),
            new DamageGroup("simpleblood.gui.labels.arrows_and_tridents", "simpleblood.gui.labels.anything_shot_or_thrown", "#minecraft:is_projectile"),
            new DamageGroup("simpleblood.gui.labels.magic", "simpleblood.gui.labels.potions_poison_the_wither", "minecraft:magic", "minecraft:indirect_magic", "minecraft:wither"),
            new DamageGroup("simpleblood.gui.labels.thorns_and_spikes", "simpleblood.gui.labels.thorns_cactus_berry_bushes",
                    "minecraft:thorns", "minecraft:cactus", "minecraft:sweet_berry_bush", "minecraft:stalagmite", "minecraft:falling_stalactite"),
            new DamageGroup("simpleblood.gui.labels.freezing", "simpleblood.gui.labels.powder_snow", "#minecraft:is_freezing"),
            new DamageGroup("simpleblood.gui.labels.lightning", "simpleblood.gui.labels.struck_by_lightning", "#minecraft:is_lightning"),
            new DamageGroup("simpleblood.gui.labels.starving", "simpleblood.gui.labels.an_empty_hunger_bar", "minecraft:starve"),
    };

    private static void damageTypes(Builder b) {
        b.heading(Lang.t("simpleblood.gui.damage_types.quick_groups"));
        for (DamageGroup g : DAMAGE_GROUPS) damage(b, Lang.t(g.name()), Lang.t(g.tip()), g.entries());

        java.util.List<net.minecraft.core.Holder.Reference<net.minecraft.world.damagesource.DamageType>> types = damageTypeList();
        if (types.isEmpty()) {
            b.heading(Lang.t("simpleblood.gui.damage_types.every_damage_type"));
            b.note(Lang.t("simpleblood.gui.damage_types.join_a_world_to_see"));
            return;
        }
        java.util.Map<String, java.util.List<net.minecraft.core.Holder.Reference<net.minecraft.world.damagesource.DamageType>>> byMod = new java.util.TreeMap<>((x, y) ->
                x.equals("minecraft") ? (y.equals("minecraft") ? 0 : -1) : y.equals("minecraft") ? 1 : x.compareTo(y));
        for (var t : types) {
            String id = keyId(t.key().toString());
            byMod.computeIfAbsent(id.substring(0, Math.max(0, id.indexOf(':'))), k -> new java.util.ArrayList<>()).add(t);
        }
        for (var mod : byMod.entrySet()) {
            b.heading(mod.getKey().equals("minecraft") ? Lang.t("simpleblood.gui.damage_types.minecraft") : com.simpleblood.Platform.modName(mod.getKey()));
            mod.getValue().sort(java.util.Comparator.comparing(t -> keyId(t.key().toString())));
            for (var t : mod.getValue()) {
                String id = keyId(t.key().toString());
                String group = groupOf(t, id);
                String tip = group != null ? Lang.t("simpleblood.gui.damage_types.group", Lang.t(group)) : mod.getKey().equals("minecraft") ? id : Lang.t("simpleblood.gui.damage_types.draws_blood_like_a_hit");
                b.bool(niceName(id), tip, () -> com.simpleblood.SimpleBlood.drawsBlood(t, id), on -> setDamage(b.cfg, t, id, on), defaultDraws(t, id));
            }
        }
    }

    private static java.util.List<net.minecraft.core.Holder.Reference<net.minecraft.world.damagesource.DamageType>> damageTypeList() {
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return java.util.List.of();
        try {
            //? if <=1.21.1 {
            /*return level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).holders().collect(java.util.stream.Collectors.toList());
            *///?} else {
            return level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).listElements().collect(java.util.stream.Collectors.toList());
            //?}
        } catch (Throwable t) {
            return java.util.List.of();
        }
    }

    private static String keyId(String key) {
        int slash = key.lastIndexOf(" / ");
        return slash < 0 ? key : key.substring(slash + 3, key.length() - 1);
    }

    private static String niceName(String id) {
        String path = id.substring(id.indexOf(':') + 1).replace('_', ' ').replace('/', ' ');
        return path.isEmpty() ? id : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    private static String groupOf(net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType> type, String id) {
        for (DamageGroup g : DAMAGE_GROUPS) {
            for (String e : g.entries()) {
                if (e.startsWith("#")) {
                    if (type.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE, com.simpleblood.Ids.parse(e.substring(1))))) return g.name();
                } else if (e.equals(id)) {
                    return g.name();
                }
            }
        }
        return null;
    }

    private static boolean defaultDraws(net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType> type, String id) {
        SimpleBloodConfig.GeneralSettings g = com.simpleblood.SimpleBloodClient.getConfig().general;
        boolean always = g.bleedDamageTypes.remove(id), never = g.noBleedDamageTypes.remove(id);
        boolean draws = com.simpleblood.SimpleBlood.drawsBlood(type, id);
        if (always) g.bleedDamageTypes.add(id);
        if (never) g.noBleedDamageTypes.add(id);
        return draws;
    }

    private static void setDamage(SimpleBloodConfig cfg, net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType> type, String id, boolean on) {
        cfg.general.bleedDamageTypes.remove(id);
        cfg.general.noBleedDamageTypes.remove(id);
        if (com.simpleblood.SimpleBlood.drawsBlood(type, id) != on) (on ? cfg.general.bleedDamageTypes : cfg.general.noBleedDamageTypes).add(id);
    }

    private static void drops(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.heading(Lang.t("simpleblood.gui.drops.flight"));
        b.preview(PreviewScene.Kind.HIT, mobColour(c), PreviewScene.Aspect.DRIPS, PreviewScene.Aspect.SPLASH);
        b.slider(Lang.t("simpleblood.gui.drops.size"), Lang.t("simpleblood.gui.drops.how_big_each_drop_is"), 50, 300, 10, "%", () -> c.particles.particleSize, v -> c.particles.particleSize = v, d.particles.particleSize);
        b.slider(Lang.t("simpleblood.gui.drops.lifetime"), Lang.t("simpleblood.gui.drops.how_long_drops_stay_in"), 10, 200, 10, "%", () -> c.particles.particleLifetime, v -> c.particles.particleLifetime = v, d.particles.particleLifetime);
        b.slider(Lang.t("simpleblood.gui.drops.gravity"), Lang.t("simpleblood.gui.drops.how_fast_drops_fall"), 50, 200, 10, "%", () -> c.particles.particleGravity, v -> c.particles.particleGravity = v, d.particles.particleGravity);
        b.slider(Lang.t("simpleblood.gui.drops.drag"), Lang.t("simpleblood.gui.drops.more_drag_shorter_flights"), 0, 200, 10, "%", () -> c.particles.particleDrag, v -> c.particles.particleDrag = v, d.particles.particleDrag);

        b.heading(Lang.t("simpleblood.gui.drops.direction"));
        b.preview(PreviewScene.Kind.HIT, mobColour(c), PreviewScene.Aspect.DIRECTIONAL);
        b.bool(Lang.t("simpleblood.gui.drops.directional"), Lang.t("simpleblood.gui.drops.blood_flies_back_toward_the"), () -> c.directional.enabled, v -> c.directional.enabled = v, d.directional.enabled);
        b.preview(PreviewScene.Kind.HIT, mobColour(c), PreviewScene.Aspect.DIRECTIONAL, PreviewScene.Aspect.SPLASH);
        b.slider(Lang.t("simpleblood.gui.drops.share"), Lang.t("simpleblood.gui.drops.how_much_follows_the_blow"), 0, 100, 5, "%", () -> c.directional.share, v -> c.directional.share = v, d.directional.share);
        b.preview(PreviewScene.Kind.WEAPONS, mobColour(c));
        b.bool(Lang.t("simpleblood.gui.drops.weapon_flavour"), Lang.t("simpleblood.gui.drops.each_weapon_bleeds_its_own"), () -> c.directional.weaponFlavour, v -> c.directional.weaponFlavour = v, d.directional.weaponFlavour);
        b.preview(PreviewScene.Kind.HIT, mobColour(c), PreviewScene.Aspect.EXIT);
        b.bool(Lang.t("simpleblood.gui.drops.exit_spray"), Lang.t("simpleblood.gui.drops.a_bit_comes_out_the"), () -> c.directional.entrySpatter, v -> c.directional.entrySpatter = v, d.directional.entrySpatter);
    }

    private static void puddles(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.preview(PreviewScene.Kind.PUDDLE, mobColour(c));
        b.bool(Lang.t("simpleblood.gui.puddles.puddles"), Lang.t("simpleblood.gui.puddles.blood_stays_where_drops_land"), () -> c.surfaces.enabled, v -> { c.surfaces.enabled = v; if (!v) BloodSurfaces.clear(); }, d.surfaces.enabled);
        b.choice(Lang.t("simpleblood.gui.puddles.pixel_size"), Lang.t("simpleblood.gui.puddles.block_sized_pixels_or_double"), new String[]{"16", "8"}, new String[]{Lang.t("simpleblood.gui.puddles.normal"), Lang.t("simpleblood.gui.puddles.chunky")},
                () -> Integer.toString(c.surfaces.resolution), v -> { c.surfaces.resolution = Integer.parseInt(v); BloodSurfaces.clear(); }, Integer.toString(d.surfaces.resolution));
        b.choice(Lang.t("simpleblood.gui.puddles.shading"), Lang.t("simpleblood.gui.puddles.follow_the_block_or_a"), new String[]{"block", "pattern"}, new String[]{Lang.t("simpleblood.gui.puddles.follow_block"), Lang.t("simpleblood.gui.puddles.pattern")},
                () -> c.surfaces.highlightMode, v -> c.surfaces.highlightMode = v, d.surfaces.highlightMode);
        b.slider(Lang.t("simpleblood.gui.puddles.amount"), Lang.t("simpleblood.gui.puddles.how_much_each_drop_paints"), 25, 300, 5, "%", () -> c.surfaces.amount, v -> c.surfaces.amount = v, d.surfaces.amount);
        b.preview(PreviewScene.Kind.PUDDLE, mobColour(c));
        b.bool(Lang.t("simpleblood.gui.puddles.walls_and_ceilings"), Lang.t("simpleblood.gui.puddles.blood_on_walls_and_ceilings"), () -> c.surfaces.wallsAndCeilings, v -> c.surfaces.wallsAndCeilings = v, d.surfaces.wallsAndCeilings);
        b.preview(null, null);
        b.bool(Lang.t("simpleblood.gui.puddles.detailed_shapes"), Lang.t("simpleblood.gui.puddles.blood_follows_the_real_shape"),
                () -> c.surfaces.detailedShapes, v -> { c.surfaces.detailedShapes = v; BloodSurfaces.clear(); }, d.surfaces.detailedShapes);
        b.heading(Lang.t("simpleblood.gui.puddles.drying"));
        b.preview(PreviewScene.Kind.DRYING, mobColour(c));
        b.slider(Lang.t("simpleblood.gui.puddles.opacity"), Lang.t("simpleblood.gui.puddles.how_see_through_the_blood"), 20, 100, 2, "%", () -> c.surfaces.opacity, v -> c.surfaces.opacity = v, d.surfaces.opacity);
        b.slider(Lang.t("simpleblood.gui.puddles.lasts"), Lang.t("simpleblood.gui.puddles.seconds_until_it_dries"), 5, 600, 5, Lang.t("simpleblood.unit.seconds"), () -> c.surfaces.lifetimeSeconds, v -> c.surfaces.lifetimeSeconds = v, d.surfaces.lifetimeSeconds);
        b.heading(Lang.t("simpleblood.gui.puddles.mixing"));
        b.preview(PreviewScene.Kind.MIX, mobColour(c));
        b.bool(Lang.t("simpleblood.gui.puddles.colours_mix"), Lang.t("simpleblood.gui.puddles.blood_of_different_colours_blends"),
                () -> c.surfaces.mixing, v -> c.surfaces.mixing = v, d.surfaces.mixing);
        b.slider(Lang.t("simpleblood.gui.puddles.marbling"), Lang.t("simpleblood.gui.puddles.swirls_where_it_meets_rather"), 0, 100, 5, "%",
                () -> c.surfaces.marbling, v -> c.surfaces.marbling = v, d.surfaces.marbling);
    }

    private static void footprints(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.preview(PreviewScene.Kind.FOOTPRINTS, mobColour(c));
        b.bool(Lang.t("simpleblood.gui.footprints.footprints"), Lang.t("simpleblood.gui.footprints.fresh_blood_leaves_a_trail"), () -> c.surfaces.footprints, v -> c.surfaces.footprints = v, d.surfaces.footprints);
        b.slider(Lang.t("simpleblood.gui.footprints.trail_length"), Lang.t("simpleblood.gui.footprints.steps_until_feet_are_clean"), 2, 16, 1, "", () -> c.surfaces.footprintSteps, v -> c.surfaces.footprintSteps = v, d.surfaces.footprintSteps);
        b.heading(Lang.t("simpleblood.gui.footprints.walking_through_blood"));
        b.bool(Lang.t("simpleblood.gui.footprints.wading"), Lang.t("simpleblood.gui.footprints.steps_push_smear_and_tread"), () -> c.surfaces.wading, v -> c.surfaces.wading = v, d.surfaces.wading);
        b.slider(Lang.t("simpleblood.gui.footprints.splashes"), Lang.t("simpleblood.gui.footprints.drops_kicked_up_by_steps"), 0, 300, 10, "%", () -> c.surfaces.wadeSplash, v -> c.surfaces.wadeSplash = v, d.surfaces.wadeSplash);
        b.bool(Lang.t("simpleblood.gui.footprints.wakes"), Lang.t("simpleblood.gui.footprints.steps_send_a_ripple_across"), () -> c.surfaces.wakes, v -> c.surfaces.wakes = v, d.surfaces.wakes);
        b.slider(Lang.t("simpleblood.gui.footprints.trampling"), Lang.t("simpleblood.gui.footprints.trodden_blood_dries_sooner"), 0, 300, 10, "%", () -> c.surfaces.trample, v -> c.surfaces.trample = v, d.surfaces.trample);
        b.preview(null, null);
        b.heading(Lang.t("simpleblood.gui.footprints.first_person"));
        b.bool(Lang.t("simpleblood.gui.footprints.steps_follow_view_bob"), Lang.t("simpleblood.gui.footprints.your_steps_land_as_the"), () -> c.surfaces.viewBobSteps, v -> c.surfaces.viewBobSteps = v, d.surfaces.viewBobSteps);
        if (com.simpleblood.surface.Footprints.firstPersonModel()) b.note(Lang.t("simpleblood.gui.footprints.first_person_model_is_installed"));
    }

    private static void weather(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.preview(PreviewScene.Kind.RAIN, mobColour(c));
        b.bool(Lang.t("simpleblood.gui.weather.rain_washes"), Lang.t("simpleblood.gui.weather.rain_washes_blood_away_faster"), () -> c.surfaces.rainWashes, v -> c.surfaces.rainWashes = v, d.surfaces.rainWashes);
        b.preview(null, null);
        b.bool(Lang.t("simpleblood.gui.weather.heat_dries_blood"), Lang.t("simpleblood.gui.weather.blood_dries_fast_near_fire"),
                () -> c.surfaces.heatDries, v -> c.surfaces.heatDries = v, d.surfaces.heatDries);

    }

    private static void bodies(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.preview(PreviewScene.Kind.COAT, mobColour(c));
        b.bool(Lang.t("simpleblood.gui.bodies.blood_on_mobs"), Lang.t("simpleblood.gui.bodies.hits_leave_blood_on_bodies"),
                () -> c.mobCoat.enabled, v -> c.mobCoat.enabled = v, d.mobCoat.enabled);
        b.slider(Lang.t("simpleblood.gui.bodies.amount"), Lang.t("simpleblood.gui.bodies.how_much_stays_on_the"), 0, 300, 10, "%", () -> c.mobCoat.amount, v -> c.mobCoat.amount = v, d.mobCoat.amount);
        b.preview(PreviewScene.Kind.COAT_DRYING, mobColour(c));
        b.slider(Lang.t("simpleblood.gui.bodies.lasts"), Lang.t("simpleblood.gui.bodies.seconds_until_it_dries"), 5, 300, 5, Lang.t("simpleblood.unit.seconds"),
                () -> c.mobCoat.lifetimeSeconds, v -> c.mobCoat.lifetimeSeconds = v, d.mobCoat.lifetimeSeconds);
        b.preview(PreviewScene.Kind.COAT, mobColour(c));
        b.bool(Lang.t("simpleblood.gui.bodies.runs_down"), Lang.t("simpleblood.gui.bodies.heavy_blood_runs_down_the"), () -> c.mobCoat.runs, v -> c.mobCoat.runs = v, d.mobCoat.runs);
        b.bool(Lang.t("simpleblood.gui.bodies.drips_off"), Lang.t("simpleblood.gui.bodies.drips_off_and_stains_the"), () -> c.mobCoat.drips, v -> c.mobCoat.drips = v, d.mobCoat.drips);
    }

    private static void weapons(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        SimpleBloodConfig.CombatSettings w = c.combat, dw = d.combat;
        b.heading(Lang.t("simpleblood.gui.weapons.hard_armor"));
        b.preview(PreviewScene.Kind.ARMOR, mobColour(c));
        b.note(Lang.t("simpleblood.gui.weapons.metal_and_diamond_count_as"));
        b.slider(Lang.t("simpleblood.gui.weapons.blood_through_armor"), Lang.t("simpleblood.gui.weapons.blood_that_gets_through_hard"), 0, 100, 5, "%",
                () -> w.armorBleed, v -> w.armorBleed = v, dw.armorBleed);
        b.bool(Lang.t("simpleblood.gui.weapons.sparks"), Lang.t("simpleblood.gui.weapons.metal_hits_spark_off_armor"), () -> w.armorSparks, v -> w.armorSparks = v, dw.armorSparks);
        b.bool(Lang.t("simpleblood.gui.weapons.shards"), Lang.t("simpleblood.gui.weapons.flakes_chip_off_the_armor"), () -> w.armorShards, v -> w.armorShards = v, dw.armorShards);
        b.bool(Lang.t("simpleblood.gui.weapons.clank"), Lang.t("simpleblood.gui.weapons.a_metal_clank_or_a"), () -> w.armorClank, v -> w.armorClank = v, dw.armorClank);
    }

    private static void ragdolls(Builder b) {
        if (!com.simpleblood.compat.PhysicsModRagdolls.anyRagdollMod()) {
            b.note(Lang.t("simpleblood.gui.ragdolls.needs_a_ragdoll_mod_none"));
        }
        physicsMod(b);
    }

    private static final String[] WOUND_KEYS = {"blade", "axe", "mace", "arrow", "spear", "other"};
    private static final String[] WOUND_LABELS = {"simpleblood.gui.labels.sweep", "simpleblood.gui.labels.chop", "simpleblood.gui.labels.slam", "simpleblood.gui.labels.pierce", "simpleblood.gui.labels.skewer", "simpleblood.gui.labels.spray"};

    private static void wounds(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.note(Lang.t("simpleblood.gui.wounds.pick_another_wound_for_any"));
        b.preview(PreviewScene.Kind.WEAPONS, mobColour(c));
        b.heading(Lang.t("simpleblood.gui.wounds.wound_per_weapon"));
        b.weapon(com.simpleblood.HitContext.Weapon.BLADE, null);
        b.choice(Lang.t("simpleblood.gui.wounds.swords"), Lang.t("simpleblood.gui.wounds.a_fan_of_streaks_along"), WOUND_KEYS, Lang.all(WOUND_LABELS),
                () -> c.directional.swordWound, v -> { c.directional.swordWound = v; b.host.refreshList(); }, d.directional.swordWound);
        b.weapon(com.simpleblood.HitContext.Weapon.AXE, null);
        b.choice(Lang.t("simpleblood.gui.wounds.axes"), Lang.t("simpleblood.gui.wounds.a_heavy_splash_and_a"), WOUND_KEYS, Lang.all(WOUND_LABELS),
                () -> c.directional.axeWound, v -> { c.directional.axeWound = v; b.host.refreshList(); }, d.directional.axeWound);
        b.weapon(com.simpleblood.HitContext.Weapon.MACE, null);
        b.choice(Lang.t("simpleblood.gui.wounds.maces"), Lang.t("simpleblood.gui.wounds.slammed_down_a_crater_on"), WOUND_KEYS, Lang.all(WOUND_LABELS),
                () -> c.directional.maceWound, v -> { c.directional.maceWound = v; b.host.refreshList(); }, d.directional.maceWound);
        b.weapon(com.simpleblood.HitContext.Weapon.ARROW, null);
        b.choice(Lang.t("simpleblood.gui.wounds.shot_and_thrown"), Lang.t("simpleblood.gui.wounds.a_narrow_stream_out_the"), WOUND_KEYS, Lang.all(WOUND_LABELS),
                () -> c.directional.arrowWound, v -> { c.directional.arrowWound = v; b.host.refreshList(); }, d.directional.arrowWound);
        b.weapon(com.simpleblood.HitContext.Weapon.SPEAR, null);
        b.choice(Lang.t("simpleblood.gui.wounds.spears"), Lang.t("simpleblood.gui.wounds.a_deep_stab_out_the"), WOUND_KEYS, Lang.all(WOUND_LABELS),
                () -> c.directional.spearWound, v -> { c.directional.spearWound = v; b.host.refreshList(); }, d.directional.spearWound);
        b.weapon(com.simpleblood.HitContext.Weapon.OTHER, null);
        b.choice(Lang.t("simpleblood.gui.wounds.fists_and_the_rest"), Lang.t("simpleblood.gui.wounds.a_plain_burst"), WOUND_KEYS, Lang.all(WOUND_LABELS),
                () -> c.directional.fistWound, v -> { c.directional.fistWound = v; b.host.refreshList(); }, d.directional.fistWound);
        b.weapon(null, null);
        b.preview(null, null);
        b.note(Lang.t("simpleblood.gui.wounds.needs_weapon_flavour_on_blood"));
        detectedWeapons(b);
    }

    private static final String[] KIND_NAMES = {"simpleblood.gui.labels.swords", "simpleblood.gui.labels.axes", "simpleblood.gui.labels.maces", "simpleblood.gui.labels.shot_and_thrown", "simpleblood.gui.labels.spears", "simpleblood.gui.labels.the_rest"};

    private static void detectedWeapons(Builder b) {
        SimpleBloodConfig c = b.cfg;
        if (net.minecraft.client.Minecraft.getInstance().level == null) {
            b.heading(Lang.t("simpleblood.gui.detected_weapons.detected_weapons"));
            b.note(Lang.t("simpleblood.gui.detected_weapons.join_a_world_to_see"));
            return;
        }
        java.util.Map<String, java.util.List<net.minecraft.world.item.Item>> byMod = new java.util.TreeMap<>((x, y) ->
                x.equals("minecraft") ? (y.equals("minecraft") ? 0 : -1) : y.equals("minecraft") ? 1 : x.compareTo(y));
        for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
            com.simpleblood.WeaponKinds.Kind kind = com.simpleblood.WeaponKinds.of(item);
            if (kind.weapon() == com.simpleblood.HitContext.Weapon.OTHER && !c.directional.itemWounds.containsKey(id)) continue;
            byMod.computeIfAbsent(id.substring(0, id.indexOf(':')), k -> new java.util.ArrayList<>()).add(item);
        }
        for (var mod : byMod.entrySet()) {
            b.heading(mod.getKey().equals("minecraft") ? Lang.t("simpleblood.gui.detected_weapons.minecraft_weapons") : com.simpleblood.Platform.modName(mod.getKey()));
            for (net.minecraft.world.item.Item item : mod.getValue()) {
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
                net.minecraft.world.item.ItemStack stack;
                try {
                    stack = new net.minecraft.world.item.ItemStack(item);
                } catch (Throwable t) {
                    continue;
                }
                com.simpleblood.WeaponKinds.Kind kind = com.simpleblood.WeaponKinds.of(item);
                int k = woundIndex(kind.weapon());
                String autoKey = familyWound(c, kind.weapon());
                String[] keys = new String[WOUND_KEYS.length + 1], labels = new String[WOUND_KEYS.length + 1];
                keys[0] = "auto";
                labels[0] = Lang.t("simpleblood.gui.detected_weapons.auto", Lang.t(WOUND_LABELS[Math.max(0, java.util.Arrays.asList(WOUND_KEYS).indexOf(autoKey))]));
                System.arraycopy(WOUND_KEYS, 0, keys, 1, WOUND_KEYS.length);
                System.arraycopy(Lang.all(WOUND_LABELS), 0, labels, 1, WOUND_LABELS.length);
                b.preview(PreviewScene.Kind.WEAPONS, mobColour(c));
                b.weapon(kind.weapon(), id);
                b.choice(stack.getHoverName().getString(), Lang.t("simpleblood.gui.detected_weapons.told_by", Lang.t(KIND_NAMES[k]), kind.how() == com.simpleblood.WeaponKinds.How.EPIC_FIGHT ? Lang.t(kind.how().label) : Lang.t(kind.how().label).toLowerCase(Locale.ROOT)), keys, labels,
                        () -> c.directional.itemWounds.getOrDefault(id, "auto"),
                        v -> { if (v.equals("auto") || v.equals(familyWound(c, kind.weapon()))) c.directional.itemWounds.remove(id); else c.directional.itemWounds.put(id, v); },
                        "auto");
                b.icon(stack);
            }
        }
        b.weapon(null, null);
        b.preview(null, null);
    }

    private static int woundIndex(com.simpleblood.HitContext.Weapon w) {
        return switch (w) {
            case BLADE -> 0;
            case AXE -> 1;
            case MACE -> 2;
            case ARROW -> 3;
            case SPEAR -> 4;
            default -> 5;
        };
    }

    private static String familyWound(SimpleBloodConfig c, com.simpleblood.HitContext.Weapon w) {
        return switch (w) {
            case BLADE -> c.directional.swordWound;
            case AXE -> c.directional.axeWound;
            case MACE -> c.directional.maceWound;
            case ARROW -> c.directional.arrowWound;
            case SPEAR -> c.directional.spearWound;
            default -> c.directional.fistWound;
        };
    }

    private static void water(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.preview(PreviewScene.Kind.WATER, mobColour(c));
        b.bool(Lang.t("simpleblood.gui.water.blood_clouds"), Lang.t("simpleblood.gui.water.drops_become_clouds_under_water"), () -> c.underwater.transformToFog, v -> c.underwater.transformToFog = v, d.underwater.transformToFog);
        b.slider(Lang.t("simpleblood.gui.water.cloud_size"), Lang.t("simpleblood.gui.water.how_big_the_clouds_get"), 30, 300, 10, "%", () -> c.underwater.fogSize, v -> c.underwater.fogSize = v, d.underwater.fogSize);
        b.slider(Lang.t("simpleblood.gui.water.cloud_lifetime"), Lang.t("simpleblood.gui.water.how_long_they_hang_around"), 30, 200, 10, "%", () -> c.underwater.fogLifetime, v -> c.underwater.fogLifetime = v, d.underwater.fogLifetime);
        b.slider(Lang.t("simpleblood.gui.water.cloud_opacity"), Lang.t("simpleblood.gui.water.how_thick_they_look"), 30, 200, 10, "%", () -> c.underwater.fogOpacity, v -> c.underwater.fogOpacity = v, d.underwater.fogOpacity);
    }

    private static void sound(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.bool(Lang.t("simpleblood.gui.sound.sounds"), Lang.t("simpleblood.gui.sound.every_sound_of_the_mod"), () -> c.audio.soundEnabled, v -> c.audio.soundEnabled = v, d.audio.soundEnabled);
        b.slider(Lang.t("simpleblood.gui.sound.volume"), Lang.t("simpleblood.gui.sound.louder_or_quieter"), 0, 200, 5, "%", () -> c.audio.soundVolume, v -> c.audio.soundVolume = v, d.audio.soundVolume);
        b.slider(Lang.t("simpleblood.gui.sound.pitch"), Lang.t("simpleblood.gui.sound.higher_or_lower"), 50, 150, 5, "%", () -> c.audio.soundPitch, v -> c.audio.soundPitch = v, d.audio.soundPitch);
        b.heading(Lang.t("simpleblood.gui.sound.each_sound"));
        b.note(Lang.t("simpleblood.gui.sound.on_top_of_the_volume"));
        b.soundSlider(Lang.t("simpleblood.gui.sound.hits"), Lang.t("simpleblood.gui.sound.hits_and_deaths"), com.simpleblood.BloodSounds.Preview.HITS,
                () -> c.audio.hitVolume, v -> c.audio.hitVolume = v, d.audio.hitVolume);
        b.soundSlider(Lang.t("simpleblood.gui.sound.drops_landing"), Lang.t("simpleblood.gui.sound.drops_hitting_blocks_and_puddles"), com.simpleblood.BloodSounds.Preview.LANDING,
                () -> c.audio.landingVolume, v -> c.audio.landingVolume = v, d.audio.landingVolume);
        b.soundSlider(Lang.t("simpleblood.gui.sound.ledge_drips"), Lang.t("simpleblood.gui.sound.blood_dripping_off_ledges"), com.simpleblood.BloodSounds.Preview.LEDGE_DRIPS,
                () -> c.audio.ledgeDripVolume, v -> c.audio.ledgeDripVolume = v, d.audio.ledgeDripVolume);
        b.soundSlider(Lang.t("simpleblood.gui.sound.wound_drips"), Lang.t("simpleblood.gui.sound.a_badly_hurt_mob_dripping"), com.simpleblood.BloodSounds.Preview.WOUND_DRIPS,
                () -> c.audio.woundDripVolume, v -> c.audio.woundDripVolume = v, d.audio.woundDripVolume);
        b.soundSlider(Lang.t("simpleblood.gui.sound.mace_slaps"), Lang.t("simpleblood.gui.sound.the_wet_slap_of_a"), com.simpleblood.BloodSounds.Preview.SLAPS,
                () -> c.audio.slapVolume, v -> c.audio.slapVolume = v, d.audio.slapVolume);
        b.soundSlider(Lang.t("simpleblood.gui.sound.water"), Lang.t("simpleblood.gui.sound.blood_falling_into_water"), com.simpleblood.BloodSounds.Preview.WATER,
                () -> c.audio.waterVolume, v -> c.audio.waterVolume = v, d.audio.waterVolume);
        b.soundSlider(Lang.t("simpleblood.gui.sound.heat"), Lang.t("simpleblood.gui.sound.sizzling_near_fire_and_lava"), com.simpleblood.BloodSounds.Preview.HEAT,
                () -> c.audio.heatVolume, v -> c.audio.heatVolume = v, d.audio.heatVolume);
        b.soundSlider(Lang.t("simpleblood.gui.sound.pieces"), Lang.t("simpleblood.gui.sound.chips_flakes_and_giblets_landing"), com.simpleblood.BloodSounds.Preview.PIECES,
                () -> c.audio.pieceVolume, v -> c.audio.pieceVolume = v, d.audio.pieceVolume);
        b.soundSlider(Lang.t("simpleblood.gui.sound.footsteps"), Lang.t("simpleblood.gui.sound.squelching_through_blood"), com.simpleblood.BloodSounds.Preview.FOOTSTEPS,
                () -> c.audio.footstepVolume, v -> c.audio.footstepVolume = v, d.audio.footstepVolume);
        b.soundSlider(Lang.t("simpleblood.gui.sound.armor"), Lang.t("simpleblood.gui.sound.metal_clanking_on_armor"), com.simpleblood.BloodSounds.Preview.ARMOR,
                () -> c.audio.armorVolume, v -> c.audio.armorVolume = v, d.audio.armorVolume);
        b.soundSlider(Lang.t("simpleblood.gui.sound.limbs_tearing_off"), Lang.t("simpleblood.gui.sound.a_ragdoll_losing_a_head"), com.simpleblood.BloodSounds.Preview.TEARS,
                () -> c.audio.tearVolume, v -> c.audio.tearVolume = v, d.audio.tearVolume);
    }

    private static void shaders(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.note(Lang.t("simpleblood.gui.shaders.only_with_an_iris_shader"));
        b.preview(PreviewScene.Kind.WATER, mobColour(c));
        b.slider(Lang.t("simpleblood.gui.shaders.clouds_with_shaders"), Lang.t("simpleblood.gui.shaders.cloud_thickness_with_shaders_on"),
                10, 200, 10, "%", () -> c.underwater.shaderCloudOpacity, v -> c.underwater.shaderCloudOpacity = v, d.underwater.shaderCloudOpacity);
        b.preview(PreviewScene.Kind.PUDDLE, mobColour(c));
        b.bool(Lang.t("simpleblood.gui.shaders.skip_swaying_blocks"), Lang.t("simpleblood.gui.shaders.no_blood_on_swaying_plants"),
                () -> c.surfaces.skipSwayingBlocks, v -> c.surfaces.skipSwayingBlocks = v, d.surfaces.skipSwayingBlocks);
    }

    private static void performance(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.slider(Lang.t("simpleblood.gui.performance.drop_cap"), Lang.t("simpleblood.gui.performance.most_drops_in_the_air"), 100, 6000, 100, "",
                () -> c.general.particleBudget, v -> c.general.particleBudget = v, d.general.particleBudget);
        b.slider(Lang.t("simpleblood.gui.performance.max_painted_faces"), Lang.t("simpleblood.gui.performance.most_bloody_block_faces_oldest"), 128, 3000, 64, "", () -> c.surfaces.maxTiles, v -> c.surfaces.maxTiles = v, d.surfaces.maxTiles);
        b.slider(Lang.t("simpleblood.gui.performance.render_distance"), Lang.t("simpleblood.gui.performance.blood_beyond_this_is_hidden"), 16, 128, 4, Lang.t("simpleblood.gui.performance.blocks"), () -> c.surfaces.renderDistance, v -> c.surfaces.renderDistance = v, d.surfaces.renderDistance);
    }

    private static void interfacePage(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        b.bool(Lang.t("simpleblood.gui.interface_page.menu_button"), Lang.t("simpleblood.gui.interface_page.blood_button_on_title_and"),
                () -> c.general.menuButton, v -> c.general.menuButton = v, d.general.menuButton);
        b.bool(Lang.t("simpleblood.gui.interface_page.animations"), Lang.t("simpleblood.gui.interface_page.menu_animations"), () -> c.general.menuAnimations, v -> c.general.menuAnimations = v, d.general.menuAnimations);
        b.bool(Lang.t("simpleblood.gui.interface_page.slider_sounds"), Lang.t("simpleblood.gui.interface_page.sliders_click_as_they_move"), () -> c.general.sliderSounds, v -> c.general.sliderSounds = v, d.general.sliderSounds);
        b.bool(Lang.t("simpleblood.gui.interface_page.live_previews"), Lang.t("simpleblood.gui.interface_page.the_preview_scene_beside_settings"), () -> c.general.livePreviews,
                v -> { c.general.livePreviews = v; b.host.rebuildPage(); }, d.general.livePreviews);
    }

    private static void backup(Builder b) {
        b.heading(Lang.t("simpleblood.gui.backup.your_settings"));
        b.action(Lang.t("simpleblood.gui.backup.save_to_file"), Lang.t("simpleblood.gui.backup.pick_where_to_save_your"), Lang.t("simpleblood.gui.backup.save"), () -> SettingsBackup.saveToFile(b.host::toast));
        b.action(Lang.t("simpleblood.gui.backup.load_from_file"), Lang.t("simpleblood.gui.backup.pick_a_settings_file_to"), Lang.t("simpleblood.gui.backup.load"), () -> SettingsBackup.loadFromFile(b.host::toast, b.host::rebuildPage));
        b.action(Lang.t("simpleblood.gui.backup.copy_as_text"), Lang.t("simpleblood.gui.backup.to_paste_anywhere_like_a"), Lang.t("simpleblood.gui.backup.copy"), () -> b.host.toast(SettingsBackup.copy()));
        b.action(Lang.t("simpleblood.gui.backup.paste_from_text"), Lang.t("simpleblood.gui.backup.load_settings_someone_copied"), Lang.t("simpleblood.gui.backup.paste"), () -> {
            b.host.toast(SettingsBackup.paste());
            b.host.rebuildPage();
        });
        b.action(Lang.t("simpleblood.gui.backup.open_folder"), Lang.t("simpleblood.gui.backup.where_saved_settings_go_first"), Lang.t("simpleblood.gui.backup.open"), SettingsBackup::openFolder);
        b.action(Lang.t("simpleblood.gui.backup.reset_everything"), Lang.t("simpleblood.gui.backup.everything_back_to_default_cancel"), Lang.t("simpleblood.gui.backup.reset"), () -> {
            SimpleBloodConfig fresh = new SimpleBloodConfig();
            fresh.vanillaEntities.initializeDefaults();
            com.simpleblood.SimpleBloodClient.setConfig(fresh);
            BloodSurfaces.clear();
            b.host.toast(Lang.t("simpleblood.gui.backup.back_to_defaults"));
            b.host.rebuildPage();
        });
    }

    private static void kinds(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        SimpleBloodConfig.KindSettings k = c.kinds, dk = d.kinds;
        b.note(Lang.t("simpleblood.gui.kinds.what_bloodless_mobs_throw_set"));
        b.heading(Lang.t("simpleblood.gui.kinds.bone"));
        b.preview(PreviewScene.Kind.BONE, mobColour(c));
        b.slider(Lang.t("simpleblood.gui.kinds.amount"), Lang.t("simpleblood.gui.kinds.chips_per_hit_from_skeletons"), 0, 300, 10, "%", () -> k.boneAmount, v -> k.boneAmount = v, dk.boneAmount);
        b.slider(Lang.t("simpleblood.gui.kinds.dust"), Lang.t("simpleblood.gui.kinds.how_thick_the_dust_looks"), 10, 200, 10, "%", () -> k.boneDustOpacity, v -> k.boneDustOpacity = v, dk.boneDustOpacity);
        b.heading(Lang.t("simpleblood.gui.kinds.metal"));
        b.preview(PreviewScene.Kind.METAL, mobColour(c));
        b.slider(Lang.t("simpleblood.gui.kinds.amount"), Lang.t("simpleblood.gui.kinds.flakes_per_hit_from_golems"), 0, 300, 10, "%", () -> k.metalAmount, v -> k.metalAmount = v, dk.metalAmount);
        b.bool(Lang.t("simpleblood.gui.kinds.glint"), Lang.t("simpleblood.gui.kinds.flakes_catch_the_light"), () -> k.metalGlint, v -> k.metalGlint = v, dk.metalGlint);
        b.heading(Lang.t("simpleblood.gui.kinds.wood"));
        b.preview(PreviewScene.Kind.WOOD, mobColour(c));
        b.slider(Lang.t("simpleblood.gui.kinds.amount"), Lang.t("simpleblood.gui.kinds.splinters_per_hit_from_the"), 0, 300, 10, "%", () -> k.woodAmount, v -> k.woodAmount = v, dk.woodAmount);
        b.slider(Lang.t("simpleblood.gui.kinds.resin"), Lang.t("simpleblood.gui.kinds.drops_that_stay_resin_and"), 0, 100, 5, "%", () -> k.resinShare, v -> k.resinShare = v, dk.resinShare);
        b.heading(Lang.t("simpleblood.gui.kinds.embers"));
        b.preview(PreviewScene.Kind.EMBER, mobColour(c));
        b.slider(Lang.t("simpleblood.gui.kinds.amount"), Lang.t("simpleblood.gui.kinds.embers_per_hit_from_blazes"), 0, 300, 10, "%", () -> k.emberAmount, v -> k.emberAmount = v, dk.emberAmount);
        b.bool(Lang.t("simpleblood.gui.kinds.smoke"), Lang.t("simpleblood.gui.kinds.wisps_of_smoke_among_embers"), () -> k.emberSmoke, v -> k.emberSmoke = v, dk.emberSmoke);
        b.heading(Lang.t("simpleblood.gui.kinds.sparks"));
        b.preview(PreviewScene.Kind.SPIRIT, mobColour(c));
        b.slider(Lang.t("simpleblood.gui.kinds.amount"), Lang.t("simpleblood.gui.kinds.sparks_per_hit_from_vexes"), 0, 300, 10, "%", () -> k.sparkAmount, v -> k.sparkAmount = v, dk.sparkAmount);
        b.heading(Lang.t("simpleblood.gui.kinds.wind"));
        b.preview(PreviewScene.Kind.WIND, mobColour(c));
        b.slider(Lang.t("simpleblood.gui.kinds.amount"), Lang.t("simpleblood.gui.kinds.gusts_per_hit_from_breezes"), 0, 300, 10, "%", () -> k.windAmount, v -> k.windAmount = v, dk.windAmount);
        b.slider(Lang.t("simpleblood.gui.kinds.thickness"), Lang.t("simpleblood.gui.kinds.how_thick_the_gusts_look"), 10, 200, 10, "%", () -> k.windOpacity, v -> k.windOpacity = v, dk.windOpacity);
        b.heading(Lang.t("simpleblood.gui.kinds.pieces"));
        b.preview(PreviewScene.Kind.BONE, mobColour(c));
        b.slider(Lang.t("simpleblood.gui.kinds.lifetime"), Lang.t("simpleblood.gui.kinds.how_long_pieces_last"), 25, 300, 25, "%", () -> k.pieceLifetime, v -> k.pieceLifetime = v, dk.pieceLifetime);
        b.heading(Lang.t("simpleblood.gui.kinds.glowing_blood"));
        b.preview(PreviewScene.Kind.GLOW, mobColour(c));
        b.bool(Lang.t("simpleblood.gui.kinds.glow"), Lang.t("simpleblood.gui.kinds.glow_squid_ink_glows"), () -> k.glow, v -> k.glow = v, dk.glow);
        b.slider(Lang.t("simpleblood.gui.kinds.cloud_thickness"), Lang.t("simpleblood.gui.kinds.glowing_clouds_under_water"), 10, 200, 10, "%", () -> k.glowCloudOpacity, v -> k.glowCloudOpacity = v, dk.glowCloudOpacity);
    }

    private static void physicsMod(Builder b) {
        SimpleBloodConfig c = b.cfg, d = b.defaults;
        SimpleBloodConfig.PhysicsModSettings p = c.physicsMod, dp = d.physicsMod;
        b.bool(Lang.t("simpleblood.gui.physics_mod.ragdolls_bleed"), Lang.t("simpleblood.gui.physics_mod.bodies_left_by_ragdoll_mods"),
                () -> c.general.physicsModRagdolls, v -> c.general.physicsModRagdolls = v, d.general.physicsModRagdolls);
        String found = ragdollModsFound();
        if (!found.isEmpty()) b.note(Lang.t("simpleblood.gui.physics_mod.bleeding_for", found));

        b.heading(Lang.t("simpleblood.gui.physics_mod.the_body"));
        b.slider(Lang.t("simpleblood.gui.physics_mod.bleeding_lasts"), Lang.t("simpleblood.gui.physics_mod.how_long_a_body_drips"), 1, 60, 1, Lang.t("simpleblood.unit.seconds"),
                () -> p.bleedSeconds, v -> p.bleedSeconds = v, dp.bleedSeconds);
        b.slider(Lang.t("simpleblood.gui.physics_mod.drips"), Lang.t("simpleblood.gui.physics_mod.how_much_blood_drips_off"), 0, 300, 10, "%", () -> p.drips, v -> p.drips = v, dp.drips);

        b.heading(Lang.t("simpleblood.gui.physics_mod.cut_off_heads_and_limbs"));
        b.slider(Lang.t("simpleblood.gui.physics_mod.spurts"), Lang.t("simpleblood.gui.physics_mod.how_hard_a_stump_and"), 0, 300, 10, "%",
                () -> p.spurts, v -> p.spurts = v, dp.spurts);
        b.slider(Lang.t("simpleblood.gui.physics_mod.blood_supply"), Lang.t("simpleblood.gui.physics_mod.how_much_blood_a_body"), 25, 400, 25, "%",
                () -> p.bloodSupply, v -> p.bloodSupply = v, dp.bloodSupply);
        b.note(Lang.t("simpleblood.gui.physics_mod.every_stump_pumps_from_the"));

        b.heading(Lang.t("simpleblood.gui.physics_mod.landing_and_sliding"));
        b.slider(Lang.t("simpleblood.gui.physics_mod.splashes"), Lang.t("simpleblood.gui.physics_mod.drops_thrown_up_where_a"), 0, 300, 10, "%", () -> p.splashes, v -> p.splashes = v, dp.splashes);
        b.bool(Lang.t("simpleblood.gui.physics_mod.stains"), Lang.t("simpleblood.gui.physics_mod.a_hard_landing_leaves_a"), () -> p.stains, v -> p.stains = v, dp.stains);
        b.slider(Lang.t("simpleblood.gui.physics_mod.stain_size"), Lang.t("simpleblood.gui.physics_mod.how_big_those_stains_are"), 25, 300, 25, "%", () -> p.stainSize, v -> p.stainSize = v, dp.stainSize);
        b.slider(Lang.t("simpleblood.gui.physics_mod.uncut_bodies_stain"), Lang.t("simpleblood.gui.physics_mod.how_often_a_body_in"), 0, 100, 5, "%",
                () -> p.wholeRagdollStains, v -> p.wholeRagdollStains = v, dp.wholeRagdollStains);
        b.bool(Lang.t("simpleblood.gui.physics_mod.smears"), Lang.t("simpleblood.gui.physics_mod.parts_sliding_along_the_ground"), () -> p.smears, v -> p.smears = v, dp.smears);
        b.slider(Lang.t("simpleblood.gui.physics_mod.smear_amount"), Lang.t("simpleblood.gui.physics_mod.how_often_a_sliding_part"), 10, 200, 10, "%", () -> p.smearAmount, v -> p.smearAmount = v, dp.smearAmount);

        b.heading(Lang.t("simpleblood.gui.physics_mod.mobs_that_break_apart"));
        b.bool(Lang.t("simpleblood.gui.physics_mod.broken_mobs_bleed"), Lang.t("simpleblood.gui.physics_mod.mobs_physics_mod_breaks_into"),
                () -> p.pieces, v -> p.pieces = v, dp.pieces);
    }

    private static String ragdollModsFound() {
        java.util.List<String> ids = com.simpleblood.Platform.modIds();
        java.util.List<String> names = new java.util.ArrayList<>();
        if (ids.contains("physicsmod")) names.add(Lang.t("simpleblood.gui.ragdoll_mods_found.physics_mod"));
        if (ids.contains("ragdollified")) names.add(Lang.t("simpleblood.gui.ragdoll_mods_found.ragdollified"));
        if (ids.contains("saros_ragdoll")) names.add(Lang.t("simpleblood.gui.ragdoll_mods_found.saro_s_player_ragdoll"));
        return String.join(", ", names);
    }

    public static String mobFilter = "";
    private static final java.util.Set<String> UNSEEN = java.util.Set.of("armor_stand", "giant", "illusioner");

    private static void mobs(Builder b) {
        SimpleBloodConfig c = b.cfg;
        com.simpleblood.MobTable.sync(c);
        Widgets.TextField search = new Widgets.TextField();
        search.hint = Lang.t("simpleblood.gui.mobs.find_a_mob");
        search.set(mobFilter);
        search.w = Math.min(220, b.rowW - 8);
        search.onChange = s -> { mobFilter = s; b.host.refreshList(); };
        b.fixed(search, 20);
        List<String> ids = new ArrayList<>(c.vanillaEntities.entities.keySet());
        ids.sort(String::compareTo);
        String f = mobFilter.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (String id : ids) {
            if (!SimpleBlood.vanillaMobExists(id)) continue;
            if (UNSEEN.contains(id)) continue;
            if (!f.isEmpty() && !id.contains(f) && !pretty(id).toLowerCase(Locale.ROOT).contains(f)) continue;
            SimpleBloodConfig.VanillaEntities.EntitySettings s = c.vanillaEntities.entities.get(id);
            mobRow(b, id, () -> s.bloodColor, v -> s.bloodColor = v, () -> s.kind, v -> s.kind = v,
                    () -> s.enabled, v -> s.enabled = v, () -> s.canDripAtLowHealth, v -> s.canDripAtLowHealth = v,
                    () -> s.transformToStains, v -> s.transformToStains = v,
                    () -> s.glows, v -> s.glows = v,
                    () -> s.armor, v -> s.armor = v, null, false);
            shown++;
        }
        flushMobs(b);
        if (shown == 0) b.note(Lang.t("simpleblood.gui.mobs.nothing_found"));
    }

    public static String newModdedId = "";
    public static String moddedFilter = "";

    private static void modded(Builder b) {
        SimpleBloodConfig c = b.cfg;
        SimpleBloodConfig.ModdedEntities m = c.moddedEntities;
        Widget.Container addRow = new Widget.Container();
        addRow.w = b.rowW; addRow.h = 20;
        Widgets.TextField find = new Widgets.TextField();
        find.hint = Lang.t("simpleblood.gui.modded.find_a_mob");
        find.set(moddedFilter);
        find.onChange = v -> { moddedFilter = v; b.host.refreshList(); };
        find.x = 0; find.y = 0; find.w = Math.min(160, (b.rowW - 70) / 2); find.h = 20;
        Widgets.TextField id = new Widgets.TextField();
        id.hint = "modid:mob_name";
        id.set(newModdedId);
        id.onChange = v -> newModdedId = v;
        id.x = find.w + 8; id.y = 0; id.w = Math.min(160, (b.rowW - 70) / 2); id.h = 20;
        id.tooltip = List.of(Lang.t("simpleblood.gui.modded.a_mob_that_is_not"));
        Runnable addAction = () -> {
            String v = newModdedId.trim();
            if (!v.contains(":") || v.endsWith(":")) { b.host.toast(Lang.t("simpleblood.gui.modded.needs_to_look_like_modid")); return; }
            if (m.customEntities.containsKey(v)) { b.host.toast(Lang.t("simpleblood.gui.modded.already_on_the_list")); return; }
            m.editable(v);
            SimpleBlood.LOGGER.info("Added modded entity via config UI: {}", v);
            newModdedId = "";
            b.host.rebuildPage();
        };
        id.onEnter = addAction;
        Widgets.Button add = new Widgets.Button(Lang.t("simpleblood.gui.modded.add"), addAction);
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
            b.note(Lang.t("simpleblood.gui.modded.no_other_mod_adds_mobs"));
            return;
        }
        b.note(Lang.t("simpleblood.gui.modded.modded_mobs_sorted_automatically_your"));
        String f = moddedFilter.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (Map.Entry<String, List<Identifier>> mod : byMod.entrySet()) {
            boolean headed = false;
            for (Identifier eid : mod.getValue()) {
                String full = eid.toString();
                if (!f.isEmpty() && !full.contains(f) && !pretty(eid.getPath()).toLowerCase(Locale.ROOT).contains(f)) continue;
                if (!headed) {
                    b.heading(com.simpleblood.Platform.modName(mod.getKey()));
                    headed = true;
                }
                moddedRow(b, m, full);
                shown++;
            }
            flushMobs(b);
        }
        if (!missing.isEmpty()) {
            boolean headed = false;
            for (String eid : missing) {
                if (!f.isEmpty() && !eid.contains(f)) continue;
                if (!headed) {
                    b.heading(Lang.t("simpleblood.gui.modded.not_installed"));
                    b.note(Lang.t("simpleblood.gui.modded.kept_for_mods_that_are"));
                    headed = true;
                }
                moddedRow(b, m, eid);
                shown++;
            }
            flushMobs(b);
        }
        if (shown == 0) b.note(Lang.t("simpleblood.gui.modded.nothing_found"));
    }

    private static void moddedRow(Builder b, SimpleBloodConfig.ModdedEntities m, String eid) {
        Supplier<SimpleBloodConfig.ModdedEntities.ModdedEntitySettings> now = () -> {
            SimpleBloodConfig.ModdedEntities.ModdedEntitySettings s = m.effective(eid);
            return s != null ? s : m.editable(eid);
        };
        Runnable remove = m.isGuess(eid) ? null : () -> { m.customEntities.remove(eid); b.host.rebuildPage(); };
        mobRow(b, eid, () -> now.get().bloodColor, v -> m.editable(eid).bloodColor = v,
                () -> now.get().kind, v -> m.editable(eid).kind = v,
                () -> now.get().enabled, v -> m.editable(eid).enabled = v,
                () -> now.get().canDripAtLowHealth, v -> m.editable(eid).canDripAtLowHealth = v,
                () -> now.get().transformToStains, v -> m.editable(eid).transformToStains = v,
                () -> now.get().glows, v -> m.editable(eid).glows = v,
                () -> now.get().armor, v -> m.editable(eid).armor = v,
                remove, m.isGuess(eid));
    }

    private static final String[] ARMOR_KEYS = {"auto", "armor", "metal"};
    private static final String[] ARMOR_LABELS = {"simpleblood.gui.labels.auto", "simpleblood.gui.labels.armor", "simpleblood.gui.labels.metal"};

    private record MobEntry(String id, IntSupplier colGet, IntConsumer colSet, Supplier<String> kindGet, Consumer<String> kindSet,
                            Supplier<Boolean> onGet, Consumer<Boolean> onSet, Supplier<Boolean> dripGet, Consumer<Boolean> dripSet,
                            Supplier<Boolean> stainGet, Consumer<Boolean> stainSet, Supplier<Boolean> glowGet, Consumer<Boolean> glowSet,
                            Supplier<String> armorGet, Consumer<String> armorSet, Runnable remove, boolean guessed) {}

    private static final List<MobEntry> PENDING = new ArrayList<>();
    static String openMob;

    private static void mobRow(Builder b, String id, IntSupplier colGet, IntConsumer colSet,
                               Supplier<String> kindGet, Consumer<String> kindSet,
                               Supplier<Boolean> onGet, Consumer<Boolean> onSet,
                               Supplier<Boolean> dripGet, Consumer<Boolean> dripSet,
                               Supplier<Boolean> stainGet, Consumer<Boolean> stainSet,
                               Supplier<Boolean> glowGet, Consumer<Boolean> glowSet,
                               Supplier<String> armorGet, Consumer<String> armorSet,
                               Runnable remove, boolean guessed) {
        PENDING.add(new MobEntry(id, colGet, colSet, kindGet, kindSet, onGet, onSet, dripGet, dripSet, stainGet, stainSet,
                glowGet, glowSet, armorGet, armorSet, remove, guessed));
    }

    private static final int CARD_H = 58, CARD_GAP = 4;

    private static void flushMobs(Builder b) {
        if (PENDING.isEmpty()) return;
        int cols = Math.max(2, (b.rowW - 8 + CARD_GAP) / (84 + CARD_GAP));
        int cw = (b.rowW - 8 - (cols - 1) * CARD_GAP) / cols;
        for (int i = 0; i < PENDING.size(); i += cols) {
            MobEntry open = null;
            for (int k = 0; k < cols && i + k < PENDING.size(); k++) {
                MobEntry e = PENDING.get(i + k);
                MobCard card = new MobCard(b, e);
                card.x = 4 + k * (cw + CARD_GAP);
                card.y = b.cursor;
                card.w = cw;
                card.h = CARD_H;
                b.panel.add(card);
                if (e.id().equals(openMob)) open = e;
            }
            b.cursor += CARD_H + CARD_GAP;
            if (open != null) {
                b.host.openPopup(mobPopup(b, open));
                openMob = null;
            }
        }
        PENDING.clear();
    }

    private static final String[] KIND_ITEMS = {"minecraft:red_dye", "minecraft:gravel", "minecraft:blaze_powder", "minecraft:snowball",
            "minecraft:bone_meal", "minecraft:iron_nugget", "minecraft:stick", "minecraft:glowstone_dust", "minecraft:wind_charge"};

    private static net.minecraft.world.item.ItemStack kindItem(String kind) {
        int i = java.util.Arrays.asList(KIND_KEYS).indexOf(kind);
        net.minecraft.world.item.ItemStack stack = itemOf(KIND_ITEMS[Math.max(0, i)]);
        return stack.isEmpty() ? itemOf("minecraft:feather") : stack;
    }

    private static net.minecraft.world.item.ItemStack eggOf(String id) {
        String full = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        return itemOf(full + "_spawn_egg");
    }

    private static final Map<String, Boolean> TEXTURES = new java.util.HashMap<>();

    private static void drawItem(Gfx g, net.minecraft.world.item.ItemStack stack, String id, int x, int y) {
        if (!stack.isEmpty()) {
            g.item(stack, x, y);
            return;
        }
        String full = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        String tex = full.replace(":", ":textures/item/") + ".png";
        boolean there = TEXTURES.computeIfAbsent(tex, t -> {
            try {
                return net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(com.simpleblood.Ids.parse(t)).isPresent();
            } catch (Throwable e) {
                return false;
            }
        });
        if (there) g.texture(com.simpleblood.Ids.parse(tex), x, y, 16);
    }

    private static net.minecraft.world.item.ItemStack itemOf(String id) {
        try {
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(com.simpleblood.Ids.parse(id))
                    .map(net.minecraft.world.item.ItemStack::new).orElse(net.minecraft.world.item.ItemStack.EMPTY);
        } catch (Throwable t) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
    }

    public static void openMobForTests(String id) {
        openMob = id;
    }

    private static String mobName(String id) {
        return pretty(id.substring(id.indexOf(':') + 1));
    }

    private static final class MobCard extends Widget {
        private final MobEntry e;
        private final Builder b;
        private final net.minecraft.world.item.ItemStack egg;

        MobCard(Builder b, MobEntry e) {
            this.b = b;
            this.e = e;
            this.egg = eggOf(e.id());
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            boolean open = false, on = e.onGet().get();
            g.fill(x, y, x + w, y + h, Widgets.EDGE);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, Gfx.mix(0xFF29292E, 0xFF34343A, hover));
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x18FFFFFF);
            if (open) g.outline(x + 1, y + 1, x + w - 1, y + h - 1, Widgets.ACCENT);
            int cx = x + w / 2;
            g.slot(cx - 9, y + 4);
            String full = e.id().indexOf(':') < 0 ? "minecraft:" + e.id() : e.id();
            drawItem(g, egg, full + "_spawn_egg", cx - 8, y + 5);
            String name = mobName(e.id());
            int nw = Math.min(g.textWidth(name), w - 8);
            g.textClipped(name, cx - nw / 2, y + 25, w - 8, on ? Widgets.TEXT : Widgets.TEXT_DIM);
            String kind = e.kindGet().get();
            boolean glowing = e.glowGet().get() && "liquid".equals(kind);
            String label = glowing ? Lang.t("simpleblood.gui.mob_name.glowing") : Lang.t(KIND_LABELS[Math.max(0, java.util.Arrays.asList(KIND_KEYS).indexOf(kind))]);
            int kw = 16 + 2 + g.textWidth(label) + 4 + 5;
            int kx = cx - Math.min(kw, w - 6) / 2;
            String kindId = glowing ? "minecraft:glow_ink_sac" : KIND_ITEMS[Math.max(0, java.util.Arrays.asList(KIND_KEYS).indexOf(kind))];
            drawItem(g, glowing ? itemOf(kindId) : kindItem(kind), kindId, kx, y + 37);
            g.textClipped(label, kx + 18, y + 41, w - 30, on ? 0xFFC9C5BF : Widgets.TEXT_DARK);
            int dx = Math.min(kx + kw - 5, x + w - 8);
            g.fill(dx, y + 42, dx + 5, y + 47, Widgets.EDGE);
            g.fill(dx + 1, y + 43, dx + 4, y + 46, 0xFF000000 | (e.colGet().getAsInt() & 0xFFFFFF));
            if (e.guessed()) g.text(Lang.t("simpleblood.gui.mob_name.auto"), x + 4, y + 4, 0xFF8FD3AE);
            else if (e.remove() != null) g.text(Lang.t("simpleblood.gui.mob_name.yours"), x + 4, y + 4, Widgets.CHANGED);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != Keys.MOUSE_LEFT || !contains(mx, my)) return false;
            Widgets.click();
            b.host.openPopup(mobPopup(b, e));
            return true;
        }
    }

    private static Widget.Container mobPopup(Builder b, MobEntry e) {
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        int sw = b.host.screenWidth(), sh = b.host.screenHeight();
        int pad = 12, gap = 20, row = 14, gutter = 16;
        int headRule = pad + 18 + 8;
        int fieldsY = headRule + 9;
        int midRule = fieldsY + 2 * gap + row + 9;
        int switchesY = midRule + 9;
        int footY = switchesY + gap + row + 10;
        int footH = 28;
        int w = Math.min(280, sw - 24), h = footY + footH;
        int x0 = (sw - w) / 2, y0 = (sh - h) / 2;
        int ctrlW = Math.min(140, w - pad * 2 - 60);
        int colW = (w - pad * 2 - gutter) / 2;
        String[] names = {Lang.t("simpleblood.gui.mob_popup.bleeds"), Lang.t("simpleblood.gui.mob_popup.drips"), Lang.t("simpleblood.gui.mob_popup.pools"), Lang.t("simpleblood.gui.mob_popup.glows")};
        String[] labels = {Lang.t("simpleblood.gui.mob_popup.type"), Lang.t("simpleblood.gui.mob_popup.colour"), Lang.t("simpleblood.gui.mob_popup.armor")};
        net.minecraft.world.item.ItemStack egg = eggOf(e.id());
        String full = e.id().indexOf(':') < 0 ? "minecraft:" + e.id() : e.id();
        java.util.List<Supplier<Boolean>> gets = List.of(e.onGet(), e.dripGet(), e.stainGet(), e.glowGet());
        java.util.List<Consumer<Boolean>> sets = List.of(e.onSet(), e.dripSet(), e.stainSet(), e.glowSet());
        Widget.Container c = new Widget.Container() {
            @Override
            protected void draw(Gfx g, int mx, int my, float dt) {
                g.fill(x, y, x + w, y + h, Widgets.EDGE);
                g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF29292E);
                g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x20FFFFFF);
                g.fill(x + 1, y + footY, x + w - 1, y + h - 1, 0xFF232327);
                g.fill(x + 1, y + footY, x + w - 1, y + footY + 1, 0x20FFFFFF);
                g.slot(x + pad, y + pad);
                drawItem(g, egg, full + "_spawn_egg", x + pad + 1, y + pad + 1);
                g.textClipped(mobName(e.id()), x + pad + 26, y + pad + 1, w - pad * 2 - 26, Widgets.TEXT);
                g.textClipped(e.id(), x + pad + 26, y + pad + 11, w - pad * 2 - 26, Widgets.DESC);
                g.fill(x + pad, y + headRule, x + w - pad, y + headRule + 1, 0x20FFFFFF);
                for (int i = 0; i < 3; i++) g.text(labels[i], x + pad, y + fieldsY + i * gap + 3, 0xFFC9C5BF);
                g.fill(x + pad, y + midRule, x + w - pad, y + midRule + 1, 0x20FFFFFF);
                for (int i = 0; i < 4; i++) {
                    int lx = x + pad + (i % 2) * (colW + gutter);
                    g.textClipped(names[i], lx, y + switchesY + (i / 2) * gap + 3, colW - 34,
                            gets.get(i).get() ? 0xFFC9C5BF : Widgets.TEXT_DIM);
                }
                super.draw(g, mx, my, dt);
            }
        };
        c.x = x0; c.y = y0; c.w = w; c.h = h;
        int ctrlX = x0 + w - pad - ctrlW;
        Widgets.Dropdown kind = new Widgets.Dropdown(KIND_KEYS, Lang.all(KIND_LABELS), e.kindGet(), e.kindSet());
        kind.x = ctrlX; kind.y = y0 + fieldsY; kind.w = ctrlW;
        Widgets.Swatch colour = new Widgets.Swatch(e.colGet(), null);
        colour.onClick = () -> b.host.openPicker(e.colGet().getAsInt(), e.colSet());
        colour.x = ctrlX; colour.y = y0 + fieldsY + gap; colour.w = ctrlW; colour.h = row;
        Widgets.Dropdown armor = new Widgets.Dropdown(ARMOR_KEYS, Lang.all(ARMOR_LABELS), () -> e.armorGet().get() == null ? "auto" : e.armorGet().get(), e.armorSet());
        armor.x = ctrlX; armor.y = y0 + fieldsY + 2 * gap; armor.w = ctrlW;
        armor.tooltip = List.of(Lang.t("simpleblood.gui.mob_popup.auto_sparks_only_off_metal"), Lang.t("simpleblood.gui.mob_popup.armor_counts_as_wearing_metal"),
                Lang.t("simpleblood.gui.mob_popup.metal_a_body_of_metal"));
        c.add(kind);
        c.add(colour);
        c.add(armor);
        for (int i = 0; i < 4; i++) {
            Widgets.Toggle t = new Widgets.Toggle(gets.get(i), sets.get(i));
            t.x = x0 + pad + (i % 2) * (colW + gutter) + colW - t.w; t.y = y0 + switchesY + (i / 2) * gap;
            c.add(t);
        }
        int buttonY = y0 + footY + (footH - row) / 2;
        Widgets.Button done = new Widgets.Button(Lang.t("simpleblood.gui.mob_popup.done"), b.host::closePopup);
        done.accent = true;
        done.w = Math.max(56, font.width(Lang.t("simpleblood.gui.mob_popup.done")) + 24); done.h = row;
        done.x = x0 + w - pad - done.w; done.y = buttonY;
        c.add(done);
        if (e.remove() != null) {
            Widgets.Button undo = new Widgets.Button(Lang.t("simpleblood.gui.mob_popup.back_to_the_guess"), () -> {
                e.remove().run();
                b.host.closePopup();
            });
            undo.w = font.width(undo.text) + 14; undo.h = row;
            undo.x = x0 + pad; undo.y = buttonY;
            c.add(undo);
        }
        return c;
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
        b.preview(PreviewScene.Kind.HIT, mobColour(b.cfg));
        com.google.gson.Gson gson = new com.google.gson.Gson();
        String current = gson.toJson(b.cfg);
        PresetMatch match = new PresetMatch(b.cfg);
        for (Presets.Preset p : Presets.ALL) {
            SimpleBloodConfig look = gson.fromJson(current, SimpleBloodConfig.class);
            p.apply().accept(look);
            b.previewWith(look);
            b.action(Lang.t(p.name()), Lang.t(p.description()), Lang.t("simpleblood.gui.presets.apply"), () -> {
                p.apply().accept(b.cfg);
                BloodSurfaces.clear();
                b.host.toast(Lang.t("simpleblood.gui.presets.applied", Lang.t(p.name())));
                b.host.rebuildPage();
            });
            if (b.lastRow != null && b.lastRow.control instanceof Widgets.Button apply) {
                apply.done = () -> match.applied(p);
                apply.doneText = Lang.t("simpleblood.gui.presets.applied_2");
            }
        }
        b.previewWith(null);
        b.preview(null, null);
    }

    private static final class PresetMatch {
        private final SimpleBloodConfig cfg;
        private final com.google.gson.Gson gson = new com.google.gson.Gson();
        private final java.util.Set<String> matching = new java.util.HashSet<>();
        private float checkedAt = -1;

        PresetMatch(SimpleBloodConfig cfg) { this.cfg = cfg; }

        boolean applied(Presets.Preset p) {
            float now = Widget.Anim.now();
            if (checkedAt < 0 || now - checkedAt > 0.25f) {
                checkedAt = now;
                matching.clear();
                String current = gson.toJson(cfg);
                for (Presets.Preset each : Presets.ALL) {
                    SimpleBloodConfig copy = gson.fromJson(current, SimpleBloodConfig.class);
                    each.apply().accept(copy);
                    if (gson.toJson(copy).equals(current)) matching.add(each.name());
                }
            }
            return matching.contains(p.name());
        }
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
