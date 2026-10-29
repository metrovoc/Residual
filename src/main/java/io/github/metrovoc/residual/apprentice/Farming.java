package io.github.metrovoc.residual.apprentice;

import io.github.metrovoc.residual.learn.Ability;
import io.github.metrovoc.residual.learn.Drill;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Walks the farmland around home plot by plot. At each plot its mind reads the plot's state, the way the field test
 * writes it ({@code 3w7h.p5}: growth 0 to 7, or {@code .} for bare), and answers harvest, wait or plant.
 */
final class Farming extends Work {
    static final int RADIUS = 7;
    /** Plots remembered in the prompt, as the field test shows them. */
    private static final int HISTORY = (Ability.REACTING.length - 1) / 2;

    private final List<BlockPos> plots = new ArrayList<>();
    private final StringBuilder seen = new StringBuilder();
    private int next;
    private int rescan;
    private BlockPos plot;
    private boolean unloading;

    Farming(Apprentice a) {
        super(a);
    }

    /** A plot's state as the field test writes it, or 0 if {@code farmland} is no plot. */
    static char state(ServerLevel level, BlockPos farmland) {
        if (!(level.getBlockState(farmland).getBlock() instanceof FarmlandBlock)) {
            return 0;
        }
        BlockState crop = level.getBlockState(farmland.above());
        if (crop.isAir()) {
            return Drill.BARE;
        }
        if (crop.getBlock() instanceof CropBlock c) {
            return c.isMaxAge(crop) ? Drill.RIPE : (char) ('0' + Math.min(6, c.getAge(crop) * 7 / c.getMaxAge()));
        }
        return 0;
    }

    @Override
    void tick(ServerLevel level) {
        if (rescan-- <= 0) {
            scan(level);
            rescan = 400;
        }
        if (unloading || a.full()) {
            unload(level);
            return;
        }
        if (plots.isEmpty()) {
            a.status("residual.apprentice.status.no_fields", RADIUS);
            a.pause(100);
            return;
        }
        if (plot == null) {
            if (next >= plots.size()) {
                next = 0;
                seen.setLength(0);
                if (produce() >= 16) {
                    unloading = true;
                    return;
                }
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
                a.status("residual.apprentice.status.farming", plots.size());
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
        String expected = String.valueOf(Drill.decide(state));
        a.ask(seen.toString() + state, 1, '\n', expected, answer -> act(level, at, state, answer));
    }

    private void act(ServerLevel level, BlockPos at, char state, String answer) {
        char said = answer.isEmpty() ? '?' : answer.charAt(0);
        remember(state);
        String key = switch (said) {
            case Drill.HARVEST -> "harvest";
            case Drill.WAIT -> "wait";
            case Drill.PLANT -> "plant";
            default -> "unknown";
        };
        a.show(a.decided(Component.translatable("residual.apprentice.said." + key)));
        a.pause(8);
        if (state(level, at) != state) {
            plot = null;
            return;
        }
        if (said == Drill.HARVEST && state != Drill.BARE) {
            BlockPos crop = at.above();
            BlockState cropState = level.getBlockState(crop);
            for (ItemStack drop : Block.getDrops(cropState, level, crop, null, a, ItemStack.EMPTY)) {
                ItemStack rest = a.keep(drop);
                if (!rest.isEmpty()) {
                    Block.popResource(level, crop, rest);
                }
            }
            level.destroyBlock(crop, false, a);
            a.handle(crop);
            // the plot is bare now: ask about it again, as the field test would go on
            return;
        }
        if (said == Drill.PLANT && state == Drill.BARE) {
            plant(level, at.above());
        }
        plot = null;
    }

    private void remember(char state) {
        seen.append(state).append(Drill.decide(state));
        if (seen.length() > 2 * HISTORY) {
            seen.delete(0, seen.length() - 2 * HISTORY);
        }
    }

    private void plant(ServerLevel level, BlockPos pos) {
        for (int i = 0; i < Apprentice.CARRY; i++) {
            ItemStack s = a.items.getItem(i);
            if (s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock) {
                BlockState crop = bi.getBlock().defaultBlockState();
                if (crop.canSurvive(level, pos)) {
                    level.setBlockAndUpdate(pos, crop);
                    s.shrink(1);
                    a.items.setChanged();
                    a.handle(pos);
                }
                return;
            }
        }
        a.status("residual.apprentice.status.no_seeds");
    }

    /** Items carried that are not for planting. */
    private int produce() {
        int n = 0;
        for (int i = 0; i < Apprentice.CARRY; i++) {
            ItemStack s = a.items.getItem(i);
            if (!seed(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static boolean seed(ItemStack s) {
        return s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock;
    }

    /** Takes the harvest to the chest at home, keeping one stack of each kind of seed. */
    private void unload(ServerLevel level) {
        BlockPos chest = Stores.homeChest(level, a.home());
        if (chest == null) {
            a.status("residual.apprentice.status.no_home_chest");
            unloading = false;
            a.pause(100);
            return;
        }
        a.status("residual.apprentice.status.unloading");
        switch (a.walk(chest, 2.2)) {
            case ON_THE_WAY -> {
                return;
            }
            case LOST -> {
                unloading = false;
                a.pause(100);
                return;
            }
            case HERE -> {
            }
        }
        Container c = Stores.at(level, chest);
        if (c != null) {
            List<net.minecraft.world.item.Item> keptSeeds = new ArrayList<>();
            for (int i = 0; i < Apprentice.CARRY; i++) {
                ItemStack s = a.items.getItem(i);
                if (s.isEmpty() || seed(s) && !keptSeeds.contains(s.getItem()) && keptSeeds.add(s.getItem())) {
                    continue;
                }
                a.items.setItem(i, Stores.insert(c, s));
            }
            a.items.setChanged();
            a.handle(chest);
        }
        unloading = false;
        if (a.full()) {
            a.status("residual.apprentice.status.home_full");
            a.pause(200);
        }
    }

    private void scan(ServerLevel level) {
        plots.clear();
        BlockPos home = a.home();
        for (BlockPos p : BlockPos.betweenClosed(home.offset(-RADIUS, -2, -RADIUS), home.offset(RADIUS, 2, RADIUS))) {
            if (level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof FarmlandBlock) {
                plots.add(p.immutable());
            }
        }
        // walk the rows: by z, then x, snaking
        plots.sort((p, q) -> p.getZ() != q.getZ() ? Integer.compare(p.getZ(), q.getZ())
                : Math.floorMod(p.getZ(), 2) == 0 ? Integer.compare(p.getX(), q.getX()) : Integer.compare(q.getX(), p.getX()));
        next = Math.min(next, plots.size());
    }
}
