package io.github.metrovoc.residual.world;

import io.github.metrovoc.residual.graph.GraphSpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lays a machine graph out as blocks in front of a Model Core, in the Core's frame: {@code x} runs out of its front,
 * {@code l} to one side and {@code y} up. Machines stand in a row facing along {@code x}, three blocks apart, so each
 * has a cable cell behind it (main input) and in front of it (output). A value that only feeds the next machine's
 * main input crosses straight over; any other value rises from its output cell to a track of its own one block to
 * the side and drops down to every input it feeds, the Core's top included. Columns never collide (each belongs to
 * one port) and tracks at the same height never overlap, so networks only ever meet side by side, where cable colors
 * keep them apart.
 */
public final class CaseLayout {
    /** Cable colors in the order they are handed out, as {@code DyeColor} ordinals. */
    private static final int[] PALETTE = {3, 1, 5, 2, 4, 9, 6, 10, 14, 13, 11, 12, 0, 8, 7, 15};
    private static final int SPACING = 3;
    private static final int FIRST_TRACK = 2;

    /** A position in the Core's frame; the Core itself is at the origin. */
    public record Cell(int x, int l, int y) {
        Cell step(int dx, int dl, int dy) {
            return new Cell(x + dx, l + dl, y + dy);
        }
    }

    public record Machine(int node, Cell at) {
    }

    public record Cable(int color, Cell at) {
    }

    public final List<Machine> machines;
    public final List<Cable> cables;
    /** Size of the box the layout fills, Core included, measured from the Core. */
    public final int length, width, height;

    private CaseLayout(List<Machine> machines, List<Cable> cables) {
        this.machines = List.copyOf(machines);
        this.cables = List.copyOf(cables);
        int x = 1, l = 1, y = 1;
        for (Cable c : cables) {
            x = Math.max(x, c.at.x + 1);
            l = Math.max(l, c.at.l + 1);
            y = Math.max(y, c.at.y + 1);
        }
        for (Machine m : machines) {
            x = Math.max(x, m.at.x + 1);
        }
        this.length = x;
        this.width = l;
        this.height = y;
    }

    /** Every cell the layout fills apart from the Core's. */
    public List<Cell> cells() {
        List<Cell> out = new ArrayList<>();
        machines.forEach(m -> out.add(m.at));
        cables.forEach(c -> out.add(c.at));
        return out;
    }

    public static CaseLayout of(GraphSpec spec) {
        if (spec.output < 0) {
            throw new IllegalArgumentException("graph has no output node");
        }
        List<Integer> order = new ArrayList<>();
        place(spec, spec.output, new HashSet<>(), order);
        Map<Integer, Integer> slot = new HashMap<>();
        for (int k = 0; k < order.size(); k++) {
            slot.put(order.get(k), k);
        }

        // value (node index, or TOKENS) -> the cells of its network, in placement order
        Map<Integer, Set<Cell>> nets = new LinkedHashMap<>();
        Map<Integer, List<Integer>> reads = new LinkedHashMap<>();
        Map<Integer, Integer> spanStart = new HashMap<>();
        Map<Integer, Integer> spanEnd = new HashMap<>();
        for (int k = 0; k < order.size(); k++) {
            GraphSpec.Node node = spec.node(order.get(k));
            int machine = column(k);
            for (int i = 0; i < node.op.arity; i++) {
                int src = node.inputs[i];
                if (src < GraphSpec.TOKENS) {
                    throw new IllegalArgumentException("graph has an unconnected input");
                }
                int port = i == 0 ? machine - 1 : machine;
                nets.computeIfAbsent(src, s -> new LinkedHashSet<>()).add(i == 0 ? new Cell(port, 0, 0) : new Cell(port, 0, 1));
                if (i == 0 && port == output(src, slot) + 1) {
                    continue;
                }
                reads.computeIfAbsent(src, s -> new ArrayList<>()).add(port);
            }
        }
        reads.computeIfAbsent(spec.output, s -> new ArrayList<>()).add(0);
        nets.computeIfAbsent(spec.output, s -> new LinkedHashSet<>());

        // give every value that needs one a track, lowest free height first
        List<int[]> tracks = new ArrayList<>();
        Map<Integer, Integer> heights = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> e : reads.entrySet()) {
            int from = output(e.getKey(), slot);
            int lo = from, hi = from;
            for (int c : e.getValue()) {
                lo = Math.min(lo, c);
                hi = Math.max(hi, c);
            }
            int h = 0;
            while (h < tracks.size() && overlaps(tracks.get(h), lo, hi)) {
                h++;
            }
            if (h == tracks.size()) {
                tracks.add(new int[0]);
            }
            int[] used = tracks.get(h);
            int[] grown = java.util.Arrays.copyOf(used, used.length + 2);
            grown[used.length] = lo;
            grown[used.length + 1] = hi;
            tracks.set(h, grown);
            heights.put(e.getKey(), FIRST_TRACK + h);
            spanStart.put(e.getKey(), lo);
            spanEnd.put(e.getKey(), hi);
        }

