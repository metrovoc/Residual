package io.github.metrovoc.residual.model;

import io.github.metrovoc.residual.graph.CompileException;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.graph.Decoder;
import io.github.metrovoc.residual.graph.Env;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A model's weights kept in fewer bits. Each run of {@link #GROUP} weights shares a scale, a 16-bit float, and each weight
 * is stored as the whole number of those scales nearest to it, in {@code bits} bits. The packed bytes are all there is:
 * {@link #bytes} is their real size, and {@link #restore} rebuilds a model from them alone, for a test to score or anything
 * to run.
 */
public final class Quantized {
    public static final int GROUP = 32;

    public final int bits;
    private final GraphSpec spec;
    private final Env env;
    private final byte[] packed;

    private Quantized(CompiledGraph model, int bits, byte[] packed) {
        this.bits = bits;
        this.spec = model.spec;
        this.env = model.env;
        this.packed = packed;
    }

    /** {@code model}'s weights in {@code bits} bits each, from 2 to 8. */
    public static Quantized of(CompiledGraph model, int bits) {
        return of(model, bits, GROUP);
    }

    static Quantized of(CompiledGraph model, int bits, int group) {
        int size = 0;
        for (Tensor t : model.parameters()) {
            size += (t.size() + group - 1) / group * Short.BYTES + (t.size() * bits + 7) / 8;
        }
        Writer out = new Writer(new byte[size]);
        int most = (1 << bits - 1) - 1;
        for (Tensor t : model.parameters()) {
            for (int at = 0; at < t.data.length; at += group) {
                int end = Math.min(at + group, t.data.length);
                float largest = 0;
                for (int i = at; i < end; i++) {
                    largest = Math.max(largest, Math.abs(t.data[i]));
                }
                short stored = Float.floatToFloat16(largest / most);
                float scale = Float.float16ToFloat(stored);
                out.bits(stored, 16);
                for (int i = at; i < end; i++) {
                    out.bits(scale == 0 ? 0 : Math.clamp(Math.round(t.data[i] / scale), -most, most), bits);
                }
            }
            out.align();
        }
        return new Quantized(model, bits, out.bytes);
    }

    /** The real size of its weights, in bytes. */
    public int bytes() {
        return packed.length;
    }

    /** A model with the same machines whose weights are what the packed bytes say. */
    public CompiledGraph restore() {
        return restore(GROUP);
    }

    CompiledGraph restore(int group) {
        CompiledGraph model;
        try {
            model = CompiledGraph.compile(spec, env, null, 0);
        } catch (CompileException e) {
            throw new IllegalStateException("a compiled model no longer compiles", e);
        }
        Reader in = new Reader(packed);
        for (Tensor t : model.parameters()) {
            for (int at = 0; at < t.data.length; at += group) {
                int end = Math.min(at + group, t.data.length);
                float scale = Float.float16ToFloat((short) in.bits(16));
                for (int i = at; i < end; i++) {
                    t.data[i] = in.signed(bits) * scale;
                }
            }
            in.align();
        }
        return model;
    }

    /**
     * Weights read out of the packed bytes machine by machine, each time they are wanted, into scratch space no bigger than the
     * largest machine: nothing else of them is held as floats. Not safe to share between threads.
     */
    public Decoder.Weights unpacked() {
        return new Unpacked();
    }

    private final class Unpacked implements Decoder.Weights {
        /** Where each machine's weights start in the packed bytes. */
        private final Map<Integer, Integer> starts = new HashMap<>();
        private final Map<Integer, Map<String, Tensor>> shapes = new HashMap<>();
        private final Map<Integer, ArrayDeque<float[]>> free = new HashMap<>();
        private final Map<Integer, Map<String, Tensor>> open = new HashMap<>();

        Unpacked() {
            CompiledGraph model;
            try {
                model = CompiledGraph.compile(spec, env, null, 0);
            } catch (CompileException e) {
                throw new IllegalStateException("a compiled model no longer compiles", e);
            }
            int at = 0;
            for (int node : model.order) {
                starts.put(node, at);
                shapes.put(node, model.params(node));
                for (Tensor t : model.params(node).values()) {
                    at += (t.size() + GROUP - 1) / GROUP * Short.BYTES + (t.size() * bits + 7) / 8;
                }
            }
        }

        @Override
        public Map<String, Tensor> of(int node) {
            Map<String, Tensor> known = open.get(node);
            if (known != null) {
                return known;
            }
            Map<String, Tensor> like = shapes.get(node);
            if (like.isEmpty()) {
                return like;
            }
            Map<String, Tensor> out = new LinkedHashMap<>();
            int at = starts.get(node);
            for (Map.Entry<String, Tensor> e : like.entrySet()) {
                Tensor t = e.getValue();
                ArrayDeque<float[]> spare = free.get(t.size());
                float[] data = spare == null || spare.isEmpty() ? new float[t.size()] : spare.pop();
                at = read(at, data);
                out.put(e.getKey(), new Tensor(t.shape, data, false));
            }
            open.put(node, out);
            return out;
        }

        @Override
        public void done(int node) {
            Map<String, Tensor> used = open.remove(node);
            if (used != null) {
                used.values().forEach(t -> free.computeIfAbsent(t.size(), n -> new ArrayDeque<>()).push(t.data));
            }
        }

        /** Reads one tensor's groups from {@code at} and says where the next one starts. */
        private int read(int at, float[] data) {
            byte[] in = packed;
            for (int from = 0; from < data.length; from += GROUP) {
                int end = Math.min(from + GROUP, data.length);
                float scale = Float.float16ToFloat((short) ((in[at] & 0xFF) << 8 | in[at + 1] & 0xFF));
                at += Short.BYTES;
                if (bits == 8) {
                    for (int i = from; i < end; i++) {
                        data[i] = in[at + i - from] * scale;
                    }
                    at += end - from;
                } else if (bits == 4) {
                    for (int i = from; i < end; i++) {
                        int b = in[at + (i - from >> 1)];
                        data[i] = ((i - from & 1) == 0 ? b >> 4 : b << 28 >> 28) * scale;
                    }
                    at += end - from + 1 >> 1;
                } else {
                    throw new IllegalStateException("only 8 and 4 bits are unpacked on the way");
                }
            }
            return at;
        }
    }

    /** Writes numbers bit by bit, most significant first. */
    private static final class Writer {
        final byte[] bytes;
        int bit;

        Writer(byte[] bytes) {
            this.bytes = bytes;
        }

        void bits(int value, int n) {
            for (int i = n - 1; i >= 0; i--, bit++) {
                if ((value >>> i & 1) != 0) {
                    bytes[bit >> 3] |= (byte) (0x80 >> (bit & 7));
                }
            }
        }

        void align() {
            bit = bit + 7 & ~7;
        }
    }

    private static final class Reader {
        final byte[] bytes;
        int bit;

        Reader(byte[] bytes) {
            this.bytes = bytes;
        }

        int bits(int n) {
            int value = 0;
            for (int i = 0; i < n; i++, bit++) {
                value = value << 1 | bytes[bit >> 3] >> 7 - (bit & 7) & 1;
            }
            return value;
        }

        int signed(int n) {
            return bits(n) << 32 - n >> 32 - n;
        }

        void align() {
            bit = bit + 7 & ~7;
        }
    }
}
