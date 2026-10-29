package io.github.metrovoc.residual.menu;

import io.github.metrovoc.residual.apprentice.Apprentice;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** An apprentice's screen: what it carries, the mind slot, and the player's inventory. */
public class ApprenticeMenu extends AbstractContainerMenu {
    public static final int CARRY_X = 8;
    public static final int CARRY_Y = 104;
    public static final int MIND_X = 152;
    public static final int MIND_Y = 104;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 166;

    /** Null on the client if the apprentice isn't loaded there. */
    @Nullable
    public final Apprentice apprentice;
    public final int entityId;

    public ApprenticeMenu(int id, Inventory inventory, int entityId) {
        this(id, inventory, inventory.player.level().getEntity(entityId) instanceof Apprentice a ? a : null, entityId);
    }

    public ApprenticeMenu(int id, Inventory inventory, Apprentice apprentice) {
        this(id, inventory, apprentice, apprentice.getId());
    }

    private ApprenticeMenu(int id, Inventory inventory, @Nullable Apprentice apprentice, int entityId) {
        super(ModMenus.APPRENTICE.get(), id);
        this.apprentice = apprentice;
        this.entityId = entityId;
        Container c = apprentice != null ? apprentice.items : new SimpleContainer(Apprentice.CARRY + 1);
        for (int i = 0; i < Apprentice.CARRY; i++) {
            addSlot(new Slot(c, i, CARRY_X + i % 6 * 18, CARRY_Y + i / 6 * 18));
        }
        addSlot(new Slot(c, Apprentice.MIND, MIND_X, MIND_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isMind(stack);
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
    }

    public static boolean isMind(ItemStack stack) {
        return stack.getItem() instanceof CrystalItem && CrystalItem.mind(stack) != null || stack.is(ModItems.RELAY_CARD.get());
    }

    @Override
    public boolean stillValid(Player player) {
        return apprentice != null && apprentice.isAlive() && player.distanceToSqr(apprentice) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        int own = Apprentice.CARRY + 1;
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!(isMind(stack) && moveItemStackTo(stack, Apprentice.MIND, own, false))
                && !moveItemStackTo(stack, 0, Apprentice.CARRY, false)) {
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