        for (Map.Entry<Integer, Set<Cell>> e : nets.entrySet()) {
            int value = e.getKey();
            Set<Cell> cells = e.getValue();
            int from = output(value, slot);
            cells.add(new Cell(from, 0, 0));
            Integer h = heights.get(value);
            if (h == null) {
                continue;
            }
            for (int y = 1; y <= h; y++) {
                cells.add(new Cell(from, 0, y));
            }
            for (int x = spanStart.get(value); x <= spanEnd.get(value); x++) {
                cells.add(new Cell(x, 1, h));
            }
            for (int c : reads.get(value)) {
                for (int y = 1; y <= h; y++) {
                    cells.add(new Cell(c, 0, y));
                }
            }
        }

        // color the networks so that no two that touch share a color
        Map<Cell, Integer> owner = new HashMap<>();
        for (Map.Entry<Integer, Set<Cell>> e : nets.entrySet()) {
            for (Cell c : e.getValue()) {
                Integer prev = owner.put(c, e.getKey());
                if (prev != null && !prev.equals(e.getKey())) {
                    throw new IllegalStateException("two networks routed through " + c);
                }
            }
        }
        Map<Integer, Integer> colors = new HashMap<>();
        List<Cable> cables = new ArrayList<>();
        for (Map.Entry<Integer, Set<Cell>> e : nets.entrySet()) {
            Set<Integer> taken = new HashSet<>();
            for (Cell c : e.getValue()) {
                for (Cell n : List.of(c.step(1, 0, 0), c.step(-1, 0, 0), c.step(0, 1, 0), c.step(0, -1, 0), c.step(0, 0, 1), c.step(0, 0, -1))) {
                    Integer other = owner.get(n);
                    if (other != null && !other.equals(e.getKey()) && colors.containsKey(other)) {
                        taken.add(colors.get(other));
                    }
                }
            }
            int color = -1;
            for (int p : PALETTE) {
                if (!taken.contains(p)) {
                    color = p;
                    break;
                }
            }
            if (color < 0) {
                throw new IllegalStateException("ran out of cable colors");
            }
            colors.put(e.getKey(), color);
            for (Cell c : e.getValue()) {
                cables.add(new Cable(color, c));
            }
        }

        List<Machine> machines = new ArrayList<>();
        for (int k = 0; k < order.size(); k++) {
            machines.add(new Machine(order.get(k), new Cell(column(k), 0, 0)));
        }
        return new CaseLayout(machines, cables);
    }

    /** Depth-first, side input before main input, so a machine's main input tends to sit right behind it. */
    private static void place(GraphSpec spec, int node, Set<Integer> seen, List<Integer> order) {
        if (node < 0 || !seen.add(node)) {
            return;
        }
        int[] inputs = spec.node(node).inputs;
        for (int i = inputs.length - 1; i >= 0; i--) {
            place(spec, inputs[i], seen, order);
        }
        order.add(node);
    }

    private static int column(int slot) {
        return SPACING * slot + SPACING;
    }

    /** The column of the cell a value leaves its producer through: the Core's front for tokens. */
    private static int output(int value, Map<Integer, Integer> slot) {
        return value == GraphSpec.TOKENS ? 1 : column(slot.get(value)) + 1;
    }

    /** Whether a span overlaps (or touches) any of the spans already on a track. */
    private static boolean overlaps(int[] spans, int lo, int hi) {
        for (int i = 0; i < spans.length; i += 2) {
            if (lo <= spans[i + 1] + 1 && spans[i] <= hi + 1) {
                return true;
            }
        }
        return false;
    }
}
