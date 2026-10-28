package io.github.metrovoc.residual.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStackTemplate;

/** Awarded for passing a lesson. Recipes use it as a catalyst: it stays in the crafting grid. */
public class InsightItem extends Item {
    public InsightItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
        return new ItemStackTemplate(this);
    }
}
