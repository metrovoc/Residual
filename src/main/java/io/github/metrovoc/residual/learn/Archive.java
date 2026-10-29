package io.github.metrovoc.residual.learn;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * What the people of this world wrote, in records. Shards carry records with some of their text lost, block by
 * block. One record in ten never turns up in a shard: reading is tested on those, text no model has seen.
 */
public final class Archive {
    public record Record(int id, String title, String text) {
        /** How many blocks of {@link #BLOCK} characters the record has. */
        public int blocks() {
            return (text.length() + BLOCK - 1) / BLOCK;
        }

        /** The stretches of text that survive when the blocks in {@code lost} are missing. */
        public List<String> legible(BitSet lost) {
            List<String> out = new ArrayList<>();
            int from = 0;
            for (int b = 0; b <= blocks(); b++) {
                if (b == blocks() || lost.get(b)) {
                    int to = Math.min(text.length(), b * BLOCK);
                    if (to > from) {
                        out.add(text.substring(from, to));
                    }
                    from = (b + 1) * BLOCK;
                }
            }
            return out;
        }
    }

    /** Characters per block of loss. */
    public static final int BLOCK = 32;
    /** The archive's files, in record order. Records are only ever added at the end of the last file. */
    private static final List<String> FILES = List.of("01-letters", "02-journals", "03-stories", "04-songs",
            "05-household", "06-manuals", "07-notices", "08-late");
    private static List<Record> records;
    private static TextTask test;

    private Archive() {
    }

    public static synchronized List<Record> records() {
        if (records == null) {
            List<Record> all = new ArrayList<>();
            for (String file : FILES) {
                for (String rec : read("/archive/" + file + ".txt").split("(?m)^%%$")) {
                    String text = rec.strip();
                    if (!text.isEmpty()) {
                        int nl = text.indexOf('\n');
                        all.add(new Record(all.size(), nl < 0 ? text : text.substring(0, nl).strip(), text));
                    }
                }
            }
            records = List.copyOf(all);
        }
        return records;
    }

    public static Record get(int id) {
        List<Record> all = records();
        return id >= 0 && id < all.size() ? all.get(id) : null;
    }

    /** Kept out of every shard, to test reading. */
    public static boolean heldOut(int id) {
        return id % 10 == 3;
    }

    /** Reading's test: every held-out record, whole. */
    public static synchronized TextTask test() {
        if (test == null) {
            List<String> texts = new ArrayList<>();
            for (Record r : records()) {
                if (heldOut(r.id())) {
                    texts.add(r.text());
                }
            }
            test = new TextTask(texts, Benchmark.READING.target);
        }
        return test;
    }

    /** Practice text: the legible parts of the given records, each with the blocks it has lost. */
    public static TextTask practice(Map<Integer, BitSet> recovered) {
        List<String> texts = new ArrayList<>();
        recovered.forEach((id, lost) -> {
            Record r = get(id);
            if (r != null && !heldOut(id)) {
                texts.addAll(r.legible(lost));
            }
        });
        return new TextTask(texts, Benchmark.READING.target);
    }

    private static String read(String path) {
        try (InputStream in = Archive.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r", "");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
