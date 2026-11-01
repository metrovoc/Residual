package io.github.metrovoc.residual.storage;

import java.util.function.ObjLongConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * What a Port does with the tank in front of it, as it does with an inventory: {@link #RATE} millibuckets a tick in
 * import, export, stock or window. A filled bucket in its filter names that fluid; a filter that names no fluid at all
 * and holds something else lets none through.
 */
final class PortFluids {
    /** Millibuckets a Port moves a tick: 8 buckets, 160 a second. */
    static final int RATE = 8_000;

    private final PortBlockEntity port;
    @Nullable
    private BlockCapabilityCache<ResourceHandler<FluidResource>, Direction> cache;
    private int movedThisSecond;
    private int movedLastSecond;
    private int sinceFlow;
    private boolean flowIn;
    private FluidResource flowFluid = FluidResource.EMPTY;
    private BlockPos flowTo;
    private boolean flowFull;

    PortFluids(PortBlockEntity port) {
        this.port = port;
    }

    /** The tank in front of it, or null. */
    @Nullable
    ResourceHandler<FluidResource> target() {
        if (!(port.getLevel() instanceof ServerLevel server)) {
            return null;
        }
        Direction front = port.getBlockState().getValue(PortBlock.FACING);
        BlockPos at = port.getBlockPos().relative(front);
        if (cache == null || !cache.pos().equals(at) || cache.context() != front.getOpposite()) {
            cache = BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, server, at, front.getOpposite());
        }
        return cache.getCapability();
    }

    /** Buckets it moved in the last whole second. */
    int movedLastSecond() {
        return movedLastSecond;
    }

    void nextSecond() {
        movedLastSecond = movedThisSecond / (int) Drive.BUCKET;
        movedThisSecond = 0;
    }

    void tick(ServerLevel level, StorageNetwork net, PortMode mode) {
        ResourceHandler<FluidResource> tank = target();
        if (tank == null) {
            return;
        }
        int allowed = net.allowance(port.getBlockPos(), RATE / DockBlockEntity.FLUID_UNIT) * DockBlockEntity.FLUID_UNIT;
        int budget = allowed;
        switch (mode) {
            case IMPORT -> {
                budget -= take(tank, net, budget, FluidResource.EMPTY);
                flowIn = true;
            }
            case EXPORT -> {
                for (int i = 0; i < PortBlockEntity.FILTERS && budget > 0; i++) {
                    FluidResource named = named(port.filterEntry(i));
                    budget -= named.isEmpty() ? 0 : give(tank, net, named, budget);
                }
                flowIn = false;
            }
            case STOCK -> {
                for (int i = 0; i < PortBlockEntity.FILTERS && budget > 0; i++) {
                    FluidResource named = named(port.filterEntry(i));
                    if (!named.isEmpty()) {
                        long have = held(tank, named), want = port.stockAmount() * Drive.BUCKET;
                        if (have < want) {
                            budget -= give(tank, net, named, (int) Math.min(budget, want - have));
                            flowIn = false;
                        } else if (have > want) {
                            budget -= take(tank, net, (int) Math.min(budget, have - want), named);
                            flowIn = true;
                        }
                    }
                }
            }
            default -> {
            }
        }
        int moved = allowed - budget;
        BlockPos pos = port.getBlockPos();
        net.carry(pos, moved / DockBlockEntity.FLUID_UNIT);
        movedThisSecond += moved;
        sinceFlow += moved;
        if (moved > 0) {
            flowFluid = net.lastFluid;
            flowTo = net.lastStore;
            flowFull = allowed < RATE;
        }
        if (sinceFlow > 0 && level.getGameTime() % Flows.EVERY == Math.floorMod(pos.asLong(), Flows.EVERY)) {
            Flows.send(level, pos, flowTo, flowIn, sinceFlow / DockBlockEntity.FLUID_UNIT, ItemResource.EMPTY, flowFluid, flowFull);
            sinceFlow = 0;
        }
    }

    /** The fluid a filter entry names: what the bucket or tank there holds. */
    private static FluidResource named(ItemStack entry) {
        FluidStack inside = FluidUtil.getFirstStackContained(entry);
        return inside.isEmpty() ? FluidResource.EMPTY : FluidResource.of(inside);
    }

    /** Whether its filter lets {@code fluid} through: anything when the filter is empty. */
    private boolean allows(FluidResource fluid) {
        boolean any = false;
        for (int i = 0; i < PortBlockEntity.FILTERS; i++) {
            ItemStack entry = port.filterEntry(i);
            if (!entry.isEmpty()) {
                any = true;
                if (named(entry).equals(fluid)) {
                    return true;
                }
            }
        }
        return !any;
    }

    private static long held(ResourceHandler<FluidResource> tank, FluidResource fluid) {
        long n = 0;
        for (int i = 0; i < tank.size(); i++) {
            if (tank.getResource(i).equals(fluid)) {
                n += tank.getAmountAsLong(i);
            }
        }
        return n;
    }

    /** Moves up to {@code most} millibuckets the filter allows (or only {@code only}) from the tank into the network. */
    private int take(ResourceHandler<FluidResource> tank, StorageNetwork net, int most, FluidResource only) {
        int moved = 0;
        for (int i = 0; i < tank.size() && moved < most; i++) {
            FluidResource fluid = tank.getResource(i);
            if (fluid.isEmpty() || (only.isEmpty() ? !allows(fluid) : !fluid.equals(only))) {
                continue;
            }
            int want = (int) Math.min(most - moved, tank.getAmountAsLong(i));
            int fits = (int) net.insert(fluid, want, true);
            if (fits <= 0) {
                continue;
            }
            int got;
            try (Transaction tx = Transaction.openRoot()) {
                got = tank.extract(i, fluid, fits, tx);
                tx.commit();
            }
            moved += (int) net.insert(fluid, got, false);
        }
        return moved;
    }

    /** Moves up to {@code most} millibuckets of {@code fluid} from the network into the tank. */
    private static int give(ResourceHandler<FluidResource> tank, StorageNetwork net, FluidResource fluid, int most) {
        int have = (int) net.extract(fluid, most, true);
        if (have <= 0) {
            return 0;
        }
        int room;
        try (Transaction tx = Transaction.openRoot()) {
            room = tank.insert(fluid, have, tx);
        }
        if (room <= 0) {
            return 0;
        }
        int took = (int) net.extract(fluid, room, false), put;
        try (Transaction tx = Transaction.openRoot()) {
            put = tank.insert(fluid, took, tx);
            tx.commit();
        }
        if (put < took) {
            net.insert(fluid, took - put, false);
        }
        return put;
    }

    // the tank in front, as a window's store

    long amount(FluidResource fluid) {
        ResourceHandler<FluidResource> tank = target();
        return tank == null ? 0 : held(tank, fluid);
    }

    long insert(FluidResource fluid, long n, boolean simulate) {
        ResourceHandler<FluidResource> tank = target();
        if (tank == null || !allows(fluid)) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int put = tank.insert(fluid, (int) Math.min(n, Integer.MAX_VALUE), tx);
            if (!simulate) {
                tx.commit();
                movedThisSecond += put;
            }
            return put;
        }
    }

    long extract(FluidResource fluid, long n, boolean simulate) {
        ResourceHandler<FluidResource> tank = target();
        if (tank == null) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int took = tank.extract(fluid, (int) Math.min(n, Integer.MAX_VALUE), tx);
            if (!simulate) {
                tx.commit();
                movedThisSecond += took;
            }
            return took;
        }
    }

    void forEach(ObjLongConsumer<FluidResource> each) {
        ResourceHandler<FluidResource> tank = target();
        for (int i = 0; tank != null && i < tank.size(); i++) {
            FluidResource fluid = tank.getResource(i);
            if (!fluid.isEmpty()) {
                each.accept(fluid, tank.getAmountAsLong(i));
            }
        }
    }
}
