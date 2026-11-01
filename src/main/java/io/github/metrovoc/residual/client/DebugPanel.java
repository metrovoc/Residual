package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.agent.Debug;
import io.github.metrovoc.residual.agent.Program;
import io.github.metrovoc.residual.net.ProgramSync;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * The debugger beside a program's trace: its steps, one to a line, with the one under way marked and a dot where it stops;
 * pause, go on and one step; and what its variables hold, with expressions of the player's own to watch. A step's number
 * is its place in the listing, numbered as the server numbers it.
 */
final class DebugPanel {
    static final int WIDTH = Palette.WIDTH;
    private static final int LINE = 9;
    private static final int LINES_TOP = 18, LINES = 12;
    private static final int TOOLS = LINES_TOP + LINES * LINE + 3;
    private static final int VALUES_TOP = TOOLS + 22;

    /** What the panel needs from the screen it sits on. */
    interface Host {
        /** Sends a move of the debugger, with the step it is about, or the watched expression. */
        void debug(Debug.Move move, int number, String text);

        /** The program as the body runs it. */
        String program();

        @Nullable
        ProgramSync view();
    }

    private final Host host;
    private final Font font;
    private int px, py, height;
    private int scroll;
    private int valueScroll;
    private boolean shownPaused;
    @Nullable
    private IconButton hold;
    @Nullable
    private EditBox watch;
    private List<Program.Line> listing = List.of();
    private String listed = "";

    DebugPanel(Host host, Font font) {
        this.host = host;
        this.font = font;
    }

    /** {@code debug_lines} and {@code debug_values}, and {@code debug_line:0} on, by the program's own line. */
    void parts(Parts out) {
        out.add("debugger_panel", px, py, WIDTH, height);
        out.add("debug_lines", px + 6, py + LINES_TOP, WIDTH - 12, LINES * LINE);
        out.add("debug_values", px + 6, py + VALUES_TOP + 10, WIDTH - 12, height - VALUES_TOP - 34);
        for (int i = 0; i < LINES && scroll + i < listing.size(); i++) {
            out.add("debug_line:" + (scroll + i), px + 6, py + LINES_TOP + i * LINE, WIDTH - 12, LINE);
        }
    }

    void lay(int x, int y, int height, Consumer<AbstractWidget> add) {
        px = x;
        py = y;
        this.height = height;
        ProgramSync view = host.view();
        shownPaused = view != null && view.paused();
        hold = IconButton.setting(x + 6, y + TOOLS, List.of("pause", "run"), Component.translatable("residual.debug.title"),
                List.of(Component.translatable("residual.debug.running").getString(), Component.translatable("residual.debug.paused").getString()),
                shownPaused ? 1 : 0, Component.translatable("residual.debug.pause.help"), i -> host.debug(i == 1 ? Debug.Move.PAUSE : Debug.Move.RESUME, 0, ""));
        add.accept(hold);
        add.accept(IconButton.action(x + 26, y + TOOLS, "step", Component.translatable("residual.debug.step"),
                Component.translatable("residual.debug.step.help"), () -> host.debug(Debug.Move.STEP, 0, "")));
        watch = new EditBox(font, x + 6, y + height - 20, WIDTH - 12, 14, Component.translatable("residual.debug.watch"));
        watch.setMaxLength(60);
        watch.setHint(Component.translatable("residual.debug.watch_hint"));
        add.accept(watch);
    }

    /** The expression typed, on Enter. */
    boolean submit() {
        if (watch != null && watch.isFocused() && !watch.getValue().isBlank()) {
            host.debug(Debug.Move.WATCH, 0, watch.getValue());
            watch.setValue("");
            return true;
        }
        return false;
    }

    /** Whether the screen should hand keys to its watch box. */
    boolean typing() {
        return watch != null && watch.isFocused();
    }

    boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return watch != null && (watch.keyPressed(event) || watch.canConsumeInput());
    }

    private List<Program.Line> listing() {
        String text = host.program();
        if (!text.equals(listed)) {
            listed = text;
            listing = ProgramSync.listing(text);
            scroll = 0;
        }
        return listing;
    }

    boolean clicked(double mouseX, double mouseY) {
        ProgramSync v = host.view();
        int x = (int) mouseX - px, y = (int) mouseY - py;
        if (x < 6 || x >= WIDTH - 6) {
            return false;
        }
        if (y >= LINES_TOP && y < LINES_TOP + LINES * LINE) {
            int number = (y - LINES_TOP) / LINE + scroll;
            if (number < listing().size()) {
                host.debug(Debug.Move.BREAK, number, "");
            }
            return true;
        }
        if (v != null && y >= VALUES_TOP + 10 && y < VALUES_TOP + 10 + v.watched().size() * LINE) {
            host.debug(Debug.Move.UNWATCH, (y - VALUES_TOP - 10) / LINE + valueScroll, "");
            return true;
        }
        return false;
    }

    boolean scrolled(double mouseX, double mouseY, double amount) {
        if (!Ui.inside(mouseX, mouseY, px, py, WIDTH, height)) {
            return false;
        }
        if (mouseY - py < VALUES_TOP) {
            scroll = Math.max(0, Math.min(scroll - (int) amount, Math.max(0, listing().size() - LINES)));
        } else {
            valueScroll = Math.max(0, valueScroll - (int) amount);
        }
        return true;
    }

    void draw(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Ui.window(g, px, py, WIDTH, height);
        g.text(font, Component.translatable("residual.debug.title"), px + 7, py + 6, Ui.LABEL, false);
        Ui.screen(g, px + 6, py + LINES_TOP, WIDTH - 12, LINES * LINE);
        ProgramSync v = host.view();
        List<Program.Line> lines = listing();
        int shownW = WIDTH - 12;
        for (int i = scroll; i < Math.min(lines.size(), scroll + LINES); i++) {
            Program.Line line = lines.get(i);
            int y = py + LINES_TOP + (i - scroll) * LINE;
            boolean under = v != null && v.at() == i;
            if (under) {
                g.fill(px + 6, y, px + 6 + shownW, y + LINE, Ui.SELECTION);
            }
            if (v != null && v.breaks().contains(i)) {
                g.fill(px + 8, y + 2, px + 12, y + 6, Ui.RED);
            }
            int indent = 8 + Math.min(line.depth(), 5) * 4;
            g.text(font, Ui.fit(font, line.text(), shownW - indent), px + 6 + indent, y + 1, under ? Ui.TEAL : Ui.TEXT, false);
            if (Ui.inside(mouseX, mouseY, px + 6, y, shownW, LINE) && font.width(line.text()) > shownW - indent) {
                g.setTooltipForNextFrame(font.split(Component.literal(line.text()), 240), mouseX, mouseY);
            }
        }
        boolean paused = v != null && v.paused();
        if (hold != null && paused != shownPaused) {
            shownPaused = paused;
            hold.set(paused ? 1 : 0);
        }
        int top = py + VALUES_TOP;
        g.text(font, Component.translatable("residual.debug.values"), px + 7, top, Ui.LABEL, false);
        int bottom = py + height - 24;
        Ui.screen(g, px + 6, top + 10, WIDTH - 12, bottom - top - 10);
        List<ProgramSync.Value> shown = new ArrayList<>();
        if (v != null) {
            shown.addAll(v.watched());
            shown.addAll(v.seen());
        }
        int room = (bottom - top - 10) / LINE;
        int from = Math.min(valueScroll, Math.max(0, shown.size() - room));
        valueScroll = from;
        for (int i = from; i < Math.min(shown.size(), from + room); i++) {
            ProgramSync.Value value = shown.get(i);
            int y = top + 10 + (i - from) * LINE;
            String text = value.name() + " = " + value.value();
            boolean watched = v != null && i < v.watched().size();
            g.text(font, Ui.fit(font, text, WIDTH - 16), px + 8, y + 1, watched ? Ui.AMBER : Ui.TEXT, false);
            if (Ui.inside(mouseX, mouseY, px + 6, y, WIDTH - 12, LINE)) {
                List<FormattedCharSequence> tip = new ArrayList<>(font.split(Component.literal(text), 240));
                if (watched) {
                    tip.addAll(font.split(Component.translatable("residual.debug.unwatch"), 240));
                }
                g.setTooltipForNextFrame(tip, mouseX, mouseY);
            }
        }
    }
}
