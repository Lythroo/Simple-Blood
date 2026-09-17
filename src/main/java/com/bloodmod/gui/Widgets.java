package com.bloodmod.gui;

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

    public static class Heading extends Widget {
        public final String text;

        public Heading(String text) { this.text = text; this.h = 22; }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            g.text(text, x, y + 5, TEXT);
            int tw = g.textWidth(text);
            g.fill(x + tw + 8, y + 9, x + w, y + 10, 0x40FFFFFF);
            g.fill(x, y + 15, x + tw, y + 16, ACCENT);
        }
    }

    public static class Button extends Widget {
        public String text;
        public Runnable onClick;
        public boolean accent;

        public Button(String text, Runnable onClick) {
            this.text = text;
            this.onClick = onClick;
            this.h = 20;
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            boolean over = enabled && contains(mx, my);
            int lift = over ? -1 : 0;
            g.button(x, y + lift, w, h, enabled, over);
            if (accent) g.fill(x + 2, y + lift + h - 3, x + w - 2, y + lift + h - 2, Gfx.alpha(ACCENT, 0.6f + 0.4f * hover));
            int colour = enabled ? TEXT : TEXT_DIM;
            g.textClipped(text, x + (w - Math.min(w - 6, g.textWidth(text))) / 2, y + lift + (h - 8) / 2, w - 6, colour);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || button != Keys.MOUSE_LEFT || !contains(mx, my)) return false;
            click();
            if (onClick != null) onClick.run();
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
            this.w = 60;
            this.h = 20;
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            boolean on = get.get();
            if (knob < 0) knob = on ? 1 : 0;
            knob = Anim.approach(knob, on ? 1f : 0f, dt * 18f);
            boolean over = enabled && contains(mx, my);
            g.button(x, y, w, h, enabled, over);
            int slotX = x + 5, slotY = y + 6, slotW = 20, slotH = 8;
            g.fill(slotX, slotY, slotX + slotW, slotY + slotH, 0xFF1C1C1C);
            g.fill(slotX, slotY, slotX + slotW, slotY + 1, 0xFF0A0A0A);
            int fillW = Math.round(slotW * knob);
            g.fill(slotX, slotY + 1, slotX + fillW, slotY + slotH, Gfx.mix(0xFF4A2020, ACCENT, knob));
            int kx = slotX + Math.round((slotW - 8) * knob);
            g.fill(kx, slotY - 1, kx + 8, slotY + slotH + 1, on ? 0xFFF0E0DE : 0xFFB0B0B0);
            g.fill(kx, slotY - 1, kx + 8, slotY, 0xFFFFFFFF);
            g.fill(kx, slotY + slotH, kx + 8, slotY + slotH + 1, 0xFF808080);
            String label = on ? "ON" : "OFF";
            g.text(label, x + 33, y + 6, enabled ? (on ? TEXT : TEXT_DIM) : TEXT_DARK);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || button != Keys.MOUSE_LEFT || !contains(mx, my)) return false;
            click();
            set.accept(!get.get());
            return true;
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
            g.checkbox(x, y, 17, get.get(), enabled && contains(mx, my));
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
        private boolean dragging;

        public Slider(int min, int max, int step, IntSupplier get, IntConsumer set) {
            this.min = min;
            this.max = max;
            this.step = Math.max(1, step);
            this.get = get;
            this.set = set;
            this.w = 100;
            this.h = 20;
        }

        public Slider unit(String unit) {
            this.format = v -> v + unit;
            return this;
        }

        private float frac(int v) { return (v - min) / (float) Math.max(1, max - min); }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            int v = get.getAsInt();
            float target = frac(v);
            if (Float.isNaN(shown) || dragging) shown = target;
            else shown = Anim.approach(shown, target, dt * 16f);
            boolean over = enabled && contains(mx, my);
            g.sliderTrack(x, y, w, h, over || dragging);
            int handleX = x + Math.round((w - 8) * shown);
            g.fill(x + 1, y + h - 4, handleX + 4, y + h - 2, Gfx.alpha(ACCENT, 0.35f + 0.25f * hover));
            g.sliderHandle(handleX, y, 8, h, over || dragging);
            String s = format.apply(v);
            g.textCentered(s, x + w / 2, y + (h - 8) / 2, enabled ? TEXT : TEXT_DIM);
        }

        private void setFromMouse(double mx) {
            double f = (mx - (x + 4)) / (double) (w - 8);
            f = Math.max(0, Math.min(1, f));
            int v = min + (int) Math.round(f * (max - min) / step) * step;
            v = Math.max(min, Math.min(max, v));
            if (v != get.getAsInt()) set.accept(v);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || button != Keys.MOUSE_LEFT || !contains(mx, my)) return false;
            dragging = true;
            setFromMouse(mx);
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

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            if (!enabled || !contains(mx, my)) return false;
            int v = get.getAsInt() + (delta > 0 ? step : -step);
            set.accept(Math.max(min, Math.min(max, v)));
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
            boolean over = enabled && contains(mx, my);
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
        private float caretBlink;

        public TextField() {
            this.w = 120;
            this.h = 20;
        }

        public TextField set(String v) {
            value = v == null ? "" : v;
            cursor = value.length();
            return this;
        }

        @Override
        public boolean wantsFocus() { return true; }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            g.textField(x, y, w, h, focused || (enabled && contains(mx, my)));
            int tx = x + 4, ty = y + (h - 8) / 2;
            int inner = w - 8;
            if (value.isEmpty() && !focused) {
                g.textClipped(hint, tx, ty, inner, TEXT_DARK);
                return;
            }
            String shown = value;
            int start = 0;
            while (start < cursor && g.textWidth(shown.substring(start, cursor)) > inner - 2) start++;
            shown = shown.substring(start);
            String vis = g.font.plainSubstrByWidth(shown, inner);
            g.text(vis, tx, ty, enabled ? TEXT : TEXT_DIM);
            if (focused) {
                caretBlink += dt;
                if ((int) (caretBlink * 2.5f) % 2 == 0) {
                    int cx = tx + g.textWidth(value.substring(start, Math.max(start, cursor)));
                    g.fill(cx, ty - 1, cx + 1, ty + 9, TEXT);
                }
            }
        }

        private void changed() {
            if (onChange != null) onChange.accept(value);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!enabled || !contains(mx, my)) return false;
            if (button == Keys.MOUSE_RIGHT) { value = ""; cursor = 0; changed(); }
            cursor = value.length();
            return true;
        }

        @Override
        public boolean keyPressed(int key, int modifiers) {
            if (!focused) return false;
            if ((modifiers & Keys.PASTE) != 0) {
                insert(Minecraft.getInstance().keyboardHandler.getClipboard());
                return true;
            }
            switch (key) {
                case Keys.BACKSPACE -> {
                    if (cursor > 0) {
                        value = value.substring(0, cursor - 1) + value.substring(cursor);
                        cursor--;
                        changed();
                    }
                    return true;
                }
                case Keys.DELETE -> {
                    if (cursor < value.length()) {
                        value = value.substring(0, cursor) + value.substring(cursor + 1);
                        changed();
                    }
                    return true;
                }
                case Keys.LEFT -> { cursor = Math.max(0, cursor - 1); return true; }
                case Keys.RIGHT -> { cursor = Math.min(value.length(), cursor + 1); return true; }
                case Keys.HOME -> { cursor = 0; return true; }
                case Keys.END -> { cursor = value.length(); return true; }
                case Keys.ENTER, Keys.NUMPAD_ENTER -> { if (onEnter != null) onEnter.run(); return true; }
                default -> { return false; }
            }
        }

        private void insert(String s) {
            if (s == null || s.isEmpty()) return;
            StringBuilder sb = new StringBuilder();
            for (char c : s.toCharArray()) if (c >= 32 && c != 127) sb.append(c);
            String ins = sb.toString();
            if (value.length() + ins.length() > maxLength) ins = ins.substring(0, Math.max(0, maxLength - value.length()));
            value = value.substring(0, cursor) + ins + value.substring(cursor);
            cursor += ins.length();
            changed();
        }

        @Override
        public boolean charTyped(char c) {
            if (!focused || c < 32 || c == 127) return false;
            insert(String.valueOf(c));
            return true;
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
            boolean over = enabled && contains(mx, my);
            g.button(x, y, w, h, enabled, over);
            int rgb = get.getAsInt() & 0xFFFFFF;
            g.fill(x + 4, y + 4, x + 20, y + h - 4, 0xFF000000);
            g.fill(x + 5, y + 5, x + 19, y + h - 5, 0xFF000000 | rgb);
            g.fill(x + 5, y + 5, x + 19, y + 6, Gfx.alpha(0xFFFFFFFF, 0.25f));
            String hex = String.format("#%06X", rgb);
            if (g.textWidth(hex) <= w - 29) g.text(hex, x + 25, y + (h - 8) / 2, enabled ? TEXT : TEXT_DIM);
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
        private float resetShow;
        public boolean even;
        public com.bloodmod.surface.PreviewScene.Kind previewKind;
        public IntSupplier previewColour;
        public String previewText;

        @Override public com.bloodmod.surface.PreviewScene.Kind previewKind() { return previewKind; }
        @Override public IntSupplier previewColour() { return previewColour; }
        @Override public String previewText() { return previewText; }
        @Override public String previewName() { return name; }
        @Override public boolean previewActive() { return control instanceof Slider s && s.dragging; }
        public java.util.EnumSet<com.bloodmod.surface.PreviewScene.Aspect> previewAspects = java.util.EnumSet.allOf(com.bloodmod.surface.PreviewScene.Aspect.class);
        @Override public java.util.EnumSet<com.bloodmod.surface.PreviewScene.Aspect> previewAspects() { return previewAspects; }

        public Row(String name, String desc, Widget control, Supplier<Boolean> isDefault, Runnable reset) {
            this.name = name;
            this.desc = desc;
            this.control = control;
            this.isDefault = isDefault;
            this.reset = reset;
            this.h = desc == null || desc.isEmpty() ? 28 : 36;
            add(control);
            if (desc != null && !desc.isEmpty()) tooltip = List.of(desc);
        }

        public void layout() {
            int cw = control.w;
            control.x = x + w - cw - 26;
            control.y = y + (h - control.h) / 2;
        }

        @Override
        public void render(Gfx g, int mx, int my, float dt) {
            layout();
            super.render(g, mx, my, dt);
        }

        @Override
        protected void draw(Gfx g, int mx, int my, float dt) {
            boolean over = contains(mx, my);
            int bg = even ? 0x12FFFFFF : 0x00000000;
            g.fill(x, y, x + w, y + h, bg);
            if (hover > 0.01f) g.fill(x, y, x + w, y + h, Gfx.alpha(0xFFFFFFFF, 0.06f * hover));
            if (hover > 0.01f) g.fill(x, y + 1, x + 1, y + h - 1, Gfx.alpha(ACCENT, hover));
            int labelW = control.x - x - 18;
            if (desc == null || desc.isEmpty()) {
                g.textClipped(name, x + 10, y + (h - 8) / 2, labelW, TEXT);
            } else {
                g.textClipped(name, x + 10, y + 8, labelW, TEXT);
                g.textClipped(desc, x + 10, y + 20, labelW, TEXT_DIM);
            }
            super.draw(g, mx, my, dt);
            boolean showReset = isDefault != null && !isDefault.get();
            resetShow = Anim.approach(resetShow, showReset ? 1f : 0f, dt * 12f);
            if (resetShow > 0.02f) {
                int rx = x + w - 20, ry = y + (h - 12) / 2;
                boolean overReset = over && mx >= rx && mx < rx + 14 && my >= ry && my < ry + 12;
                int c = Gfx.alpha(overReset ? TEXT : TEXT_DIM, resetShow);
                g.fill(rx + 3, ry + 3, rx + 10, ry + 4, c);
                g.fill(rx + 9, ry + 4, rx + 11, ry + 8, c);
                g.fill(rx + 4, ry + 8, rx + 11, ry + 9, c);
                g.fill(rx + 2, ry + 4, rx + 3, ry + 5, c);
                g.fill(rx + 1, ry + 5, rx + 2, ry + 6, c);
                g.fill(rx + 2, ry + 6, rx + 3, ry + 7, c);
                g.fill(rx + 3, ry + 2, rx + 4, ry + 3, c);
                g.fill(rx + 3, ry + 7, rx + 4, ry + 8, c);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!contains(mx, my)) return false;
            if (super.mouseClicked(mx, my, button)) return true;
            int rx = x + w - 20, ry = y + (h - 12) / 2;
            if (reset != null && resetShow > 0.5f && mx >= rx && mx < rx + 14 && my >= ry && my < ry + 12 && button == Keys.MOUSE_LEFT) {
                click();
                reset.run();
                return true;
            }
            return false;
        }

        @Override
        public Widget hovered(double mx, double my) {
            Widget h = super.hovered(mx, my);
            return h == this || h == control ? this : h;
        }
    }
}
