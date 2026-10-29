package io.github.metrovoc.residual.world;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The relay network: a Model Server answers anything within {@link #SERVER_RANGE} blocks of it, and anything within
 * {@link #RANGE} blocks of a relay that is in reach of it, directly or through other relays. Only loaded relays and
 * servers take part; they sign themselves in and out as their chunks load and unload.
 */
public final class Relays {
    public static final int RANGE = 32;
    public static final int SERVER_RANGE = 16;
    private static final Map<Level, Set<BlockPos>> RELAYS = new WeakHashMap<>();
    private static final Map<Level, Set<BlockPos>> SERVERS = new WeakHashMap<>();

    private Relays() {
    }

    public static synchronized void relay(Level level, BlockPos pos, boolean present) {
        sign(RELAYS, level, pos, present);
    }

    public static synchronized void server(Level level, BlockPos pos, boolean present) {
        sign(SERVERS, level, pos, present);
    }

    private static void sign(Map<Level, Set<BlockPos>> map, Level level, BlockPos pos, boolean present) {
        if (present) {
            map.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
        } else if (map.containsKey(level)) {
            map.get(level).remove(pos);
        }
    }

    /** Whether the server at {@code server} can answer something at {@code where}. */
    public static synchronized boolean covers(Level level, BlockPos server, BlockPos where) {
        if (server.distSqr(where) <= (double) SERVER_RANGE * SERVER_RANGE) {
            return true;
        }
        for (BlockPos r : network(level, server)) {
            if (r.distSqr(where) <= (double) RANGE * RANGE) {
                return true;
            }
        }
        return false;
    }

    /** The relays the server at {@code server} reaches, directly or through each other. */
    public static synchronized Set<BlockPos> network(Level level, BlockPos server) {
        Set<BlockPos> all = RELAYS.getOrDefault(level, Set.of());
        Set<BlockPos> reached = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        for (BlockPos r : all) {
            if (r.distSqr(server) <= (double) RANGE * RANGE) {
                reached.add(r);
                todo.add(r);
            }
        }
        while (!todo.isEmpty()) {
            BlockPos at = todo.poll();
            for (BlockPos r : all) {
                if (!reached.contains(r) && r.distSqr(at) <= (double) RANGE * RANGE) {
                    reached.add(r);
                    todo.add(r);
                }
            }
        }
        return reached;
    }

    /** The loaded servers whose network includes the relay at {@code relay}. */
    public static synchronized List<BlockPos> serversReaching(Level level, BlockPos relay) {
        return SERVERS.getOrDefault(level, Set.of()).stream().filter(s -> network(level, s).contains(relay)).toList();
    }

    /** Relays within reach of the one at {@code relay}. */
    public static synchronized int neighbours(Level level, BlockPos relay) {
        int n = 0;
        for (BlockPos r : RELAYS.getOrDefault(level, Set.of())) {
            if (!r.equals(relay) && r.distSqr(relay) <= (double) RANGE * RANGE) {
                n++;
            }
        }
        return n;
    }
}
