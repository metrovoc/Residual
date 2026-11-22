package io.github.metrovoc.residual.interior;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchTest {
    private static CompiledGraph compile(Arch arch, Map<String, Map<String, Tensor>> bank, long seed) throws Exception {
        GraphSpec spec = arch.spec();
        return CompiledGraph.compile(spec, new Env(Vocab.WORLD, Vocab.CONTEXT), Arch.carried(spec, bank), seed);
    }

    @Test
    void everyArrangementCompiles() throws Exception {
        Arch arch = Arch.table();
        assertNotNull(compile(arch, Map.of(), 1));
        for (int width : Arch.WIDTHS) {
            Arch a = arch.withWidth(width).withPositions(true).insert(0, Arch.Kind.ATTENTION).insert(1, Arch.Kind.FEED_FORWARD);
            for (int heads : Arch.HEADS) {
                if (width % heads == 0) {
                    assertNotNull(compile(a.withHeads(heads), Map.of(), 1));
                    assertNotNull(compile(a.withHeads(heads).withNorm(false), Map.of(), 1));
                }
            }
        }
    }

    @Test
    void aPartSwitchedOffAndOnKeepsItsWeights() throws Exception {
        Arch arch = Arch.table().withPositions(true).insert(0, Arch.Kind.ATTENTION).insert(1, Arch.Kind.FEED_FORWARD);
        int attention = arch.parts().get(0).id();
        CompiledGraph first = compile(arch, Map.of(), 1);
        Map<String, Map<String, Tensor>> bank = new HashMap<>();
        Arch.keep(first, bank);

        Arch off = arch.toggle(attention);
        assertEquals(0, off.layers());
        CompiledGraph without = compile(off, bank, 2);
        assertEquals(-1, Arch.node(without.spec, Arch.part(attention)));
        Arch.keep(without, bank);

        CompiledGraph again = compile(off.toggle(attention), bank, 3);
        for (String name : new String[]{Arch.embed(), Arch.position(), Arch.part(attention), Arch.logits()}) {
            Map<String, Tensor> was = first.params(Arch.node(first.spec, name));
            Map<String, Tensor> now = again.params(Arch.node(again.spec, name));
            assertTrue(!was.isEmpty(), name);
            was.forEach((key, tensor) -> assertArrayEquals(tensor.data, now.get(key).data, name + "." + key));
        }
    }

    @Test
    void aNewWidthStartsAgain() throws Exception {
        Arch arch = Arch.table();
        CompiledGraph first = compile(arch, Map.of(), 1);
        Map<String, Map<String, Tensor>> bank = new HashMap<>();
        Arch.keep(first, bank);
        CompiledGraph wider = compile(arch.withWidth(32), bank, 1);
        assertEquals(32, wider.params(Arch.node(wider.spec, Arch.embed())).values().iterator().next().shape[1]);
    }

    @Test
    void partsKeepTheirNumbersAndSurviveSaving() {
        Arch arch = Arch.table().insert(0, Arch.Kind.ATTENTION).insert(0, Arch.Kind.FEED_FORWARD).insert(1, Arch.Kind.ATTENTION);
        assertEquals(3, arch.parts().stream().map(Arch.Part::id).distinct().count());
        Arch fewer = arch.remove(arch.parts().get(0).id()).toggle(arch.parts().get(2).id());
        assertEquals(fewer, Arch.load(fewer.save()));
        Arch full = arch;
        while (full.parts().size() < Arch.MAX_PARTS) {
            full = full.insert(0, Arch.Kind.FEED_FORWARD);
        }
        assertNull(full.insert(0, Arch.Kind.ATTENTION));
    }

    @Test
    void theHallFollowsTheParts() {
        Arch arch = Arch.table().withPositions(true).insert(0, Arch.Kind.ATTENTION).insert(1, Arch.Kind.FEED_FORWARD);
        HallPlan plan = HallPlan.of(arch, 12);
        assertEquals(2, plan.pitch());
        assertEquals(HallPlan.WALK + 24 + HallPlan.MARGIN, plan.width());
        int z = 0;
        for (HallPlan.Station s : plan.stations()) {
            assertEquals(z, s.z());
            z = s.end();
        }
        assertEquals(z, plan.length());
        assertEquals(3, plan.stations().stream().filter(s -> s.type() == HallPlan.Type.BAY).count());
        assertNotNull(plan.part(arch.parts().get(1).id()));
        assertEquals(1, HallPlan.of(arch, 64).pitch());
    }
}
