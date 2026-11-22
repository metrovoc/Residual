package io.github.metrovoc.residual.interior;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * What a model is made of, as a player arranges it: a stream of some width that every token gets its own copy of,
 * and the parts that stand across it in order. With no parts it is a table: what a letter is turned into is read
 * straight back as chances for the next.
 *
 * @param width     how many numbers the stream carries for each token
 * @param heads     how many heads each attention part has; divides the width
 * @param positions whether each place in the text adds its own vector to the stream
 * @param norm      whether a part reads the stream normalized, and the stream is normalized before it is read out
 */
public record Arch(int width, int heads, boolean positions, boolean norm, List<Part> parts) {
    public static final int[] WIDTHS = {8, 16, 32, 64};
    public static final int[] HEADS = {1, 2, 4, 8};
    /** More parts than this and a hall is too long to walk. */
    public static final int MAX_PARTS = 12;

    public enum Kind {
        /** Lets a token read from the tokens before it. */
        ATTENTION,
        /** Works on each token's stream alone. */
        FEED_FORWARD
    }

    /**
     * One part across the stream.
     *
     * @param id its own number, which its weights go by however the parts around it change
     * @param on whether it adds to the stream; a part switched off keeps its weights
     */
    public record Part(int id, Kind kind, boolean on) {
    }

    public Arch {
        parts = List.copyOf(parts);
        if (width % heads != 0) {
            throw new IllegalArgumentException(heads + " heads do not divide a stream of " + width);
        }
    }

    /** The smallest model: a table. */
    public static Arch table() {
        return new Arch(16, 1, false, true, List.of());
    }

    public Arch withWidth(int width) {
        return new Arch(width, Math.min(heads, width), positions, norm, parts);
    }

    public Arch withHeads(int heads) {
        return new Arch(width, heads, positions, norm, parts);
    }

    public Arch withPositions(boolean positions) {
        return new Arch(width, heads, positions, norm, parts);
    }

    public Arch withNorm(boolean norm) {
        return new Arch(width, heads, positions, norm, parts);
    }

    /** With a new part of {@code kind} standing at {@code index}; null if the hall is full. */
    public Arch insert(int index, Kind kind) {
        if (parts.size() >= MAX_PARTS) {
            return null;
        }
        int id = 1;
        for (Part p : parts) {
            id = Math.max(id, p.id + 1);
        }
        List<Part> next = new ArrayList<>(parts);
        next.add(Math.clamp(index, 0, next.size()), new Part(id, kind, true));
        return new Arch(width, heads, positions, norm, next);
    }

    public Arch remove(int id) {
        List<Part> next = new ArrayList<>(parts);
        next.removeIf(p -> p.id == id);
        return new Arch(width, heads, positions, norm, next);
    }

    public Arch toggle(int id) {
        List<Part> next = new ArrayList<>();
        for (Part p : parts) {
            next.add(p.id == id ? new Part(p.id, p.kind, !p.on) : p);
        }
        return new Arch(width, heads, positions, norm, next);
    }

    /** How many attention parts are switched on: how many times a token can read from the others. */
    public int layers() {
        return (int) parts.stream().filter(p -> p.on && p.kind == Kind.ATTENTION).count();
    }

    public static String embed() {
        return "embed";
    }

    public static String position() {
        return "position";
    }

    /** The node that holds a part's weights. */
    public static String part(int id) {
        return "part" + id;
    }

    /** The node that holds the stream after a part has added to it. */
    public static String after(int id) {
        return "after" + id;
    }

    public static String logits() {
        return "logits";
    }

    /** The model as the engine runs it. A part switched off is left out, and the stream passes it unchanged. */
    public GraphSpec spec() {
        GraphSpec g = new GraphSpec();
        String w = Integer.toString(width);
        int x = g.add(embed(), Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", w), GraphSpec.TOKENS);
        if (positions) {
            x = g.add("placed", Op.ADD, x, g.add(position(), Op.POSITION, Config.of(Op.POSITION, "dim", w)));
        }
        for (Part p : parts) {
            if (!p.on) {
                continue;
            }
            int in = norm ? g.add("norm" + p.id, Op.NORM, x) : x;
            int out = p.kind == Kind.ATTENTION
                    ? g.add(part(p.id), Op.ATTENTION, Config.of(Op.ATTENTION, "heads", Integer.toString(heads)), in)
                    : g.add(part(p.id), Op.MLP, in);
            x = g.add(after(p.id), Op.ADD, x, out);
        }
        int out = norm ? g.add("norm_out", Op.NORM, x) : x;
        g.output = g.add(logits(), Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), out);
        return g;
    }

    /** The index of the node called {@code name} in {@code spec}, or -1. */
    public static int node(GraphSpec spec, String name) {
        for (int i = 0; i < spec.size(); i++) {
            if (name.equals(spec.node(i).name)) {
                return i;
            }
        }
        return -1;
    }

    /** Every node's weights by its name, added to {@code bank}: what a rebuilt model starts from. */
    public static void keep(CompiledGraph model, Map<String, Map<String, Tensor>> bank) {
        model.snapshot().forEach((node, params) -> {
            if (!params.isEmpty()) {
                bank.put(model.spec.node(node).name, params);
            }
        });
    }

    /** Hands a compiler the banked weights of each node of {@code spec} whose shapes still fit. */
    public static IntFunction<Map<String, Tensor>> carried(GraphSpec spec, Map<String, Map<String, Tensor>> bank) {
        return node -> bank.get(spec.node(node).name);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("width", width);
        tag.putInt("heads", heads);
        tag.putBoolean("positions", positions);
        tag.putBoolean("norm", norm);
        ListTag list = new ListTag();
        for (Part p : parts) {
            CompoundTag t = new CompoundTag();
            t.putInt("id", p.id);
            t.putString("kind", p.kind.name());
            t.putBoolean("on", p.on);
            list.add(t);
        }
        tag.put("parts", list);
        return tag;
    }

    public static Arch load(CompoundTag tag) {
        List<Part> parts = new ArrayList<>();
        ListTag list = tag.getListOrEmpty("parts");
        Map<Integer, Boolean> seen = new HashMap<>();
        for (int i = 0; i < list.size() && parts.size() < MAX_PARTS; i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            int id = t.getIntOr("id", 0);
            Kind kind = "FEED_FORWARD".equals(t.getStringOr("kind", "")) ? Kind.FEED_FORWARD : Kind.ATTENTION;
            if (id > 0 && seen.put(id, true) == null) {
                parts.add(new Part(id, kind, t.getBooleanOr("on", true)));
            }
        }
        int width = nearest(WIDTHS, tag.getIntOr("width", 16));
        int heads = nearest(HEADS, tag.getIntOr("heads", 1));
        return new Arch(width, Math.min(heads, width), tag.getBooleanOr("positions", false), tag.getBooleanOr("norm", true), parts);
    }

    private static int nearest(int[] allowed, int value) {
        int best = allowed[0];
        for (int a : allowed) {
            if (Math.abs(a - value) < Math.abs(best - value)) {
                best = a;
            }
        }
        return best;
    }
}
