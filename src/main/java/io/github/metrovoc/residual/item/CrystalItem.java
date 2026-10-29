package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.learn.Ability;
import io.github.metrovoc.residual.mind.MindInfo;
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

/** Holds a saved mind: put it in a Model Core's crystal slot and save. Its tooltip is the mind's report card. */
public class CrystalItem extends Item {
    public CrystalItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static MindInfo mind(ItemStack stack) {
        return stack.get(ModComponents.MIND.get());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return mind(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        MindInfo mind = mind(stack);
        if (mind == null) {
            out.accept(Component.translatable("residual.crystal.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        out.accept(Component.translatable("residual.crystal.mind", mind.tag(), String.format(Locale.ROOT, "%,d", mind.parameters()),
                String.format(Locale.ROOT, "%,d", mind.steps())).withStyle(ChatFormatting.GRAY));
        if (!mind.causal()) {
            out.accept(Component.translatable("residual.crystal.peeks").withStyle(ChatFormatting.RED));
        }
        for (Ability a : Ability.values()) {
            out.accept(score(mind, a));
        }
    }

    /** One line of the report card: the ability, its score and whether it passes. */
    public static MutableComponent score(MindInfo mind, Ability a) {
        float s = mind.score(a);
        boolean pass = mind.passes(a);
        String value = Float.isNaN(s) ? "–" : a.metric == io.github.metrovoc.residual.learn.Task.Metric.LOSS
                ? String.format(Locale.ROOT, "%.2f", s) : String.format(Locale.ROOT, "%.0f%%", 100 * s);
        return Component.literal(pass ? " ✔ " : " · ").withStyle(pass ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                .append(Component.translatable("residual.ability." + a.id).withStyle(pass ? ChatFormatting.WHITE : ChatFormatting.GRAY))
                .append(Component.literal(" " + value).withStyle(ChatFormatting.DARK_GRAY));
    }
}
