package io.github.metrovoc.residual.graph;

import io.github.metrovoc.residual.learn.Vocab;

/** What a graph is checked against: the tokenizer it reads with and the longest context it must handle. */
public record Env(Vocab tokenizer, int context) {
    /** Characters only, at the usual context. */
    public static final Env WORLD = new Env(Vocab.WORLD, Vocab.CONTEXT);

    /** How many tokens it has: the width of an embedding's input and of the scores. */
    public int vocab() {
        return tokenizer.size();
    }
}
