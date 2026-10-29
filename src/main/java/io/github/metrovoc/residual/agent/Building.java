package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Dataset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Carries on a row of blocks on top of the marked blocks, from the first mark towards the second. Its model is shown
 * the row so far with a letter
 * for each kind of block, the way the pattern test writes it ({@code wjtabcab…}), and names the next one; it fetches
 * that block from the chests at home if it has none, and lays it.
 */
final class Building extends Work {
    private final Random rng = new Random();
    /** The kind of block its model chose for the next place, or null while it has not chosen. */
    private BlockState chosen;
    private BlockPos fetchingFrom;

    Building(Agent a) {
        super(a);
    }

    /** The places of the row, above the first mark to above the second, or null if the marks don't make a straight row. */
    private List<BlockPos> row() {
        List<BlockPos> m = a.marks();
        if (m.size() < 2) {
            return null;
        }
        BlockPos from = m.get(0), to = m.get(1);
        if (from.getY() != to.getY() || from.getX() != to.getX() && from.getZ() != to.getZ() || from.equals(to)) {
            return null;
        }
        Direction d = Direction.getApproximateNearest(to.getX() - from.getX(), 0, to.getZ() - from.getZ());
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p = from; out.size() < 256; p = p.relative(d)) {
            out.add(p.above());
            if (p.equals(to)) {
                break;
            }
        }
        return out;
    }

    @Override
    void tick(ServerLevel level) {
        List<BlockPos> row = row();
        if (row == null) {
            fail("residual.agent.status.no_row");
            a.pause(100);
            return;
        }
        List<BlockState> laid = new ArrayList<>();
        BlockPos next = null;
        for (BlockPos p : row) {
            BlockState s = level.getBlockState(p);
            if (s.canBeReplaced()) {
                next = p;
                break;
            }
            laid.add(s);
        }
        if (next == null) {
            a.status("residual.agent.status.row_done");
            a.pause(200);
            finish();
            return;
        }
        if (chosen == null) {
            choose(laid);
            return;
        }
        if (!hasBlock(chosen.getBlock()) && !fetch(level, chosen.getBlock())) {
            return;
        }
        a.status("residual.agent.status.building", laid.size(), row.size());
        switch (a.walk(next, 3.0)) {
            case ON_THE_WAY -> {
                return;
            }
            case LOST -> {
                a.pause(60);
                return;
            }
            case HERE -> {
            }
        }
        if (useBlock(chosen.getBlock())) {
            level.setBlockAndUpdate(next, single(chosen));
            level.playSound(null, next, chosen.getSoundType().getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
            a.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
        chosen = null;
        a.pause(6);
    }

    /** Asks its model which kind of block comes next. */
    private void choose(List<BlockState> laid) {
        if (laid.size() < 2) {
            fail("residual.agent.status.pattern_short");
            a.pause(100);
            return;
        }
        List<BlockState> tail = laid.subList(Math.max(0, laid.size() - (Benchmark.INDUCTION.length - 1)), laid.size());
        Map<Block, Character> letters = new HashMap<>();
        Map<Character, BlockState> kinds = new HashMap<>();
        int[] pool = Dataset.distinct(rng, Dataset.LETTERS.length(), Dataset.LETTERS.length());
        StringBuilder prompt = new StringBuilder();
        for (BlockState s : tail) {
            char c = letters.computeIfAbsent(s.getBlock(), b -> Dataset.LETTERS.charAt(pool[letters.size()]));
            kinds.put(c, s);
            prompt.append(c);
        }
        // look back for the last block: what followed it then comes next now
        int last = prompt.length() - 1;
        int before = prompt.lastIndexOf(String.valueOf(prompt.charAt(last)), last - 1);
        if (before < 0) {
            fail("residual.agent.status.pattern_short");
            a.pause(100);
            return;
        }
        String expected = String.valueOf(prompt.charAt(before + 1));
        a.ask(prompt.toString(), 1, '\n', expected, answer -> {
            BlockState s = answer.isEmpty() ? null : kinds.get(answer.charAt(0));
            if (s == null) {
                a.show(a.decided(Component.translatable("residual.agent.said.no_block", answer)));
                a.pause(40);
                return;
            }
            chosen = s;
            a.show(a.decided(s.getBlock().getName()));
        });
    }

    /** {@code s} as one item places it: a bottom slab for a double one, one candle or pickle, a young crop. */
    static BlockState single(BlockState s) {
        BlockState plain = s.getBlock().defaultBlockState();
        for (Property<?> p : s.getProperties()) {
            // counts, ages and fill levels; a sign's or a banner's rotation is only which way it faces
            if (p instanceof IntegerProperty && p != BlockStateProperties.ROTATION_16
                    || p == BlockStateProperties.SLAB_TYPE && s.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
                s = copy(plain, s, p);
            }
        }
        return s;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> p) {
        return to.setValue(p, from.getValue(p));
    }

    private boolean hasBlock(Block block) {
        for (int i = 0; i < Agent.CARRY; i++) {
            if (a.items.getItem(i).getItem() instanceof BlockItem bi && bi.getBlock() == block) {
                return true;
            }
        }
        return false;
    }

    private boolean useBlock(Block block) {
        for (int i = 0; i < Agent.CARRY; i++) {
            ItemStack s = a.items.getItem(i);
            if (s.getItem() instanceof BlockItem bi && bi.getBlock() == block) {
                s.shrink(1);
                a.items.setChanged();
                return true;
            }
        }
        return false;
    }

    /** Fetches a stack of {@code block} from the chests at home; true once it has some. */
    private boolean fetch(ServerLevel level, Block block) {
        if (fetchingFrom == null) {
            for (BlockPos p : Stores.near(level, a.home(), Storing.RADIUS, Stores.ANY)) {
                Container c = Stores.at(level, p);
                if (c != null && Stores.has(c, s -> s.getItem() instanceof BlockItem bi && bi.getBlock() == block)) {
                    fetchingFrom = p;
                    break;
                }
            }
            if (fetchingFrom == null) {
                fail("residual.agent.status.needs_block", block.getName().getString());
                a.pause(100);
                return false;
            }
        }
        a.status("residual.agent.status.fetching_block", block.getName().getString());
        switch (a.walk(fetchingFrom, 2.2)) {
            case ON_THE_WAY -> {
                return false;
            }
            case LOST -> {
                fetchingFrom = null;
                a.pause(60);
                return false;
            }
            case HERE -> {
            }
        }
        Container c = Stores.at(level, fetchingFrom);
        if (c != null) {
            if (Stores.fetch(c, a, s -> s.getItem() instanceof BlockItem bi && bi.getBlock() == block, 64) == 0 && a.full()) {
                a.status("residual.agent.status.hands_full");
                a.pause(100);
            }
            a.handle(fetchingFrom);
        }
        fetchingFrom = null;
        return hasBlock(block);
    }
}
