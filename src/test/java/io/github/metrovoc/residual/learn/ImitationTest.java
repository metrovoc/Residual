package io.github.metrovoc.residual.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ImitationTest {
    private static final String LINE = "Zj1j1i1i1h5h0h0h0h5h0h0h0h5h0h0h0h5h0h0h0";

    @Test
    void howThingsStandIsOneLetterForEachBandAndFlag() {
        char far = Imitation.state(40, false, false);
        assertEquals('f', far);
        assertEquals('a', Imitation.state(1, false, false));
        assertEquals('g', Imitation.state(1, true, false));
        assertEquals('m', Imitation.state(1, false, true));
        assertEquals('x', Imitation.state(40, true, true));
        assertTrue(Vocab.WORLD.has('x'));
    }

    @Test
    void aPromptNeverOutgrowsWhatAModelReadsAndKeepsItsKind() {
        Bout bout = new Bout('Z', true, new Random(1));
        Random rng = new Random(2);
        while (!bout.over()) {
            String prompt = bout.prompt();
            assertTrue(prompt.length() <= Vocab.CONTEXT, prompt);
            assertEquals('Z', prompt.charAt(0));
            // a state, then pairs of a state and an action: it ends on how things stand now
            assertEquals(bout.state(), prompt.charAt(prompt.length() - 1));
            bout.act(Imitation.ACTIONS.charAt(rng.nextInt(Imitation.ACTIONS.length())), rng);
        }
        assertEquals(Bout.LOOKS, bout.look());
    }

    @Test
    void aBoutComesToBlowsAndStaysOnItsFloor() {
        Random rng = new Random(3);
        Bout bout = new Bout('Z', true, rng);
        boolean struck = false;
        while (!bout.over()) {
            bout.act(Imitation.CLOSER, rng);
            struck |= bout.struck();
            assertTrue(bout.distance() >= 1 && bout.distance() <= 12);
        }
        assertTrue(struck, "a mob that comes on gets hit");
    }

    @Test
    void tiersFollowHowFarAModelGetsBetweenTheTables() {
        assertEquals(0, Imitation.tier(0));
        assertEquals(1, Imitation.tier(Imitation.TIERS[0]));
        assertEquals(Imitation.TIERS.length, Imitation.tier(1));
        for (int i = 1; i < Imitation.TIERS.length; i++) {
            assertTrue(Imitation.TIERS[i] > Imitation.TIERS[i - 1]);
        }
        // a plain table counts what most often followed each letter: here 1 after a, 5 after g
        String read = "Za1a1a1a1a1a2a1a1a1a1g5g5g5g0g5g5g5g5g5g5g5g5g5g5";
        String kept = "Za1a1a1a1a1a1a1a1a1a1g5g5g0g0g5g5g0g0g5g5";
        // from the ninth look on: two a1, right, then ten of g, six of them g5, right
        assertEquals(8 / 12.0, Imitation.plainTable(List.of(read), List.of(kept)), 1e-9);
        assertEquals(0, new Imitation.Check(5000, 0.5, 0.5, 0.75).tier());
        assertEquals(1, new Imitation.Check(5000, 0.75, 0.5, 0.75).reach(), 1e-9);
        assertEquals(Imitation.TIERS.length, new Imitation.Check(5000, 0.9, 0.5, 0.75).tier());
        // however little reading back is worth on a mob, a model does not earn a tier for a hair's width
        assertEquals(0, new Imitation.Check(5000, 0.901, 0.9, 0.901).tier());
        // and however well it does on a short log, that log vouches for so much and no more
        assertEquals(1, new Imitation.Check(Imitation.LOOKS_FOR[0], 0.9, 0.5, 0.75).tier());
        assertEquals(2, new Imitation.Check(Imitation.LOOKS_FOR[1], 0.9, 0.5, 0.75).tier());
        assertFalse(Imitation.byPlayer(1));
        assertTrue(Imitation.byPlayer(2));
        assertEquals(0, Imitation.looting(2));
        assertEquals(3, Imitation.looting(Imitation.TIERS.length));
    }

    /** Every third action a blow, whatever the letter says: only a table that reads back can tell when, and it must. */
    @Test
    void aTableThatReadsBackFindsARhythmThePlainOneCannot() {
        StringBuilder line = new StringBuilder("Z");
        for (int k = 0; k < 30; k++) {
            line.append('g').append(k % 3 == 0 ? '5' : '0');
        }
        List<String> read = List.of(line.toString()), kept = List.of(line.toString());
        assertEquals(15 / 22.0, Imitation.plainTable(read, kept), 1e-9);
        assertEquals(1, Imitation.readBackTable(read, kept), 1e-9);
    }

    @Test
    void aTenthOfALogIsKeptFromPracticeHoweverItGrows() {
        Random rng = new Random(5);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 4000; i++) {
            StringBuilder line = new StringBuilder(LINE);
            for (int k = 0; k < 8; k++) {
                line.append((char) ('a' + rng.nextInt(24))).append(Imitation.ACTIONS.charAt(rng.nextInt(8)));
            }
            lines.add(line.toString());
        }
        List<String> kept = Imitation.heldOut(lines);
        assertEquals(400, kept.size(), 60);
        assertEquals(4000, kept.size() + Imitation.practised(lines).size());
        // a line stays on its side when older lines fall away
        assertEquals(Imitation.heldOut(lines.subList(1000, 4000)), kept.stream().filter(l -> lines.indexOf(l) >= 1000).toList());
        assertEquals(kept.size() * ((LINE.length() + 16 - 1) / 2 - Imitation.SHOWN), Imitation.scorable(kept));
        assertTrue(Imitation.targeting(LINE));
        assertFalse(Imitation.targeting("Wc0c0c0c0c0c0c0c0c0c0c0c0"));
    }

    /** The recorded lines are real and read as the format says; a model that reads only the last letter cannot pass on them. */
    @Test
    void theRecordedLinesScoreAModelFairly() throws Exception {
        Imitating task = new Imitating();
        assertTrue(task.batch(new Random(1), 4, true).weights().length > 0);
        CompiledGraph g = CompiledGraph.compile(Blueprints.bigram(), new Env(Vocab.WORLD, Vocab.CONTEXT), null, 1);
        Trainer tr = new Trainer(g, task, 1, 3e-3f, 32, 1f);
        for (int s = 0; s < 400; s++) {
            tr.train();
        }
        double score = task.measure(g)[1];
        assertTrue(score > 1.0 / Imitation.ACTIONS.length(), "better than picking at random: " + score);
        assertTrue(score < Benchmark.IMITATION.target, "a table of last letters does not pass: " + score);
        assertTrue(Trainer.isCausal(g, task));
    }

    /** A model that reads only the last letter is the plain table over again, on a real mob's lines, and earns nothing. */
    @Test
    void aTableOfLettersEarnsNoLikeness() throws Exception {
        String all;
        try (InputStream in = Imitating.class.getResourceAsStream("/imitation/lines.txt")) {
            all = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(13, all.lines().map(l -> l.charAt(0)).distinct().count(), "the kinds the field notes count");
        String zombies = all.lines().filter(l -> l.charAt(0) == Imitation.letter("minecraft:zombie")).collect(Collectors.joining("\n"));
        CompiledGraph g = CompiledGraph.compile(Blueprints.bigram(), new Env(Vocab.WORLD, Vocab.CONTEXT), null, 1);
        Trainer tr = new Trainer(g, new Imitating(zombies, Vocab.WORLD), 1, 3e-3f, 32, 1f);
        for (int s = 0; s < 2000; s++) {
            tr.train();
        }
        Imitation.Check check = Imitation.check(g, Imitation.lines(zombies));
        assertEquals(0, check.tier(), check.toString());
        assertTrue(check.readBack() > check.plain(), check.toString());
    }

    @Test
    void aMobLogPractisesAndChecksOnItsOwnLines() throws Exception {
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            // one look more or less, so that some of them are kept from practice
            log.append(LINE, 0, LINE.length() - 2 * (i % 6)).append('\n');
        }
        Imitating task = new Imitating(log.toString(), Vocab.WORLD);
        assertFalse(task.isEmpty());
        CompiledGraph g = CompiledGraph.compile(Blueprints.reverse(), new Env(Vocab.WORLD, Vocab.CONTEXT), null, 2);
        Trainer tr = new Trainer(g, task, 2, 3e-3f, 16, 1f);
        for (int s = 0; s < 150; s++) {
            tr.train();
        }
        // every line alike here, so either all of them are held out or none: score them outright
        long[] score = Imitation.score(g, Imitation.lines(log.toString()));
        assertTrue(score[0] > 0);
        assertTrue(score[1] > score[0] * 0.8, "a rhythm this plain is learned: " + score[1] + " of " + score[0]);
        // and what it asks of a model comes back as one chance for each action
        float[][] probs = Imitation.actions(g, List.of(Imitation.prompt("Zh5h0h0", 'h'), Imitation.prompt("Zj1", 'j')));
        assertEquals(2, probs.length);
        float sum = 0;
        for (float p : probs[0]) {
            sum += p;
        }
        assertEquals(1f, sum, 1e-3f);
    }

    @Test
    void groundIsWrittenInTheLettersOfGroundAndTrustedOnlyAsFarAsItIsThere() throws Exception {
        CompiledGraph g = CompiledGraph.compile(Blueprints.bigram(), new Env(Vocab.WORLD, Vocab.CONTEXT), null, 3);
        Trainer tr = new Trainer(g, Dataset.of(Benchmark.GROUND), 3, 3e-3f, 32, 1f);
        for (int s = 0; s < 300; s++) {
            tr.train();
        }
        String[] rows = Ground.draw(g, 8, new Random(4));
        assertEquals(8, rows.length);
        for (String row : rows) {
            assertEquals(Survey.ROW, row.length());
            for (char c : row.toCharArray()) {
                assertTrue((Ground.ORES + "SDTAWLB").indexOf(c) >= 0, row);
            }
        }
        // ground with iron in it and nothing else: the iron can be trusted at most as far as it is there, and nothing else at all
        List<String> real = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            real.add(i % 10 == 0 ? "SSSIISSSSSSSSSSS" : "SSSSSSSSSSSSSSSS");
        }
        float[] trust = Ground.trust(g, real, new Random(5));
        assertEquals(0f, trust[Ground.ORES.indexOf('X')]);
        assertTrue(trust[Ground.ORES.indexOf('I')] > 0);
        // a Surveyor's rows are practice from their first block on
        String log = String.join("\n", real) + "\n";
        Trainer onLog = new Trainer(g, new FieldTask(log, Vocab.WORLD), 3, 3e-3f, 32, 1f);
        float first = onLog.train(), last = first;
        for (int s = 0; s < 200; s++) {
            last = onLog.train();
        }
        assertTrue(last < first, first + " to " + last);
        assertTrue(Ground.isLog("SSSSSSSSSSSSSSSS\n"));
        assertFalse(Ground.isLog("0012\n"));
        assertFalse(Ground.isLog("|0012\n"));
    }
}
