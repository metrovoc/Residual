package io.github.metrovoc.residual.menu;

import io.github.metrovoc.residual.block.ForecasterBlockEntity;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.FieldLogItem;
import io.github.metrovoc.residual.item.LinkItem;
import io.github.metrovoc.residual.registry.ModMenus;
import java.util.function.Predicate;
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

/** The Forecaster's screen: the log it writes, the model it runs, and the player's inventory. */
public class ForecasterMenu extends AbstractContainerMenu {
    public static final int LOG_X = 12, MODEL_X = 12, LOG_Y = 30, MODEL_Y = 74;
    public static final int INVENTORY_X = 48, INVENTORY_Y = 152;

    public final BlockPos pos;
    public final ContainerData data;
    private final Container items;

    public ForecasterMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(ForecasterBlockEntity.SLOTS), new SimpleContainerData(ForecasterBlockEntity.DATA));
    }

    public ForecasterMenu(int id, Inventory inventory, BlockPos pos, Container items, ContainerData data) {
        super(ModMenus.FORECASTER.get(), id);
        this.pos = pos;
        this.data = data;
        this.items = items;
        addSlot(only(ForecasterBlockEntity.LOG, LOG_X, LOG_Y, s -> s.getItem() instanceof FieldLogItem));
        addSlot(only(ForecasterBlockEntity.MODEL, MODEL_X, MODEL_Y, ForecasterMenu::model));
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

    public static boolean model(ItemStack stack) {
        return stack.getItem() instanceof CrystalItem || stack.getItem() instanceof LinkItem;
    }

    private Slot only(int index, int x, int y, Predicate<ItemStack> allowed) {
        return new Slot(items, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return allowed.test(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
    }

    public ItemStack slotItem(int index) {
        return items.getItem(index);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof ForecasterBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        int own = ForecasterBlockEntity.SLOTS;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            int target = stack.getItem() instanceof FieldLogItem ? ForecasterBlockEntity.LOG
                    : model(stack) ? ForecasterBlockEntity.MODEL : -1;
            if (target < 0 || !moveItemStackTo(stack, target, target + 1, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }
}
