package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.registry.ModMenus;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A Processor's pattern and the player's inventory. Each grid shows a page of nine of its 81 cells, and holds pictures of
 * kinds and amounts, not items: clicking a cell with a stack puts that many of that kind there, right-clicking one more of
 * it; empty-handed, a click clears the cell and a right-click takes one off.
 */
public class ProcessorMenu extends AbstractContainerMenu {
    public static final int IN_X = 26, OUT_X = 96, GRID_Y = 20, INVENTORY_X = 8, INVENTORY_Y = 134;
    /** The slots of the two pages shown, before the inventory's. */
    public static final int CELLS = ProcessorBlockEntity.PAGE * 2;
    /** Buttons: the input page back and on, the output page back and on. */
    public static final int IN_BACK = 0, IN_ON = 1, OUT_BACK = 2, OUT_ON = 3;
    /** Menu data after the Processor's: the two pages shown. */
    public static final int IN_PAGE = ProcessorBlockEntity.DATA, OUT_PAGE = IN_PAGE + 1;

    public final BlockPos pos;
    public final ContainerData data;
    @Nullable
    private final ProcessorBlockEntity processor;

    public ProcessorMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(ProcessorBlockEntity.PAGE), new SimpleContainer(ProcessorBlockEntity.PAGE),
                new SimpleContainerData(OUT_PAGE + 1), null, true);
    }

    /** On the server, {@code processor} pauses while the screen is open. */
    public ProcessorMenu(int id, Inventory inventory, BlockPos pos, Container inputs, Container outputs, ContainerData data,
                         ProcessorBlockEntity processor) {
        this(id, inventory, pos, inputs, outputs, withPages(data), processor, false);
    }

    private ProcessorMenu(int id, Inventory inventory, BlockPos pos, Container inputs, Container outputs, ContainerData data,
                          @Nullable ProcessorBlockEntity processor, boolean client) {
        super(ModMenus.PROCESSOR.get(), id);
        this.pos = pos;
        this.data = data;
        this.processor = processor;
        if (processor != null) {
            processor.viewers++;
        }
        // on the client a grid is only the page the server shows it
        Container in = client ? inputs : new Page(inputs, IN_PAGE), out = client ? outputs : new Page(outputs, OUT_PAGE);
        for (int i = 0; i < ProcessorBlockEntity.PAGE; i++) {
            addSlot(new Cell(in, i, IN_X + i % 3 * 18, GRID_Y + i / 3 * 18));
        }
        for (int i = 0; i < ProcessorBlockEntity.PAGE; i++) {
            addSlot(new Cell(out, i, OUT_X + i % 3 * 18, GRID_Y + i / 3 * 18));
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

    /** Draws a recipe from the recipe viewer over the pattern: what goes in and what comes out, in the order given. */
    public void draw(List<ItemStack> inputs, List<ItemStack> outputs) {
        if (processor == null) {
            return;
        }
        processor.draw(inputs, outputs);
    }

    /** The Processor's numbers, with the pages this menu shows after them. */
    private static ContainerData withPages(ContainerData data) {
        int[] pages = new int[2];
        return new ContainerData() {
            @Override
            public int get(int i) {
                return i >= IN_PAGE ? pages[i - IN_PAGE] : data.get(i);
            }

            @Override
            public void set(int i, int value) {
                if (i >= IN_PAGE) {
                    pages[i - IN_PAGE] = value;
                }
            }

            @Override
            public int getCount() {
                return OUT_PAGE + 1;
            }
        };
    }

    /** A cell that shows but cannot be taken from. */
    private static final class Cell extends Slot {
        Cell(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    /** Nine cells of a grid: the page this menu shows. */
    private final class Page extends SimpleContainer {
        private final Container grid;
        private final int which;

        Page(Container grid, int which) {
            super(ProcessorBlockEntity.PAGE);
            this.grid = grid;
            this.which = which;
        }

        private int cell(int slot) {
            return data.get(which) * ProcessorBlockEntity.PAGE + slot;
        }

        @Override
        public ItemStack getItem(int slot) {
            return grid.getItem(cell(slot));
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            grid.setItem(cell(slot), stack);
        }

        @Override
        public void setChanged() {
            grid.setChanged();
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        int which = id < OUT_BACK ? IN_PAGE : OUT_PAGE;
        int by = id == IN_BACK || id == OUT_BACK ? -1 : 1;
        data.set(which, Math.floorMod(data.get(which) + by, ProcessorBlockEntity.PAGES));
        return true;
    }

    @Override
    public void clicked(int index, int button, ContainerInput input, Player player) {
        if (index >= 0 && index < CELLS) {
            if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
                draw(getSlot(index), button == 1);
            }
            return;
        }
        super.clicked(index, button, input, player);
    }

    private void draw(Slot cell, boolean one) {
        ItemStack carried = getCarried(), there = cell.getItem();
        if (carried.isEmpty()) {
            cell.set(!one || there.getCount() <= 1 ? ItemStack.EMPTY : there.copyWithCount(there.getCount() - 1));
        } else if (one) {
            int n = ItemStack.isSameItemSameComponents(carried, there) ? Math.min(there.getCount() + 1, Item.ABSOLUTE_MAX_STACK_SIZE) : 1;
            cell.set(carried.copyWithCount(n));
        } else {
            cell.set(carried.copy());
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (processor != null) {
            processor.viewers--;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof ProcessorBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }
}
