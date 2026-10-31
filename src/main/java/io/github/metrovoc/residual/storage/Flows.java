package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.net.FlowSync;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

/** Tells players near a storage block that it moved items, so they see them travel along the links. */
public final class Flows {
    /** How close a player has to be to see a flow. */
    private static final double SEEN = 48;
    /** Ticks between two flows from the same block. */
    public static final int EVERY = 8;

    private Flows() {
    }

    /** {@code count} items went between {@code from} and the network's store at {@code to}: in, or out of it. */
    public static void send(ServerLevel level, BlockPos from, BlockPos to, boolean in, int count) {
        if (to != null && count > 0 && !to.equals(from)) {
            PacketDistributor.sendToPlayersNear(level, null, from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5, SEEN,
                    new FlowSync(from, to, in, count));
        }
    }
}
