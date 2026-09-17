package com.bloodmod.gui;

import com.bloodmod.BloodMod;
import com.bloodmod.BloodModClient;
import com.bloodmod.BloodModConfig;
import com.bloodmod.Guard;
import com.bloodmod.surface.BloodSurfaces;
import com.google.gson.Gson;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

public final class BloodConfigScreen extends Screen implements Pages.Host {

    private static final Gson GSON = new Gson();
    private static final int TITLE_H = 26;
    private static final int FOOTER_H = 30;
    private int sidebar = 96;
    private int margin = 8;
    private int paneW = 160;

    private final Screen parent;
    private final String backup;
    private final List<Pages.Page> pages = Pages.all();
    private int page;
    private int prevPage = -1;
    private float pageAnim = 1f;

    private final Widget.Container root = new Widget.Container();
    private final Widget.Container tabs = new Widget.Container();
    private final Widget.Container header = new Widget.Container();
    private final Widget.ScrollPanel list = new Widget.ScrollPanel();
    private final Widget.Container footer = new Widget.Container();
    private ColourPicker picker;
    private boolean saved;

    private final java.util.EnumMap<com.bloodmod.surface.PreviewScene.Kind, SceneView> scenes = new java.util.EnumMap<>(com.bloodmod.surface.PreviewScene.Kind.class);
    private Widget.PreviewSource previewRow;
    private Widget.PreviewSource hoverRow;
    private float previewHover;
    private float previewSwap;
    private String previewName;
    private static final float PREVIEW_DELAY = 0.15f;

    private float lastTime = Widget.Anim.now();
    private String toast;
    private float toastUntil;

