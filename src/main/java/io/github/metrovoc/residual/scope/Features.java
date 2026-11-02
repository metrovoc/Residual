package io.github.metrovoc.residual.scope;

import io.github.metrovoc.residual.graph.Captures;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.learn.Batch;
import io.github.metrovoc.residual.learn.Sampler;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.tensor.Ops;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Sparse-autoencoder features on the residual stream: a dictionary fitted to what one point of the stream carries over a benchmark's
 * test, and what each of its directions turned out to fire on and to push the scores toward, read from text it was not fitted to.
 */
public final class Features {
    /** How many places a fit reads and how many it is judged on, how long it fits, and how many of each feature's strongest places it keeps. */
    private static final int READ = 48_000, JUDGED = 12_000, STEPS = 1500, CONTEXTS = 5;
    /** How much of the text around a place a tooltip shows. */
    private static final int BEFORE = 18, AFTER = 8;

    /**
     * One direction.
     *
     * @param frequency how often it fires, of the judged places
     * @param strongest what it reached there, along its direction
     * @param label     the token it fired on most strongly
     * @param contexts  where it fired most strongly
     * @param pushes    what it pushes the scores toward, most first
     */
    public record Feature(int latent, float frequency, float strongest, String label, List<Component> contexts, List<String> pushes) {
    }

    /**
     * What a fit made of one point.
     *
     * @param explained how much of what the stream varies by the fit accounts for, on places it was not fitted to
     * @param dead      directions that fired on none of those
     * @param loss      the model's loss there as it is, with the fit's version of the stream, and with nothing but the stream's average
     */
    public record Dictionary(Stream stream, int point, Autoencoder sae, float explained, int dead, float loss, float lossFitted, float lossGone,
                             List<Feature> features) {
        /** How much of what this point of the stream is worth, the fit's version keeps. */
        public float recovered() {
            return lossGone <= loss ? 0 : (lossGone - lossFitted) / (lossGone - loss);
        }

        public Feature feature(int latent) {
            for (Feature f : features) {
                if (f.latent() == latent) {
                    return f;
                }
            }
            return null;
        }
    }

    private Features() {
    }

    /** The size of dictionary a stream of {@code width} gets. */
    static int latents(int width) {
        return Math.min(1024, 8 * width);
    }

    /** How many directions a dictionary may use at once. */
    static int active(int width) {
        return Math.max(4, width / 8);
    }

    /**
     * Fits a dictionary at one point, on stretches from {@code stretches}, which takes a seeded source and how many to give.
     */
    public static Dictionary fit(Stream stream, int point, Function<Random, Batch> stretches, long seed, BooleanSupplier stop, IntConsumer progress) {
        CompiledGraph model = stream.model;
        int d = stream.width();
        Random rng = new Random(seed);
        float[] x = gather(model, point, stretches, rng, READ, d);
        int n = x.length / d;
        int steps = Math.min(STEPS, Math.max(400, n * 4 / 256));
        Autoencoder sae = Autoencoder.fit(x, n, d, latents(d), active(d), steps, seed, stop, s -> progress.accept(100 * s / steps));
        return judge(stream, point, sae, stretches, new Random(seed + 1));
    }

    private static float[] gather(CompiledGraph model, int point, Function<Random, Batch> stretches, Random rng, int want, int d) {
        float[] x = new float[0];
        int have = 0;
        while (have < want) {
            Batch b = stretches.apply(rng);
            float[] out = Reads.run(model, b.input()).outputs.get(point).data;
            if (x.length < (have + out.length / d) * d) {
                x = Arrays.copyOf(x, Math.max(x.length * 2, (have + out.length / d) * d));
            }
            System.arraycopy(out, 0, x, have * d, out.length);
            have += out.length / d;
        }
        return Arrays.copyOf(x, have * d);
    }

