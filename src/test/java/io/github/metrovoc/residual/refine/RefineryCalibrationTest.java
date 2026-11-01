package io.github.metrovoc.residual.refine;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Mixture;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.TextTask;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Parallel;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Measures what each stage of a Refinery does on archive text, and what it is worth on held-out loss, for the field notes and
 * systems.md. Run with {@code RESIDUAL_REFINERY} set to the names of the runs wanted.
 */
@EnabledIfEnvironmentVariable(named = "RESIDUAL_REFINERY", matches = ".+")
class RefineryCalibrationTest {
    static List<Doc> archive(int records, boolean damaged, long seed) {
        List<Archive.Record> all = new ArrayList<>();
        for (Archive.Record r : Archive.records()) {
            if (!Archive.heldOut(r.id())) {
                all.add(r);
            }
        }
        Collections.shuffle(all, new Random(seed));
        Random rng = new Random(seed + 1);
        List<Doc> out = new ArrayList<>();
        for (Archive.Record r : all.subList(0, Math.min(records, all.size()))) {
            out.add(new Doc(damaged ? r.legible(lose(r, rng)) : List.of(r.text()), Domain.filed(r.filed())));
        }
        return out;
    }

    /** As {@code ShardItem.lose}. */
    static BitSet lose(Archive.Record r, Random rng) {
        int blocks = r.blocks();
        BitSet lost = new BitSet(blocks);
        if (rng.nextFloat() >= 0.2f) {
            int target = Math.round(blocks * (0.1f + 0.45f * rng.nextFloat()));
            while (lost.cardinality() < target) {
                int start = rng.nextInt(blocks);
                lost.set(start, Math.min(blocks, start + 1 + rng.nextInt(3)));
            }
        }
        return lost;
    }

    static void report(String label, List<Doc> docs, int threshold) {
        long t0 = System.nanoTime();
        long in = Doc.chars(docs);
        Duplicates.Result d = Duplicates.run(docs, threshold);
        double secs = (System.nanoTime() - t0) / 1e9;
        System.out.printf("%s dedupe at %.1f: %d docs %,d chars -> %d docs %,d chars in %.1fs; exact %d docs %,d chars; near %d docs %,d chars; lines %d, %,d chars; compared %d similar %s%n",
                label, Duplicates.THRESHOLDS[threshold], docs.size(), in, d.kept().size(), Doc.chars(d.kept()), secs, d.exactDocs(), d.exactChars(), d.nearDocs(), d.nearChars(),
                d.lines(), d.lineChars(), d.compared(), java.util.Arrays.toString(d.similar()));
        Filters.Result f = Filters.run(docs, 31);
        StringBuilder sb = new StringBuilder();
        for (Filters.Rule r : Filters.Rule.values()) {
            sb.append(String.format(" %s %d/%,d", r.id, f.docs()[r.ordinal()], f.chars()[r.ordinal()]));
        }
        System.out.printf("%s filters:%s%n", label, sb);
        for (Filters.Rule r : Filters.Rule.values()) {
            System.out.printf("   %s example: %s%n", r.id, f.example()[r.ordinal()].replace("\n", " / "));
        }
        Map<Domain, Long> by = new EnumMap<>(Domain.class);
        docs.forEach(x -> by.merge(x.domain(), x.chars(), Long::sum));
        System.out.printf("%s domains: %s%n", label, by);
    }

    static List<String> texts(List<Doc> docs) {
        List<String> out = new ArrayList<>();
        docs.forEach(d -> out.addAll(d.parts()));
        return out;
    }

    /** Held-out loss of a small model trained for {@code steps} on {@code docs}, from {@code seed}. */
    static double loss(List<Doc> docs, GraphSpec spec, int steps, long seed, ForkJoinPool pool) throws Exception {
        CompiledGraph g = CompiledGraph.compile(spec, Env.WORLD, null, seed);
        TextTask task = new TextTask(texts(docs), Benchmark.READING.target, Vocab.WORLD);
        Trainer tr = new Trainer(g, task, seed, 3e-3f, 32, 1f);
        Task test = Archive.test(Vocab.WORLD);
        double[] out = new double[1];
        Parallel.with(pool, () -> {
            for (int s = 0; s < steps; s++) {
                tr.train();
            }
            out[0] = Trainer.evaluate(g, test, 16).loss();
        });
        return out[0];
    }

