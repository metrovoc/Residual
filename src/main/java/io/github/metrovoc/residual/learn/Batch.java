package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.tensor.Tokens;

/**
 * Inputs and next-token targets. {@code weights[i]} says whether prediction i counts towards the
 * loss and accuracy (synthetic tasks only score the answer positions).
 *
 * @param perChar what turns its loss per token into loss per character: its tokens over the characters they stand for
 */
public record Batch(Tokens input, int[] targets, float[] weights, double perChar) {
    public Batch(Tokens input, int[] targets, float[] weights) {
        this(input, targets, weights, 1);
    }
}
