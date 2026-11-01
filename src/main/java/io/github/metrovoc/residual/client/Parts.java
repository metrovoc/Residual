package io.github.metrovoc.residual.client;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * What the parts of a screen are called: a rehearsal script clicks a tab or a grid's cell by name instead of by where it
 * happens to be drawn, and the field notes' pictures of the screen point at the same names. Widgets and slots are named by
 * the harness from what they already say; a screen adds what it only draws.
 */
public final class Parts {
    /** A named rectangle in the screen's coordinates; {@code on} when it is the one chosen, such as the open tab. */
    public record Part(String name, int x, int y, int w, int h, boolean on, boolean disabled) {
    }

    /** A screen, or something on a guide page, that draws parts of its own. */
    public interface Drawn {
        void parts(Parts out);
    }

    /** A widget whose label says too little or changes, with a name of its own. */
    public interface Called {
        String partName();
    }

    private final Map<String, Part> parts = new LinkedHashMap<>();

    public void add(String name, int x, int y, int w, int h) {
        add(name, x, y, w, h, false);
    }

    public void add(String name, int x, int y, int w, int h, boolean on) {
        parts.put(name, new Part(name, x, y, w, h, on, false));
    }

    /** Another name for a widget. */
    public void add(String name, AbstractWidget widget) {
        if (widget != null && widget.visible) {
            parts.put(name, new Part(name, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(), false, !widget.active));
        }
    }

    /** The frame of a slot on the screen, under a name that says what it is for. */
    public void add(String name, AbstractContainerScreen<?> screen, Slot slot) {
        if (slot.isActive()) {
            add(name, screen.getLeftPos() + slot.x - 1, screen.getTopPos() + slot.y - 1, 18, 18);
        }
    }

    /** All the slots from {@code from} up to {@code to} of the menu as one part, for what they are together. */
    public void group(String name, AbstractContainerScreen<?> screen, int from, int to) {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (int i = from; i < to; i++) {
            Slot slot = screen.getMenu().getSlot(i);
            x0 = Math.min(x0, slot.x - 1);
            y0 = Math.min(y0, slot.y - 1);
            x1 = Math.max(x1, slot.x + 17);
            y1 = Math.max(y1, slot.y + 17);
        }
        add(name, screen.getLeftPos() + x0, screen.getTopPos() + y0, x1 - x0, y1 - y0);
    }

    public Map<String, Part> all() {
        return parts;
    }
}
