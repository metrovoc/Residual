package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.storage.PortableHost;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** A Storage Drive that opens a terminal's screen on itself, in the hand, with no rack and no network; in a rack or a Dock it works like any other drive. */
public class PortableDriveItem extends StorageDriveItem {
    public PortableDriveItem(long capacity, Properties properties) {
        super(capacity, properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            PortableHost.open(server, player.getItemInHand(hand));
        }
        return InteractionResult.SUCCESS;
    }
}
