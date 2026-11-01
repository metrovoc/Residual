package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.agent.Upgrades;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Makes a body that runs programs faster, stronger or longer in reach. Goes in the upgrade slots of an agent or a Port. */
public class UpgradeItem extends Item {
    public final Upgrades.Kind kind;

    public UpgradeItem(Upgrades.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("item.residual." + kind.name().toLowerCase(java.util.Locale.ROOT) + "_upgrade.help", kind.most).withStyle(ChatFormatting.GRAY));
    }
}
