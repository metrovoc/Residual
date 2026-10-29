package io.github.metrovoc.residual.menu;

import io.github.metrovoc.residual.block.ReaderBlockEntity;
import io.github.metrovoc.residual.item.ShardItem;
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

/** The Reader's screen: a shard slot, a mind slot and the player's inventory. */
public class ReaderMenu extends AbstractContainerMenu {
    public static final int SHARD_X = 18, SHARD_Y = 30, MIND_X = 18, MIND_Y = 72;
    public static final int INVENTORY_X = 48, INVENTORY_Y = 150;

    public final BlockPos pos;
    public final ContainerData data;

    public ReaderMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(2), new SimpleContainerData(ReaderBlockEntity.DATA));
    }

    public ReaderMenu(int id, Inventory inventory, BlockPos pos, Container items, ContainerData data) {
        super(ModMenus.READER.get(), id);
        this.pos = pos;
        this.data = data;
        addSlot(new Slot(items, ReaderBlockEntity.SHARD, SHARD_X, SHARD_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof ShardItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(items, ReaderBlockEntity.MIND, MIND_X, MIND_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ApprenticeMenu.isMind(stack);
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

    public ItemStack shard() {
        return getSlot(ReaderBlockEntity.SHARD).getItem();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof ReaderBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, stack.getItem() instanceof ShardItem ? 0 : 1, stack.getItem() instanceof ShardItem ? 1 : 2, false)) {
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
