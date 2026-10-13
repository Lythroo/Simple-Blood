package com.simpleblood.gui;

import com.simpleblood.SimpleBlood;
import com.simpleblood.SimpleBloodClient;
import com.simpleblood.SimpleBloodConfig;
import com.simpleblood.Guard;
import com.simpleblood.surface.BloodSurfaces;
import com.google.gson.Gson;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Simple Blood's settings screen. Vanilla buttons and sliders, a tab strip down the left,
 * rows of options on the right, a live puddle preview, presets, a colour picker and
 * try-it-out buttons that throw blood at the block you are looking at.
 *
 * Every change applies to the running game immediately (the screen does not pause it), so
 * the world is the preview; Cancel restores the values from when the screen was opened,
 * Save writes them to disk.
 */
public final class BloodConfigScreen extends Screen implements Pages.Host {

    private static final Gson GSON = new Gson();
    private static final int TITLE_H = 26;
    private static final int FOOTER_H = 22;
    /** The tab list's scrollbar (6 px) plus a gap, kept clear of the tabs. */
    private static final int TAB_BAR_ROOM = 8;
    private int sidebar = 96;
    private int margin = 8;
    private int paneW = 160;

    private final Screen parent;
    private final String backup; // config as JSON when the screen opened, for Cancel
    private final List<Pages.Page> pages = Pages.all();
    private int page;
    private int prevPage = -1;
    private float pageAnim = 1f;

    private final Widget.Container root = new Widget.Container();
    private final Widget.Container tabs = new Widget.Container();
    // The sidebar: a settings search on top, the pages under their group names below it.
    private final Widget.ScrollPanel tabList = new Widget.ScrollPanel();
    private Widgets.TextField search;
    private String searchText = "";
    private final Widget.Container header = new Widget.Container();
    private final Widget.ScrollPanel list = new Widget.ScrollPanel();
    private final Widget.Container footer = new Widget.Container();
    private ColourPicker picker;
    private boolean saved;

    // The preview pane: a fixed panel beside the settings list showing a diorama of the effect
    // the hovered (or dragged) setting changes, and nothing else.
    private final java.util.EnumMap<com.simpleblood.surface.PreviewScene.Kind, SceneView> scenes = new java.util.EnumMap<>(com.simpleblood.surface.PreviewScene.Kind.class);
    private Widget.PreviewSource previewRow;   // what the pane shows (sticks until another row is hovered)
    private Widget.PreviewSource hoverRow;     // what is under the mouse right now
    private float previewHover;                // seconds hoverRow has been hovered
    private float previewSwap;                 // fade when the pane switches scenes
    private String previewName;
    private static final float PREVIEW_DELAY = 0.15f;

    private float lastTime = Widget.Anim.now();
    private String toast;
    private float toastUntil;


    public BloodConfigScreen(Screen parent) {
        super(Component.literal("Simple Blood"));
        this.parent = parent;
        SimpleBloodConfig cfg = SimpleBloodClient.getConfig();
        this.backup = cfg == null ? null : GSON.toJson(cfg);
        Guard.retryScreen(); // opened on purpose: an earlier error does not keep it shut
    }

    // ------------------------------------------------------------------ crash guard

    /**
     * Every way in from the game runs through here: an error is reported and closes the
     * screen (the guard does that on the next tick) instead of taking the game down.
     */
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

