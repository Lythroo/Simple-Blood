package com.bloodmod.gui;

import java.util.ArrayList;
import java.util.List;

public abstract class Widget {

    public interface PreviewSource {
        com.bloodmod.surface.PreviewScene.Kind previewKind();
        java.util.function.IntSupplier previewColour();
        String previewText();
        String previewName();
        boolean previewActive();
        java.util.EnumSet<com.bloodmod.surface.PreviewScene.Aspect> previewAspects();
    }

    public int x, y, w, h;
    public boolean visible = true;
    public boolean enabled = true;
    public List<String> tooltip;
    protected float hover;
    protected boolean focused;

    public Widget size(int w, int h) { this.w = w; this.h = h; return this; }
    public Widget at(int x, int y) { this.x = x; this.y = y; return this; }
    public Widget tip(String... lines) { this.tooltip = List.of(lines); return this; }

    public boolean contains(double mx, double my) {
        return visible && mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public void render(Gfx g, int mx, int my, float dt) {
        if (!visible) return;
        boolean over = enabled && contains(mx, my);
        hover = Anim.approach(hover, over ? 1f : 0f, dt * 14f);
        draw(g, mx, my, dt);
    }

    protected abstract void draw(Gfx g, int mx, int my, float dt);

    public Widget hovered(double mx, double my) {
        return contains(mx, my) ? this : null;
    }

    public boolean mouseClicked(double mx, double my, int button) { return false; }
    public boolean mouseReleased(double mx, double my, int button) { return false; }
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { return false; }
    public boolean mouseScrolled(double mx, double my, double delta) { return false; }
    public boolean keyPressed(int key, int modifiers) { return false; }
    public boolean charTyped(char c) { return false; }

    public boolean wantsFocus() { return false; }
    public void setFocused(boolean focused) { this.focused = focused; }
    public boolean isFocused() { return focused; }

    public void blur() { setFocused(false); }

    public static class Container extends Widget {
        protected final List<Widget> children = new ArrayList<>();

        public <T extends Widget> T add(T child) {
            children.add(child);
            return child;
        }

        public void clear() { children.clear(); }

        public List<Widget> children() { return children; }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            for (Widget c : children) c.render(g, mx, my, dt);
        }

        @Override
        public Widget hovered(double mx, double my) {
            if (!contains(mx, my)) return null;
            for (int i = children.size() - 1; i >= 0; i--) {
                Widget h = children.get(i).hovered(mx, my);
                if (h != null) return h;
            }
            return this;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!contains(mx, my)) return false;
            for (int i = children.size() - 1; i >= 0; i--) {
                Widget c = children.get(i);
                if (c.visible && c.mouseClicked(mx, my, button)) return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            boolean any = false;
            for (Widget c : children) if (c.visible && c.mouseReleased(mx, my, button)) any = true;
            return any;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            for (Widget c : children) if (c.visible && c.mouseDragged(mx, my, button, dx, dy)) return true;
            return false;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            if (!contains(mx, my)) return false;
            for (int i = children.size() - 1; i >= 0; i--) {
                Widget c = children.get(i);
                if (c.visible && c.mouseScrolled(mx, my, delta)) return true;
            }
            return false;
        }

        @Override
        public boolean keyPressed(int key, int modifiers) {
            for (Widget c : children) if (c.visible && c.keyPressed(key, modifiers)) return true;
            return false;
        }

        @Override
        public boolean charTyped(char ch) {
            for (Widget c : children) if (c.visible && c.charTyped(ch)) return true;
            return false;
        }

        @Override
        public void blur() {
            super.blur();
            for (Widget c : children) c.blur();
        }

        public Widget focusedChild() {
            for (Widget c : children) {
                if (c instanceof Container k) {
                    Widget f = k.focusedChild();
                    if (f != null) return f;
                } else if (c.isFocused()) {
                    return c;
                }
            }
            return null;
        }
    }

