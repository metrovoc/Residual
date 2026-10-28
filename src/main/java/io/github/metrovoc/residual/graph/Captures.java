package io.github.metrovoc.residual.graph;

import io.github.metrovoc.residual.tensor.Tensor;
import java.util.HashMap;
import java.util.Map;

/** Collects intermediate tensors during a forward pass for inspection. */
public final class Captures {
    public final Map<Integer, Tensor> outputs = new HashMap<>();
    /** Attention probabilities [B, H, T, T] (or [B, T, T]) keyed by node. */
    public final Map<Integer, Tensor> patterns = new HashMap<>();

    void output(int node, Tensor t) {
        outputs.put(node, t);
    }

    void pattern(int node, Tensor probs) {
        patterns.put(node, probs);
    }
}
