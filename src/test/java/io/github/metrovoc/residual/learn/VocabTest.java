package io.github.metrovoc.residual.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VocabTest {
    @Test
    void wordsKeepTheSpaceBeforeThemAndDigitsStandAlone() {
        List<String> chunks = new ArrayList<>();
        Vocab.chunks("Hello, world!\n  42 \"hi\"", chunks::add);
        assertEquals(List.of("Hello", ",", " world", "!", "\n", " ", " ", "4", "2", " \"", "hi", "\""), chunks);
    }

    /** Merges are kept counted from the characters text is read in, so what was saved when the alphabet was shorter reads the same. */
    @Test
    void whatWasSavedBeforeTheAlphabetGrewReadsTheSame() {
        int t = Vocab.WORLD.id('t'), h = Vocab.WORLD.id('h'), e = Vocab.WORLD.id('e');
        Vocab vocab = Vocab.WORLD.with(t, h).with(Vocab.CHARACTERS, e);
        assertEquals("the", vocab.piece(Vocab.CHARACTERS + 1));
        // as a tokenizer from before the program characters kept it: the first merge is token 78
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{t, h, Vocab.TEXT, e}, vocab.saved());
        assertEquals(vocab, Vocab.ofSaved(new int[]{t, h, Vocab.TEXT, e}));
        assertEquals(vocab, Vocab.ofSaved(vocab.saved()));
    }

    /** Programs can be spelled, but text is read as it always was, and no merge ever holds a program's character. */
    @Test
    void programCharactersAreForProgramsOnly() {
        for (char c : "$@#[]<+*/%_".toCharArray()) {
            assertTrue(Vocab.WORLD.has(c), "spelled: " + c);
            assertFalse(Vocab.WORLD.reads(c), "not in text: " + c);
        }
        assertEquals(Vocab.TEXT + 11, Vocab.CHARACTERS);
        assertEquals(2, Vocab.WORLD.encode("1%2").length);
        assertEquals("$x", Vocab.WORLD.decode(Vocab.WORLD.spell("$x")));
        assertEquals("100 percent", Vocab.WORLD.decode(Vocab.WORLD.encode("100 per%cent")));
    }
}
