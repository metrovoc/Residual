package io.github.metrovoc.residual.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Curriculum;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Lays out every machine the mod builds and reads it back the way the world scanner does, on an abstract grid:
 * machines face +x, the Core sits at the origin facing +x, cables of one color join.
 */
class CaseLayoutTest {
    private static final int[][] STEPS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final CaseLayout.Cell CORE = new CaseLayout.Cell(0, 0, 0);

    static Stream<Arguments> machines() {
        List<Arguments> out = new ArrayList<>();
        for (Curriculum.Lesson l : Curriculum.ALL) {
            if (l.brokenMachine() != null) {
                out.add(Arguments.of(l.id(), l.brokenMachine().get()));
            }
        }
        out.add(Arguments.of("bigram", Blueprints.bigram()));
        out.add(Arguments.of("context", Blueprints.context()));
        out.add(Arguments.of("recall", Blueprints.recall()));
        out.add(Arguments.of("reverse", Blueprints.reverse()));
        out.add(Arguments.of("induction", Blueprints.induction()));
        out.add(Arguments.of("gpt", Blueprints.gpt(32, 2, 4)));
        return out.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("machines")
    void layoutReadsBackAsTheSameGraph(String name, GraphSpec spec) {
        CaseLayout layout = CaseLayout.of(spec);
        Map<CaseLayout.Cell, Integer> machineAt = new HashMap<>();
        layout.machines.forEach(m -> machineAt.put(m.at(), m.node()));
        Map<CaseLayout.Cell, Integer> colorAt = new HashMap<>();
        layout.cables.forEach(c -> colorAt.put(c.at(), c.color()));
        assertEquals(layout.cables.size(), colorAt.size(), "two cables in one cell");
        assertTrue(layout.cells().stream().noneMatch(CORE::equals), "something built inside the Core");

        // flood each cable network and find what drives it
        Map<CaseLayout.Cell, Integer> netOf = new HashMap<>();
        List<Integer> drivers = new ArrayList<>();
        for (CaseLayout.Cell start : colorAt.keySet()) {
            if (netOf.containsKey(start)) {
                continue;
            }
            int net = drivers.size();
            Set<Integer> driving = new HashSet<>();
            ArrayDeque<CaseLayout.Cell> todo = new ArrayDeque<>(List.of(start));
            netOf.put(start, net);
            while (!todo.isEmpty()) {
                CaseLayout.Cell c = todo.poll();
                for (int[] s : STEPS) {
                    CaseLayout.Cell n = new CaseLayout.Cell(c.x() + s[0], c.l() + s[1], c.y() + s[2]);
                    if (colorAt.containsKey(n) && colorAt.get(n).equals(colorAt.get(c)) && !netOf.containsKey(n)) {
                        netOf.put(n, net);
                        todo.add(n);
                    }
                    // a cable in front of a machine (step -x from the cable) is that machine's output
                    if (s[0] == -1 && machineAt.containsKey(n)) {
                        driving.add(machineAt.get(n));
                    }
                    if (s[0] == -1 && n.equals(CORE)) {
                        driving.add(GraphSpec.TOKENS);
                    }
                }
            }
            assertTrue(driving.size() <= 1, name + ": network " + net + " has drivers " + driving);
            drivers.add(driving.isEmpty() ? GraphSpec.MISSING : driving.iterator().next());
        }

        for (CaseLayout.Machine m : layout.machines) {
            GraphSpec.Node node = spec.node(m.node());
            CaseLayout.Cell p = m.at();
            if (node.op.arity >= 1) {
                assertEquals(node.inputs[0], source(drivers, netOf, List.of(new CaseLayout.Cell(p.x() - 1, p.l(), p.y()))),
                        name + ": main input of " + node.name);
            }
            List<CaseLayout.Cell> sides = List.of(new CaseLayout.Cell(p.x(), p.l() + 1, p.y()), new CaseLayout.Cell(p.x(), p.l() - 1, p.y()),
                    new CaseLayout.Cell(p.x(), p.l(), p.y() + 1), new CaseLayout.Cell(p.x(), p.l(), p.y() - 1));
            if (node.op.arity == 2) {
                assertEquals(node.inputs[1], source(drivers, netOf, sides), name + ": side input of " + node.name);
            }
        }
        List<CaseLayout.Cell> coreInputs = List.of(new CaseLayout.Cell(-1, 0, 0), new CaseLayout.Cell(0, 1, 0), new CaseLayout.Cell(0, -1, 0),
                new CaseLayout.Cell(0, 0, 1), new CaseLayout.Cell(0, 0, -1));
        assertEquals(spec.output, source(drivers, netOf, coreInputs), name + ": the Core's input");
    }

    /** The one value reaching a set of faces: MISSING if none, CONFLICT if they disagree. */
    private static int source(List<Integer> drivers, Map<CaseLayout.Cell, Integer> netOf, List<CaseLayout.Cell> cells) {
        int found = GraphSpec.MISSING;
        for (CaseLayout.Cell c : cells) {
            Integer net = netOf.get(c);
            if (net == null) {
                continue;
            }
            int d = drivers.get(net);
            if (found != GraphSpec.MISSING && found != d) {
                return GraphSpec.CONFLICT;
            }
            found = d;
        }
        return found;
    }
}
