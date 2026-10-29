package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * What a Forecaster has watched a field do: one line per crop, its age at every look. In a Model Core's data slot it
 * is practice for Forecasting.
 */
public class FieldLogItem extends Item {
    /** The most a log holds; a Forecaster keeps its newest lines. */
    public static final int CAPACITY = 16_000;

    public FieldLogItem(Properties properties) {
        super(properties);
    }

    public static String log(ItemStack stack) {
        return stack.getOrDefault(ModComponents.FIELD_LOG.get(), "");
    }

    /** Looks that follow another look at the same crop, and crops. */
    public static int[] counts(String log) {
        int looks = 0, crops = 0;
        for (int i = 0; i < log.length(); i++) {
            if (log.charAt(i) == '\n') {
                crops++;
            } else if (i > 0 && log.charAt(i - 1) != '\n') {
                looks++;
            }
        }
        return new int[]{looks, crops};
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        String log = log(stack);
        if (log.isEmpty()) {
            out.accept(Component.translatable("residual.field_log.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        int[] c = counts(log);
        out.accept(Component.translatable("residual.field_log.looks", c[0], c[1]).withStyle(ChatFormatting.GRAY));
    }
}
