package io.github.metrovoc.residual.compute;

import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;

/**
 * A way of splitting a model's training across devices: a forward pass through it gives the logits as one device would,
 * and their backward pass, recorded on the tape, leaves the gradients in the model's own parameters as one device would.
 * So the trainer does not know it is there.
 */
public interface Split extends AutoCloseable {
    Tensor forward(Tape tape, Tokens tokens);

    /** How the last whole step ran, or null before there was one. */
    Timing last();

    int devices();

    @Override
    void close();
}
