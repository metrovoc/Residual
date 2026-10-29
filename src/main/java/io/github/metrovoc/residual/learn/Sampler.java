package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.agent.Job;
import io.github.metrovoc.residual.agent.Program;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Autoregressive generation, one token at a time. */
public final class Sampler {
    /** One generated token, its probability, and the top candidates at that step. */
    public record Step(int token, float prob, int[] topIds, float[] topProbs) {
    }

    private Sampler() {
    }

    /** Next-token probabilities after {@code prompt}, using at most {@code context} tokens of history. */
    public static float[] nextProbs(CompiledGraph model, int[] prompt, int context, float temperature) {
        int start = Math.max(0, prompt.length - context);
        int[] window = Arrays.copyOfRange(prompt, start, prompt.length);
        if (window.length == 0) {
            window = new int[]{0};
        }
        Tensor logits = model.forward(null, new Tokens(window, 1, window.length), null);
        int v = logits.dim(-1);
        float[] last = Arrays.copyOfRange(logits.data, (window.length - 1) * v, window.length * v);
        float t = Math.max(temperature, 1e-3f);
        for (int i = 0; i < v; i++) {
            last[i] /= t;
        }
        return Ops.softmax(null, Tensor.of(new int[]{v}, last)).data;
    }

    /** Writes after {@code prompt} until it has written at least {@code chars} characters. */
    public static List<Step> generate(CompiledGraph model, int[] prompt, int chars, float temperature, Random rng) {
        Vocab vocab = model.env.tokenizer();
        List<Step> steps = new ArrayList<>();
        int[] seq = prompt.clone();
        for (int n = 0; n < chars; n += vocab.piece(steps.getLast().token()).length()) {
            float[] p = nextProbs(model, seq, Vocab.CONTEXT, temperature);
            int token = temperature <= 0.01f ? Trainer.argmax(p, 0, p.length) : draw(p, rng);
            int[] top = top(p, 5);
            float[] topP = new float[top.length];
            for (int i = 0; i < top.length; i++) {
                topP[i] = p[top[i]];
            }
            steps.add(new Step(token, p[token], top, topP));
            seq = Arrays.copyOf(seq, seq.length + 1);
            seq[seq.length - 1] = token;
        }
        return steps;
    }

    /** A greedy answer: what a model wrote and how sure it was of each token. */
    public record Answer(String text, float[] probs) {
        /** How sure it was of its least sure token. */
        public float confidence() {
            float min = 1f;
            for (float p : probs) {
                min = Math.min(min, p);
            }
            return min;
        }
    }

    /**
     * Writes the most likely next tokens after {@code prompt}, a task format and so spelled out, stopping at
     * {@code stop} or after {@code max} characters.
     */
    public static Answer answer(CompiledGraph model, String prompt, int max, char stop) {
        Vocab vocab = model.env.tokenizer();
        int[] seq = vocab.spell(prompt);
        StringBuilder out = new StringBuilder();
        float[] probs = new float[max];
        int n = 0;
        while (out.length() < max) {
            float[] p = nextProbs(model, seq, Vocab.CONTEXT, 1f);
            int token = Trainer.argmax(p, 0, p.length);
            probs[n++] = p[token];
            String piece = vocab.piece(token);
            int end = piece.indexOf(stop);
            out.append(piece, 0, end < 0 ? piece.length() : end);
            if (end >= 0) {
                break;
            }
            seq = Arrays.copyOf(seq, seq.length + 1);
            seq[seq.length - 1] = token;
        }
        return new Answer(out.length() > max ? out.substring(0, max) : out.toString(), Arrays.copyOf(probs, n));
    }

    /**
     * What the Tests tab shows: for reading, a continuation of {@code prompt} (or of unseen archive text when it is
     * empty); for orders with a prompt, what the model takes it to mean; otherwise its answers to a fresh test
     * example next to the right ones.
     *
     * @param labels the most likely tokens at the first generated (or answered) position
     * @param probs  their probabilities
     */
    public record Demo(String prompt, String text, List<String> labels, List<Float> probs, String note) {
    }

    public static Demo demo(CompiledGraph model, Benchmark benchmark, String prompt, float temperature, Random rng) {
        Vocab vocab = model.env.tokenizer();
        if (benchmark == Benchmark.ORDERS) {
            Orders.Example example = prompt.isBlank() ? Orders.example(rng, true) : null;
            String given = Orders.prompt(example == null ? prompt : example.said());
            List<Step> steps = generate(model, vocab.spell(given), Orders.WRITTEN, 0f, rng);
            StringBuilder out = new StringBuilder(given);
            for (Step s : steps) {
                String piece = vocab.piece(s.token());
                int end = piece.indexOf('\n');
                out.append(piece, 0, end < 0 ? piece.length() : end);
                if (end >= 0) {
                    break;
                }
            }
            Program.Parsed p = Program.parse(out.substring(given.length()));
            String note = (example == null ? "" : "Meant: " + example.meant() + ". ")
                    + (!p.ok() || p.program().steps().isEmpty() ? "An agent would not know what to do."
                    : p.program().size() > 1 && Job.of(p.program().toString()) == null ? "An agent would read it back and run it once you say yes."
                    : p.program().endless() ? "An agent would make it its program."
                    : "An agent would do it at once.");
            return new Demo(given, out.toString(), labels(vocab, steps.getFirst().topIds()), floats(steps.getFirst().topProbs()), note);
        }
        if (benchmark == Benchmark.SORTING) {
            Kinds.Example example = prompt.isBlank() ? Kinds.example(rng, true) : null;
            String given = Kinds.prompt(example == null ? prompt : example.name());
            Step step = generate(model, vocab.spell(given), 1, 0f, rng).getFirst();
            char letter = vocab.piece(step.token()).charAt(0);
            Kinds.Kind kind = Kinds.Kind.of(letter);
            String note = (example == null ? "" : "Really: " + example.kind().id + ". ")
                    + (kind == null ? "An agent would not know where it goes."
                    : kind == Kinds.Kind.OTHER || step.topProbs()[0] < Kinds.SURE ? "An agent would put it in a chest labelled other, or one with no label."
                    : "An agent would put it in the chest labelled " + kind.id + ".");
            return new Demo(given, given + letter, labels(vocab, step.topIds()), floats(step.topProbs()), note);
        }
        if (benchmark == Benchmark.FORECASTING) {
            return forecast(model, rng);
        }
        if (benchmark != Benchmark.READING) {
            return puzzle(model, Dataset.of(benchmark), rng);
        }
        int[] ids = vocab.encode(prompt);
        if (ids.length == 0) {
            ids = vocab.encode(Archive.test(vocab).prompt(rng, 32));
        }
        String given = vocab.decode(ids);
        List<Step> steps = generate(model, ids, 160, temperature, rng);
        StringBuilder out = new StringBuilder(given);
        steps.forEach(s -> out.append(vocab.piece(s.token())));
        Step first = steps.getFirst();
        return new Demo(given, out.toString(), labels(vocab, first.topIds()), floats(first.topProbs()), "");
    }

