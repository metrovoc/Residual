package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.world.Relays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** How a body reaches beyond itself: through the Model Server a Relay Card among its upgrades is tuned to. */
public final class Reach {
    private Reach() {
    }

    /** Whether the relay network carries for a body at {@code at}: its card is tuned, and that server covers it, or it stands in another dimension's relays. */
    static boolean relayed(ServerLevel level, BlockPos at, Container upgrades) {
        GlobalPos link = Upgrades.relay(upgrades).get(ModComponents.LINK.get());
        if (link == null) {
            return false;
        }
        return link.dimension().equals(level.dimension()) ? Relays.covers(level, link.pos(), at) : Relays.relayNear(level, at);
    }

    /** Whether that network covers a place in a level. */
    static boolean covered(ServerLevel level, Container upgrades, BlockPos target) {
        GlobalPos link = Upgrades.relay(upgrades).get(ModComponents.LINK.get());
        if (link == null) {
            return false;
        }
        return link.dimension().equals(level.dimension()) ? Relays.covers(level, link.pos(), target) : Relays.relayNear(level, target);
    }

    /** Takes energy from a battery, if it holds that much. */
    static boolean spend(EnergyHandler battery, int energy) {
        try (Transaction tx = Transaction.openRoot()) {
            if (battery.extract(energy, tx) == energy) {
                tx.commit();
                return true;
            }
        }
        return false;
    }
}
