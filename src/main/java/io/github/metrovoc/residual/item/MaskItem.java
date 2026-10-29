package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.bench.MaskData;
import io.github.metrovoc.residual.bench.Spec;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

/**
 * A photomask: blank until a Bench records a verified design onto it, or found in a ruin with a salvaged one. The
 * Fabricator lays the design down; the mask itself is never used up.
 */
public class MaskItem extends Item {
    public MaskItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static MaskData mask(ItemStack stack) {
        return stack.get(ModComponents.MASK.get());
    }

    public static ItemStack of(Item item, MaskData data) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModComponents.MASK.get(), data);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        MaskData m = mask(stack);
        if (m == null || m.specOrNull() == null) {
            return Component.translatable("item.residual.photomask.blank");
        }
        return Component.translatable("item.residual.photomask.of", Component.translatable("residual.spec." + m.spec()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        MaskData m = mask(stack);
        if (m == null || m.specOrNull() == null) {
            out.accept(Component.translatable("residual.mask.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        Spec spec = m.specOrNull();
        if (m.salvaged()) {
            out.accept(Component.translatable("residual.mask.salvaged").withStyle(ChatFormatting.GOLD));
        } else {
            out.accept(Component.translatable("residual.mask.verified", m.author()).withStyle(ChatFormatting.AQUA));
        }
        out.accept(Component.translatable("residual.mask.area", m.area(), spec.area()).withStyle(ChatFormatting.GRAY));
        for (var op : spec.unlocks()) {
            out.accept(Component.translatable("residual.mask.makes", Component.translatable("block.residual." + op.id))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (spec.id().equals("matmul")) {
            out.accept(Component.translatable("residual.mask.makes", Component.translatable("block.residual.accelerator"))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
