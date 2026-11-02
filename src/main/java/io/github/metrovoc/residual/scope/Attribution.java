package io.github.metrovoc.residual.scope;

import io.github.metrovoc.residual.graph.Captures;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tape;
import io.github.metrovoc.residual.tensor.Tensor;
import io.github.metrovoc.residual.tensor.Tokens;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * An attribution graph: for one thing a model says, which features of its stream made it say so, and which features and tokens made
 * those. A model this small can afford the exact answer: each thing is taken out of the stream for real, the rest of the model runs on,
 * and what changes is what it put in. A larger one has to estimate instead, with attention patterns and normalization held still so that
 * a gradient times an amount says about the same; that estimate stands beside each answer, and where the model's work is done by
 * attention it shows how far off a straight line can be.
 */
public final class Attribution {
    /** How many nodes a layer keeps, how many edges a node takes in, and how many of the last positions are looked at. */
    private static final int KEPT = 4, INTO = 3, REACH = 24;

    /**
     * One node: a token read ({@code latent} -1) or a feature at a point of the stream.
     *
     * @param layer    0 for the tokens, then the stream's points after the first
     * @param position where in the text
     * @param amount   how strongly it fired
     * @param effect   how far its score falls when it is taken out
     * @param linear   what a gradient times its amount says of that, attention and norms held still
     */
    public record Node(int layer, int latent, int position, float amount, String label, float effect, float linear, Component about) {
    }

    /** Taking {@code from} out takes {@code weight} from {@code to}; {@code to} is -1 for the score itself. */
    public record Edge(int from, int to, float weight) {
    }

    /**
     * @param target    the token it was asked about
     * @param agreement how closely the straight-line estimate follows what is measured, over the graph's features: a correlation from -1 to 1
     */
    public record Graph(Stream stream, int[] tokens, int target, float score, List<Node> nodes, List<Edge> edges, float agreement) {
    }

    private Attribution() {
    }

    /** How much the model's score for {@code target} at the last position stands above its average score. */
    private static Tensor score(Tape tape, Tensor logits, int target) {
        int v = logits.dim(-1), t = logits.dim(-2);
        float[] mask = new float[logits.size()];
        for (int j = 0; j < v; j++) {
            mask[(t - 1) * v + j] = -1f / v;
        }
        mask[(t - 1) * v + target] += 1f;
        return Ops.sum(tape, Ops.mul(tape, logits, Tensor.of(logits.shape, mask)));
    }

