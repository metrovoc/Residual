package io.github.metrovoc.residual.refine;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.learn.Recipe;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.train.Jobs;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Refined text: each supply a file in the world's folder, as models are, since a tank can hold more than an item ever should. A
 * Token Cell only names its supply. Reading one and cutting it into tokens takes a moment, so that happens off the server thread
 * and the last few stay ready.
 */
public final class Supplies {
    private static final int KEEP = 6;
    private static final Map<Key, SupplyTask> READY = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, SupplyTask> eldest) {
            return size() > KEEP;
        }
    };
    private static final Set<Key> COMING = new HashSet<>();

    private record Key(UUID id, Vocab vocab) {
    }

    private Supplies() {
    }

    private static Path file(MinecraftServer server, UUID id) {
        return server.getWorldPath(LevelResource.ROOT).resolve("residual").resolve("supplies").resolve(id + ".dat");
    }

    /** Writes a supply, in place of what it held before. */
    public static synchronized void save(MinecraftServer server, UUID id, Supply supply) throws IOException {
        write(file(server, id), supply);
        READY.keySet().removeIf(k -> k.id().equals(id));
    }

    static void write(Path path, Supply supply) throws IOException {
        Files.createDirectories(path.getParent());
        Path pending = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(pending))))) {
            out.writeInt(supply.mix().length);
            for (float m : supply.mix()) {
                out.writeFloat(m);
            }
            out.writeInt(supply.anneal());
            out.writeInt(supply.after());
            out.writeInt(supply.docs().size());
            for (Doc d : supply.docs()) {
                out.writeByte(d.domain().ordinal());
                out.writeInt(d.parts().size());
                for (String part : d.parts()) {
                    byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                    out.writeInt(bytes.length);
                    out.write(bytes);
                }
            }
        }
        Files.move(pending, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    static Supply read(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))))) {
            float[] mix = new float[in.readInt()];
            for (int i = 0; i < mix.length; i++) {
                mix[i] = in.readFloat();
            }
            int anneal = in.readInt(), after = in.readInt(), docs = in.readInt();
            List<Doc> out = new ArrayList<>(docs);
            Domain[] domains = Domain.values();
            for (int d = 0; d < docs; d++) {
                Domain domain = domains[in.readByte()];
                int parts = in.readInt();
                List<String> texts = new ArrayList<>(parts);
                for (int p = 0; p < parts; p++) {
                    byte[] bytes = new byte[in.readInt()];
                    in.readFully(bytes);
                    texts.add(new String(bytes, StandardCharsets.UTF_8));
                }
                out.add(new Doc(texts, domain));
            }
            return new Supply(out, mix, anneal, after);
        }
    }

    /**
     * Practice from the supply {@code id}, its own to count steps with, as {@code vocab} reads it; null until it is ready, when
     * {@code ready} runs on the server thread. A supply whose file is gone never gets ready.
     */
    @Nullable
    public static SupplyTask task(MinecraftServer server, UUID id, Vocab vocab, Runnable ready) {
        return task(server, id, vocab, Recipe.Packing.WINDOWS, ready);
    }

    @Nullable
    public static synchronized SupplyTask task(MinecraftServer server, UUID id, Vocab vocab, Recipe.Packing packing, Runnable ready) {
        Key key = new Key(id, vocab);
        SupplyTask known = READY.get(key);
        if (known != null) {
            return known.fresh(packing);
        }
        if (COMING.add(key)) {
            Jobs.EXECUTOR.execute(() -> {
                SupplyTask task = null;
                try {
                    task = new SupplyTask(read(file(server, id)), vocab);
                } catch (IOException | RuntimeException e) {
                    Residual.LOG.error("Could not read refined text {}", id, e);
                }
                synchronized (Supplies.class) {
                    COMING.remove(key);
                    if (task != null) {
                        READY.put(key, task);
                    }
                }
                if (task != null) {
                    Jobs.back(server, ready);
                }
            });
        }
        return null;
    }

    /** Reads a supply off the server thread. */
    public static Supply load(MinecraftServer server, UUID id) throws IOException {
        return read(file(server, id));
    }
}
