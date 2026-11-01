package io.github.metrovoc.residual.storage;

import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** The search slot of something carried: a Wireless Terminal or a Portable Drive keeps the model in it on itself. */
final class HeldModel extends SimpleContainer {
    private final Supplier<ItemStack> holder;

    HeldModel(ItemStack holding, Supplier<ItemStack> holder) {
        super(1);
        this.holder = holder;
        ItemContainerContents contents = holding.get(DataComponents.CONTAINER);
        if (contents != null) {
            contents.copyInto(getItems());
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
        ItemStack holding = holder.get();
        if (holding != null) {
            holding.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(getItems()));
        }
    }
}
