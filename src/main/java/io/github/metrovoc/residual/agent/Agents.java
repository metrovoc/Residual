package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.item.TerminalItem;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.registry.ModEntities;
import io.github.metrovoc.residual.world.Relays;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.ServerChatEvent;

/** Orders said in chat: an agent takes the ones that start with its name. */
public final class Agents {
    /** How far an agent hears an order said out loud. */
    public static final int HEARING = 24;

    private Agents() {
    }

    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String said = event.getRawText().strip();
        String name = said.split("[ ,:;!.?]", 2)[0];
        if (name.isEmpty()) {
            return;
        }
        ServerLevel level = player.level();
        ModelLink.Served line = line(player);
        for (Agent a : level.getEntities(ModEntities.AGENT.get(), a -> a.getName().getString().equalsIgnoreCase(name))) {
            boolean heard = a.distanceToSqr(player) <= HEARING * HEARING;
            boolean relayed = line != null && Relays.covers(level, line.server().getBlockPos(), a.blockPosition());
            if (heard || relayed) {
                a.order(player, said);
            }
        }
    }

    /** The Model Server a Terminal in the player's hands reaches from where they stand, if any. */
    private static ModelLink.Served line(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof TerminalItem && ModelLink.of(player.level(), player.blockPosition(), held) instanceof ModelLink.Served s) {
                return s;
            }
        }
        return null;
    }
}
