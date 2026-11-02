package io.github.metrovoc.residual.menu;

import io.github.metrovoc.residual.block.SurveyorBlockEntity;
import io.github.metrovoc.residual.item.FieldLogItem;
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

/** The Surveyor's screen: its model slot, in a panel beside the window, and the player's inventory. */
public class SurveyorMenu extends AbstractContainerMenu {
    /** The model slot's panel beside the window, tucked under its edge. */
    public static final int SIDE_X = 172, SIDE_Y = 4, SIDE_W = 29, SIDE_H = 46, MODEL_X = 179, MODEL_Y = 10, LOG_Y = 28;
    public static final int INVENTORY_X = 8, INVENTORY_Y = 100;

    public final BlockPos pos;
    public final ContainerData data;

    public SurveyorMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(2), new SimpleContainerData(SurveyorBlockEntity.DATA));
    }

    public SurveyorMenu(int id, Inventory inventory, BlockPos pos, Container items, ContainerData data) {
        super(ModMenus.SURVEYOR.get(), id);
        this.pos = pos;
        this.data = data;
        addSlot(new Slot(items, SurveyorBlockEntity.MODEL, MODEL_X, MODEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return AgentMenu.isModel(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(items, SurveyorBlockEntity.LOG, MODEL_X, LOG_Y) {
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
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof SurveyorBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index <= SurveyorBlockEntity.LOG) {
            if (!moveItemStackTo(stack, SurveyorBlockEntity.LOG + 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof FieldLogItem) {
            if (!moveItemStackTo(stack, SurveyorBlockEntity.LOG, SurveyorBlockEntity.LOG + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!AgentMenu.isModel(stack) || !moveItemStackTo(stack, SurveyorBlockEntity.MODEL, SurveyorBlockEntity.MODEL + 1, false)) {
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
