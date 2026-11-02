package io.github.metrovoc.residual.compute;

import io.github.metrovoc.residual.block.AcceleratorBlock;
import io.github.metrovoc.residual.energy.PowerNetwork;
import io.github.metrovoc.residual.energy.PowerNode;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.world.Cooling;
import io.github.metrovoc.residual.world.GraphScanner;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Everything computing together around a block: Accelerators touching it or each other, and racks reached through
 * switches and Interconnect, found by a flood fill when first asked for and forgotten whenever a piece of it comes, goes
 * or changes, as storage and power networks are.
 *
 * <p>A rack is a column of up to {@link #MODULES_A_RACK} modules with a Rack Switch on top. With no Pod Switch a cluster
 * has the use of one rack; a Pod Switch carries {@link #RACKS_A_POD} racks and a cluster of one pod no more than that; a Hall
 * Console, with at least two pods and a closed cooling loop, makes a hall, where every pod counts.
 *
 * <p>A device is one Accelerator, on its own or in a rack's bay, printed at a {@link ProcessNode}. The first {@link #FREE}
 * Accelerators on their own run on what the block they touch can spare. Every other device has to be paid for, from
 * the power network it is part of, and stays dark while that has nothing to give.
 */
public final class Cluster {
    /** Accelerators a block runs without any power. */
    public static final int FREE = 4;
    /** What a Model Core holds on its own. */
    public static final long CORE_MEMORY = 2L << 20;
    public static final int MODULES_A_RACK = 4, RACKS_A_POD = 8;
    /** How long a report of a device's load lasts, in ticks. */
    private static final int LOAD_LASTS = 40;
    private static final int LARGEST = 16_384;
    private static final int MOST_RACKS_SHOWN = 128;
    private static final Map<ServerLevel, Map<BlockPos, Cluster>> FOUND = new WeakHashMap<>();

    /** One device: an Accelerator at {@code at}, on its own when {@code bay} is -1. */
    public record Device(ProcessNode node, BlockPos at, int bay) {
        public boolean bare() {
            return bay < 0;
        }
    }

    /** A rack: its Rack Switch and its modules from the top down. */
    public record Rack(BlockPos top, List<BlockPos> modules) {
    }

    /** A cooling line: what it is made of and the devices along it, by their place in {@link #devices}. */
    private static final class Loop {
        final Set<BlockPos> pipes = new HashSet<>();
        final List<ChillerBlockEntity> chillers = new ArrayList<>();
        final List<Integer> along = new ArrayList<>();
        boolean flowing;
    }

    /**
     * How a run would go on this cluster.
     *
     * @param used       the devices it would use, by their place in {@link #devices}; the Core itself is -1
     * @param threads    threads it would train with
     * @param budget     the memory that has to hold it, in the way this split has to: a single device's for data parallelism
     * @param fits       whether it does
     * @param metered    whether it uses a device that has to be paid for
     * @param flopsPerFe what its devices get for a unit of energy
     */
    public record Plan(Parallelism mode, int[] used, int threads, long budget, boolean fits, boolean metered, double flopsPerFe, int hot) {
    }

    private final ServerLevel level;
    private final List<Device> devices = new ArrayList<>();
    private final List<Rack> racks = new ArrayList<>();
    private final List<Loop> loops = new ArrayList<>();
    private final Set<BlockPos> members = new LinkedHashSet<>();
    private final List<BlockPos> walls = new ArrayList<>();
    private int freeCount, allRacks, pods;
    private boolean hall, console;
    /** A block of each part of it that power could reach on its own: Interconnect carries none. */
    private final List<BlockPos> powerAt = new ArrayList<>();
    private boolean[] cooled = new boolean[0];
    private long cooledAt = Long.MIN_VALUE;
    private float[] load = new float[0];
    private long loadAt;
    private long flopsNow;
    private double flopsPerSecond;
    private record RatedPayment(long tick, boolean funded) {
    }

    private final Map<BlockPos, RatedPayment> paid = new HashMap<>();
    private long itFe, itFeThen, coolingFeThen;
    private double pue = 1;
    private long meteredAt;
    private int feNow;

    private Cluster(ServerLevel level) {
        this.level = level;
    }

    /** A piece of a cluster came, went, loaded, unloaded or changed: clusters in this level are found again when next asked for. */
    public static void changed(ServerLevel level) {
        FOUND.remove(level);
    }

    /** What computes around {@code pos}, a block of it or one that touches it. */
    public static Cluster at(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Cluster> found = FOUND.computeIfAbsent(level, l -> new HashMap<>());
        Cluster known = found.get(pos);
        if (known != null) {
            return known;
        }
        known = sameAround(level, pos, found);
        if (known != null) {
            found.put(pos.immutable(), known);
            return known;
        }
        Cluster cluster = new Cluster(level);
        cluster.find(pos);
        cluster.members.forEach(p -> found.put(p, cluster));
        found.put(pos.immutable(), cluster);
        return cluster;
    }

    /** The one cluster already found all around a block that takes compute, so that two such blocks see one cluster; or null. */
    @Nullable
    private static Cluster sameAround(ServerLevel level, BlockPos pos, Map<BlockPos, Cluster> found) {
        if (level.getBlockState(pos).getBlock() instanceof ComputeNode) {
            return null;
        }
        Cluster same = null;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (level.isLoaded(n) && level.getBlockState(n).getBlock() instanceof ComputeNode) {
                Cluster there = found.get(n);
                if (there == null || same != null && same != there) {
                    return null;
                }
                same = there;
            }
        }
        return same;
    }

    private void find(BlockPos start) {
        ArrayDeque<BlockPos> next = new ArrayDeque<>();
        if (level.getBlockState(start).getBlock() instanceof ComputeNode) {
            members.add(start.immutable());
            next.add(start.immutable());
        } else {
            // a block that takes compute takes it from what touches it, even a rack from the side
            for (Direction d : Direction.values()) {
                BlockPos n = start.relative(d);
                if (level.isLoaded(n) && level.getBlockState(n).getBlock() instanceof ComputeNode && members.add(n)) {
                    next.add(n);
                }
            }
        }
        List<BlockPos> switches = new ArrayList<>();
        Set<BlockPos> modules = new HashSet<>();
        List<Device> bare = new ArrayList<>();
        int podSwitches = 0;
        while (!next.isEmpty() && members.size() < LARGEST) {
            BlockPos at = next.poll();
            BlockState here = level.getBlockState(at);
            Block block = here.getBlock();
            if (block instanceof AcceleratorBlock a) {
                bare.add(new Device(a.node, at, -1));
            } else if (block instanceof RackModuleBlock) {
                modules.add(at);
            } else if (block instanceof RackSwitchBlock) {
                switches.add(at);
            } else if (block instanceof PodSwitchBlock) {
                podSwitches++;
            } else if (block instanceof HallConsoleBlock) {
                console = true;
            } else if (block instanceof LoadWallBlock) {
                walls.add(at);
            }
            if (!(block instanceof ComputeNode node)) {
                continue;
            }
            for (Direction d : Direction.values()) {
                BlockPos n = at.relative(d);
                if (members.contains(n) || !level.isLoaded(n) || !node.links(here, d)) {
                    continue;
                }
                BlockState there = level.getBlockState(n);
                if (there.getBlock() instanceof ComputeNode other && other.links(there, d.getOpposite())) {
                    members.add(n);
                    next.add(n);
                }
            }
        }
        pods = podSwitches;
        // bare devices first, nearest the block asked about first
        bare.sort(Comparator.comparingDouble(d -> d.at().distSqr(start)));
        freeCount = Math.min(FREE, bare.size());
        devices.addAll(bare.subList(0, Math.min(bare.size(), GraphScanner.MAX_ACCELERATORS)));
        List<Rack> all = new ArrayList<>();
        for (BlockPos top : switches) {
            List<BlockPos> column = new ArrayList<>();
            BlockPos at = top.below();
            while (column.size() < MODULES_A_RACK && modules.contains(at)) {
                column.add(at);
                at = at.below();
            }
            if (!column.isEmpty()) {
                all.add(new Rack(top, column));
            }
        }
        all.sort(Comparator.comparingDouble(r -> r.top().distSqr(start)));
        allRacks = all.size();
        boolean closed = findLoops(all);
        hall = console && pods >= 2 && closed;
        int usable = pods == 0 ? 1 : hall ? RACKS_A_POD * pods : RACKS_A_POD;
        for (Rack r : all.subList(0, Math.min(usable, all.size()))) {
            racks.add(r);
            for (BlockPos module : r.modules()) {
                if (level.getBlockEntity(module) instanceof RackModuleBlockEntity be) {
                    for (int bay = 0; bay < RackModuleBlockEntity.BAYS; bay++) {
                        ItemStack s = be.bay(bay);
                        if (!s.isEmpty() && RackModuleBlockEntity.node(s) instanceof ProcessNode node) {
                            devices.add(new Device(node, module, bay));
                        }
                    }
                }
            }
        }
        attachLoops();
        for (Device d : devices) {
            if (d.bare()) {
                powerAt.add(d.at());
                break;
            }
        }
        racks.forEach(r -> powerAt.add(r.top()));
        load = new float[devices.size()];
        for (Device d : devices) {
            flopsPerSecond += d.node().flopsPerSecond();
        }
    }

    /** Every cooling line that touches a module or an Accelerator, and whether any of them has a chiller closing it. */
    private boolean findLoops(List<Rack> all) {
        Map<BlockPos, Loop> byPipe = new HashMap<>();
        Set<BlockPos> touching = new HashSet<>();
        for (Rack r : all) {
            touching.addAll(r.modules());
        }
        for (Device d : devices) {
            touching.add(d.at());
        }
        boolean closed = false;
        for (BlockPos at : touching) {
            for (Direction d : Direction.values()) {
                BlockPos n = at.relative(d);
                if (byPipe.containsKey(n) || !level.isLoaded(n) || !(level.getBlockState(n).getBlock() instanceof CoolantPipeBlock)) {
                    continue;
                }
                Loop loop = new Loop();
                ArrayDeque<BlockPos> next = new ArrayDeque<>();
                loop.pipes.add(n);
                next.add(n);
                Set<BlockPos> chillers = new HashSet<>();
                while (!next.isEmpty() && loop.pipes.size() < LARGEST) {
                    BlockPos p = next.poll();
                    byPipe.put(p, loop);
                    for (Direction f : Direction.values()) {
                        BlockPos q = p.relative(f);
                        if (!level.isLoaded(q)) {
                            continue;
                        }
                        Block block = level.getBlockState(q).getBlock();
                        if (block instanceof CoolantPipeBlock && loop.pipes.add(q)) {
                            next.add(q);
                        } else if (block instanceof ChillerBlock) {
                            chillers.add(q);
                        }
                    }
                }
                for (BlockPos c : chillers) {
                    Direction facing = level.getBlockState(c).getValue(ChillerBlock.FACING);
                    // a chiller closes the line that leaves its front and comes back to its back
                    if (loop.pipes.contains(c.relative(facing)) && loop.pipes.contains(c.relative(facing.getOpposite()))
                            && level.getBlockEntity(c) instanceof ChillerBlockEntity be) {
                        loop.chillers.add(be);
                        closed = true;
                    }
                }
                loops.add(loop);
            }
        }
        return closed;
    }

    /** Which devices lie along each line: an Accelerator by touching it, and the whole of a rack when any of its modules does. */
    private void attachLoops() {
        for (Loop loop : loops) {
            Set<BlockPos> touching = new HashSet<>();
            for (Rack r : racks) {
                if (r.modules().stream().anyMatch(m -> touches(loop, m))) {
                    touching.addAll(r.modules());
                }
            }
            for (int i = 0; i < devices.size(); i++) {
                Device d = devices.get(i);
                if (d.bare() ? touches(loop, d.at()) : touching.contains(d.at())) {
                    loop.along.add(i);
                }
            }
        }
    }

    private static boolean touches(Loop loop, BlockPos at) {
        for (Direction d : Direction.values()) {
            if (loop.pipes.contains(at.relative(d))) {
                return true;
            }
        }
        return false;
    }

    public boolean empty() {
        return members.isEmpty();
    }

    public List<Device> devices() {
        return devices;
    }

    public List<Rack> racks() {
        return racks;
    }

    /** Racks beyond what its switches give it the use of. */
    public int racksDark() {
        return allRacks - racks.size();
    }

    public int pods() {
        return pods;
    }

    public boolean hall() {
        return hall;
    }

    public List<BlockPos> walls() {
        return walls;
    }

    public boolean contains(BlockPos pos) {
        return members.contains(pos);
    }

    /** The power networks its parts belong to, each once. */
    private Set<PowerNetwork> networks() {
        Set<PowerNetwork> out = new LinkedHashSet<>();
        for (BlockPos p : powerAt) {
            out.add(PowerNetwork.at(level, p));
        }
        return out;
    }

    /** Whether any power network it is part of has anything to give. */
    public boolean powered() {
        for (PowerNetwork network : networks()) {
            if (network.powered()) {
                return true;
            }
        }
        return false;
    }

    /** Whether device {@code i} runs without being paid for. */
    public boolean free(int i) {
        return i < freeCount;
    }

    /** Which devices are cooled, looked at again at most once a second. */
    private boolean[] cooled() {
        long now = level.getGameTime();
        if (now - cooledAt < 20 && cooled.length == devices.size()) {
            return cooled;
        }
        cooledAt = now;
        cooled = new boolean[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            Device d = devices.get(i);
            cooled[i] = d.bare() && Cooling.cooled(level, d.at());
        }
        for (Loop loop : loops) {
            long room = 0;
            for (ChillerBlockEntity chiller : loop.chillers) {
                room += chiller.room();
            }
            boolean flowing = room > 0;
            for (int i : loop.along) {
                int heat = devices.get(i).node().fePerTick();
                if (room >= heat) {
                    room -= heat;
                    cooled[i] = true;
                }
            }
            if (flowing != loop.flowing) {
                loop.flowing = flowing;
                for (BlockPos p : loop.pipes) {
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() instanceof CoolantPipeBlock) {
                        level.setBlock(p, s.setValue(CoolantPipeBlock.FLOWING, flowing), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        return cooled;
    }

    public boolean cooled(int device) {
        boolean[] c = cooled();
        return device < c.length && c[device];
    }

    /** Whether device {@code i} is running now: free, or the network has power for it. */
    private boolean lit(int i, boolean powered) {
        return free(i) || powered;
    }

    /** What the cluster lends something that takes compute but runs no model of its own. */
    public Cooling.Compute compute(int base, int max) {
        boolean powered = powered();
        boolean[] cool = cooled();
        int units = 0, hotUnits = 0, hot = 0;
        for (int i = 0; i < devices.size(); i++) {
            if (lit(i, powered)) {
                if (cool[i]) {
                    units += devices.get(i).node().units();
                } else {
                    hotUnits += devices.get(i).node().units();
                    hot++;
                }
            }
        }
        return new Cooling.Compute(Math.max(1, Math.min(max, base + units + hotUnits / 2)), hot);
    }

    /** Notes every device that runs as flat out, for what takes compute but runs no model of its own. */
    public void flatOut() {
        boolean powered = powered();
        for (int i = 0; i < devices.size(); i++) {
            load[i] = lit(i, powered) ? 1 : 0;
        }
        loadAt = level.getGameTime();
        flopsNow = 0;
    }

    /** Lights its Accelerators that run hot. */
    public void lights(boolean on) {
        boolean powered = powered();
        boolean[] cool = cooled();
        for (int i = 0; i < devices.size(); i++) {
            Device d = devices.get(i);
            if (d.bare()) {
                BlockState s = level.getBlockState(d.at());
                boolean hot = on && lit(i, powered) && !cool[i];
                if (s.getBlock() instanceof AcceleratorBlock && s.getValue(AcceleratorBlock.HOT) != hot) {
                    level.setBlock(d.at(), s.setValue(AcceleratorBlock.HOT, hot), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** How busy a run's devices were: {@code used} as a {@link Plan} names them, the Core itself left out; and the flops it ran a second. */
    public void report(int[] used, double[] busy, long flops) {
        Arrays.fill(load, 0);
        for (int k = 0; k < used.length && k < busy.length; k++) {
            if (used[k] >= 0 && used[k] < load.length) {
                load[used[k]] = (float) busy[k];
            }
        }
        loadAt = level.getGameTime();
        flopsNow = flops;
    }

    /** The flops a second of the run on it lately, or none. */
    public long flopsNow() {
        return level.getGameTime() - loadAt <= LOAD_LASTS ? flopsNow : 0;
    }

    /** Its devices at work lately. */
    public int busy() {
        int busy = 0;
        for (int i = 0; i < devices.size(); i++) {
            busy += load(i) > 0.02f ? 1 : 0;
        }
        return busy;
    }

    /** How busy each rack it has the use of has been lately, in 256ths. */
    public byte[] rackLoads() {
        byte[] out = new byte[Math.min(racks.size(), MOST_RACKS_SHOWN)];
        for (int r = 0; r < out.length; r++) {
            List<BlockPos> modules = racks.get(r).modules();
            float sum = 0;
            int count = 0;
            for (int i = 0; i < devices.size(); i++) {
                if (modules.contains(devices.get(i).at())) {
                    sum += load(i);
                    count++;
                }
            }
            out[r] = (byte) Math.clamp(Math.round(255 * sum / Math.max(1, count)), 0, 255);
        }
        return out;
    }

    /** How busy device {@code i} has been lately, 0 to 1. */
    public float load(int i) {
        return level.getGameTime() - loadAt <= LOAD_LASTS && i < load.length ? load[i] : 0;
    }

    /** How busy the devices of the rack module at {@code pos} have been lately, by bay. */
    public void loads(BlockPos module, float[] out) {
        Arrays.fill(out, 0);
        for (int i = 0; i < devices.size(); i++) {
            Device d = devices.get(i);
            if (d.at().equals(module) && d.bay() >= 0 && d.bay() < out.length) {
                out[d.bay()] = load(i);
            }
        }
    }

    public boolean coolingShort(BlockPos module) {
        boolean[] c = cooled();
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).at().equals(module) && !c[i]) {
                return true;
            }
        }
        return false;
    }

    /** Whether the module at {@code pos} is in a rack it has the use of. */
    public boolean uses(BlockPos module) {
        for (Rack r : racks) {
            if (r.modules().contains(module)) {
                return true;
            }
        }
        return false;
    }

    /** Pays {@code fe} from its power network, for work at {@code at}; what it got. The chillers then carry that much heat. */
    public int pay(int fe, BlockPos at) {
        if (fe <= 0) {
            return 0;
        }
        int got = 0;
        for (PowerNetwork network : networks()) {
            if (got < fe) {
                got += network.draw(fe - got, at);
            }
        }
        if (got > 0) {
            itFe += got;
            carryHeat(got);
        }
        return got;
    }

    /** Hands {@code fe} of heat to the chillers of every line that has one. */
    private void carryHeat(int fe) {
        int chillers = 0;
        for (Loop loop : loops) {
            chillers += loop.chillers.size();
        }
        if (chillers > 0) {
            for (Loop loop : loops) {
                for (ChillerBlockEntity chiller : loop.chillers) {
                    chiller.carry(fe / chillers);
                }
            }
        }
    }

    /** Pays for one tick at full load, once per host and game tick; whether every metered device was funded. */
    public boolean payRated(BlockPos host) {
        long now = level.getGameTime();
        RatedPayment previous = paid.get(host);
        if (previous != null && previous.tick() == now) {
            return previous.funded();
        }
        long fe = 0;
        for (int i = freeCount; i < devices.size(); i++) {
            fe += devices.get(i).node().fePerTick();
        }
        int cost = (int) Math.min(Integer.MAX_VALUE, fe);
        boolean funded = pay(cost, host) == cost;
        paid.put(host.immutable(), new RatedPayment(now, funded));
        return funded;
    }

    /** The share of power its computing took on top for cooling, measured since it was last asked: total over IT. */
    public double pue() {
        long now = level.getGameTime();
        long since = now - meteredAt;
        if (since >= 200) {
            meteredAt = now;
            long cooling = 0;
            for (Loop loop : loops) {
                for (ChillerBlockEntity chiller : loop.chillers) {
                    cooling += chiller.spent();
                }
            }
            long it = itFe - itFeThen, c = cooling - coolingFeThen;
            if (it > 0) {
                pue = (double) (it + c) / it;
            }
            feNow = (int) ((it + c) / since);
            itFeThen = itFe;
            coolingFeThen = cooling;
        }
        return pue;
    }

    /** What it has drawn a tick lately, cooling included. */
    public int feNow() {
        pue();
        return feNow;
    }

    /** Every device rated: what G25 and G30's bookings run on. */
    public Accounts.Capacity capacity() {
        long memory = 0;
        double energy = 0;
        for (Device d : devices) {
            memory += d.node().memoryBytes();
            energy += d.node().flopsPerSecond() / d.node().flopsPerFe();
        }
        return new Accounts.Capacity(devices.size(), flopsPerSecond, memory, energy > 0 ? flopsPerSecond / energy : 1, pue());
    }

    /**
     * How a model that takes {@code bytes} to train, compiled as {@code model}, would train from the block this cluster was
     * asked about. The Core itself is a device of {@code base} threads and {@link #CORE_MEMORY}; on one device, the
     * Accelerators on their own pool their memory and threads with it, and racks sit idle.
     */
    public Plan plan(Parallelism mode, int base, int maxThreads, long bytes, @Nullable CompiledGraph model) {
        boolean powered = powered();
        boolean[] cool = cooled();
        if (mode == Parallelism.ONE) {
            long memory = CORE_MEMORY;
            int cooledBare = 0, hotBare = 0, metered = 0;
            double energy = 1 / ProcessNode.N90.flopsPerFe(), count = 1;
            List<Integer> used = new ArrayList<>();
            for (int i = 0; i < devices.size(); i++) {
                Device d = devices.get(i);
                if (d.bare() && lit(i, powered)) {
                    memory += d.node().memoryBytes();
                    used.add(i);
                    metered += free(i) ? 0 : 1;
                    energy += 1 / d.node().flopsPerFe();
                    count++;
                    if (cool[i]) {
                        cooledBare++;
                    } else {
                        hotBare++;
                    }
                }
            }
            int threads = Math.max(1, Math.min(maxThreads, base + cooledBare + hotBare / 2));
            return new Plan(mode, used.stream().mapToInt(Integer::intValue).toArray(), threads, memory, bytes <= memory, metered > 0,
                    count / energy, hotBare);
        }
        // the devices it could use, the Core first and then the largest
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < devices.size(); i++) {
            if (lit(i, powered)) {
                order.add(i);
            }
        }
        order.sort(Comparator.<Integer>comparingLong(i -> -devices.get(i).node().memoryBytes()).thenComparing(i -> cool[i] ? 0 : 1));
        // a hot device lends half: every second one sits out
        List<Integer> running = new ArrayList<>();
        int hotSeen = 0, hot = 0;
        for (int i : order) {
            if (cool[i] || hotSeen++ % 2 == 0) {
                running.add(i);
                hot += cool[i] ? 0 : 1;
            }
        }
        long[] memory = new long[running.size() + 1];
        memory[0] = CORE_MEMORY;
        for (int k = 0; k < running.size(); k++) {
            memory[k + 1] = devices.get(running.get(k)).node().memoryBytes();
        }
        int n = Math.min(maxThreads, memory.length);
        long budget;
        boolean fits;
        int count;
        switch (mode) {
            case DATA -> {
                // only a device that holds all of it can take part
                List<Integer> holding = new ArrayList<>();
                for (int k = 0; k < memory.length; k++) {
                    if (memory[k] >= bytes) {
                        holding.add(k);
                    }
                }
                count = Math.min(n, holding.size());
                budget = Arrays.stream(memory).max().orElse(0);
                fits = count > 0;
                int[] used = new int[count];
                for (int k = 0; k < count; k++) {
                    used[k] = holding.get(k) == 0 ? -1 : running.get(holding.get(k) - 1);
                }
                return plan(mode, used, count, budget, fits);
            }
            case TENSOR -> {
                // as many of the largest as hold an even share
                long[] sorted = memory.clone();
                Arrays.sort(sorted);
                int best = 0;
                for (int c = n; c >= 1; c--) {
                    if (bytes / c <= sorted[sorted.length - c]) {
                        best = c;
                        break;
                    }
                }
                count = Math.max(best, 1);
                budget = 0;
                for (int c = 0; c < count; c++) {
                    budget += sorted[sorted.length - 1 - c];
                }
                fits = best > 0;
            }
            case PIPELINE -> {
                long[] stages = model == null ? new long[] {bytes} : PipelineParallel.stageBytes(model, n);
                long[] sorted = memory.clone();
                Arrays.sort(sorted);
                long[] needs = stages.clone();
                Arrays.sort(needs);
                fits = needs.length <= sorted.length;
                budget = 0;
                count = needs.length;
                for (int k = 0; fits && k < needs.length; k++) {
                    fits = needs[needs.length - 1 - k] <= sorted[sorted.length - 1 - k];
                }
                for (int k = 0; k < Math.min(count, sorted.length); k++) {
                    budget += sorted[sorted.length - 1 - k];
                }
            }
            default -> throw new IllegalStateException();
        }
        int[] used = new int[count];
        for (int k = 0; k < count; k++) {
            used[k] = k == 0 ? -1 : k <= running.size() ? running.get(k - 1) : -1;
        }
        return plan(mode, used, count, budget, fits);
    }

    private Plan plan(Parallelism mode, int[] used, int threads, long budget, boolean fits) {
        boolean metered = false;
        double energy = 0;
        int counted = 0, hot = 0;
        boolean[] cool = cooled();
        for (int i : used) {
            if (i >= 0) {
                metered |= !free(i);
                energy += 1 / devices.get(i).node().flopsPerFe();
                counted++;
                hot += cool[i] ? 0 : 1;
            }
        }
        return new Plan(mode, used, Math.max(1, threads), budget, fits, metered, counted == 0 ? ProcessNode.N90.flopsPerFe() : counted / energy, hot);
    }
}
