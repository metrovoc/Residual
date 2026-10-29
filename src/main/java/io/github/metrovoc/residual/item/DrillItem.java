package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.learn.Ability;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Endless practice for one ability. In a Model Core's data slots, it is what the mind trains on. */
public class DrillItem extends Item {
    public final Ability ability;

    public DrillItem(Ability ability, Properties properties) {
        super(properties);
        this.ability = ability;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("residual.drill.practises", Component.translatable("residual.ability." + ability.id))
                .withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("residual.ability." + ability.id + ".job").withStyle(ChatFormatting.DARK_GRAY));
    }
}
