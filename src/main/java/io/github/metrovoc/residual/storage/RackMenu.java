package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.item.StorageDriveItem;
import io.github.metrovoc.residual.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A Drive Rack's bays and the player's inventory. */
public class RackMenu extends AbstractContainerMenu {
    public static final int BAYS_X = 53, BAYS_Y = 24, INVENTORY_X = 8, INVENTORY_Y = 76;

    public final BlockPos pos;
    private final Container drives;

    public RackMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(RackBlockEntity.BAYS));
    }

    public RackMenu(int id, Inventory inventory, BlockPos pos, Container drives) {
        super(ModMenus.RACK.get(), id);
        this.pos = pos;
        this.drives = drives;
        for (int i = 0; i < RackBlockEntity.BAYS; i++) {
            addSlot(new Slot(drives, i, BAYS_X + i % 4 * 18, BAYS_Y + i / 4 * 18) {
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
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof RackBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        int own = RackBlockEntity.BAYS;
        if (index < own ? !moveItemStackTo(stack, own, slots.size(), true)
                : !(stack.getItem() instanceof StorageDriveItem) || !moveItemStackTo(stack, 0, own, false)) {
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
