package io.github.metrovoc.residual.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The look shared by the mod's screens: bevelled grey panels, as in vanilla and AE2, holding dark displays. */
public final class Ui {
    public static final int PANEL = 0xFFC6C6C6;
    public static final int HIGHLIGHT = 0xFFFFFFFF;
    public static final int SHADOW = 0xFF555555;
    public static final int OUTLINE = 0xFF000000;
    public static final int LABEL = 0xFF404040;
    public static final int LABEL_DIM = 0xFF6E6E6E;

    public static final int SCREEN = 0xFF11161B;
    public static final int TEXT = 0xFFE4EBF1;
    public static final int TEAL = 0xFF3CD6C6;
    public static final int AMBER = 0xFFFFB547;

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

    /** {@code text} cut to {@code width} pixels, ending in an ellipsis when something had to go. */
    public static String fit(Font font, String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("…")) + "…";
    }
}
