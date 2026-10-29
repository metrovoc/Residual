package io.github.metrovoc.residual.menu;

import io.github.metrovoc.residual.block.FabricatorBlockEntity;
import io.github.metrovoc.residual.item.MaskItem;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.registry.ModItems;
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

/** The Fabricator's screen: a mask, the materials, the machine it makes, and the player's inventory. */
public class FabricatorMenu extends AbstractContainerMenu {
    public static final int MASK_X = 14, MASK_Y = 24;
    public static final int CASING_X = 14, WAFER_X = 32, CRYSTAL_X = 50, MATERIALS_Y = 98;
    public static final int OUTPUT_X = 218, OUTPUT_Y = 98;
    public static final int INVENTORY_X = 48, INVENTORY_Y = 136;

    public final BlockPos pos;
    public final ContainerData data;
    private final Container items;

    public FabricatorMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(FabricatorBlockEntity.SLOTS), new SimpleContainerData(FabricatorBlockEntity.DATA));
    }

    public FabricatorMenu(int id, Inventory inventory, BlockPos pos, Container items, ContainerData data) {
        super(ModMenus.FABRICATOR.get(), id);
        this.pos = pos;
        this.data = data;
        this.items = items;
        addSlot(only(FabricatorBlockEntity.MASK, MASK_X, MASK_Y, s -> MaskItem.mask(s) != null));
        addSlot(only(FabricatorBlockEntity.CASING, CASING_X, MATERIALS_Y, s -> s.is(ModBlocks.CASING.asItem())));
        addSlot(only(FabricatorBlockEntity.WAFER, WAFER_X, MATERIALS_Y, s -> s.is(ModItems.WAFER.get())));
        addSlot(only(FabricatorBlockEntity.CRYSTAL, CRYSTAL_X, MATERIALS_Y, s -> s.is(ModItems.WEIGHT_CRYSTAL.get())));
        addSlot(only(FabricatorBlockEntity.OUTPUT, OUTPUT_X, OUTPUT_Y, s -> false));
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

    private Slot only(int index, int x, int y, Predicate<ItemStack> allowed) {
        return new Slot(items, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return allowed.test(stack);
            }

            @Override
            public int getMaxStackSize() {
                return index == FabricatorBlockEntity.MASK ? 1 : super.getMaxStackSize();
            }
        };
    }

    public ItemStack mask() {
        return items.getItem(FabricatorBlockEntity.MASK);
    }

    public ItemStack slotItem(int index) {
        return items.getItem(index);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!player.level().isClientSide() && player.level().getBlockEntity(pos) instanceof FabricatorBlockEntity be) {
            be.choose(id);
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof FabricatorBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        int own = FabricatorBlockEntity.SLOTS;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            int target = -1;
            for (int i = 0; i < FabricatorBlockEntity.OUTPUT; i++) {
                if (slots.get(i).mayPlace(stack)) {
                    target = i;
                }
            }
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
