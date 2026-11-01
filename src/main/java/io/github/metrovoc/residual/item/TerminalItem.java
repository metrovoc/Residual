package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.client.ClientHooks;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.world.Relays;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A handheld line to a Model Server, and through it to the base: use it, anywhere the network reaches, to see the network
 * on a map, ask what the base holds and what its machines and agents are doing, tell them what to do, and write with the
 * served model. While it is in hand, orders said to an agent anywhere the network reaches get through.
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

    /** Whether a Terminal in the player's hands reaches its server from where they stand, and that server's network the place {@code at}. */
    public static boolean reaches(ServerPlayer player, GlobalPos at) {
        ItemStack terminal = held(player);
        return terminal != null && ModelLink.of(player.level(), player.blockPosition(), terminal) instanceof ModelLink.Served served
                && Relays.of(player.level().getServer()).covers(served.where(), at.dimension(), at.pos());
    }
}
