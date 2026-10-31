package io.github.metrovoc.residual.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * How a storage network would make an amount of something: a tree of what each step needs, how much of it comes from
 * storage, how many crafts make the rest, and what is missing. It works on a copy of what the network keeps, so it runs
 * off the server thread, and it is generic in the kind of thing, so tests can plan with names.
 */
public final class CraftPlan<K> {
    /** A way to make something. Per craft: how many it makes, what it takes, and what it leaves, such as a bucket. */
    public record Recipe<K>(K output, int made, Map<K, Integer> inputs, Map<K, Integer> leftovers) {
    }

    /** One kind the plan needs, and where it comes from. */
    public static final class Step<K> {
        public final K kind;
        public final long needed;
        public long stored, crafts, missing;
        @Nullable
        public Recipe<K> recipe;
        public final List<Step<K>> parts = new ArrayList<>();

        Step(K kind, long needed) {
            this.kind = kind;
            this.needed = needed;
        }
    }

    /** A tree is cut short past this many levels or steps, and what it would still need counts as missing. */
    public static final int DEEPEST = 32, LARGEST = 4096;

    public final Step<K> root;
    /** Crafts by recipe, over the whole tree. */
    public final Map<Recipe<K>, Long> crafts = new LinkedHashMap<>();
    /** What neither storage nor a recipe provides, by kind. */
    public final Map<K, Long> missing = new LinkedHashMap<>();
    /** Whether the tree grew too deep or too large and was cut short. */
    public boolean cut;
    private final Map<K, Long> stock;
    private final Map<K, List<Recipe<K>>> recipes;
    private int steps;

    private CraftPlan(K target, long amount, Map<K, Long> stock, Map<K, List<Recipe<K>>> recipes) {
        this.stock = new HashMap<>(stock);
        this.recipes = recipes;
        this.root = need(target, amount, new HashSet<>(), true);
    }

    /**
     * Plans {@code amount} more of {@code target}: what is asked for is always made, not taken from storage; what it
     * takes comes from {@code stock} first, then from {@code recipes}. Surplus a craft makes, and what a craft leaves,
     * go back into the stock for the rest of the tree. A step's crafts are planned together, so what they leave serves
     * later steps but not their own: three cakes from three buckets plan six more, though a job making them one at a
     * time would refill the same three.
     */
    public static <K> CraftPlan<K> of(K target, long amount, Map<K, Long> stock, Map<K, List<Recipe<K>>> recipes) {
        return new CraftPlan<>(target, amount, stock, recipes);
    }

    public boolean complete() {
        return missing.isEmpty();
    }

    public long totalCrafts() {
        return crafts.values().stream().mapToLong(Long::longValue).sum();
    }

    private Step<K> need(K kind, long n, Set<K> path, boolean root) {
        Step<K> step = new Step<>(kind, n);
        steps++;
        if (!root) {
            long take = Math.min(n, stock.getOrDefault(kind, 0L));
            if (take > 0) {
                stock.merge(kind, -take, Long::sum);
                step.stored = take;
                n -= take;
            }
        }
        if (n == 0) {
            return step;
        }
        // a kind already being made further up would make itself: that branch stops here
        List<Recipe<K>> ways = path.contains(kind) ? null : recipes.get(kind);
        Recipe<K> recipe = ways == null || ways.isEmpty() ? null : ways.getFirst();
        if (recipe != null && (path.size() >= DEEPEST || steps >= LARGEST)) {
            cut = true;
            recipe = null;
        }
        if (recipe == null) {
            step.missing = n;
            missing.merge(kind, n, Long::sum);
            return step;
        }
        long crafts = (n + recipe.made() - 1) / recipe.made();
        step.crafts = crafts;
        step.recipe = recipe;
        this.crafts.merge(recipe, crafts, Long::sum);
        path.add(kind);
        for (Map.Entry<K, Integer> in : recipe.inputs().entrySet()) {
            step.parts.add(need(in.getKey(), in.getValue() * crafts, path, false));
        }
        path.remove(kind);
        long surplus = crafts * recipe.made() - n;
        if (surplus > 0) {
            stock.merge(kind, surplus, Long::sum);
        }
        for (Map.Entry<K, Integer> left : recipe.leftovers().entrySet()) {
            stock.merge(left.getKey(), left.getValue() * crafts, Long::sum);
        }
        return step;
    }
}
