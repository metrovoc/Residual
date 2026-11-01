package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.refine.TokenSupply;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

/** A tap on a Token Tank: filled there, it draws on all the tank holds. In a Model Core's data slots, it is practice for Reading. */
public class TokenCellItem extends Item {
    public TokenCellItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static TokenSupply supply(ItemStack stack) {
        return stack.get(ModComponents.TOKEN_SUPPLY.get());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return supply(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        TokenSupply s = supply(stack);
        if (s == null) {
            out.accept(Component.translatable("residual.token_cell.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        out.accept(Component.translatable("residual.token_cell.tokens", String.format(Locale.ROOT, "%,d", s.tokens()),
                String.format(Locale.ROOT, "%,d", s.characters())).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("residual.token_cell.docs", String.format(Locale.ROOT, "%,d", s.docs())).withStyle(ChatFormatting.DARK_GRAY));
    }
}
