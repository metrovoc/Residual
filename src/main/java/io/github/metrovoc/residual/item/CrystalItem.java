package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

/** Holds a saved model: put it in a Model Core's crystal slot and save. Its tooltip is the model's report card. */
public class CrystalItem extends Item {
    public CrystalItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static ModelInfo model(ItemStack stack) {
        return stack.get(ModComponents.MODEL.get());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return model(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        ModelInfo model = model(stack);
        if (model == null) {
            out.accept(Component.translatable("residual.crystal.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        out.accept(Component.translatable("residual.crystal.model", model.tag(), String.format(Locale.ROOT, "%,d", model.parameters()),
                String.format(Locale.ROOT, "%,d", model.steps())).withStyle(ChatFormatting.GRAY));
        if (!model.causal()) {
            out.accept(Component.translatable("residual.crystal.peeks").withStyle(ChatFormatting.RED));
        }
        for (Benchmark a : Benchmark.values()) {
            out.accept(score(model, a));
        }
    }

    /** One line of the report card: the benchmark, its score and whether it passes. */
    public static MutableComponent score(ModelInfo model, Benchmark a) {
        float s = model.score(a);
        boolean pass = model.passes(a);
        String value = Float.isNaN(s) ? "–" : a.metric == io.github.metrovoc.residual.learn.Task.Metric.LOSS
                ? String.format(Locale.ROOT, "%.2f", s) : String.format(Locale.ROOT, "%.0f%%", 100 * s);
        return Component.literal(pass ? " ✔ " : " · ").withStyle(pass ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                .append(Component.translatable("residual.benchmark." + a.id).withStyle(pass ? ChatFormatting.WHITE : ChatFormatting.GRAY))
                .append(Component.literal(" " + value).withStyle(ChatFormatting.DARK_GRAY));
    }
}