    private static Dictionary judge(Stream stream, int point, Autoencoder sae, Function<Random, Batch> stretches, Random rng) {
        CompiledGraph model = stream.model;
        Vocab vocab = model.env.tokenizer();
        int d = stream.width(), m = sae.latents;
        long[] fired = new long[m];
        float[] strongest = new float[m];
        float[][] best = new float[m][CONTEXTS];
        String[][] where = new String[m][CONTEXTS];
        String[] label = new String[m];
        int[] ids = new int[sae.active];
        float[] values = new float[sae.active];
        double error = 0, spread = 0;
        double loss = 0, fitted = 0, gone = 0;
        long places = 0;
        int batches = 0;
        while (places < JUDGED) {
            Batch b = stretches.apply(rng);
            int size = b.input().batch, t = b.input().time;
            float[] out = Reads.run(model, b.input()).outputs.get(point).data;
            float[] rebuilt = new float[out.length];
            for (int i = 0; i < size * t; i++) {
                int found = sae.encode(out, i * d, ids, values);
                sae.reconstruct(out, i * d, rebuilt, i * d);
                for (int j = 0; j < d; j++) {
                    double e = out[i * d + j] - rebuilt[i * d + j], c = out[i * d + j] - sae.center[j];
                    error += e * e;
                    spread += c * c;
                }
                for (int k = 0; k < found; k++) {
                    int l = ids[k];
                    fired[l]++;
                    strongest[l] = Math.max(strongest[l], values[k]);
                    if (values[k] > best[l][CONTEXTS - 1]) {
                        keep(best[l], where[l], values[k], context(vocab, b.input().ids, i / t * t, t, i % t));
                        if (values[k] >= strongest[l]) {
                            label[l] = vocab.piece(b.input().ids[i]);
                        }
                    }
                }
            }
            Tensor plain = model.forward(null, b.input(), null);
            loss += Ops.crossEntropy(null, plain, b.targets(), b.weights()).item();
            fitted += Ops.crossEntropy(null, model.forward(null, b.input(), replacing(point, rebuilt)), b.targets(), b.weights()).item();
            float[] average = new float[out.length];
            for (int i = 0; i < size * t; i++) {
                System.arraycopy(sae.center, 0, average, i * d, d);
            }
            gone += Ops.crossEntropy(null, model.forward(null, b.input(), replacing(point, average)), b.targets(), b.weights()).item();
            places += (long) size * t;
            batches++;
        }
        List<Feature> features = new ArrayList<>();
        int dead = 0;
        for (int l = 0; l < m; l++) {
            if (fired[l] == 0) {
                dead++;
                continue;
            }
            features.add(new Feature(l, (float) fired[l] / places, strongest[l], label[l] == null ? "" : label[l], contexts(where[l]), pushes(stream, sae, l)));
        }
        features.sort((a, b) -> Float.compare(b.frequency() * b.strongest(), a.frequency() * a.strongest()));
        return new Dictionary(stream, point, sae, (float) (1 - error / Math.max(spread, 1e-9)), dead, (float) (loss / batches), (float) (fitted / batches),
                (float) (gone / batches), features);
    }

    private static Captures replacing(int point, float[] with) {
        Captures c = new Captures();
        c.edit = (tape, node, out) -> node == point ? Tensor.of(out.shape, with) : out;
        return c;
    }

    private static void keep(float[] best, String[] where, float value, String text) {
        int k = best.length - 1;
        while (k > 0 && best[k - 1] < value) {
            best[k] = best[k - 1];
            where[k] = where[k - 1];
            k--;
        }
        best[k] = value;
        where[k] = text;
    }

    /** {@code before\u0001token\u0001after}, the text around one place of one stretch. */
    private static String context(Vocab vocab, int[] ids, int row, int t, int at) {
        StringBuilder before = new StringBuilder(), after = new StringBuilder();
        for (int i = at - 1; i >= 0 && before.length() < BEFORE; i--) {
            before.insert(0, vocab.piece(ids[row + i]));
        }
        for (int i = at + 1; i < t && after.length() < AFTER; i++) {
            after.append(vocab.piece(ids[row + i]));
        }
        String head = before.length() > BEFORE ? before.substring(before.length() - BEFORE) : before.toString();
        String tail = after.length() > AFTER ? after.substring(0, AFTER) : after.toString();
        return head + '\u0001' + vocab.piece(ids[row + at]) + '\u0001' + tail;
    }

