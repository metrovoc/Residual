package io.github.metrovoc.residual.scope;

import java.util.Arrays;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * A sparse autoencoder: many more directions than the stream has dimensions, of which only the {@link #active} that fit a place best may
 * add up to what the stream carries there. Forced to say so much with so few, its directions tend to come out as things that mean
 * something. Each direction is a row of {@link #decoder}, kept at length one.
 */
public final class Autoencoder {
    public final int width, latents, active;
    /** [latents][width] each. */
    public final float[] encoder, decoder;
    /** What is taken off the stream first, and what it is divided by, so a place's length comes to about the square root of its width. */
    public final float[] center;
    public final float scale;

    private Autoencoder(int width, int latents, int active, float[] encoder, float[] decoder, float[] center, float scale) {
        this.width = width;
        this.latents = latents;
        this.active = active;
        this.encoder = encoder;
        this.decoder = decoder;
        this.center = center;
        this.scale = scale;
    }

    /** Fits to {@code n} places of the stream, {@code x} their [n][width] values. */
    public static Autoencoder fit(float[] x, int n, int width, int latents, int active, int steps, long seed, BooleanSupplier stop, IntConsumer step) {
        Random rng = new Random(seed);
        float[] center = new float[width];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < width; j++) {
                center[j] += x[i * width + j] / n;
            }
        }
        double spread = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < width; j++) {
                double c = x[i * width + j] - center[j];
                spread += c * c;
            }
        }
        float scale = (float) Math.sqrt(spread / (n * (double) width) + 1e-12);
        float[] decoder = new float[latents * width], encoder = new float[latents * width];
        // each direction starts as a place it has seen, which keeps most of them alive
        for (int l = 0; l < latents; l++) {
            int at = rng.nextInt(n);
            for (int j = 0; j < width; j++) {
                decoder[l * width + j] = x[at * width + j] - center[j] + (float) rng.nextGaussian() * scale * 0.1f;
            }
            normalize(decoder, l * width, width);
            for (int j = 0; j < width; j++) {
                encoder[l * width + j] = decoder[l * width + j] / (float) Math.sqrt(active);
            }
        }
        Autoencoder sae = new Autoencoder(width, latents, active, encoder, decoder, center, scale);
        sae.train(x, n, steps, rng, stop, step);
        return sae;
    }

    private static void normalize(float[] m, int off, int width) {
        double len = 0;
        for (int j = 0; j < width; j++) {
            len += m[off + j] * m[off + j];
        }
        float s = (float) (1 / Math.sqrt(len + 1e-12));
        for (int j = 0; j < width; j++) {
            m[off + j] *= s;
        }
    }

    private void train(float[] x, int n, int steps, Random rng, BooleanSupplier stop, IntConsumer report) {
        int batch = 256, size = latents * width;
        float[] gradE = new float[size], gradD = new float[size], mE = new float[size], vE = new float[size], mD = new float[size], vD = new float[size];
        float[] in = new float[width], recon = new float[width];
        int[] ids = new int[active];
        float[] values = new float[active];
        boolean[] touched = new boolean[latents];
        float lr = 1e-3f;
        for (int step = 1; step <= steps && !stop.getAsBoolean(); step++) {
            Arrays.fill(gradE, 0f);
            Arrays.fill(gradD, 0f);
            Arrays.fill(touched, false);
            for (int b = 0; b < batch; b++) {
                int at = rng.nextInt(n);
                for (int j = 0; j < width; j++) {
                    in[j] = (x[at * width + j] - center[j]) / scale;
                }
                int found = encodeScaled(in, 0, ids, values);
                Arrays.fill(recon, 0f);
                for (int k = 0; k < found; k++) {
                    int off = ids[k] * width;
                    for (int j = 0; j < width; j++) {
                        recon[j] += values[k] * decoder[off + j];
                    }
                }
                for (int j = 0; j < width; j++) {
                    recon[j] -= in[j];
                }
                for (int k = 0; k < found; k++) {
                    int off = ids[k] * width;
                    float along = 0;
                    for (int j = 0; j < width; j++) {
                        along += decoder[off + j] * recon[j];
                    }
                    float c = 2f / batch;
                    for (int j = 0; j < width; j++) {
                        gradD[off + j] += c * values[k] * recon[j];
                        gradE[off + j] += c * along * in[j];
                    }
                    touched[ids[k]] = true;
                }
            }
            double bc1 = 1 - Math.pow(0.9, step), bc2 = 1 - Math.pow(0.999, step);
            for (int l = 0; l < latents; l++) {
                // a direction that fired on nothing this step keeps its place
                if (!touched[l]) {
                    continue;
                }
                int off = l * width;
                adam(encoder, gradE, mE, vE, off, width, lr, bc1, bc2);
                adam(decoder, gradD, mD, vD, off, width, lr, bc1, bc2);
                normalize(decoder, off, width);
            }
            if (step % 50 == 0) {
                report.accept(step);
            }
        }
    }

    private static void adam(float[] p, float[] g, float[] m, float[] v, int off, int n, float lr, double bc1, double bc2) {
        for (int i = off; i < off + n; i++) {
            m[i] = 0.9f * m[i] + 0.1f * g[i];
            v[i] = 0.999f * v[i] + 0.001f * g[i] * g[i];
            p[i] -= (float) (lr * (m[i] / bc1) / (Math.sqrt(v[i] / bc2) + 1e-8));
        }
    }

    /** The directions that fit a place best, and how much of each, in the fit's units: returns how many fired. */
    private int encodeScaled(float[] in, int off, int[] ids, float[] values) {
        int found = 0;
        for (int l = 0; l < latents; l++) {
            float z = 0;
            int row = l * width;
            for (int j = 0; j < width; j++) {
                z += encoder[row + j] * in[off + j];
            }
            if (z <= 0) {
                continue;
            }
            if (found < active) {
                int k = found++;
                while (k > 0 && values[k - 1] < z) {
                    values[k] = values[k - 1];
                    ids[k] = ids[k - 1];
                    k--;
                }
                values[k] = z;
                ids[k] = l;
            } else if (z > values[active - 1]) {
                int k = active - 1;
                while (k > 0 && values[k - 1] < z) {
                    values[k] = values[k - 1];
                    ids[k] = ids[k - 1];
                    k--;
                }
                values[k] = z;
                ids[k] = l;
            }
        }
        return found;
    }

    /** The directions at one place of the stream, {@code x} from {@code off}; values are the stream's own units along each direction. */
    public int encode(float[] x, int off, int[] ids, float[] values) {
        float[] in = new float[width];
        for (int j = 0; j < width; j++) {
            in[j] = (x[off + j] - center[j]) / scale;
        }
        int found = encodeScaled(in, 0, ids, values);
        for (int k = 0; k < found; k++) {
            values[k] *= scale;
        }
        return found;
    }

    /** What the fit says the stream carries at one place. */
    public void reconstruct(float[] x, int off, float[] out, int outOff) {
        int[] ids = new int[active];
        float[] values = new float[active];
        int found = encode(x, off, ids, values);
        System.arraycopy(center, 0, out, outOff, width);
        for (int k = 0; k < found; k++) {
            for (int j = 0; j < width; j++) {
                out[outOff + j] += values[k] * decoder[ids[k] * width + j];
            }
        }
    }
}
