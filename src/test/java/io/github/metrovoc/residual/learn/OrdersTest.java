package io.github.metrovoc.residual.learn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.agent.Program;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
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
            assertEquals(ex.meant(), Program.parse(p.program().pretty()).program().toString(), "read back on lines");
            assertTrue(ex.said().length() <= Orders.LONGEST && ex.meant().length() <= Orders.WRITTEN, ex.toString());
            assertEquals(ex.said().strip(), Orders.clean(ex.said()), "said as a player could type it");
        }
    }

    /** Every letter of a program is scored once, in the very window a model writing it would read. */
    @Test
    void aWindowIsWhatAModelWritingTheProgramWouldRead() {
        Random rng = new Random(4);
        int longest = 0;
        for (int i = 0; i < 2000; i++) {
            Orders.Example ex = Orders.example(rng, i % 2 == 0);
            String prompt = Orders.prompt(ex.said()), written = ex.meant() + "\n";
            longest = Math.max(longest, written.length());
            int letter = 0;
            for (Orders.Window w : Orders.windows(ex)) {
                assertTrue(w.text().length() <= Vocab.CONTEXT + 1, ex.toString());
                assertTrue(w.text().startsWith(prompt), "the order is in view");
                for (int at = w.scoredFrom(); at < w.scoredTo(); at++, letter++) {
                    assertEquals(written.charAt(letter), w.text().charAt(at), ex + " letter " + letter);
                    // what the model reads to write this letter, as it would when writing
                    int[] read = Sampler.window(Vocab.WORLD.spell(prompt + written.substring(0, letter)), prompt.length());
                    assertArrayEquals(Vocab.WORLD.spell(w.text().substring(0, at)), read, ex + " letter " + letter);
                }
            }
            assertEquals(written.length(), letter, "every letter is scored, once: " + ex);
        }
        assertTrue(longest > Vocab.CONTEXT, "programs longer than a window: " + longest);
    }

    /** The Try tab writes one program, as an agent would, keeping the order in view. */
    @Test
    void tryingAnOrderWritesOneProgram() throws Exception {
        CompiledGraph model = CompiledGraph.compile(Blueprints.gpt(8, 1, 1), new Env(Vocab.WORLD, Vocab.CONTEXT), null, 3);
        String order = "move 16 cobblestone from mark 1 to mark 2";
        Sampler.Demo demo = Sampler.demo(model, Benchmark.ORDERS, order, 0f, new Random(1));
        assertEquals(Orders.prompt(order), demo.prompt());
        assertTrue(demo.text().startsWith(demo.prompt()), demo.text());
        assertFalse(demo.text().substring(demo.prompt().length()).contains("\n"), demo.text());
        assertTrue(demo.text().length() <= demo.prompt().length() + Orders.WRITTEN + 1, demo.text());
    }

    @Test
    void ordersAreSaidInTheWholeLanguage() {
        Random rng = new Random(5);
        Set<String> said = new HashSet<>();
        int longer = 0, n = 5000;
        for (int i = 0; i < n; i++) {
            String m = Orders.example(rng, true).meant();
            longer += m.length() > 40 ? 1 : 0;
            for (String form : List.of("from ", "at box(", "wall(", "sphere(", "tag(", "except ", "where ", "kind(", " or ", "for each ", "to ", "on ", "while ", "set $", "entities(", "pump ", "charge ", "if count(")) {
                if (m.contains(form)) {
                    said.add(form);
                }
            }
        }
        assertEquals(18, said.size(), said.toString());
        assertTrue(longer > n / 10, "programs over 40 letters: " + longer);
    }

    @Test
    void oneStepNeedsNoReadingOnlyWhenNothingSaysWhereOrHow() {
        for (String now : List.of("fetch bread", "come", "go 3", "mine 4 stone", "stay", "repeat (farm)".substring(8, 12))) {
            assertTrue(Program.parse(now).program().atOnce(), now);
        }
        for (String read : List.of("void 8 coal", "dig at box(mark(1), mark(2))", "send coal from 1 to 2", "attack at entities(8, zombies)", "retire", "take 2 coal, come", "to a (come), a")) {
            assertFalse(Program.parse(read).program().atOnce(), read);
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
                default -> {
                }
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