    private static List<Component> contexts(String[] where) {
        List<Component> out = new ArrayList<>();
        for (String w : where) {
            if (w != null) {
                String[] parts = w.split("\u0001", -1);
                MutableComponent line = Component.literal(Picture.shown(parts[0])).withStyle(ChatFormatting.GRAY);
                line.append(Component.literal(Picture.shown(parts[1])).withStyle(ChatFormatting.GOLD));
                line.append(Component.literal(Picture.shown(parts[2])).withStyle(ChatFormatting.DARK_GRAY));
                out.add(line);
            }
        }
        return out;
    }

    /** The tokens a direction raises most, the head reading it alone against the stream's average. */
    private static List<String> pushes(Stream stream, Autoencoder sae, int latent) {
        Vocab vocab = stream.model.env.tokenizer();
        int d = sae.width;
        float[] base = sae.center.clone(), up = sae.center.clone();
        float len = 2 * sae.scale;
        for (int j = 0; j < d; j++) {
            up[j] += len * sae.decoder[latent * d + j];
        }
        Tensor a = stream.read(null, Tensor.of(new int[]{1, 1, d}, base));
        Tensor b = stream.read(null, Tensor.of(new int[]{1, 1, d}, up));
        float[] lift = new float[a.size()];
        for (int i = 0; i < lift.length; i++) {
            lift[i] = b.data[i] - a.data[i];
        }
        List<String> out = new ArrayList<>();
        for (int id : Sampler.top(lift, 4)) {
            out.add(Picture.shown(vocab.piece(id)));
        }
        return out;
    }

    /** The fit's numbers, then the features that matter most, a row each, as many as fit from {@code from} on. */
    public static Picture picture(Dictionary r, int from) {
        Picture.Builder b = new Picture.Builder();
        b.text(4, 3, Component.translatable("residual.scope.features.title", r.stream.name(r.point), Picture.percent(r.explained),
                Integer.toString(r.sae.active), Integer.toString(r.sae.latents), Integer.toString(r.dead)), Picture.DIM);
        b.text(4, 13, Component.translatable("residual.scope.features.loss", Picture.two(r.loss), Picture.two(r.lossFitted), Picture.two(r.lossGone),
                Picture.percent(r.recovered())), Picture.TEXT);
        int rows = (Picture.H - 28) / 10;
        float top = r.features.isEmpty() ? 1 : r.features.getFirst().frequency();
        for (int i = 0; i < rows && from + i < r.features.size(); i++) {
            Feature f = r.features.get(from + i);
            int y = 26 + i * 10;
            b.box(2, y, Picture.W - 4, 10, 0x00000000, tip(f));
            b.text(4, y + 1, "#" + f.latent(), Picture.DIM);
            b.box(34, y + 2, 40, 6, Picture.GRID);
            b.box(34, y + 2, Math.max(1, Math.round(40 * f.frequency() / top)), 6, Picture.TEAL);
            b.text(78, y + 1, Picture.percent(f.frequency()), Picture.DIM);
            b.text(106, y + 1, 192, firstContext(f), Picture.TEXT);
        }
        return b.build();
    }

    private static Component firstContext(Feature f) {
        return f.contexts().isEmpty() ? Component.empty() : f.contexts().getFirst();
    }

    private static Component tip(Feature f) {
        MutableComponent tip = Component.translatable("residual.scope.features.feature", "#" + f.latent(), Picture.percent(f.frequency()), Picture.two(f.strongest()));
        for (Component c : f.contexts()) {
            tip.append("\n").append(c);
        }
        tip.append("\n").append(Component.translatable("residual.scope.features.pushes", String.join("  ", f.pushes())).withStyle(ChatFormatting.AQUA));
        return tip;
    }
}
