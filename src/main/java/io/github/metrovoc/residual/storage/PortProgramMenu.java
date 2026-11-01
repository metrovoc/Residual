package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.agent.PortWork;
import io.github.metrovoc.residual.agent.Upgrades;
import io.github.metrovoc.residual.registry.ModItems;
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

/**
 * A Port's programs: a row of Program Cards, one of which the screen shows, what its programs hold in their hands, its
 * upgrades in a panel beside the window, and the player's inventory.
 */
public class PortProgramMenu extends AbstractContainerMenu {
    public static final int CARDS_X = 8, CARDS_Y = 20, HANDS_X = 8, HANDS_Y = 136, INVENTORY_X = 8, INVENTORY_Y = 166;
    public static final int SIDE_X = 172, SIDE_Y = 4, SIDE_W = 47, SIDE_H = 86, UPGRADES_X = 179, UPGRADES_Y = 10;
    /** Where each kind of slot starts, among the menu's. */
    public static final int CARDS = 0, HANDS = PortWork.CARDS, UPGRADES = HANDS + PortWork.HANDS, INVENTORY = UPGRADES + Upgrades.SLOTS;

    public final BlockPos pos;
    private final ContainerData access;
    private int selected;
    private boolean hidden;

    public PortProgramMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(PortWork.CARDS), new SimpleContainer(Upgrades.SLOTS), new SimpleContainer(PortWork.HANDS), new SimpleContainerData(1));
    }

    public PortProgramMenu(int id, Inventory inventory, BlockPos pos, Container cards, Container upgrades, Container hands, ContainerData access) {
        super(ModMenus.PORT_PROGRAM.get(), id);
        this.pos = pos;
        this.access = access;
        addDataSlots(access);
        for (int i = 0; i < PortWork.CARDS; i++) {
            addSlot(new Slot(cards, i, CARDS_X + i * 18, CARDS_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModItems.PROGRAM_CARD.get());
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean isActive() {
                    return !hidden;
                }
            });
        }
        for (int i = 0; i < PortWork.HANDS; i++) {
            addSlot(new Shown(hands, i, HANDS_X + i * 18, HANDS_Y));
        }
        for (int i = 0; i < Upgrades.SLOTS; i++) {
            addSlot(new Slot(upgrades, i, UPGRADES_X + i % 2 * 18, UPGRADES_Y + i / 2 * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return Upgrades.accepts(stack);
                }

                @Override
                public int getMaxStackSize(ItemStack stack) {
                    return Upgrades.limit(stack);
                }

                @Override
                public boolean isActive() {
                    return !hidden;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Shown(inventory, 9 + row * 9 + col, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Shown(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
    }

    /** A slot that stands aside while the program has the whole window. */
    private final class Shown extends Slot {
        Shown(Container c, int index, int x, int y) {
            super(c, index, x, y);
        }

        @Override
        public boolean isActive() {
            return !hidden;
        }
    }

    /** Clears the screen of slots, or brings them back, so the program can have the whole window. */
    public void hideSlots(boolean hide) {
        hidden = hide;
    }

    /** Who may use the Port: an {@link io.github.metrovoc.residual.agent.Ownership.Access}'s place. */
    public int access() {
        return access.get(0);
    }

    /** The card the screen shows. */
    public int selected() {
        return selected;
    }

    public void select(int slot) {
        selected = slot;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < INVENTORY) {
            if (!moveItemStackTo(stack, INVENTORY, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.PROGRAM_CARD.get())) {
            if (!moveItemStackTo(stack.split(1), CARDS, HANDS, false)) {
                stack.grow(1);
                return ItemStack.EMPTY;
            }
        } else if (Upgrades.accepts(stack)) {
            if (!moveItemStackTo(stack, UPGRADES, INVENTORY, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, HANDS, UPGRADES, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof PortBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }
}
