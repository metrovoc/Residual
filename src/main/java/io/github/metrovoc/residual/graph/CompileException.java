package io.github.metrovoc.residual.graph;

import java.util.List;
import net.minecraft.network.chat.Component;

/** Everything wrong with a graph, each issue pinned to the node it concerns (or -1 for the Model Core). */
public final class CompileException extends Exception {
    public record Issue(int node, Component message) {
    }

    public final List<Issue> issues;
    /** Output shapes of the nodes that did check out (null entries elsewhere), when the check got that far. */
    public final Sym[] shapes;

    public CompileException(List<Issue> issues) {
        this(issues, null);
    }

    public CompileException(List<Issue> issues, Sym[] shapes) {
        super(issues.isEmpty() ? "invalid graph" : issues.getFirst().message().getString());
        this.issues = List.copyOf(issues);
        this.shapes = shapes;
    }
}
