package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.item.WayfinderItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Registers the mod's packets and handles the ones sent from screens. */
public final class Network {
    private Network() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(FindRuin.TYPE, FindRuin.STREAM_CODEC, Network::onFindRuin);
    }

    private static void onFindRuin(FindRuin f, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player) {
            WayfinderItem.find(player, f.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, f.kind());
        }
    }
}
