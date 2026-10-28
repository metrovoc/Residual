package io.github.metrovoc.residual.graph;

import java.util.List;

/** Everything wrong with a graph, each issue pinned to the node it concerns (or -1 for the Model Core). */
public final class CompileException extends Exception {
    public record Issue(int node, String message) {
    }

    public final List<Issue> issues;

    public CompileException(List<Issue> issues) {
        super(issues.isEmpty() ? "invalid graph" : issues.getFirst().message());
        this.issues = List.copyOf(issues);
    }
}
