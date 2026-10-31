package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A Port's filter, its settings and the player's inventory. The filter's slots hold a picture of a kind, not the items:
 * clicking one with a stack names that kind, and clicking it empty-handed clears it.
 */
public class PortMenu extends AbstractContainerMenu {
    public static final int FILTER_X = 8, FILTER_Y = 46, INVENTORY_X = 8, INVENTORY_Y = 104;

    public final BlockPos pos;
    public final ContainerData data;
    private final Container filter;

    public PortMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(PortBlockEntity.FILTERS), new SimpleContainerData(PortBlockEntity.DATA));
    }

    public PortMenu(int id, Inventory inventory, BlockPos pos, Container filter, ContainerData data) {
        super(ModMenus.PORT.get(), id);
        this.pos = pos;
        this.data = data;
        this.filter = filter;
        for (int i = 0; i < PortBlockEntity.FILTERS; i++) {
            addSlot(new Slot(filter, i, FILTER_X + i * 18, FILTER_Y) {
                @Override
                public boolean mayPickup(Player player) {
                    return false;
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
    public void clicked(int index, int button, ContainerInput input, Player player) {
        if (index >= 0 && index < PortBlockEntity.FILTERS) {
            ItemStack carried = getCarried();
            filter.setItem(index, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            return;
        }
        super.clicked(index, button, input, player);
    }

    /** Shift-clicking a stack in the inventory names its kind in the first free filter slot. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < PortBlockEntity.FILTERS) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int free = -1;
        for (int i = PortBlockEntity.FILTERS - 1; i >= 0; i--) {
            ItemStack f = filter.getItem(i);
            if (ItemStack.isSameItemSameComponents(f, stack)) {
                return ItemStack.EMPTY;
            }
            if (f.isEmpty()) {
                free = i;
            }
        }
        if (free >= 0) {
            filter.setItem(free, stack.copyWithCount(1));
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof PortBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }
}
