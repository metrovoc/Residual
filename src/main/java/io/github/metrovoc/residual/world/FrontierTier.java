package io.github.metrovoc.residual.world;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Whether a base has reached the Frontier tier, which only a frontier run can show. What decides it plugs in here with
 * {@link #decidedBy}; until something does, no base has.
 */
public final class FrontierTier {
    /** Says whether the base of {@code owner}, or of no one in particular, has reached the Frontier tier. */
    @FunctionalInterface
    public interface Gate {
        boolean reached(ServerLevel level, @Nullable UUID owner);
    }

    private static Gate gate = (level, owner) -> false;

    private FrontierTier() {
    }

    public static void decidedBy(Gate decides) {
        gate = decides;
    }

    public static boolean reached(ServerLevel level, @Nullable UUID owner) {
        return gate.reached(level, owner);
    }
}