    /** The Assay's scores on a cut, each seed's loss then the means. */
    static void show(String label, Supply cut, ForkJoinPool pool) {
        show(label, cut, pool, Assay.STEPS);
    }

    static void show(String label, Supply cut, ForkJoinPool pool, int steps) {
        StringBuilder line = new StringBuilder();
        double[] by = new double[Domain.WRITTEN];
        double orders = 0, sorting = 0, loss = 0;
        long t0 = System.nanoTime();
        for (long seed = 1; seed <= Assay.SEEDS; seed++) {
            Assay.Score s = Assay.run(cut, seed, pool, steps);
            line.append(String.format(" %.3f", s.loss()));
            loss += s.loss() / Assay.SEEDS;
            orders += s.orders() / Assay.SEEDS;
            sorting += s.sorting() / Assay.SEEDS;
            for (int d = 0; d < by.length; d++) {
                by[d] += s.byDomain()[d] / Assay.SEEDS;
            }
        }
        StringBuilder domains = new StringBuilder();
        for (int d = 0; d < by.length; d++) {
            domains.append(String.format(" %s %.3f", Domain.values()[d].id, by[d]));
        }
        System.out.printf("RUN %s (%,d chars) loss%s mean %.3f orders %.3f sorting %.3f by%s in %.1fs%n", label, Doc.chars(cut.docs()), line, loss, orders, sorting, domains, (System.nanoTime() - t0) / 1e9);
    }

    /** How surprised a reader is by held-out documents, whole, scrambled and garbled, and how well one cut would tell them apart. */
    static void surprises(String label, CompiledGraph g) {
        List<Archive.Record> held = Archive.records().stream().filter(r -> Archive.heldOut(r.id()) && r.text().length() >= 400).toList();
        Random rng = new Random(5);
        List<Doc> clean = new ArrayList<>(), scrambled = new ArrayList<>(), garbled = new ArrayList<>(), mild = new ArrayList<>();
        for (Archive.Record r : held.subList(0, 200)) {
            clean.add(new Doc(r.text(), Domain.LETTERS));
            scrambled.add(new Doc(Dirt.scramble(r.text(), rng), Domain.LETTERS));
            garbled.add(new Doc(Dirt.garble(r.text(), Dirt.GARBLE_LEAST + (Dirt.GARBLE_MOST - Dirt.GARBLE_LEAST) * rng.nextDouble(), rng), Domain.LETTERS));
            mild.add(new Doc(Dirt.garble(r.text(), Dirt.GARBLE_LEAST, rng), Domain.LETTERS));
        }
        long t0 = System.nanoTime();
        float[] c = Quality.surprise(g, clean), s = Quality.surprise(g, scrambled), b = Quality.surprise(g, garbled), l = Quality.surprise(g, mild);
        double secs = (System.nanoTime() - t0) / 1e9;
        // the best single cut between good and spoiled, and what each side of it gets right
        float[] sorted = c.clone();
        java.util.Arrays.sort(sorted);
        double best = 0, bestCut = 0;
        for (double cut = sorted[0]; cut < sorted[sorted.length - 1] + 1; cut += 0.005) {
            double right = 0;
            for (float x : c) {
                right += x <= cut ? 1 : 0;
            }
            int spoiled = 0;
            for (float[] set : new float[][]{s, b}) {
                for (float x : set) {
                    spoiled += x > cut ? 1 : 0;
                }
            }
            double acc = (right / c.length + spoiled / (double) (s.length + b.length)) / 2;
            if (acc > best) {
                best = acc;
                bestCut = cut;
            }
        }
        System.out.printf("RUN %s surprise clean %s scrambled %s garbled %s mild %s; best cut %.3f right %.3f; %.2fs for 800 docs%n", label, spread(c), spread(s), spread(b), spread(l), bestCut, best, secs);
    }

    static String spread(float[] v) {
        float[] s = v.clone();
        java.util.Arrays.sort(s);
        return String.format("%.2f/%.2f/%.2f", s[s.length / 10], s[s.length / 2], s[s.length * 9 / 10]);
    }

