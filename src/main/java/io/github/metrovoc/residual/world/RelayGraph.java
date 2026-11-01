package io.github.metrovoc.residual.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * How a relay network joins, worked out from where its parts stand: two parts in one dimension join when their reaches
 * touch, so a network's coverage has no gaps and a place is covered by one network at most; and two gates tuned to each
 * other join across any distance and between dimensions. Reach runs level, at any height, as a map shows it.
 */
public final class RelayGraph<K> {
    /** A part of a network at a column of a dimension, reaching {@code reach} blocks around it. */
    public record Part<K>(K key, Object dimension, int x, int z, int reach, @Nullable K pair) {
        boolean touches(Part<K> o) {
            return dimension.equals(o.dimension) && distanceSqr(o.x, o.z) <= (long) (reach + o.reach) * (reach + o.reach);
        }

        boolean covers(Object dimension, int x, int z) {
            return this.dimension.equals(dimension) && reach > 0 && distanceSqr(x, z) <= (long) reach * reach;
        }

        long distanceSqr(int x, int z) {
            long dx = this.x - x, dz = this.z - z;
            return dx * dx + dz * dz;
        }
    }

    private final List<Part<K>> parts;
    private final Map<K, Integer> index = new HashMap<>();
    /** Each part's network, the index of the network's first part. */
    private final int[] root;
    private final Map<Integer, List<Part<K>>> networks = new HashMap<>();

    public RelayGraph(Collection<Part<K>> parts) {
        this.parts = List.copyOf(parts);
        root = new int[this.parts.size()];
        for (int i = 0; i < root.length; i++) {
            root[i] = i;
            index.put(this.parts.get(i).key(), i);
        }
        for (int i = 0; i < root.length; i++) {
            Part<K> a = this.parts.get(i);
            for (int j = i + 1; j < root.length; j++) {
                Part<K> b = this.parts.get(j);
                // gates and links reach nothing themselves, so most pairs are skipped here
                if ((a.reach() > 0 || b.reach() > 0) && a.touches(b)) {
                    join(i, j);
                }
            }
            Integer other = a.pair() == null ? null : index.get(a.pair());
            if (other != null) {
                join(i, other);
            }
        }
        for (int i = 0; i < root.length; i++) {
            networks.computeIfAbsent(find(i), k -> new ArrayList<>()).add(this.parts.get(i));
        }
    }

    private int find(int i) {
        while (root[i] != i) {
            root[i] = root[root[i]];
            i = root[i];
        }
        return i;
    }

    private void join(int a, int b) {
        int ra = find(a), rb = find(b);
        if (ra != rb) {
            root[Math.max(ra, rb)] = Math.min(ra, rb);
        }
    }

    /** The network a part belongs to, or -1 if there is no such part. */
    public int network(K key) {
        Integer i = index.get(key);
        return i == null ? -1 : find(i);
    }

    public List<Part<K>> members(int network) {
        return networks.getOrDefault(network, List.of());
    }

    public Collection<Integer> networks() {
        return networks.keySet();
    }

    /** The network that covers a column, or -1. */
    public int covering(Object dimension, int x, int z) {
        for (int i = 0; i < root.length; i++) {
            if (parts.get(i).covers(dimension, x, z)) {
                return find(i);
            }
        }
        return -1;
    }

    /** The steps a request takes from one part to another, or -1 if they are not on one network. */
    public int hops(K from, K to) {
        Integer start = index.get(from), goal = index.get(to);
        if (start == null || goal == null || find(start) != find(goal)) {
            return -1;
        }
        Map<Integer, Integer> steps = new HashMap<>();
        ArrayDeque<Integer> next = new ArrayDeque<>();
        steps.put(start, 0);
        next.add(start);
        while (!next.isEmpty()) {
            int at = next.poll();
            if (at == goal) {
                return steps.get(at);
            }
            Part<K> a = parts.get(at);
            Integer far = a.pair() == null ? null : index.get(a.pair());
            for (Part<K> b : members(find(at))) {
                int i = index.get(b.key());
                boolean joined = (a.reach() > 0 || b.reach() > 0) && a.touches(b) || far != null && far == i;
                if (joined && !steps.containsKey(i)) {
                    steps.put(i, steps.get(at) + 1);
                    next.add(i);
                }
            }
        }
        return -1;
    }
}
