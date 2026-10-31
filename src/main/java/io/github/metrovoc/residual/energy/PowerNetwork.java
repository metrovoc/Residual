package io.github.metrovoc.residual.energy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Power cables and the blocks they join. Once a tick, machines take what they want from the generators, and from the
 * cells when the generators fall short; whatever the generators make beyond that charges the cells.
 */
public final class PowerNetwork {
    /** Blocks one network may hold. */
    private static final int LARGEST = 4096;
    private static final Map<ServerLevel, Map<BlockPos, PowerNetwork>> FOUND = new WeakHashMap<>();

    private final ServerLevel level;
    private final List<BlockPos> devices = new ArrayList<>();
    private long tickedAt = -1;

    private PowerNetwork(ServerLevel level, Set<BlockPos> blocks) {
        this.level = level;
        for (BlockPos p : blocks) {
            if (level.getBlockEntity(p) instanceof PowerBlockEntity) {
                devices.add(p);
            }
        }
    }

    /** A power block came, went, loaded or unloaded: networks in this level are found again when next asked for. */
    public static void changed(ServerLevel level) {
        FOUND.remove(level);
    }

    /** The network the power block at {@code pos} belongs to. */
    public static PowerNetwork at(ServerLevel level, BlockPos pos) {
        Map<BlockPos, PowerNetwork> found = FOUND.computeIfAbsent(level, l -> new HashMap<>());
        PowerNetwork known = found.get(pos);
        if (known != null) {
            return known;
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> next = new ArrayDeque<>();
        seen.add(pos.immutable());
        next.add(pos.immutable());
        while (!next.isEmpty() && seen.size() < LARGEST) {
            BlockPos at = next.poll();
            BlockState here = level.getBlockState(at);
            if (!(here.getBlock() instanceof PowerNode node)) {
                continue;
            }
            for (Direction d : Direction.values()) {
                BlockPos n = at.relative(d);
                if (seen.contains(n) || !level.isLoaded(n) || !node.joins(here, d)) {
                    continue;
                }
                BlockState there = level.getBlockState(n);
                if (there.getBlock() instanceof PowerNode other && other.joins(there, d.getOpposite())) {
                    seen.add(n);
                    next.add(n);
                }
            }
        }
        PowerNetwork network = new PowerNetwork(level, seen);
        seen.forEach(p -> found.put(p, network));
        return network;
    }

    /** Moves this tick's power, once however many of its blocks ask. */
    public void tick() {
        long now = level.getGameTime();
        if (tickedAt == now) {
            return;
        }
        tickedAt = now;
        List<EnergyHandler> generators = new ArrayList<>(), cells = new ArrayList<>(), machines = new ArrayList<>();
        for (BlockPos p : devices) {
            if (level.getBlockEntity(p) instanceof PowerBlockEntity be) {
                switch (be.role()) {
                    case GENERATOR -> generators.add(be.energy());
                    case CELL -> cells.add(be.energy());
                    case MACHINE -> machines.add(be.energy());
                }
            }
        }
        try (Transaction tx = Transaction.openRoot()) {
            for (EnergyHandler machine : machines) {
                for (EnergyHandler from : generators) {
                    EnergyHandlerUtil.move(from, machine, Integer.MAX_VALUE, tx);
                }
                for (EnergyHandler from : cells) {
                    EnergyHandlerUtil.move(from, machine, Integer.MAX_VALUE, tx);
                }
            }
            for (EnergyHandler from : generators) {
                for (EnergyHandler cell : cells) {
                    EnergyHandlerUtil.move(from, cell, Integer.MAX_VALUE, tx);
                }
            }
            tx.commit();
        }
    }

    /** Power stored in its cells, and what they hold in all. */
    public long[] stored() {
        long amount = 0, capacity = 0;
        for (BlockPos p : devices) {
            if (level.getBlockEntity(p) instanceof PowerBlockEntity be && be.role() == PowerBlockEntity.Role.CELL) {
                amount += be.energy().getAmountAsLong();
                capacity += be.energy().getCapacityAsLong();
            }
        }
        return new long[] {amount, capacity};
    }
}
