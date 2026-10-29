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
     * The share of block {@code block}'s characters the model predicts as its first choice, each token from the true
     * text before it (up to its context). A token counts for the characters it has in the block.
     */
    public static float accuracy(CompiledGraph model, String text, int block) {
        Vocab vocab = model.env.tokenizer();
        int start = block * Archive.BLOCK, end = Math.min(text.length(), start + Archive.BLOCK);
        int from = Math.max(0, end - 8 * Vocab.CONTEXT);
        int[] all = vocab.encode(text.substring(from, end));
        int skip = Math.max(0, all.length - Vocab.CONTEXT - 1);
        int[] ids = Arrays.copyOfRange(all, skip, all.length);
        int[] input = Arrays.copyOf(ids, Math.max(0, ids.length - 1));
        if (input.length == 0) {
            return 0;
        }
        int at = from;
        for (int i = 0; i < skip; i++) {
            at += vocab.piece(all[i]).length();
        }
        Tensor logits = model.forward(null, new Tokens(input, 1, input.length), null);
        int v = logits.dim(-1), right = 0, count = 0;
        for (int i = 0; i < ids.length; at += vocab.piece(ids[i]).length(), i++) {
            int in = Math.min(end, at + vocab.piece(ids[i]).length()) - Math.max(start, at);
            if (i > 0 && in > 0) {
                right += Trainer.argmax(logits.data, (i - 1) * v, v) == ids[i] ? in : 0;
                count += in;
            }
        }
        return count == 0 ? 0 : (float) right / count;
    }
}
