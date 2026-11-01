package io.github.metrovoc.residual.learn;

import java.util.Random;

/**
 * Pilot: holding a flight frame at its height against a wind. A flight's line starts with the wind as the frame's vane
 * reads it ({@link #VANE}), then gives {@link #LOOKS} looks at the frame's height, a letter each from {@code a} (lowest)
 * to {@code h}, the hold {@code d} and {@code e}, each followed by the thrust the pilot chose: {@code 0} less, {@code 1}
 * as it was, {@code 2} more. Thrust changes how fast the frame climbs or sinks, up to two steps a look, not where it is,
 * so a pilot has to read its speed from the heights before. The wind pushes down or up every two or three looks, as the
 * vane says, but from a look of its own, which a pilot has to make out from how the flight has gone and meet before it
 * pushes. A flight starts in the hold and at rest; practice counts a look a step off the hold for half.
 *
 * <p>It is practised by playing ({@link Played}). A model that sees only the last three characters holds at most 44% of
 * the flights in its worst wind; a two-block transformer holds all of them after about 1,050 steps (CalibrationTest
 * {@code pilot}).
 */
public final class Pilot extends Played {
    /** The winds, a kind each: every so many looks, by so much, down or up; and the letter the frame's vane reads each as. */
    private static final int[][] WINDS = {{2, -1}, {3, -1}, {2, 1}, {3, 1}};
    public static final String VANE = "wxyz";
    private static final int FASTEST = 2;

    private final int[][] winds;
    private final int fastest;

    public Pilot() {
        this(WINDS, FASTEST);
    }

    /** Flights in other winds, or that climb or sink faster, to calibrate with. */
    Pilot(int[][] winds, int fastest) {
        this.winds = winds;
        this.fastest = fastest;
    }

    @Override
    protected int kinds() {
        return winds.length;
    }

    @Override
    protected Episode episode(int kind, Random rng) {
        return new Flight(this, kind, rng.nextInt(winds[kind][0]), rng.nextBoolean() ? 3 : 4);
    }

    /** One flight: the frame's height and speed, and when its wind pushes. */
    public static final class Flight extends Episode {
        private final Pilot pilot;
        private final int phase;
        private int height;
        private int speed;

        Flight(Pilot pilot, int wind, int phase, int height) {
            super(wind, VANE.charAt(wind));
            this.pilot = pilot;
            this.phase = phase;
            this.height = height;
        }

        @Override
        public char band() {
            return (char) ('a' + height);
        }

        @Override
        protected double move(int thrust, Random rng, int look) {
            speed += thrust - 1;
            int[] wind = pilot.winds[kind];
            if ((look + phase) % wind[0] == wind[0] - 1) {
                speed += wind[1];
            }
            speed = Math.clamp(speed, -pilot.fastest, pilot.fastest);
            height += speed;
            if (height < 0 || height > 7) {
                // the ground, or as high as it climbs: it stops there
                height = Math.clamp(height, 0, 7);
                speed = 0;
            }
            return height == 3 || height == 4 ? 1 : height == 2 || height == 5 ? 0.5 : 0;
        }
    }
}
