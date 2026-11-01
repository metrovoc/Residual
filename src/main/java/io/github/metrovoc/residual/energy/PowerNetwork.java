package io.github.metrovoc.residual.energy;

import io.github.metrovoc.residual.block.RelayLinkBlockEntity;
import io.github.metrovoc.residual.world.Relays;
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
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

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
    private final List<BlockPos> links = new ArrayList<>();
    private long tickedAt = -1;
    private long drawn;

    private PowerNetwork(ServerLevel level, Set<BlockPos> blocks) {
        this.level = level;
        for (BlockPos p : blocks) {
            if (level.getBlockEntity(p) instanceof PowerBlockEntity) {
                devices.add(p);
            } else if (level.getBlockEntity(p) instanceof RelayLinkBlockEntity) {
                links.add(p);
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

    /** What generators, cells and machines a network has at the moment. */
    private record Handlers(List<EnergyHandler> generators, List<EnergyHandler> cells, List<EnergyHandler> machines) {
    }

    private Handlers handlers() {
        Handlers out = new Handlers(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (BlockPos p : devices) {
            if (level.getBlockEntity(p) instanceof PowerBlockEntity be) {
                switch (be.role()) {
                    case GENERATOR -> out.generators.add(be.energy());
                    case CELL -> out.cells.add(be.energy());
                    case MACHINE -> out.machines.add(be.energy());
                }
            }
        }
        return out;
    }

    /** Moves this tick's power, once however many of its blocks ask. */
    public void tick() {
        long now = level.getGameTime();
        if (tickedAt == now) {
            return;
        }
        tickedAt = now;
        Handlers mine = handlers();
        List<EnergyHandler> generators = mine.generators, cells = mine.cells, machines = mine.machines;
        try (Transaction tx = Transaction.openRoot()) {
            drawn = 0;
            for (EnergyHandler machine : machines) {
                for (EnergyHandler from : generators) {
                    drawn += EnergyHandlerUtil.move(from, machine, Integer.MAX_VALUE, tx);
                }
                for (EnergyHandler from : cells) {
                    drawn += EnergyHandlerUtil.move(from, machine, Integer.MAX_VALUE, tx);
                }
            }
            for (EnergyHandler from : generators) {
                for (EnergyHandler cell : cells) {
                    EnergyHandlerUtil.move(from, cell, Integer.MAX_VALUE, tx);
                }
            }
            carryOverRelays(mine, now, tx);
            tx.commit();
        }
    }

    /** Takes up to {@code amount} FE out of its cells, at once, and says how much that came to. */
    public int take(int amount) {
        int taken = 0;
        for (BlockPos p : devices) {
            if (taken < amount && level.getBlockEntity(p) instanceof PowerBlockEntity be && be.role() == PowerBlockEntity.Role.CELL) {
                int stored = be.energy().getAmountAsInt(), moved = Math.min(amount - taken, stored);
                be.energy().set(stored - moved);
                taken += moved;
            }
        }
        return taken;
    }

    /**
     * Through its Relay Links, while their network is open: what its machines still want comes from the generators and
     * then the cells of the power networks joined to it, and what its generators have left goes to their cells, as much a
     * tick as the relay network carries. Each network does this once a tick for itself, so power goes both ways.
     */
    private void carryOverRelays(Handlers mine, long now, TransactionContext tx) {
        if (links.isEmpty()) {
            return;
        }
        Relays relays = Relays.of(level.getServer());
        Set<PowerNetwork> seen = new HashSet<>();
        seen.add(this);
        for (BlockPos link : links) {
            Relays.Net relay = relays.netOf(GlobalPos.of(level.dimension(), link));
            if (relay == null || !relay.open()) {
                continue;
            }
            for (Relays.Part p : relay.parts) {
                ServerLevel there = p.kind() == Relays.Kind.LINK ? level.getServer().getLevel(p.at().dimension()) : null;
                if (there == null || !there.isLoaded(p.at().pos())) {
                    continue;
                }
                PowerNetwork far = at(there, p.at().pos());
                if (seen.add(far)) {
                    Handlers theirs = far.handlers();
                    for (EnergyHandler machine : mine.machines) {
                        drawn += carry(theirs.generators, machine, relay, now, tx);
                        drawn += carry(theirs.cells, machine, relay, now, tx);
                    }
                    for (EnergyHandler cell : theirs.cells) {
                        carry(mine.generators, cell, relay, now, tx);
                    }
                }
            }
        }
    }

    private static long carry(List<EnergyHandler> from, EnergyHandler to, Relays.Net relay, long now, TransactionContext tx) {
        long carried = 0;
        for (EnergyHandler handler : from) {
            long room = relay.feRoom(now);
            if (room > 0) {
                long moved = EnergyHandlerUtil.move(handler, to, (int) Math.min(room, Integer.MAX_VALUE), tx);
                relay.feCarried(moved);
                carried += moved;
            }
        }
        return carried;
    }

    /** Power stored in its cells, what they hold in all, how many there are, and what its machines drew last tick. */
    public long[] stored() {
        long amount = 0, capacity = 0, cells = 0;
        for (BlockPos p : devices) {
            if (level.getBlockEntity(p) instanceof PowerBlockEntity be && be.role() == PowerBlockEntity.Role.CELL) {
                amount += be.energy().getAmountAsLong();
                capacity += be.energy().getCapacityAsLong();
                cells++;
            }
        }
        return new long[] {amount, capacity, cells, drawn};
    }
}
