package io.github.metrovoc.residual.compute;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** How a cluster and its numbers read to a player. */
public final class ClusterStatus {
    private ClusterStatus() {
    }

    /** One line about a cluster for the action bar. */
    public static Component line(Cluster c) {
        Accounts.Capacity at = c.capacity();
        Component line = Component.translatable("residual.cluster.status", c.racks().size(), at.devices(), flops(at.flopsPerSecond()), bytes(at.memoryBytes()));
        if (c.racksDark() > 0) {
            return line.copy().append(Component.translatable(c.pods() == 0 ? "residual.cluster.needs_pod" : "residual.cluster.needs_hall", c.racksDark()));
        }
        return line;
    }

    /** A number with its prefix and a place for its unit straight after: 412 M, 3.2 G; and with no prefix, a space. */
    public static String flops(double flops) {
        return scaled(flops, 1000, " ", " k", " M", " G", " T", " P", " E", " Z", " Y");
    }

    public static String bytes(double bytes) {
        return scaled(bytes, 1024, " B", " KiB", " MiB", " GiB", " TiB", " PiB");
    }

    private static String scaled(double value, double step, String... units) {
        int i = 0;
        while (value >= 999.5 && i < units.length - 1) {
            value /= step;
            i++;
        }
        return String.format(Locale.ROOT, value >= 100 || i == 0 ? "%.0f%s" : "%.1f%s", value, units[i]);
    }
}
