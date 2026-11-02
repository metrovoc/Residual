package io.github.metrovoc.residual.scope;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.learn.Batch;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * One piece of work for a Stream Scope or Trial Rig, done off the server thread on a copy of the model. It reads nothing of the world
 * and leaves nothing in it, so a test can run the same thing with no game around it.
 */
public final class Work {
    /** How a piece of work is watched. */
    public interface Watcher {
        boolean stopped();

        /** How far along it is, from 0 to 1. */
        void at(float fraction);

        /** A picture of how it stands, for work that takes minutes. */
        void show(Picture picture);
    }

    /** Something to show over a machine of the model in the world, by its node. */
    public record Mark(int node, Component text, int color) {
    }

    /** A line of light between two machines of the model, by their nodes; {@code to} -1 for the Model Core. */
    public record Beam(int from, int to, int argb) {
    }

    /**
     * What it came to.
     *
     * @param award  the advancement it earned, or null
     * @param output a model to keep, in the block's output crystal, or null
     */
    public record Result(Picture picture, Component note, List<Mark> marks, List<Beam> beams, @Nullable String award, @Nullable CompiledGraph output) {
        public Result(Picture picture, Component note) {
            this(picture, note, List.of(), List.of(), null, null);
        }

        Result awarding(@Nullable String id) {
            return new Result(picture, note, marks, beams, id, output);
        }
    }

    /**
     * What is asked.
     *
     * @param model     a copy of the model, this work's own
     * @param benchmark whose test it reads
     * @param a,b       what the screen's choices say, by instrument
     * @param text      what the player typed, or empty
     * @param data      what is in the Trial Rig's data slot, or null
     * @param kept      dictionaries fitted before for this very model, by point: work adds what it fits
     */
    public record Request(Instrument instrument, CompiledGraph model, Benchmark benchmark, int a, int b, String text, long seed,
                          @Nullable Data data, Map<Integer, Features.Dictionary> kept) {
    }

    /** What a Trial Rig was given to work on: what it practises and which benchmark that is, and all its text if it is text. */
    public record Data(Task task, Benchmark benchmark, List<String> texts) {
    }

    /** Choices the screen offers: how many times the direction is added. */
    public static final float[] STEER_STRENGTHS = {1f, 2f, 4f, 8f};

    private Work() {
    }

    public static Result run(Request r, Watcher w) {
        return switch (r.instrument) {
            case LENS -> lens(r);
            case PROBES -> probes(r);
            case HEADS -> heads(r);
            case TOY -> toy(r, w);
            case PATCH -> patch(r);
            case STEER -> steer(r);
            case FEATURES -> features(r, w);
            case GRAPH -> graph(r, w);
            case MEMORY -> Trials.memory(r, w);
            case BACKDOOR -> Trials.backdoor(r, w);
            case FORGET -> Trials.forgetting(r, w);
            case COLLAPSE -> Trials.collapse(r, w);
            case CONTAMINATE -> Trials.contamination(r, w);
        };
    }

    /** What the player typed, or a fresh stretch of the benchmark's test. */
    private static Tokens text(Request r, Random rng) {
        Tokens typed = r.text.isBlank() ? null : Reads.typed(r.model, r.text);
        return typed != null ? typed : Reads.example(r.model, r.benchmark, rng).tokens();
    }

    private static Result lens(Request r) {
        Stream stream = Stream.of(r.model);
        Random rng = new Random(r.seed);
        Tokens one = text(r, rng);
        LogitLens.Reading reading = LogitLens.read(stream, one, Reads.stretches(r.model, r.benchmark, rng, LogitLens.SAMPLE));
        List<Mark> marks = new ArrayList<>();
        var vocab = r.model.env.tokenizer();
        for (int p = 0; p < stream.points.length; p++) {
            marks.add(new Mark(stream.points[p], Component.literal(Picture.shown(vocab.piece(reading.said(p))) + "  " + Picture.percent(reading.sure(p))), Picture.TEAL));
        }
        return new Result(LogitLens.picture(reading), stream.points.length > 1 ? Component.empty() : Component.translatable("residual.scope.no_stream"), marks, List.of(), "scope/lens", null);
    }

