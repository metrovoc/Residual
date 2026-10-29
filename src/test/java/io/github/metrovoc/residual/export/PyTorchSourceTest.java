package io.github.metrovoc.residual.export;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Wrecks;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The generated source without running Python (see {@link PyTorchParityTest} for that): it must always render. */
class PyTorchSourceTest {
    @Test
    void everyModelRenders() throws Exception {
        Env env = new Env(27, 16);
        List<GraphSpec> specs = List.of(Blueprints.bigram(), Blueprints.context(), Blueprints.recall(), Blueprints.reverse(),
                Blueprints.induction(), Blueprints.gpt(32, 2, 4), PyTorchParityTest.kitchenSink(),
                Wrecks.byId("leak").machine().get(), Wrecks.byId("depth").machine().get());
        for (GraphSpec spec : specs) {
            String py = PyTorch.module(CompiledGraph.compile(spec, env, null, 1), "abcdefghijklmnopqrstuvwxyz.", "test");
            assertTrue(py.contains("def forward(self, tokens)"), py);
        }
    }

    @Test
    void everyMachinePreviewRenders() {
        for (Op op : Op.values()) {
            for (int features : new int[]{-1, 16}) {
                for (Env env : List.of(new Env(0, 0), new Env(27, 16))) {
                    List<String> lines = PyTorch.preview(op, new Config(op), "", features, env);
                    assertFalse(lines.isEmpty(), op.id);
                    lines.forEach(line -> assertFalse(line.contains("null"), op.id + ": " + line));
                }
            }
        }
    }
}
