package io.github.metrovoc.residual.item;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Found in the ruins. Recipes use it as a pattern to copy: it stays in the crafting grid. */
public class SchematicItem extends Item {
    /** What each schematic is the plan for, by translation key. */
    private static final Map<String, List<String>> PLANS = Map.of(
            "schematic_relays", List.of("block.residual.model_server", "block.residual.relay", "item.residual.relay_card"));

    public SchematicItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
        return new ItemStackTemplate(this);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("residual.schematic.plans").withStyle(ChatFormatting.GRAY));
        for (String key : PLANS.getOrDefault(BuiltInRegistries.ITEM.getKey(this).getPath(), List.of())) {
            out.accept(Component.literal("  ").append(Component.translatable(key)).withStyle(ChatFormatting.DARK_GRAY));
        }
        out.accept(Component.translatable("residual.schematic.kept").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
