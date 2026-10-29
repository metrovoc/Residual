package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A vocabulary. Blank, it holds only the characters, and models read and write one character at a time; a BPE Forge
 * writes the merges it learns onto it. In a Model Core's tokenizer slot, it is what the model reads and writes with.
 */
public class TokenizerItem extends Item {
    public TokenizerItem(Properties properties) {
        super(properties);
    }

    /** The vocabulary it holds: just the characters when it is blank. */
    public static Vocab vocab(ItemStack stack) {
        return stack.getOrDefault(ModComponents.VOCAB.get(), Vocab.WORLD);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        Vocab v = vocab(stack);
        if (v.merges() == 0) {
            out.accept(Component.translatable("residual.tokenizer.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        out.accept(Component.translatable("residual.tokenizer.size", v.size(), Vocab.CHARACTERS, v.merges()).withStyle(ChatFormatting.GRAY));
        List<String> longest = new ArrayList<>();
        IntStream.range(Vocab.CHARACTERS, v.size()).boxed()
                .sorted(Comparator.comparingInt((Integer id) -> v.piece(id).length()).reversed())
                .limit(3).forEach(id -> longest.add(v.label(id)));
        out.accept(Component.translatable("residual.tokenizer.longest", String.join("  ", longest)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
