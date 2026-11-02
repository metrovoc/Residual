package io.github.metrovoc.residual.learn;

import java.util.Random;

/**
 * Precision: holding a melt not in the band but at one step of it, {@code d}. It is the melt {@link Control} plays, but what was near enough there is a miss here: a controller that
 * only answers what the last look showed is a step out every time its ore drifts, so it has to know the ore's rhythm and act before it. A fresh charge still costs a look or two, so a melt
 * is held with {@link #HELD} of its looks at the mark. Its line starts with its ore's letter in capitals, so one model can do both.
 *
 * <p>It is not practised on a dataset. What a controller is told by practice is how good its choices were; here it takes a checker's word on whole melts, on a Tuner.
 */
public final class Precision extends Played {
    /** The share of scored looks at the mark that hold a melt. */
    public static final double HELD = 0.7;
    public static final String ORES = "GICO";
    private static final int AT = 3;

    @Override
    protected int kinds() {
        return ORES.length();
    }

    @Override
    protected Episode episode(int kind, Random rng) {
        return new Melt(kind);
    }

    /** One melt, at the mark or not. */
    public static final class Melt extends Episode {
        private int level = AT;

        public Melt(int ore) {
            super(ore, ORES.charAt(ore));
        }

        @Override
        public char band() {
            return (char) ('a' + level);
        }

        @Override
        protected double move(int power, Random rng, int look) {
            level = Control.Melt.next(level, power, kind, look, rng);
            return level == AT ? 1 : Math.abs(level - AT) == 1 ? 0.25 : 0;
        }

        @Override
        public boolean held() {
            return over() && score() >= HELD;
        }
    }
}
