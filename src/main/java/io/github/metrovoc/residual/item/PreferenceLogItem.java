package io.github.metrovoc.residual.item;

import io.github.metrovoc.residual.learn.Preferences;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A player's choices between two answers, kept for a Tuner to learn from. Each entry is a prompt, what was chosen and what was not, a line each. */
public class PreferenceLogItem extends Item {
    /** The most choices a log keeps; after that the oldest go. */
    public static final int CAPACITY = 512;

    public PreferenceLogItem(Properties properties) {
        super(properties);
    }

    public static List<Preferences.Pair> pairs(ItemStack stack) {
        List<String> lines = stack.getOrDefault(ModComponents.PREFERENCES.get(), List.of());
        List<Preferences.Pair> out = new ArrayList<>();
        for (int i = 0; i + 2 < lines.size(); i += 3) {
            out.add(new Preferences.Pair(lines.get(i), lines.get(i + 1), lines.get(i + 2)));
        }
        return out;
    }

    public static void add(ItemStack stack, Preferences.Pair pair) {
        List<String> lines = new ArrayList<>(stack.getOrDefault(ModComponents.PREFERENCES.get(), List.of()));
        lines.add(pair.prompt());
        lines.add(pair.chosen());
        lines.add(pair.rejected());
        while (lines.size() > 3 * CAPACITY) {
            lines.subList(0, 3).clear();
        }
        stack.set(ModComponents.PREFERENCES.get(), List.copyOf(lines));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        int n = pairs(stack).size();
        out.accept((n == 0 ? Component.translatable("residual.preference_log.blank") : Component.translatable("residual.preference_log.choices", n)).withStyle(ChatFormatting.GRAY));
    }
}
