package io.github.metrovoc.residual.rehearsal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import guideme.document.LytPoint;
import guideme.document.LytRect;
import guideme.document.block.LytDocument;
import guideme.document.block.LytNode;
import guideme.internal.screen.DocumentScreen;
import guideme.internal.screen.IndepentScaleScreen;
import guideme.ui.GuideUiHost;
import io.github.metrovoc.residual.client.Parts;
import io.github.metrovoc.residual.client.Parts.Part;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The named parts of the open screen, for a script to click and a picture of the screen to mark. A widget goes by the
 * translation key of its label, less {@code residual.}, or by the words of a label that is only words; a slot by its
 * number in the menu ({@code slot:3}), its place in the player's inventory ({@code inv:0}, {@code hotbar:0}) and what it
 * holds ({@code has:minecraft:coal}, the first one holding it); and a screen adds what it only draws (client/Parts).
 * A name taken already gets {@code #2} and so on.
 */
final class Named {
    private static final Pattern OFFSET = Pattern.compile("(.+)\\+(-?\\d+)\\+(-?\\d+)");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Named() {
    }

    static Map<String, Part> of(Screen screen) {
        Parts out = new Parts();
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible) {
                String name = unique(out, name(widget));
                out.add(name, widget);
            }
        }
        if (screen instanceof AbstractContainerScreen<?> container) {
            slots(container, out);
        }
        if (screen instanceof Parts.Drawn drawn) {
            drawn.parts(out);
        }
        if (screen instanceof DocumentScreen document) {
            guide(document, out);
        }
        return out.all();
    }

    private static String name(AbstractWidget widget) {
        if (widget instanceof Parts.Called called) {
            return called.partName();
        }
        Component message = widget.getMessage();
        if (message.getContents() instanceof TranslatableContents t) {
            String key = t.getKey();
            // a Cycler's or a setting's label is "name: value"
            if (key.equals("residual.cycler") && t.getArgs().length > 0 && t.getArgs()[0] instanceof Component label
                    && label.getContents() instanceof TranslatableContents named) {
                key = named.getKey();
            }
            return key.startsWith("residual.") ? key.substring("residual.".length()) : key;
        }
        String words = message.getString().strip().replace(' ', '_').toLowerCase(Locale.ROOT);
        return words.isEmpty() ? "widget" : words;
    }

    private static String unique(Parts out, String name) {
        String free = name;
        for (int n = 2; out.all().containsKey(free); n++) {
            free = name + "#" + n;
        }
        return free;
    }

    private static void slots(AbstractContainerScreen<?> screen, Parts out) {
        for (Slot slot : screen.getMenu().slots) {
            out.add("slot:" + slot.index, screen, slot);
            if (slot.container instanceof Inventory) {
                int at = slot.getContainerSlot();
                if (at < 9) {
                    out.add("hotbar:" + at, screen, slot);
                } else if (at < 36) {
                    out.add("inv:" + (at - 9), screen, slot);
                }
            }
            if (slot.isActive() && slot.hasItem()) {
                String has = "has:" + BuiltInRegistries.ITEM.getKey(slot.getItem().getItem());
                if (!out.all().containsKey(has)) {
                    out.add(has, screen, slot);
                }
            }
        }
    }

    /** What the things on a guide page draw, from the page's coordinates to the screen's. */
    private static void guide(DocumentScreen screen, Parts out) {
        LytDocument document = document(screen);
        if (document != null) {
            guide(document, (GuideUiHost) screen, out);
        }
    }

    private static void guide(LytNode node, GuideUiHost host, Parts out) {
        if (node instanceof Parts.Drawn drawn) {
            Parts inner = new Parts();
            drawn.parts(inner);
            for (Part p : inner.all().values()) {
                LytPoint at = host.getScreenPoint(new LytPoint(p.x(), p.y()));
                out.add(unique(out, p.name()), (int) at.x(), (int) at.y(), p.w(), p.h(), p.on());
            }
        }
        for (LytNode child : node.getChildren()) {
            guide(child, host, out);
        }
    }

    private static LytDocument document(DocumentScreen screen) {
        try {
            Method method = DocumentScreen.class.getDeclaredMethod("getDocument");
            method.setAccessible(true);
            return (LytDocument) method.invoke(screen);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The part a name means, or {@code NAME+DX+DY}, the point that far from its corner, as a part of no size. */
    static Part find(Minecraft mc, String name) {
        Matcher offset = OFFSET.matcher(name);
        if (offset.matches()) {
            Part part = part(mc, offset.group(1));
            return new Part(name, part.x() + Integer.parseInt(offset.group(2)), part.y() + Integer.parseInt(offset.group(3)), 0, 0, part.on(), part.disabled());
        }
        return part(mc, name);
    }

    private static Part part(Minecraft mc, String name) {
        if (mc.screen == null) {
            throw new IllegalStateException("no screen is open, so no part " + name);
        }
        Part part = of(mc.screen).get(name);
        if (part == null) {
            throw new IllegalStateException("no part " + name + " on " + mc.screen.getClass().getSimpleName() + "; it has " + of(mc.screen).keySet());
        }
        if (mc.screen instanceof DocumentScreen document) {
            return reveal(mc, document, part, name);
        }
        return part;
    }

    /** A part of a guide page may lie below what the page shows: scrolls until it is in view. */
    private static Part reveal(Minecraft mc, DocumentScreen screen, Part part, String name) {
        LytRect shown = ((GuideUiHost) screen).getDocumentRect();
        for (int tries = 0; tries < 1000; tries++) {
            int below = part.y() + part.h() - shown.bottom(), above = shown.y() - part.y();
            if (below <= 0 && above <= 0) {
                break;
            }
            int before = part.y();
            // the page's scrollbar moves ten pixels for one notch
            screen.mouseScrolled(shown.x() + 1, shown.y() + 1, 0, below > 0 ? -Math.ceil(below / 10.0) : Math.ceil(above / 10.0));
            part = of(screen).get(name);
            if (part.y() == before) {
                break;
            }
        }
        return part;
    }

    /** The smallest part under a point, the one named last among equals, for the log: what a click by place clicked. */
    static Part at(Screen screen, double x, double y) {
        Part best = null;
        for (Part p : of(screen).values()) {
            if (x >= p.x() && y >= p.y() && x < p.x() + p.w() && y < p.y() + p.h()
                    && (best == null || p.w() * p.h() <= best.w() * best.h())) {
                best = p;
            }
        }
        return best;
    }

    /** Writes beside a shot of the open screen where its parts are, for the field notes' marks ({@code tools/screens.py}). */
    static void write(Minecraft mc, Path file) throws IOException {
        Screen screen = mc.screen;
        JsonObject out = new JsonObject();
        out.addProperty("screen", screen.getClass().getSimpleName());
        out.addProperty("width", screen.width);
        out.addProperty("height", screen.height);
        // how many of the shot's pixels make one of the screen's: on a guide page more than the GUI scale says, at some scales
        out.addProperty("scale", mc.getWindow().getGuiScale() * (screen instanceof IndepentScaleScreen scaled ? scaled.getEffectiveScale() : 1));
        if (screen instanceof AbstractContainerScreen<?> container) {
            out.add("window", rect(container.getLeftPos(), container.getTopPos(), container.getXSize(), container.getYSize()));
        }
        JsonObject parts = new JsonObject();
        for (Part p : of(screen).values()) {
            parts.add(p.name(), rect(p.x(), p.y(), p.w(), p.h()));
        }
        out.add("parts", parts);
        Files.writeString(file, GSON.toJson(out));
    }

    private static JsonArray rect(int x, int y, int w, int h) {
        JsonArray rect = new JsonArray();
        rect.add(x);
        rect.add(y);
        rect.add(w);
        rect.add(h);
        return rect;
    }
}