    /** Like {@link #guarded} but runs even after an error: closing has to happen either way. */
    private void cleanup(String doing, Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            Guard.fail(Guard.Part.SCREEN, doing, t);
        }
    }

    /** The crash guard is closing us: keep nothing, go back to where we came from. */
    public void abandon() {
        saved = true;
        Screens.open(minecraft, parent);
    }

    // ------------------------------------------------------------------ host

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
        picker = new ColourPicker(current, apply, () -> picker = null, note);
        picker.layout(width, height);
    }

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

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        guarded("building the settings screen", this::layout);
    }

    private void layout() {
        root.clear();
        root.x = 0; root.y = 0; root.w = width; root.h = height;
        // Everything scales with the room there is: at a big GUI scale the screen is barely
        // 480 x 270, so the sidebar, gaps and the preview pane shrink to fit.
        margin = width < 520 ? 6 : 10;
        // Wide enough for the longest tab title next to the scrollbar (a tab needs its text
        // plus 16 px of marker and padding; the list around it takes 14 more), but never more
        // than a quarter of the screen.
        int longest = 0;
        for (Pages.Page p : pages) longest = Math.max(longest, this.font.width(p.title()));
        sidebar = Math.min(Math.max(width < 520 ? 84 : 104, longest + 16 + 6 + TAB_BAR_ROOM), width / 4);
        tabs.x = margin; tabs.y = TITLE_H + margin; tabs.w = sidebar; tabs.h = height - TITLE_H - FOOTER_H - margin * 2;
        footer.x = 0; footer.y = height - FOOTER_H; footer.w = width; footer.h = FOOTER_H;
        layoutContent();
        previewRow = null;
        hoverRow = null;
        root.add(tabs);
        root.add(header);
        root.add(list);
        root.add(footer);
        buildTabs();
        buildFooter();
        buildPage(true);
        if (picker != null) picker.layout(width, height);
    }

    /**
     * Widths of the list and the pane beside it. Pages without a diorama (the mob tables) get
     * the whole width for their columns.
     */
    private void layoutContent() {
        int contentX = margin + sidebar + margin;
        int contentW = width - contentX - margin;
        boolean pane = hasPane();
        paneW = pane ? Math.max(124, Math.min(200, Math.round(contentW * 0.38f))) : 0;
        list.x = contentX; list.w = pane ? contentW - paneW - margin : contentW;
        header.x = contentX; header.w = list.w;
    }

    private boolean hasPane() {
        return searching() || pages.get(page).pane();
    }

    /** Whether the list shows search results instead of a page. */
    private boolean searching() {
        return !searchText.isBlank();
    }

    private void buildTabs() {
        tabs.clear();
        tabList.clear();
        int inner = sidebar - 8;
        search = new Widgets.TextField();
        search.hint = "Search...";
        search.set(searchText);
        search.x = tabs.x + 4; search.y = tabs.y + 4; search.w = inner; search.h = 18;
        search.tooltip = List.of("Find a setting on any page");
        search.onChange = text -> {
            boolean was = searching();
            searchText = text;
            if (searching() || was) {
                pageAnim = was ? 1f : 0f;
                buildPage(true);
            }
        };
        tabs.add(search);
        tabList.x = tabs.x + 2; tabList.y = search.y + search.h + 4; tabList.w = sidebar - 4; tabList.h = tabs.y + tabs.h - tabList.y - 2;
        tabList.padding = 2;
        int y = 0;
        String group = null;
        for (int i = 0; i < pages.size(); i++) {
            final int idx = i;
            Pages.Page p = pages.get(i);
            if (!p.group().equals(group)) {
                group = p.group();
                Widgets.Label label = new Widgets.Label(group.toUpperCase(java.util.Locale.ROOT));
                label.colour = Widgets.TEXT_DIM;
                label.x = 4; label.y = y + (i == 0 ? 2 : 6);
                tabList.add(label);
                y = label.y + 11;
            }
            Tab t = new Tab(p.title(), () -> selectPage(idx), () -> page == idx && !searching());
            t.x = 2; t.y = y; t.w = tabList.w - t.x - TAB_BAR_ROOM; t.h = 17;
            t.tooltip = List.of(p.hint());
            tabList.add(t);
            y += 19;
        }
        tabs.add(tabList);
    }

    private void buildFooter() {
        footer.clear();
        int bw = width < 520 ? 54 : 62, bh = 16, gap = 6;
        int total = bw * 3 + gap * 2;
        int x0 = (width - total) / 2;
        int y0 = footer.y + (FOOTER_H - bh) / 2;
        Widgets.Button done = new Widgets.Button("Save", this::save);
        done.accent = true;
        done.x = x0; done.y = y0; done.w = bw; done.h = bh;
        Widgets.Button cancel = new Widgets.Button("Cancel", this::cancel);
        cancel.x = x0 + bw + gap; cancel.y = y0; cancel.w = bw; cancel.h = bh;
        Widgets.Button reset = new Widgets.Button("Undo", () -> {
            restore();
            BloodSurfaces.clear();
            toast("Undone");
            buildPage(true);
        });
        reset.tooltip = List.of("Back to how things were when you opened this screen");
        reset.x = x0 + (bw + gap) * 2; reset.y = y0; reset.w = bw; reset.h = bh;
        // Off to the right: every setting back to how the mod comes. Cancel or Undo still brings
        // back what was there when the screen was opened.
        Widgets.Button defaults = new Widgets.Button("Reset to defaults", () -> {
            SimpleBloodConfig fresh = new SimpleBloodConfig();
            fresh.vanillaEntities.initializeDefaults();
            SimpleBloodClient.setConfig(fresh);
            BloodSurfaces.clear();
            toast("Back to defaults");
            buildPage(true);
        });
        defaults.tooltip = List.of("Every setting back to how the mod comes, mob colours and kinds too",
                "Cancel or Undo brings your settings back");
        int dw = this.font.width(defaults.text) + 14;
        int centreRight = x0 + total + gap;
        if (width - margin - dw < centreRight) { // a narrow screen: the short name
            defaults.text = "Defaults";
            dw = this.font.width(defaults.text) + 14;
        }
        defaults.x = width - margin - dw; defaults.y = y0; defaults.w = dw; defaults.h = bh;
        footer.add(done);
        footer.add(cancel);
        footer.add(reset);
        footer.add(defaults);
    }

    private void buildPage(boolean withHeader) {
        layoutContent();
        list.clear();
        if (withHeader) header.clear();
        int top = TITLE_H + margin;
        Pages.Builder b = new Pages.Builder(list, header, this, !withHeader);
        // Build with a provisional height; the header decides where the list starts.
        header.y = top;
        list.y = top;
        list.h = height - top - FOOTER_H - margin;
        if (searching()) Pages.search(b, pages, searchText);
        else pages.get(page).build().accept(b);
        int headerH = withHeader ? b.headerHeight() : header.h;
        header.h = headerH;
        list.y = top + headerH;
        list.h = height - list.y - FOOTER_H - margin;
        if (withHeader) {
            previewRow = null;
            hoverRow = null;
        }
        for (Widget w : header.children()) w.y += 0; // positions are absolute already
    }

    @Override
    public void tick() {
        guarded("the previews moving", com.simpleblood.surface.PreviewScene::tickAll);
    }

    @Override
    public void removed() {
        Widget.stopHoverTracking();
        cleanup("closing the settings screen", () -> {
            for (SceneView v : scenes.values()) v.dispose();
            scenes.clear();
            com.simpleblood.surface.PreviewScene.disposeAll();
        });
    }

    /** The row under the mouse that has a diorama to show, if any. */
    private static Widget.PreviewSource previewSourceAt(Widget.Container c, double mx, double my) {
        for (int i = c.children().size() - 1; i >= 0; i--) {
            Widget w = c.children().get(i);
            if (!w.visible || !w.contains(mx, my)) continue;
            if (w instanceof Widget.PreviewSource src && src.previewKind() != null) return src;
            if (w instanceof Widget.Container k) {
                Widget.PreviewSource inner = previewSourceAt(k, mx, my);
                if (inner != null) return inner;
            }
        }
        return null;
    }

    /** A row whose control is being dragged keeps its preview even when the mouse strays. */
    private static Widget.PreviewSource activeSource(Widget.Container c) {
        for (Widget w : c.children()) {
            if (w instanceof Widget.PreviewSource src && src.previewKind() != null && src.previewActive()) return src;
            if (w instanceof Widget.Container k) {
                Widget.PreviewSource inner = activeSource(k);
                if (inner != null) return inner;
            }
        }
        return null;
    }

    /** The pane to the right of the list: the diorama of the hovered setting. */
    private void drawPreview(Gfx g, int mx, int my, float dt) {
        int px = list.x + list.w + margin, py = TITLE_H + margin;
        int pw = paneW, ph = height - py - FOOTER_H - margin;
        panel(g, px, py, px + pw, py + ph);

        // Which setting: a dragged control wins, then whatever the mouse rests on for a moment.
        Widget.PreviewSource src = picker != null ? null : activeSource(list);
        boolean active = src != null;
        if (src == null && picker == null) src = previewSourceAt(list, mx, my);
        if (src != hoverRow) {
            hoverRow = src;
            previewHover = 0;
        } else {
            previewHover += dt;
        }
        boolean fresh = false;
        if (src != null && (active || previewHover >= PREVIEW_DELAY) && src != previewRow) {
            previewRow = src;
            previewSwap = 0f;
            fresh = true;
        }
        if (previewRow == null) {
            previewRow = firstSource(list);
            fresh = previewRow != null;
        }
        previewSwap = Math.min(1f, previewSwap + dt * 5f);
        float a = Widget.Anim.easeOut(previewSwap);

        int ix = px + 4, iy = py + 4, iw = pw - 8;
        if (previewRow == null) {
            g.textCentered("Hover something", px + pw / 2, py + ph / 2 - 4, Widgets.TEXT_DARK);
            return;
        }
        String name = previewRow.previewName() == null ? "" : previewRow.previewName();
        SceneView view = scenes.computeIfAbsent(previewRow.previewKind(), k -> new SceneView(k, () -> 0x7A0F0F));
        java.util.function.IntSupplier colour = previewRow.previewColour();
        view.colour(colour != null ? colour : () -> config().player.clientPlayerBloodColor);
        view.show(previewRow.previewAspects());
        view.config(previewRow.previewConfig());
        if (fresh) view.trigger(); // the effect plays the moment a setting is hovered

        // Name, diorama (4:3), then the explanation.
        if (!name.isEmpty()) g.textClipped(name, ix + 2, iy + 2, iw - 4, Gfx.alpha(Widgets.TEXT, a));
        int sy = iy + (name.isEmpty() ? 0 : 13);
        int sh = Math.min(Math.round(iw * view.aspect()), ph - (sy - py) - 8);
        view.draw(g, ix, sy, iw, sh, dt);
        if (a < 1f) g.fill(ix, sy, ix + iw, sy + sh, Gfx.alpha(0xFF000000, 1f - a));
        String text = previewRow.previewText();
        if (text != null && !text.isEmpty()) {
            int ty = sy + sh + 5;
            for (String line : g.wrap(text, iw - 4)) {
                if (ty + 10 > py + ph - 3) break;
                g.text(line, ix + 2, ty, Gfx.alpha(Widgets.TEXT_DIM, a));
                ty += 10;
            }
        }
    }

    /** The first row on the page that has a diorama, so the pane is never blank. */
    private static Widget.PreviewSource firstSource(Widget.Container c) {
        for (Widget w : c.children()) {
            if (w instanceof Widget.PreviewSource src && src.previewKind() != null) return src;
            if (w instanceof Widget.Container k) {
                Widget.PreviewSource inner = firstSource(k);
                if (inner != null) return inner;
            }
        }
        return null;
    }

    private void selectPage(int idx) {
        if (searching()) {
            // Picking a page ends the search.
            searchText = "";
            if (search != null) search.set("");
            page = -1; // so the page below is built even if it is the one already chosen
        }
        if (idx == page) return;
        prevPage = page;
        page = idx;
        pageAnim = 0f;
        buildPage(true);
    }

    // ------------------------------------------------------------------ save / cancel

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
            SimpleBloodClient.setConfig(old);
        } catch (Exception e) {
            SimpleBlood.LOGGER.error("Could not restore config", e);
        }
    }

    @Override
    public void onClose() {
        // Closed by something other than our buttons (a disconnect, another screen): keep
        // what was changed, the world has been showing it all along.
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
        return false; // the world is the live preview
    }

    // ------------------------------------------------------------------ drawing

    private void draw(Gfx g, int mx, int my) {
        guarded("drawing the settings screen", () -> drawAll(g, mx, my));
    }

    private void drawAll(Gfx g, int mx, int my) {
        float now = Widget.Anim.now();
        float dt = Math.min(0.1f, now - lastTime);
        lastTime = now;

        // Title bar.
        g.fill(0, 0, width, TITLE_H, 0xB0000000);
        g.fill(0, TITLE_H - 1, width, TITLE_H, 0x60FFFFFF);
        g.text("Simple Blood", margin + 2, 9, Widgets.TEXT);
        g.textRight(searching() ? "Search" : pages.get(page).title(), width - margin - 2, 9, Widgets.TEXT_DIM);

        // Panels.
        panel(g, tabs.x, tabs.y, tabs.x + tabs.w, tabs.y + tabs.h);
        panel(g, list.x, header.y, list.x + list.w, list.y + list.h);
        if (hasPane()) drawPreview(g, mx, my, dt);
        g.fill(0, footer.y, width, height, 0xB0000000);
        g.fill(0, footer.y, width, footer.y + 1, 0x60FFFFFF);

        // Page slide-in: the content eases in from the right.
        pageAnim = Math.min(1f, pageAnim + dt * 6f);
        float ease = Widget.Anim.easeOut(pageAnim);
        int slide = Math.round((1f - ease) * 10f);
        list.x += slide;
        header.x += slide;
        // Only the topmost widget under the mouse lights up: the picker, when open, covers the rest.
        Widget.hoverTop(picker != null ? picker.hovered(mx, my) : root.hovered(mx, my));
        tabs.render(g, mx, my, dt);
        header.render(g, mx, my, dt);
        list.render(g, mx, my, dt);
        list.x -= slide;
        header.x -= slide;
        if (pageAnim < 1f) g.fill(list.x, header.y, list.x + list.w, list.y + list.h, Gfx.alpha(0xFF000000, 0.5f * (1f - ease)));
        footer.render(g, mx, my, dt);

        // Toast.
        if (toast != null && now < toastUntil) {
            float a = Math.min(1f, (toastUntil - now) / 0.4f);
            int tw = g.textWidth(toast) + 12;
            int tx = (width - tw) / 2, ty = footer.y - 22;
            g.fill(tx, ty, tx + tw, ty + 16, Gfx.alpha(0xF0202020, a));
            g.outline(tx, ty, tx + tw, ty + 16, Gfx.alpha(Widgets.ACCENT, a));
            g.text(toast, tx + 6, ty + 4, Gfx.alpha(Widgets.TEXT, a));
        }

        // Tooltip for whatever is under the mouse (not while the picker is open).
        if (picker == null) {
            Widget over = root.hovered(mx, my);
            if (over != null && over.tooltip != null) g.tooltip(over.tooltip, mx, my);
        } else {
            g.layer();
            picker.render(g, mx, my, dt);
        }
    }

    private void panel(Gfx g, int x1, int y1, int x2, int y2) {
        g.fill(x1, y1, x2, y2, 0x90000000);
        g.bevel(x1, y1, x2, y2, 0x30FFFFFF, 0x80000000);
    }

    /**
     * The backdrop. In a world the game itself is the preview, so it only gets dimmed a
     * little (no blur); outside one the vanilla panorama and menu background are used.
     * Vanilla draws the background itself before calling render on 1.21.11+, so only the
     * 1.21.1 render method calls it.
     */
    private boolean dimWorld(Gfx g) {
        if (minecraft.level == null) return false;
        g.gradient(0, 0, width, height, 0x70000000, 0x90000000);
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

    // ------------------------------------------------------------------ input

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
        return (picker != null ? picker : root).mouseReleased(mx, my, button);
    }

    private boolean dragTo(double mx, double my, int button, double dx, double dy) {
        return (picker != null ? picker : root).mouseDragged(mx, my, button, dx, dy);
    }

    private boolean keyDown(int key, int modifiers) {
        if (picker != null) return picker.keyPressed(key, modifiers) || key == Keys.ESCAPE;
        if (root.keyPressed(key, modifiers)) return true;
        if (key == Keys.ESCAPE) { // escape: same as Save
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
            if (picker != null) return true;
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

    // ------------------------------------------------------------------ tab widget

    /** A sidebar tab: a vanilla button with a red marker when selected. */
    private static final class Tab extends Widget {
        private final String text;
        private final Runnable onClick;
        private final java.util.function.BooleanSupplier selected;
        private float sel;

        Tab(String text, Runnable onClick, java.util.function.BooleanSupplier selected) {
            this.text = text;
            this.onClick = onClick;
            this.selected = selected;
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            boolean on = selected.getAsBoolean();
            sel = Anim.approach(sel, on ? 1f : 0f, dt * 14f);
            boolean over = hot(mx, my);
            g.button(x, y, w, h, true, over || on);
            if (sel > 0.01f) {
                int barH = Math.round((h - 6) * sel);
                g.fill(x + 2, y + (h - barH) / 2, x + 4, y + (h + barH) / 2, Widgets.ACCENT);
            }
            g.textClipped(text, x + 8 + Math.round(2 * sel), y + (h - 8) / 2, w - 14, on ? Widgets.TEXT : Gfx.mix(Widgets.TEXT_DIM, Widgets.TEXT, hover));
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
