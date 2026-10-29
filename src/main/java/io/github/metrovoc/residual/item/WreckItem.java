package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.learn.Wrecks;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

/** A broken mind from the ruins. In a Model Core's data slots it can be rebuilt in front of the Core and repaired. */
public class WreckItem extends Item {
    public WreckItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static Wrecks.Wreck wreck(ItemStack stack) {
        String id = stack.get(ModComponents.WRECK.get());
        return id == null ? null : Wrecks.byId(id);
    }

    public static ItemStack of(Item item, Wrecks.Wreck wreck) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModComponents.WRECK.get(), wreck.id());
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        Wrecks.Wreck w = wreck(stack);
        if (w == null) {
            return;
        }
        out.accept(Component.translatable("residual.wreck." + w.id() + ".title").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        out.accept(Component.translatable("residual.wreck.ability", Component.translatable("residual.ability." + w.ability().id))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