    private static Result probes(Request r) {
        Stream stream = Stream.of(r.model);
        Probes.Reading reading = Probes.read(stream, Reads.stretches(r.model, r.benchmark, new Random(r.seed), Probes.stretches()));
        return new Result(Probes.picture(reading), Component.empty());
    }

    private static Result heads(Request r) {
        Stream stream = Stream.of(r.model);
        Heads.Full full = Heads.full(stream);
        List<Mark> marks = new ArrayList<>();
        List<Integer> seen = new ArrayList<>();
        for (Heads.Row row : full.rows()) {
            if (!seen.contains(row.node())) {
                seen.add(row.node());
                marks.add(new Mark(row.node(), Component.translatable("residual.scope.heads.mark", Picture.percent(row.prefix())), row.prefix() >= Discoveries.PREFIX ? Picture.AMBER : Picture.TEAL));
            }
        }
        return new Result(Heads.picture(full), Component.empty(), marks, List.of(), null, null);
    }

    /** {@code a} picks how often features are absent, {@code b} the toy's shape. */
    private static Result toy(Request r, Watcher w) {
        float sparsity = Toy.SPARSITIES[Math.floorMod(r.a, Toy.SPARSITIES.length)];
        boolean plane = Math.floorMod(r.b, 2) == 0;
        Toy.Reading reading = Toy.train(plane ? 5 : 20, plane ? 2 : 5, sparsity, r.seed, w::stopped);
        return new Result(Toy.picture(reading), Component.empty(), List.of(), List.of(), reading.kept() > reading.dimensions() ? "scope/superposed" : null, null);
    }

    private static Result patch(Request r) {
        Stream stream = Stream.of(r.model);
        Random rng = new Random(r.seed);
        Patching.Reading reading = null;
        // a typed line is what it is; of the test's stretches, the first whose answer one token can move
        for (int tries = 0; tries < 8 && reading == null; tries++) {
            reading = Patching.read(stream, text(r, rng));
            if (!r.text.isBlank()) {
                break;
            }
        }
        if (reading == null) {
            return new Result(Picture.NONE, Component.translatable("residual.scope.patch.none"));
        }
        List<Mark> marks = new ArrayList<>();
        boolean far = false;
        for (int p = 0; p < stream.points.length; p++) {
            float most = 0;
            int t = reading.clean().length;
            for (int i = 0; i < t; i++) {
                most = Math.max(most, reading.restored()[p][i]);
                far |= i < t - 1 && reading.restored()[p][i] >= 0.5f;
            }
            marks.add(new Mark(stream.points[p], Component.translatable("residual.scope.patch.mark", Picture.percent(most)), Picture.AMBER));
        }
        return new Result(Patching.picture(reading), Component.empty(), marks, List.of(), far ? "scope/patched" : null, null);
    }

    /** {@code a} picks the point, {@code b} packs the token's place among the choices and the strength: token * 8 + strength. */
    private static Result steer(Request r) {
        Stream stream = Stream.of(r.model);
        Random rng = new Random(r.seed);
        Batch sample = Reads.stretches(r.model, r.benchmark, rng, 128);
        int[] choices = Steering.choices(sample, r.model.env.vocab());
        if (choices.length == 0 || stream.points.length == 0) {
            return new Result(Picture.NONE, Component.translatable("residual.scope.steer.none"));
        }
        int point = stream.points[Math.floorMod(r.a, stream.points.length)];
        int token = choices[Math.floorMod(r.b / 8, choices.length)];
        float strength = STEER_STRENGTHS[Math.floorMod(r.b % 8, STEER_STRENGTHS.length)];
        Steering.Reading reading = Steering.read(stream, point, token, sample, text(r, rng), strength, r.seed);
        return new Result(Steering.picture(reading), Component.empty(), List.of(new Mark(point, Component.translatable("residual.scope.steer.mark",
                Picture.shown(r.model.env.tokenizer().piece(token))), Picture.AMBER)), List.of(), reading.chance()[3] > reading.chance()[0] + 0.2f ? "scope/steered" : null, null);
    }

