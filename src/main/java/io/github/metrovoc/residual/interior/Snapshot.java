package io.github.metrovoc.residual.interior;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a hall shows at one moment of a run: one example passing through the model, number by number, and how
 * the run is going. Numbers travel as bytes with a scale, a grid at a time.
 *
 * @param step      steps taken since the weights were last new
 * @param state     what the run is doing
 * @param pace      steps a second it is asked for; 0 for as fast as it goes
 * @param rate      the learning rate
 * @param trainLoss the loss of the latest step, per token scored
 * @param testLoss  the loss on examples it has not practised, or NaN before the first test
 * @param score     the share of answers right on those, or NaN
 * @param passed    whether that meets the task's mark
 * @param tokens    the example: what stands on each lane
 * @param targets   what should follow each token
 * @param scored    which lanes' predictions count
 * @param embed     what each token was turned into, lanes by width
 * @param position  what each place added, or null without positions
 * @param parts     what each part that is switched on did, in order
 * @param chances   the chance given to every token of the vocabulary at each lane, 0 to 255
 * @param laneLoss  how surprised the model was by what followed each lane's token
 * @param tables    the weights that are shown whole, or null when they are not sent this time
 * @param curve     the run so far
 */
public record Snapshot(int step, State state, int pace, float rate, float trainLoss, float testLoss, float score, boolean passed,
                       long parameters, float stepMillis,
                       int[] tokens, int[] targets, boolean[] scored,
                       Grid embed, @Nullable Grid position, List<PartView> parts, int vocab, byte[] chances, float[] laneLoss,
                       @Nullable Tables tables, List<Point> curve) {
    public enum State { PAUSED, RUNNING, DIVERGED }

    /**
     * Numbers in rows and columns, each a signed byte times {@code scale} over 127.
     */
    public record Grid(int rows, int cols, float scale, byte[] cells) {
        public static Grid of(float[] data, int offset, int rows, int cols) {
            float top = 0;
            for (int i = 0; i < rows * cols; i++) {
                float v = Math.abs(data[offset + i]);
                if (v > top && Float.isFinite(v)) {
                    top = v;
                }
            }
            byte[] cells = new byte[rows * cols];
            if (top > 0) {
                for (int i = 0; i < cells.length; i++) {
                    float v = data[offset + i];
                    cells[i] = (byte) (Float.isFinite(v) ? Math.round(Math.clamp(v / top, -1f, 1f) * 127) : 0);
                }
            }
            return new Grid(rows, cols, top, cells);
        }

        public float at(int row, int col) {
            return cells[row * cols + col] * scale / 127f;
        }

        /** From -1 to 1: how large the cell is beside the largest of its grid. */
        public float share(int row, int col) {
            return cells[row * cols + col] / 127f;
        }
    }

    /**
     * @param id      the part's own number
     * @param added   what it added to the stream, lanes by width
     * @param after   the stream once it had
     * @param pattern for attention, how much each lane read from each lane, 0 to 255, heads by lanes by lanes; else null
     */
    public record PartView(int id, Grid added, Grid after, int heads, @Nullable byte[] pattern) {
        /** From 0 to 1: how much of what lane {@code to} read through {@code head} came from lane {@code from}. */
        public float weight(int head, int to, int from) {
            int lanes = added.rows();
            return (pattern[(head * lanes + to) * lanes + from] & 0xff) / 255f;
        }
    }

    /**
     * @param embed  the vector of every token, vocabulary by width
     * @param readout what the stream is compared with to score every token, vocabulary by width
     * @param places the vector of every place, or null
     * @param follows for a model that is only a table: the chance it gives each of {@link #letters} after each of them,
     *                a row for the letter before; or null
     * @param written for writing: what the model writes on from the example's first token, each token drawn by the
     *                chances it gives, always with the same draws; or null
     * @param basis   three directions of the stream, each {@code width} numbers, along which the tokens' vectors spread
     *                most: where a vector is drawn in space is its length along each
     */
    public record Tables(Grid embed, Grid readout, @Nullable Grid places, @Nullable Grid follows, @Nullable int[] written, float[] basis) {
    }

    /** The tokens a table's wall shows: the small letters and the space. */
    public static int[] letters(io.github.metrovoc.residual.learn.Vocab vocab) {
        String wanted = "abcdefghijklmnopqrstuvwxyz ";
        int[] ids = new int[wanted.length()];
        for (int id = 0; id < vocab.size(); id++) {
            String piece = vocab.piece(id);
            int at = piece.length() == 1 ? wanted.indexOf(piece.charAt(0)) : -1;
            if (at >= 0) {
                ids[at] = id;
            }
        }
        return ids;
    }

    public record Point(int step, float train, float test, float score) {
    }

    public int lanes() {
        return tokens.length;
    }

    /** From 0 to 1: the chance given to {@code token} following lane {@code lane}'s. */
    public float chance(int lane, int token) {
        return (chances[lane * vocab + token] & 0xff) / 255f;
    }
}
