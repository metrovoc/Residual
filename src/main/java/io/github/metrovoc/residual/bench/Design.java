package io.github.metrovoc.residual.bench;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.tensor.ShapeException;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * A module as the player wired it on a Bench: the spec's inputs, primitives and verified modules, each reading the
 * nodes on its input wires, and one node whose value is the result. Positions are where the nodes sit on the Bench.
 */
public record Design(List<Node> nodes, int output) {
    public static final int MAX_NODES = 64;
    /** An input with no wire. */
    public static final int OPEN = -1;

    /**
     * One node. {@code kind} is {@code input}, a primitive's id, or {@code module:} and a spec's id; {@code value} is a
     * constant's number or an input's index.
     */
    public record Node(String kind, float value, int x, int y, List<Integer> inputs) {
        public static final Codec<Node> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("kind").forGetter(Node::kind),
                Codec.FLOAT.optionalFieldOf("value", 0f).forGetter(Node::value),
                Codec.INT.fieldOf("x").forGetter(Node::x),
                Codec.INT.fieldOf("y").forGetter(Node::y),
                Codec.INT.listOf().fieldOf("inputs").forGetter(Node::inputs)
        ).apply(i, Node::new));

        public boolean isInput() {
            return kind.equals("input");
        }

        @Nullable
        public Prim prim() {
            return Prim.byId(kind);
        }

        @Nullable
        public Spec module() {
            return kind.startsWith("module:") ? Specs.get(kind.substring(7)) : null;
        }

        public int arity() {
            Prim p = prim();
            if (p != null) {
                return p.arity;
            }
            Spec m = module();
            return m == null ? 0 : m.ports().size();
        }

        public Node moved(int nx, int ny) {
            return new Node(kind, value, nx, ny, inputs);
        }

        public Node withValue(float v) {
            return new Node(kind, v, x, y, inputs);
        }

        public Node wired(int slot, int source) {
            List<Integer> in = new ArrayList<>(inputs);
            in.set(slot, source);
            return new Node(kind, value, x, y, List.copyOf(in));
        }
    }

    public static final Codec<Design> CODEC = RecordCodecBuilder.create(i -> i.group(
            Node.CODEC.listOf().fieldOf("nodes").forGetter(Design::nodes),
            Codec.INT.fieldOf("output").forGetter(Design::output)
    ).apply(i, Design::new));
    public static final StreamCodec<ByteBuf, Design> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    /** A fresh design for {@code spec}: just its inputs, down the left edge. */
    public static Design blank(Spec spec) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < spec.ports().size(); i++) {
            nodes.add(new Node("input", i, 4, 6 + i * 26, List.of()));
        }
        return new Design(List.copyOf(nodes), OPEN);
    }

    /** Whether this design still fits its spec: the same inputs, every wire in range, known kinds. */
    public boolean fits(Spec spec) {
        if (nodes.size() > MAX_NODES || output < OPEN || output >= nodes.size()) {
            return false;
        }
        int inputs = 0;
        for (Node n : nodes) {
            if (n.isInput()) {
                if ((int) n.value() != inputs++) {
                    return false;
                }
            } else if (n.prim() == null && n.module() == null) {
                return false;
            }
            if (n.inputs().size() != n.arity()) {
                return false;
            }
            for (int src : n.inputs()) {
                if (src < OPEN || src >= nodes.size()) {
                    return false;
                }
            }
        }
        return inputs == spec.ports().size();
    }

    // ---------------------------------------------------------------- running

    /** What each node computed, or why it couldn't. */
    public record Run(Tensor[] values, String[] errors, @Nullable Tensor result) {
    }

    /** Runs every node it can on one set of inputs; a node waiting on a broken or open input stays empty. */
    public Run run(@Nullable Tape tape, Tensor[] inputs) {
        int n = nodes.size();
        Tensor[] values = new Tensor[n];
        String[] errors = new String[n];
        int[] state = new int[n];
        for (int i = 0; i < n; i++) {
            eval(i, tape, inputs, values, errors, state);
        }
        Tensor result = output >= 0 ? values[output] : null;
        return new Run(values, errors, result);
    }

    private void eval(int i, @Nullable Tape tape, Tensor[] inputs, Tensor[] values, String[] errors, int[] state) {
        if (state[i] == 2) {
            return;
        }
        if (state[i] == 1) {
            errors[i] = "loop";
            return;
        }
        state[i] = 1;
        Node node = nodes.get(i);
        Tensor[] in = new Tensor[node.inputs().size()];
        boolean ready = true;
        for (int k = 0; k < in.length; k++) {
            int src = node.inputs().get(k);
            if (src == OPEN) {
                ready = false;
                continue;
            }
            eval(src, tape, inputs, values, errors, state);
            if (values[src] == null) {
                ready = false;
                if (errors[src] != null && errors[src].equals("loop")) {
                    errors[i] = "loop";
                }
            }
            in[k] = values[src];
        }
        state[i] = 2;
        if (!ready) {
            return;
        }
        try {
            if (node.isInput()) {
                values[i] = inputs[(int) node.value()];
            } else if (node.prim() != null) {
                values[i] = node.prim().apply(tape, in, node.value());
            } else {
                values[i] = node.module().kernel().apply(tape, in);
            }
        } catch (ShapeException | IllegalArgumentException e) {
            errors[i] = e.getMessage();
        }
    }

    // ---------------------------------------------------------------- measures

    /** Fabrication area: what each primitive and module in the design costs to lay down. */
    public int area() {
        int a = 0;
        for (Node n : nodes) {
            if (n.prim() != null) {
                a += n.prim().area;
            } else if (n.module() != null) {
                a += n.module().area();
            }
        }
        return a;
    }

    /** Primitives and modules, not counting the inputs. */
    public int parts() {
        int c = 0;
        for (Node n : nodes) {
            c += n.isInput() ? 0 : 1;
        }
        return c;
    }

    /** The specs whose modules this design uses. */
    public List<String> modules() {
        List<String> out = new ArrayList<>();
        for (Node n : nodes) {
            if (n.module() != null && !out.contains(n.module().id())) {
                out.add(n.module().id());
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- editing

    public Design with(int index, Node node) {
        List<Node> list = new ArrayList<>(nodes);
        list.set(index, node);
        return new Design(List.copyOf(list), output);
    }

    public Design plus(Node node) {
        List<Node> list = new ArrayList<>(nodes);
        list.add(node);
        return new Design(List.copyOf(list), output);
    }

    public Design withOutput(int node) {
        return new Design(nodes, node);
    }

    /** Removes a node and every wire from it; inputs can't be removed. */
    public Design without(int index) {
        if (nodes.get(index).isInput()) {
            return this;
        }
        List<Node> list = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            if (i == index) {
                continue;
            }
            Node n = nodes.get(i);
            List<Integer> in = new ArrayList<>();
            for (int src : n.inputs()) {
                in.add(src == index ? OPEN : src > index ? src - 1 : src);
            }
            list.add(new Node(n.kind(), n.value(), n.x(), n.y(), List.copyOf(in)));
        }
        int out = output == index ? OPEN : output > index ? output - 1 : output;
        return new Design(List.copyOf(list), out);
    }

    /** A node of {@code kind} with all its inputs open. */
    public static Node node(String kind, float value, int x, int y, int arity) {
        Integer[] in = new Integer[arity];
        Arrays.fill(in, OPEN);
        return new Node(kind, value, x, y, List.of(in));
    }

    // ---------------------------------------------------------------- building in code

    /** Builds a design node by node, laying the nodes out by depth; the salvaged designs are written this way. */
    public static final class Builder {
        private final List<Node> nodes = new ArrayList<>();
        private final List<Integer> depth = new ArrayList<>();

        public Builder(Spec spec) {
            for (int i = 0; i < spec.ports().size(); i++) {
                nodes.add(new Node("input", i, 0, 0, List.of()));
                depth.add(0);
            }
        }

        public int input(int i) {
            return i;
        }

        public int constant(float v) {
            nodes.add(new Node(Prim.CONST.id, v, 0, 0, List.of()));
            depth.add(1);
            return nodes.size() - 1;
        }

        public int prim(Prim p, int... in) {
            return add(p.id, in);
        }

        public int module(String spec, int... in) {
            return add("module:" + spec, in);
        }

        private int add(String kind, int... in) {
            List<Integer> list = new ArrayList<>();
            int d = 0;
            for (int src : in) {
                list.add(src);
                d = Math.max(d, depth.get(src));
            }
            nodes.add(new Node(kind, 0f, 0, 0, List.copyOf(list)));
            depth.add(d + 1);
            return nodes.size() - 1;
        }

        public Design output(int node) {
            int[] rows = new int[nodes.size() + 1];
            List<Node> placed = new ArrayList<>();
            for (int i = 0; i < nodes.size(); i++) {
                int d = depth.get(i);
                Node n = nodes.get(i);
                placed.add(n.moved(4 + d * 62, 6 + rows[d]++ * 26));
            }
            return new Design(List.copyOf(placed), node);
        }
    }
}
