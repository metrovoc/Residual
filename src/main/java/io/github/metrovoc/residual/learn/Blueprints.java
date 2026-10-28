package io.github.metrovoc.residual.learn;

import io.github.metrovoc.residual.graph.Config;
import io.github.metrovoc.residual.graph.GraphSpec;
import io.github.metrovoc.residual.graph.Op;

/**
 * Reference solutions for every lesson. They calibrate the targets, seed the casebook's broken
 * machines and illustrate the guide.
 */
public final class Blueprints {
    private Blueprints() {
    }

    /** A table of next-letter scores: the embedding row of the current letter is the prediction. */
    public static GraphSpec bigram() {
        GraphSpec g = new GraphSpec();
        g.output = g.add("table", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "vocab"), GraphSpec.TOKENS);
        return g;
    }

    /** Bengio et al. (2003): embed the last three letters, concatenate, one hidden layer. */
    public static GraphSpec context() {
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "24"), GraphSpec.TOKENS);
        int s1 = g.add("prev1", Op.SHIFT, Config.of(Op.SHIFT, "steps", "1"), e);
        int s2 = g.add("prev2", Op.SHIFT, Config.of(Op.SHIFT, "steps", "2"), e);
        int c1 = g.add("pair", Op.CONCAT, e, s1);
        int c2 = g.add("triple", Op.CONCAT, c1, s2);
        int h = g.add("hidden", Op.LINEAR, Config.of(Op.LINEAR, "out", "128"), c2);
        int a = g.add("act", Op.ACTIVATION, Config.of(Op.ACTIVATION, "fn", "tanh"), h);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), a);
        return g;
    }

    /** One hand-built attention head whose keys come from the previous token. */
    public static GraphSpec recall() {
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "32"), GraphSpec.TOKENS);
        int prev = g.add("prev", Op.SHIFT, e);
        int q = g.add("query", Op.LINEAR, Config.of(Op.LINEAR, "out", "32", "bias", "false"), e);
        int qs = g.add("q_scaled", Op.SCALE, q);
        int k = g.add("key", Op.LINEAR, Config.of(Op.LINEAR, "out", "32", "bias", "false"), prev);
        int v = g.add("value", Op.LINEAR, Config.of(Op.LINEAR, "out", "32", "bias", "false"), e);
        int s = g.add("scores", Op.MATMUL, Config.of(Op.MATMUL, "transpose", "true"), qs, k);
        int m = g.add("masked", Op.CAUSAL_MASK, s);
        int p = g.add("weights", Op.SOFTMAX, m);
        int o = g.add("mixed", Op.MATMUL, p, v);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), o);
        return g;
    }

    /** Attention by position: tokens plus learned positions, one attention head, a feed-forward block. */
    public static GraphSpec reverse() {
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "32"), GraphSpec.TOKENS);
        int pos = g.add("pos", Op.POSITION, Config.of(Op.POSITION, "dim", "32"));
        int x = g.add("x", Op.ADD, e, pos);
        int n1 = g.add("norm1", Op.NORM, x);
        int a = g.add("attn", Op.ATTENTION, Config.of(Op.ATTENTION, "heads", "2"), n1);
        int r1 = g.add("res1", Op.ADD, x, a);
        int n2 = g.add("norm2", Op.NORM, r1);
        int f = g.add("ffn", Op.MLP, n2);
        int r2 = g.add("res2", Op.ADD, r1, f);
        int n3 = g.add("norm_out", Op.NORM, r2);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), n3);
        return g;
    }

    /** Two attention layers, no delay lines: the first learns to look one step back, the second to copy. */
    public static GraphSpec induction() {
        GraphSpec g = new GraphSpec();
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", "32"), GraphSpec.TOKENS);
        int pos = g.add("pos", Op.POSITION, Config.of(Op.POSITION, "dim", "32"));
        int x = g.add("x", Op.ADD, e, pos);
        int a1 = g.add("attn1", Op.ATTENTION, Config.of(Op.ATTENTION, "heads", "1"), g.add("norm1", Op.NORM, x));
        int r1 = g.add("res1", Op.ADD, x, a1);
        int a2 = g.add("attn2", Op.ATTENTION, Config.of(Op.ATTENTION, "heads", "1"), g.add("norm2", Op.NORM, r1));
        int r2 = g.add("res2", Op.ADD, r1, a2);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), g.add("norm_out", Op.NORM, r2));
        return g;
    }

    /** A small GPT: token and position embeddings, two transformer blocks, a final norm and unembedding. */
    public static GraphSpec gpt(int dim, int blocks, int heads) {
        GraphSpec g = new GraphSpec();
        String d = Integer.toString(dim);
        int e = g.add("embed", Op.EMBEDDING, Config.of(Op.EMBEDDING, "dim", d), GraphSpec.TOKENS);
        int pos = g.add("pos", Op.POSITION, Config.of(Op.POSITION, "dim", d));
        int x = g.add("x", Op.ADD, e, pos);
        for (int i = 0; i < blocks; i++) {
            x = g.add("block" + (i + 1), Op.TRANSFORMER, Config.of(Op.TRANSFORMER, "heads", Integer.toString(heads)), x);
        }
        int n = g.add("norm_out", Op.NORM, x);
        g.output = g.add("logits", Op.LINEAR, Config.of(Op.LINEAR, "out", "vocab"), n);
        return g;
    }
}
