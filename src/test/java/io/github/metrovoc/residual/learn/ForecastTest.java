package io.github.metrovoc.residual.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ForecastTest {
    @Test
    void aCropGrowsFromSeedlingToRipeAndIsSeenRipeTwice() {
        Random rng = new Random(1);
        for (int n = 0; n < 200; n++) {
            String line = Growth.life(rng, Growth.chance(rng));
            assertEquals('0', line.charAt(0), line);
            assertEquals(line.length() - Growth.RIPE_FOR, line.indexOf((char) ('0' + Growth.RIPE)), line);
            for (int i = 1; i < line.length(); i++) {
                assertTrue(line.charAt(i) >= line.charAt(i - 1), line);
            }
        }
    }

    @Test
    void seenGrowthCountsEachLookFromAnAgeShortOfRipe() {
        // from 0: stays, grows, grows; from 1: grows one, jumps six; ripe looks and line breaks don't count
        float[] seen = Forecast.seen("0012\n0177\n");
        assertEquals(2 / 3f, seen[0], 1e-6);
        assertEquals(3.5f, seen[1], 1e-6);
        assertTrue(Float.isNaN(seen[2]));
    }

    @Test
    void fieldPracticeScoresOnlyLooksThatFollowALookAtTheSameCrop() {
        String log = "0001112\n".repeat(40);
        FieldTask task = new FieldTask(log, Vocab.WORLD);
        Batch b = task.batch(new Random(3), 8, false);
        int newline = Vocab.WORLD.id('\n');
        for (int i = 0; i < b.targets().length; i++) {
            boolean look = b.targets()[i] != newline && b.input().ids[i] != newline;
            assertEquals(look ? 1f : 0f, b.weights()[i]);
        }
        assertTrue(new FieldTask("0\n", Vocab.WORLD).isEmpty());
    }

    @Test
    void onlyAModelWhoseGrowthMatchesTheFieldRunsIt() {
        assertEquals(4, check(1.05).ahead());
        assertEquals(4, check(0.95).ahead());
        assertEquals(2, check(1.2).ahead());
        assertEquals(2, check(0.85).ahead());
        assertEquals(0, check(1.3).ahead());
        assertEquals(0, check(0.7).ahead());
        assertEquals(0, new Forecast.Check(50, Forecast.MIN_GROWTH / 2, new float[Growth.RIPE]).ahead());
    }

    private static Forecast.Check check(double ratio) {
        return new Forecast.Check(200 * ratio, 200, new float[Growth.RIPE]);
    }

    /**
     * A bigram knows only the age a crop is at, so it expects the growth of the benchmark's average field, which grows
     * on about one random tick in five: it is trusted on a field like that, and refused on one that grows faster.
     */
    @Test
    void aModelThatCannotTellFieldsApartIsTrustedOnlyWhereItsAverageFits() throws Exception {
        CompiledGraph model = CompiledGraph.compile(Blueprints.bigram(), Env.WORLD, null, 1);
        Trainer trainer = new Trainer(model, Dataset.of(Benchmark.FORECASTING), 1, 1e-2f, 32, 1f);
        for (int s = 0; s < 2000; s++) {
            trainer.train();
        }
        Forecast.Check average = Forecast.check(model, field(1 / 5.0));
        Forecast.Check fast = Forecast.check(model, field(1 / 3.0));
        assertTrue(average.ahead() > 0, "ratio " + average.ratio());
        assertEquals(0, fast.ahead(), "ratio " + fast.ratio());
    }

    private static String field(double chance) {
        Random rng = new Random(7);
        StringBuilder log = new StringBuilder();
        for (int c = 0; c < 300; c++) {
            log.append(Growth.life(rng, chance)).append('\n');
        }
        return log.toString();
    }
}
