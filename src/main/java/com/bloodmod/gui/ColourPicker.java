package com.bloodmod.gui;

import java.util.function.IntConsumer;

public final class ColourPicker extends Widget.Container {

    private static final int CELLS = 16;
    private static final int CELL = 6;
    private static final int GRID = CELLS * CELL;

    private static final int[] PRESETS = {
            0x7A0F0F, 0x660303, 0x4A1414, 0x8A1A1A, 0xC8281E, 0x3B7A2A, 0x7FCF4E, 0x5E1C82,
            0x0F4B58, 0xF5A623, 0xD3540A, 0xC9C2A8, 0x8C8C8C, 0xEDF4FA, 0x101828, 0x2FAF9E,
    };

    private final int original;
    private final IntConsumer apply;
    private final Runnable close;
    private float hue, sat, val;
    private final Widgets.TextField hex = new Widgets.TextField();
    private int gridX, gridY, hueX, hueY;
    private boolean dragGrid, dragHue;
    private float open;

    public ColourPicker(int rgb, IntConsumer apply, Runnable close) {
        this.original = rgb & 0xFFFFFF;
        this.apply = apply;
        this.close = close;
        float[] hsv = toHsv(rgb);
        hue = hsv[0]; sat = hsv[1]; val = hsv[2];
        w = 224;
        h = 166;
        hex.maxLength = 7;
        hex.set(String.format("#%06X", original));
        hex.onChange = s -> {
            String t = s.startsWith("#") ? s.substring(1) : s;
            if (t.length() == 6) {
                try {
                    int v = Integer.parseInt(t, 16);
                    float[] c = toHsv(v);
                    hue = c[0]; sat = c[1]; val = c[2];
                    apply.accept(v);
                } catch (NumberFormatException ignored) {}
            }
        };
        add(hex);
        add(new Widgets.Button("Cancel", () -> { apply.accept(original); close.run(); }));
        Widgets.Button ok = add(new Widgets.Button("Done", close));
        ok.accent = true;
    }

    public void layout(int screenW, int screenH) {
        x = (screenW - w) / 2;
        y = (screenH - h) / 2;
        gridX = x + 10;
        gridY = y + 22;
        hueX = gridX + GRID + 8;
        hueY = gridY;
        hex.x = hueX + 22; hex.y = gridY; hex.w = 80; hex.h = 18;
        Widgets.Button cancel = (Widgets.Button) children.get(1), ok = (Widgets.Button) children.get(2);
        cancel.x = hueX + 22; cancel.y = y + h - 26; cancel.w = 38; cancel.h = 18;
        ok.x = cancel.x + 42; ok.y = cancel.y; ok.w = 38; ok.h = 18;
    }

    private int rgb() { return fromHsv(hue, sat, val); }

    private void push() {
        int v = rgb();
        hex.set(String.format("#%06X", v));
        apply.accept(v);
    }

    @Override
    public void render(Gfx g, int mx, int my, float dt) {
        open = Anim.approach(open, 1f, dt * 14f);
        super.render(g, mx, my, dt);
    }

