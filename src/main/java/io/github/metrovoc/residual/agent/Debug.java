package io.github.metrovoc.residual.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a debugger holds for a program, kept when the program starts over: where it is paused, its breakpoints, by the
 * number of a step in the listing, and the expressions being watched. A breakpoint's number follows the step only until
 * the text changes.
 */
public final class Debug {
    /** The most expressions watched at once. */
    public static final int WATCHES = 8;

    public final Set<Integer> breaks = new TreeSet<>();
    public final List<String> watches = new ArrayList<>();
    /** Whether it holds at the next step. */
    public boolean paused;
    /** Whether one step may go, and then it holds again. */
    public boolean stepping;
    /** The number of the step under way, or -1. */
    public int at = -1;

    public enum Move { PAUSE, RESUME, STEP, BREAK, WATCH, UNWATCH }

    /** Does what the debugger's controls ask. */
    public void apply(Move move, int number, String text) {
        switch (move) {
            case PAUSE -> paused = true;
            case RESUME -> {
                paused = false;
                stepping = false;
            }
            case STEP -> {
                paused = true;
                stepping = true;
            }
            case BREAK -> {
                if (!breaks.remove(number)) {
                    breaks.add(number);
                }
            }
            case WATCH -> {
                String watch = text.strip();
                if (!watch.isEmpty() && !watches.contains(watch) && watches.size() < WATCHES && Program.expression(watch) != null) {
                    watches.add(watch);
                }
            }
            case UNWATCH -> {
                if (number >= 0 && number < watches.size()) {
                    watches.remove(number);
                }
            }
        }
    }
}
