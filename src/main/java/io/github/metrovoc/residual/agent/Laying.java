package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.net.GhostSync;
import io.github.metrovoc.residual.storage.StorageNetwork;
import io.github.metrovoc.residual.storage.TerminalBlockEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * The Architect's hands: lays blocks from what it carries, fetched from the chests and barrels around home or from a
 * storage network through a terminal there, and asks its model which slice of a build comes next.
 */
abstract class Laying extends Work {
    /** Blocks it lays a tick once it stands by a slice. */
    static final int PER_TICK = 4;
    /** How near a slice's foot it stands to lay it. */
    static final double REACH = 4.5;
    /** Ticks between two showings of the places it will still lay blocks in. */
    static final int GHOST_EVERY = 40;
    /** Below this it stops rather than act on its model's answer, as sorting sets aside what it is unsure of. */
    static final float SURE = 0.6f;
    /** Every place laid this round, so neighbours are told once the whole build stands. */
    final List<BlockPos> placed = new ArrayList<>();
    /** When it last showed players where it will lay blocks. */
    private int ghostAt = -GHOST_EVERY;
    @Nullable
    private BlockPos fetchingFrom;
    @Nullable
    private Item fetching;

    Laying(Agent a) {
        super(a);
    }

    /** What laying {@code wanted} still takes, by item: places that hold their block or something else take nothing. */
    static Map<Item, Integer> needs(ServerLevel level, Map<BlockPos, BlockState> wanted) {
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : wanted.entrySet()) {
            BlockState s = e.getValue();
            if (!s.isAir() && level.getBlockState(e.getKey()).canBeReplaced()) {
                need.merge(s.getBlock().asItem(), Slices.cost(s), Integer::sum);
            }
        }
        return need;
    }

    /**
     * Lays what {@code wanted} still lacks, standing by {@code foot}: fetches what it doesn't carry first, along with
     * what the slices after it will take ({@code ahead}) while its hands have room. True once each place holds its
     * block, or something it won't build over.
     */
    boolean lay(ServerLevel level, Map<BlockPos, BlockState> wanted, BlockPos foot, Map<Item, Integer> ahead) {
        List<Map.Entry<BlockPos, BlockState>> todo = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> e : wanted.entrySet()) {
            if (!e.getValue().isAir() && level.getBlockState(e.getKey()).canBeReplaced()) {
                todo.add(e);
            }
        }
        if (todo.isEmpty()) {
            return true;
        }
        List<Map.Entry<BlockPos, BlockState>> ready = todo.stream().filter(e -> carried(e.getValue().getBlock().asItem()) >= Slices.cost(e.getValue())).toList();
        if (ready.isEmpty()) {
            Map<Item, Integer> need = needs(level, wanted);
            Item blocking = need.entrySet().stream().filter(e -> carried(e.getKey()) < e.getValue()).map(Map.Entry::getKey).findFirst().orElseThrow();
            Map<Item, Integer> lack = new LinkedHashMap<>();
            need.forEach((item, n) -> lack.merge(item, n, Integer::sum));
            ahead.forEach((item, n) -> lack.merge(item, n, Integer::sum));
            lack.replaceAll((item, n) -> n - carried(item));
            lack.values().removeIf(n -> n <= 0);
            fetch(level, blocking, lack, need.keySet());
            return false;
        }
        switch (a.walk(foot, REACH)) {
            case ON_THE_WAY -> {
                return false;
            }
            case LOST -> {
                a.pause(60);
                return false;
            }
            case HERE -> {
            }
        }
        for (Map.Entry<BlockPos, BlockState> e : ready.subList(0, Math.min(PER_TICK, ready.size()))) {
            BlockState s = e.getValue();
            if (use(s.getBlock().asItem(), Slices.cost(s))) {
                // no neighbour told yet: a torch laid before its wall would fall
                level.setBlock(e.getKey(), s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                placed.add(e.getKey().immutable());
            }
        }
        BlockState first = ready.getFirst().getValue();
        level.playSound(null, ready.getFirst().getKey(), first.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8f, 1f);
        a.swing(InteractionHand.MAIN_HAND);
        a.pause(2);
        return false;
    }

    /** Shows players near it where it will still lay blocks, every two seconds, and nothing once the build stands. */
    void ghost(List<BlockPos> places) {
        if (!places.isEmpty() && a.tickCount - ghostAt < GHOST_EVERY) {
            return;
        }
        ghostAt = a.tickCount;
        PacketDistributor.sendToPlayersTrackingEntity(a, new GhostSync(a.getId(), places.stream().mapToLong(BlockPos::asLong).toArray()));
    }

    /** The places of {@code wanted} still to lay. */
    static List<BlockPos> left(ServerLevel level, Map<BlockPos, BlockState> wanted) {
        List<BlockPos> out = new ArrayList<>();
        wanted.forEach((p, s) -> {
            if (!s.isAir() && level.getBlockState(p).canBeReplaced()) {
                out.add(p);
            }
        });
        return out;
    }

    /** Tells the neighbours of every place laid this round, now that the whole build stands. */
    void settle(ServerLevel level) {
        for (BlockPos p : placed) {
            tell(level, p, Blocks.AIR.defaultBlockState());
        }
        placed.clear();
    }

    /** What setting a block normally does after: its neighbours reshape (a torch with no wall falls) and update. */
    static void tell(ServerLevel level, BlockPos p, BlockState before) {
        level.getBlockState(p).updateNeighbourShapes(level, p, Block.UPDATE_ALL);
        level.updateNeighboursOnBlockSet(p, before);
    }

    private int carried(Item item) {
        int n = 0;
        for (int i = 0; i < Agent.CARRY; i++) {
            if (a.items.getItem(i).is(item)) {
                n += a.items.getItem(i).getCount();
            }
        }
        return n;
    }

    private boolean use(Item item, int count) {
        if (count == 0) {
            return true;
        }
        if (carried(item) < count) {
            return false;
        }
        for (int i = 0; i < Agent.CARRY && count > 0; i++) {
            ItemStack s = a.items.getItem(i);
            if (s.is(item)) {
                int n = Math.min(count, s.getCount());
                s.shrink(n);
                count -= n;
            }
        }
        a.items.setChanged();
        return true;
    }

    /**
     * Fetches from around home: walks to a store holding {@code item}, then takes what it has of everything in
     * {@code lack}, as much as its hands hold. Hands full of what later slices take put that back for what {@code now}
     * takes.
     */
    private void fetch(ServerLevel level, Item item, Map<Item, Integer> lack, Set<Item> now) {
        if (fetchingFrom == null || fetching != item) {
            fetching = item;
            fetchingFrom = source(level, item);
            if (fetchingFrom == null) {
                fail("residual.agent.status.needs_block", item.getName(item.getDefaultInstance()).getString());
                a.pause(100);
                return;
            }
        }
        a.status("residual.agent.status.fetching_block", item.getName(item.getDefaultInstance()).getString());
        switch (a.walk(fetchingFrom, 2.2)) {
            case ON_THE_WAY -> {
                return;
            }
            case LOST -> {
                fetchingFrom = null;
                a.pause(60);
                return;
            }
            case HERE -> {
            }
        }
        if (a.full()) {
            putBack(level, fetchingFrom, now);
        }
        int got = 0;
        for (Map.Entry<Item, Integer> e : lack.entrySet()) {
            got += take(level, fetchingFrom, e.getKey(), e.getValue());
        }
        a.handle(fetchingFrom);
        fetchingFrom = null;
        if (got == 0 && a.full()) {
            fail("residual.agent.status.hands_full");
            a.pause(100);
        }
    }

    /** Where around home it can get {@code item}: a chest or barrel holding some, or a terminal whose network does. */
    @Nullable
    private BlockPos source(ServerLevel level, Item item) {
        for (BlockPos p : Stores.near(level, a.home(), Stores.AROUND, Stores.ANY)) {
            Container c = Stores.at(level, p);
            if (c != null && Stores.has(c, s -> s.is(item))) {
                return p;
            }
        }
        for (BlockPos p : Stores.near(level, a.home(), Stores.AROUND, be -> be instanceof TerminalBlockEntity)) {
            if (StorageNetwork.at(level, p).count(ItemResource.of(item)) > 0) {
                return p;
            }
        }
        return null;
    }

    /** Puts everything but {@code keep} into the store at {@code p}, as far as it has room. */
    private void putBack(ServerLevel level, BlockPos p, Set<Item> keep) {
        Container c = Stores.at(level, p);
        StorageNetwork net = c == null ? StorageNetwork.at(level, p) : null;
        for (int i = 0; i < Agent.CARRY; i++) {
            ItemStack s = a.items.getItem(i);
            if (s.isEmpty() || keep.contains(s.getItem())) {
                continue;
            }
            if (c != null) {
                a.items.setItem(i, Stores.insert(c, s));
            } else {
                s.shrink((int) net.insert(ItemResource.of(s.getItem()), s.getCount(), false));
            }
        }
        a.items.setChanged();
    }

    /** Takes up to {@code count} of {@code item} from the store at {@code p}, a stack at a time while its hands have room. */
    private int take(ServerLevel level, BlockPos p, Item item, int count) {
        int got = 0;
        Container c = Stores.at(level, p);
        StorageNetwork net = c == null ? StorageNetwork.at(level, p) : null;
        while (got < count) {
            int want = Math.min(count - got, item.getDefaultMaxStackSize());
            int moved;
            if (c != null) {
                moved = Stores.fetch(c, a, s -> s.is(item), want);
            } else {
                ItemResource kind = ItemResource.of(item);
                int out = (int) net.extract(kind, want, false);
                ItemStack left = a.keep(new ItemStack(item, out));
                if (!left.isEmpty()) {
                    net.insert(kind, left.getCount(), false);
                }
                moved = out - left.getCount();
            }
            if (moved == 0) {
                break;
            }
            got += moved;
        }
        return got;
    }

    /** What its model chose: a letter's place in the alphabet it was shown, or -1, and the place the rule gives. */
    interface Choice {
        void accept(int letter, int rule);
    }

    /**
     * Asks its model which letter comes after {@code seen}, the way the Induction test asks: the rule is that what
     * followed the last letter the time before comes next now. Does nothing if there is no letter to look back to.
     */
    void ask(String seen, String alphabet, Choice then) {
        String prompt = seen.substring(Math.max(0, seen.length() - (Benchmark.INDUCTION.length - 1)));
        int last = prompt.length() - 1;
        int before = prompt.lastIndexOf(prompt.charAt(last), last - 1);
        if (before < 0) {
            fail("residual.agent.status.pattern_short");
            a.pause(100);
            return;
        }
        char rule = prompt.charAt(before + 1);
        a.ask(prompt, 1, '\n', String.valueOf(rule), answer ->
                then.accept(answer.isEmpty() ? -1 : alphabet.indexOf(answer.charAt(0)), alphabet.indexOf(rule)));
    }

    /** Whether its model's last answer is one to act on; if not, it stops and says how sure the model was. */
    boolean sure(int letter) {
        if (letter >= 0 && a.confidence() >= SURE) {
            return true;
        }
        fail("residual.agent.status.unsure", Math.round(100 * a.confidence()));
        a.pause(100);
        return false;
    }

    /** "slice 3 · 97%" over its head. */
    void decided(int slice) {
        a.show(a.decided(Component.translatable("residual.agent.said.slice", slice + 1)));
    }
}
