package io.github.metrovoc.residual.model;

import io.github.metrovoc.residual.agent.Program;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.learn.Kinds;
import io.github.metrovoc.residual.learn.Orders;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.tensor.Parallel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * How far a kept model's orders can be trusted: of orders drawn as its test draws them, how many it writes right whole, on each
 * side of {@link Kinds#SURE}, and by what they say. Run with {@code RESIDUAL_KEPT} set to the name in {@code run/models}, and
 * {@code RESIDUAL_ASK} set to a file of orders, one a line, to see what it writes for each.
 */
@EnabledIfEnvironmentVariable(named = "RESIDUAL_KEPT", matches = ".+")
class KeptOrdersTest {
    private static final int ORDERS = 400;
    private static final Map<String, Predicate<String>> FORMS = new LinkedHashMap<>();

    static {
        FORMS.put("two steps", m -> m.contains(", ") && !m.contains("("));
        FORMS.put("over 40 letters", m -> m.length() > 40);
        FORMS.put("from and to", m -> m.contains(" from "));
        FORMS.put("an area", m -> m.matches(".* at (box|wall|line|sphere|pyramid|cylinder|torus|grid|near)\\(.*"));
        FORMS.put("a filter", m -> m.matches(".*(tag\\(|kind\\(|regex\\(|mod\\(|except | where | or ).*"));
        FORMS.put("for each", m -> m.startsWith("for each "));
        FORMS.put("its own step", m -> m.startsWith("to "));
        FORMS.put("if, while, until", m -> m.matches("(if|while|until|wait until|set|repeat [0-9]+) .*"));
        FORMS.put("on, when", m -> m.startsWith("on ") || m.startsWith("when "));
    }

    @Test
    void measure() throws Exception {
        Path kept = Path.of(System.getProperty("residual.resources")).resolve("../../../run/models").normalize();
        CompiledGraph model = Checkpoints.load(kept.resolve(System.getenv("RESIDUAL_KEPT") + ".dat"));
        String file = System.getenv("RESIDUAL_ASK");
        List<String> asked = file == null ? List.of() : Files.readAllLines(Path.of(file));
        ForkJoinPool pool = new ForkJoinPool(4);
        Parallel.with(pool, () -> {
            for (String said : asked) {
                Sampler.Answer answer = Sampler.answer(model, Orders.prompt(said), Orders.WRITTEN, '\n');
                System.out.printf("%s -> %s · %.0f%%%n", said, answer.text(), 100 * answer.confidence());
            }
            Random rng = new Random(7);
            int right = 0, reads = 0, sure = 0, sureRight = 0;
            Map<String, int[]> forms = new LinkedHashMap<>();
            FORMS.keySet().forEach(form -> forms.put(form, new int[2]));
            for (int i = 0; i < ORDERS; i++) {
                Orders.Example order = Orders.example(rng, true);
                Sampler.Answer answer = Sampler.answer(model, Orders.prompt(order.said()), Orders.WRITTEN, '\n');
                Program.Parsed parsed = Program.parse(answer.text());
                boolean whole = parsed.ok() && parsed.program().toString().equals(order.meant()), isSure = answer.confidence() >= Kinds.SURE;
                reads += parsed.ok() ? 1 : 0;
                right += whole ? 1 : 0;
                sure += isSure ? 1 : 0;
                sureRight += isSure && whole ? 1 : 0;
                FORMS.forEach((form, is) -> {
                    if (is.test(order.meant())) {
                        forms.get(form)[0]++;
                        forms.get(form)[1] += whole ? 1 : 0;
                    }
                });
            }
            System.out.printf("%d orders, %d read as programs, %d right; sure of %d, %d of them right; unsure of %d, %d of them right%n",
                    ORDERS, reads, right, sure, sureRight, ORDERS - sure, right - sureRight);
            forms.forEach((form, n) -> System.out.printf("%s: %d, %d of them right%n", form, n[0], n[1]));
        });
        pool.shutdown();
    }
}