    public BloodConfigScreen(Screen parent) {
        super(Component.literal("Simple Blood"));
        this.parent = parent;
        BloodModConfig cfg = BloodModClient.getConfig();
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
    public BloodModConfig config() {
        return BloodModClient.getConfig();
    }

    @Override
    public void openPicker(int current, IntConsumer apply) {
        picker = new ColourPicker(current, apply, () -> picker = null);
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

    @Override
    protected void init() {
        guarded("building the settings screen", this::layout);
    }

    private void layout() {
        root.clear();
        root.x = 0; root.y = 0; root.w = width; root.h = height;
        margin = width < 520 ? 6 : 10;
        sidebar = width < 520 ? 84 : 104;
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

    private void layoutContent() {
        int contentX = margin + sidebar + margin;
        int contentW = width - contentX - margin;
        boolean pane = hasPane();
        paneW = pane ? Math.max(124, Math.min(200, Math.round(contentW * 0.38f))) : 0;
        list.x = contentX; list.w = pane ? contentW - paneW - margin : contentW;
        header.x = contentX; header.w = list.w;
    }

    private boolean hasPane() {
        return pages.get(page).pane();
    }

    private void buildTabs() {
        tabs.clear();
        int n = pages.size();
        int spacing = Math.max(16, Math.min(24, (tabs.h - 8) / n));
        int th = Math.min(20, spacing - 2);
        int y = 0;
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Pages.Page p = pages.get(i);
            Tab t = new Tab(p.title(), () -> selectPage(idx), () -> page == idx);
            t.x = tabs.x + 4; t.y = tabs.y + 4 + y; t.w = sidebar - 8; t.h = th;
            t.tooltip = List.of(p.hint());
            tabs.add(t);
            y += spacing;
        }
    }

    private void buildFooter() {
        footer.clear();
        int bw = width < 520 ? 66 : 76, gap = 8;
        int total = bw * 3 + gap * 2;
        int x0 = (width - total) / 2;
        int y0 = footer.y + 5;
        Widgets.Button done = new Widgets.Button("Done", this::save);
        done.accent = true;
        done.x = x0; done.y = y0; done.w = bw;
        Widgets.Button cancel = new Widgets.Button("Cancel", this::cancel);
        cancel.x = x0 + bw + gap; cancel.y = y0; cancel.w = bw;
        Widgets.Button reset = new Widgets.Button("Undo", () -> {
            restore();
            BloodSurfaces.clear();
            toast("Undone");
            buildPage(true);
        });
        reset.tooltip = List.of("Back to how things were when you opened this screen");
        reset.x = x0 + (bw + gap) * 2; reset.y = y0; reset.w = bw;
        footer.add(done);
        footer.add(cancel);
        footer.add(reset);
    }

    private void buildPage(boolean withHeader) {
        layoutContent();
        list.clear();
        if (withHeader) header.clear();
        int top = TITLE_H + margin;
        Pages.Builder b = new Pages.Builder(list, header, this, !withHeader);
        header.y = top;
        list.y = top;
        list.h = height - top - FOOTER_H - margin;
        pages.get(page).build().accept(b);
        int headerH = withHeader ? b.headerHeight() : header.h;
        header.h = headerH;
        list.y = top + headerH;
        list.h = height - list.y - FOOTER_H - margin;
        if (withHeader) {
            previewRow = null;
            hoverRow = null;
        }
        for (Widget w : header.children()) w.y += 0;
    }

    @Override
    public void tick() {
        guarded("the previews moving", com.bloodmod.surface.PreviewScene::tickAll);
    }

    @Override
    public void removed() {
        cleanup("closing the settings screen", () -> {
            for (SceneView v : scenes.values()) v.dispose();
            scenes.clear();
            com.bloodmod.surface.PreviewScene.disposeAll();
        });
    }

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

    private void drawPreview(Gfx g, int mx, int my, float dt) {
        int px = list.x + list.w + margin, py = TITLE_H + margin;
        int pw = paneW, ph = height - py - FOOTER_H - margin;
        panel(g, px, py, px + pw, py + ph);

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
        if (fresh) view.trigger();

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
        if (idx == page) return;
        prevPage = page;
        page = idx;
        pageAnim = 0f;
        buildPage(true);
    }

    private void save() {
        BloodModConfig cfg = config();
        if (cfg != null) BloodModConfig.save(cfg);
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
            BloodModConfig old = GSON.fromJson(backup, BloodModConfig.class);
            old.vanillaEntities.ensureDefaults();
            BloodModClient.setConfig(old);
        } catch (Exception e) {
            BloodMod.LOGGER.error("Could not restore config", e);
        }
    }

    @Override
    public void onClose() {
        if (!saved) {
            cleanup("saving the settings", () -> {
                BloodModConfig cfg = config();
                if (cfg != null) BloodModConfig.save(cfg);
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

    private void drawAll(Gfx g, int mx, int my) {
        float now = Widget.Anim.now();
        float dt = Math.min(0.1f, now - lastTime);
        lastTime = now;

        g.fill(0, 0, width, TITLE_H, 0xB0000000);
        g.fill(0, TITLE_H - 1, width, TITLE_H, 0x60FFFFFF);
        g.text("Simple Blood", margin + 2, 9, Widgets.TEXT);
        g.textRight(pages.get(page).title(), width - margin - 2, 9, Widgets.TEXT_DIM);

        panel(g, tabs.x, tabs.y, tabs.x + tabs.w, tabs.y + tabs.h);
        panel(g, list.x, header.y, list.x + list.w, list.y + list.h);
        if (hasPane()) drawPreview(g, mx, my, dt);
        g.fill(0, footer.y, width, height, 0xB0000000);
        g.fill(0, footer.y, width, footer.y + 1, 0x60FFFFFF);

        pageAnim = Math.min(1f, pageAnim + dt * 6f);
        float ease = Widget.Anim.easeOut(pageAnim);
        int slide = Math.round((1f - ease) * 10f);
        list.x += slide;
        header.x += slide;
        tabs.render(g, mx, my, dt);
        header.render(g, mx, my, dt);
        list.render(g, mx, my, dt);
        list.x -= slide;
        header.x -= slide;
        if (pageAnim < 1f) g.fill(list.x, header.y, list.x + list.w, list.y + list.h, Gfx.alpha(0xFF000000, 0.5f * (1f - ease)));
        footer.render(g, mx, my, dt);

        if (toast != null && now < toastUntil) {
            float a = Math.min(1f, (toastUntil - now) / 0.4f);
            int tw = g.textWidth(toast) + 12;
            int tx = (width - tw) / 2, ty = footer.y - 22;
            g.fill(tx, ty, tx + tw, ty + 16, Gfx.alpha(0xF0202020, a));
            g.outline(tx, ty, tx + tw, ty + 16, Gfx.alpha(Widgets.ACCENT, a));
            g.text(toast, tx + 6, ty + 4, Gfx.alpha(Widgets.TEXT, a));
        }

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

    private boolean dimWorld(Gfx g) {
        if (minecraft.level == null) return false;
        g.gradient(0, 0, width, height, 0x70000000, 0x90000000);
        return true;
    }

    //? if 1.21.1 {
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
        if (key == Keys.ESCAPE) {
            save();
            return true;
        }
        return false;
    }

    private boolean charIn(char c) {
        return (picker != null ? picker : root).charTyped(c);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return guarded("scrolling", () -> {
            if (picker != null) return true;
            return root.mouseScrolled(mx, my, dy);
        });
    }

    //? if 1.21.1 {
    /*@Override
    public boolean mouseClicked(double mx, double my, int button) { return click(mx, my, button); }

    @Override
    public boolean mouseReleased(double mx, double my, int button) { return release(mx, my, button); }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { return drag(mx, my, button, dx, dy); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int mods = (hasControlDown() ? Keys.CTRL : 0) | (hasShiftDown() ? Keys.SHIFT : 0) | (isPaste(keyCode) ? Keys.PASTE : 0);
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
        int mods = (e.hasControlDown() ? Keys.CTRL : 0) | (e.hasShiftDown() ? Keys.SHIFT : 0) | (e.isPaste() ? Keys.PASTE : 0);
        return key(e.key(), mods);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent e) {
        int cp = e.codepoint();
        return cp >= 0 && cp <= 0xFFFF && typed((char) cp);
    }
    //?}

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
            boolean over = contains(mx, my);
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
