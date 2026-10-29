package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.view.Heatmap;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The look shared by the mod's screens: bevelled grey panels, as in vanilla and AE2, holding dark instrument
 * displays where the model's numbers live. Teal marks outputs and the primary action, amber inputs and validation.
 */
public final class Ui {
    public static final int PANEL = 0xFFC6C6C6;
    public static final int HIGHLIGHT = 0xFFFFFFFF;
    public static final int SHADOW = 0xFF555555;
    public static final int OUTLINE = 0xFF000000;
    public static final int LABEL = 0xFF404040;
    public static final int LABEL_DIM = 0xFF6E6E6E;

    public static final int SCREEN = 0xFF11161B;
    public static final int GRID = 0xFF1D252D;
    public static final int TEXT = 0xFFE4EBF1;
    public static final int DIM = 0xFF7F8D99;
    public static final int TEAL = 0xFF3CD6C6;
    public static final int AMBER = 0xFFFFB547;
    public static final int RED = 0xFFFF6B6B;
    public static final int GREEN = 0xFF84E08A;
    public static final int SELECTION = 0xFF263440;

    private Ui() {
    }

    /** A raised window, vanilla style: black outline with cut corners, light top-left, dark bottom-right. */
    public static void window(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, OUTLINE);
        g.fill(x, y + 1, x + w, y + h - 1, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, HIGHLIGHT);
        g.fill(x + 3, y + 3, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, PANEL);
        g.fill(x + w - 3, y + 1, x + w - 1, y + 3, PANEL);
        g.fill(x + 1, y + h - 3, x + 3, y + h - 1, PANEL);
    }

    /** A sunken dark display. */
    public static void screen(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF373737);
        g.fill(x, y, x + w + 1, y + h + 1, HIGHLIGHT);
        g.fill(x, y, x + w, y + h, SCREEN);
    }

    /** The sunken frame of an item slot whose item sits at {@code (x, y)}. */
    public static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
        g.fill(x, y, x + 18, y + 18, HIGHLIGHT);
        g.fill(x, y, x + 17, y + 17, 0xFF8B8B8B);
        g.fill(x - 1, y + 17, x, y + 18, 0xFF8B8B8B);
        g.fill(x + 17, y - 1, x + 18, y, 0xFF8B8B8B);
    }

    /** A thin separator line on a panel. */
    public static void rule(GuiGraphicsExtractor g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, 0xFF8B8B8B);
        g.fill(x, y + 1, x + w, y + 2, HIGHLIGHT);
    }

    /** A horizontal bar filled to {@code fraction}. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float fraction, int color) {
        g.fill(x, y, x + w, y + h, GRID);
        int filled = Math.round(w * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, color);
        }
    }

    /** Diverging color for a value in [-1, 1]: cyan below zero, near-black at zero, amber above. */
    public static int heat(float v) {
        float t = Math.max(-1f, Math.min(1f, v));
        int base = 0x15, r, gr, b;
        if (t >= 0) {
            r = lerp(base, 0xFF, t);
            gr = lerp(base + 2, 0xA8, t);
            b = lerp(base + 6, 0x3A, t);
        } else {
            float s = -t;
            r = lerp(base, 0x3E, s);
            gr = lerp(base + 2, 0xB8, s);
            b = lerp(base + 6, 0xFF, s);
        }
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    private static int lerp(int a, int b, float t) {
        // a gentle curve keeps small values visible
        float k = (float) Math.sqrt(t);
        return Math.round(a + (b - a) * k);
    }

    /** Draws a heatmap stretched over the box, keeping its cells square-ish. */
    public static void heatmap(GuiGraphicsExtractor g, Heatmap map, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SCREEN);
        if (map == null || map.rows() == 0 || map.cols() == 0) {
            return;
        }
        for (int r = 0; r < map.rows(); r++) {
            int y0 = y + r * h / map.rows();
            int y1 = y + (r + 1) * h / map.rows();
            for (int c = 0; c < map.cols(); c++) {
                int x0 = x + c * w / map.cols();
                int x1 = x + (c + 1) * w / map.cols();
                g.fill(x0, y0, x1, y1, heat(map.value(r, c)));
            }
        }
    }

    /** A compact human number: 0.0123, 1.23, 12.3k. */
    public static String num(double v) {
        if (Double.isNaN(v)) {
            return "–";
        }
        if (Double.isInfinite(v)) {
            return v > 0 ? "∞" : "-∞";
        }
        double a = Math.abs(v);
        if (a >= 1e6) {
            return String.format(Locale.ROOT, "%.2fM", v / 1e6);
        }
        if (a >= 1e4) {
            return String.format(Locale.ROOT, "%.1fk", v / 1e3);
        }
        if (a >= 100) {
            return String.format(Locale.ROOT, "%.0f", v);
        }
        if (a >= 1) {
            return String.format(Locale.ROOT, "%.3f", v);
        }
        if (a >= 1e-3 || a == 0) {
            return String.format(Locale.ROOT, "%.4f", v);
        }
        return String.format(Locale.ROOT, "%.1e", v);
    }

    /** A count with thousands separators. */
    public static String count(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    /** {@code text} cut to {@code width} pixels, ending in an ellipsis when something had to go. */
    public static String fit(Font font, String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
