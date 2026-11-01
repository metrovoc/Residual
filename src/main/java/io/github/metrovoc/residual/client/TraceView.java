package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.agent.Trace;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** What a program has been doing, newest last, on a dark display. */
final class TraceView {
    private TraceView() {
    }

    /** Draws the last lines of a trace, the first of them at {@code top}, as many as {@code shownMost} fit. */
    static void draw(GuiGraphicsExtractor g, Font font, List<Trace> lines, int top, int shownMost, int mx, int my, int mouseX, int mouseY) {
        if (lines.isEmpty()) {
            g.text(font, Component.translatable("residual.trace.none"), 12, top + 4, Ui.DIM, false);
            return;
        }
        int shown = Math.min(shownMost, lines.size());
        for (int i = 0; i < shown; i++) {
            int at = lines.size() - shown + i;
            Trace t = lines.get(at);
            // more of how a step went, such as each stack sorted, goes under the line that names the step, in line with it
            boolean under = i > 0 && outcome(t) && outcome(lines.get(at - 1)) && lines.get(at - 1).step().equals(t.step());
            Component line = line(t, under);
            int x = under ? 12 + font.width("✔ ") : 12, y = top + 3 + i * 9;
            g.text(font, Ui.fit(font, line.getString(), 164 - x), x, y, color(t.kind()), false);
            if (Ui.inside(mx, my, 8, y - 1, 160, 9) && font.width(line) > 164 - x) {
                g.setTooltipForNextFrame(font.split(line(t, false), 220), mouseX, mouseY);
            }
        }
    }

    private static boolean outcome(Trace t) {
        return (t.kind() == Trace.Kind.DONE || t.kind() == Trace.Kind.FAILED) && !t.key().isEmpty();
    }

    /**
     * One line of the trace: a mark, the step, and how it went. A line {@code under} one naming its step says only how it
     * went, and only a failure keeps its mark.
     */
    private static Component line(Trace t, boolean under) {
        String mark = switch (t.kind()) {
            case RUNNING -> "▸ ";
            case DONE -> under ? "" : "✔ ";
            case FAILED -> "✘ ";
            case YES, NO -> "? ";
            case NOTE -> "• ";
        };
        Component body = switch (t.kind()) {
            case NOTE -> Component.translatable(t.key(), t.step());
            case YES, NO -> Component.translatable(t.kind() == Trace.Kind.YES ? "residual.trace.yes" : "residual.trace.no", t.step());
            default -> t.key().isEmpty() ? Component.literal(t.step())
                    : under ? Component.translatable(t.key(), t.args().toArray())
                    : Component.translatable("residual.trace.outcome", t.step(), Component.translatable(t.key(), t.args().toArray()));
        };
        Component out = Component.literal(mark).append(body);
        return t.times() > 1 ? out.copy().append(Component.translatable("residual.trace.times", t.times())) : out;
    }

    private static int color(Trace.Kind kind) {
        return switch (kind) {
            case RUNNING -> Ui.TEXT;
            case DONE -> Ui.GREEN;
            case FAILED -> Ui.RED;
            case YES, NO -> Ui.DIM;
            case NOTE -> Ui.AMBER;
        };
    }
}
