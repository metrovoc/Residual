package io.github.metrovoc.residual.graph;

/** What a graph is checked against: the vocabulary size and the longest context it must handle. */
public record Env(int vocab, int context) {
}
