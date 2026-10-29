package io.github.metrovoc.residual.learn;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;

/**
 * A vocabulary: the world's characters, then the merges a BPE Forge learned. Ids below {@link #CHARACTERS} are single
 * characters, and merge i joins two earlier tokens into id {@code CHARACTERS + i}. Text is cut into chunks before
 * merging (a word with the space before it, a single digit, a run of punctuation with the space before it, a
 * newline), and no merge crosses a chunk's edge.
 */
public final class Vocab {
    private static final String ALPHABET = "\n !\"'(),-.0123456789:;=>?ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz|";
    private static final int[] INDEX = new int[65536];
    /** How many single characters every vocabulary starts with. */
    public static final int CHARACTERS = ALPHABET.length();
    /** Characters only: the vocabulary of a model without a tokenizer, and the one task formats are spelled in. */
    public static final Vocab WORLD = new Vocab(new int[0]);
    /** The longest stretch of tokens any model reads at once. */
    public static final int CONTEXT = 64;
    /** Chunks remembered per vocabulary, so common words are merged once. */
    private static final int CACHE = 1 << 17;

    public static final Codec<Vocab> CODEC = Codec.INT_STREAM.xmap(s -> of(s.toArray()), v -> IntStream.of(v.merges));
    public static final StreamCodec<ByteBuf, Vocab> STREAM_CODEC = StreamCodec.of((buf, v) -> {
        VarInt.write(buf, v.merges.length);
        for (int m : v.merges) {
            VarInt.write(buf, m);
        }
    }, buf -> {
        int[] m = new int[Math.min(VarInt.read(buf), 1 << 16)];
        for (int i = 0; i < m.length; i++) {
            m[i] = VarInt.read(buf);
        }
        return of(m);
    });

    static {
        Arrays.fill(INDEX, -1);
        for (int i = 0; i < ALPHABET.length(); i++) {
            INDEX[ALPHABET.charAt(i)] = i;
        }
    }

    /** Merge i joins {@code merges[2i]} and {@code merges[2i + 1]}. */
    private final int[] merges;
    private final String[] pieces;
    /** The token two neighbours merge into, by {@link #pair}, or -1. */
    private final Long2IntOpenHashMap joined;
    private final Map<String, int[]> chunks = new ConcurrentHashMap<>();

    private Vocab(int[] merges) {
        this.merges = merges;
        int n = merges.length / 2;
        pieces = new String[CHARACTERS + n];
        for (int i = 0; i < CHARACTERS; i++) {
            pieces[i] = String.valueOf(ALPHABET.charAt(i));
        }
        joined = new Long2IntOpenHashMap(n);
        joined.defaultReturnValue(-1);
        for (int i = 0; i < n; i++) {
            int a = merges[2 * i], b = merges[2 * i + 1];
            pieces[CHARACTERS + i] = pieces[a] + pieces[b];
            joined.put(pair(a, b), CHARACTERS + i);
        }
    }

    /** The vocabulary these merges make, up to the first one that joins a token not made yet. */
    public static Vocab of(int[] merges) {
        int n = 0;
        while (2 * n + 1 < merges.length && known(merges[2 * n], n) && known(merges[2 * n + 1], n)) {
            n++;
        }
        return n == 0 ? WORLD : new Vocab(Arrays.copyOf(merges, 2 * n));
    }

    private static boolean known(int id, int merged) {
        return id >= 0 && id < CHARACTERS + merged;
    }

    static long pair(int a, int b) {
        return (long) a << 32 | (b & 0xFFFFFFFFL);
    }

    /** This vocabulary with one more merge, joining {@code a} and {@code b}. */
    public Vocab with(int a, int b) {
        int[] m = Arrays.copyOf(merges, merges.length + 2);
        m[merges.length] = a;
        m[merges.length + 1] = b;
        return new Vocab(m);
    }

    /** Its first {@code size} tokens: the vocabulary it was on its way through. */
    public Vocab first(int size) {
        return size >= size() ? this : of(Arrays.copyOf(merges, 2 * Math.max(0, size - CHARACTERS)));
    }

    public int size() {
        return pieces.length;
    }

    /** How many merges it learned. */
    public int merges() {
        return merges.length / 2;
    }

    /** Its merges, two ids each, in the order they were learned. */
    public int[] mergeIds() {
        return merges.clone();
    }

