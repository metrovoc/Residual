package io.github.metrovoc.residual.tensor;

/** A shape or value mismatch with a message written for players, not programmers. */
public final class ShapeException extends RuntimeException {
    public ShapeException(String message) {
        super(message);
    }
}
