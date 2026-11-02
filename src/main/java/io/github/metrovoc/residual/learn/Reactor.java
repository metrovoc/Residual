package io.github.metrovoc.residual.learn;

import java.util.Random;

/**
 * Containment: holding a reactor at its power with rods that answer late. A cycle's line starts with the fuel's age
 * ({@link #AGES}), then gives {@link #LOOKS} looks at the core's power, a letter each from {@code a} (dying) to
 * {@code h} (running away), the band {@code d} and {@code e}, each followed by what the controller did to the rods:
 * {@code 0} in, {@code 1} as they are, {@code 2} out. Moving the rods changes how fast the power climbs or sinks, up to
 * two steps a look, and only from the look after next. The fuel pushes the same way every two or three looks, as its age
 * says, but from a look of its own, which a controller has to make out from how the cycle has gone and meet before it
 * pushes. Past the band's edges a core feeds on itself: at {@code g} and {@code h} it climbs faster each look, and at
 * {@code a} and {@code b} it sinks faster. Now and then the power jumps a step for no reason anyone could foresee.
 *
 * <p>It is practised by playing ({@link Played}). The test scores the share of cycles held of the fuel held worst, as a
 * reactor pays for them; a reactor trusts no controller that fails it, and it stops itself rather than go on without one. No
 * rule holds a cycle of the worst fuel ({@code ReactorTest}), nor a model that sees only the last three characters; a two-block
 * transformer 32 wide scores 0.90625 at 4,500 steps ({@code KeepModelTest}), passing the 0.9 gate. A live reactor with
 * that controller completed 16 cycles across all four fuel ages without a trip; 12 of those cycles met the held target.
 */
public final class Reactor extends Played {
    /** The fuels by age, fresh to spent: every so many looks, by so much, up or down; and the letter each cycle's line opens with. */
    private static final int[][] FUELS = {{2, 1}, {3, 1}, {3, -1}, {2, -1}};
    public static final String AGES = "fgos";
    /** Looks after it is chosen that a move of the rods takes effect, past the look it is chosen at. */
    private static final int LATE = 1;
    private static final int FASTEST = 2;
    /** Where a core starts to feed on itself: at or above, at or below. */
    private static final int HOT = 6, COLD = 1;
    private static final double JUMP = 0.01;

    private final int[][] fuels;
    private final int late, hot, cold;

    public Reactor() {
        this(FUELS, LATE, HOT, COLD);
    }

    /** Fuels, rods or a core of another kind, to calibrate with. */
    Reactor(int[][] fuels, int late, int hot, int cold) {
        this.fuels = fuels;
        this.late = late;
        this.hot = hot;
        this.cold = cold;
    }

    @Override
    protected int kinds() {
        return fuels.length;
    }

    @Override
    protected Episode episode(int kind, Random rng) {
        return new Cycle(kind, rng.nextInt(fuels[kind][0]), rng.nextBoolean() ? 3 : 4, fuels, late, hot, cold);
    }

    /** A cycle of the fuel at this age, from a level start. */
    public static Cycle cycle(int age, Random rng) {
        return new Cycle(age, rng.nextInt(FUELS[age][0]), rng.nextBoolean() ? 3 : 4, FUELS, LATE, HOT, COLD);
    }

    /** One cycle: the core's power and how fast it is moving, when its fuel pushes, and what the rods are about to do. */
    public static final class Cycle extends Episode {
        private final int[][] fuels;
        private final int phase, hot, cold;
        private final int[] rods;
        private int power;
        private int speed;

        Cycle(int age, int phase, int power, int[][] fuels, int late, int hot, int cold) {
            super(age, AGES.charAt(age));
            this.phase = phase;
            this.power = power;
            this.fuels = fuels;
            this.rods = new int[late];
            this.hot = hot;
            this.cold = cold;
        }

        @Override
        public char band() {
            return (char) ('a' + power);
        }

        /** The core's power now, 0 to 7. */
        public int power() {
            return power;
        }

        @Override
        protected double move(int moved, Random rng, int look) {
            int arriving = rods.length == 0 ? moved - 1 : rods[0];
            if (rods.length > 0) {
                System.arraycopy(rods, 1, rods, 0, rods.length - 1);
                rods[rods.length - 1] = moved - 1;
            }
            speed += arriving;
            int[] fuel = fuels[kind];
            if ((look + phase) % fuel[0] == fuel[0] - 1) {
                speed += fuel[1];
            }
            if (power >= hot) {
                speed++;
            } else if (power <= cold) {
                speed--;
            }
            speed = Math.clamp(speed, -FASTEST, FASTEST);
            power += speed;
            if (rng.nextDouble() < JUMP) {
                power += rng.nextBoolean() ? 1 : -1;
            }
            if (power < 0 || power > 7) {
                // it cannot read past the ends of its gauge, which is where it trips
                power = Math.clamp(power, 0, 7);
                speed = 0;
            }
            return power == 3 || power == 4 ? 1 : power == 2 || power == 5 ? 0.5 : 0;
        }
    }
}
