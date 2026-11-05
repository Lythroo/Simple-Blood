package com.simpleblood.gui;

import com.simpleblood.Lang;

import com.simpleblood.SimpleBlood;
import com.simpleblood.SimpleBloodClient;
import com.simpleblood.SimpleBloodConfig;
import com.simpleblood.Guard;
import com.simpleblood.surface.BloodSurfaces;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class BloodConfigScreen extends Screen implements Pages.Host {

    private static final Gson GSON = new Gson();
    private static final int TITLE_H = 24;
    private static final int FOOTER_H = 24;
    private static final int PAGE_HEAD = 38;
    private int sidebar = 100;
    private int margin = 8;
    private int paneW = 180;

    private final Screen parent;
    private final String backup;
    private final List<Pages.Page> pages = Pages.all();
    private int page;

    private final Widget.Container root = new Widget.Container();
    private final Widget.Container top = new Widget.Container();
    private final Widget.ScrollPanel tabList = new Widget.ScrollPanel();
    private Widgets.TextField search;
    private String searchText = "";
    private final Widget.Container header = new Widget.Container();
    private final Widget.ScrollPanel list = new Widget.ScrollPanel();
    private final Widget.Container footer = new Widget.Container();
    private final Widget.Container paneBox = new Widget.Container();
    private Widgets.Button replay, slow, backToDefault;
    private Widgets.ResetButton resetPage;
    private ColourPicker picker;
    private Widget.Container popup;
    private boolean saved;

    private final java.util.EnumMap<com.simpleblood.surface.PreviewScene.Kind, SceneView> scenes = new java.util.EnumMap<>(com.simpleblood.surface.PreviewScene.Kind.class);
    private Widget.PreviewSource previewRow;
    private Widget.PreviewSource hoverRow;
    private float previewHover;
    private static final float PREVIEW_DELAY = 0.12f;
    private boolean slowMotion;
    private int tickCount;

    private int changed;
    private float changedAt = -1;

    private float lastTime = Widget.Anim.now();
    private String toast;
    private float toastUntil;

    public BloodConfigScreen(Screen parent) {
        super(Component.literal(Lang.t("simpleblood.screen.simple_blood")));
        this.parent = parent;
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        this.backup = cfg == null ? null : GSON.toJson(cfg);
        Guard.retryScreen();
    }

    private boolean guarded(String doing, java.util.function.BooleanSupplier task) {
        if (!Guard.ok(Guard.Part.SCREEN)) return false;
        try {
            return task.getAsBoolean();
        } catch (Throwable t) {
            Guard.fail(Guard.Part.SCREEN, doing, t);
            return false;
        }
    }

    private void guarded(String doing, Runnable task) {
        guarded(doing, () -> {
            task.run();
            return true;
        });
    }

    private void cleanup(String doing, Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            Guard.fail(Guard.Part.SCREEN, doing, t);
        }
    }

    public void abandon() {
        saved = true;
        Screens.open(minecraft, parent);
    }

    @Override
    public SimpleBloodConfig config() {
        return SimpleBloodClient.getConfig();
    }

    @Override
    public void openPicker(int current, IntConsumer apply) {
        openPicker(current, apply, null);
    }

    @Override
    public void openPicker(int current, IntConsumer apply, String note) {
        Widgets.Dropdown.close();
        picker = new ColourPicker(current, apply, () -> picker = null, note);
        picker.layout(width, height);
    }

    @Override
    public void openPopup(Widget.Container panel) {
        Widgets.Dropdown.close();
        popup = panel;
    }

    @Override
    public void closePopup() {
        Widgets.Dropdown.close();
        popup = null;
    }

    @Override
    public int screenWidth() { return width; }

    @Override
    public int screenHeight() { return height; }

    @Override
    public void rebuildPage() {
        buildPage(true);
    }

    @Override
    public void refreshList() {
        buildPage(false);
    }

    @Override
    public void toast(String text) {
        toast = text;
        toastUntil = Widget.Anim.now() + 2.2f;
    }

    @Override
    protected void init() {
        guarded("building the settings screen", this::layout);
    }

    private void layout() {
        root.clear();
        root.x = 0; root.y = 0; root.w = width; root.h = height;
        Widgets.Dropdown.close();
        popup = null;
        margin = width < 520 ? 6 : 8;
        int longest = 0;
        for (Pages.Page p : pages) longest = Math.max(longest, this.font.width(p.title()) + 22);
        for (String c : categories()) longest = Math.max(longest, this.font.width(c) + 18);
        sidebar = Math.min(Math.max(width < 520 ? 82 : 96, longest + 8), width / 5);
        tabList.x = margin; tabList.y = TITLE_H + margin; tabList.w = sidebar; tabList.h = height - TITLE_H - FOOTER_H - margin * 2;
        tabList.padding = 3;
        footer.x = 0; footer.y = height - FOOTER_H; footer.w = width; footer.h = FOOTER_H;
        layoutContent();
        previewRow = null;
        hoverRow = null;
        root.add(top);
        root.add(tabList);
        root.add(header);
        root.add(list);
        root.add(paneBox);
        root.add(footer);
        buildTop();
        buildTabs();
        buildFooter();
        buildPane();
        buildPage(true);
        if (picker != null) picker.layout(width, height);
    }

    private void layoutContent() {
        int contentX = margin + sidebar + margin;
        int contentW = width - contentX - margin;
        boolean pane = hasPane();
        if (pane) {
            paneW = Math.max(130, Math.min(360, Math.round(contentW * 0.42f)));
            int listW = Math.min(440, contentW - paneW - margin);
            int extra = contentW - listW - margin - paneW;
            int grow = Math.max(0, Math.min(extra, 360 - paneW));
            paneW += grow;
            extra -= grow;
            list.x = contentX + Math.max(0, extra) / 2;
            list.w = listW;
        } else {
            paneW = 0;
            list.x = contentX;
            list.w = contentW;
        }
        header.x = list.x; header.w = list.w;
        paneBox.visible = pane;
        paneBox.x = list.x + list.w + margin; paneBox.y = TITLE_H + margin;
        paneBox.w = paneW; paneBox.h = height - paneBox.y - FOOTER_H - margin;
    }

    private boolean hasPane() {
        return searching() || pages.get(page).pane();
    }

    private boolean searching() {
        return !searchText.isBlank();
    }

    private List<String> categories() {
        List<String> out = new ArrayList<>();
        for (Pages.Page p : pages) if (!out.contains(p.group())) out.add(p.group());
        return out;
    }

    private void buildTop() {
        top.clear();
        top.x = 0; top.y = 0; top.w = width; top.h = TITLE_H + margin + PAGE_HEAD;
        int sw = Math.min(150, width / 4);
        search = new Widgets.TextField();
        search.hint = Lang.t("simpleblood.screen.search_every_setting");
        search.set(searchText);
        search.x = width - margin - sw; search.y = (TITLE_H - 16) / 2; search.w = sw; search.h = 16;
        search.onChange = text -> {
            boolean was = searching();
            searchText = text;
            if (searching() || was) {
                Widgets.Dropdown.close();
                layoutContent();
                buildTabs();
                buildPage(true);
            }
        };
        top.add(search);
    }

    private void buildTabs() {
        tabList.clear();
        String open = searching() ? null : pages.get(page).group();
        int y = 2, inner = tabList.w - 4, shownY = -1;
        for (String cat : categories()) {
            int first = -1;
            for (int i = 0; i < pages.size(); i++) if (pages.get(i).group().equals(cat)) { first = i; break; }
            final int firstPage = first;
            boolean isOpen = cat.equals(open);
            CategoryTab ct = new CategoryTab(cat, isOpen, () -> selectPage(firstPage));
            ct.x = 2; ct.y = y; ct.w = inner; ct.h = 17;
            tabList.add(ct);
            y += 18;
            if (!isOpen) continue;
            int n = 0;
            for (int i = 0; i < pages.size(); i++) {
                Pages.Page p = pages.get(i);
                if (!p.group().equals(cat)) continue;
                final int idx = i;
                Tab t = new Tab(p.title(), () -> selectPage(idx), () -> page == idx && !searching(), n++);
                t.x = 2; t.y = y; t.w = inner; t.h = 15;
                t.tooltip = List.of(p.hint());
                tabList.add(t);
                if (idx == page) shownY = y;
                y += 15;
            }
            y += 3;
        }
        if (shownY >= 0) tabList.scrollTo(shownY + 30 - tabList.h);
    }

    private void buildFooter() {
        footer.clear();
        int bh = 16, gap = 6;
        int y0 = footer.y + (FOOTER_H - bh) / 2;
        int bw = width < 520 ? 54 : 70;
        Widgets.Button done = new Widgets.Button(Lang.t("simpleblood.screen.done"), this::save);
        done.accent = true;
        done.x = width - margin - bw; done.y = y0; done.w = bw; done.h = bh;
        Widgets.Button cancel = new Widgets.Button(Lang.t("simpleblood.screen.cancel"), this::cancel);
        cancel.tooltip = List.of(Lang.t("simpleblood.screen.close_and_put_everything_back"));
        cancel.x = done.x - gap - bw; cancel.y = y0; cancel.w = bw; cancel.h = bh;
        Widgets.Button undo = new Widgets.Button(Lang.t("simpleblood.screen.undo"), () -> {
            restore();
            BloodSurfaces.clear();
            toast(Lang.t("simpleblood.screen.undone"));
            buildPage(true);
        });
        undo.tooltip = List.of(Lang.t("simpleblood.screen.back_to_how_things_were"));
        int uw = this.font.width(Lang.t("simpleblood.screen.undo")) + 16;
        undo.x = margin + 4 + this.font.width(Lang.t("simpleblood.screen.unsaved_changes", 999)) + 10; undo.y = y0; undo.w = uw; undo.h = bh;
        footer.add(done);
        footer.add(cancel);
        footer.add(undo);
    }

    private void buildPane() {
        paneBox.clear();
        replay = new Widgets.Button(Lang.t("simpleblood.screen.replay"), () -> {
            if (previewRow != null && previewRow.previewKind() != null) {
                SceneView v = scenes.get(previewRow.previewKind());
                if (v != null) v.trigger();
            }
        });
        slow = new Widgets.Button(Lang.t("simpleblood.screen.slow"), () -> slowMotion = !slowMotion);
        slow.tooltip = List.of(Lang.t("simpleblood.screen.plays_the_scene_at_a"));
        backToDefault = new Widgets.Button(Lang.t("simpleblood.screen.back_to_default"), () -> {
            if (previewRow instanceof Widgets.Row r && r.reset() != null) {
                r.reset().run();
                refreshList();
            }
        });
        for (Widgets.Button b : List.of(replay, slow, backToDefault)) {
            b.h = 14;
            b.visible = false;
            paneBox.add(b);
        }
    }

    private void buildPage(boolean withHeader) {
        layoutContent();
        list.clear();
        if (withHeader) header.clear();
        int listTop = TITLE_H + margin + PAGE_HEAD;
        Pages.Builder b = new Pages.Builder(list, header, this, !withHeader);
        header.y = listTop;
        list.y = listTop;
        list.h = height - listTop - FOOTER_H - margin;
        if (searching()) Pages.search(b, pages, searchText);
        else pages.get(page).build().accept(b);
        int headerH = withHeader ? b.headerHeight() : header.h;
        header.h = headerH;
        list.y = listTop + headerH;
        list.h = height - list.y - FOOTER_H - margin;
        if (withHeader) {
            previewRow = null;
            hoverRow = null;
            if (resetPage != null) top.children().remove(resetPage);
            resetPage = new Widgets.ResetButton(Lang.t("simpleblood.screen.reset_page"), () -> {
                resetAll(list);
                toast(Lang.t("simpleblood.screen.page_reset"));
                refreshList();
            });
            resetPage.tooltip = List.of(Lang.t("simpleblood.screen.puts_every_amber_setting_on"));
            resetPage.y = TITLE_H + margin + 10 + (18 - resetPage.h) / 2;
            resetPage.visible = false;
            top.add(resetPage);
        }
    }

    private static int countChanged(Widget.Container c) {
        int n = 0;
        for (Widget w : c.children()) {
            if (w instanceof Widgets.Row r) {
                if (r.changed() && r.reset() != null) n++;
            } else if (w instanceof Widget.Container k) {
                n += countChanged(k);
            }
        }
        return n;
    }

    private static void resetAll(Widget.Container c) {
        for (Widget w : new ArrayList<>(c.children())) {
            if (w instanceof Widgets.Row r) {
                if (r.changed() && r.reset() != null) r.reset().run();
            } else if (w instanceof Widget.Container k) {
                resetAll(k);
            }
        }
    }

    @Override
    public void tick() {
        if (!slowMotion || tickCount++ % 3 == 0) guarded("the previews moving", com.simpleblood.surface.PreviewScene::tickAll);
    }

    @Override
    public void removed() {
        Widget.stopHoverTracking();
        Widgets.Dropdown.close();
        root.blur();
        if (picker != null) picker.blur();
        cleanup("closing the settings screen", () -> {
            for (SceneView v : scenes.values()) v.dispose();
            scenes.clear();
            com.simpleblood.surface.PreviewScene.disposeAll();
        });
    }

    private static Widget.PreviewSource previewSourceAt(Widget.Container c, double mx, double my) {
        for (int i = c.children().size() - 1; i >= 0; i--) {
            Widget w = c.children().get(i);
            if (!w.visible || !w.contains(mx, my)) continue;
            if (w instanceof Widget.PreviewSource src) return src;
            if (w instanceof Widget.Container k) {
                Widget.PreviewSource inner = previewSourceAt(k, mx, my);
                if (inner != null) return inner;
            }
        }
        return null;
    }

    private static Widget.PreviewSource activeSource(Widget.Container c) {
        for (Widget w : c.children()) {
            if (w instanceof Widget.PreviewSource src && src.previewActive()) return src;
            if (w instanceof Widget.Container k) {
                Widget.PreviewSource inner = activeSource(k);
                if (inner != null) return inner;
            }
        }
        return null;
    }

    private static Widget.PreviewSource firstSource(Widget.Container c) {
        for (Widget w : c.children()) {
            if (w instanceof Widget.PreviewSource src) return src;
            if (w instanceof Widget.Container k) {
                Widget.PreviewSource inner = firstSource(k);
                if (inner != null) return inner;
            }
        }
        return null;
    }

    private void drawPreview(Gfx g, int mx, int my, float dt) {
        int px = list.x + list.w + margin, py = TITLE_H + margin;
        int pw = paneW, ph = height - py - FOOTER_H - margin;
        panel(g, px, py, px + pw, py + ph);

        boolean overList = picker == null && !Widgets.Dropdown.anyOpen() && list.contains(mx, my);
        Widget.PreviewSource src = picker != null ? null : activeSource(list);
        boolean active = src != null;
        if (src == null && overList) src = previewSourceAt(list, mx, my);
        if (src != hoverRow) {
            hoverRow = src;
            previewHover = 0;
        } else {
            previewHover += dt;
        }
        boolean fresh = false;
        if (src != null && (active || previewHover >= PREVIEW_DELAY) && src != previewRow) {
            previewRow = src;
            fresh = true;
        }
        if (previewRow == null) {
            previewRow = firstSource(list);
            fresh = previewRow != null;
        }
        float a = 1f;

        int ix = px + 5, iw = pw - 10;
        int y = py + 5;
        replay.visible = slow.visible = backToDefault.visible = false;
        if (previewRow == null) return;

        if (previewRow.previewKind() != null) {
            SceneView view = scenes.computeIfAbsent(previewRow.previewKind(), k -> new SceneView(k, () -> 0x7A0F0F));
            java.util.function.IntSupplier colour = previewRow.previewColour();
            view.colour(colour != null ? colour : () -> 0x660303);
            view.show(previewRow.previewAspects());
            view.config(previewRow.previewConfig());
            view.figure(previewRow.previewFigure());
            view.bloodKind(previewRow.previewBloodKind());
            view.weapon(previewRow.previewWeapon(), previewRow.previewItem());
            if (fresh) view.trigger();
            int sh = Math.max(60, Math.min(iw, ph - 118));
            view.draw(g, ix, y, iw, sh, dt);
            g.fill(ix + 3, y + 3, ix + 9 + g.textWidth(Lang.t("simpleblood.screen.live_preview")), y + 15, 0xA0000000);
            g.text(Lang.t("simpleblood.screen.live_preview"), ix + 6, y + 5, 0xFFE2DED8);
            y += sh + 4;
            String caption = view.scene().caption();
            replay.w = g.textWidth(Lang.t("simpleblood.screen.replay")) + 12;
            slow.w = g.textWidth(Lang.t("simpleblood.screen.slow")) + 12;
            slow.x = ix + iw - slow.w; slow.y = y;
            replay.x = slow.x - 4 - replay.w; replay.y = y;
            slow.accent = slowMotion;
            replay.visible = slow.visible = true;
            if (caption != null && !caption.isEmpty()) g.textClipped(caption, ix + 1, y + 3, replay.x - ix - 6, Widgets.DESC);
            y += 20;
            g.fill(ix, y - 3, ix + iw, y - 2, 0x20FFFFFF);
        }

        String name = previewRow.previewName() == null ? "" : previewRow.previewName();
        boolean changedRow = previewRow instanceof Widgets.Row r && r.changed();
        g.textClipped(name, ix + 1, y + 2, iw - 2, Gfx.alpha(changedRow ? Widgets.CHANGED : Widgets.TEXT, a));
        y += 14;
        String text = previewRow.previewText();
        if (text != null && !text.isEmpty()) {
            for (String line : g.wrap(text, iw - 2)) {
                if (y + 10 > py + ph - 22) break;
                g.text(line, ix + 1, y, Gfx.alpha(0xFFC9C5BF, a));
                y += 10;
            }
        }
        if (previewRow instanceof Widgets.Row r && r.side instanceof Widgets.PlayButton
                && com.simpleblood.BloodSounds.previewPlaying() != null && !com.simpleblood.BloodSounds.previewFiles().isEmpty()) {
            y += 3;
            g.textClipped(Lang.t("simpleblood.screen.playing", com.simpleblood.BloodSounds.previewFiles()), ix + 1, y, iw - 2, Widgets.ACCENT);
            y += 10;
        }
        if (changedRow && ((Widgets.Row) previewRow).reset() != null) {
            backToDefault.w = Math.min(iw, g.textWidth(backToDefault.text) + 14);
            backToDefault.x = ix; backToDefault.y = Math.min(y + 4, py + ph - 19);
            backToDefault.visible = true;
        }
    }

    private void selectPage(int idx) {
        Widgets.Dropdown.close();
        popup = null;
        if (searching()) {
            searchText = "";
            if (search != null) search.set("");
            page = -1;
        }
        if (idx == page) return;
        page = idx;
        buildTabs();
        buildPage(true);
    }

    private void save() {
        SimpleBloodConfig cfg = config();
        if (cfg != null) SimpleBloodConfig.save(cfg);
        saved = true;
        onClose();
    }

    private void cancel() {
        restore();
        saved = true;
        onClose();
    }

    private void restore() {
        if (backup == null) return;
        try {
            SimpleBloodConfig old = GSON.fromJson(backup, SimpleBloodConfig.class);
            old.vanillaEntities.ensureDefaults();
            SimpleBloodConfig now = SimpleBloodClient.getConfig();
            if (now != null) {
                old.player.skinColourAdopted = now.player.skinColourAdopted;
                old.player.skinKindAdopted = now.player.skinKindAdopted;
            }
            SimpleBloodClient.setConfig(old);
        } catch (Exception e) {
            SimpleBlood.LOGGER.error("Could not restore config", e);
        }
    }

    @Override
    public void onClose() {
        if (!saved) {
            cleanup("saving the settings", () -> {
                SimpleBloodConfig cfg = config();
                if (cfg != null) SimpleBloodConfig.save(cfg);
            });
        }
        Screens.open(minecraft, parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void draw(Gfx g, int mx, int my) {
        guarded("drawing the settings screen", () -> drawAll(g, mx, my));
    }

    private int changedCount(float now) {
        if (changedAt >= 0 && now - changedAt < 0.5f) return changed;
        changedAt = now;
        SimpleBloodConfig cfg = config();
        if (cfg == null) return changed = 0;
        if (backup == null) return changed = 0;
        JsonElement mine = GSON.toJsonTree(cfg), base = GSON.fromJson(backup, JsonElement.class);
        return changed = differences(mine, base);
    }

    private static final java.util.Set<String> SKIN_RECORDS = java.util.Set.of("skinColourAdopted", "skinKindAdopted");

    private static int differences(JsonElement a, JsonElement b) {
        if (a instanceof JsonObject oa && b instanceof JsonObject ob) {
            int n = 0;
            java.util.Set<String> keys = new java.util.HashSet<>(oa.keySet());
            keys.addAll(ob.keySet());
            for (String k : keys) {
                if (SKIN_RECORDS.contains(k)) continue;
                n += oa.has(k) && ob.has(k) ? differences(oa.get(k), ob.get(k)) : 1;
            }
            return n;
        }
        return java.util.Objects.equals(a, b) ? 0 : 1;
    }

    private void drawAll(Gfx g, int mx, int my) {
        float now = Widget.Anim.now();
        float dt = Math.min(0.1f, now - lastTime);
        lastTime = now;
        SimpleBloodConfig cfg = config();
        Widget.Anim.enabled = cfg == null || cfg.general.menuAnimations;
        Widgets.Slider.sounds = cfg == null || cfg.general.sliderSounds;

        g.fill(0, 0, width, TITLE_H, 0xD0161619);
        g.fill(0, TITLE_H - 1, width, TITLE_H, Widgets.EDGE);
        g.text(Lang.t("simpleblood.screen.simple_blood"), margin + 2, (TITLE_H - 8) / 2, Widgets.TEXT);
        String where = searching() ? Lang.t("simpleblood.screen.search") : pages.get(page).group() + "  /  " + pages.get(page).title();
        g.text(where, margin + 2 + this.font.width(Lang.t("simpleblood.screen.simple_blood")) + 12, (TITLE_H - 8) / 2, 0xFF8C8882);

        panel(g, tabList.x, tabList.y, tabList.x + tabList.w, tabList.y + tabList.h);
        panel(g, list.x, TITLE_H + margin, list.x + list.w, list.y + list.h);
        if (hasPane()) drawPreview(g, mx, my, dt);
        g.fill(0, footer.y, width, height, 0xD0161619);
        g.fill(0, footer.y, width, footer.y + 1, Widgets.EDGE);
        int n = changedCount(now);
        g.text(n == 0 ? Lang.t("simpleblood.screen.no_unsaved_changes") : Lang.t(n == 1 ? "simpleblood.screen.unsaved_change" : "simpleblood.screen.unsaved_changes", n), margin + 4, footer.y + (FOOTER_H - 8) / 2, n == 0 ? 0xFF8C8882 : Widgets.CHANGED);

        int hy = TITLE_H + margin + 10;
        String title = searching() ? Lang.t("simpleblood.screen.search") : pages.get(page).title();
        String hint = searching() ? Lang.t("simpleblood.screen.settings_on_every_page") : pages.get(page).hint();
        int pageChanges = resetPage != null && !searching() && pages.get(page).searchable() ? countChanged(list) : 0;
        if (resetPage != null) {
            resetPage.visible = pageChanges > 0;
            resetPage.count = pageChanges;
            resetPage.w = resetPage.fitWidth(g, pageChanges);
            resetPage.x = list.x + list.w - 10 - resetPage.w;
        }
        int titleW = list.w - 20 - (resetPage != null && resetPage.visible ? resetPage.w + 8 : 0);
        g.textClipped(title, list.x + 10, hy, titleW, Widgets.TEXT);
        g.textClipped(hint, list.x + 10, hy + 11, titleW, Widgets.DESC);
        g.fill(list.x + 10, list.y - 2 - (header.h > 0 ? header.h : 0), list.x + list.w - 10, list.y - 1 - (header.h > 0 ? header.h : 0), 0x30FFFFFF);
        boolean covered = picker != null || Widgets.Dropdown.anyOpen() || popup != null;
        Widget.hoverTop(picker != null ? picker.hovered(mx, my) : Widgets.Dropdown.anyOpen() ? null
                : popup != null ? popup.hovered(mx, my) : root.hovered(mx, my));
        top.render(g, mx, my, dt);
        tabList.render(g, mx, my, dt);
        header.render(g, mx, my, dt);
        list.render(g, mx, my, dt);
        paneBox.render(g, mx, my, dt);
        footer.render(g, mx, my, dt);

        if (popup != null) {
            g.layer();
            g.fill(0, 0, width, height, 0x90000000);
            popup.render(g, mx, my, dt);
        }

        if (Widgets.Dropdown.anyOpen()) {
            g.layer();
            Widgets.Dropdown.renderOpen(g, mx, my, dt);
        }

        if (toast != null && now < toastUntil) {
            float ta = Math.min(1f, (toastUntil - now) / 0.4f);
            int tw = g.textWidth(toast) + 12;
            int tx = (width - tw) / 2, ty = footer.y - 22;
            g.fill(tx, ty, tx + tw, ty + 16, Gfx.alpha(0xF0222226, ta));
            g.outline(tx, ty, tx + tw, ty + 16, Gfx.alpha(Widgets.ACCENT, ta));
            g.text(toast, tx + 6, ty + 4, Gfx.alpha(Widgets.TEXT, ta));
        }

        if (!covered) {
            Widget over = root.hovered(mx, my);
            if (over != null && over.tooltip != null) g.tooltip(over.tooltip, mx, my);
        } else if (picker == null && popup != null && !Widgets.Dropdown.anyOpen()) {
            Widget over = popup.hovered(mx, my);
            if (over != null && over.tooltip != null) g.tooltip(over.tooltip, mx, my);
        } else if (picker != null) {
            g.layer();
            picker.render(g, mx, my, dt);
        }
    }

    private void panel(Gfx g, int x1, int y1, int x2, int y2) {
        g.fill(x1, y1, x2, y2, Widgets.PANEL);
        g.outline(x1, y1, x2, y2, Widgets.EDGE);
        g.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, 0x18FFFFFF);
    }

    private boolean dimWorld(Gfx g) {
        if (minecraft.level == null) return false;
        g.gradient(0, 0, width, height, 0x60000000, 0x80000000);
        return true;
    }

    //? if 1.20.1 {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        draw(new Gfx(graphics, font), mouseX, mouseY);
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics) {
        if (!dimWorld(new Gfx(graphics, font))) super.renderBackground(graphics);
    }
    *///?} elif 1.21.1 {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        draw(new Gfx(graphics, font), mouseX, mouseY);
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!dimWorld(new Gfx(graphics, font))) super.renderBackground(graphics, mouseX, mouseY, partialTick);
    }
    *///?} elif <26.1 {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        draw(new Gfx(graphics, font), mouseX, mouseY);
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!dimWorld(new Gfx(graphics, font))) super.renderBackground(graphics, mouseX, mouseY, partialTick);
    }
    *///?} else {
    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        draw(new Gfx(graphics, font), mouseX, mouseY);
    }

    @Override
    public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!dimWorld(new Gfx(graphics, font))) super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }
    //?}

    private boolean click(double mx, double my, int button) {
        return guarded("a click", () -> clickAt(mx, my, button));
    }

    private boolean release(double mx, double my, int button) {
        return guarded("a click", () -> releaseAt(mx, my, button));
    }

    private boolean drag(double mx, double my, int button, double dx, double dy) {
        return guarded("a drag", () -> dragTo(mx, my, button, dx, dy));
    }

    private boolean key(int key, int modifiers) {
        return guarded("a key", () -> keyDown(key, modifiers));
    }

    private boolean typed(char c) {
        return guarded("typing", () -> charIn(c));
    }

    private boolean clickAt(double mx, double my, int button) {
        if (picker != null) {
            focusAfterClick(picker, mx, my, button);
            return true;
        }
        if (Widgets.Dropdown.anyOpen()) {
            Widgets.Dropdown.clickOpen(mx, my);
            return true;
        }
        if (popup != null) {
            if (popup.contains(mx, my)) focusAfterClick(popup, mx, my, button);
            else popup = null;
            return true;
        }
        root.blur();
        boolean handled = root.mouseClicked(mx, my, button);
        Widget over = root.hovered(mx, my);
        if (over != null && over.wantsFocus() && over.contains(mx, my)) over.setFocused(true);
        return handled;
    }

    private void focusAfterClick(Widget.Container c, double mx, double my, int button) {
        c.blur();
        c.mouseClicked(mx, my, button);
        Widget over = c.hovered(mx, my);
        if (over != null && over.wantsFocus() && over.contains(mx, my)) over.setFocused(true);
    }

    private boolean releaseAt(double mx, double my, int button) {
        return (picker != null ? picker : popup != null ? popup : root).mouseReleased(mx, my, button);
    }

    private boolean dragTo(double mx, double my, int button, double dx, double dy) {
        return (picker != null ? picker : popup != null ? popup : root).mouseDragged(mx, my, button, dx, dy);
    }

    private boolean keyDown(int key, int modifiers) {
        if (picker != null) return picker.keyPressed(key, modifiers) || key == Keys.ESCAPE;
        if (Widgets.Dropdown.anyOpen() && key == Keys.ESCAPE) {
            Widgets.Dropdown.close();
            return true;
        }
        if (popup != null) {
            if (key == Keys.ESCAPE) popup = null;
            else popup.keyPressed(key, modifiers);
            return true;
        }
        if (root.keyPressed(key, modifiers)) return true;
        if (key == Keys.ESCAPE) {
            save();
            return true;
        }
        return false;
    }

    private boolean charIn(char c) {
        return (picker != null ? picker : root).charTyped(c);
    }

    //? if 1.20.1 {
    /*@Override
    public boolean mouseScrolled(double mx, double my, double dy) {
    *///?} else {
    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
    //?}
        return guarded("scrolling", () -> {
            if (picker != null || popup != null) return true;
            Widgets.Dropdown.close();
            return root.mouseScrolled(mx, my, dy);
        });
    }

    //? if <=1.21.1 {
    /*@Override
    public boolean mouseClicked(double mx, double my, int button) { return click(mx, my, button); }

    @Override
    public boolean mouseReleased(double mx, double my, int button) { return release(mx, my, button); }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { return drag(mx, my, button, dx, dy); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int mods = (hasControlDown() ? Keys.CTRL : 0) | (hasShiftDown() ? Keys.SHIFT : 0) | (isPaste(keyCode) ? Keys.PASTE : 0)
                | (isSelectAll(keyCode) ? Keys.SELECT_ALL : 0) | (isCopy(keyCode) ? Keys.COPY : 0) | (isCut(keyCode) ? Keys.CUT : 0);
        return key(keyCode, mods);
    }

    @Override
    public boolean charTyped(char c, int modifiers) { return typed(c); }
    *///?} else {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean doubleClick) { return click(e.x(), e.y(), e.button()); }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent e) { return release(e.x(), e.y(), e.button()); }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent e, double dx, double dy) { return drag(e.x(), e.y(), e.button(), dx, dy); }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        int mods = (e.hasControlDown() ? Keys.CTRL : 0) | (e.hasShiftDown() ? Keys.SHIFT : 0) | (e.isPaste() ? Keys.PASTE : 0)
                | (e.isSelectAll() ? Keys.SELECT_ALL : 0) | (e.isCopy() ? Keys.COPY : 0) | (e.isCut() ? Keys.CUT : 0);
        return key(e.key(), mods);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent e) {
        int cp = e.codepoint();
        return cp >= 0 && cp <= 0xFFFF && typed((char) cp);
    }
    //?}

    private static final class CategoryTab extends Widget {
        private final String text;
        private final boolean open;
        private final Runnable onClick;

        CategoryTab(String text, boolean open, Runnable onClick) {
            this.text = text;
            this.open = open;
            this.onClick = onClick;
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            if (hover > 0.01f && !open) g.fill(x, y, x + w, y + h, Gfx.alpha(0xFFFFFFFF, 0.06f * hover));
            int c = open ? Widgets.TEXT : Gfx.mix(0xFFC9C5BF, Widgets.TEXT, hover);
            g.text(text, x + 6, y + (h - 8) / 2 + 1, c);
            if (open) g.fill(x + 6, y + h - 2, x + 6 + g.textWidth(text), y + h - 1, 0x60FFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != Keys.MOUSE_LEFT || !contains(mx, my)) return false;
            if (!open) {
                Widgets.click();
                onClick.run();
            }
            return true;
        }
    }

    private static final class Tab extends Widget {
        private final String text;
        private final Runnable onClick;
        private final java.util.function.BooleanSupplier selected;
        private final int order;
        private float sel, appear = Float.NaN;

        Tab(String text, Runnable onClick, java.util.function.BooleanSupplier selected, int order) {
            this.text = text;
            this.onClick = onClick;
            this.selected = selected;
            this.order = order;
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            float a = 1f;
            int dx = 0;
            boolean on = selected.getAsBoolean();
            sel = Anim.linear(sel, on ? 1f : 0f, dt, 0.14f);
            if (sel > 0.01f) {
                g.fill(x + 10, y, x + w, y + h, Gfx.alpha(0xFF34343A, sel * a));
                int barH = Math.round(h * sel);
                g.fill(x + 10, y + (h - barH) / 2, x + 12, y + (h + barH) / 2, Gfx.alpha(Widgets.ACCENT, a));
            } else if (hover > 0.01f) {
                g.fill(x + 10, y, x + w, y + h, Gfx.alpha(0xFFFFFFFF, 0.05f * hover * a));
            }
            int c = on ? Widgets.TEXT : Gfx.mix(0xFF9E9A94, Widgets.TEXT, hover);
            g.textClipped(text, x + 17 + dx, y + (h - 8) / 2 + 1, w - 20, Gfx.alpha(c, a));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != Keys.MOUSE_LEFT || !contains(mx, my)) return false;
            if (!selected.getAsBoolean()) {
                Widgets.click();
                onClick.run();
            }
            return true;
        }
    }
}
