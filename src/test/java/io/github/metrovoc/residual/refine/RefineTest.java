package io.github.metrovoc.residual.refine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.TextTask;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Vocab;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RefineTest {
    private static String prose(long seed, int words) {
        Random rng = new Random(seed);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words; i++) {
            out.append(i == 0 ? "" : " ");
            for (int k = 0; k < 4; k++) {
                out.append((char) ('a' + rng.nextInt(26)));
            }
        }
        return out.toString();
    }

    private static Doc doc(String text) {
        return new Doc(text, Domain.LETTERS);
    }

    @Test
    void minHashAgreesAsOftenAsDocsShareRuns() {
        long[][] mix = new long[Duplicates.HASHES][2];
        Random rng = new Random(9);
        for (long[] a : mix) {
            a[0] = rng.nextLong() | 1;
            a[1] = rng.nextLong();
        }
        String[] words = prose(1, 2000).split(" ");
        // the same words, the second half of the second document different
        String[] other = words.clone();
        String[] fresh = prose(2, 2000).split(" ");
        System.arraycopy(fresh, 1000, other, 1000, 1000);
        long[] a = Duplicates.runs(doc(String.join(" ", words))), b = Duplicates.runs(doc(String.join(" ", other)));
        double truth = Duplicates.jaccard(a, b);
        assertEquals(1.0 / 3, truth, 0.01, "half shared of each is a third of both");
        int[] sa = Duplicates.signature(a, mix), sb = Duplicates.signature(b, mix);
        int agree = 0;
        for (int i = 0; i < sa.length; i++) {
            agree += sa[i] == sb[i] ? 1 : 0;
        }
        // 128 draws of a coin of 1/3: well within 0.15 almost always, and this draw is fixed
        assertEquals(truth, agree / (double) Duplicates.HASHES, 0.15);
    }

    @Test
    void chanceFollowsTheBands() {
        assertEquals(1 - Math.pow(1 - Math.pow(0.5, 4), 32), Duplicates.chance(1, 0.5), 1e-12);
        assertTrue(Duplicates.chance(2, 0.3) < 0.02, "far below the threshold, hardly ever compared");
        assertTrue(Duplicates.chance(2, 0.9) > 0.99, "far above it, nearly always");
    }

    @Test
    void takesOutCopiesAndNearCopiesAndKeepsTheLonger() {
        String base = prose(3, 400);
        String near = base + " " + prose(4, 12);
        String other = prose(5, 400);
        List<Doc> docs = List.of(doc(base), doc(other), doc(other), doc(near));
        Duplicates.Result r = Duplicates.run(docs, 1);
        assertEquals(1, r.exactDocs());
        assertEquals(1, r.nearDocs());
        assertEquals(2, r.kept().size());
        assertTrue(r.kept().stream().anyMatch(d -> d.parts().getFirst().equals(near)), "of two near copies, the longer stays");
    }

    @Test
    void unrelatedDocsAreNotCopies() {
        List<Doc> docs = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            docs.add(doc(prose(100 + i, 200)));
        }
        Duplicates.Result r = Duplicates.run(docs, 0);
        assertEquals(40, r.kept().size());
        assertEquals(0, r.nearDocs());
    }

    @Test
    void longLinesSeenBeforeGoAndShortOnesStay() {
        String line = "This line is long enough to count as a repeat when it comes round.";
        Duplicates.Result r = Duplicates.run(List.of(doc("Dear Oren,\n" + line + "\nfirst"), doc("Dear Oren,\n" + line + "\nsecond")), 3);
        assertEquals(1, r.lines());
        assertEquals("Dear Oren,\nsecond", r.kept().get(1).parts().getFirst());
    }

    @Test
    void eachRuleThrowsOutWhatItSays() {
        String good = "The road is shut, so I will write this a little at a time and send it when the snow goes.\nI have kept the lamp lit.\nTell Tam I am well.";
        assertEquals(null, Filters.fails(doc(good), 31));
        assertEquals(Filters.Rule.SHORT, Filters.fails(doc("too few words."), 31));
        assertEquals(Filters.Rule.SYMBOLS, Filters.fails(doc(". - . - . - . - . - . - a b c d e f g h i j k l"), 31));
        assertEquals(Filters.Rule.WORDS, Filters.fails(doc("a b c d e f g h i j k l m n o p q r s t"), 31));
        assertEquals(null, Filters.fails(doc("a b c d e f g h i j k l m n o p q r s t"), 31 & ~(1 << Filters.Rule.WORDS.ordinal())));
        assertEquals(Filters.Rule.REPEATS, Filters.fails(doc("the same line again.\n".repeat(5) + "and another line here.\none more thing to say."), 31));
        assertEquals(Filters.Rule.ENDINGS, Filters.fails(doc("no ending here at all\n".repeat(3) + "another line that stops\nyet another of them here\nand this makes six of them"), 31));
    }

    @Test
    void theRuleSeesLettersGoWrongAndNotWordsGoOutOfOrder() {
        Archive.Record r = Archive.records().get(40);
        assertTrue(Quality.ruleGood(doc(r.text())), "the archive's own words are words");
        Random rng = new Random(3);
        assertFalse(Quality.ruleGood(doc(Dirt.garble(r.text(), 0.3, rng))));
        assertTrue(Quality.ruleGood(doc(Dirt.scramble(r.text(), rng))), "every word is still a word");
    }

    @Test
    void qualityJudgesRecordsNoShardCarries() {
        List<String> tested = Quality.testTexts();
        assertTrue(tested.size() > 100, "enough long records: " + tested.size());
        Set<String> heldOut = new HashSet<>();
        for (Archive.Record r : Archive.records()) {
            if (Archive.heldOut(r.id())) {
                heldOut.add(r.text());
            }
        }
        assertTrue(heldOut.containsAll(tested));
        Quality.Reader sure = new Quality.Reader(1.5f, 0.1f);
        assertTrue(sure.good(1.0f) > 0.99f);
        assertEquals(0.5f, sure.good(1.5f), 1e-5);
        assertTrue(sure.good(2.0f) < 0.01f);
    }

    @Test
    void aReaderIsMoreSurprisedByWhatABadLineHasBeenAt() throws Exception {
        CompiledGraph model = CompiledGraph.compile(Blueprints.context(), Env.WORLD, null, 1);
        List<String> texts = new ArrayList<>();
        for (Archive.Record r : Archive.records().subList(0, 200)) {
            texts.add(r.text());
        }
        Trainer trainer = new Trainer(model, new TextTask(texts, Benchmark.READING.target, Vocab.WORLD), 1, 3e-3f, 16, 1f);
        for (int s = 0; s < 150; s++) {
            trainer.train();
        }
        String text = Quality.testTexts().getFirst();
        float[] seen = Quality.surprise(model, List.of(doc(text), doc(Dirt.garble(text, 0.3, new Random(1))), doc("too short to read")));
        assertTrue(seen[1] > seen[0] + 0.1, seen[0] + " then " + seen[1]);
        assertTrue(Float.isNaN(seen[2]), "too little to read");
        double right = Benchmark.QUALITY.test(Vocab.WORLD).measure(model)[1];
        assertTrue(right > 0.5 && right <= 1, "a test it scores: " + right);
    }

    @Test
    void dumpsAreTheSameEachTimeAndHoldNothingHeldOut() {
        List<Dump.Piece> a = Dump.read(11), b = Dump.read(11);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).doc().parts(), b.get(i).doc().parts());
        }
        assertNotEquals(Dump.read(12).getFirst().doc().parts(), a.getFirst().doc().parts());
        Set<String> heldOut = new HashSet<>();
        for (Archive.Record r : Archive.records()) {
            if (Archive.heldOut(r.id())) {
                heldOut.add(r.text());
            }
        }
        for (Dump.Piece p : a) {
            for (String part : p.doc().parts()) {
                for (String h : heldOut) {
                    assertFalse(part.contains(h), "a dump holds only what shards can carry");
                }
            }
        }
    }

    @Test
    void theSieveTakesOutADumpsRepeatsAndTheFiltersItsCarrier() {
        List<Dump.Piece> pieces = Dump.read(13);
        List<Doc> docs = new ArrayList<>();
        long copied = 0, resent = 0, clean = 0;
        for (Dump.Piece p : pieces) {
            docs.add(p.doc());
            copied += p.fate() == Dump.Fate.COPIED ? p.doc().chars() : 0;
            resent += p.fate() == Dump.Fate.RESENT ? p.doc().chars() : 0;
            clean += p.fate() == Dump.Fate.CLEAN ? p.doc().chars() : 0;
        }
        Duplicates.Result r = Duplicates.run(docs, 1);
        assertTrue(copied > 0 && resent > 0);
        assertTrue(r.exactChars() >= copied * 0.8, r.exactChars() + " of " + copied);
        assertTrue(r.nearChars() >= resent * 0.8, r.nearChars() + " of " + resent);
        assertTrue(Doc.chars(r.kept()) >= clean * 0.9, "what came through clean stays");
        Filters.Result f = Filters.run(r.kept(), 31);
        for (Dump.Piece p : pieces) {
            if (p.fate() == Dump.Fate.CARRIER) {
                assertFalse(f.kept().contains(p.doc()), "carrier is thrown out");
            }
        }
    }

    @Test
    void contaminationFindsWhatEachTestHoldsAndNothingElse() {
        // a line of Orders' own test, as text; lines practice shows too do not count
        List<String> held = Benchmark.ORDERS.test(Vocab.WORLD).heldOutTexts();
        String line = null;
        for (String text : held) {
            for (String l : text.split("\n")) {
                if (l.startsWith("= fetch ") && l.length() > 12 && line == null && Contamination.run(List.of(doc(l)), false).findings().get(Benchmark.ORDERS.ordinal()).found() > 0) {
                    line = l;
                }
            }
        }
        assertTrue(line != null, "some fetch line belongs to the test alone");
        String text = "Some writing.\n> go on then\n" + line + "\nMore writing after it.";
        Contamination.Result r = Contamination.run(List.of(doc(text), doc("Nothing to see in this one at all.")), true);
        assertEquals(1, r.findings().get(Benchmark.ORDERS.ordinal()).found());
        assertEquals("Some writing.\nMore writing after it.", r.kept().getFirst().parts().getFirst(), "what was said goes with what was meant by it");
        assertEquals(2, r.lines());
        // a stretch of a held-out record, word for word
        Archive.Record out = Archive.records().stream().filter(x -> Archive.heldOut(x.id()) && x.text().length() > 500).findFirst().orElseThrow();
        String stretch = out.text().substring(100, 400);
        Contamination.Result copied = Contamination.run(List.of(doc(stretch)), true);
        assertTrue(copied.findings().get(Benchmark.READING.ordinal()).found() >= 1);
        assertTrue(copied.lines() > 0);
        assertEquals(0, Contamination.run(List.of(doc(prose(77, 300))), true).lines());
        assertTrue(r.findings().get(Benchmark.CONTROL.ordinal()).played(), "a test played has nothing written to find");
    }

    @Test
    void programsWriteWhatTheyAreAskedAndChoicesComeOutOneAtATime() {
        List<Doc> sums = Programs.write(Programs.Program.SUMS, 5000, List.of(), 1);
        assertTrue(Doc.chars(sums) >= 5000);
        assertTrue(sums.getFirst().parts().getFirst().contains("Working:"));
        Random rng = new Random(2);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(Programs.expand("the {miller|baker {sold|bought}} a {loaf|jar}", rng));
        }
        assertEquals(6, seen.size(), seen.toString());
        assertTrue(seen.contains("the baker bought a jar"));
        assertEquals("an open {brace", Programs.expand("an open {brace", rng));
        assertEquals(0, Programs.write(Programs.Program.BOOK, 100, List.of(), 1).size(), "no book, nothing written");
        assertTrue(Doc.chars(Programs.write(Programs.Program.BOOK, 100, List.of("{a|b} c"), 1)) >= 100);
        // what orders and sorting write draws only on what practice shows
        for (Programs.Program p : List.of(Programs.Program.ORDERS, Programs.Program.SORTING)) {
            List<Doc> docs = Programs.write(p, 200_000, List.of(), 3);
            Contamination.Result r = Contamination.run(docs, false);
            // made-up names can come up in both by chance, and no more than that
            int found = r.findings().get(Benchmark.ORDERS.ordinal()).found() + r.findings().get(Benchmark.SORTING.ordinal()).found();
            assertTrue(found < 40, p + " lets next to nothing of a test in: " + found);
        }
    }

    @Test
    void aSupplyDrawsByItsMixAndAnneals() {
        List<Doc> docs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            docs.add(new Doc(prose(200 + i, 400), Domain.LETTERS));
            docs.add(new Doc(prose(300 + i, 400), Domain.TALES));
        }
        float[] mix = {1f, 3f, 1f, 1f, 1f, 1f, 1f};
        float[] shares = new Supply(docs, mix, Supply.NO_ANNEAL, 0).shares();
        assertEquals(0.75f, shares[Domain.TALES.ordinal()], 0.05f);
        SupplyTask task = new SupplyTask(new Supply(docs, new float[]{1, 1, 1, 1, 1, 1, 1}, Domain.TALES.ordinal(), 5), Vocab.WORLD);
        Random rng = new Random(1);
        for (int i = 0; i < 5; i++) {
            assertFalse(task.annealing());
            assertEquals(1f, task.rateScale());
            task.batch(rng, 2, false);
        }
        assertTrue(task.annealing());
        assertEquals(Supply.ANNEAL_RATE, task.rateScale());
        // tests never count as steps, and while it anneals practice reads the one domain
        task.batch(rng, 2, true);
        assertEquals(5, task.served());
        Set<String> tales = new HashSet<>();
        docs.stream().filter(d -> d.domain() == Domain.TALES).forEach(d -> tales.addAll(List.of(d.parts().getFirst().split(" "))));
        for (int i = 0; i < 20; i++) {
            String row = Task.decode(task.batch(rng, 1, false), Vocab.WORLD).getFirst();
            String[] words = row.split(" ");
            assertTrue(tales.contains(words[1]), row);
        }
        SupplyTask next = task.fresh();
        assertEquals(0, next.served());
        next.carryOn(task);
        assertTrue(next.annealing());
    }

    @Test
    void aSupplySurvivesItsFile(@TempDir Path dir) throws IOException {
        Supply s = new Supply(List.of(new Doc(List.of("one stretch", "and another"), Domain.TOWN), new Doc("a third", Domain.PROGRAMS)),
                new float[]{1, 2, 3, 4, 5, 6, 7}, 2, 4000);
        Path file = dir.resolve("a").resolve("supply.dat");
        Supplies.write(file, s);
        assertTrue(Files.exists(file));
        Supply back = Supplies.read(file);
        assertEquals(s.docs(), back.docs());
        assertEquals(2, back.anneal());
        assertEquals(4000, back.after());
        assertEquals(7f, back.mix()[6]);
    }
}
