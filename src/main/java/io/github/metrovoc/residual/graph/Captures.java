package io.github.metrovoc.residual.graph;

import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Collects intermediate tensors during a forward pass for inspection, and lets an instrument reach into it. */
public final class Captures {
    /** What an instrument does to a node's output as it leaves. */
    public interface Edit {
        Tensor apply(@Nullable Tape tape, int node, Tensor out);
    }

    /** What an instrument does to a node's attention pattern [B, H, T, T] or [B, T, T] as it is used. */
    public interface PatternEdit {
        Tensor apply(int node, Tensor probs);
    }

    public final Map<Integer, Tensor> outputs = new HashMap<>();
    /** Attention probabilities [B, H, T, T] (or [B, T, T]) keyed by node. */
    public final Map<Integer, Tensor> patterns = new HashMap<>();
    /** The largest score any attention turned into weights, in size. */
    public float topScore;
    @Nullable
    public Edit edit;
    @Nullable
    public PatternEdit patternEdit;
    /** The only nodes whose outputs are kept, for reading a lot of text at once; null for all of them. */
    @Nullable
    public Set<Integer> only;
    /** Gradients pass through attention patterns and normalization as through constants, which makes them linear in what they carry. */
    public boolean frozen;

    void output(int node, Tensor t) {
        if (only == null || only.contains(node)) {
            outputs.put(node, t);
        }
    }

    /** Notes the scores a softmax is about to turn into weights; what a mask hid doesn't count. */
    void score(Tensor scores) {
        for (float s : scores.data) {
            if (Float.isFinite(s)) {
                topScore = Math.max(topScore, Math.abs(s));
            }
        }
    }

    Tensor pattern(int node, Tensor probs) {
        Tensor used = patternEdit == null ? probs : patternEdit.apply(node, probs);
        patterns.put(node, used);
        return used;
    }
}
