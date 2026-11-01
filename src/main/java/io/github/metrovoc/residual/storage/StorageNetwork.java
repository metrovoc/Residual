package io.github.metrovoc.residual.storage;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * Storage blocks joined by links, and everything they keep together. There are no channels and no limit on devices;
 * nothing needs power to keep or show what it holds. A network is found again whenever one of its blocks comes or
 * goes. Links limit one thing, how much flows: what Ports and Docks move goes by the shortest way to the nearest place
 * things are kept, and a link that has carried its {@link #LINK_RATE} this tick makes the rest wait for the next, or
 * carries {@link #DRIVEN_RATE} while a Link Driver has power.
 */
public final class StorageNetwork {
    /** The networks found so far in each level, by every block in them; forgotten whenever a storage block changes. */
    private static final Map<ServerLevel, Map<BlockPos, StorageNetwork>> FOUND = new WeakHashMap<>();
    /** Bigger networks are not followed further, so a runaway link line cannot stall the server. */
    private static final int LARGEST = 262_144;
    /** Items a Storage Link carries a tick, and with a Link Driver running. */
    public static final int LINK_RATE = 1_024, DRIVEN_RATE = 4_096;

    private final ServerLevel level;
    public final Set<BlockPos> nodes;
    private final List<BlockPos> racks = new ArrayList<>();
    private final List<BlockPos> ports = new ArrayList<>();
    private final List<BlockPos> assemblers = new ArrayList<>();
    private final List<BlockPos> processors = new ArrayList<>();
    private final List<BlockPos> accessPoints = new ArrayList<>();
    private final List<BlockPos> drivers = new ArrayList<>();
    /** Where the last items stored or taken went or came from, for the flows players see along the links. */
    @Nullable
    public BlockPos lastStore;
    /** What it was, for the streams: one of these is empty. */
    public ItemResource lastItem = ItemResource.EMPTY;
    public FluidResource lastFluid = FluidResource.EMPTY;
    /** Each block's index, the next block on its way to the nearest store (-1 at a store, -2 with none), and its load this tick. */
    @Nullable
    private Object2IntOpenHashMap<BlockPos> index;
    private int[] toward;
    private boolean[] link;
    private int[] load;
    private long[] loadedAt;
    /** Until when a Link Driver has paid for faster links, and when a link last wanted to carry more than its plain rate. */
    private long drivenUntil = -1, wanted = -1;
    /** A single drive taken for a network, as a portable drive's own screen works on one; null on a network of blocks. */
    @Nullable
    private final Drive heldDrive;
    @Nullable
    private final Store held;

    private StorageNetwork(ServerLevel level, Set<BlockPos> nodes) {
        this.level = level;
        this.nodes = nodes;
        this.heldDrive = null;
        this.held = null;
        for (BlockPos p : nodes) {
            if (level.getBlockEntity(p) instanceof RackBlockEntity) {
                racks.add(p);
            } else if (level.getBlockEntity(p) instanceof PortBlockEntity) {
                ports.add(p);
            } else if (level.getBlockEntity(p) instanceof AssemblerBlockEntity) {
                assemblers.add(p);
            } else if (level.getBlockEntity(p) instanceof ProcessorBlockEntity) {
                processors.add(p);
            } else if (level.getBlockEntity(p) instanceof AccessPointBlockEntity) {
                accessPoints.add(p);
            } else if (level.getBlockEntity(p) instanceof LinkDriverBlockEntity) {
                drivers.add(p);
            }
        }
    }

    private StorageNetwork(ServerLevel level, Drive drive, BlockPos at) {
        this.level = level;
        this.nodes = Set.of();
        this.heldDrive = drive;
        this.held = Store.of(drive, at);
    }

    /** One drive on its own, carried by whoever stands at {@code at}: no links, no machines, only what it holds. */
    public static StorageNetwork holding(ServerLevel level, Drive drive, BlockPos at) {
        return new StorageNetwork(level, drive, at);
    }

    /** A storage block came, went, loaded or unloaded: networks in this level are found again when next asked for. */
    public static void changed(ServerLevel level) {
        FOUND.remove(level);
    }

    /** The network the storage block at {@code pos} belongs to. */
    public static StorageNetwork at(ServerLevel level, BlockPos pos) {
        Map<BlockPos, StorageNetwork> found = FOUND.computeIfAbsent(level, l -> new HashMap<>());
        StorageNetwork known = found.get(pos);
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
            if (!(here.getBlock() instanceof Node node)) {
                continue;
            }
            for (Direction d : Direction.values()) {
                BlockPos n = at.relative(d);
                if (seen.contains(n) || !level.isLoaded(n) || !node.joins(here, d)) {
                    continue;
                }
                BlockState there = level.getBlockState(n);
                if (there.getBlock() instanceof Node other && other.joins(there, d.getOpposite())) {
                    seen.add(n);
                    next.add(n);
                }
            }
        }
        StorageNetwork network = new StorageNetwork(level, seen);
        seen.forEach(p -> found.put(p, network));
        return network;
    }

    /** Where the network keeps items and fluids, highest priority first. */
    public List<Store> stores() {
        List<Store> out = new ArrayList<>();
        if (held != null) {
            out.add(held);
            return out;
        }
        for (BlockPos p : racks) {
            if (level.getBlockEntity(p) instanceof RackBlockEntity rack) {
                rack.stores(out);
            }
        }
        for (BlockPos p : ports) {
            if (level.getBlockEntity(p) instanceof PortBlockEntity port && port.window() != null) {
                out.add(port.window());
            }
        }
        out.sort(Comparator.comparingInt(Store::priority).reversed());
        return out;
    }

    public List<PortBlockEntity> ports() {
        List<PortBlockEntity> out = new ArrayList<>();
        for (BlockPos p : ports) {
            if (level.getBlockEntity(p) instanceof PortBlockEntity port) {
                out.add(port);
            }
        }
        return out;
    }

    public List<AssemblerBlockEntity> assemblers() {
        List<AssemblerBlockEntity> out = new ArrayList<>();
        for (BlockPos p : assemblers) {
            if (level.getBlockEntity(p) instanceof AssemblerBlockEntity a) {
                out.add(a);
            }
        }
        return out;
    }

    public List<ProcessorBlockEntity> processors() {
        List<ProcessorBlockEntity> out = new ArrayList<>();
        for (BlockPos p : processors) {
            if (level.getBlockEntity(p) instanceof ProcessorBlockEntity a) {
                out.add(a);
            }
        }
        return out;
    }

    public List<AccessPointBlockEntity> accessPoints() {
        List<AccessPointBlockEntity> out = new ArrayList<>();
        for (BlockPos p : accessPoints) {
            if (level.getBlockEntity(p) instanceof AccessPointBlockEntity a) {
                out.add(a);
            }
        }
        return out;
    }

    private List<LinkDriverBlockEntity> drivers() {
        List<LinkDriverBlockEntity> out = new ArrayList<>();
        for (BlockPos p : drivers) {
            if (level.getBlockEntity(p) instanceof LinkDriverBlockEntity d) {
                out.add(d);
            }
        }
        return out;
    }

    /** Whether a Link Driver on it has power to spend. */
    public boolean driverHasPower() {
        for (LinkDriverBlockEntity d : drivers()) {
            if (d.energy().getAmountAsInt() >= LinkDriverBlockEntity.COST) {
                return true;
            }
        }
        return false;
    }

    /** A tick of reaching further than an Access Point's plain range, paid by the first Link Driver with power. */
    void payForReach() {
        for (LinkDriverBlockEntity d : drivers()) {
            if (d.spend()) {
                return;
            }
        }
    }

    /** What a link carries a tick just now. */
    public int linkRate() {
        return level.getGameTime() <= drivenUntil ? DRIVEN_RATE : LINK_RATE;
    }

    /** Whether a link wanted to carry more than its plain rate this tick or the last. */
    boolean wantsDriving(long now) {
        return wanted >= now - 1;
    }

    void driveUntil(long tick) {
        drivenUntil = tick;
    }

    /** Works out each block's way to the nearest place things are kept, the first time anything asks. */
    private void route() {
        int n = nodes.size();
        BlockPos[] at = nodes.toArray(new BlockPos[0]);
        index = new Object2IntOpenHashMap<>(n);
        index.defaultReturnValue(-1);
        for (int i = 0; i < n; i++) {
            index.put(at[i], i);
        }
        toward = new int[n];
        Arrays.fill(toward, -2);
        link = new boolean[n];
        load = new int[n];
        loadedAt = new long[n];
        int[] queue = new int[n];
        int head = 0, tail = 0;
        for (int i = 0; i < n; i++) {
            link[i] = level.getBlockState(at[i]).getBlock() instanceof LinkBlock;
            boolean keeps = level.getBlockEntity(at[i]) instanceof RackBlockEntity
                    || level.getBlockEntity(at[i]) instanceof PortBlockEntity port && port.mode() == PortMode.WINDOW;
            if (keeps) {
                toward[i] = -1;
                queue[tail++] = i;
            }
        }
        while (head < tail) {
            int i = queue[head++];
            BlockState here = level.getBlockState(at[i]);
            if (!(here.getBlock() instanceof Node node)) {
                continue;
            }
            for (Direction d : Direction.values()) {
                int j = index.getInt(at[i].relative(d));
                if (j >= 0 && toward[j] == -2 && node.joins(here, d)) {
                    BlockState there = level.getBlockState(at[j]);
                    if (there.getBlock() instanceof Node other && other.joins(there, d.getOpposite())) {
                        toward[j] = i;
                        queue[tail++] = j;
                    }
                }
            }
        }
    }

    /** How many of the {@code most} items {@code from} would like to move this tick the links on its way have room for. */
    public int allowance(BlockPos from, int most) {
        if (held != null) {
            return most;
        }
        if (index == null) {
            route();
        }
        int at = index.getInt(from);
        if (at < 0) {
            return most;
        }
        long now = level.getGameTime();
        int rate = linkRate(), room = most;
        for (at = toward[at]; at >= 0; at = toward[at]) {
            if (link[at]) {
                room = Math.min(room, rate - (loadedAt[at] == now ? load[at] : 0));
            }
        }
        if (room < most) {
            wanted = now;
        }
        return Math.max(room, 0);
    }

    /** {@code from} moved {@code items}: they count against every link on its way this tick. */
    public void carry(BlockPos from, int items) {
        if (held != null || items <= 0) {
            return;
        }
        if (index == null) {
            route();
        }
        int at = index.getInt(from);
        if (at < 0) {
            return;
        }
        long now = level.getGameTime();
        for (at = toward[at]; at >= 0; at = toward[at]) {
            if (link[at]) {
                if (loadedAt[at] != now) {
                    loadedAt[at] = now;
                    load[at] = 0;
                }
                load[at] += items;
                if (load[at] > LINK_RATE) {
                    wanted = now;
                }
            }
        }
    }

    /**
     * Stores up to {@code amount} of {@code item}, first where that kind already is, then wherever there is room,
     * highest priority first; returns how many fit.
     */
    public long insert(ItemResource item, long amount, boolean simulate) {
        if (item.isEmpty() || amount <= 0) {
            return 0;
        }
        List<Store> stores = stores();
        long left = amount;
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (Store s : stores) {
                if (pass == 0 && s.count(item) == 0) {
                    continue;
                }
                long put = s.insert(item, left, simulate);
                if (put > 0 && !simulate) {
                    lastStore = s.pos();
                    lastItem = item;
                    lastFluid = FluidResource.EMPTY;
                }
                left -= put;
                if (left == 0) {
                    break;
                }
            }
        }
        return amount - left;
    }

    /** Takes up to {@code amount} of {@code item} out, lowest priority first; returns how many there were. */
    public long extract(ItemResource item, long amount, boolean simulate) {
        if (item.isEmpty() || amount <= 0) {
            return 0;
        }
        List<Store> stores = stores();
        long left = amount;
        for (int i = stores.size() - 1; i >= 0 && left > 0; i--) {
            long took = stores.get(i).extract(item, left, simulate);
            if (took > 0 && !simulate) {
                lastStore = stores.get(i).pos();
                lastItem = item;
                lastFluid = FluidResource.EMPTY;
            }
            left -= took;
        }
        return amount - left;
    }

    public long count(ItemResource item) {
        long n = 0;
        for (Store s : stores()) {
            n += s.count(item);
        }
        return n;
    }

    /** Everything it keeps, kind by kind. */
    public Object2LongLinkedOpenHashMap<ItemResource> contents() {
        Object2LongLinkedOpenHashMap<ItemResource> all = new Object2LongLinkedOpenHashMap<>();
        for (Store s : stores()) {
            s.forEach(all::addTo);
        }
        return all;
    }

    /** Stores up to {@code amount} millibuckets of {@code fluid}, as items are stored; returns how much fit. */
    public long insert(FluidResource fluid, long amount, boolean simulate) {
        if (fluid.isEmpty() || amount <= 0) {
            return 0;
        }
        List<Store> stores = stores();
        long left = amount;
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (Store s : stores) {
                if (pass == 0 && s.amount(fluid) == 0) {
                    continue;
                }
                long put = s.insert(fluid, left, simulate);
                if (put > 0 && !simulate) {
                    lastStore = s.pos();
                    lastFluid = fluid;
                    lastItem = ItemResource.EMPTY;
                }
                left -= put;
                if (left == 0) {
                    break;
                }
            }
        }
        return amount - left;
    }

    /** Takes up to {@code amount} millibuckets of {@code fluid} out, lowest priority first; returns how much there was. */
    public long extract(FluidResource fluid, long amount, boolean simulate) {
        if (fluid.isEmpty() || amount <= 0) {
            return 0;
        }
        List<Store> stores = stores();
        long left = amount;
        for (int i = stores.size() - 1; i >= 0 && left > 0; i--) {
            long took = stores.get(i).extract(fluid, left, simulate);
            if (took > 0 && !simulate) {
                lastStore = stores.get(i).pos();
                lastFluid = fluid;
                lastItem = ItemResource.EMPTY;
            }
            left -= took;
        }
        return amount - left;
    }

    public long amount(FluidResource fluid) {
        long n = 0;
        for (Store s : stores()) {
            n += s.amount(fluid);
        }
        return n;
    }

    /** Every fluid it keeps, in millibuckets, kind by kind. */
    public Object2LongLinkedOpenHashMap<FluidResource> fluids() {
        Object2LongLinkedOpenHashMap<FluidResource> all = new Object2LongLinkedOpenHashMap<>();
        for (Store s : stores()) {
            s.forEachFluid(all::addTo);
        }
        return all;
    }

    /** How much its drives hold, and how much they could. */
    public long[] usage() {
        long[] used = new long[2];
        if (heldDrive != null) {
            used[0] = heldDrive.stored();
            used[1] = heldDrive.capacity;
        }
        for (BlockPos p : racks) {
            if (level.getBlockEntity(p) instanceof RackBlockEntity rack) {
                rack.usage(used);
            }
        }
        return used;
    }
}
