package io.github.metrovoc.residual.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class VocabTest {
    private static final List<String> TEXT = text();

    private static List<String> text() {
        Map<Integer, BitSet> whole = new TreeMap<>();
        for (Archive.Record r : Archive.records()) {
            whole.put(r.id(), new BitSet());
        }
        return Archive.legible(whole);
    }

    private static Bpe learn(Vocab start, int size) {
        Bpe bpe = new Bpe(TEXT, start);
        while (bpe.vocab().size() < size && bpe.step()) {
        }
        return bpe;
    }

    @Test
    void wordsKeepTheSpaceBeforeThemAndDigitsStandAlone() {
        List<String> chunks = new ArrayList<>();
        Vocab.chunks("Hello, world!\n  42 \"hi\"", chunks::add);
        assertEquals(List.of("Hello", ",", " world", "!", "\n", " ", " ", "4", "2", " \"", "hi", "\""), chunks);
    }

    @Test
    void encodingSplitsTextTheWayLearningDid() {
        Bpe bpe = learn(Vocab.WORLD, 512);
        Vocab vocab = bpe.vocab();
        assertEquals(512, vocab.size());
        long chars = 0, tokens = 0;
        for (String t : TEXT) {
            int[] ids = vocab.encode(t);
            assertEquals(Vocab.WORLD.decode(Vocab.WORLD.spell(t)), vocab.decode(ids));
            chars += vocab.decode(ids).length();
            tokens += ids.length;
        }
        assertEquals(bpe.charsPerToken(), (double) chars / tokens, 1e-12);
        assertTrue(bpe.charsPerToken() > 2, "512 tokens should at least halve the text: " + bpe.charsPerToken());
    }

    @Test
    void aBiggerVocabularyKeepsEveryMergeOfASmallerOne() {
        Vocab small = learn(Vocab.WORLD, 200).vocab();
        Vocab big = learn(Vocab.WORLD, 300).vocab();
        assertEquals(small, big.first(200));
        assertEquals(big, learn(small, 300).vocab());
    }

    @Test
    void savedMergesComeBackTheSameAndBrokenOnesAreCut() {
        Vocab vocab = learn(Vocab.WORLD, 150).vocab();
        assertEquals(vocab, Vocab.of(vocab.mergeIds()));
        int[] broken = Arrays.copyOf(vocab.mergeIds(), vocab.mergeIds().length + 3);
        broken[broken.length - 3] = 9999;
        assertEquals(vocab, Vocab.of(broken));
        assertSame(Vocab.WORLD, Vocab.of(new int[0]));
        assertEquals("the", Vocab.WORLD.with(Vocab.WORLD.id('t'), Vocab.WORLD.id('h'))
                .with(Vocab.CHARACTERS, Vocab.WORLD.id('e')).piece(Vocab.CHARACTERS + 1));
    }
}
