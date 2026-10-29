package io.github.metrovoc.residual.menu;

import io.github.metrovoc.residual.block.CoreBlock;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.DatasetItem;
import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.TokenizerItem;
import io.github.metrovoc.residual.item.WreckItem;
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
 * The Model Core's screen: four data slots for what the model practises on, a slot for the tokenizer it reads with and
 * a crystal slot to save it into. The screen shows the slots and the player's inventory only on its Data tab.
 */
public class CoreMenu extends AbstractContainerMenu {
    public static final int DATA_SLOTS = 4;
    public static final int CRYSTAL = 4;
    public static final int TOKENIZER = 5;
    public static final int SLOTS = 6;
    /** Where the slots sit on the Data tab. */
    public static final int DATA_X = 14;
    public static final int DATA_Y = 36;
    public static final int TOKENIZER_X = 110;
    public static final int CRYSTAL_X = 176;
    public static final int INVENTORY_X = 80;
    public static final int INVENTORY_Y = 112;

    public final BlockPos pos;
    /** Whether the screen is showing the slots; the server always is. */
    public boolean slotsShown = true;

    public CoreMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(SLOTS));
    }

    public CoreMenu(int id, Inventory inventory, BlockPos pos, Container container) {
        super(ModMenus.CORE.get(), id);
        this.pos = pos;
        for (int i = 0; i < DATA_SLOTS; i++) {
            addSlot(new Shown(container, i, DATA_X + i * 20, DATA_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return isData(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addSlot(new Shown(container, CRYSTAL, CRYSTAL_X, DATA_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CrystalItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Shown(container, TOKENIZER, TOKENIZER_X, DATA_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof TokenizerItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
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

    public static boolean isData(ItemStack stack) {
        return stack.getItem() instanceof DatasetItem || stack.getItem() instanceof DriveItem || stack.getItem() instanceof WreckItem;
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
        if (index < SLOTS) {
            if (!moveItemStackTo(stack, SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof CrystalItem) {
            if (!moveItemStackTo(stack, CRYSTAL, CRYSTAL + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof TokenizerItem) {
            if (!moveItemStackTo(stack, TOKENIZER, TOKENIZER + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!isData(stack) || !moveItemStackTo(stack, 0, DATA_SLOTS, false)) {
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
        return player.level().getBlockState(pos).getBlock() instanceof CoreBlock && player.isWithinBlockInteractionRange(pos, 4.0);
    }
}