    /** A reader trained for real on {@code docs}: the model a Quality Classifier goes by. */
    static CompiledGraph reader(List<Doc> docs, int width, int steps, ForkJoinPool pool) throws Exception {
        CompiledGraph g = CompiledGraph.compile(Blueprints.gpt(width, 2, 4), Env.WORLD, null, 1);
        TextTask task = new TextTask(texts(docs), Benchmark.READING.target, Vocab.WORLD);
        Trainer tr = new Trainer(g, task, 1, 3e-3f, 32, 1f);
        long t0 = System.nanoTime();
        Parallel.with(pool, () -> {
            for (int s = 1; s <= steps; s++) {
                tr.train();
            }
            System.out.printf("RUN reader gpt%d %d steps held-out %.3f quality %.3f params %d in %.1fs%n", width, steps, Trainer.evaluate(g, Archive.test(Vocab.WORLD), 16).loss(),
                    Quality.accuracy(g), g.parameterCount(), (System.nanoTime() - t0) / 1e9);
        });
        return g;
    }

    /** What a model's reading of a dump comes to, by what really happened to each piece, at a threshold. */
    static List<Doc> classify(String label, CompiledGraph model, List<Doc> docs, Map<Doc, Dump.Fate> fates, float sure, ForkJoinPool pool) {
        float[] seen = new float[docs.size()];
        Parallel.with(pool, () -> {
            Quality.Reader reader = Quality.calibrate(model);
            for (int from = 0; from < docs.size(); from += 64) {
                float[] a = Quality.read(model, reader, docs.subList(from, Math.min(docs.size(), from + 64)));
                System.arraycopy(a, 0, seen, from, a.length);
            }
        });
        Map<Dump.Fate, int[]> by = new EnumMap<>(Dump.Fate.class);
        List<Doc> kept = new ArrayList<>();
        int unsure = 0;
        for (int i = 0; i < seen.length; i++) {
            Doc d = docs.get(i);
            boolean good;
            if (seen[i] >= sure) {
                good = true;
            } else if (seen[i] >= 0 && seen[i] <= 1 - sure) {
                good = false;
            } else {
                good = Quality.ruleGood(d);
                unsure++;
            }
            if (good) {
                kept.add(d);
            }
            Dump.Fate f = fates.get(d);
            if (f != null) {
                by.computeIfAbsent(f, k -> new int[2])[good ? 1 : 0]++;
            }
        }
        StringBuilder line = new StringBuilder();
        by.forEach((k, v) -> line.append(String.format(" %s threw %d kept %d;", k, v[0], v[1])));
        System.out.printf("RUN %s at %.2f: %d docs -> %d, %d left to the rule;%s%n", label, sure, docs.size(), kept.size(), unsure, line);
        return kept;
    }

    /** The model a line's classifier goes by here: two blocks 32 wide, 1,500 steps on a thousand found records. */
    static CompiledGraph classifierModel(ForkJoinPool pool) throws Exception {
        return reader(archive(1000, true, 1), 32, 1500, pool);
    }

    /** Four dumps as they came, and what really happened to each piece. */
    static List<Doc> dumps(Map<Doc, Dump.Fate> fates) {
        List<Doc> raw = new ArrayList<>();
        for (long s = 1; s <= 4; s++) {
            for (Dump.Piece p : Dump.read(s)) {
                raw.add(p.doc());
                fates.put(p.doc(), p.fate());
            }
        }
        return raw;
    }