    /** The token for a single character, or -1 outside the alphabet. */
    public int id(char c) {
        return INDEX[c];
    }

    public boolean has(char c) {
        return INDEX[c] >= 0;
    }

    /** The text a token stands for. */
    public String piece(int id) {
        return pieces[id];
    }

    /** A printable label for a token, newlines and spaces made visible. */
    public String label(int id) {
        return pieces[id].replace("\n", "⏎").replace(' ', '␣');
    }

    /** Encodes text as tokens, merging what it can and skipping characters outside the alphabet. */
    public int[] encode(String text) {
        if (merges.length == 0) {
            return spell(text);
        }
        IntArrayList out = new IntArrayList(text.length() / 2 + 1);
        chunks(text, c -> {
            int[] ids = chunk(c);
            out.addElements(out.size(), ids);
        });
        return out.toIntArray();
    }

    /** Encodes text one character to a token, the way task formats are written, skipping characters outside the alphabet. */
    public int[] spell(String text) {
        int[] out = new int[text.length()];
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            int id = INDEX[text.charAt(i)];
            if (id >= 0) {
                out[n++] = id;
            }
        }
        return Arrays.copyOf(out, n);
    }

    public String decode(int[] ids) {
        StringBuilder sb = new StringBuilder(ids.length * 2);
        for (int id : ids) {
            sb.append(pieces[id]);
        }
        return sb.toString();
    }

    /** Characters per token over {@code texts}: how much it compresses them. */
    public double charsPerToken(Iterable<String> texts) {
        long chars = 0, tokens = 0;
        for (String t : texts) {
            int[] ids = encode(t);
            tokens += ids.length;
            for (int id : ids) {
                chars += pieces[id].length();
            }
        }
        return tokens == 0 ? 1 : (double) chars / tokens;
    }

    private int[] chunk(String c) {
        int[] ids = chunks.get(c);
        if (ids == null) {
            ids = merge(spell(c));
            if (chunks.size() < CACHE) {
                chunks.put(c, ids);
            }
        }
        return ids;
    }

    /** Applies the merges in the order they were learned, each to every place it fits, left to right. */
    private int[] merge(int[] ids) {
        int n = ids.length;
        while (n > 1) {
            int at = -1, best = Integer.MAX_VALUE;
            for (int i = 0; i + 1 < n; i++) {
                int m = joined.get(pair(ids[i], ids[i + 1]));
                if (m >= 0 && m < best) {
                    best = m;
                    at = i;
                }
            }
            if (at < 0) {
                break;
            }
            n = join(ids, n, ids[at], ids[at + 1], best);
        }
        return Arrays.copyOf(ids, n);
    }

    /** Replaces each {@code a b} in the first {@code n} ids with {@code into}, left to right; returns the new length. */
    static int join(int[] ids, int n, int a, int b, int into) {
        int w = 0;
        for (int i = 0; i < n; i++) {
            if (i + 1 < n && ids[i] == a && ids[i + 1] == b) {
                ids[w++] = into;
                i++;
            } else {
                ids[w++] = ids[i];
            }
        }
        return w;
    }

    /** Cuts text into the chunks merges stay within, dropping characters outside the alphabet. */
    static void chunks(String text, Consumer<String> out) {
        StringBuilder s = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (INDEX[c] >= 0) {
                s.append(c);
            }
        }
        int n = s.length();
        int i = 0;
        while (i < n) {
            int start = i;
            if (s.charAt(i) == ' ' && i + 1 < n && (letter(s.charAt(i + 1)) || mark(s.charAt(i + 1)))) {
                i++;
            }
            char c = s.charAt(i);
            if (letter(c)) {
                while (i < n && letter(s.charAt(i))) {
                    i++;
                }
            } else if (mark(c)) {
                while (i < n && mark(s.charAt(i))) {
                    i++;
                }
            } else {
                i++;
            }
            out.accept(s.substring(start, i));
        }
    }

    private static boolean letter(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
    }

    /** Punctuation: in the alphabet, but not a letter, digit, space or newline. */
    private static boolean mark(char c) {
        return INDEX[c] >= 0 && !letter(c) && !(c >= '0' && c <= '9') && c != ' ' && c != '\n';
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Vocab v && Arrays.equals(merges, v.merges);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(merges);
    }
}
