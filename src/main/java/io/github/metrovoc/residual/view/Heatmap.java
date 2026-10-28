package io.github.metrovoc.residual.view;

import io.github.metrovoc.residual.tensor.Tensor;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A small picture of a matrix for machine faces and screens: at most {@link #MAX}×{@link #MAX} cells, each a signed
 * byte relative to the largest magnitude ({@link #scale}).
 */
public record Heatmap(int rows, int cols, float scale, byte[] cells) {
    public static final int MAX = 24;

    public static final StreamCodec<ByteBuf, Heatmap> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Heatmap::rows,
            ByteBufCodecs.VAR_INT, Heatmap::cols,
            ByteBufCodecs.FLOAT, Heatmap::scale,
            ByteBufCodecs.BYTE_ARRAY, Heatmap::cells,
            Heatmap::new);

    /** Cell value in [-1, 1]. */
    public float value(int row, int col) {
        return cells[row * cols + col] / 127f;
    }

    /** The first matrix of a tensor (batch 0, head 0), or a vector as a single row. */
    public static Heatmap of(Tensor t) {
        int cols = t.dim(-1);
        int rows = t.rank() >= 2 ? t.dim(-2) : 1;
        return of(t.data, rows, cols);
    }

    /** Averages a row-major matrix down to at most {@link #MAX} cells per side. */
    public static Heatmap of(float[] data, int rows, int cols) {
        int r = Math.min(rows, MAX);
        int c = Math.min(cols, MAX);
        float[] binned = new float[r * c];
        for (int i = 0; i < r; i++) {
            int r0 = i * rows / r;
            int r1 = (i + 1) * rows / r;
            for (int j = 0; j < c; j++) {
                int c0 = j * cols / c;
                int c1 = (j + 1) * cols / c;
                double sum = 0;
                for (int y = r0; y < r1; y++) {
                    for (int x = c0; x < c1; x++) {
                        sum += data[y * cols + x];
                    }
                }
                binned[i * c + j] = (float) (sum / ((r1 - r0) * (c1 - c0)));
            }
        }
        float scale = 0;
        for (float v : binned) {
            if (Float.isFinite(v)) {
                scale = Math.max(scale, Math.abs(v));
            }
        }
        byte[] cells = new byte[r * c];
        for (int i = 0; i < cells.length; i++) {
            float v = Float.isFinite(binned[i]) && scale > 0 ? binned[i] / scale : 0;
            cells[i] = (byte) Math.round(v * 127);
        }
        return new Heatmap(r, c, scale, cells);
    }

    public void write(ValueOutput out) {
        out.putInt("rows", rows);
        out.putInt("cols", cols);
        out.putFloat("scale", scale);
        int[] packed = new int[(cells.length + 3) / 4];
        for (int i = 0; i < cells.length; i++) {
            packed[i >> 2] |= (cells[i] & 0xff) << ((i & 3) * 8);
        }
        out.putIntArray("cells", packed);
    }

    public static Heatmap read(ValueInput in) {
        int rows = in.getIntOr("rows", 0);
        int cols = in.getIntOr("cols", 0);
        int[] packed = in.getIntArray("cells").orElse(new int[0]);
        if (rows <= 0 || cols <= 0 || rows > MAX || cols > MAX || packed.length * 4 < rows * cols) {
            return null;
        }
        byte[] cells = new byte[rows * cols];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = (byte) (packed[i >> 2] >> ((i & 3) * 8));
        }
        return new Heatmap(rows, cols, in.getFloatOr("scale", 0), cells);
    }
}
