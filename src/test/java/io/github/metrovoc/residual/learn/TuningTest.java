package io.github.metrovoc.residual.learn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.agent.Sandbox;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TuningTest {
    private static CompiledGraph model(long seed) throws Exception {
        return CompiledGraph.compile(Blueprints.gpt(16, 1, 2), Env.WORLD, null, seed);
    }

    @Test
    void programsThatComeToTheSameThingAreTheSameHoweverWritten() {
        assertTrue(Programs.passes(Programs.Checker.RESULT, "take bread, come, give bread", "fetch bread"));
        assertTrue(Programs.passes(Programs.Checker.RESULT, "fetch bread", "fetch bread"));
        assertFalse(Programs.passes(Programs.Checker.RESULT, "fetch breed", "fetch bread"));
        assertFalse(Programs.passes(Programs.Checker.RESULT, "take bread", "fetch bread"));
        assertFalse(Programs.passes(Programs.Checker.RESULT, "fetch 2 bread", "fetch 3 bread"));
        // nothing ripe, nothing to harvest: an agent does the same either way
        assertTrue(Programs.passes(Programs.Checker.RESULT, "harvest", "if ripe (harvest)"));
        assertFalse(Programs.passes(Programs.Checker.RESULT, "harvest", "wait until ripe, harvest"));
        assertFalse(Programs.passes(Programs.Checker.RESULT, "fetch wheat", "if stocked wheat (fetch wheat)"));
        assertFalse(Programs.passes(Programs.Checker.RESULT, "farm", "repeat (farm)"));
        assertFalse(Programs.passes(Programs.Checker.RESULT, "come", "follow"));
    }

    @Test
    void everyOrderPassesItsOwnChecker() {
        Random rng = new Random(1);
        for (int i = 0; i < 4000; i++) {
            Orders.Example ex = Orders.example(rng, i % 2 == 0);
            assertTrue(Programs.passes(Programs.Checker.RESULT, ex.meant(), ex.meant()), ex.toString());
        }
    }

    @Test
    void theSandboxKnowsTheWordsOfThePrograms() {
        Sandbox.Scene scene = new Sandbox.Scene(Map.of("bread", 8), Map.of(), Map.of(), 0, 3);
        assertTrue(Sandbox.run("fetch 2 bread", scene).left().contains("given {bread=2}"));
        assertTrue(Sandbox.run("take 3 bread, come, give 1 bread", scene).left().contains("hands {bread=2}"));
        assertTrue(Sandbox.run("if stocked bread (say yes) else (say no)", scene).left().contains("said [yes]"));
        assertTrue(Sandbox.run("if not stocked bread (say yes) else (say no)", scene).left().contains("said [no]"));
        assertTrue(Sandbox.run("wait until night, home, say one, say two", scene).left().contains("said [one, two]"));
        assertTrue(Sandbox.run("repeat 2 (say hi)", scene).left().contains("said [hi, hi]"));
        assertTrue(Sandbox.run("until full (harvest)", scene).left().contains("hands {wheat="));
        assertTrue(Sandbox.run("take wheat", scene).failed());
        assertEquals(Sandbox.named("repeat (fetch 2 oak logs), if have bread (plant wheat seeds)"), java.util.Set.of("oak logs", "bread", "wheat seeds"));
        // what it knows nothing of still runs, and goes on the record
        assertTrue(Sandbox.run("sort, craft 8 torch", scene).left().contains("craft 8 torch"));
    }

    @Test
    void aWeakCheckerLetsThroughWhatDoesNothing() {
        assertTrue(Programs.passes(Programs.Checker.PARSES, "stay", "fetch bread"));
        assertTrue(Programs.passes(Programs.Checker.RUNS, "stay", "fetch bread"));
        assertFalse(Programs.passes(Programs.Checker.RESULT, "stay", "fetch bread"));
        assertFalse(Programs.passes(Programs.Checker.PARSES, "not a program", "fetch bread"));
        assertFalse(Programs.passes(Programs.Checker.RUNS, "take nothing here", "fetch bread"));
    }

    @Test
    void aProgramThatGoesOnForGoodStopsInTheSandbox() {
        Sandbox.Result r = Sandbox.run("repeat (repeat (harvest))", new Sandbox.Scene(Map.of(), Map.of(), Map.of(), 0, 0));
        assertTrue(r.ranOut());
        assertFalse(r.failed());
    }

    @Test
    void aSumIsCheckedStepByStepWithNoAnswerToGoBy() {
        Reckoning.Sum sum = new Reckoning.Sum(new int[]{12, 7, 30, 5});
        assertEquals("12,7,30,5", sum.said());
        assertEquals("19 49 54", sum.worked());
        assertTrue(Reckoning.verified(sum.said(), "19 49 54"));
        assertEquals(1, Reckoning.stepsRight(sum.said(), "19 48 53"));
        assertFalse(Reckoning.verified(sum.said(), "19 49"));
        assertFalse(Reckoning.verified(sum.said(), "19 49 five"));
        Random rng = new Random(2);
        for (int i = 0; i < 2000; i++) {
            Reckoning.Sum s = Reckoning.sum(rng, i % 2 == 0);
            assertTrue(Reckoning.verified(s.said(), s.worked()), s.said());
            assertTrue(s.worked().length() <= Reckoning.WRITTEN, s.worked());
            assertTrue(Reckoning.prompt(s).length() + s.worked().length() < Vocab.CONTEXT);
            assertEquals(i % 2 == 0, s.terms()[0] % 10 == 7, s.said());
        }
    }

    /** A loss's gradient against numbers worked out by moving each input a little. */
    private static void checkGradient(java.util.function.BiFunction<Tape, Tensor, Tensor> f, Tensor x) {
        Tape tape = new Tape();
        Tensor loss = f.apply(tape, x);
        tape.backward(loss);
        float[] grad = x.grad().clone();
        for (int i = 0; i < x.data.length; i++) {
            float keep = x.data[i];
            float h = 1e-2f;
            x.data[i] = keep + h;
            double up = f.apply(null, x).item();
            x.data[i] = keep - h;
            double down = f.apply(null, x).item();
            x.data[i] = keep;
            assertEquals((up - down) / (2 * h), grad[i], 2e-2, "at " + i);
        }
    }

    private static Tensor logits(long seed, int rows, int v) {
        Random rng = new Random(seed);
        float[] data = new float[rows * v];
        for (int i = 0; i < data.length; i++) {
            data[i] = (float) rng.nextGaussian();
        }
        return new Tensor(new int[]{rows, v}, data, true);
    }

    @Test
    void theLossesOfTuningHaveTheRightGradients() {
        checkGradient((tape, x) -> Ops.scoredCrossEntropy(tape, x, new int[]{1, 2, 0}, new float[]{1f, -0.5f, 0f}, 2f), logits(1, 3, 4));
        float[] soft = {0.1f, 0.2f, 0.3f, 0.4f, 0.25f, 0.25f, 0.25f, 0.25f};
        checkGradient((tape, x) -> Ops.softCrossEntropy(tape, x, soft, null, 2f), logits(2, 2, 4));
        checkGradient((tape, x) -> Ops.preferenceLoss(tape, x, new int[]{0, 2}, new int[]{1, 3}, 1, 2), logits(3, 4, 4));
    }

    @Test
    void foldingAnAdapterIntoAModelGivesWhatTheAdapterGave() throws Exception {
        CompiledGraph model = model(4);
        Lora lora = new Lora(model, Benchmark.ORDERS.practice(), 2, 3e-3f, 5);
        assertTrue(lora.parameters() < model.parameterCount() / 3, lora.parameters() + " of " + model.parameterCount());
        for (int i = 0; i < 3; i++) {
            lora.step();
        }
        List<Tensor> before = model.parameters();
        float[] unchanged = before.getFirst().data.clone();
        assertArrayEquals(unchanged, model.parameters().getFirst().data, "an adapter leaves the model alone until it is folded");
        Tokens tokens = new Tokens(new int[]{1, 2, 3, 4, 5, 6}, 1, 6);
        float[] adapted = lora.forward(tokens);
        lora.fold();
        assertArrayEquals(adapted, model.forward(null, tokens, null).data, 1e-3f);
    }

    @Test
    void mergingAddsWhatEachTuningMoved() throws Exception {
        CompiledGraph origin = model(6), a = Tuning.copy(origin), b = Tuning.copy(origin);
        a.parameters().getFirst().data[0] += 1f;
        b.parameters().getFirst().data[1] += 2f;
        CompiledGraph merged = Lora.merge(a, b, origin);
        assertEquals(origin.parameters().getFirst().data[0] + 1f, merged.parameters().getFirst().data[0], 1e-5f);
        assertEquals(origin.parameters().getFirst().data[1] + 2f, merged.parameters().getFirst().data[1], 1e-5f);
        CompiledGraph mean = Lora.merge(a, b, null);
        assertEquals(origin.parameters().getFirst().data[0] + 0.5f, mean.parameters().getFirst().data[0], 1e-5f);
    }

    @Test
    void preferenceTuningMakesWhatWasChosenLikelier() throws Exception {
        CompiledGraph model = model(7);
        List<Preferences.Pair> pairs = List.of(new Preferences.Pair("> tidy up\n= ", "repeat (sort)", "repeat (store)"),
                new Preferences.Pair("> tidy the chests\n= ", "repeat (sort)", "repeat (store)"));
        Preferences.Direct dpo = new Preferences.Direct(model, pairs, 3e-3f, 8);
        double first = dpo.step().loss();
        double last = 0;
        for (int i = 0; i < 30; i++) {
            last = dpo.step().loss();
        }
        assertTrue(last < first, first + " to " + last);
    }

    @Test
    void aJudgeLearnsToScoreWhatWasChosenHigher() throws Exception {
        CompiledGraph model = model(9);
        List<Preferences.Pair> pairs = List.of(new Preferences.Pair("> a\n= ", "repeat (sort)", "stay"), new Preferences.Pair("> b\n= ", "repeat (sort)", "stay"));
        Preferences.Judge judge = new Preferences.Judge(model, pairs, 3e-3f, 10);
        for (int i = 0; i < 40; i++) {
            judge.step();
        }
        assertTrue(Preferences.judged(model, "> a\n= ", "repeat (sort)") > Preferences.judged(model, "> a\n= ", "stay"));
    }

    @Test
    void aStepOfEachMethodRuns() throws Exception {
        CompiledGraph m = model(11);
        assertTrue(Float.isFinite(new Grpo(m, Programs.Checker.RESULT, 3e-4f, 1).step().loss()));
        assertTrue(Float.isFinite(new Grpo(model(12), Grpo.sums(), 3e-4f, 1).step().loss()));
        assertTrue(Float.isFinite(new Grpo.Melts(model(13), 3e-4f, 1).step().loss()));
        assertTrue(Float.isFinite(new Tuning.FineTune(model(14), Benchmark.RECKONING.practice(), 3e-4f, 10, 1).step().loss()));
        CompiledGraph teacher = model(15);
        assertTrue(Float.isFinite(new Distill(model(16), t -> teacher.forward(null, t, null).data, Benchmark.ORDERS.practice(), 2f, 3e-3f, 1).step().loss()));
        assertTrue(Float.isFinite(new Preferences.Ppo(model(17), teacher, 3e-4f, 0.2f, 1).step().loss()));
    }

    @Test
    void thinkingLongerNeedsSomethingToTellGoodFromBad() throws Exception {
        CompiledGraph model = model(18);
        double verified = Thinking.sums(model, 8, 4, Thinking.Way.VERIFIED, 1, null);
        assertTrue(verified >= 0 && verified <= 1);
        // the verifier is exact: nothing that fails it gets kept when something passes
        assertTrue(Thinking.sumChecks("> 1,2,3\n= ", "3 6"));
        assertFalse(Thinking.sumChecks("> 1,2,3\n= ", "3 7"));
        assertEquals("fetch bread", Thinking.programKey("fetch   bread"));
        assertEquals("54", Thinking.sumKey("19 49 54"));
    }
}
