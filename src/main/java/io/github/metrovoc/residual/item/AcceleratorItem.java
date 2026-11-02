package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.block.AcceleratorBlock;
import io.github.metrovoc.residual.compute.ClusterStatus;
import io.github.metrovoc.residual.compute.ProcessNode;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** An Accelerator as an item, which says what its process node makes it. */
public class AcceleratorItem extends BlockItem {
    private final ProcessNode node;

    public AcceleratorItem(AcceleratorBlock block, Properties properties) {
        super(block, properties);
        this.node = block.node;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("residual.accelerator.tooltip", node.nanometres()).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("residual.accelerator.tooltip_numbers", ClusterStatus.flops(node.flopsPerSecond()),
                ClusterStatus.bytes(node.memoryBytes()), node.fePerTick()).withStyle(ChatFormatting.GRAY));
    }
}
