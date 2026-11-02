package io.github.metrovoc.residual.menu;

import io.github.metrovoc.residual.block.InstrumentBlockEntity;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A Stream Scope's or Trial Rig's screen: the model's slot, for the Rig what it works on and a crystal for what comes of it, and the
 * player's inventory. As on the Model Core, the screen shows them only on its first tab.
 */
public class InstrumentMenu extends AbstractContainerMenu {
    public static final int SLOTS_Y = 36, MODEL_X = 14, DATA_X = 56, OUTPUT_X = 98;
    public static final int INVENTORY_X = 80, INVENTORY_Y = 112;

    public final BlockPos pos;
    public final boolean rig;
    /** Whether the screen is showing the slots; the server always is. */
    public boolean slotsShown = true;

    public InstrumentMenu(int id, Inventory inventory, BlockPos pos, boolean rig) {
        this(id, inventory, pos, rig, new SimpleContainer(InstrumentBlockEntity.SLOTS));
    }

    public InstrumentMenu(int id, Inventory inventory, BlockPos pos, boolean rig, Container items) {
        super(ModMenus.INSTRUMENT.get(), id);
        this.pos = pos;
        this.rig = rig;
        addSlot(new Shown(items, InstrumentBlockEntity.MODEL, MODEL_X, SLOTS_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CrystalItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Shown(items, InstrumentBlockEntity.DATA, DATA_X, SLOTS_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return CoreMenu.isData(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean isActive() {
                return rig && super.isActive();
            }
        });
        addSlot(new Shown(items, InstrumentBlockEntity.OUTPUT, OUTPUT_X, SLOTS_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CrystalItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean isActive() {
                return rig && super.isActive();
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Shown(inventory, 9 + row * 9 + col, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Shown(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
    }

    private class Shown extends Slot {
        Shown(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return slotsShown;
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < InstrumentBlockEntity.SLOTS) {
            if (!moveItemStackTo(stack, InstrumentBlockEntity.SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof CrystalItem) {
            // the model first; on a Rig, then the crystal for what comes of it
            if (!moveItemStackTo(stack, InstrumentBlockEntity.MODEL, InstrumentBlockEntity.MODEL + 1, false)
                    && !(rig && moveItemStackTo(stack, InstrumentBlockEntity.OUTPUT, InstrumentBlockEntity.OUTPUT + 1, false))) {
                return ItemStack.EMPTY;
            }
        } else if (!rig || !CoreMenu.isData(stack) || !moveItemStackTo(stack, InstrumentBlockEntity.DATA, InstrumentBlockEntity.DATA + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof InstrumentBlockEntity && player.isWithinBlockInteractionRange(pos, 4.0);
    }
}
