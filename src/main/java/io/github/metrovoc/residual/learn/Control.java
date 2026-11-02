package io.github.metrovoc.residual.learn;

import java.util.Random;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;

/**
 * Control: holding a Smelter's melt in its band. A melt's line starts with its ore, then gives {@link #LOOKS} looks at
 * its temperature, a letter each from {@code a} (coldest) to {@code h}, the band {@code d} and {@code e}, each followed
 * by the power the controller chose: {@code 0} cools it a step, {@code 1} holds, {@code 2} heats it a step. Each ore
 * drifts on a rhythm of its own as it melts, gold and iron cooling, copper and the rest giving off heat; a fresh charge of
 * cold ore drops it two steps at looks 8 and 16; now and then it jumps a step for no reason anyone could foresee.
 * A melt is held when at least {@link #HELD} of its scored looks end in the band. A model that sees only the last three
 * characters holds most copper melts but no gold or iron; a two-block transformer, which can tell the ore from the
 * line's start and where in the melt it is, passes after 200 to 400 steps from each of five seeds (CalibrationTest
 * {@code control_seeds}).
 *
 * <p>It is practised by playing ({@link Played}): the test scores the share held of the ore it holds worst, as a
 * Smelter pays for them.
 */
public final class Control extends Played {
    /** Letters of the ores, as a melt's line starts: gold, iron, copper, the rest. */
    public static final String ORES = "gico";
    /** How each ore drifts as it melts: every so many looks, by so many steps. */
    private static final int[][] DRIFTS = {{3, -1}, {2, -1}, {3, 1}, {2, 1}};
    private static final double JUMP = 0.02;

    /** Which of {@link #ORES} {@code ore} melts as: gold, iron, copper, or the rest. */
    public static int ore(ItemStack ore) {
        if (ore.is(Tags.Items.ORES_GOLD) || ore.is(Tags.Items.RAW_MATERIALS_GOLD)) {
            return 0;
        }
        if (ore.is(Tags.Items.ORES_IRON) || ore.is(Tags.Items.RAW_MATERIALS_IRON)) {
            return 1;
        }
        if (ore.is(Tags.Items.ORES_COPPER) || ore.is(Tags.Items.RAW_MATERIALS_COPPER)) {
            return 2;
        }
        return 3;
    }

    @Override
    protected int kinds() {
        return ORES.length();
    }

    @Override
    protected Episode episode(int kind, Random rng) {
        return new Melt(kind);
    }

    /** One melt: its temperature step and its line so far. */
    public static final class Melt extends Episode {
        private int level = 3;

        public Melt(int ore) {
            super(ore, ORES.charAt(ore));
        }

        @Override
        public char band() {
            return (char) ('a' + level);
        }

        @Override
        protected double move(int power, Random rng, int look) {
            level = next(level, power, kind, look, rng);
            return level == 3 || level == 4 ? 1 : 0;
        }

        /** Where a melt of {@code ore} at {@code level} ends up under {@code power}, at the {@code look}th look. */
        static int next(int level, int power, int ore, int look, Random rng) {
            level += power - 1;
            int[] drift = DRIFTS[ore];
            if (look % drift[0] == drift[0] - 1) {
                level += drift[1];
            }
            if (look == 8 || look == 16) {
                level -= 2;
            }
            if (rng.nextDouble() < JUMP) {
                level += rng.nextBoolean() ? 1 : -1;
            }
            return Math.clamp(level, 0, 7);
        }
    }
}
