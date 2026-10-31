package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.registry.ModMenus;
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
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * An Assembler's recipe grid, what it makes, and the player's inventory. The grid holds pictures of kinds, not items:
 * clicking a cell with a stack puts that kind there, and clicking it empty-handed clears it.
 */
public class AssemblerMenu extends AbstractContainerMenu {
    public static final int GRID_X = 30, GRID_Y = 20, RESULT_X = 124, RESULT_Y = 38, INVENTORY_X = 8, INVENTORY_Y = 118;

    public final BlockPos pos;
    public final ContainerData data;
    private final Container grid;
    @Nullable
    private final AssemblerBlockEntity assembler;

    public AssemblerMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new SimpleContainer(AssemblerBlockEntity.GRID), new SimpleContainer(1),
                new SimpleContainerData(AssemblerBlockEntity.DATA), null);
    }

    /** On the server, {@code assembler} pauses while the screen is open. */
    public AssemblerMenu(int id, Inventory inventory, BlockPos pos, Container grid, Container result, ContainerData data,
                         @Nullable AssemblerBlockEntity assembler) {
        super(ModMenus.ASSEMBLER.get(), id);
        this.pos = pos;
        this.data = data;
        this.grid = grid;
        this.assembler = assembler;
        if (assembler != null) {
            assembler.viewers++;
        }
        for (int i = 0; i < AssemblerBlockEntity.GRID; i++) {
            addSlot(new Slot(grid, i, GRID_X + i % 3 * 18, GRID_Y + i / 3 * 18) {
                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }
        addSlot(new Slot(result, 0, RESULT_X, RESULT_Y) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
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
    public void clicked(int index, int button, ContainerInput input, Player player) {
        if (index >= 0 && index < AssemblerBlockEntity.GRID) {
            ItemStack carried = getCarried();
            grid.setItem(index, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            return;
        }
        if (index == AssemblerBlockEntity.GRID) {
            return;
        }
        super.clicked(index, button, input, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (assembler != null) {
            assembler.viewers--;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof AssemblerBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }
}
