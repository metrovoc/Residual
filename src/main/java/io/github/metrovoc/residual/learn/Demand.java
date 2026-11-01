package io.github.metrovoc.residual.learn;

import java.util.Random;

/**
 * What a Port writes down of what it moves out: one digit a look for how much went, nothing or up to 16, 64, 256 or more.
 * A model that goes on with such a line says what the next looks will move, so the Port can have it made first. Bases draw
 * in rhythms, and only looking back at how the line went before shows where in its rhythm it is.
 */
public final class Demand {
    /** Ticks between looks: 5 seconds. */
    public static final int LOOK = 100;
    /** Looks a model is asked to write ahead: 40 seconds. */
    public static final int AHEAD = 8;
    /** The longest line a model reads, leaving room for what it writes. */
    public static final int LINE = Vocab.CONTEXT - AHEAD;
    /** The shortest it is worth asking about. */
    public static final int READS = 24;
    /** How sure of its least sure look a model must be for a Port to act on it. */
    public static final float SURE = 0.6f;
    /** What begins each line of a Port's Field Log, which tells it from a Forecaster's. */
    public static final char START = '|';

    private static final long[] MOST = {0, 16, 64, 256, 1024};
    /** The most looks a rhythm takes to come round: a minute. */
    private static final int LONGEST = 12;
    private static final float SLIP = 0.01f;

    /** A line of looks, and where it has shown enough of itself to be scored. */
    public record Line(String looks, int scoredFrom) {
    }

    private Demand() {
    }

    /** The digit for {@code moved} items. */
    public static char look(long moved) {
        for (int d = 0; d < MOST.length - 1; d++) {
            if (moved <= MOST[d]) {
                return (char) ('0' + d);
            }
        }
        return (char) ('0' + MOST.length - 1);
    }

    /** How much {@code looks} would move, each at the most its digit stands for; anything but a digit moves nothing. */
    public static long moved(String looks) {
        long n = 0;
        for (int i = 0; i < looks.length(); i++) {
            int d = looks.charAt(i) - '0';
            n += d >= 0 && d < MOST.length ? MOST[d] : 0;
        }
        return n;
    }

    /** Whether a Field Log holds a Port's lines. */
    public static boolean isLog(String log) {
        return !log.isEmpty() && log.charAt(0) == START;
    }

    /**
     * How a base draws: bursts of one size on a quieter steady draw, coming round in a rhythm, or one steady draw, and now and
     * then a look that does not fit. The test's mixes of rhythm, burst and draw are ones practice never shows.
     */
    record Rhythm(int period, int width, int quiet, int burst, boolean steady, int phase) {
        static Rhythm draw(Random rng, boolean test) {
            int period, width, quiet, burst;
            do {
                period = 2 + rng.nextInt(LONGEST - 1);
                width = 1 + rng.nextInt(Math.min(3, period - 1));
                quiet = rng.nextInt(3) == 0 ? 1 : 0;
                burst = quiet + 1 + rng.nextInt(MOST.length - 1 - quiet);
            } while ((period * 7 + width * 3 + quiet * 5 + burst) % 5 == 0 != test);
            return new Rhythm(period, width, quiet, burst, rng.nextInt(8) == 0, rng.nextInt(period));
        }

        /** The digit it means for look {@code i}, as if it took {@code stretch} times as long to come round. */
        int digit(int i, int stretch) {
            return steady || (i + phase) % (period * stretch) < width ? burst : quiet;
        }

        /** How many looks it takes to show itself. */
        int shown() {
            return steady ? 2 : 2 * period + 1;
        }
    }

    /** The digit for a look, or now and then one that does not fit. */
    static char slip(Random rng, int digit) {
        return (char) ('0' + (rng.nextFloat() < SLIP ? rng.nextInt(MOST.length) : digit));
    }

    /** A line of {@code length} looks. */
    static Line line(Random rng, int length, boolean test) {
        Rhythm rhythm = Rhythm.draw(rng, test);
        StringBuilder looks = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            looks.append(slip(rng, rhythm.digit(i, 1)));
        }
        return new Line(looks.toString(), rhythm.shown());
    }
}
