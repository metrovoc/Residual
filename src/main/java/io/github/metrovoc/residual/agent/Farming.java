package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Dataset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Walks the farmland around home plot by plot. At each plot its model reads the plot's state, the way the field test
 * writes it ({@code 3w7h.p5}: growth 0 to 7, or {@code .} for bare), and answers harvest, wait or plant.
 */
final class Farming extends Work {
    static final int RADIUS = 7;
    /** The order it walks the rows in: by z, then x, snaking. */
    static final Comparator<BlockPos> ROWS = (p, q) -> p.getZ() != q.getZ() ? Integer.compare(p.getZ(), q.getZ())
            : Math.floorMod(p.getZ(), 2) == 0 ? Integer.compare(p.getX(), q.getX()) : Integer.compare(q.getX(), p.getX());
    /** Plots remembered in the prompt, as the field test shows them. */
    private static final int HISTORY = (Benchmark.REACTING.length - 1) / 2;

    private final List<BlockPos> plots = new ArrayList<>();
    private final StringBuilder seen = new StringBuilder();
    private int next;
    private int rescan;
    private BlockPos plot;
    private boolean unloading;
    /** It is unloading at the end of a pass, which ends the round. */
    private boolean ending;

    Farming(Agent a) {
        super(a);
    }

    /** A plot's state as the field test writes it, or 0 if {@code farmland} is no plot. */
    static char state(ServerLevel level, BlockPos farmland) {
        if (!(level.getBlockState(farmland).getBlock() instanceof FarmlandBlock)) {
            return 0;
        }
        BlockState crop = level.getBlockState(farmland.above());
        if (crop.isAir()) {
            return Dataset.BARE;
        }
        if (crop.getBlock() instanceof CropBlock c) {
            return c.isMaxAge(crop) ? Dataset.RIPE : (char) ('0' + Math.min(6, c.getAge(crop) * 7 / c.getMaxAge()));
        }
        return 0;
    }

    @Override
    void tick(ServerLevel level) {
        if (rescan-- <= 0) {
            plots.clear();
            plots.addAll(plots(level, a.home()));
            next = Math.min(next, plots.size());
            rescan = 400;
        }
        if (unloading || a.full()) {
            unload(level);
            return;
        }
        if (plots.isEmpty()) {
            fail("residual.agent.status.no_fields", RADIUS);
            a.pause(100);
            return;
        }
        if (plot == null) {
            if (next >= plots.size()) {
                next = 0;
                seen.setLength(0);
                if (produce() >= 16) {
                    unloading = true;
                    ending = true;
                } else {
                    finish();
                }
                return;
            }
            plot = plots.get(next++);
        }
        char state = state(level, plot);
        if (state == 0) {
            plot = null;
            return;
        }
        switch (a.walk(plot.above(), 1.6)) {
            case ON_THE_WAY -> {
                a.status("residual.agent.status.farming", plots.size());
                return;
            }
            case LOST -> {
                plot = null;
                return;
            }
            case HERE -> {
            }
        }
        BlockPos at = plot;
        String expected = String.valueOf(Dataset.decide(state));
        a.ask(seen.toString() + state, 1, '\n', expected, answer -> act(level, at, state, answer));
    }

    private void act(ServerLevel level, BlockPos at, char state, String answer) {
        char said = answer.isEmpty() ? '?' : answer.charAt(0);
        remember(state);
        String key = switch (said) {
            case Dataset.HARVEST -> "harvest";
            case Dataset.WAIT -> "wait";
            case Dataset.PLANT -> "plant";
            default -> "unknown";
        };
        a.show(a.decided(Component.translatable("residual.agent.said." + key)));
        a.pause(8);
        if (state(level, at) != state) {
            plot = null;
            return;
        }
        if (said == Dataset.HARVEST && state != Dataset.BARE) {
            reap(level, a, at.above());
            // the plot is bare now: ask about it again, as the field test would go on
            return;
        }
        if (said == Dataset.PLANT && state == Dataset.BARE && !sow(level, a, at.above(), Farming::seed)) {
            a.status("residual.agent.status.no_seeds");
        }
        plot = null;
    }