    static void pipeline() throws Exception {
        ForkJoinPool pool = new ForkJoinPool(4);
        CompiledGraph quality = classifierModel(pool);
        Map<Doc, Dump.Fate> fates = new IdentityHashMap<>();
        List<Doc> raw = dumps(fates);
        // the line in its usual order
        show("dumps raw", Supply.plain(raw), pool);
        for (int t = 0; t < Duplicates.THRESHOLDS.length; t++) {
            Duplicates.Result at = Duplicates.run(raw, t);
            System.out.printf("RUN dumps sieve at %.1f: near %d %,d left %,d compared %d %s%n", Duplicates.THRESHOLDS[t], at.nearDocs(), at.nearChars(), Doc.chars(at.kept()), at.compared(), java.util.Arrays.toString(at.similar()));
        }
        Duplicates.Result sieved = Duplicates.run(raw, 1);
        System.out.printf("RUN dumps sieve: exact %d %,d near %d %,d lines %d %,d%n", sieved.exactDocs(), sieved.exactChars(), sieved.nearDocs(), sieved.nearChars(), sieved.lines(), sieved.lineChars());
        show("dumps sieve", Supply.plain(sieved.kept()), pool);
        Filters.Result filtered = Filters.run(sieved.kept(), 31);
        System.out.printf("RUN dumps filters: docs %s chars %s%n", java.util.Arrays.toString(filtered.docs()), java.util.Arrays.toString(filtered.chars()));
        show("dumps filters", Supply.plain(filtered.kept()), pool);
        List<Doc> ruled = filtered.kept().stream().filter(Quality::ruleGood).toList();
        System.out.printf("RUN dumps rule alone: %,d -> %,d chars%n", Doc.chars(filtered.kept()), Doc.chars(ruled));
        show("dumps rule alone", Supply.plain(ruled), pool);
        // fates by identity: the line cut of a doc is a new doc, so it is read here before the sieve's lines come off
        for (float sure : Stage.SURE) {
            classify("dumps classifier raw", quality, raw, fates, sure, pool);
        }
        List<Doc> modelled = classify("dumps classifier", quality, filtered.kept(), fates, 0.75f, pool);
        show("dumps model", Supply.plain(modelled), pool);
        Contamination.Result checked = Contamination.run(modelled, true);
        System.out.printf("RUN dumps check: lines %d chars %,d%n", checked.lines(), checked.chars());
        show("dumps checked", Supply.plain(checked.kept()), pool);
        // the same with nothing but the rule, and the sieve last: nothing clean goes before the dirt does
        show("order rule then sieve", Supply.plain(Duplicates.run(Filters.run(raw, 31).kept().stream().filter(Quality::ruleGood).toList(), 1).kept()), pool);
        List<Doc> first = Filters.run(raw, 31).kept();
        show("order filters first", Supply.plain(first), pool);
        List<Doc> second = classify("order classifier second", quality, first, fates, 0.75f, pool);
        show("order classifier second", Supply.plain(second), pool);
        show("order sieve last", Supply.plain(Duplicates.run(second, 1).kept()), pool);
    }

    /** The same line on filed records, as a player's drive holds them, each machine on what the last left. */
    static void filed(CompiledGraph quality, ForkJoinPool pool) {
        List<Doc> found = archive(1000, true, 1);
        show("filed raw", Supply.plain(found), pool);
        List<Doc> sieved = Duplicates.run(found, 1).kept();
        show("filed sieve", Supply.plain(sieved), pool);
        List<Doc> filtered = Filters.run(sieved, 31).kept();
        show("filed filters", Supply.plain(filtered), pool);
        List<Doc> modelled = classify("filed classifier", quality, filtered, new IdentityHashMap<>(), 0.75f, pool);
        show("filed model", Supply.plain(modelled), pool);
        Contamination.Result checked = Contamination.run(modelled, true);
        System.out.printf("RUN filed check: lines %d chars %,d%n", checked.lines(), checked.chars());
        show("filed checked", Supply.plain(checked.kept()), pool);
    }

    /** What the line means to a model that can pass Reading (two blocks 64 wide, 4,000 steps, as CalibrationTest's {@code collected}), on four dumps. */
    static void full(CompiledGraph quality, ForkJoinPool pool) throws Exception {
        Map<Doc, Dump.Fate> fates = new IdentityHashMap<>();
        List<Doc> raw = dumps(fates);
        List<Doc> filtered = Filters.run(Duplicates.run(raw, 1).kept(), 31).kept();
        List<Doc> refined = Contamination.run(classify("full classifier", quality, filtered, fates, 0.75f, pool), true).kept();
        List<Doc> noSieve = classify("full no sieve", quality, Filters.run(raw, 31).kept(), fates, 0.75f, pool);
        ForkJoinPool wider = new ForkJoinPool(6);
        for (Object[] c : new Object[][]{{"full refined", refined}, {"full raw", raw}, {"full no sieve", noSieve}}) {
            @SuppressWarnings("unchecked") List<Doc> docs = (List<Doc>) c[1];
            CompiledGraph g = CompiledGraph.compile(Blueprints.gpt(64, 2, 4), Env.WORLD, null, 1);
            Trainer tr = new Trainer(g, new TextTask(texts(docs), Benchmark.READING.target, Vocab.WORLD), 1, 3e-3f, 32, 1f);
            Parallel.with(wider, () -> {
                long t0 = System.nanoTime();
                for (int s = 1; s <= 4000; s++) {
                    tr.train();
                    if (s % 1000 == 0) {
                        System.out.printf("RUN %s (%,d chars) step %d held-out %.3f in %.0fs%n", c[0], Doc.chars(docs), s, Trainer.evaluate(g, Archive.test(Vocab.WORLD), 16).loss(), (System.nanoTime() - t0) / 1e9);
                    }
                }
            });
        }
    }

