package io.github.metrovoc.residual.model;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.learn.Blueprints;
import org.junit.jupiter.api.Test;

class ModelsTest {
    private CompiledGraph model() throws Exception {
        return CompiledGraph.compile(Blueprints.bigram(), Env.WORLD, null, 1);
    }

    @Test
    void successfulWorkReturnsItsAnswer() throws Exception {
        CompiledGraph model = model();
        Object answer = new Object();
        assertSame(answer, Models.evaluate(model, received -> {
            assertSame(model, received);
            return answer;
        }));
    }

    @Test
    void inferenceFailureReturnsNull() throws Exception {
        IllegalStateException failure = new IllegalStateException("inference failed");
        failure.setStackTrace(new StackTraceElement[0]);
        assertNull(Models.evaluate(model(), model -> {
            throw failure;
        }));
    }

    @Test
    void missingModelDoesNotRunTheWork() {
        assertNull(Models.evaluate(null, model -> {
            throw new AssertionError("work must not run without a model");
        }));
    }
}
