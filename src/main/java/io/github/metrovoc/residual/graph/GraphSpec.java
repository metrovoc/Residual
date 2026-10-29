package io.github.metrovoc.residual.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * A model as data: operators and which node feeds each of their inputs. The world scanner, the
 * casebook and the tests all build these; the compiler turns one into something runnable.
 */
public final class GraphSpec {
    /** Input fed by the Model Core's token output. */
    public static final int TOKENS = -1;
    /** Input with nothing connected. */
    public static final int MISSING = -2;
    /** Input whose cable network has more than one driver. */
    public static final int CONFLICT = -3;

    public static final class Node {
        public final Op op;
        public final Config config;
        public final int[] inputs;
        public String name;
        /** Where the node lives in the world (a packed block position), or 0. */
        public long origin;
        /** Whether its machine runs fused kernels (a verified design) or one primitive at a time (a salvaged one). */
        public boolean fused = true;

        Node(Op op, Config config, int[] inputs, String name, long origin) {
            this.op = op;
            this.config = config;
            this.inputs = inputs;
            this.name = name;
            this.origin = origin;
        }
    }

    public final List<Node> nodes = new ArrayList<>();
    /** The node feeding the Model Core's logits input. */
    public int output = MISSING;

    public int add(String name, Op op, Config config, int... inputs) {
        if (inputs.length != op.arity) {
            throw new IllegalArgumentException(op.id + " takes " + op.arity + " inputs");
        }
        nodes.add(new Node(op, config, inputs, name, 0));
        return nodes.size() - 1;
    }

    public int add(String name, Op op, int... inputs) {
        return add(name, op, new Config(op), inputs);
    }

    /** Adds a node whose inputs are wired later through {@link Node#inputs}. */
    public int addUnwired(String name, Op op, Config config, long origin) {
        int[] in = new int[op.arity];
        java.util.Arrays.fill(in, MISSING);
        nodes.add(new Node(op, config, in, name, origin));
        return nodes.size() - 1;
    }

    public Node node(int i) {
        return nodes.get(i);
    }

    public int size() {
        return nodes.size();
    }

    /** Every node, each after the nodes it reads (a cycle is cut anywhere): the order to list machines in. */
    public int[] readingOrder() {
        int[] order = new int[size()];
        boolean[] seen = new boolean[size()];
        int next = 0;
        for (int i = 0; i < size(); i++) {
            next = visit(i, seen, order, next);
        }
        return order;
    }

    private int visit(int node, boolean[] seen, int[] order, int next) {
        if (seen[node]) {
            return next;
        }
        seen[node] = true;
        for (int in : nodes.get(node).inputs) {
            if (in >= 0) {
                next = visit(in, seen, order, next);
            }
        }
        order[next] = node;
        return next + 1;
    }
}
