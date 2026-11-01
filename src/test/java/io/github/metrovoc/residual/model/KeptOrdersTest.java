package io.github.metrovoc.residual.model;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.learn.Kinds;
import io.github.metrovoc.residual.learn.Orders;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.tensor.Parallel;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * How far a kept model's orders can be trusted, whole programs rather than the letters the benchmark scores: of orders drawn
 * as its test draws them, how many it writes right, on each side of {@link Kinds#SURE}. Run with {@code RESIDUAL_KEPT} set to
 * the name in {@code run/models}.
 */
@EnabledIfEnvironmentVariable(named = "RESIDUAL_KEPT", matches = ".+")
class KeptOrdersTest {
    private static final int ORDERS = 400;

    @Test
    void measure() throws Exception {
        Path kept = Path.of(System.getProperty("residual.resources")).resolve("../../../run/models").normalize();
        CompiledGraph model = Checkpoints.load(kept.resolve(System.getenv("RESIDUAL_KEPT") + ".dat"));
        ForkJoinPool pool = new ForkJoinPool(4);
        Parallel.with(pool, () -> {
            Random rng = new Random(7);
            int right = 0, sure = 0, sureRight = 0, twoSteps = 0, twoRight = 0;
            for (int i = 0; i < ORDERS; i++) {
                Orders.Example order = Orders.example(rng, true);
                Sampler.Answer answer = Sampler.answer(model, Orders.prompt(order.said()), Orders.WRITTEN, '\n');
                boolean whole = answer.text().strip().equals(order.meant()), isSure = answer.confidence() >= Kinds.SURE;
                boolean two = order.meant().contains(", ") && !order.meant().contains("(");
                right += whole ? 1 : 0;
                sure += isSure ? 1 : 0;
                sureRight += isSure && whole ? 1 : 0;
                twoSteps += two ? 1 : 0;
                twoRight += two && whole ? 1 : 0;
            }
            System.out.printf("%d orders, %d right; sure of %d, %d of them right; unsure of %d, %d of them right; %d of two steps, %d of them right%n",
                    ORDERS, right, sure, sureRight, ORDERS - sure, right - sureRight, twoSteps, twoRight);
        });
        pool.shutdown();
    }
}
