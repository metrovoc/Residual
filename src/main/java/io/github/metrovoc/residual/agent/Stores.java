package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.storage.StorageNetwork;
import io.github.metrovoc.residual.storage.TerminalBlockEntity;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/** Chests, barrels and storage networks around a place: finding them, and putting things in and taking them out. */
final class Stores {
    static final Predicate<BlockEntity> CHESTS = be -> be instanceof ChestBlockEntity;
    static final Predicate<BlockEntity> BARRELS = be -> be instanceof BarrelBlockEntity;
    static final Predicate<BlockEntity> ANY = be -> be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity;
    /** How far from home it looks for stores to take from and put into. */
    static final int AROUND = Storing.RADIUS + 4;

    private Stores() {
    }

    /** Containers within {@code r} blocks of {@code center}, one per double chest, nearest first. */
    static List<BlockPos> near(ServerLevel level, BlockPos center, int r, Predicate<BlockEntity> which) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -3, -r), center.offset(r, 3, r))) {
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(p);
            if (be != null && which.test(be) && !secondHalf(be.getBlockState())) {
                out.add(p.immutable());
            }
        }
        out.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        return out;
    }

    /** The other half of a double chest, which the first half stands for. */
    private static boolean secondHalf(BlockState state) {
        return state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) == ChestType.RIGHT;
    }

    /** The container at {@code pos}, both halves of a double chest together. */
    @Nullable
    static Container at(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, level, pos, true);
        }
        return level.getBlockEntity(pos) instanceof Container c ? c : null;
    }

    /** The container nearest to {@code home}, within three blocks of it. */
    @Nullable
    static BlockPos homeChest(ServerLevel level, BlockPos home) {
        List<BlockPos> found = near(level, home, 3, ANY);
        return found.isEmpty() ? null : found.getFirst();
    }

    /** The Storage Terminals around home, one for each network, nearest first: an agent reaches a network through one. */
    static List<BlockPos> terminals(ServerLevel level, BlockPos home) {
        Set<StorageNetwork> seen = new HashSet<>();
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : near(level, home, AROUND, be -> be instanceof TerminalBlockEntity)) {
            if (seen.add(StorageNetwork.at(level, p))) {
                out.add(p);
            }
        }
        return out;
    }

    /** The nearest terminal not yet tried whose network keeps something that {@code what} matches. */
    @Nullable
    static BlockPos terminalWith(ServerLevel level, BlockPos home, Predicate<ItemStack> what, Set<BlockPos> tried) {
        for (BlockPos p : terminals(level, home)) {
            if (!tried.contains(p) && count(StorageNetwork.at(level, p), what) > 0) {
                return p;
            }
        }
        return null;
    }

    /** The nearest terminal not yet tried whose network has room for {@code stack}. */
    @Nullable
    static BlockPos terminalFor(ServerLevel level, BlockPos home, ItemStack stack, Set<BlockPos> tried) {
        for (BlockPos p : terminals(level, home)) {
            if (!tried.contains(p) && StorageNetwork.at(level, p).insert(ItemResource.of(stack), 1, true) > 0) {
                return p;
            }
        }
        return null;
    }

    /** How many of what matches {@code what} a network keeps. */
    static long count(StorageNetwork net, Predicate<ItemStack> what) {
        long n = 0;
        for (Object2LongMap.Entry<ItemResource> e : net.contents().object2LongEntrySet()) {
            if (what.test(e.getKey().toStack())) {
                n += e.getLongValue();
            }
        }
        return n;
    }

    /** Moves as many as its hands hold of one kind that matches {@code what}, up to {@code max}, from a network to {@code a}; returns how many. */
    static int fetch(StorageNetwork net, Body a, Predicate<ItemStack> what, int max) {
        for (ItemResource kind : net.contents().keySet()) {
            if (what.test(kind.toStack())) {
                int took = (int) net.extract(kind, Math.min(max, kind.getMaxStackSize()), false);
                ItemStack left = a.keep(kind.toStack(took));
                net.insert(kind, left.getCount(), false);
                return took - left.getCount();
            }
        }
        return 0;
    }

    /** Puts {@code stack} into a network; returns what didn't fit. */
    static ItemStack insert(StorageNetwork net, ItemStack stack) {
        long stored = net.insert(ItemResource.of(stack), stack.getCount(), false);
        return stack.copyWithCount(stack.getCount() - (int) stored);
    }

    /** Puts {@code stack} into {@code c}; returns what didn't fit. */
    static ItemStack insert(Container c, ItemStack stack) {
        ItemStack rest = HopperBlockEntity.addItem(null, c, stack, null);
        c.setChanged();
        return rest;
    }

    /** Takes up to {@code max} items that match {@code what} (all of one kind) out of {@code c}. */
    static ItemStack take(Container c, Predicate<ItemStack> what, int max) {
        ItemStack out = ItemStack.EMPTY;
        for (int i = 0; i < c.getContainerSize() && out.getCount() < max; i++) {
            ItemStack in = c.getItem(i);
            if (in.isEmpty() || !what.test(in) || !out.isEmpty() && !ItemStack.isSameItemSameComponents(in, out)) {
                continue;
            }
            int n = Math.min(in.getCount(), Math.min(max, in.getMaxStackSize()) - out.getCount());
            if (out.isEmpty()) {
                out = in.copyWithCount(n);
            } else {
                out.grow(n);
            }
            in.shrink(n);
        }
        c.setChanged();
        return out;
    }

    /** Moves as many as its hands hold, up to {@code max}, of what matches {@code what} from {@code c} to {@code a}; returns how many. */
    static int fetch(Container c, Body a, Predicate<ItemStack> what, int max) {
        ItemStack got = take(c, what, max);
        int count = got.getCount();
        ItemStack left = a.keep(got);
        int moved = count - left.getCount();
        insert(c, left);
        return moved;
    }

    /** How many of what matches {@code what} there are in {@code c}. */
    static int count(Container c, Predicate<ItemStack> what) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && what.test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** The nearest store around {@code home} with exactly {@code wanted}, else with a kind of it, leaving out {@code tried}. */
    @Nullable
    static BlockPos find(ServerLevel level, BlockPos home, Picker wanted, Set<BlockPos> tried) {
        BlockPos loose = null;
        for (BlockPos p : near(level, home, AROUND, ANY)) {
            Container c = tried.contains(p) ? null : at(level, p);
            if (c == null) {
                continue;
            }
            if (has(c, wanted::exactly)) {
                return p;
            }
            if (loose == null && has(c, wanted::loosely)) {
                loose = p;
            }
        }
        return loose;
    }

    /**
     * Where {@code stack} goes around {@code home}: a store that already keeps some of it, else the store at home, else
     * the nearest with room; null if none has room, leaving out {@code tried}.
     */
    @Nullable
    static BlockPos into(ServerLevel level, BlockPos home, ItemStack stack, Set<BlockPos> tried) {
        BlockPos roomy = null;
        BlockPos homeChest = homeChest(level, home);
        for (BlockPos p : near(level, home, AROUND, ANY)) {
            Container c = tried.contains(p) ? null : at(level, p);
            if (c == null || !room(c, stack)) {
                continue;
            }
            if (has(c, s -> ItemStack.isSameItemSameComponents(s, stack))) {
                return p;
            }
            if (roomy == null || p.equals(homeChest)) {
                roomy = p;
            }
        }
        return roomy;
    }

    /** Whether {@code c} has room for some of {@code stack}. */
    static boolean room(Container c, ItemStack stack) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack in = c.getItem(i);
            if (in.isEmpty() && c.canPlaceItem(i, stack)
                    || ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    static boolean has(Container c, Predicate<ItemStack> what) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (!c.getItem(i).isEmpty() && what.test(c.getItem(i))) {
                return true;
            }
        }
        return false;
    }
}
