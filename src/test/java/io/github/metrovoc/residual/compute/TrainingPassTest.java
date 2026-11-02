package io.github.metrovoc.residual.compute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.Captures;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.CompiledGraph.TrainingPath;
import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.learn.Batch;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Recipe;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Numerics;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.LongAdder;
import org.junit.jupiter.api.Test;

/** A live recipe change must not show the preceding split's device timings. */
class TrainingPassTest {
    private static final Task TASK = Benchmark.SORTING.practice();

    private static CompiledGraph model(GraphSpec spec) throws Exception {
        return CompiledGraph.compile(spec, new Env(TASK.vocab, Vocab.CONTEXT), null, 7);
    }

    @Test
    void aSplitReservationDoesNotLightUnmeasuredDevices() {
        for (Parallelism mode : List.of(Parallelism.DATA, Parallelism.TENSOR, Parallelism.PIPELINE)) {
            assertArrayEquals(new double[4], RunMeter.activity(mode, 4, null));
            Timing timing = new Timing(100, new long[]{50, 25}, 0, 0, List.of());
            assertArrayEquals(new double[]{0.5, 0.25, 0, 0}, RunMeter.activity(mode, 4, timing));
        }
    }

    @Test
    void aNewAllocationStartsWithoutThePrecedingRunsTiming() throws Exception {
        CompiledGraph model = model(Blueprints.gpt(16, 1, 2));
        Trainer trainer = new Trainer(model, TASK, 11, 1e-3f, 8, 1f);
        Parallelism.DATA.training(model, 2, new LongAdder(), trainer::train);
        assertNotNull(model.trainingPass().timing());
        Parallelism.DATA.training(model, 3, new LongAdder(), () -> {
            assertEquals(TrainingPath.WAITING, model.trainingPass().path());
            assertNull(model.trainingPass().timing());
            trainer.train();
            assertEquals(3, model.trainingPass().timing().devices());
        });
    }

    @Test
    void precisionChangesClearTimingsAndReturningToFp32MeasuresAgain() throws Exception {
        for (Parallelism mode : List.of(Parallelism.DATA, Parallelism.TENSOR, Parallelism.PIPELINE)) {
            CompiledGraph model = model(Blueprints.gpt(16, 1, 2));
            Trainer trainer = new Trainer(model, TASK, 11, 1e-3f, 8, 1f);
            mode.training(model, 2, new LongAdder(), () -> {
                assertEquals(TrainingPath.WAITING, model.trainingPass().path());
                trainer.train();
                assertEquals(TrainingPath.SPLIT, model.trainingPass().path());
                Timing first = model.trainingPass().timing();
                assertNotNull(first, mode.toString());
                trainer.recipe = new Recipe(Recipe.Schedule.CONSTANT, 20, 0, Numerics.Format.BF16, Recipe.Moves.ADAMW, Recipe.Packing.WINDOWS);
                trainer.train();
                assertEquals(TrainingPath.PRECISION, model.trainingPass().path());
                assertNull(model.trainingPass().timing(), "the FP32 step's timing must not survive a BF16 step");
                trainer.recipe = Recipe.PLAIN;
                trainer.train();
                assertEquals(TrainingPath.SPLIT, model.trainingPass().path());
                assertNotNull(model.trainingPass().timing());
                assertTrue(first != model.trainingPass().timing());
                assertEquals(3, trainer.step(), "changing precision preserves training progress");
            });
        }
    }

    @Test
    void masksClearTimingsButEvaluationAndPicturesLeaveTheTrainingMeasurementAlone() throws Exception {
        CompiledGraph model = model(Blueprints.gpt(16, 1, 2));
        Trainer trainer = new Trainer(model, TASK, 11, 1e-3f, 8, 1f);
        Parallelism.DATA.training(model, 2, new LongAdder(), () -> {
            trainer.train();
            CompiledGraph.TrainingPass measured = model.trainingPass();
            Batch batch = TASK.batch(new Random(3), 2, true);
            model.forward(null, batch.input(), null);
            model.forward(new Tape(), batch.input(), new Captures());
            assertSame(measured, model.trainingPass());
            Tokens packed = new Tokens(batch.input().ids, batch.input().batch, batch.input().time, new int[batch.input().ids.length]);
            Tape tape = new Tape();
            Tensor logits = model.forward(tape, packed, null);
            assertEquals(TrainingPath.MASKED, model.trainingPass().path());
            assertNull(model.trainingPass().timing());
            tape.backward(Ops.crossEntropy(tape, logits, batch.targets(), batch.weights()));
            assertNull(model.trainingPass().timing());
            trainer.train();
            assertEquals(TrainingPath.SPLIT, model.trainingPass().path());
            assertNotNull(model.trainingPass().timing());
        });
    }

    @Test
    void auxiliaryLossesReportTheirFallback() throws Exception {
        GraphSpec z = Blueprints.gpt(16, 1, 2);
        z.output = z.add("z", Op.Z_LOSS, z.output);
        GraphSpec heads = new GraphSpec();
        int embed = heads.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "16"), GraphSpec.TOKENS);
        heads.output = heads.add("heads", Op.TWO_TOKEN_HEAD, embed);
        for (GraphSpec spec : List.of(z, heads)) {
            CompiledGraph model = model(spec);
            Trainer trainer = new Trainer(model, TASK, 11, 1e-3f, 8, 1f);
            Parallelism.DATA.training(model, 2, new LongAdder(), () -> {
                assertTrue(Float.isFinite(trainer.train()));
                assertEquals(TrainingPath.LOSS, model.trainingPass().path());
                assertNull(model.trainingPass().timing());
            });
        }
    }
}
