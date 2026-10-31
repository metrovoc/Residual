package io.github.metrovoc.residual.storage;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** What a Port does with the inventory in front of it. */
public enum PortMode implements StringRepresentable {
    /** Takes everything, or only the kinds its filter names, into the network. */
    IMPORT,
    /** Puts the kinds its filter names from the network into the inventory. */
    EXPORT,
    /** Keeps the inventory holding a set number of each kind its filter names, no more and no less. */
    STOCK,
    /** Opens the inventory to the network, which keeps items in it and takes them out as if it were a drive. */
    WINDOW;

    public final String id = name().toLowerCase(Locale.ROOT);

    @Override
    public String getSerializedName() {
        return id;
    }
}
