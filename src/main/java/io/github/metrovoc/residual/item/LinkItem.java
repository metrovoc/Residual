package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.block.GateBlock;
import io.github.metrovoc.residual.block.ServerBlockEntity;
import io.github.metrovoc.residual.net.ServerLoad;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.world.Relays;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

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

    /** Held, it says how its server is doing, once a second, if the server is loaded. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        GlobalPos link = stack.get(ModComponents.LINK.get());
        if (link != null && entity instanceof ServerPlayer player && slot != null && slot.getType() == EquipmentSlot.Type.HAND && level.getGameTime() % 20 == 0
                && link.dimension().equals(level.dimension()) && level.isLoaded(link.pos()) && level.getBlockEntity(link.pos()) instanceof ServerBlockEntity server) {
            PacketDistributor.sendToPlayer(player, new ServerLoad(server.rate(), server.load(), server.waiting(), server.full()));
        }
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
