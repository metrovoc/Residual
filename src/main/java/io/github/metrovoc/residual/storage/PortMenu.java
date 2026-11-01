package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.FieldLogItem;
import io.github.metrovoc.residual.item.LinkItem;
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
 * A Port's filter, its filter model, its settings and the player's inventory. The filter's slots hold a picture of a
 * kind, not the items: clicking one with a stack names that kind, and clicking it empty-handed clears it. The filter
 * model's slot sits in a panel beside the window, with its target model's, its Field Log's and the toggle for asking.
 */
public class PortMenu extends AbstractContainerMenu {
    public static final int FILTER_X = 8, FILTER_Y = 40, INVENTORY_X = 8, INVENTORY_Y = 108;
    public static final int SIDE_X = 172, SIDE_Y = 4, SIDE_W = 47, SIDE_H = 48, MODEL_X = 179, MODEL_Y = 10;
    /** Beside the filter model's slot, the target model's; under them, the toggle for asking and the Field Log's slot. */
    public static final int TARGET_X = 197, TARGET_Y = 10, CRAFT_X = 178, CRAFT_Y = 30, LOG_X = 197, LOG_Y = 31;
    /** The side slots' indices, after the filter's. */
    public static final int MODEL = PortBlockEntity.FILTERS, TARGET = MODEL + 1, LOG = MODEL + 2;

    public final BlockPos pos;
    public final ContainerData data;
    private final Container filter;

    public PortMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(PortBlockEntity.FILTERS), new SimpleContainer(1), new SimpleContainer(2),
                new SimpleContainerData(PortBlockEntity.DATA));
    }

    public PortMenu(int id, Inventory inventory, BlockPos pos, Container filter, Container model, Container extras, ContainerData data) {
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
        addSlot(new Slot(model, 0, MODEL_X, MODEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isModel(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(extras, PortBlockEntity.TARGET, TARGET_X, TARGET_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isModel(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(extras, PortBlockEntity.LOG, LOG_X, LOG_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof FieldLogItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
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

    /** A checkpoint or relay card, what a filter model comes in. */
    static boolean isModel(ItemStack stack) {
        return CrystalItem.model(stack) != null || stack.getItem() instanceof LinkItem;
    }

    /**
     * Shift-clicking a model in the inventory puts it in the first free model slot, a Field Log in its slot, and either
     * back; any other stack names its kind in the first free filter slot.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < PortBlockEntity.FILTERS) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slots.get(index).getItem();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (index <= LOG) {
            moveItemStackTo(stack, LOG + 1, slots.size(), true);
            slots.get(index).setChanged();
            return ItemStack.EMPTY;
        }
        if (isModel(stack) && !(slots.get(MODEL).hasItem() && slots.get(TARGET).hasItem())
                || stack.getItem() instanceof FieldLogItem && !slots.get(LOG).hasItem()) {
            moveItemStackTo(stack, MODEL, LOG + 1, false);
            slots.get(index).setChanged();
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
