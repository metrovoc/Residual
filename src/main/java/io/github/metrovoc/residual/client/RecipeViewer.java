package io.github.metrovoc.residual.client;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * The search box of a recipe viewer, if there is one: its plugin connects here when it starts. Nothing else in the mod knows
 * one exists, and all of this does nothing without it.
 */
public final class RecipeViewer {
    @Nullable
    private static Supplier<String> filter;
    @Nullable
    private static Consumer<String> setFilter;

    private RecipeViewer() {
    }

    public static void connect(Supplier<String> get, Consumer<String> set) {
        filter = get;
        setFilter = set;
    }

    public static void disconnect() {
        filter = null;
        setFilter = null;
    }

    /** What its search box holds, or null with none there. */
    @Nullable
    public static String search() {
        return filter == null ? null : filter.get();
    }

    public static void search(String text) {
        if (setFilter != null) {
            setFilter.accept(text);
        }
    }
}
