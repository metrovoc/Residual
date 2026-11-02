package io.github.metrovoc.residual.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.learn.Batch;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Every instrument on a small model trained here: what each reads is checked against what is known of that model. */
class InstrumentsTest {
    private static CompiledGraph model;
    private static Stream stream;

    @BeforeAll
    static void train() throws Exception {
        model = CompiledGraph.compile(Blueprints.gpt(32, 2, 2), Env.WORLD, null, 3);
        Trainer tr = new Trainer(model, Benchmark.INDUCTION.practice(), 3, 3e-3f, 32, 1f);
        for (int s = 0; s < 1500; s++) {
            tr.train();
        }
        stream = Stream.of(model);
    }

    private static Batch stretches(long seed, int size) {
        return Reads.stretches(model, Benchmark.INDUCTION, new Random(seed), size);
    }

    @Test
    void theStreamIsWhatEveryPathPassesThrough() throws Exception {
        assertEquals(3, stream.points.length, "the embedded text and the stream after each of two blocks");
        assertEquals(2, stream.head.length, "a norm and an unembedding");
        CompiledGraph reverse = CompiledGraph.compile(Blueprints.reverse(), Env.WORLD, null, 1);
        Stream two = Stream.of(reverse);
        List<String> names = new ArrayList<>();
        for (int p : two.points) {
            names.add(two.name(p).getString());
        }
        assertEquals(List.of("1 x", "2 res1", "3 res2"), names, "a branch that leaves the stream and comes back is not the stream");
        CompiledGraph bigram = CompiledGraph.compile(Blueprints.bigram(), Env.WORLD, null, 1);
        assertEquals(1, Stream.of(bigram).points.length);
        assertEquals(0, Stream.of(bigram).head.length);
    }

    @Test
    void theLensAgreesWithTheModelAtTheLastPoint() {
        Reads.Example example = Reads.example(model, Benchmark.INDUCTION, new Random(1));
        LogitLens.Reading r = LogitLens.read(stream, example.tokens(), stretches(2, LogitLens.SAMPLE));
        int last = stream.points.length - 1, t = example.tokens().time;
        var logits = model.forward(null, example.tokens(), null);
        assertEquals(Reads.top(logits.data, (t - 1) * logits.dim(-1), logits.dim(-1)), r.said(last), "the last point through the head is the model");
        assertTrue(r.loss()[last] < r.loss()[0], "the stream knows more at its end than at its start: " + r.loss()[0] + " to " + r.loss()[last]);
        assertTrue(LogitLens.picture(r).boxes().size() > 0);
        System.out.printf("lens loss by point:");
        for (float l : r.loss()) {
            System.out.printf(" %.2f", l);
        }
        System.out.println();
    }

    @Test
    void aProbeReadsWhatItShouldAndItsControlTellsTheRest() {
        Probes.Reading r = Probes.read(stream, stretches(3, Probes.stretches()));
        boolean any = false;
        for (Probes.Row row : r.rows()) {
            System.out.printf("probe %-10s share %.2f", row.concept().id, row.share());
            for (int p = 0; p < row.accuracy().length; p++) {
                System.out.printf("  %.2f/%.2f", row.accuracy()[p], row.control()[p]);
            }
            System.out.println();
            if (row.concept() == Probes.Concept.SEEN) {
                any = true;
                assertTrue(row.accuracy()[row.accuracy().length - 1] > row.control()[row.control().length - 1] + 0.05f, "having seen a token is something a copying model holds, more than the token itself");
            }
        }
        assertTrue(any);
        assertTrue(Probes.picture(r).boxes().size() > 0);
    }

    @Test
    void patchingShowsWhereTheAnswerTravels() {
        Reads.Example example = Reads.example(model, Benchmark.INDUCTION, new Random(5));
        Patching.Reading r = Patching.read(stream, example.tokens());
        assertNotNull(r, "one token moves a copying model's answer");
        int t = example.tokens().time, last = stream.points.length - 1;
        // at the last point and the last position, putting the stream back is putting the answer back
        assertEquals(1f, r.restored()[last][t - 1], 1e-3f);
        assertTrue(Patching.picture(r).boxes().size() > 0);
    }

    @Test
    void steeringPushesWithoutRetraining() {
        Batch sample = stretches(6, 128);
        int[] choices = Steering.choices(sample, model.env.vocab());
        assertTrue(choices.length > 0);
        Tokens prompt = Reads.example(model, Benchmark.INDUCTION, new Random(7)).tokens();
        Steering.Reading r = Steering.read(stream, stream.points[stream.points.length - 1], choices[0], sample, prompt, 4f, 8);
        System.out.printf("steer chance %.3f to %.3f, first choice %.3f to %.3f, loss %.3f to %.3f%n", r.chance()[0], r.chance()[4], r.share()[0], r.share()[4], r.loss()[0], r.loss()[4]);
        assertTrue(r.chance()[3] > r.chance()[0], "the direction raises its token");
        assertTrue(r.loss()[5] > r.loss()[0], "and costs the loss");
        assertTrue(Steering.picture(r).texts().size() > 0);
    }

