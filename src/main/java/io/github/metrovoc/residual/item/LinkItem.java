package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.block.GateBlock;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.world.Relays;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.TooltipDisplay;

/** Something tuned to a Model Server by using it on one: a Relay Card for an agent's model slot, or a Terminal. */
public class LinkItem extends Item {
    public LinkItem(Properties properties) {
        super(properties);
    }

    /** Sneaking, a Relay Card cuts the gate it is used on from the one it is tuned to. */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!ctx.isSecondaryUseActive() || !ctx.getItemInHand().is(ModItems.RELAY_CARD.get())
                || !(ctx.getLevel().getBlockState(ctx.getClickedPos()).getBlock() instanceof GateBlock)) {
            return InteractionResult.PASS;
        }
        if (ctx.getLevel() instanceof ServerLevel server && ctx.getPlayer() != null) {
            Relays.of(server.getServer()).untune(GlobalPos.of(server.dimension(), ctx.getClickedPos()));
            ctx.getPlayer().sendOverlayMessage(Component.translatable("residual.relay_gate.untuned"));
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
        out.accept(link == null ? Component.translatable("residual.link.none").withStyle(ChatFormatting.GRAY)
                : Component.translatable("residual.link.to", link.pos().toShortString()).withStyle(ChatFormatting.GRAY));
        GlobalPos gate = stack.get(ModComponents.GATE.get());
        if (gate != null) {
            out.accept(Component.translatable("residual.link.gate", gate.pos().toShortString()).withStyle(ChatFormatting.GRAY));
        }
    }
}
