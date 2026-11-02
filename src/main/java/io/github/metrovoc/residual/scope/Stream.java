package io.github.metrovoc.residual.scope;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.graph.Sym;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A model's residual stream, read from its graph: the points every path from the tokens to the scores passes through, at the
 * width the last of them hands to the head that turns it into scores. In a stack of blocks they are the stream after each block,
 * however the player laid it out, and the head is what comes after the last: a norm and an unembedding.
 */
public final class Stream {
    public final CompiledGraph model;
    /** The stream's points, in the order they run. */
    public final int[] points;
    /** The head, in the order it runs: nothing when the last point already is the scores. */
    public final int[] head;

    private Stream(CompiledGraph model, int[] points, int[] head) {
        this.model = model;
        this.points = points;
        this.head = head;
    }

    public static Stream of(CompiledGraph model) {
        GraphSpec spec = model.spec;
        List<Integer> head = new ArrayList<>();
        int at = spec.output;
        while (headOp(spec.node(at).op) && spec.node(at).inputs[0] >= 0) {
            head.addFirst(at);
            at = spec.node(at).inputs[0];
        }
        int width = model.shapes[at].dim(-1);
        List<Integer> points = new ArrayList<>();
        for (int node : model.order) {
            Sym shape = model.shapes[node];
            if (!head.contains(node) && !shape.tokens && shape.rank() == 3 && shape.dim(-1) == width && carriesAll(spec, node)) {
                points.add(node);
            }
        }
        // an embedding with its positions still to come is the same stream a moment early
        if (points.size() > 1 && spec.node(points.getFirst()).op == Op.EMBEDDING) {
            points.removeFirst();
        }
        return new Stream(model, points.stream().mapToInt(Integer::intValue).toArray(), head.stream().mapToInt(Integer::intValue).toArray());
    }

    private static boolean headOp(Op op) {
        return op == Op.LINEAR || op == Op.NORM || op == Op.ACTIVATION || op == Op.SCALE;
    }

    /** Whether every path from the tokens to the output passes through {@code node}. */
    private static boolean carriesAll(GraphSpec spec, int node) {
        return !reachesTokens(spec, spec.output, node, new boolean[spec.size()]);
    }

    private static boolean reachesTokens(GraphSpec spec, int from, int blocked, boolean[] seen) {
        if (from == GraphSpec.TOKENS) {
            return true;
        }
        if (from < 0 || from == blocked || seen[from]) {
            return false;
        }
        seen[from] = true;
        for (int in : spec.node(from).inputs) {
            if (reachesTokens(spec, in, blocked, seen)) {
                return true;
            }
        }
        return false;
    }

    /** What the head makes of what a point of the stream carries: scores for every token at every position. */
    public Tensor read(@Nullable Tape tape, Tensor at) {
        return model.through(tape, head, at);
    }

    /** A machine's name: what it is called, or what it is, with its place along the stream if it is on it. */
    public Component name(int node) {
        GraphSpec.Node n = model.spec.node(node);
        Component name = n.name == null || n.name.isEmpty() ? Component.translatable("block.residual." + n.op.id) : Component.literal(n.name);
        for (int p = 0; p < points.length; p++) {
            if (points[p] == node) {
                return Component.literal((p + 1) + " ").append(name);
            }
        }
        return name;
    }

    /** How wide the stream is. */
    public int width() {
        return points.length == 0 ? 0 : model.shapes[points[0]].dim(-1);
    }
}
