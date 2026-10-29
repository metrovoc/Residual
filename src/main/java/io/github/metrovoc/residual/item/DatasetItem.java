package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.learn.Benchmark;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Endless practice for one benchmark. In a Model Core's data slots, it is what the model trains on. */
public class DatasetItem extends Item {
    public final Benchmark benchmark;

    public DatasetItem(Benchmark benchmark, Properties properties) {
        super(properties);
        this.benchmark = benchmark;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("residual.dataset.practises", Component.translatable("residual.benchmark." + benchmark.id))
                .withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("residual.benchmark." + benchmark.id + ".job").withStyle(ChatFormatting.DARK_GRAY));
    }
}
