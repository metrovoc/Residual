package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.storage.WirelessHost;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** A Storage Terminal in the hand: tuned to an Access Point, it opens that network's screen as far as the network reaches. */
public class WirelessTerminalItem extends Item {
    public WirelessTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            WirelessHost.open(server, player.getItemInHand(hand));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModComponents.LINK.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        GlobalPos link = stack.get(ModComponents.LINK.get());
        out.accept(link == null ? Component.translatable("residual.wireless.none").withStyle(ChatFormatting.GRAY)
                : Component.translatable("residual.wireless.to", link.pos().toShortString()).withStyle(ChatFormatting.GRAY));
    }
}