    /** Breaks the crop at {@code crop} and keeps what it drops. */
    static void reap(ServerLevel level, Agent a, BlockPos crop) {
        BlockState cropState = level.getBlockState(crop);
        for (ItemStack drop : Block.getDrops(cropState, level, crop, null, a, ItemStack.EMPTY)) {
            ItemStack rest = a.keep(drop);
            if (!rest.isEmpty()) {
                Block.popResource(level, crop, rest);
            }
        }
        level.destroyBlock(crop, false, a);
        a.handle(crop);
    }

    /** Plants at {@code pos} the first carried seed that {@code which} allows; false if it carries none. */
    static boolean sow(ServerLevel level, Agent a, BlockPos pos, Predicate<ItemStack> which) {
        for (int i = 0; i < Agent.CARRY; i++) {
            ItemStack s = a.items.getItem(i);
            if (seed(s) && which.test(s)) {
                BlockState crop = ((BlockItem) s.getItem()).getBlock().defaultBlockState();
                if (crop.canSurvive(level, pos)) {
                    level.setBlockAndUpdate(pos, crop);
                    s.shrink(1);
                    a.items.setChanged();
                    a.handle(pos);
                }
                return true;
            }
        }
        return false;
    }

    private void remember(char state) {
        seen.append(state).append(Dataset.decide(state));
        if (seen.length() > 2 * HISTORY) {
            seen.delete(0, seen.length() - 2 * HISTORY);
        }
    }

    /** Items carried that are not for planting. */
    private int produce() {
        int n = 0;
        for (int i = 0; i < Agent.CARRY; i++) {
            ItemStack s = a.items.getItem(i);
            if (!seed(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Something that grows on farmland: wheat seeds, carrots, melon seeds. */
    static boolean seed(ItemStack s) {
        return s.getItem() instanceof BlockItem bi && (bi.getBlock() instanceof CropBlock || bi.getBlock() instanceof StemBlock);
    }

    /** Takes the harvest to the chest at home, keeping one stack of each kind of seed. */
    private void unload(ServerLevel level) {
        BlockPos chest = Stores.homeChest(level, a.home());
        if (chest == null) {
            a.status("residual.agent.status.no_home_chest");
            unloaded();
            a.pause(100);
            return;
        }
        a.status("residual.agent.status.unloading");
        switch (a.walk(chest, 2.2)) {
            case ON_THE_WAY -> {
                return;
            }
            case LOST -> {
                unloaded();
                a.pause(100);
                return;
            }
            case HERE -> {
            }
        }
        Container c = Stores.at(level, chest);
        if (c != null) {
            List<net.minecraft.world.item.Item> keptSeeds = new ArrayList<>();
            for (int i = 0; i < Agent.CARRY; i++) {
                ItemStack s = a.items.getItem(i);
                if (s.isEmpty() || seed(s) && !keptSeeds.contains(s.getItem()) && keptSeeds.add(s.getItem())) {
                    continue;
                }
                a.items.setItem(i, Stores.insert(c, s));
            }
            a.items.setChanged();
            a.handle(chest);
        }
        unloaded();
        if (a.full()) {
            a.status("residual.agent.status.home_full");
            a.pause(200);
        }
    }

    private void unloaded() {
        unloading = false;
        if (ending) {
            ending = false;
            finish();
        }
    }

    /** The farmland within {@link #RADIUS} of {@code home}, in the order it walks the rows. */
    static List<BlockPos> plots(ServerLevel level, BlockPos home) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(home.offset(-RADIUS, -2, -RADIUS), home.offset(RADIUS, 2, RADIUS))) {
            if (level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof FarmlandBlock) {
                out.add(p.immutable());
            }
        }
        out.sort(ROWS);
        return out;
    }
}
