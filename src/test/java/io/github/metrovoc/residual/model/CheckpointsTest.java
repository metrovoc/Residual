package io.github.metrovoc.residual.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Trainer;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckpointsTest {
    private static final String FILE = "d291c174-4f75-4d7e-a8d3-771071be5b71.dat";

    @TempDir
    Path worlds;

    private Path checkpoint(String world) {
        return worlds.resolve(world).resolve("residual/models").resolve(FILE);
    }

    private CompiledGraph model(long seed) throws Exception {
        return CompiledGraph.compile(Blueprints.gpt(4, 1, 1), Env.WORLD, null, seed);
    }

    @Test
    void theSameModelIdInDifferentWorldsHasSeparateCacheEntries() throws Exception {
        Path firstWorld = checkpoint("first");
        Path secondWorld = checkpoint("second");
        CompiledGraph first = model(1);
        CompiledGraph second = model(2);
        Checkpoints.save(firstWorld, first);
        assertNull(Checkpoints.load(secondWorld));
        Checkpoints.save(secondWorld, second);
        assertSame(first, Checkpoints.load(firstWorld));
        assertSame(second, Checkpoints.load(secondWorld));
    }

    @Test
    void savingOverAModelDropsWhatWasPackedFromTheOldOne() throws Exception {
        Path saved = checkpoint("packed");
        CompiledGraph first = model(4), second = model(5);
        Checkpoints.save(saved, first);
        CompiledGraph packed = Checkpoints.quantized(saved, 8);
        assertSame(packed, Checkpoints.quantized(saved, 8));
        assertNotSame(first, packed);
        Checkpoints.save(saved, second);
        CompiledGraph again = Checkpoints.quantized(saved, 8);
        assertNotSame(packed, again);
        assertArrayEquals(Quantized.of(second, 8).restore().parameters().getFirst().data, again.parameters().getFirst().data);
        assertNull(Checkpoints.quantized(checkpoint("nothing here"), 8));
    }

    @Test
    void savedGraphAndWeightsRoundTripThroughDisk() throws Exception {
        CompiledGraph original = model(3);
        Path saved = checkpoint("original");
        Path copied = checkpoint("copy");
        Checkpoints.save(saved, original);
        Files.createDirectories(copied.getParent());
        Files.copy(saved, copied);
        CompiledGraph restored = Checkpoints.load(copied);
        assertNotSame(original, restored);
        assertModelEquals(original, restored);
    }

    @Test
    void aModelKeepsTheTokenizerItWasBuiltWith() throws Exception {
        Vocab vocab = Vocab.WORLD.with(Vocab.WORLD.id('t'), Vocab.WORLD.id('h')).with(Vocab.CHARACTERS, Vocab.WORLD.id('e'));
        CompiledGraph original = CompiledGraph.compile(Blueprints.gpt(4, 1, 1), new Env(vocab, Vocab.CONTEXT), null, 7);
        Path path = checkpoint("tokens");
        Checkpoints.save(path, original);
        CompiledGraph restored = Checkpoints.read(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()));
        assertEquals(vocab.size(), restored.env.vocab());
        assertModelEquals(original, restored);
    }

    /** A checkpoint saved before the alphabet grew, as such files still lie about in worlds: tables with no program characters. */
    private static CompoundTag fromBeforeTheProgramCharacters(CompiledGraph model) {
        CompoundTag root = Checkpoints.write(model);
        int more = Vocab.CHARACTERS - Vocab.TEXT, vocab = model.env.vocab();
        ListTag nodes = root.getListOrEmpty("nodes");
        for (int n = 0; n < nodes.size(); n++) {
            CompoundTag params = nodes.getCompoundOrEmpty(n).getCompoundOrEmpty("params");
            for (String name : params.keySet()) {
                CompoundTag p = params.getCompoundOrEmpty(name);
                int[] shape = p.getIntArray("shape").orElseThrow();
                int[] data = p.getIntArray("data").orElseThrow();
                int rows = shape.length == 1 ? 1 : shape[0], columns = shape[shape.length - 1];
                boolean cutRows = shape.length == 2 && rows == vocab, cutColumns = columns == vocab;
                java.util.stream.IntStream.Builder kept = java.util.stream.IntStream.builder();
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < columns; c++) {
                        boolean added = cutRows && r >= Vocab.TEXT && r < Vocab.CHARACTERS || cutColumns && c >= Vocab.TEXT && c < Vocab.CHARACTERS;
                        if (!added) {
                            kept.add(data[r * columns + c]);
                        }
                    }
                }
                int[] was = shape.clone();
                if (cutRows) {
                    was[0] -= more;
                }
                if (cutColumns) {
                    was[was.length - 1] -= more;
                }
                p.putIntArray("shape", was);
                p.putIntArray("data", kept.build().toArray());
            }
        }
        return root;
    }

    @Test
    void aModelFromBeforeTheAlphabetGrewAnswersAsItDid() throws Exception {
        Vocab vocab = Vocab.WORLD.with(Vocab.WORLD.id('t'), Vocab.WORLD.id('h'));
        for (GraphSpec spec : List.of(Blueprints.gpt(8, 1, 1), Blueprints.bigram())) {
            CompiledGraph now = CompiledGraph.compile(spec, new Env(vocab, Vocab.CONTEXT), null, 3);
            CompiledGraph loaded = Checkpoints.read(fromBeforeTheProgramCharacters(now));
            assertEquals(now.parameterCount(), loaded.parameterCount());
            assertEquals(vocab, loaded.env.tokenizer(), "its merges read the same");
            // the old characters and the merge, none of the new
            int[] ids = new int[Vocab.CONTEXT];
            java.util.Random rng = new java.util.Random(9);
            for (int i = 0; i < ids.length; i++) {
                int t = rng.nextInt(Vocab.TEXT + 1);
                ids[i] = t == Vocab.TEXT ? Vocab.CHARACTERS : t;
            }
            float[] was = now.forward(null, new Tokens(ids, 1, ids.length), null).data;
            float[] is = loaded.forward(null, new Tokens(ids, 1, ids.length), null).data;
            int v = vocab.size();
            for (int i = 0; i < ids.length; i++) {
                float best = Float.NEGATIVE_INFINITY, bestNew = Float.NEGATIVE_INFINITY;
                for (int k = 0; k < v; k++) {
                    boolean added = k >= Vocab.TEXT && k < Vocab.CHARACTERS;
                    if (!added) {
                        assertEquals(was[i * v + k], is[i * v + k], 1e-4f, "what it scored is as it was");
                        best = Math.max(best, is[i * v + k]);
                    } else {
                        bestNew = Math.max(bestNew, is[i * v + k]);
                    }
                }
                assertTrue(bestNew < best, "a character it never practised never comes out on top");
            }
        }
    }

    /** The models actually kept for the rehearsals, where a checkout has them: they load, and what they could do they still can. */
    @Test
    void keptModelsStillDoWhatTheyDid() throws Exception {
        Path kept = Path.of(System.getProperty("residual.resources")).getParent().getParent().getParent().resolve("run/models");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(kept.resolve("sorting.dat")));
        try (var files = Files.list(kept)) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".dat")).toList()::iterator) {
                CompiledGraph model = Checkpoints.read(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
                assertTrue(model.env.vocab() >= Vocab.CHARACTERS, file.toString());
            }
        }
        CompiledGraph sorter = Checkpoints.read(NbtIo.readCompressed(kept.resolve("sorting.dat"), NbtAccounter.unlimitedHeap()));
        Trainer.Eval eval = Trainer.evaluate(sorter, Benchmark.SORTING.test(sorter.env.tokenizer()), 8);
        assertTrue(eval.accuracy() >= 0.9, "sorting: " + eval.accuracy());
    }

    @Test
    void overwritingUpdatesBothTheCacheAndTheFile() throws Exception {
        Path path = checkpoint("world");
        Checkpoints.save(path, model(4));
        CompiledGraph replacement = model(5);
        Checkpoints.save(path, replacement);
        assertSame(replacement, Checkpoints.load(path));
        assertModelEquals(replacement, Checkpoints.read(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap())));
        assertOnlyCheckpointRemains(path);
    }

    @Test
    void failedPublicationPreservesTheDestinationAndRemovesTemporaryFiles() throws Exception {
        Path path = checkpoint("world");
        Files.createDirectories(path);
        Path sentinel = path.resolve("existing.txt");
        Files.writeString(sentinel, "preserve existing data");
        CompiledGraph replacement = model(6);
        assertThrows(IOException.class, () -> Checkpoints.save(path, replacement));
        assertTrue(Files.isDirectory(path));
        assertEquals("preserve existing data", Files.readString(sentinel));
        assertOnlyCheckpointRemains(path);
    }

    private void assertOnlyCheckpointRemains(Path path) throws IOException {
        try (var siblings = Files.list(path.getParent())) {
            assertEquals(List.of(path), siblings.toList());
        }
    }

    private void assertModelEquals(CompiledGraph expected, CompiledGraph actual) {
        assertNotNull(actual);
        assertEquals(expected.env, actual.env);
        assertEquals(expected.spec.output, actual.spec.output);
        assertEquals(expected.spec.size(), actual.spec.size());
        for (int i = 0; i < expected.spec.size(); i++) {
            GraphSpec.Node before = expected.spec.node(i);
            GraphSpec.Node after = actual.spec.node(i);
            assertEquals(before.op, after.op);
            assertEquals(before.name, after.name);
            assertEquals(before.config.asMap(), after.config.asMap());
            assertArrayEquals(before.inputs, after.inputs);
            assertEquals(expected.params(i).keySet(), actual.params(i).keySet());
            for (String name : expected.params(i).keySet()) {
                Tensor want = expected.params(i).get(name);
                Tensor got = actual.params(i).get(name);
                assertArrayEquals(want.shape, got.shape);
                assertArrayEquals(want.data, got.data);
            }
        }
        int[] ids = Vocab.WORLD.encode("hello");
        Tokens input = new Tokens(ids, 1, ids.length);
        assertArrayEquals(expected.forward(null, input, null).data, actual.forward(null, input, null).data);
    }
}
