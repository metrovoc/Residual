package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Something tuned to a Model Server by using it on one: a Relay Card for an agent's model slot, or a Terminal. */
public class LinkItem extends Item {
    public LinkItem(Properties properties) {
        super(properties);
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
    }
}
