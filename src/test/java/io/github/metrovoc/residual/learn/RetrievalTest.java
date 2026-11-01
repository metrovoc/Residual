package io.github.metrovoc.residual.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RetrievalTest {
    @Test
    void everythingIsSaidTheWayATerminalAsks() {
        Random rng = new Random(1);
        for (int i = 0; i < 40000; i++) {
            Retrieval.Example ex = Retrieval.example(rng, i % 2 == 0);
            String prompt = Retrieval.prompt(ex.said());
            assertEquals(ex.said() + "? ", prompt, "said as a terminal says it: " + ex);
            assertTrue(prompt.length() + 2 < Vocab.CONTEXT, prompt);
            assertEquals(prompt.length(), Vocab.WORLD.spell(prompt).length, prompt);
            assertNotNull(Retrieval.Use.of(ex.use().letter));
        }
    }

    @Test
    void theTestAsksAboutThingsPracticeNeverNames() {
        Random rng = new Random(2);
        Set<String> practised = new HashSet<>();
        Set<String> tested = new HashSet<>();
        for (int i = 0; i < 200000; i++) {
            practised.add(Retrieval.example(rng, false).said());
            String t = Retrieval.example(rng, true).said();
            if (!Retrieval.known(t).isEmpty()) {
                tested.add(t);
            }
        }
        assertTrue(tested.size() > 40, "real things held out: " + tested.size());
        for (String t : tested) {
            assertFalse(practised.contains(t), t);
            assertEquals(1, Retrieval.known(t).size(), "a thing held out has one right answer: " + t);
        }
    }

    @Test
    void aThingAndAQueryFitAsFarAsTheyAgree() {
        int n = Retrieval.Use.values().length;
        float[] query = new float[n], lantern = new float[n], potato = new float[n], stone = new float[n], unknown = new float[n];
        query[Retrieval.Use.LIGHT.ordinal()] = 0.9f;
        query[Retrieval.Use.OTHER.ordinal()] = 0.1f;
        lantern[Retrieval.Use.LIGHT.ordinal()] = 0.95f;
        potato[Retrieval.Use.FOOD.ordinal()] = 0.5f;
        potato[Retrieval.Use.PLANT.ordinal()] = 0.5f;
        stone[Retrieval.Use.BUILD.ordinal()] = 0.99f;
        unknown[Retrieval.Use.OTHER.ordinal()] = 1f;
        assertTrue(Retrieval.fit(query, lantern) >= Retrieval.SURE);
        assertEquals(0, Retrieval.fit(query, stone), 1e-6);
        assertEquals(0, Retrieval.fit(query, unknown), 1e-6, "knowing nothing of both is no agreement");
        float[] eat = new float[n];
        eat[Retrieval.Use.FOOD.ordinal()] = 0.9f;
        assertTrue(Retrieval.fit(eat, potato) >= Retrieval.SURE, "a thing of two uses still fits each");
        assertEquals(0, Retrieval.fit(Retrieval.place(null, "火把"), lantern), 1e-6, "what it cannot read fits nothing");
    }

    @Test
    void aSearchIsAskedInPlainWords() {
        assertEquals("light a cave? ", Retrieval.prompt("Light a  CAVE"));
        assertTrue(Retrieval.known("torch").contains(Retrieval.Use.LIGHT));
        assertTrue(Retrieval.known("potato").contains(Retrieval.Use.FOOD) && Retrieval.known("potato").contains(Retrieval.Use.PLANT));
        assertTrue(Retrieval.known("no such thing").isEmpty());
    }
}
