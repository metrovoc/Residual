package io.github.metrovoc.residual.storage;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Storage blocks joined by links, and everything they keep together. There are no channels and no limit on devices;
 * nothing needs power to keep or show what it holds. A network is found again whenever one of its blocks comes or
 * goes.
 */
public final class StorageNetwork {
    /** The networks found so far in each level, by every block in them; forgotten whenever a storage block changes. */
    private static final Map<ServerLevel, Map<BlockPos, StorageNetwork>> FOUND = new WeakHashMap<>();
    /** Bigger networks are not followed further, so a runaway link line cannot stall the server. */
    private static final int LARGEST = 16_384;

    private final ServerLevel level;
    public final Set<BlockPos> nodes;
    private final List<BlockPos> racks = new ArrayList<>();
    private final List<BlockPos> ports = new ArrayList<>();

    private StorageNetwork(ServerLevel level, Set<BlockPos> nodes) {
        this.level = level;
        this.nodes = nodes;
        for (BlockPos p : nodes) {
            if (level.getBlockEntity(p) instanceof RackBlockEntity) {
                racks.add(p);
            } else if (level.getBlockEntity(p) instanceof PortBlockEntity) {
                ports.add(p);
            }
        }
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

    /** Where the network keeps items, highest priority first. */
    public List<Store> stores() {
        List<Store> out = new ArrayList<>();
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
                left -= s.insert(item, left, simulate);
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
            left -= stores.get(i).extract(item, left, simulate);
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

    /** How many items its drives hold, and how many they could. */
    public long[] usage() {
        long[] used = new long[2];
        for (BlockPos p : racks) {
            if (level.getBlockEntity(p) instanceof RackBlockEntity rack) {
                rack.usage(used);
            }
        }
        return used;
    }
}
