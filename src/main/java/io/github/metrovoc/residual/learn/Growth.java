package io.github.metrovoc.residual.learn;

import java.util.Random;

/**
 * What the blocks a Forecaster watches do, written the way it records them, one line per block. A crop's line is its
 * age at each look, every {@link #OBSERVE} ticks, from planting until it has been seen ripe {@link #RIPE_FOR} times; a
 * crop grows on random ticks, and each one grows it with a chance set by the soil and its neighbours, the way the game
 * itself does. A machine's line is its progress through the item it is making, eight looks an item, from its first
 * look until it has been seen idle twice: {@code abcdefghabcd…aa}.
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
    /** A machine's progress at a look: {@code a} for none of its item, {@code h} for seven eighths. */
    public static final char IDLE = 'a';
    public static final int EIGHTHS = 8;
    /**
     * Ticks an item takes in the machines a Forecaster watches, and how common each is in the benchmark: a furnace, a
     * smoker or blast furnace, a brewing stand.
     */
    private static final int[] COOKS = {200, 100, 400};
    private static final double[] COOK_SHARES = {0.5, 0.35, 0.15};
    /** Items a machine makes in a run, on average, before its input runs out. */
    private static final double ITEMS = 9;

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

    /** A machine drawn from the benchmark's mix: ticks an item takes in it. */
    public static int cook(Random rng) {
        double r = rng.nextDouble();
        for (int i = 0; i < COOKS.length - 1; i++) {
            r -= COOK_SHARES[i];
            if (r < 0) {
                return COOKS[i];
            }
        }
        return COOKS[COOKS.length - 1];
    }

    /**
     * Ticks between two looks at a machine that takes {@code cook} ticks an item: an eighth of an item, rounded up, so
     * a working machine never shows the same progress twice in a row.
     */
    public static int every(int cook) {
        return Math.max(1, (cook + EIGHTHS - 1) / EIGHTHS);
    }

    /** A machine's progress at a look, for a timer {@code timer} ticks into an item of {@code cook}. */
    public static char progress(int timer, int cook) {
        return (char) (IDLE + Math.clamp((long) timer * EIGHTHS / Math.max(1, cook), 0, EIGHTHS - 1));
    }

    /**
     * One machine's line: {@code abcdefghabcd…aa}. It works through a run of items until its input runs out; now and
     * then its fuel runs out first, and a furnace's item half made cools back to nothing, two ticks for every tick.
     */
    public static String work(Random rng, int cook) {
        int every = every(cook);
        long end = (long) Math.ceil(-Math.log(1 - rng.nextDouble()) * ITEMS) * cook;
        long cold = cook < 400 && rng.nextInt(4) == 0 ? (long) (rng.nextDouble() * end) : Long.MAX_VALUE;
        StringBuilder line = new StringBuilder();
        char last = 0;
        for (long t = rng.nextInt(every); ; t += every) {
            int timer = t < Math.min(end, cold) ? (int) (t % cook)
                    : t >= cold ? (int) Math.max(0, cold % cook - 2 * (t - cold)) : 0;
            char look = progress(timer, cook);
            line.append(look);
            if (look == IDLE && last == IDLE) {
                return line.toString();
            }
            last = look;
        }
    }

    /**
     * How far a machine got between two looks, in eighths of an item: forward through the items it made, or back as
     * an unfinished item cools.
     */
    public static int advance(char from, char to) {
        int d = Math.floorMod(to - from, EIGHTHS);
        return d > EIGHTHS / 2 ? d - EIGHTHS : d;
    }

    public static boolean isAge(char c) {
        return c >= '0' && c <= '0' + RIPE;
    }

    public static boolean isProgress(char c) {
        return c >= IDLE && c < IDLE + EIGHTHS;
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
