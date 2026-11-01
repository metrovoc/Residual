package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.agent.Pieces;
import io.github.metrovoc.residual.agent.Program;
import io.github.metrovoc.residual.agent.Things;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The pieces of programs a screen lays out beside a program, to click together instead of typing: pages of chips under
 * headings, one page in view, scrolled with the wheel. Its last page is what the player holds and the body carries, to name
 * things by a click.
 */
final class Palette {
    /** How wide it is, beside the window on its left. */
    static final int WIDTH = 150;
    private static final int TOP = 20, CHIP_GAP = 2, ROW = Chip.HEIGHT + CHIP_GAP;
    /** The page in view, and how far it is scrolled, while the game runs. */
    private static int page;
    private static int scroll;

    private final List<Chip> chips = new ArrayList<>();
    /** Where each heading stands, down from the panel's top before scrolling. */
    private final Map<Integer, Component> headings = new LinkedHashMap<>();
    private int px, py, height, contentHeight;

    private static int color(int kind) {
        return switch (kind) {
            case Pieces.BLOCK -> Ui.TEAL;
            case Pieces.CHECK -> Ui.AMBER;
            case Pieces.VALUE -> Ui.BLUE;
            default -> Ui.TEXT;
        };
    }

    private static int pages() {
        return Pieces.PAGES.size() + 1;
    }

    private static Component title(int i) {
        return Component.translatable(i < Pieces.PAGES.size() ? Pieces.PAGES.get(i).title() : "residual.palette.page.things");
    }