    /** Reads what made the model name its likeliest next token after {@code one}, through {@code dictionaries}: one per point of the stream after the first. */
    public static Graph trace(Stream stream, List<Features.Dictionary> dictionaries, Tokens one, BooleanSupplier stop) {
        CompiledGraph model = stream.model;
        Vocab vocab = model.env.tokenizer();
        int t = one.time, d = stream.width(), layers = dictionaries.size(), from = Math.max(0, t - REACH);
        Captures plain = new Captures();
        Tensor logits = model.forward(null, one, plain);
        int target = Reads.top(logits.data, (t - 1) * logits.dim(-1), logits.dim(-1));
        float baseline = score(null, logits, target).item();

        // the straight-line estimate
        Captures frozen = new Captures();
        frozen.frozen = true;
        Tape tape = new Tape();
        tape.backward(score(tape, model.forward(tape, one, frozen), target));

        // tokens: what the first point carries of each, in place of the average of them all
        int first = stream.points[0];
        float[] average = new float[d];
        float[] start = plain.outputs.get(first).data;
        for (int i = 0; i < t; i++) {
            for (int j = 0; j < d; j++) {
                average[j] += start[i * d + j] / t;
            }
        }
        List<Node> nodes = new ArrayList<>();
        List<Node> tokens = new ArrayList<>();
        Tensor firstFrozen = frozen.outputs.get(first);
        for (int i = from; i < t; i++) {
            if (stop.getAsBoolean()) {
                return null;
            }
            float effect = baseline - score(null, model.forward(null, one, tokenOut(first, i, average)), target).item();
            float linear = 0;
            for (int j = 0; j < d; j++) {
                linear += (start[i * d + j] - average[j]) * firstFrozen.grad()[i * d + j];
            }
            tokens.add(new Node(0, -1, i, 1, Picture.shown(vocab.piece(one.ids[i])), effect, linear, Component.empty()));
        }
        addKept(nodes, tokens);
        // features: each one firing near the end of the text, taken out in turn
        for (int l = 0; l < layers; l++) {
            Features.Dictionary dict = dictionaries.get(l);
            float[] at = plain.outputs.get(dict.point()).data;
            float[] grad = frozen.outputs.get(dict.point()).grad();
            List<Node> found = new ArrayList<>();
            int[] ids = new int[dict.sae().active];
            float[] values = new float[dict.sae().active];
            for (int i = from; i < t; i++) {
                if (stop.getAsBoolean()) {
                    return null;
                }
                int n = dict.sae().encode(at, i * d, ids, values);
                for (int k = 0; k < n; k++) {
                    float effect = baseline - score(null, model.forward(null, one, featureOut(dict, ids[k], i, values[k])), target).item();
                    float linear = values[k] * along(dict.sae(), ids[k], grad, i * d);
                    Features.Feature f = dict.feature(ids[k]);
                    found.add(new Node(l + 1, ids[k], i, values[k], f == null ? "#" + ids[k] : Picture.shown(f.label()), effect, linear,
                            about(ids[k], i, values[k], effect, linear, f, one, vocab)));
                }
            }
            addKept(nodes, found);
        }

        // edges: what taking each kept node out does to the kept features of the next layer
        List<Edge> edges = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) {
            Node source = nodes.get(index);
            if (source.layer() == layers) {
                edges.add(new Edge(index, -1, source.effect()));
                continue;
            }
            Features.Dictionary next = dictionaries.get(source.layer());
            Captures after = source.layer() == 0 ? tokenOut(first, source.position(), average)
                    : featureOut(dictionaries.get(source.layer() - 1), source.latent(), source.position(), source.amount());
            model.forward(null, one, after);
            float[] was = plain.outputs.get(next.point()).data, now = after.outputs.get(next.point()).data;
            for (int to = 0; to < nodes.size(); to++) {
                Node other = nodes.get(to);
                if (other.layer() == source.layer() + 1) {
                    float weight = amount(next.sae(), other.latent(), was, other.position() * d) - amount(next.sae(), other.latent(), now, other.position() * d);
                    if (weight != 0) {
                        edges.add(new Edge(index, to, weight));
                    }
                }
            }
        }
        return new Graph(stream, one.ids, target, baseline, nodes, prune(edges, nodes.size()), agreement(nodes));
    }

    private static Captures tokenOut(int point, int position, float[] average) {
        Captures c = new Captures();
        c.edit = (nothing, node, out) -> {
            if (node != point) {
                return out;
            }
            float[] data = out.data.clone();
            System.arraycopy(average, 0, data, position * average.length, average.length);
            return Tensor.of(out.shape, data);
        };
        return c;
    }

    private static Captures featureOut(Features.Dictionary dict, int latent, int position, float amount) {
        Captures c = new Captures();
        int d = dict.sae().width;
        c.edit = (nothing, node, out) -> {
            if (node != dict.point()) {
                return out;
            }
            float[] data = out.data.clone();
            for (int j = 0; j < d; j++) {
                data[position * d + j] -= amount * dict.sae().decoder[latent * d + j];
            }
            return Tensor.of(out.shape, data);
        };
        return c;
    }

    private static float amount(Autoencoder sae, int latent, float[] x, int off) {
        float z = 0;
        for (int j = 0; j < sae.width; j++) {
            z += sae.encoder[latent * sae.width + j] * (x[off + j] - sae.center[j]);
        }
        return Math.max(0, z);
    }

    private static float along(Autoencoder sae, int latent, float[] gradient, int off) {
        float sum = 0;
        for (int j = 0; j < sae.width; j++) {
            sum += sae.decoder[latent * sae.width + j] * gradient[off + j];
        }
        return sum;
    }

    /** Keeps the ones that matter most. */
    private static void addKept(List<Node> nodes, List<Node> found) {
        found.sort((a, b) -> Float.compare(Math.abs(b.effect()), Math.abs(a.effect())));
        nodes.addAll(found.subList(0, Math.min(KEPT, found.size())));
    }

    /** Each node's strongest few edges in, and every edge to the score. */
    private static List<Edge> prune(List<Edge> edges, int nodes) {
        List<Edge> out = new ArrayList<>();
        for (int to = -1; to < nodes; to++) {
            List<Edge> in = new ArrayList<>();
            for (Edge e : edges) {
                if (e.to() == to) {
                    in.add(e);
                }
            }
            in.sort((a, b) -> Float.compare(Math.abs(b.weight()), Math.abs(a.weight())));
            out.addAll(to < 0 ? in : in.subList(0, Math.min(INTO, in.size())));
        }
        return out;
    }

    private static Component about(int latent, int position, float amount, float effect, float linear, Features.Feature f, Tokens one, Vocab vocab) {
        MutableComponent tip = Component.translatable("residual.scope.graph.node", "#" + latent, Picture.shown(vocab.piece(one.ids[position])), Picture.two(amount));
        tip.append("\n").append(Component.translatable("residual.scope.graph.check", Picture.signed(effect), Picture.signed(linear)));
        if (f != null) {
            for (Component c : f.contexts()) {
                tip.append("\n").append(c);
            }
        }
        return tip;
    }

    private static float agreement(List<Node> nodes) {
        double sx = 0, sy = 0, xx = 0, yy = 0, xy = 0;
        int n = 0;
        for (Node node : nodes) {
            if (node.layer() > 0) {
                sx += node.linear();
                sy += node.effect();
                xx += node.linear() * node.linear();
                yy += node.effect() * node.effect();
                xy += node.linear() * node.effect();
                n++;
            }
        }
        double cov = n * xy - sx * sy, vx = n * xx - sx * sx, vy = n * yy - sy * sy;
        return vx <= 0 || vy <= 0 ? 0 : (float) (cov / Math.sqrt(vx * vy));
    }

    /** The tokens on the left, a column of features for each point of the stream, and the score on the right. */
    public static Picture picture(Graph g) {
        Vocab vocab = g.stream.model.env.tokenizer();
        Picture.Builder b = new Picture.Builder();
        b.text(4, 3, Component.translatable("residual.scope.graph.title", Picture.shown(vocab.piece(g.target)), Picture.two(g.score), Picture.two(g.agreement)), Picture.DIM);
        int layers = 0;
        for (Node n : g.nodes) {
            layers = Math.max(layers, n.layer());
        }
        int columns = layers + 2, boxW = 44, boxH = 13, gap = (Picture.W - 8 - columns * boxW) / Math.max(1, columns - 1), top = 18;
        int[] count = new int[layers + 1], placed = new int[layers + 1];
        for (Node n : g.nodes) {
            count[n.layer()]++;
        }
        int[][] at = new int[g.nodes.size()][2];
        float strongest = 1e-6f;
        for (Node n : g.nodes) {
            strongest = Math.max(strongest, Math.abs(n.effect()));
        }
        for (int i = 0; i < g.nodes.size(); i++) {
            Node n = g.nodes.get(i);
            int rowH = (Picture.H - top - 4) / Math.max(1, count[n.layer()]);
            at[i][0] = 4 + n.layer() * (boxW + gap);
            at[i][1] = top + placed[n.layer()]++ * rowH + (rowH - boxH) / 2;
        }
        int ox = 4 + (layers + 1) * (boxW + gap), oy = top + (Picture.H - top - 4 - boxH) / 2;
        float widest = 1e-6f;
        for (Edge e : g.edges) {
            widest = Math.max(widest, Math.abs(e.weight()));
        }
        for (Edge e : g.edges) {
            int x0 = at[e.from()][0] + boxW, y0 = at[e.from()][1] + boxH / 2;
            int x1 = e.to() < 0 ? ox : at[e.to()][0], y1 = e.to() < 0 ? oy + boxH / 2 : at[e.to()][1] + boxH / 2;
            int alpha = Math.round(0x40 + 0xBF * Math.min(1f, Math.abs(e.weight()) / widest)) << 24;
            b.line(x0, y0, x1, y1, alpha | (e.weight() >= 0 ? Picture.AMBER : Picture.BLUE) & 0xFFFFFF);
        }
        for (int i = 0; i < g.nodes.size(); i++) {
            Node n = g.nodes.get(i);
            Component tip = n.layer() == 0 ? Component.translatable("residual.scope.graph.token", n.label(), Integer.toString(n.position()), Picture.signed(n.effect())) : n.about();
            b.box(at[i][0], at[i][1], boxW, boxH, Picture.heat(n.effect() / strongest * 0.6f), tip);
            b.text(at[i][0] + 2, at[i][1] + 3, boxW - 4, n.label(), Picture.TEXT);
        }
        b.box(ox, oy, boxW, boxH, 0xFF1F7A72, Component.translatable("residual.scope.graph.output", Picture.shown(vocab.piece(g.target)), Picture.two(g.score)));
        b.text(ox + 2, oy + 3, boxW - 4, Picture.shown(vocab.piece(g.target)), Picture.TEXT);
        return b.build();
    }
}
