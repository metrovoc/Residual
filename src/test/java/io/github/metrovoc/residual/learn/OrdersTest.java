package io.github.metrovoc.residual.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.agent.Program;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrdersTest {
    @Test
    void everyOrderMeansAProgramAgentsRun() {
        Random rng = new Random(1);
        for (int i = 0; i < 20000; i++) {
            Orders.Example ex = Orders.example(rng, i % 2 == 0);
            Program.Parsed p = Program.parse(ex.meant());
            assertTrue(p.ok(), ex + " → " + p.problem());
            assertEquals(ex.meant(), p.program().toString(), "written as agents write it");
            assertTrue(ex.said().length() <= Orders.LONGEST && ex.meant().length() <= Orders.WRITTEN, ex.toString());
            assertTrue((Orders.prompt(ex.said()) + ex.meant() + "\n").length() <= Vocab.CONTEXT + 1, "fits a window: " + ex);
            assertEquals(ex.said().strip(), Orders.clean(ex.said()), "said as a player could type it");
        }
    }

    @Test
    void theTestAsksForThingsPracticeNeverNames() {
        Random rng = new Random(2);
        Set<String> practised = new HashSet<>();
        for (int i = 0; i < 20000; i++) {
            things(Program.parse(Orders.example(rng, false).meant()).program().steps(), practised);
        }
        for (String thing : new String[]{"beetroot seeds", "cooked salmon", "apples", "carrots", "happy birthday"}) {
            assertFalse(practised.contains(thing), thing);
        }
        assertTrue(practised.contains("golden apples") && practised.contains("bread"));
    }

    /** The things and words named in {@code steps}. */
    private static void things(List<Program.Step> steps, Set<String> into) {
        for (Program.Step s : steps) {
            switch (s) {
                case Program.Act a -> into.add(a.thing());
                case Program.If i -> {
                    into.add(i.check().thing());
                    things(i.then(), into);
                    things(i.otherwise(), into);
                }
                case Program.Repeat r -> things(r.body(), into);
                case Program.Until u -> things(u.body(), into);
                case Program.WaitUntil w -> into.add(w.check().thing());
            }
        }
    }

    @Test
    void ordersComeInEveryShape() {
        Random rng = new Random(3);
        Set<String> shapes = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            String m = Orders.example(rng, true).meant();
            shapes.add(m.startsWith("if ") ? "if" : m.startsWith("wait until") ? "wait until" : m.startsWith("until") ? "until"
                    : m.startsWith("repeat") ? "repeat" : m.contains(", ") ? "two steps" : "one step");
        }
        assertEquals(Set.of("if", "wait until", "until", "repeat", "two steps", "one step"), shapes);
    }
}
