package io.github.metrovoc.residual.menu;

import io.github.metrovoc.residual.block.ForgeBlockEntity;
import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.TokenizerItem;
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

/** The BPE Forge's screen: the drive it reads, the tokenizer it writes, and the player's inventory. */
public class ForgeMenu extends AbstractContainerMenu {
    public static final int TEXT_X = 12, CARD_X = 68, SLOT_Y = 22;
    public static final int INVENTORY_X = 48, INVENTORY_Y = 152;

    public final BlockPos pos;
    public final ContainerData data;
    private final Container items;

    public ForgeMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(ForgeBlockEntity.SLOTS), new SimpleContainerData(ForgeBlockEntity.DATA));
    }

    public ForgeMenu(int id, Inventory inventory, BlockPos pos, Container items, ContainerData data) {
        super(ModMenus.FORGE.get(), id);
        this.pos = pos;
        this.data = data;
        this.items = items;
        addSlot(only(ForgeBlockEntity.TEXT, TEXT_X, s -> s.getItem() instanceof DriveItem));
        addSlot(only(ForgeBlockEntity.CARD, CARD_X, s -> s.getItem() instanceof TokenizerItem));
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

    private Slot only(int index, int x, Predicate<ItemStack> allowed) {
        return new Slot(items, index, x, SLOT_Y) {
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
    public boolean clickMenuButton(Player player, int id) {
        if (!player.level().isClientSide() && player.level().getBlockEntity(pos) instanceof ForgeBlockEntity be) {
            be.press(id);
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof ForgeBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        int own = ForgeBlockEntity.SLOTS;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            int target = stack.getItem() instanceof DriveItem ? ForgeBlockEntity.TEXT
                    : stack.getItem() instanceof TokenizerItem ? ForgeBlockEntity.CARD : -1;
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