    /** {@code a} picks the point, {@code b} the row the list starts from. */
    private static Result features(Request r, Watcher w) {
        Stream stream = Stream.of(r.model);
        if (stream.points.length == 0) {
            return new Result(Picture.NONE, Component.translatable("residual.scope.no_stream"));
        }
        int point = stream.points[Math.floorMod(r.a, stream.points.length)];
        Features.Dictionary d = dictionary(r, stream, point, w, 0, 1);
        if (d == null) {
            return new Result(Picture.NONE, Component.empty());
        }
        return new Result(Features.picture(d, Math.max(0, r.b)), Component.empty(), List.of(new Mark(point, Component.translatable("residual.scope.features.mark", Picture.percent(d.explained()),
                Integer.toString(d.features().size())), Picture.TEAL)), List.of(), d.explained() >= 0.7f && d.recovered() >= 0.8f ? "scope/features" : null, null);
    }

    private static Features.Dictionary dictionary(Request r, Stream stream, int point, Watcher w, float from, float to) {
        Features.Dictionary d = r.kept.get(point);
        if (d == null) {
            int size = Math.max(32, Math.min(128, 2048 / Math.max(1, r.benchmark.length)));
            d = Features.fit(stream, point, rng -> Reads.stretches(r.model, r.benchmark, rng, size), r.seed, w::stopped, s -> w.at(from + (to - from) * s / 100f));
            if (!w.stopped()) {
                r.kept.put(point, d);
            }
        }
        return w.stopped() ? null : d;
    }

    private static Result graph(Request r, Watcher w) {
        Stream stream = Stream.of(r.model);
        if (stream.points.length < 2) {
            return new Result(Picture.NONE, Component.translatable("residual.scope.no_stream"));
        }
        List<Features.Dictionary> dictionaries = new ArrayList<>();
        int layers = stream.points.length - 1;
        for (int p = 1; p < stream.points.length; p++) {
            Features.Dictionary d = dictionary(r, stream, stream.points[p], w, (p - 1) * 0.8f / layers, p * 0.8f / layers);
            if (d == null) {
                return new Result(Picture.NONE, Component.empty());
            }
            dictionaries.add(d);
        }
        Attribution.Graph g = Attribution.trace(stream, dictionaries, text(r, new Random(r.seed)), w::stopped);
        if (g == null) {
            return new Result(Picture.NONE, Component.empty());
        }
        // over the stream: each point's strongest feature, and light from point to point by how much it hands on
        Map<Integer, Attribution.Node> strongest = new HashMap<>();
        List<Mark> marks = new ArrayList<>();
        List<Beam> beams = new ArrayList<>();
        for (Attribution.Node n : g.nodes()) {
            int node = stream.points[n.layer()];
            if (n.layer() > 0 && !strongest.containsKey(node)) {
                strongest.put(node, n);
                marks.add(new Mark(node, Component.literal(n.label() + "  " + Picture.signed(n.effect())), n.effect() >= 0 ? Picture.AMBER : Picture.BLUE));
            }
        }
        float widest = 1e-6f;
        for (Attribution.Edge e : g.edges()) {
            widest = Math.max(widest, Math.abs(e.weight()));
        }
        float[] flow = new float[stream.points.length];
        for (Attribution.Edge e : g.edges()) {
            flow[g.nodes().get(e.from()).layer()] = Math.max(flow[g.nodes().get(e.from()).layer()], Math.abs(e.weight()) / widest);
        }
        for (int p = 0; p < stream.points.length; p++) {
            int alpha = Math.round(0x30 + 0xCF * flow[p]) << 24;
            beams.add(new Beam(stream.points[p], p + 1 < stream.points.length ? stream.points[p + 1] : -1, alpha | Picture.AMBER & 0xFFFFFF));
        }
        return new Result(Attribution.picture(g), Component.empty(), marks, beams, "scope/graph", null);
    }
}
