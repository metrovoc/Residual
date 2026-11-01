package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.client.ClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A handheld line to a Model Server: use it to see the relay network on a map, go to a gate on it from anywhere it reaches,
 * and write with the served model. While it is in hand, orders said to an agent anywhere the network reaches get through.
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

    /** The Terminal in the player's hands, the main hand first, or null. */
    @Nullable
    public static ItemStack held(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof TerminalItem) {
                return stack;
            }
        }
        return null;
    }
}
