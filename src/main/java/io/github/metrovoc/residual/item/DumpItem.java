package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.registry.ModComponents;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

/**
 * What a fallen mast's box held: all that passed through its relay, repeats and bad lines included. It holds more than any drive
 * and most of it is worth little until it has been refined. A dump settles on what it holds the first time it is carried.
 */
public class DumpItem extends Item {
    public DumpItem(Properties properties) {
        super(properties);
    }

    /** What it holds, as the seed its text is written from. */
    public static long seed(ItemStack stack, net.minecraft.util.RandomSource rng) {
        Long seed = stack.get(ModComponents.DUMP.get());
        if (seed == null) {
            seed = rng.nextLong();
            stack.set(ModComponents.DUMP.get(), seed);
        }
        return seed;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        seed(stack, level.getRandom());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("residual.relay_dump.raw").withStyle(ChatFormatting.GRAY));
        Long seed = stack.get(ModComponents.DUMP.get());
        if (seed != null) {
            out.accept(Component.translatable("residual.relay_dump.from", String.format(Locale.ROOT, "%04X", seed & 0xFFFF)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
