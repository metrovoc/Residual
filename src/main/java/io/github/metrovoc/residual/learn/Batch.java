package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.tensor.Tokens;

/**
 * Inputs and next-token targets. {@code weights[i]} says whether prediction i counts towards the
 * loss and accuracy (synthetic tasks only score the answer positions).
 */
public record Batch(Tokens input, int[] targets, float[] weights) {
}