    /**
     * Lays out the page in view on a panel whose top left is {@code (x, y)}. {@code add} gives each chip to the screen,
     * {@code pick} what a chip says to the program, {@code things} what can be named.
     */
    void lay(int x, int y, int height, Consumer<AbstractWidget> add, BiConsumer<String, Program.Join> pick, Supplier<List<ItemStack>> things) {
        this.px = x;
        this.py = y;
        this.height = height;
        chips.clear();
        headings.clear();
        int top = TOP + 6;
        List<Pieces.Group> groups = page < Pieces.PAGES.size() ? Pieces.PAGES.get(page).groups() : List.of(thingsGroup(things.get()));
        for (Pieces.Group group : groups) {
            headings.put(top, Component.translatable(group.title()));
            top += 11;
            int cx = 6;
            for (Pieces.Piece piece : group.pieces()) {
                Chip chip = new Chip(x, y, piece, color(group.kind()), () -> pick.accept(piece.text(), piece.join()));
                if (cx > 6 && cx + chip.getWidth() > WIDTH - 6) {
                    cx = 6;
                    top += ROW;
                }
                chip.home(x + cx, y + top);
                chips.add(chip);
                add.accept(chip);
                cx += chip.getWidth() + CHIP_GAP;
            }
            top += ROW + 4;
        }
        contentHeight = top;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - (height - 8))));
        place();
    }

    private static Pieces.Group thingsGroup(List<ItemStack> stacks) {
        Map<String, Pieces.Piece> pieces = new LinkedHashMap<>();
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) {
                String name = Things.name(s);
                pieces.putIfAbsent(name, new Pieces.Piece(name, name, Program.Join.WORD, "residual.palette.thing"));
            }
        }
        return new Pieces.Group("residual.palette.group.things", Pieces.VALUE, List.copyOf(pieces.values()));
    }

    /** Puts each chip where the scroll has it, and hides what is out of view. */
    private void place() {
        for (Chip c : chips) {
            int y = c.homeY - scroll;
            c.setPosition(c.homeX, y);
            c.visible = y >= py + TOP + 6 && y + Chip.HEIGHT <= py + height - 4;
        }
    }

    /** Scrolls it, if the mouse is over it; whether it did. */
    boolean scrolled(double mouseX, double mouseY, double amount) {
        if (!Ui.inside(mouseX, mouseY, px, py, WIDTH, height)) {
            return false;
        }
        scroll = Math.max(0, Math.min(scroll - (int) (amount * ROW), Math.max(0, contentHeight - (height - 8))));
        place();
        return true;
    }

    /** Where a page's name sits, in rows of up to what fits the panel: its top left, as x and y. */
    private static int[] tabAt(Font font, int i) {
        int x = 6, y = 5;
        for (int k = 0; k <= i; k++) {
            int w = font.width(title(k)) + 4;
            if (x + w > WIDTH - 4) {
                x = 6;
                y += 11;
            }
            if (k == i) {
                return new int[]{x, y};
            }
            x += w + 1;
        }
        return new int[]{x, y};
    }

    /** A click on a page's name: true if it hit one, which then needs a new layout. */
    boolean clickedTab(Font font, double mouseX, double mouseY) {
        for (int i = 0; i < pages(); i++) {
            int[] at = tabAt(font, i);
            if (Ui.inside(mouseX, mouseY, px + at[0], py + at[1], font.width(title(i)) + 4, 11)) {
                if (i != page) {
                    page = i;
                    scroll = 0;
                }
                return true;
            }
        }
        return false;
    }

    /** {@code palette_panel}, and the names of its pages by number, {@code palette_page:0} on; its chips name themselves. */
    void parts(Parts out, Font font) {
        out.add("palette_panel", px, py, WIDTH, height);
        for (int i = 0; i < pages(); i++) {
            int[] at = tabAt(font, i);
            out.add("palette_page:" + i, px + at[0], py + at[1], font.width(title(i)) + 4, 11, i == page);
        }
    }

    void draw(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        Ui.window(g, px, py, WIDTH, height);
        for (int i = 0; i < pages(); i++) {
            Component t = title(i);
            int[] at = tabAt(font, i);
            int w = font.width(t) + 4;
            boolean hover = Ui.inside(mouseX, mouseY, px + at[0], py + at[1], w, 11);
            if (i == page) {
                g.fill(px + at[0], py + at[1] + 9, px + at[0] + w, py + at[1] + 10, 0xFF2A9D92);
            }
            g.text(font, t, px + at[0] + 2, py + at[1], i == page ? Ui.LABEL : hover ? 0xFF222222 : Ui.LABEL_DIM, false);
        }
        headings.forEach((top, title) -> {
            int y = py + top - scroll;
            if (y >= py + TOP + 6 && y + 9 <= py + height - 4) {
                g.text(font, title, px + 7, y, Ui.LABEL, false);
            }
        });
        // how much of the page there is below or above, when it does not all fit
        int view = height - 8, track = view - TOP - 6;
        if (contentHeight > view) {
            int thumb = Math.max(8, track * view / contentHeight);
            int y = py + TOP + 6 + (track - thumb) * scroll / (contentHeight - view);
            g.fill(px + WIDTH - 5, y, px + WIDTH - 3, y + thumb, Ui.LABEL_DIM);
        }
    }

    /** A piece on a dark chip, outlined in its colour while pointed at. */
    static final class Chip extends AbstractButton implements Parts.Called {
        static final int HEIGHT = 12;
        private final int color;
        private final String help;
        private final Runnable onPress;
        int homeX, homeY;

        Chip(int x, int y, Pieces.Piece piece, int color, Runnable onPress) {
            super(x, y, Math.min(WIDTH - 12, Minecraft.getInstance().font.width(piece.label()) + 6), HEIGHT, Component.literal(piece.label()));
            this.color = color;
            this.help = piece.help();
            this.onPress = onPress;
            setTooltip(Tooltip.create(Component.translatable(piece.help())));
        }

        void home(int x, int y) {
            homeX = x;
            homeY = y;
        }

        /** {@code palette.repeat_times}, for {@code repeat 3 (…)}. */
        @Override
        public String partName() {
            return help.substring("residual.".length());
        }

        @Override
        public void onPress(InputWithModifiers input) {
            onPress.run();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            boolean lit = isHoveredOrFocused();
            g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), lit ? Ui.SELECTION : Ui.SCREEN);
            if (lit) {
                g.outline(getX(), getY(), getWidth(), getHeight(), color);
            }
            g.text(Minecraft.getInstance().font, Ui.fit(Minecraft.getInstance().font, getMessage().getString(), getWidth() - 4), getX() + 3, getY() + 2, color, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
