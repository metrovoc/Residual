package io.github.metrovoc.residual.tensor;

/** A batch of token id sequences, shape [batch, time]. */
public final class Tokens {
    public final int[] ids;
    public final int batch;
    public final int time;

    public Tokens(int[] ids, int batch, int time) {
        if (ids.length != batch * time) {
            throw new IllegalArgumentException(ids.length + " ids for a " + batch + "x" + time + " batch");
        }
        this.ids = ids;
        this.batch = batch;
        this.time = time;
    }

    public int at(int b, int t) {
        return ids[b * time + t];
    }

    public int[] shape() {
        return new int[]{batch, time};
    }
}
