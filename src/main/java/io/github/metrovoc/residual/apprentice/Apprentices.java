package io.github.metrovoc.residual.apprentice;

import io.github.metrovoc.residual.item.TerminalItem;
import io.github.metrovoc.residual.mind.MindLink;
import io.github.metrovoc.residual.registry.ModEntities;
import io.github.metrovoc.residual.world.Relays;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.ServerChatEvent;

/** Orders said in chat: an apprentice takes the ones that start with its name. */
public final class Apprentices {
    /** How far an apprentice hears an order said out loud. */
    public static final int HEARING = 24;

    private Apprentices() {
    }

    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String said = event.getRawText().strip();
        String name = said.split("[ ,:;!.?]", 2)[0];
        if (name.isEmpty()) {
            return;
        }
        ServerLevel level = player.level();
        MindLink.Served line = line(player);
        for (Apprentice a : level.getEntities(ModEntities.APPRENTICE.get(), a -> a.getName().getString().equalsIgnoreCase(name))) {
            boolean heard = a.distanceToSqr(player) <= HEARING * HEARING;
            boolean relayed = line != null && Relays.covers(level, line.server().getBlockPos(), a.blockPosition());
            if (heard || relayed) {
                a.order(player, said);
            }
        }
    }

    /** The Model Server a Terminal in the player's hands reaches from where they stand, if any. */
    private static MindLink.Served line(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof TerminalItem && MindLink.of(player.level(), player.blockPosition(), held) instanceof MindLink.Served s) {
                return s;
            }
        }
        return null;
    }
}