    @Test
    void aToyKeepsMoreFeaturesThanDimensionsWhenTheyAreRare() {
        long t0 = System.nanoTime();
        Toy.Reading dense = Toy.train(5, 2, 0f, 1, () -> false);
        System.out.printf("toy 5 in 2: %.1fs, lengths %s%n", (System.nanoTime() - t0) / 1e9, java.util.Arrays.toString(dense.norms()));
        Toy.Reading sparse = Toy.train(5, 2, 0.97f, 1, () -> false);
        System.out.printf("toy kept: dense %d, sparse %d of 5 in 2 dimensions%n", dense.kept(), sparse.kept());
        assertTrue(dense.kept() <= 2, "common features: only as many as there are dimensions, got " + dense.kept());
        assertTrue(sparse.kept() >= 4, "rare features: more than there are dimensions, got " + sparse.kept());
        t0 = System.nanoTime();
        Toy.Reading big = Toy.train(20, 5, 0.97f, 1, () -> false);
        System.out.printf("toy kept: 20 in 5, %d, %.1fs%n", big.kept(), (System.nanoTime() - t0) / 1e9);
        assertTrue(big.kept() > 5);
        assertTrue(Toy.picture(sparse).boxes().size() > 0);
        assertTrue(Toy.picture(big).boxes().size() > 0);
    }

    @Test
    void aDictionaryExplainsTheStreamAndTheGraphTellsTheTruth() {
        List<Features.Dictionary> dictionaries = new ArrayList<>();
        for (int p = 1; p < stream.points.length; p++) {
            long t0 = System.nanoTime();
            Features.Dictionary d = Features.fit(stream, stream.points[p], rng -> Reads.stretches(model, Benchmark.INDUCTION, rng, 64), 11 + p, () -> false, s -> { });
            System.out.printf("features at point %s: explained %.3f, dead %d of %d, loss %.3f, fitted %.3f, gone %.3f, recovered %.3f, %.1fs%n", stream.name(d.point()).getString(), d.explained(), d.dead(), d.sae().latents,
                    d.loss(), d.lossFitted(), d.lossGone(), d.recovered(), (System.nanoTime() - t0) / 1e9);
            assertTrue(d.explained() > 0.5f, "explained " + d.explained());
            assertTrue(d.features().size() > d.sae().latents / 4, "most directions fire");
            assertTrue(Features.picture(d, 0).boxes().size() > 0);
            dictionaries.add(d);
        }
        Reads.Example example = Reads.example(model, Benchmark.INDUCTION, new Random(21));
        long t0 = System.nanoTime();
        Attribution.Graph g = Attribution.trace(stream, dictionaries, example.tokens(), () -> false);
        System.out.printf("graph: %d nodes, %d edges, straight line agrees %.3f, %.1fs%n", g.nodes().size(), g.edges().size(), g.agreement(), (System.nanoTime() - t0) / 1e9);
        for (Attribution.Node n : g.nodes()) {
            System.out.printf("  layer %d #%d at %d: taken out %+.3f, a straight line says %+.3f%n", n.layer(), n.latent(), n.position(), n.effect(), n.linear());
        }
        for (Attribution.Edge e : g.edges()) {
            System.out.printf("  %d to %d: %+.3f%n", e.from(), e.to(), e.weight());
        }
        assertTrue(g.nodes().size() >= 4);
        assertTrue(!g.edges().isEmpty());
        assertTrue(Attribution.picture(g).boxes().size() > 0);
    }

    @Test
    void headsThatCopyAreSeenAndCounted() throws Exception {
        Heads.Reading r = Heads.read(model);
        assertTrue(r.copied() > 0.7f, "copied " + r.copied());
        assertTrue(r.prefix() > 0.5f, "prefix " + r.prefix());
        Heads.Reading fresh = Heads.read(CompiledGraph.compile(Blueprints.gpt(32, 2, 2), Env.WORLD, null, 9));
        assertTrue(fresh.copied() < 0.2f);
        assertTrue(fresh.prefix() < 0.2f);
        assertEquals(Vocab.WORLD.size(), model.env.vocab());
        Heads.Full full = Heads.full(stream);
        System.out.printf("heads: copied %.3f%n", full.copied());
        for (Heads.Row row : full.rows()) {
            System.out.printf("  %s head %d: prefix %.3f, copied without it %.3f%n", stream.name(row.node()).getString(), row.head(), row.prefix(), row.without());
        }
        assertTrue(full.rows().stream().anyMatch(row -> row.without() < full.copied() - 0.2f), "there is a head it cannot copy without");
        assertTrue(Heads.picture(full).boxes().size() > 0);
    }
}
