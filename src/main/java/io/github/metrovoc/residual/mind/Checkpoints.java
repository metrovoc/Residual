package io.github.metrovoc.residual.mind;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.graph.CompileException;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Tensor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Saved minds: each one's machines and weights, as a file in the world folder. A crystal only names its mind;
 * minds that were asked something recently stay loaded.
 */
public final class Checkpoints {
    private static final int KEEP = 32;
    private static final Map<Path, CompiledGraph> LOADED = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Path, CompiledGraph> eldest) {
            return size() > KEEP;
        }
    };

    private Checkpoints() {
    }

    private static Path file(MinecraftServer server, UUID id) {
        return server.getWorldPath(LevelResource.ROOT).resolve("residual").resolve("minds").resolve(id + ".dat");
    }

    public static void save(MinecraftServer server, UUID id, CompiledGraph model) throws IOException {
        save(file(server, id), model);
    }

    public static byte[] encode(CompiledGraph model) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(write(model), bytes);
        return bytes.toByteArray();
    }

    public static void save(MinecraftServer server, UUID id, CompiledGraph model, byte[] encoded) throws IOException {
        save(file(server, id), model, encoded);
    }

    static void save(Path file, CompiledGraph model) throws IOException {
        save(file, model, encode(model));
    }

    private static void save(Path file, CompiledGraph model, byte[] encoded) throws IOException {
        Path path = file.toAbsolutePath().normalize();
        synchronized (LOADED) {
            Files.createDirectories(path.getParent());
            Path pending = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
            try {
                Files.write(pending, encoded, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
                try {
                    Files.move(pending, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING);
                }
                LOADED.put(path, model);
            } finally {
                Files.deleteIfExists(pending);
            }
        }
    }

    /** The mind, ready to answer, or null if its file is missing or unreadable. Thread-safe. */
    @Nullable
    public static CompiledGraph load(MinecraftServer server, UUID id) {
        return load(file(server, id));
    }

    @Nullable
    static CompiledGraph load(Path file) {
        Path path = file.toAbsolutePath().normalize();
        synchronized (LOADED) {
            CompiledGraph model = LOADED.get(path);
            if (model != null) {
                return model;
            }
            if (!Files.exists(path)) {
                return null;
            }
            try {
                model = read(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()));
                LOADED.put(path, model);
                return model;
            } catch (IOException | CompileException | RuntimeException e) {
                Residual.LOG.error("Could not load mind {}", path, e);
                return null;
            }
        }
    }

    static CompoundTag write(CompiledGraph model) {
        CompoundTag root = new CompoundTag();
        root.putInt("output", model.spec.output);
        ListTag nodes = new ListTag();
        for (int i = 0; i < model.spec.size(); i++) {
            GraphSpec.Node n = model.spec.node(i);
            CompoundTag t = new CompoundTag();
            t.putString("op", n.op.id);
            t.putString("name", n.name == null ? "" : n.name);
            CompoundTag config = new CompoundTag();
            n.config.asMap().forEach(config::putString);
            t.put("config", config);
            t.putIntArray("inputs", n.inputs);
            CompoundTag params = new CompoundTag();
            model.params(i).forEach((name, tensor) -> {
                CompoundTag p = new CompoundTag();
                p.putIntArray("shape", tensor.shape);
                int[] bits = new int[tensor.data.length];
                for (int k = 0; k < bits.length; k++) {
                    bits[k] = Float.floatToRawIntBits(tensor.data[k]);
                }
                p.putIntArray("data", bits);
                params.put(name, p);
            });
            t.put("params", params);
            nodes.add(t);
        }
        root.put("nodes", nodes);
        return root;
    }

    static CompiledGraph read(CompoundTag root) throws CompileException {
        GraphSpec spec = new GraphSpec();
        List<Map<String, Tensor>> params = new ArrayList<>();
        ListTag nodes = root.getListOrEmpty("nodes");
        for (int i = 0; i < nodes.size(); i++) {
            CompoundTag t = nodes.getCompoundOrEmpty(i);
            Op op = Op.byId(t.getStringOr("op", ""));
            if (op == null) {
                throw new IllegalStateException("unknown machine " + t.getStringOr("op", ""));
            }
            Config config = new Config(op);
            CompoundTag c = t.getCompoundOrEmpty("config");
            for (String key : c.keySet()) {
                config.set(key, c.getStringOr(key, ""));
            }
            int node = spec.addUnwired(t.getStringOr("name", ""), op, config, 0);
            int[] inputs = t.getIntArray("inputs").orElse(new int[0]);
            System.arraycopy(inputs, 0, spec.node(node).inputs, 0, Math.min(inputs.length, op.arity));
            Map<String, Tensor> p = new LinkedHashMap<>();
            CompoundTag saved = t.getCompoundOrEmpty("params");
            for (String name : saved.keySet()) {
                CompoundTag w = saved.getCompoundOrEmpty(name);
                int[] bits = w.getIntArray("data").orElse(new int[0]);
                float[] data = new float[bits.length];
                for (int k = 0; k < bits.length; k++) {
                    data[k] = Float.intBitsToFloat(bits[k]);
                }
                p.put(name, Tensor.of(w.getIntArray("shape").orElse(new int[0]), data));
            }
            params.add(p);
        }
        spec.output = root.getIntOr("output", GraphSpec.MISSING);
        return CompiledGraph.compile(spec, new Env(Vocab.WORLD.size(), Vocab.CONTEXT), params::get, 0);
    }
}
