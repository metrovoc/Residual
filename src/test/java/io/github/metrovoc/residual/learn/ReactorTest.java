package io.github.metrovoc.residual.learn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** What Containment asks of a controller: rods that answer late, and that no rule reading the gauge holds. */
class ReactorTest {
    /** A rule: from the power now, how far it moved at the last look, what the rods have on their way, and which fuel; a power for the rods. */
    @FunctionalInterface
    private interface Rule {
        int choose(int power, int moved, int onItsWay, int fuel);
    }

    private static final int[] EVERY = {2, 3, 3, 2}, BY = {1, 1, -1, -1};

    /** The share of cycles a rule holds, of the fuel it holds worst. */
    private static double worst(Rule rule) {
        Random rng = new Random(4242);
        double worst = 1;
        for (int fuel = 0; fuel < Reactor.AGES.length(); fuel++) {
            int held = 0, cycles = 400;
            for (int c = 0; c < cycles; c++) {
                Reactor.Cycle cycle = Reactor.cycle(fuel, rng);
                int last = cycle.power();
                Deque<Integer> onItsWay = new ArrayDeque<>();
                while (!cycle.over()) {
                    int power = rule.choose(cycle.power(), cycle.power() - last, onItsWay.stream().mapToInt(Integer::intValue).sum(), fuel);
                    last = cycle.power();
                    cycle.act(power, rng);
                    onItsWay.addLast(power - 1);
                    if (onItsWay.size() > 1) {
                        onItsWay.removeFirst();
                    }
                }
                held += cycle.held() ? 1 : 0;
            }
            worst = Math.min(worst, held / (double) cycles);
        }
        return worst;
    }

    private static int toward(double wanted) {
        return wanted > 0.5 ? 2 : wanted < -0.5 ? 0 : 1;
    }

    @Test
    void noRuleHoldsIt() {
        record Named(String name, Rule rule) {
        }
        Named[] rules = {
                new Named("back to the band", (power, moved, way, fuel) -> power < 3 ? 2 : power > 4 ? 0 : 1),
                new Named("against its speed, gain 0.5", (power, moved, way, fuel) -> toward(3.5 - power - 0.5 * moved - way)),
                new Named("against its speed, gain 1", (power, moved, way, fuel) -> toward(3.5 - power - moved - way)),
                new Named("against its speed, gain 2", (power, moved, way, fuel) -> toward(3.5 - power - 2 * moved - way)),
                // knows the fuel, and where the power will be two looks on at the speed it has, what is on its way and the fuel's mean push
                new Named("two looks ahead, knowing the fuel", (power, moved, way, fuel) -> {
                    double ahead = power + 2 * (moved + way) + 2.0 * BY[fuel] / EVERY[fuel];
                    return ahead > 4 ? 0 : ahead < 3 ? 2 : 1;
                }),
        };
        for (Named r : rules) {
            double held = worst(r.rule());
            System.out.printf(Locale.ROOT, "%-34s holds %.3f of its worst fuel's cycles%n", r.name(), held);
            assertTrue(held < 0.1, r.name() + " holds " + held);
        }
    }

    @Test
    void rodsAnswerLate() {
        for (int seed = 0; seed < 50; seed++) {
            Reactor.Cycle still = Reactor.cycle(1, new Random(seed)), moved = Reactor.cycle(1, new Random(seed));
            Random a = new Random(seed), b = new Random(seed);
            still.act(1, a);
            moved.act(2, b);
            // the look they are moved at, nothing changes
            assertEquals(still.power(), moved.power());
            still.act(1, a);
            moved.act(1, b);
            // and from the next they climb a step a look faster
            assertEquals(still.power() + 1, moved.power());
        }
    }
}
