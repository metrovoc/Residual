package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.Arrays;

/** How a Reader tells whether a model can restore a lost block of a record. */
public final class Reading {
    /** A block comes back when the model gets at least this share of its characters right. */
    public static final float RESTORE = 0.5f;

    private Reading() {
    }

    /**
     * The share of block {@code block}'s characters the model predicts as its first choice, each from the true text
     * before it (up to its context).
     */
    public static float accuracy(CompiledGraph model, String text, int block) {
        int start = block * Archive.BLOCK, end = Math.min(text.length(), start + Archive.BLOCK);
        int from = Math.max(0, end - Vocab.CONTEXT - 1);
        int[] ids = Vocab.WORLD.encode(text.substring(from, end));
        int[] input = Arrays.copyOf(ids, Math.max(0, ids.length - 1));
        if (input.length == 0) {
            return 0;
        }
        Tensor logits = model.forward(null, new Tokens(input, 1, input.length), null);
        int v = logits.dim(-1), right = 0, count = 0;
        for (int i = Math.max(1, start - from); i < ids.length; i++) {
            int best = Trainer.argmax(logits.data, (i - 1) * v, v);
            right += best == ids[i] ? 1 : 0;
            count++;
        }
        return count == 0 ? 0 : (float) right / count;
    }
}
