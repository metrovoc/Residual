package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.learn.Dataset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Empties the barrels around home into the chests around home. Chests are numbered 0 to 9, nearest first. For a kind
 * it has seen before, its model is shown where eight kinds went, the way the storage test writes it
 * ({@code c4a7f1…|f}), and answers which chest; a new kind goes to the emptiest chest.
 */
final class Storing extends Work {
    static final int RADIUS = 8;

    private final Random rng = new Random();
    /** The chest the model chose for what it carries, or -1 while it has not chosen. */
    private int chosen = -1;
    private List<BlockPos> chests = List.of();

    Storing(Agent a) {
        super(a);
    }

    @Override
    void tick(ServerLevel level) {
        chests = Stores.near(level, a.home(), RADIUS, Stores.CHESTS);
        if (chests.size() > Dataset.CHESTS.length()) {
            chests = chests.subList(0, Dataset.CHESTS.length());
        }
        if (chests.isEmpty()) {
            fail("residual.agent.status.no_chests", RADIUS);
            a.pause(100);
            return;
        }
        int slot = carried(a);
        if (slot < 0) {
            // the barrels are empty: that is a round
            if (!collect(a, level)) {
                finish("residual.program.barrels_empty");
            }
            return;
        }
        ItemStack stack = a.items.getItem(slot);
        if (chosen < 0) {
            Map<Item, Integer> where = ledger(level);
            Integer known = where.get(stack.getItem());
            if (known == null) {
                chosen = emptiest(level);
                a.show(Component.translatable("residual.agent.said.new_kind", chosen));
                return;
            }
            a.ask(prompt(where, stack.getItem()), 1, '\n', String.valueOf(Dataset.CHESTS.charAt(known)), answer -> {
                int i = answer.isEmpty() ? -1 : Dataset.CHESTS.indexOf(answer.charAt(0));
                if (i < 0 || i >= chests.size()) {
                    a.show(a.decided(Component.translatable("residual.agent.said.no_chest", answer)));
                    a.pause(40);
                    return;
                }
                chosen = i;
                a.show(a.decided(Component.translatable("residual.agent.said.chest", i)));
            });
            return;
        }
        int number = Math.min(chosen, chests.size() - 1);
        BlockPos target = chests.get(number);
        a.status("residual.agent.status.storing", chests.size());
        switch (a.walk(target, 2.2)) {
            case ON_THE_WAY -> {
                return;
            }
            case LOST -> {
                chosen = -1;
                a.pause(60);
                return;
            }
            case HERE -> {
            }
        }
        Container c = Stores.at(level, target);
        if (c != null) {
            int count = stack.getCount();
            String name = Things.name(stack);
            ItemStack rest = Stores.insert(c, stack);
            a.items.setItem(slot, rest);
            a.handle(target);
            if (rest.getCount() < count) {
                a.trace(Trace.Kind.DONE, Program.Verb.STORE.word, "residual.program.stored",
                        List.of(String.valueOf(count - rest.getCount()), name, String.valueOf(number)));
            }
            if (!rest.isEmpty()) {
                a.status("residual.agent.status.chest_full", chosen);
                a.pause(60);
            }
        }
        chosen = -1;
    }

    /** The first slot {@code a} carries something in, or -1. */
    static int carried(Agent a) {
        for (int i = 0; i < Agent.CARRY; i++) {
            if (!a.items.getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /** Takes one stack out of the barrels around home; false once they are empty. */
    static boolean collect(Agent a, ServerLevel level) {
        for (BlockPos barrel : Stores.near(level, a.home(), RADIUS, Stores.BARRELS)) {
            Container c = Stores.at(level, barrel);
            if (c == null || c.isEmpty()) {
                continue;
            }
            a.status("residual.agent.status.collecting");
            switch (a.walk(barrel, 2.2)) {
                case ON_THE_WAY -> {
                    return true;
                }
                case LOST -> {
                    a.pause(60);
                    return true;
                }
                case HERE -> {
                }
            }
            ItemStack got = Stores.take(c, s -> true, 64);
            a.keep(got);
            a.handle(barrel);
            return true;
        }
        a.status("residual.agent.status.nothing_to_store");
        a.pause(60);
        return false;
    }

    /** Which chest holds the most of each kind. */
    private Map<Item, Integer> ledger(ServerLevel level) {
        Map<Item, Integer> where = new HashMap<>();
        Map<Item, Integer> most = new HashMap<>();
        for (int i = 0; i < chests.size(); i++) {
            Container c = Stores.at(level, chests.get(i));
            if (c == null) {
                continue;
            }
            Map<Item, Integer> counts = new HashMap<>();
            for (int s = 0; s < c.getContainerSize(); s++) {
                ItemStack in = c.getItem(s);
                if (!in.isEmpty()) {
                    counts.merge(in.getItem(), in.getCount(), Integer::sum);
                }
            }
            int chest = i;
            counts.forEach((item, n) -> {
                if (n > most.getOrDefault(item, 0)) {
                    most.put(item, n);
                    where.put(item, chest);
                }
            });
        }
        return where;
    }

    /** Eight kinds and their chests under random letters, the asked kind among them, then {@code |} and its letter. */
    private String prompt(Map<Item, Integer> where, Item asked) {
        List<Item> kinds = new ArrayList<>(where.keySet());
        kinds.remove(asked);
        java.util.Collections.shuffle(kinds, rng);
        List<int[]> pairs = new ArrayList<>();
        pairs.add(new int[]{0, where.get(asked)});
        for (int i = 0; i < Dataset.PAIRS - 1; i++) {
            // with fewer kinds than pairs, made-up kinds in random chests fill the question out
            pairs.add(new int[]{i + 1, i < kinds.size() ? where.get(kinds.get(i)) : rng.nextInt(chests.size())});
        }
        java.util.Collections.shuffle(pairs, rng);
        int[] letters = drawLetters();
        StringBuilder sb = new StringBuilder();
        for (int[] p : pairs) {
            sb.append(Dataset.GOODS.charAt(letters[p[0]])).append(Dataset.CHESTS.charAt(p[1]));
        }
        return sb.append('|').append(Dataset.GOODS.charAt(letters[0])).toString();
    }

    private int[] drawLetters() {
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < Dataset.GOODS.length(); i++) {
            pool.add(i);
        }
        java.util.Collections.shuffle(pool, rng);
        int[] out = new int[Dataset.PAIRS];
        for (int i = 0; i < out.length; i++) {
            out[i] = pool.get(i);
        }
        return out;
    }

    /** The chest with the most empty slots. */
    private int emptiest(ServerLevel level) {
        int best = 0, bestFree = -1;
        for (int i = 0; i < chests.size(); i++) {
            Container c = Stores.at(level, chests.get(i));
            int free = 0;
            if (c != null) {
                for (int s = 0; s < c.getContainerSize(); s++) {
                    free += c.getItem(s).isEmpty() ? 1 : 0;
                }
            }
            if (free > bestFree) {
                bestFree = free;
                best = i;
            }
        }
        return best;
    }
}
