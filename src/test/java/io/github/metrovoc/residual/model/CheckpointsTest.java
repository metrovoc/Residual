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
import io.github.metrovoc.residual.learn.Blueprints;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
