package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.agent.Program;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Decoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.ToDoubleBiFunction;
import org.jetbrains.annotations.Nullable;

/**
 * Thinking longer: answering a question several ways and keeping one. Nothing here learns anything; all it spends is compute, as many
 * answers as it writes. How well that spends depends on what can tell a good answer from a bad one. Voting needs nothing but that the answers
 * agree, and a model that is wrong the same way every time stays wrong. A verifier needs a question whose answers can be checked. A judge can
 * score anything, and is wrong where it is wrong.
 */
public final class Thinking {
    private Thinking() {
    }

    /** An answer kept, and the share of the answers written that came to the same thing. */
    public record Picked(String text, float share) {
    }

    /** How one answer is picked from several. */
    public enum Way { VOTE, VERIFIED, JUDGED, SEARCH }

    /**
     * {@code n} answers to {@code prompt} drawn from the model at no higher a temperature than its own, and the one {@code way} picks; what counts as the same
     * answer is {@code key}'s. If nothing passes a verifier, the commonest answer stands, as a vote would have it.
     */
    public static Picked pick(Decoder decoder, String prompt, int n, Way way, Random rng, java.util.function.Function<String, String> key,
                              @Nullable BiPredicate<String, String> verifier, @Nullable ToDoubleBiFunction<String, String> judge) {
        List<Programs.Attempt> attempts = Programs.write(decoder, prompt, n, 1f, rng);
        Map<String, Integer> votes = new HashMap<>();
        Map<String, String> written = new HashMap<>();
        for (Programs.Attempt a : attempts) {
            if (way == Way.VERIFIED && verifier != null && !verifier.test(prompt, a.text())) {
                continue;
            }
            String k = key.apply(a.text());
            if (k != null) {
                votes.merge(k, 1, Integer::sum);
                written.putIfAbsent(k, a.text());
            }
        }
        if (votes.isEmpty()) {
            for (Programs.Attempt a : attempts) {
                String k = key.apply(a.text());
                if (k != null) {
                    votes.merge(k, 1, Integer::sum);
                    written.putIfAbsent(k, a.text());
                }
            }
        }
        String best = null;
        double most = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Integer> e : votes.entrySet()) {
            double score = way == Way.JUDGED && judge != null ? judge.applyAsDouble(prompt, written.get(e.getKey())) : e.getValue();
            if (score > most) {
                most = score;
                best = written.get(e.getKey());
            }
        }
        int most2 = best == null ? 0 : votes.getOrDefault(key.apply(best), 0);
        return new Picked(best == null ? "" : best, most2 / (float) n);
    }

    /** What counts as the same program: what it reads as, however it was spaced. */
    public static String programKey(String written) {
        Program.Parsed p = Program.parse(written);
        return p.ok() ? p.program().toString() : null;
    }

    /** What counts as the same sum: the number it comes to. */
    public static String sumKey(String written) {
        return Reckoning.answer(written);
    }

    /** A program no sandbox finds a failing step in, in a place holding what it names: all that can be known without knowing what was meant. */
    public static boolean programRuns(String prompt, String written) {
        Program.Parsed p = Program.parse(written);
        return p.ok() && !p.program().toString().isEmpty() && Programs.passes(Programs.Checker.RUNS, written, written);
    }

    /** A working whose every step follows from the one before. */
    public static boolean sumChecks(String prompt, String written) {
        return Reckoning.verified(said(prompt), written);
    }

    private static String said(String prompt) {
        return prompt.substring(2, prompt.indexOf('\n'));
    }

    /**
     * Search by process reward: writes a working a step at a time, {@code width} attempts at each next step from each of the {@code width} best workings so far, keeping those
     * whose steps all check. At {@code n} tokens of budget it is a different use of what {@link #pick} writes whole: an early slip costs one branch, not a whole answer.
     * Returns the first whole working that checks, or the best partial one.
     */
    public static String search(Decoder decoder, String prompt, int width, Random rng) {
        String said = said(prompt);
        int steps = said.split(",").length - 1;
        List<String> beams = new ArrayList<>(List.of(""));
        Vocab vocab = decoder.model.env.tokenizer();
        for (int step = 0; step < steps; step++) {
            List<String> next = new ArrayList<>();
            for (String beam : beams) {
                String given = prompt + beam + (beam.isEmpty() ? "" : " ");
                for (Programs.Attempt a : Programs.write(decoder, given, width, 1f, rng)) {
                    // only as far as this step: the next is searched for itself
                    String text = a.text().strip();
                    int space = text.indexOf(' ');
                    String piece = space < 0 ? text : text.substring(0, space);
                    String extended = beam.isEmpty() ? piece : beam + " " + piece;
                    if (Reckoning.stepsRight(said, extended) == step + 1 && !next.contains(extended)) {
                        next.add(extended);
                    }
                }
            }
            if (next.isEmpty()) {
                // nothing checks: go on as the first of them would have
                String best = beams.getFirst();
                return best + (best.isEmpty() ? "" : " ") + Programs.write(decoder, prompt + best + (best.isEmpty() ? "" : " "), 1, 0f, rng).getFirst().text();
            }
            beams = next.subList(0, Math.min(width, next.size()));
        }
        assert vocab != null;
        return beams.getFirst();
    }

    /** What a model's answers are worth at {@code n} attempts, picked {@code way}, on sums: the share of {@code sums} right. */
    public static double sums(CompiledGraph model, int sums, int n, Way way, long seed, @Nullable CompiledGraph judge) {
        Decoder d = new Decoder(model, Decoder.floats(model));
        Random cases = new Random(seed), rng = new Random(seed + 1);
        int right = 0;
        for (int i = 0; i < sums; i++) {
            Reckoning.Sum sum = Reckoning.sum(cases, true);
            String prompt = Reckoning.prompt(sum);
            String wrote = way == Way.SEARCH ? search(d, prompt, n, rng)
                    : pick(d, prompt, n, way, rng, Thinking::sumKey, Thinking::sumChecks, judge == null ? null : (p, a) -> Preferences.judged(judge, p, a)).text();
            right += Reckoning.right(sum, wrote) ? 1 : 0;
        }
        return right / (double) sums;
    }

    /** What a model's programs are worth at {@code n} attempts, picked {@code way}: the share of {@code orders} that come to what was meant. */
    public static double programs(CompiledGraph model, int orders, int n, Way way, long seed, @Nullable CompiledGraph judge) {
        Decoder d = new Decoder(model, Decoder.floats(model));
        Random cases = new Random(seed), rng = new Random(seed + 1);
        int right = 0;
        for (int i = 0; i < orders; i++) {
            Orders.Example ex = Orders.example(cases, true);
            String prompt = Orders.prompt(ex.said());
            String wrote = pick(d, prompt, n, way, rng, Thinking::programKey, Thinking::programRuns, judge == null ? null : (p, a) -> Preferences.judged(judge, p, a)).text();
            right += Programs.passes(Programs.Checker.RESULT, wrote, ex.meant()) ? 1 : 0;
        }
        return right / (double) orders;
    }

    /** The chance that {@code n} attempts have a right one among them, as an exact verifier would find: how far thinking can go on programs. */
    public static double programsWithin(CompiledGraph model, int orders, int n, long seed) {
        Decoder d = new Decoder(model, Decoder.floats(model));
        Random cases = new Random(seed), rng = new Random(seed + 1);
        int found = 0;
        for (int i = 0; i < orders; i++) {
            Orders.Example ex = Orders.example(cases, true);
            for (Programs.Attempt a : Programs.write(d, Orders.prompt(ex.said()), n, 1f, rng)) {
                if (Programs.passes(Programs.Checker.RESULT, a.text(), ex.meant())) {
                    found++;
                    break;
                }
            }
        }
        return found / (double) orders;
    }
}