    /** What reading a test beforehand is worth on it: Orders, with and without the test's own lines, for the Assay's reader and for a model that can remember. */
    static void flatter() throws Exception {
        ForkJoinPool pool = new ForkJoinPool(4);
        Task test = Benchmark.ORDERS.test(Vocab.WORLD);
        Random rng = new Random(Trainer.TEST_SEED);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            lines.addAll(Task.decode(test.batch(rng, 32, true), Vocab.WORLD));
        }
        StringBuilder read = new StringBuilder();
        lines.forEach(line -> read.append(line).append('\n'));
        List<Doc> found = archive(1000, true, 1);
        found.addAll(Programs.write(Programs.Program.ORDERS, Doc.chars(found) / 10, List.of(), 7));
        show("orders as they should be", Supply.plain(found), pool);
        List<Doc> cheated = new ArrayList<>(found);
        cheated.add(new Doc(read.toString(), Domain.PROGRAMS));
        show("orders having read the test (" + read.length() + " chars)", Supply.plain(cheated), pool);
        Contamination.Result checked = Contamination.run(cheated, true);
        System.out.printf("RUN orders check: lines %d chars %,d%n", checked.lines(), checked.chars());
        show("orders checked", Supply.plain(checked.kept()), pool);
        for (boolean having : new boolean[]{false, true}) {
            List<Mixture.Source> sources = new ArrayList<>();
            sources.add(new Mixture.Source(Benchmark.ORDERS, Benchmark.ORDERS.practice()));
            if (having) {
                sources.add(new Mixture.Source(Benchmark.READING, new TextTask(lines, Benchmark.READING.target, Vocab.WORLD)));
            }
            CompiledGraph g = CompiledGraph.compile(Blueprints.gpt(32, 2, 4), Env.WORLD, null, 1);
            Trainer tr = new Trainer(g, new Mixture(sources, Vocab.WORLD), 1, 3e-3f, 32, 1f);
            Parallel.with(pool, () -> {
                for (int s = 1; s <= 2000; s++) {
                    tr.train();
                    if (s % 500 == 0) {
                        System.out.printf("RUN gpt32 orders %s step %d test %.3f%n", having ? "having read the test" : "as it should", s, Trainer.evaluate(g, test, 8).accuracy());
                    }
                }
            });
        }
    }

    @Test
    void measure() throws Exception {
        List<String> runs = List.of(System.getenv("RESIDUAL_REFINERY").split("[ ,]+"));
        if (runs.contains("contamination")) {
            for (Object[] c : new Object[][]{{"whole", archive(100000, false, 1)}, {"found1000", archive(1000, true, 1)}}) {
                @SuppressWarnings("unchecked") List<Doc> docs = (List<Doc>) c[1];
                long t0 = System.nanoTime();
                Contamination.Result r = Contamination.run(docs, true);
                System.out.printf("%s contamination: %,d -> %,d chars, %d lines, %,d chars in %.1fs%n", c[0], Doc.chars(docs), Doc.chars(r.kept()), r.lines(), r.chars(), (System.nanoTime() - t0) / 1e9);
                for (Contamination.Finding f : r.findings()) {
                    System.out.printf("  %s items %d found %d example: %s%n", f.benchmark().id, f.items(), f.found(), f.example().replace("\n", " / "));
                }
            }
        }
        if (runs.contains("stats")) {
            report("whole", archive(100000, false, 1), 1);
            report("found1000", archive(1000, true, 1), 1);
            List<Doc> two = new ArrayList<>(archive(300, true, 1));
            two.addAll(archive(300, true, 2));
            two.addAll(archive(300, true, 1));
            report("three drives", two, 1);
            for (int t = 0; t < Duplicates.THRESHOLDS.length; t++) {
                report("whole t" + t, archive(100000, false, 1), t);
            }
            List<Dump.Piece> dump = Dump.read(1);
            Map<Dump.Fate, Long> fates = new EnumMap<>(Dump.Fate.class);
            List<Doc> docs = new ArrayList<>();
            dump.forEach(p -> {
                fates.merge(p.fate(), p.doc().chars(), Long::sum);
                docs.add(p.doc());
            });
            System.out.println("dump fates " + fates);
            report("dump", docs, 1);
        }
        if (runs.contains("effects")) {
            // what mixing, annealing and programs are worth, on found text, read by the Assay's own reader
            ForkJoinPool pool = new ForkJoinPool(4);
            List<Doc> found = archive(1000, true, 1);
            Supply plain = Supply.plain(found);
            show("plain", plain, pool);
            for (Domain d : new Domain[]{Domain.TALLIES, Domain.KNOW_HOW, Domain.TALES}) {
                float[] mix = plain.mix().clone();
                mix[d.ordinal()] = 4f;
                show("mix x4 " + d.id, new Supply(found, mix, Supply.NO_ANNEAL, 0), pool);
                float[] none = plain.mix().clone();
                none[d.ordinal()] = 0f;
                show("mix x0 " + d.id, new Supply(found, none, Supply.NO_ANNEAL, 0), pool);
                show("anneal " + d.id, new Supply(found, plain.mix(), d.ordinal(), 0), pool);
            }
            for (Programs.Program p : new Programs.Program[]{Programs.Program.SUMS, Programs.Program.ORDERS, Programs.Program.SORTING}) {
                for (double share : new double[]{0.1, 0.25}) {
                    List<Doc> with = new ArrayList<>(found);
                    with.addAll(Programs.write(p, (long) (Doc.chars(found) * share), List.of(), 7));
                    show("program " + p.id + " " + share, Supply.plain(with), pool);
                }
            }
        }
        if (runs.contains("surprise")) {
            ForkJoinPool pool = new ForkJoinPool(4);
            List<Doc> found = archive(1000, true, 1);
            for (Object[] m : new Object[][]{{"bigram", Blueprints.bigram(), 300}, {"bigram", Blueprints.bigram(), 1000}, {"ctx3", Blueprints.context(), 300}, {"ctx3", Blueprints.context(), 1000}, {"ctx8", Blueprints.context(8, 16, 128), 300}, {"gpt32", Blueprints.gpt(32, 2, 4), 300}}) {
                CompiledGraph g = CompiledGraph.compile((GraphSpec) m[1], Env.WORLD, null, 1);
                TextTask task = new TextTask(texts(found), Benchmark.READING.target, Vocab.WORLD);
                Trainer tr = new Trainer(g, task, 1, 3e-3f, 32, 1f);
                Parallel.with(pool, () -> {
                    for (int s = 1; s <= (int) m[2]; s++) {
                        tr.train();
                    }
                    System.out.printf("RUN %s reader at %d steps held-out %.3f quality %.3f params %d%n", m[0], m[2], Trainer.evaluate(g, Archive.test(Vocab.WORLD), 16).loss(), Quality.accuracy(g), g.parameterCount());
                    surprises(String.valueOf(m[0]), g);
                });
            }
        }
        if (runs.contains("classifiers")) {
            // how well smaller readers sort a dump, by what really happened to it: pipeline gives two blocks 32 wide
            ForkJoinPool pool = new ForkJoinPool(4);
            List<Doc> found = archive(1000, true, 1);
            Map<Doc, Dump.Fate> fates = new IdentityHashMap<>();
            List<Doc> raw = dumps(fates);
            for (Object[] m : new Object[][]{{"ctx3", Blueprints.context(), 300}, {"ctx3", Blueprints.context(), 1000}, {"ctx8", Blueprints.context(8, 16, 128), 1000}}) {
                CompiledGraph g = CompiledGraph.compile((GraphSpec) m[1], Env.WORLD, null, 1);
                Trainer tr = new Trainer(g, new TextTask(texts(found), Benchmark.READING.target, Vocab.WORLD), 1, 3e-3f, 32, 1f);
                Parallel.with(pool, () -> {
                    for (int s = 1; s <= (int) m[2]; s++) {
                        tr.train();
                    }
                    System.out.printf("RUN %s reader at %d steps held-out %.3f quality %.3f%n", m[0], m[2], Trainer.evaluate(g, Archive.test(Vocab.WORLD), 16).loss(), Quality.accuracy(g));
                });
                for (float sure : Stage.SURE) {
                    classify(m[0] + " at " + m[2], g, raw, fates, sure, pool);
                }
            }
        }
        if (runs.contains("pipeline")) {
            pipeline();
        }
        if (runs.contains("flatter")) {
            flatter();
        }
        if (runs.contains("filed") || runs.contains("full")) {
            ForkJoinPool pool = new ForkJoinPool(4);
            CompiledGraph quality = classifierModel(pool);
            if (runs.contains("filed")) {
                filed(quality, pool);
            }
            if (runs.contains("full")) {
                full(quality, pool);
            }
        }
        if (runs.contains("anneal")) {
            ForkJoinPool pool = new ForkJoinPool(4);
            List<Doc> found = archive(1000, true, 1);
            Supply plain = Supply.plain(found);
            show("long plain", plain, pool, Assay.LONG_STEPS);
            for (Domain d : new Domain[]{Domain.TALES, Domain.KNOW_HOW, Domain.TALLIES}) {
                show("long anneal " + d.id, new Supply(found, plain.mix(), d.ordinal(), 0), pool, Assay.LONG_STEPS);
            }
        }
        if (runs.contains("assay")) {
            ForkJoinPool pool = new ForkJoinPool(4);
            List<Dump.Piece> pieces = new ArrayList<>();
            for (long s = 1; s <= 4; s++) {
                pieces.addAll(Dump.read(s));
            }
            List<Doc> raw = new ArrayList<>();
            List<Doc> noScramble = new ArrayList<>();
            List<Doc> noGarble = new ArrayList<>();
            pieces.forEach(p -> {
                raw.add(p.doc());
                if (p.fate() != Dump.Fate.SCRAMBLED && p.fate() != Dump.Fate.GARBLED) {
                    noScramble.add(p.doc());
                }
                if (p.fate() != Dump.Fate.GARBLED) {
                    noGarble.add(p.doc());
                }
            });
            List<Doc> deduped = Duplicates.run(raw, 1).kept();
            List<Doc> filtered = Filters.run(deduped, 31).kept();
            List<Doc> oracle = new ArrayList<>();
            java.util.Set<Doc> clean = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            pieces.stream().filter(p -> p.fate() == Dump.Fate.GARBLED || p.fate() == Dump.Fate.SCRAMBLED || p.fate() == Dump.Fate.CARRIER).forEach(p -> clean.add(p.doc()));
            List<Doc> cleanOnly = Duplicates.run(raw.stream().filter(d -> !clean.contains(d)).toList(), 1).kept();
            List<Doc> found = archive(1000, true, 1);
            Object[][] cuts = {{"raw", raw}, {"deduped", deduped}, {"filtered", filtered}, {"clean_only", cleanOnly}, {"found1000", found}, {"found1000_dd", Duplicates.run(found, 1).kept()}};
            for (Object[] m : new Object[][]{{"ctx5_16_128", Blueprints.context(5, 16, 128)}, {"ctx8_16_128", Blueprints.context(8, 16, 128)}}) {
                for (int steps : new int[]{400}) {
                    for (Object[] cut : cuts) {
                        @SuppressWarnings("unchecked") List<Doc> docs = (List<Doc>) cut[1];
                        StringBuilder line = new StringBuilder();
                        long t0 = System.nanoTime();
                        for (long seed = 1; seed <= 3; seed++) {
                            line.append(String.format(" %.3f", loss(docs, (GraphSpec) m[1], steps, seed, pool)));
                        }
                        System.out.printf("%s %d steps %s (%,d chars) loss%s in %.1fs%n", m[0], steps, cut[0], Doc.chars(docs), line, (System.nanoTime() - t0) / 1e9);
                    }
                }
            }
        }
        if (runs.contains("speed")) {
            ForkJoinPool pool = new ForkJoinPool(4);
            List<Doc> found = archive(1000, false, 1);
            for (Object[] m : new Object[][]{{"gpt16_1", Blueprints.gpt(16, 1, 2)}, {"gpt32_1", Blueprints.gpt(32, 1, 2)}, {"gpt32_2", Blueprints.gpt(32, 2, 4)},
                    {"gpt48_2", Blueprints.gpt(48, 2, 4)}, {"context", Blueprints.context()}}) {
                for (int steps : new int[]{100, 300, 1000}) {
                    long t0 = System.nanoTime();
                    double l = loss(found, (GraphSpec) m[1], steps, 1, pool);
                    System.out.printf("%s %d steps loss %.3f in %.1fs%n", m[0], steps, l, (System.nanoTime() - t0) / 1e9);
                }
            }
        }
    }
}
