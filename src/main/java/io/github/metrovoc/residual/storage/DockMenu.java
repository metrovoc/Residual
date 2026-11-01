package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.item.StorageDriveItem;
import io.github.metrovoc.residual.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A Drive Dock's bays, what goes in on the left and what is done on the right, and the player's inventory. */
public class DockMenu extends AbstractContainerMenu {
    public static final int IN_X = 26, OUT_X = 98, BAYS_Y = 24, INVENTORY_X = 8, INVENTORY_Y = 84;
    /** Its mode's button, in the toolbar beside the window. */
    public static final int TOOLBAR_X = 178, TOOLBAR_Y = 4;

    public final BlockPos pos;
    public final ContainerData data;

    public DockMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(DockBlockEntity.SLOTS), new SimpleContainerData(DockBlockEntity.DATA));
    }

    public DockMenu(int id, Inventory inventory, BlockPos pos, Container drives, ContainerData data) {
        super(ModMenus.DOCK.get(), id);
        this.pos = pos;
        this.data = data;
        for (int i = 0; i < DockBlockEntity.SLOTS; i++) {
            int x = (i < DockBlockEntity.BAYS ? IN_X : OUT_X) + i % DockBlockEntity.BAYS % 3 * 18;
            addSlot(new Slot(drives, i, x, BAYS_Y + i % DockBlockEntity.BAYS / 3 * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof StorageDriveItem;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
        addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof DockBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }

    /** Items moved in the last second: a menu's numbers travel as shorts, and 40,960 fits one only read without a sign. */
    public int moved() {
        return data.get(DockBlockEntity.MOVED) & 0xFFFF;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        int own = DockBlockEntity.SLOTS;
        if (index < own ? !moveItemStackTo(stack, own, slots.size(), true)
                : !(stack.getItem() instanceof StorageDriveItem) || !moveItemStackTo(stack, 0, DockBlockEntity.BAYS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }
}
