package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Dataset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Repeats a build along a run: slices from above the first mark towards above the second, each one block, or reaching
 * across and up to a third mark on top of the first slice. Its model is shown the slices laid so far, the same letter
 * for the same slice, the way the Induction test writes a pattern ({@code wjtabcab…}), and names the one that comes
 * next; it lays that slice again from the chests at home or a storage terminal there.
 */
final class Building extends Laying {
    private final Random rng = new Random();
    /** The laid slice to lay again next, by its place in the run, or -1 while its model has not chosen. */
    private int chosen = -1;
    /** Where it lays that slice: the first empty one when its model chose, kept until the slice is whole. */
    private int at = -1;

    Building(Agent a) {
        super(a);
    }

    @Override
    void tick(ServerLevel level) {
        List<BlockPos> m = a.marks();
        Slices run = m.size() < 2 ? null : Slices.run(m.get(0), m.get(1), m.size() > 2 ? m.get(2) : null);
        if (run == null) {
            fail("residual.agent.status.no_row");
            a.pause(100);
            return;
        }
        if (!run.fits()) {
            fail("residual.agent.status.too_big", Slices.MOST, Slices.MOST);
            a.pause(100);
            return;
        }
        if (chosen < 0 || at >= run.length()) {
            int next = 0;
            while (next < run.length() && !run.empty(level, next)) {
                next++;
            }
            if (next == run.length()) {
                settle(level);
                a.status("residual.agent.status.row_done");
                a.pause(200);
                finish();
                return;
            }
            at = next;
            choose(level, run, next);
            return;
        }
        a.status("residual.agent.status.building", at, run.length());
        Map<BlockPos, BlockState> wanted = new LinkedHashMap<>();
        List<BlockState> content = run.read(level, chosen);
        List<BlockPos> places = run.slice(at);
        for (int i = 0; i < places.size(); i++) {
            wanted.put(places.get(i), content.get(i));
        }
        // the slices to come are its model's to name, so it fetches a stack of each kind on the way
        Map<Item, Integer> ahead = new LinkedHashMap<>();
        needs(level, wanted).keySet().forEach(item -> ahead.put(item, item.getDefaultMaxStackSize()));
        if (lay(level, wanted, run.at(at, run.width() / 2, 0), ahead)) {
            chosen = -1;
        }
    }

    /** Asks its model which of the slices laid so far comes next, a letter for each kind of slice it was shown. */
    private void choose(ServerLevel level, Slices run, int next) {
        int from = Math.max(0, next - (Benchmark.INDUCTION.length - 1));
        int[] pool = Dataset.distinct(rng, Dataset.LETTERS.length(), Dataset.LETTERS.length());
        List<List<BlockState>> kinds = new ArrayList<>();
        List<Integer> firstAt = new ArrayList<>();
        StringBuilder seen = new StringBuilder(), alphabet = new StringBuilder();
        for (int k = from; k < next; k++) {
            List<BlockState> slice = run.read(level, k);
            int kind = kinds.indexOf(slice);
            if (kind < 0) {
                kind = kinds.size();
                kinds.add(slice);
                firstAt.add(k);
                alphabet.append(Dataset.LETTERS.charAt(pool[kind]));
            }
            seen.append(alphabet.charAt(kind));
        }
        if (seen.length() < 2) {
            fail("residual.agent.status.pattern_short");
            a.pause(100);
            return;
        }
        ask(seen.toString(), alphabet.toString(), (letter, rule) -> {
            if (!sure(letter)) {
                return;
            }
            chosen = firstAt.get(letter);
            if (run.width() * run.height() == 1) {
                a.show(a.decided(kinds.get(letter).getFirst().getBlock().getName()));
            } else {
                decided(chosen);
            }
        });
    }
}
