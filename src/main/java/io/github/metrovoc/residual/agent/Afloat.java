package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.net.StepSync;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What floats over a body at work: the step it is on, as its program writes it. A body looks twice a second, tells the
 * players near it only when that has changed, and tells a player who has just come near once. Nobody else hears of it, so a
 * base far from anyone costs a comparison, and a body whose step stays the same costs nothing at all.
 */
public final class Afloat {
    /** Ticks between two looks at what it is on, so a loop that goes round every tick shows as one step at a time. */
    public static final int EVERY = 10;
    /** How near a player is told: a little further than a label can be read, so it is there as they come into range. */
    private static final double TOLD = 20;

    /** It is working on something; it is held in the debugger; it is waiting, or listening for what it may yet do. */
    public static final int ACTING = 0, HELD = 1, WAITING = 2;

    /** The step it is on, and how it stands. */
    public record Step(String text, int kind) {
        static final Step NONE = new Step("", WAITING);
    }

    private Step step = Step.NONE;
    private int more;
    private final List<UUID> told = new ArrayList<>(1);

    /** Looks at what it is on now: {@code entity} and {@code pos} say who it is, and {@code at} where. */
    void update(ServerLevel level, Vec3 at, int entity, BlockPos pos, Step now, int others) {
        boolean changed = !now.equals(step) || others != more;
        step = now;
        more = others;
        List<ServerPlayer> players = level.players();
        for (int i = told.size() - 1; i >= 0; i--) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(told.get(i));
            if (p == null || p.level() != level || p.position().distanceToSqr(at) > TOLD * TOLD) {
                told.remove(i);
            }
        }
        for (ServerPlayer p : players) {
            if (p.position().distanceToSqr(at) <= TOLD * TOLD) {
                boolean known = told.contains(p.getUUID());
                if (!known) {
                    told.add(p.getUUID());
                }
                // nothing to say to someone who has never been told anything
                if (known ? changed : !now.text().isEmpty()) {
                    PacketDistributor.sendToPlayer(p, new StepSync(entity, pos, Running.clip(now.text()), now.kind(), others));
                }
            }
        }
    }
}
