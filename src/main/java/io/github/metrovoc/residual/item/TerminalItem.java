package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.client.ClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A handheld line to a Model Server. Use it to write with the served mind; while it is in hand, orders said to an
 * apprentice anywhere the network reaches get through.
 */
public class TerminalItem extends LinkItem {
    public TerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            ClientHooks.openTerminal(hand);
        }
        return InteractionResult.SUCCESS;
    }
}
