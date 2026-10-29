package io.github.metrovoc.residual.learn;

import java.util.Random;

/**
 * How a crop grows when watched every {@link #OBSERVE} ticks, written the way a Forecaster records it: one line per
 * crop, its age at each look, from planting until it has been seen ripe {@link #RIPE_FOR} times. A crop grows on random
 * ticks, and each one grows it with a chance set by the soil and its neighbours, the way the game itself does.
 */
public final class Growth {
    /** Ticks between two looks at a field. */
    public static final int OBSERVE = 400;
    public static final int RIPE = 7;
    /** Looks at a ripe crop before its line ends. */
    public static final int RIPE_FOR = 2;
    /** Random ticks a block gets between two looks: each tick the game picks three blocks of every 16³ section. */
    public static final double RANDOM_TICKS = OBSERVE * 3 / 4096.0;
    /**
     * The growth chance per random tick in common fields, and how common each is in the benchmark: one row on wet
     * farmland, a dense wet field, the edge of one, and a dense dry field.
     */
    private static final double[] CHANCES = {1 / 3.0, 1 / 6.0, 1 / 7.0, 1 / 13.0};
    private static final double[] SHARES = {0.3, 0.4, 0.2, 0.1};

    private Growth() {
    }

    /** A field drawn from the benchmark's mix: its growth chance per random tick. */
    public static double chance(Random rng) {
        double r = rng.nextDouble();
        for (int i = 0; i < CHANCES.length - 1; i++) {
            r -= SHARES[i];
            if (r < 0) {
                return CHANCES[i];
            }
        }
        return CHANCES[CHANCES.length - 1];
    }

    /** One crop's line: {@code 000011112…77}. */
    public static String life(Random rng, double chance) {
        StringBuilder line = new StringBuilder("0");
        int age = 0, ripe = 0;
        while (ripe < RIPE_FOR) {
            age = Math.min(RIPE, age + poisson(rng, RANDOM_TICKS * chance));
            line.append((char) ('0' + age));
            if (age == RIPE) {
                ripe++;
            }
        }
        return line.toString();
    }

    static int poisson(Random rng, double mean) {
        double limit = Math.exp(-mean), p = rng.nextDouble();
        int k = 0;
        while (p > limit) {
            p *= rng.nextDouble();
            k++;
        }
        return k;
    }
}
