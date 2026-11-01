package io.github.metrovoc.residual.tensor;

/** A batch of token id sequences, shape [batch, time]. */
public final class Tokens {
    public final int[] ids;
    public final int batch;
    public final int time;
    /** Where each token's document begins in its row, for rows packed with masks between documents; or null. */
    public final int[] starts;

    public Tokens(int[] ids, int batch, int time) {
        this(ids, batch, time, null);
    }

    public Tokens(int[] ids, int batch, int time, int[] starts) {
        if (ids.length != batch * time) {
            throw new IllegalArgumentException(ids.length + " ids for a " + batch + "x" + time + " batch");
        }
        this.ids = ids;
        this.batch = batch;
        this.time = time;
        this.starts = starts;
    }

    public int at(int b, int t) {
        return ids[b * time + t];
    }

    public int[] shape() {
        return new int[]{batch, time};
    }
}