    public static class ScrollPanel extends Container {
        private final List<Integer> localY = new ArrayList<>();
        private final List<Integer> localX = new ArrayList<>();
        public int contentHeight;
        public int padding = 4;
        private float scroll, scrollTarget;
        private boolean draggingBar;
        private double dragStartY;
        private float dragStartScroll;

        @Override
        public <T extends Widget> T add(T child) {
            super.add(child);
            localX.add(child.x);
            localY.add(child.y);
            contentHeight = Math.max(contentHeight, child.y + child.h + padding);
            return child;
        }

        @Override
        public void clear() {
            super.clear();
            localX.clear();
            localY.clear();
            contentHeight = 0;
            scroll = scrollTarget = 0;
        }

        public int maxScroll() { return Math.max(0, contentHeight - h); }

        public void scrollTo(float target) {
            scrollTarget = Math.max(0, Math.min(maxScroll(), target));
        }

        public float scroll() { return scroll; }

        private void place() {
            int s = Math.round(scroll);
            for (int i = 0; i < children.size(); i++) {
                Widget c = children.get(i);
                c.x = x + localX.get(i);
                c.y = y + localY.get(i) - s;
            }
        }

        private boolean barVisible() { return maxScroll() > 0; }
        private int barX() { return x + w - 6; }

        @Override
        public void render(Gfx g, int mx, int my, float dt) {
            if (!visible) return;
            scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
            scroll = Anim.approach(scroll, scrollTarget, dt * 16f);
            if (Math.abs(scroll - scrollTarget) < 0.3f) scroll = scrollTarget;
            place();
            boolean inside = contains(mx, my);
            g.scissor(x, y, x + w, y + h);
            for (Widget c : children) {
                if (c.y + c.h < y || c.y > y + h) { c.hover = 0; continue; }
                c.render(g, inside ? mx : -1, inside ? my : -1, dt);
            }
            g.unscissor();
            if (barVisible()) {
                int trackH = h;
                int thumbH = Math.max(12, (int) ((long) h * h / Math.max(1, contentHeight)));
                int thumbY = y + Math.round((trackH - thumbH) * (scroll / maxScroll()));
                g.scrollbar(barX(), y, 6, trackH, thumbY, thumbH);
            }
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {}

        @Override
        public Widget hovered(double mx, double my) {
            if (!contains(mx, my)) return null;
            Widget h = super.hovered(mx, my);
            return h != null ? h : this;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!contains(mx, my)) return false;
            if (barVisible() && mx >= barX() && button == Keys.MOUSE_LEFT) {
                draggingBar = true;
                dragStartY = my;
                dragStartScroll = scrollTarget;
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            draggingBar = false;
            return super.mouseReleased(mx, my, button);
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (draggingBar) {
                int thumbH = Math.max(12, (int) ((long) h * h / Math.max(1, contentHeight)));
                double perPixel = maxScroll() / (double) Math.max(1, h - thumbH);
                scrollTo((float) (dragStartScroll + (my - dragStartY) * perPixel));
                scroll = scrollTarget;
                return true;
            }
            return super.mouseDragged(mx, my, button, dx, dy);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            if (!contains(mx, my)) return false;
            if (super.mouseScrolled(mx, my, delta)) return true;
            if (!barVisible()) return false;
            scrollTo(scrollTarget - (float) delta * 24f);
            return true;
        }
    }

    public static final class Anim {
        private Anim() {}

        private static final long START = System.nanoTime();

        public static float now() {
            return (System.nanoTime() - START) / 1.0e9f;
        }

        public static float approach(float value, float target, float rate) {
            if (rate <= 0) return value;
            float t = 1f - (float) Math.exp(-rate);
            float v = value + (target - value) * t;
            return Math.abs(target - v) < 0.0005f ? target : v;
        }

        public static float easeOut(float t) {
            t = Math.max(0f, Math.min(1f, t));
            return 1f - (1f - t) * (1f - t) * (1f - t);
        }

        public static float easeInOut(float t) {
            t = Math.max(0f, Math.min(1f, t));
            return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
        }
    }
}
