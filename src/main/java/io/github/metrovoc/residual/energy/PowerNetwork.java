package io.github.metrovoc.residual.energy;

import io.github.metrovoc.residual.block.RelayLinkBlockEntity;
import io.github.metrovoc.residual.net.CurrentSync;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Power cables and the blocks they join. Once a tick, machines take what they want from the generators, and from the
 * cells when the generators fall short; whatever the generators make beyond that charges the cells. All of it goes through
 * the cables, and in a tick a network carries no more than its thinnest cable does, so one thin cable holds back a whole
 * line. Blocks that touch need no cable and hold nothing back. Accelerators and racks are part of a network too: what
 * they spend, {@link #draw} takes from it as machines' power is taken.
 */
public final class PowerNetwork {
    /** Blocks one network may hold. */
    private static final int LARGEST = 16_384;
    /** Ticks between two showings of the current on a network that is carrying some. */
    private static final int SHOWN_EVERY = 12;
    /** How close a player has to be to see the current. */
    private static final double SEEN = 48;
    private static final Map<ServerLevel, Map<BlockPos, PowerNetwork>> FOUND = new WeakHashMap<>();

    private final ServerLevel level;
    private final List<PowerBlockEntity> generators = new ArrayList<>(), cells = new ArrayList<>(), machines = new ArrayList<>();
    private final List<BlockPos> links = new ArrayList<>();
    private final int[] genLeft, cellOut, cellIn;
    /** What its thinnest cable carries in a tick; no cable, no limit. */
    private final int carries;
    private long tickedAt = -1;
    private long left;
    private long carried;
    private long drawn;
    private int firstGen, firstCell;
    @Nullable
    private BlockPos lastSink;

    private PowerNetwork(ServerLevel level, Set<BlockPos> blocks) {
        this.level = level;
        int thinnest = Integer.MAX_VALUE;
        for (BlockPos p : blocks) {
            if (level.getBlockEntity(p) instanceof PowerBlockEntity be) {
                switch (be.role()) {
                    case GENERATOR -> generators.add(be);
                    case CELL -> cells.add(be);
                    case MACHINE -> machines.add(be);
                }
            } else if (level.getBlockEntity(p) instanceof RelayLinkBlockEntity) {
                links.add(p);
            }
            if (level.getBlockState(p).getBlock() instanceof PowerNode node) {
                thinnest = Math.min(thinnest, node.carries());
            }
        }
        carries = thinnest;
        genLeft = new int[generators.size()];
        cellOut = new int[cells.size()];
        cellIn = new int[cells.size()];
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
        left = carries;
        carried = 0;
        drawn = 0;
        firstGen = firstCell = 0;
        for (int i = 0; i < genLeft.length; i++) {
            genLeft[i] = generators.get(i).available();
        }
        for (int i = 0; i < cellOut.length; i++) {
            cellOut[i] = cells.get(i).available();
            cellIn[i] = cells.get(i).room();
        }
        for (PowerBlockEntity machine : machines) {
            int room = machine.room();
            if (room > 0) {
                int got = spend(room, true);
                if (got > 0) {
                    machine.energy().set(machine.energy().getAmountAsInt() + got);
                    lastSink = machine.getBlockPos();
                    drawn += got;
                }
            }
        }
        charge(this, Long.MAX_VALUE);
        carryOverRelays(now);
        if (carried > 0 && now % SHOWN_EVERY == 0) {
            show();
        }
    }

    /** Gives up to {@code most} of what the generators still have to the cells of {@code into}, this network's or one joined to it; what it gave. */
    private long charge(PowerNetwork into, long most) {
        long gave = 0;
        for (int c = 0; c < into.cells.size() && gave < most; c++) {
            int room = (int) Math.min(into.cellIn[c], most - gave);
            if (room > 0) {
                int got = spend(room, false);
                if (got > 0) {
                    into.cellIn[c] -= got;
                    PowerBlockEntity cell = into.cells.get(c);
                    cell.energy().set(cell.energy().getAmountAsInt() + got);
                    into.lastSink = cell.getBlockPos();
                    gave += got;
                }
                if (got < room) {
                    break;
                }
            }
        }
        return gave;
    }

    /** Takes up to {@code amount} from the generators, then the cells if it may, within what the cables can still carry this tick. */
    private int spend(int amount, boolean cellsToo) {
        int want = (int) Math.min(amount, left);
        int got = 0;
        for (; firstGen < genLeft.length && got < want; firstGen++) {
            PowerBlockEntity from = generators.get(firstGen);
            // what another mod's cable took this tick is gone too
            int t = Math.min(Math.min(want - got, genLeft[firstGen]), from.energy().getAmountAsInt());
            if (t > 0) {
                genLeft[firstGen] -= t;
                from.energy().set(from.energy().getAmountAsInt() - t);
                got += t;
            }
            if (genLeft[firstGen] > 0) {
                break;
            }
        }
        for (; cellsToo && firstCell < cellOut.length && got < want; firstCell++) {
            PowerBlockEntity from = cells.get(firstCell);
            int t = Math.min(Math.min(want - got, cellOut[firstCell]), from.energy().getAmountAsInt());
            if (t > 0) {
                cellOut[firstCell] -= t;
                from.energy().set(from.energy().getAmountAsInt() - t);
                got += t;
            }
            if (cellOut[firstCell] > 0) {
                break;
            }
        }
        left -= got;
        carried += got;
        return got;
    }

    /** Spends up to {@code amount} FE of this tick's power at {@code at}, for what keeps no power of its own; how much it got. */
    public int draw(int amount, BlockPos at) {
        tick();
        int got = spend(amount, true);
        if (got > 0) {
            lastSink = at;
            drawn += got;
        }
        return got;
    }

    /** Whether it has any power to give now. */
    public boolean powered() {
        tick();
        for (int i = firstGen; i < genLeft.length; i++) {
            if (genLeft[i] > 0) {
                return true;
            }
        }
        for (int i = firstCell; i < cellOut.length; i++) {
            if (cellOut[i] > 0) {
                return true;
            }
        }
        return false;
    }

    /** Lets players nearby see the current run from where it is made to where it last went. */
    private void show() {
        if (lastSink == null || level.players().isEmpty()) {
            return;
        }
        PowerBlockEntity from = !generators.isEmpty() ? generators.get(Math.floorMod(level.getGameTime() / SHOWN_EVERY, generators.size()))
                : !cells.isEmpty() ? cells.get(Math.floorMod(level.getGameTime() / SHOWN_EVERY, cells.size())) : null;
        if (from != null && !from.getBlockPos().equals(lastSink)) {
            BlockPos at = from.getBlockPos();
            PacketDistributor.sendToPlayersNear(level, null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, SEEN,
                    new CurrentSync(at, lastSink, (int) Math.min(Integer.MAX_VALUE, carried), false));
        }
    }

    /** What it carried last tick. */
    public long carried() {
        return carried;
    }

    /** What its thinnest cable carries in a tick. */
    public int carries() {
        return carries;
    }

    /** Takes up to {@code amount} FE out of its cells, at once, and says how much that came to. */
    public int take(int amount) {
        int taken = 0;
        for (PowerBlockEntity be : cells) {
            if (taken < amount) {
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
    private void carryOverRelays(long now) {
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
                    far.tick();
                    for (PowerBlockEntity machine : machines) {
                        int got = far.spend((int) Math.min(Math.min(machine.room(), relay.feRoom(now)), left), true);
                        if (got > 0) {
                            machine.energy().set(machine.energy().getAmountAsInt() + got);
                            relay.feCarried(got);
                            left -= got;
                            carried += got;
                            drawn += got;
                        }
                    }
                    relay.feCarried(charge(far, relay.feRoom(now)));
                }
            }
        }
    }

    /** Power stored in its cells, what they hold in all, how many there are, and what its machines drew last tick. */
    public long[] stored() {
        long amount = 0, capacity = 0;
        for (PowerBlockEntity be : cells) {
            amount += be.energy().getAmountAsLong();
            capacity += be.energy().getCapacityAsLong();
        }
        return new long[] {amount, capacity, cells.size(), drawn};
    }
}
