package io.github.metrovoc.residual.storage;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** What a Storage Terminal's screen works on: a terminal in the world, a wireless terminal in hand, or a portable drive. */
public interface TerminalHost {
    /** The most kinds a terminal keeps pinned to the top of its grid: two rows of it. */
    int MOST_PINS = 18;

    /** The network it shows; a portable drive is one of its own. */
    StorageNetwork network();

    /** What it keeps pinned to the top of the grid. */
    List<ItemResource> pins();

    /** Pins a kind, or lets go of it if it is pinned. */
    void pin(ItemResource kind);

    /** Its search slot: a model that finds what a query means. */
    Container searchModel();

    /** Whether {@code player} can still use it. */
    boolean reaches(Player player);

    /** Whether {@code stack} may go in: a portable drive will not hold itself. */
    default boolean takes(ItemStack stack) {
        return true;
    }

    /** Called a few times a second while the screen is open. */
    default void tick() {
    }

    /** {@code pins} with {@code kind} pinned, or let go of if it was. */
    static List<ItemResource> pinned(List<ItemResource> pins, ItemResource kind) {
        List<ItemResource> out = new ArrayList<>(pins);
        if (!out.remove(kind) && out.size() < MOST_PINS) {
            out.add(kind);
        }
        return out;
    }
}