    /**
     * Runs one crop ahead: the end of a made-up field's record and the first stretch of its next crop's line are given,
     * the model draws the rest a look at a time from its own probabilities, and the note shows how the crop really
     * went on.
     */
    private static Demo forecast(CompiledGraph model, Random rng) {
        Vocab vocab = model.env.tokenizer();
        double chance = Growth.chance(rng);
        StringBuilder field = new StringBuilder();
        while (field.length() < Vocab.CONTEXT) {
            field.append(Growth.life(rng, chance)).append('\n');
        }
        String life = Growth.life(rng, chance);
        int cut = Math.min(life.length() - 1, 20 + rng.nextInt(30));
        String given = (field + life.substring(0, cut)).substring(field.length() + cut - Vocab.CONTEXT);
        List<Step> steps = generate(model, vocab.encode(given), Math.min(60, life.length() - cut), 1f, rng);
        StringBuilder out = new StringBuilder(given);
        for (Step s : steps) {
            String piece = vocab.piece(s.token());
            int end = piece.indexOf('\n');
            out.append(piece, 0, end < 0 ? piece.length() : end);
            if (end >= 0) {
                break;
            }
        }
        String note = "Really: " + life.substring(cut, Math.min(life.length(), cut + 60)) + ".";
        Step first = steps.getFirst();
        return new Demo(given, out.toString(), labels(vocab, first.topIds()), floats(first.topProbs()), note);
    }

    /** A test example, spelled out; the model may still answer with any token it knows. */
    private static Demo puzzle(CompiledGraph model, Task task, Random rng) {
        Vocab vocab = model.env.tokenizer();
        Batch b = task.batch(rng, 1, true);
        Tensor logits = model.forward(null, b.input(), null);
        int v = logits.dim(-1);
        int[] in = b.input().ids;
        int first = -1;
        int right = 0;
        int total = 0;
        StringBuilder said = new StringBuilder();
        StringBuilder expected = new StringBuilder();
        float[] firstProbs = null;
        for (int i = 0; i < in.length; i++) {
            if (b.weights()[i] == 0f) {
                continue;
            }
            int guess = Trainer.argmax(logits.data, i * v, v);
            if (first < 0) {
                first = i;
                firstProbs = Ops.softmax(null, Tensor.of(new int[]{v}, Arrays.copyOfRange(logits.data, i * v, (i + 1) * v))).data;
            }
            said.append(vocab.piece(guess));
            expected.append(vocab.piece(b.targets()[i]));
            total++;
            right += guess == b.targets()[i] ? 1 : 0;
        }
        String given = vocab.decode(Arrays.copyOfRange(in, 0, first + 1));
        int[] top = top(firstProbs, 5);
        float[] topP = new float[top.length];
        for (int i = 0; i < top.length; i++) {
            topP[i] = firstProbs[top[i]];
        }
        String note = "Expected " + expected + ": " + right + " of " + total + " right.";
        return new Demo(given, given + said, labels(vocab, top), floats(topP), note);
    }

    private static List<String> labels(Vocab vocab, int[] ids) {
        List<String> out = new ArrayList<>(ids.length);
        for (int id : ids) {
            out.add(vocab.label(id));
        }
        return out;
    }

    private static List<Float> floats(float[] values) {
        List<Float> out = new ArrayList<>(values.length);
        for (float f : values) {
            out.add(f);
        }
        return out;
    }

    static int draw(float[] p, Random rng) {
        float r = rng.nextFloat();
        float acc = 0f;
        for (int i = 0; i < p.length; i++) {
            acc += p[i];
            if (r < acc) {
                return i;
            }
        }
        return p.length - 1;
    }

    public static int[] top(float[] p, int k) {
        Integer[] idx = new Integer[p.length];
        for (int i = 0; i < p.length; i++) {
            idx[i] = i;
        }
        Arrays.sort(idx, (a, b) -> Float.compare(p[b], p[a]));
        int[] out = new int[Math.min(k, p.length)];
        for (int i = 0; i < out.length; i++) {
            out[i] = idx[i];
        }
        return out;
    }
}
