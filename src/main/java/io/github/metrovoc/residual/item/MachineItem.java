package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.bench.Specs;
import io.github.metrovoc.residual.block.OperatorBlock;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A machine as an item, telling whether it was fabricated from a verified design or a salvaged one. */
public class MachineItem extends BlockItem {
    private final OperatorBlock block;

    public MachineItem(OperatorBlock block, Properties properties) {
        super(block, properties);
        this.block = block;
    }

    public Op op() {
        return block.op;
    }

    public static boolean fused(ItemStack stack) {
        return stack.getOrDefault(ModComponents.FUSED.get(), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        if (Specs.unlocking(block.op) == null) {
            return;
        }
        out.accept(fused(stack)
                ? Component.translatable("residual.machine.fused").withStyle(ChatFormatting.AQUA)
                : Component.translatable("residual.machine.unfused").withStyle(ChatFormatting.GOLD));
    }
}