    @Override
    protected void draw(Gfx g, int mx, int my, float dt) {
        float a = Anim.easeOut(open);
        g.fill(0, 0, g.width(), g.height(), Gfx.alpha(0xFF000000, 0.45f * a));
        g.fill(x, y, x + w, y + h, Gfx.alpha(0xFF101010, 0.96f * a));
        g.bevel(x, y, x + w, y + h, Gfx.alpha(0xFFFFFFFF, 0.25f * a), Gfx.alpha(0xFF000000, 0.8f * a));
        g.text("Pick a colour", x + 10, y + 8, Gfx.alpha(Widgets.TEXT, a));
        for (int j = 0; j < CELLS; j++) {
            for (int i = 0; i < CELLS; i++) {
                float s = (i + 0.5f) / CELLS, v = 1f - (j + 0.5f) / CELLS;
                int c = fromHsv(hue, s, v);
                g.fill(gridX + i * CELL, gridY + j * CELL, gridX + (i + 1) * CELL, gridY + (j + 1) * CELL, Gfx.alpha(0xFF000000 | c, a));
            }
        }
        int si = Math.round(sat * (CELLS - 1)), vi = Math.round((1f - val) * (CELLS - 1));
        g.outline(gridX + si * CELL - 1, gridY + vi * CELL - 1, gridX + (si + 1) * CELL + 1, gridY + (vi + 1) * CELL + 1, Gfx.alpha(0xFFFFFFFF, a));
        g.outline(gridX + si * CELL - 2, gridY + vi * CELL - 2, gridX + (si + 1) * CELL + 2, gridY + (vi + 1) * CELL + 2, Gfx.alpha(0xFF000000, a));
        for (int j = 0; j < CELLS; j++) {
            int c = fromHsv((j + 0.5f) / CELLS, 1f, 1f);
            g.fill(hueX, hueY + j * CELL, hueX + 12, hueY + (j + 1) * CELL, Gfx.alpha(0xFF000000 | c, a));
        }
        int hi = Math.min(CELLS - 1, (int) (hue * CELLS));
        g.outline(hueX - 1, hueY + hi * CELL - 1, hueX + 13, hueY + (hi + 1) * CELL + 1, Gfx.alpha(0xFFFFFFFF, a));
        int cur = rgb();
        int px = hueX + 22, py = gridY + 24;
        g.fill(px, py, px + 80, py + 22, Gfx.alpha(0xFF000000, a));
        g.fill(px + 1, py + 1, px + 79, py + 21, Gfx.alpha(0xFF000000 | cur, a));
        g.fill(px + 1, py + 1, px + 79, py + 2, Gfx.alpha(0xFFFFFFFF, 0.2f * a));
        g.text("Quick picks", px, py + 28, Gfx.alpha(Widgets.TEXT_DIM, a));
        for (int i = 0; i < PRESETS.length; i++) {
            int sx = px + (i % 8) * 10, sy = py + 38 + (i / 8) * 10;
            boolean over = mx >= sx && mx < sx + 9 && my >= sy && my < sy + 9;
            g.fill(sx, sy, sx + 9, sy + 9, Gfx.alpha(over ? 0xFFFFFFFF : 0xFF000000, a));
            g.fill(sx + 1, sy + 1, sx + 8, sy + 8, Gfx.alpha(0xFF000000 | PRESETS[i], a));
        }
        super.draw(g, mx, my, dt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != Keys.MOUSE_LEFT) return contains(mx, my);
        if (mx >= gridX && mx < gridX + GRID && my >= gridY && my < gridY + GRID) {
            dragGrid = true;
            pickGrid(mx, my);
            return true;
        }
        if (mx >= hueX && mx < hueX + 12 && my >= hueY && my < hueY + GRID) {
            dragHue = true;
            pickHue(my);
            return true;
        }
        int px = hueX + 22, py = gridY + 24;
        for (int i = 0; i < PRESETS.length; i++) {
            int sx = px + (i % 8) * 10, sy = py + 38 + (i / 8) * 10;
            if (mx >= sx && mx < sx + 9 && my >= sy && my < sy + 9) {
                float[] c = toHsv(PRESETS[i]);
                hue = c[0]; sat = c[1]; val = c[2];
                Widgets.click();
                push();
                return true;
            }
        }
        return contains(mx, my);
    }

    private void pickGrid(double mx, double my) {
        int i = (int) Math.max(0, Math.min(CELLS - 1, (mx - gridX) / CELL));
        int j = (int) Math.max(0, Math.min(CELLS - 1, (my - gridY) / CELL));
        sat = i / (float) (CELLS - 1);
        val = 1f - j / (float) (CELLS - 1);
        push();
    }

    private void pickHue(double my) {
        int j = (int) Math.max(0, Math.min(CELLS - 1, (my - hueY) / CELL));
        hue = (j + 0.5f) / CELLS;
        push();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragGrid) { pickGrid(mx, my); return true; }
        if (dragHue) { pickHue(my); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragGrid = dragHue = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int modifiers) {
        if (key == Keys.ESCAPE) {
            apply.accept(original);
            close.run();
            return true;
        }
        return super.keyPressed(key, modifiers);
    }

    @Override
    public boolean contains(double mx, double my) {
        return true;
    }

    static float[] toHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0;
        if (d > 1e-5f) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h /= 6f;
            if (h < 0) h += 1f;
        }
        float s = max <= 1e-5f ? 0 : d / max;
        return new float[]{h, s, max};
    }

    static int fromHsv(float h, float s, float v) {
        h = (h % 1f + 1f) % 1f;
        float c = v * s;
        float hh = h * 6f;
        float x = c * (1f - Math.abs(hh % 2f - 1f));
        float r, g, b;
        if (hh < 1) { r = c; g = x; b = 0; }
        else if (hh < 2) { r = x; g = c; b = 0; }
        else if (hh < 3) { r = 0; g = c; b = x; }
        else if (hh < 4) { r = 0; g = x; b = c; }
        else if (hh < 5) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        float m = v - c;
        int ri = Math.round((r + m) * 255), gi = Math.round((g + m) * 255), bi = Math.round((b + m) * 255);
        return (ri << 16) | (gi << 8) | bi;
    }
}
