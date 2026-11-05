package com.simpleblood.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class Widgets {

    private Widgets() {}

    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFFA0A0A0;
    public static final int TEXT_DARK = 0xFF606060;
    public static final int ACCENT = 0xFFC8281E;
    public static final int ACCENT_DARK = 0xFF6E1410;
    public static final int GREEN = 0xFF5DBB4A;
    public static final int RED = 0xFFD54A3F;
    public static final int CHANGED = 0xFFE0A33A;
    public static final int PANEL = 0xEE222226, CARD = 0xF029292E, WELL = 0xFF17171A, EDGE = 0xFF0D0D0F;
    public static final int WELL_RIM = 0xFF46464E, FILL = 0xFFA61F1F, FILL_RIM = 0xFFD04545;
    public static final int DESC = 0xFFA9A49D;

    static void flatButton(Gfx g, int x, int y, int w, int h, boolean enabled, boolean over, boolean accent) {
        g.fill(x, y, x + w, y + h, EDGE);
        int face = !enabled ? 0xFF2E2E33 : accent ? (over ? 0xFFBA2727 : FILL) : (over ? 0xFF55555D : 0xFF45454D);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, accent ? FILL_RIM : 0x30FFFFFF);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0x40000000);
    }

    public static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    public static class Label extends Widget {
        public String text;
        public int colour = TEXT;
        public boolean centered;
        public boolean shadow = true;

        public Label(String text) { this.text = text; this.h = 10; }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            if (centered) g.textCentered(text, x + w / 2, y, colour);
            else g.text(text, x, y, colour, shadow);
        }
    }

    public static class Notice extends Widget {
        public static final int GOOD = 0xFF5BBF5B, WARN = 0xFFE0A030, QUIET = 0xFF808080;
        private final Supplier<String> text;
        private final IntSupplier tone;
        private final IntSupplier swatch;

        public Notice(Supplier<String> text, IntSupplier tone, IntSupplier swatch) {
            this.text = text;
            this.tone = tone;
            this.swatch = swatch;
            this.h = 26;
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            int t = tone.getAsInt();
            g.fill(x, y, x + w, y + h, 0x40000000);
            g.fill(x, y, x + 2, y + h, t);
            int tx = x + 8;
            int sw = swatch.getAsInt();
            if (sw >= 0) {
                g.fill(tx, y + (h - 16) / 2, tx + 16, y + (h + 16) / 2, 0xFF000000);
                g.fill(tx + 1, y + (h - 16) / 2 + 1, tx + 15, y + (h + 16) / 2 - 1, 0xFF000000 | sw);
                tx += 22;
            }
            java.util.List<String> lines = g.wrap(text.get(), x + w - 6 - tx);
            int n = Math.min(3, lines.size());
            int ty = y + (h - n * 10) / 2 + 1;
            for (int i = 0; i < n; i++) {
                g.text(lines.get(i), tx, ty + i * 10, i == 0 ? TEXT : TEXT_DIM);
            }
        }
    }

    public static class Heading extends Widget {
        public final String text;

        public Heading(String text) { this.text = text; this.h = 18; }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            g.text(text, x, y + 6, 0xFFC9C5BF);
            int tw = g.textWidth(text);
            g.fill(x + tw + 8, y + 9, x + w, y + 10, 0x30FFFFFF);
        }
    }

    public static class Button extends Widget {
        public String text;
        public Runnable onClick;
        public boolean accent;
        public java.util.function.BooleanSupplier done;
        public String doneText;

        public Button(String text, Runnable onClick) {
            this.text = text;
            this.onClick = onClick;
            this.h = 20;
        }

        private boolean isDone() { return done != null && done.getAsBoolean(); }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            boolean on = enabled && !isDone();
            boolean over = on && hot(mx, my);
            flatButton(g, x, y, w, h, on, over, accent && on);
            String s = on || !isDone() || doneText == null ? text : doneText;
            g.textClipped(s, x + (w - Math.min(w - 6, g.textWidth(s))) / 2, y + (h - 7) / 2, w - 6, on ? TEXT : TEXT_DIM);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || button != Keys.MOUSE_LEFT || !contains(mx, my)) return false;
            if (isDone()) return true;
            click();
            if (onClick != null) onClick.run();
            return true;
        }
    }

    public static class ResetButton extends Button {
        public int count;
        private static final String[] ARROW = {
                "...###..",
                "..#...#.",
                ".###...#",
                "..#....#",
                "#......#",
                "#......#",
                ".#....#.",
                "..####.."};

        public ResetButton(String text, Runnable onClick) {
            super(text, onClick);
            this.h = 16;
        }

        public int fitWidth(Gfx g, int n) {
            return 7 + 8 + 5 + g.textWidth(label(n)) + 7;
        }

        private String label(int n) {
            return n > 0 ? text + " (" + n + ")" : text;
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            boolean over = enabled && hot(mx, my);
            flatButton(g, x, y, w, h, enabled, over, false);
            int ax = x + 7, ay = y + (h - ARROW.length) / 2;
            int c = enabled ? (over ? 0xFFF2C064 : CHANGED) : TEXT_DARK;
            for (int r = 0; r < ARROW.length; r++) {
                String row = ARROW[r];
                for (int i = 0; i < row.length(); i++) if (row.charAt(i) == '#') g.fill(ax + i, ay + r, ax + i + 1, ay + r + 1, c);
            }
            g.textClipped(label(count), ax + 8 + 5, y + (h - 7) / 2, w - 27, enabled ? TEXT : TEXT_DIM);
        }
    }

    public static class PlayButton extends Widget {
        private final Runnable toggle;
        private final Supplier<Boolean> active, playing, sounding;
        private final Runnable whilePlaying;
        private float glow;

        public PlayButton(Runnable toggle, Supplier<Boolean> active, Supplier<Boolean> playing, Supplier<Boolean> sounding, Runnable whilePlaying) {
            this.toggle = toggle;
            this.active = active;
            this.playing = playing;
            this.sounding = sounding;
            this.whilePlaying = whilePlaying;
            this.w = 20;
            this.h = 20;
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            boolean on = enabled && active.get();
            boolean plays = on && playing.get();
            if (plays) whilePlaying.run();
            glow = Anim.approach(glow, plays && sounding.get() ? 1f : 0f, dt * 10f);
            boolean over = on && hot(mx, my);
            int lift = over ? -1 : 0;
            g.button(x, y + lift, w, h, on, over);
            if (glow > 0.01f) {
                int e = Gfx.alpha(ACCENT, glow);
                g.fill(x, y + lift, x + w, y + lift + 1, e);
                g.fill(x, y + lift + h - 1, x + w, y + lift + h, e);
                g.fill(x, y + lift, x + 1, y + lift + h, e);
                g.fill(x + w - 1, y + lift, x + w, y + lift + h, e);
            }
            int c = on ? (over ? 0xFFFFFFFF : TEXT) : TEXT_DARK;
            if (plays) {
                g.fill(x + 6, y + lift + 6, x + 14, y + lift + 14, c);
            } else {
                int ax = x + 7, ay = y + lift + 5;
                for (int i = 0; i < 6; i++) g.fill(ax + i, ay + i, ax + i + 1, ay + 10 - i, c);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || !active.get() || button != Keys.MOUSE_LEFT || !contains(mx, my)) return false;
            toggle.run();
            return true;
        }
    }

    public static class Toggle extends Widget {
        private final Supplier<Boolean> get;
        private final Consumer<Boolean> set;
        private float knob = -1;

        public Toggle(Supplier<Boolean> get, Consumer<Boolean> set) {
            this.get = get;
            this.set = set;
            this.w = 28;
            this.h = 14;
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            boolean on = get.get();
            if (knob < 0) knob = on ? 1 : 0;
            knob = Anim.linear(knob, on ? 1f : 0f, dt, 0.09f);
            boolean over = enabled && hot(mx, my);
            g.fill(x, y, x + w, y + h, EDGE);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, Gfx.mix(WELL, FILL, knob));
            g.outline(x + 1, y + 1, x + w - 1, y + h - 1, Gfx.mix(over ? 0xFF6A6A73 : WELL_RIM, FILL_RIM, knob));
            int size = h - 4;
            int kx = x + 2 + Math.round((w - 4 - size) * knob);
            int face = enabled ? Gfx.mix(0xFF8C8882, 0xFFF2F0EC, knob) : 0xFF5A5A5A;
            g.fill(kx, y + 2, kx + size, y + 2 + size, face);
            g.fill(kx + 1, y + 1 + size, kx + size, y + 2 + size, Gfx.scale(face, 0.62f) | 0xFF000000);
            g.fill(kx + size - 1, y + 3, kx + size, y + 2 + size, Gfx.scale(face, 0.62f) | 0xFF000000);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || button != Keys.MOUSE_LEFT || !contains(mx, my)) return false;
            click();
            set.accept(!get.get());
            return true;
        }
    }

    public static class Pair extends Widget.Container {
        private final Widget first, second;

        public Pair(Widget first, Widget second) {
            this.first = add(first);
            this.second = add(second);
            this.w = first.w + 4 + second.w;
            this.h = Math.max(first.h, second.h);
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            first.x = x;
            first.y = y + (h - first.h) / 2;
            second.x = x + first.w + 4;
            second.y = y + (h - second.h) / 2;
            super.draw(g, mx, my, dt);
        }
    }

    public static class Check extends Widget {
        private final Supplier<Boolean> get;
        private final Consumer<Boolean> set;

        public Check(Supplier<Boolean> get, Consumer<Boolean> set) {
            this.get = get;
            this.set = set;
            this.w = 17;
            this.h = 17;
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            g.checkbox(x, y, 17, get.get(), enabled && hot(mx, my));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || button != Keys.MOUSE_LEFT || !contains(mx, my)) return false;
            click();
            set.accept(!get.get());
            return true;
        }
    }

    public static class Slider extends Widget {
        private final IntSupplier get;
        private final IntConsumer set;
        public final int min, max, step;
        public Function<Integer, String> format = v -> Integer.toString(v);
        private float shown = Float.NaN;
        boolean dragging;
        private static final int BTN = 11, GAP = 3;

        public Slider(int min, int max, int step, IntSupplier get, IntConsumer set) {
            this.min = min;
            this.max = max;
            this.step = Math.max(1, step);
            this.get = get;
            this.set = set;
            this.w = 120;
            this.h = 14;
        }

        public Slider unit(String unit) {
            this.format = v -> v + unit;
            return this;
        }

        private float frac(int v) { return (v - min) / (float) Math.max(1, max - min); }

        int valueWidth(Gfx g) {
            return Math.max(g.textWidth(format.apply(min)), g.textWidth(format.apply(max))) + 2;
        }

        private int valueW = 30;
        private int trackX() { return x + BTN + GAP; }
        private int trackW() { return Math.max(20, w - valueW - 4 - (BTN + GAP) * 2); }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            valueW = Math.min(w / 2, valueWidth(g));
            int v = get.getAsInt();
            float target = frac(v);
            if (Float.isNaN(shown) || dragging) shown = target;
            else shown = Anim.approach(shown, target, dt * 16f);
            int tx = trackX(), tw = trackW(), cy = y + h / 2;
            boolean overMinus = enabled && hot(mx, my) && mx < tx - 1;
            boolean overPlus = enabled && hot(mx, my) && mx >= tx + tw + 1 && mx < tx + tw + GAP + BTN;
            boolean overTrack = enabled && hot(mx, my) && mx >= tx && mx < tx + tw;
            stepButton(g, x, cy - BTN / 2, "-", overMinus, v > min);
            stepButton(g, tx + tw + GAP, cy - BTN / 2, "+", overPlus, v < max);
            g.fill(tx, cy - 3, tx + tw, cy + 3, EDGE);
            g.fill(tx + 1, cy - 2, tx + tw - 1, cy + 2, WELL);
            int hx = tx + Math.round((tw - 4) * shown);
            g.fill(tx + 1, cy - 2, hx + 2, cy + 2, FILL);
            int face = enabled ? (overTrack || dragging || focused ? 0xFFFFFFFF : 0xFFE8E5E0) : 0xFF707070;
            g.fill(hx - 1, cy - 6, hx + 5, cy + 6, EDGE);
            g.fill(hx, cy - 5, hx + 4, cy + 5, face);
            g.fill(hx + 3, cy - 4, hx + 4, cy + 5, 0xFF9A968F);
            g.fill(hx, cy + 4, hx + 4, cy + 5, 0xFF9A968F);
            String s = format.apply(v);
            g.textRight(s, x + w, y + (h - 8) / 2 + 1, enabled ? TEXT : TEXT_DIM);
        }

        private static void stepButton(Gfx g, int bx, int by, String sign, boolean over, boolean can) {
            flatButton(g, bx, by, BTN, BTN, can, over && can, false);
            int c = can ? TEXT : TEXT_DARK, cx = bx + BTN / 2, cy = by + BTN / 2;
            g.fill(cx - 2, cy, cx + 3, cy + 1, c);
            if (sign.equals("+")) g.fill(cx, cy - 2, cx + 1, cy + 3, c);
        }

        private void nudge(int dir) {
            int v = Math.max(min, Math.min(max, get.getAsInt() + dir * step));
            if (v != get.getAsInt()) {
                set.accept(v);
                tickSound(v);
            }
        }

        private void setFromMouse(double mx) {
            double f = (mx - (trackX() + 2)) / (double) Math.max(1, trackW() - 4);
            f = Math.max(0, Math.min(1, f));
            int v = min + (int) Math.round(f * (max - min) / step) * step;
            v = Math.max(min, Math.min(max, v));
            if (v != get.getAsInt()) {
                set.accept(v);
                tickSound(v);
            }
        }

        private static float lastTick = -1;
        public static boolean sounds = true;

        private void tickSound(int v) {
            if (!sounds) return;
            float now = Anim.now();
            if (lastTick >= 0 && now - lastTick < 0.04f) return;
            lastTick = now;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.UI_BUTTON_CLICK.value(), 0.8f + 0.7f * frac(v), 0.2f));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || button != Keys.MOUSE_LEFT || !contains(mx, my)) return false;
            int tx = trackX(), tw = trackW();
            if (mx < tx - 1) nudge(-1);
            else if (mx >= tx + tw + 1 && mx < tx + tw + GAP + BTN) nudge(1);
            else if (mx >= tx && mx < tx + tw) {
                dragging = true;
                setFromMouse(mx);
            }
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (!dragging) return false;
            setFromMouse(mx);
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            if (dragging) {
                dragging = false;
                click();
                return true;
            }
            return false;
        }

        @Override public boolean wantsFocus() { return enabled; }

        @Override
        public boolean keyPressed(int key, int modifiers) {
            if (!focused) return false;
            if (key == Keys.LEFT) { nudge(-1); return true; }
            if (key == Keys.RIGHT) { nudge(1); return true; }
            return false;
        }
    }

    public static class Segmented extends Widget {
        private final String[] keys, labels;
        private final Supplier<String> get;
        private final Consumer<String> set;

        public Segmented(String[] keys, String[] labels, Supplier<String> get, Consumer<String> set) {
            this.keys = keys;
            this.labels = labels;
            this.get = get;
            this.set = set;
            this.w = 120;
            this.h = 14;
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            g.fill(x, y, x + w, y + h, EDGE);
            String cur = get.get();
            int n = keys.length;
            for (int i = 0; i < n; i++) {
                int x1 = x + 1 + (w - 2) * i / n, x2 = x + 1 + (w - 2) * (i + 1) / n - (i < n - 1 ? 1 : 0);
                boolean sel = keys[i].equals(cur);
                boolean over = enabled && hot(mx, my) && mx >= x1 && mx < x2;
                g.fill(x1, y + 1, x2, y + h - 1, sel ? FILL : over ? 0xFF313137 : WELL);
                if (sel) g.fill(x1, y + 1, x2, y + 2, FILL_RIM);
                int c = sel ? TEXT : over ? TEXT : TEXT_DIM;
                g.textClipped(labels[i], x1 + Math.max(2, (x2 - x1 - g.textWidth(labels[i])) / 2), y + (h - 8) / 2 + 1, x2 - x1 - 4, c);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || button != Keys.MOUSE_LEFT || !contains(mx, my)) return false;
            int i = (int) Math.max(0, Math.min(keys.length - 1, (mx - x - 1) * keys.length / Math.max(1, w - 2)));
            if (!keys[i].equals(get.get())) {
                click();
                set.accept(keys[i]);
            }
            return true;
        }
    }

    public static class Dropdown extends Widget {
        private final String[] keys, labels;
        private final Supplier<String> get;
        private final Consumer<String> set;
        private static Dropdown open;
        private static int[] box;
        private static final int ITEM = 14;

        public Dropdown(String[] keys, String[] labels, Supplier<String> get, Consumer<String> set) {
            this.keys = keys;
            this.labels = labels;
            this.get = get;
            this.set = set;
            this.w = 120;
            this.h = 14;
        }

        private int index() {
            String cur = get.get();
            for (int i = 0; i < keys.length; i++) if (keys[i].equals(cur)) return i;
            return 0;
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            boolean isOpen = open == this;
            boolean over = enabled && hot(mx, my);
            g.fill(x, y, x + w, y + h, EDGE);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, WELL);
            g.outline(x + 1, y + 1, x + w - 1, y + h - 1, isOpen ? ACCENT : over ? 0xFF6A6A73 : 0xFF38383F);
            g.textClipped(labels[index()], x + 5, y + (h - 8) / 2 + 1, w - 18, enabled ? TEXT : TEXT_DIM);
            int ax = x + w - 10, ay = y + h / 2 - 1;
            int c = over || isOpen ? TEXT : TEXT_DIM;
            for (int r = 0; r < 3; r++) {
                int row = isOpen ? 2 - r : r;
                g.fill(ax + r, ay + row, ax + 5 - r, ay + row + 1, c);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || button != Keys.MOUSE_LEFT || !contains(mx, my)) return false;
            click();
            if (open == this) open = null;
            else {
                open = this;
                box = null;
            }
            return true;
        }

        public static boolean anyOpen() { return open != null; }

        public static void close() { open = null; }

        private static int[] listBox(Gfx g) {
            Dropdown d = open;
            int lh = d.keys.length * ITEM + 2;
            int lw = d.w;
            for (String l : d.labels) lw = Math.max(lw, g.textWidth(l) + 14);
            int lx = d.x + d.w - lw;
            int ly = d.y + d.h + 1;
            if (ly + lh > g.height() - 2) ly = Math.max(2, d.y - lh - 1);
            return new int[] {lx, ly, lw, lh};
        }

        public static void renderOpen(Gfx g, int mx, int my, float dt) {
            Dropdown d = open;
            if (d == null) return;
            if (!d.visible) { open = null; return; }
            int[] b = listBox(g);
            box = b;
            int lx = b[0], ly = b[1], lw = b[2], lh = b[3];
            g.fill(lx, ly, lx + lw, ly + lh, EDGE);
            g.fill(lx + 1, ly + 1, lx + lw - 1, ly + lh - 1, 0xFF29292E);
            g.fill(lx, ly + lh, lx + lw, ly + lh + 3, 0x50000000);
            int cur = d.index();
            for (int i = 0; i < d.keys.length; i++) {
                int iy = ly + 1 + i * ITEM;
                boolean over = mx >= lx && mx < lx + lw && my >= iy && my < iy + ITEM;
                if (i == cur) {
                    g.fill(lx + 1, iy, lx + lw - 1, iy + ITEM, 0xFF33333A);
                    g.fill(lx + 1, iy, lx + 3, iy + ITEM, ACCENT);
                }
                if (over) g.fill(lx + 1, iy, lx + lw - 1, iy + ITEM, 0xFF3A3A42);
                g.text(d.labels[i], lx + 7, iy + 3, i == cur || over ? TEXT : 0xFFC9C5BF);
            }
        }

        public static boolean clickOpen(double mx, double my) {
            Dropdown d = open;
            if (d == null) return false;
            int[] b = box;
            if (b == null) { open = null; return true; }
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                int i = (int) ((my - b[1] - 1) / ITEM);
                if (i >= 0 && i < d.keys.length) {
                    click();
                    d.set.accept(d.keys[i]);
                }
                open = null;
                return true;
            }
            open = null;
            return true;
        }
    }

    public static class Cycle extends Widget {
        private final String[] keys;
        private final String[] labels;
        private final Supplier<String> get;
        private final Consumer<String> set;
        private float flash;

        public Cycle(String[] keys, String[] labels, Supplier<String> get, Consumer<String> set) {
            this.keys = keys;
            this.labels = labels;
            this.get = get;
            this.set = set;
            this.w = 100;
            this.h = 20;
        }

        private int index() {
            String cur = get.get();
            for (int i = 0; i < keys.length; i++) if (keys[i].equals(cur)) return i;
            return 0;
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            flash = Anim.approach(flash, 0f, dt * 8f);
            boolean over = enabled && hot(mx, my);
            g.button(x, y, w, h, enabled, over);
            if (flash > 0.01f) g.fill(x + 2, y + 2, x + w - 2, y + h - 2, Gfx.alpha(0xFFFFFFFF, 0.25f * flash));
            String s = labels[index()];
            g.textClipped(s, x + (w - Math.min(w - 12, g.textWidth(s))) / 2, y + (h - 8) / 2, w - 12, enabled ? TEXT : TEXT_DIM);
            int n = keys.length, cur = index();
            int dotsW = n * 3 - 1;
            for (int i = 0; i < n; i++) {
                g.fill(x + (w - dotsW) / 2 + i * 3, y + h - 4, x + (w - dotsW) / 2 + i * 3 + 2, y + h - 3, i == cur ? ACCENT : 0x60FFFFFF);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || !contains(mx, my) || (button != Keys.MOUSE_LEFT && button != Keys.MOUSE_RIGHT)) return false;
            click();
            int i = index() + (button == Keys.MOUSE_LEFT ? 1 : keys.length - 1);
            set.accept(keys[i % keys.length]);
            flash = 1f;
            return true;
        }
    }

    public static class TextField extends Widget {
        public String value = "";
        public String hint = "";
        public int maxLength = 64;
        public Consumer<String> onChange;
        public Runnable onEnter;
        private int cursor;
        private int anchor = -1;
        private int viewStart;
        private boolean dragging;
        private float caretBlink;

        public TextField() {
            this.w = 120;
            this.h = 20;
        }

        public TextField set(String v) {
            value = v == null ? "" : v;
            cursor = value.length();
            anchor = -1;
            viewStart = 0;
            return this;
        }

        @Override
        public boolean wantsFocus() { return true; }

        @Override
        public void setFocused(boolean focused) {
            boolean was = this.focused;
            super.setFocused(focused);
            if (was != focused) textInput(focused);
        }

        private void textInput(boolean on) {
            if (on) com.simpleblood.Tally.count("ui.textinput");
            //? if >=26.3 {
            /*var input = Minecraft.getInstance().textInputManager();
            if (on) input.startTextInput(this);
            else input.stopTextInput(this);
            *///?} elif >=26.1 {
            var input = Minecraft.getInstance().textInputManager();
            if (on) input.startTextInput();
            else input.stopTextInput();
            //?}
        }

        private boolean hasSelection() { return anchor >= 0 && anchor != cursor; }
        private int selStart() { return Math.min(anchor, cursor); }
        private int selEnd() { return Math.max(anchor, cursor); }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            g.textField(x, y, w, h, focused || (enabled && hot(mx, my)));
            int tx = x + 4, ty = y + (h - 8) / 2;
            int inner = w - 8;
            if (value.isEmpty() && !focused) {
                g.textClipped(hint, tx, ty, inner, TEXT_DARK);
                return;
            }
            viewStart = Math.max(0, Math.min(viewStart, Math.min(cursor, value.length())));
            while (viewStart < cursor && g.textWidth(value.substring(viewStart, cursor)) > inner - 2) viewStart++;
            String vis = g.font.plainSubstrByWidth(value.substring(viewStart), inner);
            int visEnd = viewStart + vis.length();
            if (focused && hasSelection()) {
                int a = Math.max(selStart(), viewStart), b = Math.min(selEnd(), visEnd);
                if (b > a) {
                    int sx = tx + g.textWidth(value.substring(viewStart, a));
                    int ex = tx + g.textWidth(value.substring(viewStart, b));
                    g.fill(sx, ty - 1, ex, ty + 9, 0xA03C6EB4);
                }
            }
            g.text(vis, tx, ty, enabled ? TEXT : TEXT_DIM);
            if (focused) {
                caretBlink += dt;
                if ((int) (caretBlink * 2.5f) % 2 == 0) {
                    int cx = tx + g.textWidth(value.substring(viewStart, Math.max(viewStart, Math.min(cursor, visEnd))));
                    g.fill(cx, ty - 1, cx + 1, ty + 9, TEXT);
                }
            }
        }

        private void changed() {
            if (onChange != null) onChange.accept(value);
        }

        private int indexAt(double mx) {
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            int rel = (int) Math.round(mx) - (x + 4);
            int i = viewStart;
            while (i < value.length()) {
                int left = font.width(value.substring(viewStart, i));
                int right = font.width(value.substring(viewStart, i + 1));
                if (rel < (left + right) / 2) break;
                i++;
            }
            return i;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || !contains(mx, my)) return false;
            if (button == Keys.MOUSE_RIGHT) {
                set("");
                changed();
                return true;
            }
            cursor = indexAt(mx);
            anchor = cursor;
            dragging = true;
            caretBlink = 0;
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (!dragging) return false;
            cursor = indexAt(mx);
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            boolean was = dragging;
            dragging = false;
            if (anchor == cursor) anchor = -1;
            return was;
        }

        private void moveTo(int to, boolean select) {
            if (select) {
                if (anchor < 0) anchor = cursor;
            } else {
                anchor = -1;
            }
            cursor = Math.max(0, Math.min(value.length(), to));
            caretBlink = 0;
        }

        private int wordFrom(int from, int dir) {
            int i = from;
            if (dir < 0) {
                while (i > 0 && value.charAt(i - 1) == ' ') i--;
                while (i > 0 && value.charAt(i - 1) != ' ') i--;
            } else {
                while (i < value.length() && value.charAt(i) == ' ') i++;
                while (i < value.length() && value.charAt(i) != ' ') i++;
            }
            return i;
        }

        private boolean deleteSelection() {
            if (!hasSelection()) return false;
            int a = selStart(), b = selEnd();
            value = value.substring(0, a) + value.substring(b);
            cursor = a;
            anchor = -1;
            return true;
        }

        @Override
        public boolean keyPressed(int key, int modifiers) {
            if (!focused) return false;
            boolean ctrl = (modifiers & Keys.CTRL) != 0, shift = (modifiers & Keys.SHIFT) != 0;
            if ((modifiers & Keys.SELECT_ALL) != 0) {
                anchor = 0;
                cursor = value.length();
                return true;
            }
            if ((modifiers & (Keys.COPY | Keys.CUT)) != 0) {
                if (hasSelection()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(value.substring(selStart(), selEnd()));
                    if ((modifiers & Keys.CUT) != 0 && deleteSelection()) changed();
                }
                return true;
            }
            if ((modifiers & Keys.PASTE) != 0) {
                insert(Minecraft.getInstance().keyboardHandler.getClipboard());
                return true;
            }
            switch (key) {
                case Keys.BACKSPACE -> {
                    if (deleteSelection()) {
                        changed();
                    } else if (cursor > 0) {
                        int from = ctrl ? wordFrom(cursor, -1) : cursor - 1;
                        value = value.substring(0, from) + value.substring(cursor);
                        cursor = from;
                        changed();
                    }
                    return true;
                }
                case Keys.DELETE -> {
                    if (deleteSelection()) {
                        changed();
                    } else if (cursor < value.length()) {
                        int to = ctrl ? wordFrom(cursor, 1) : cursor + 1;
                        value = value.substring(0, cursor) + value.substring(to);
                        changed();
                    }
                    return true;
                }
                case Keys.LEFT -> {
                    if (!shift && hasSelection()) moveTo(selStart(), false);
                    else moveTo(ctrl ? wordFrom(cursor, -1) : cursor - 1, shift);
                    return true;
                }
                case Keys.RIGHT -> {
                    if (!shift && hasSelection()) moveTo(selEnd(), false);
                    else moveTo(ctrl ? wordFrom(cursor, 1) : cursor + 1, shift);
                    return true;
                }
                case Keys.HOME -> { moveTo(0, shift); return true; }
                case Keys.END -> { moveTo(value.length(), shift); return true; }
                case Keys.ENTER, Keys.NUMPAD_ENTER -> { if (onEnter != null) onEnter.run(); return true; }
                default -> { return false; }
            }
        }

        private void insert(String s) {
            if (s == null || s.isEmpty()) return;
            deleteSelection();
            StringBuilder sb = new StringBuilder();
            for (char c : s.toCharArray()) if (c >= 32 && c != 127) sb.append(c);
            String ins = sb.toString();
            if (value.length() + ins.length() > maxLength) ins = ins.substring(0, Math.max(0, maxLength - value.length()));
            value = value.substring(0, cursor) + ins + value.substring(cursor);
            cursor += ins.length();
            anchor = -1;
            changed();
        }

        @Override
        public boolean charTyped(char c) {
            if (!focused || c < 32 || c == 127) return false;
            insert(String.valueOf(c));
            return true;
        }

        @Override
        public void blur() {
            super.blur();
            anchor = -1;
            dragging = false;
        }
    }

    public static class Swatch extends Widget {
        private final IntSupplier get;
        public Runnable onClick;

        public Swatch(IntSupplier get, Runnable onClick) {
            this.get = get;
            this.onClick = onClick;
            this.w = 100;
            this.h = 20;
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            boolean over = enabled && hot(mx, my);
            flatButton(g, x, y, w, h, enabled, over, false);
            int rgb = get.getAsInt() & 0xFFFFFF;
            String hex = String.format("#%06X", rgb);
            boolean withHex = g.textWidth(hex) <= w - 29;
            int right = withHex ? x + 20 : x + w - 4;
            g.fill(x + 4, y + 4, right, y + h - 4, 0xFF000000);
            g.fill(x + 5, y + 5, right - 1, y + h - 5, 0xFF000000 | rgb);
            g.fill(x + 5, y + 5, right - 1, y + 6, Gfx.alpha(0xFFFFFFFF, 0.25f));
            if (withHex) g.text(hex, x + 25, y + (h - 8) / 2, enabled ? TEXT : TEXT_DIM);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || button != Keys.MOUSE_LEFT || !contains(mx, my)) return false;
            click();
            if (onClick != null) onClick.run();
            return true;
        }
    }

    public static class Row extends Widget.Container implements Widget.PreviewSource {
        public final String name;
        public final String desc;
        public final Widget control;
        private final Supplier<Boolean> isDefault;
        private final Runnable reset;
        public boolean even;
        public com.simpleblood.surface.PreviewScene.Kind previewKind;
        public IntSupplier previewColour;
        public String previewText;

        @Override public com.simpleblood.surface.PreviewScene.Kind previewKind() { return previewKind; }
        @Override public IntSupplier previewColour() { return previewColour; }
        @Override public String previewText() { return previewText; }
        @Override public String previewName() { return name; }
        @Override public boolean previewActive() { return control instanceof Slider s && s.dragging; }
        public com.simpleblood.SimpleBloodConfig previewConfig;
        @Override public com.simpleblood.SimpleBloodConfig previewConfig() { return previewConfig; }
        public int previewFigure;
        @Override public int previewFigure() { return previewFigure; }
        public java.util.function.Supplier<com.simpleblood.BloodKind> previewBloodKind;
        public com.simpleblood.HitContext.Weapon previewWeapon;
        public String previewItem;
        @Override public com.simpleblood.HitContext.Weapon previewWeapon() { return previewWeapon; }
        @Override public String previewItem() { return previewItem; }
        @Override public com.simpleblood.BloodKind previewBloodKind() { return previewBloodKind == null ? null : previewBloodKind.get(); }
        public java.util.EnumSet<com.simpleblood.surface.PreviewScene.Aspect> previewAspects = java.util.EnumSet.allOf(com.simpleblood.surface.PreviewScene.Aspect.class);
        @Override public java.util.EnumSet<com.simpleblood.surface.PreviewScene.Aspect> previewAspects() { return previewAspects; }

        public Widget side;
        public net.minecraft.world.item.ItemStack icon;

        public Row side(Widget w) {
            side = w;
            add(w);
            return this;
        }

        public Row(String name, String desc, Widget control, Supplier<Boolean> isDefault, Runnable reset) {
            this.name = name;
            this.desc = desc;
            this.control = control;
            this.isDefault = isDefault;
            this.reset = reset;
            this.h = desc == null || desc.isEmpty() ? 28 : 40;
            add(control);
        }

        public boolean changed() { return isDefault != null && !isDefault.get(); }

        public Runnable reset() { return reset; }

        public void layout() {
            if (control.w > w / 2) control.w = Math.max(60, w / 2);
            int cw = control.w;
            control.x = x + w - cw - 10;
            control.y = y + (h - control.h) / 2;
            if (side != null) {
                side.x = control.x - side.w - 4;
                side.y = y + (h - side.h) / 2;
            }
        }

        @Override
        public void render(Gfx g, int mx, int my, float dt) {
            layout();
            super.render(g, mx, my, dt);
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            if (hover > 0.01f) g.fill(x, y, x + w, y + h, Gfx.alpha(0xFFFFFFFF, 0.07f * hover));
            g.fill(x + 10, y + h - 1, x + w - 10, y + h, 0x1AFFFFFF);
            int tx = x + 10;
            if (icon != null) {
                g.slot(x + 8, y + (h - 18) / 2);
                g.item(icon, x + 9, y + (h - 16) / 2);
                tx += 22;
            }
            int labelW = (side != null ? side.x : control.x) - tx - 8;
            int nameColour = changed() ? CHANGED : TEXT;
            if (desc == null || desc.isEmpty()) {
                g.textClipped(name, tx, y + (h - 8) / 2, labelW, nameColour);
            } else {
                g.textClipped(name, tx, y + 10, labelW, nameColour);
                g.textClipped(desc, tx, y + 22, labelW, DESC);
            }
            super.draw(g, mx, my, dt);
        }

        @Override
        public Widget hovered(double mx, double my) {
            Widget h = super.hovered(mx, my);
            return h == this || h == control ? this : h;
        }
    }
}
