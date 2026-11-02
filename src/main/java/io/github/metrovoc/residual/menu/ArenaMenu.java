package io.github.metrovoc.residual.menu;

import io.github.metrovoc.residual.block.ArenaBlockEntity;
import io.github.metrovoc.residual.item.FieldLogItem;
import io.github.metrovoc.residual.item.MobLogItem;
import io.github.metrovoc.residual.learn.Ground;
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

/** The Arena's screen: the log it plays out, what comes of it, the model in a panel beside the window, and the player's inventory. */
public class ArenaMenu extends AbstractContainerMenu {
    public static final int LOG_X = 8, LOG_Y = 35;
    /** What came of the fights, five by three on the slot grid. */
    public static final int OUT_X = 80, OUT_Y = 17, COLUMNS = 5, GRID = 18;
    /** The model's slot and the store of power, in a panel beside the window, tucked under its edge. */
    public static final int SIDE_X = 172, SIDE_Y = 4, SIDE_W = 29, SIDE_H = 76, MODEL_X = 179, MODEL_Y = 10;
    public static final int INVENTORY_X = 8, INVENTORY_Y = 84;

    public final BlockPos pos;
    public final ContainerData data;

    public ArenaMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(ArenaBlockEntity.SLOTS), new SimpleContainerData(ArenaBlockEntity.DATA));
    }

    public ArenaMenu(int id, Inventory inventory, BlockPos pos, Container items, ContainerData data) {
        super(ModMenus.ARENA.get(), id);
        this.pos = pos;
        this.data = data;
        addSlot(only(items, ArenaBlockEntity.LOG, LOG_X, LOG_Y, ArenaMenu::isLog));
        addSlot(only(items, ArenaBlockEntity.MODEL, MODEL_X, MODEL_Y, ArenaMenu::isModel));
        for (int i = 0; i < ArenaBlockEntity.OUTS; i++) {
            addSlot(new Slot(items, ArenaBlockEntity.OUT + i, OUT_X + i % COLUMNS * GRID, OUT_Y + i / COLUMNS * GRID) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
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

    /** A Mob Log that has taken a kind, or a Field Log of ground. */
    public static boolean isLog(ItemStack stack) {
        return MobLogItem.log(stack) != null || stack.getItem() instanceof FieldLogItem && Ground.isLog(FieldLogItem.log(stack));
    }

    public static boolean isModel(ItemStack stack) {
        return AgentMenu.isModel(stack);
    }

    private static Slot only(Container items, int index, int x, int y, Predicate<ItemStack> allowed) {
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

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof ArenaBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack before = stack.copy();
        boolean moved = index < ArenaBlockEntity.SLOTS ? moveItemStackTo(stack, ArenaBlockEntity.SLOTS, slots.size(), true)
                : isLog(stack) ? moveItemStackTo(stack, ArenaBlockEntity.LOG, ArenaBlockEntity.LOG + 1, false)
                : isModel(stack) && moveItemStackTo(stack, ArenaBlockEntity.MODEL, ArenaBlockEntity.MODEL + 1, false);
        if (!moved) {
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
