package io.github.metrovoc.residual.energy;

import io.github.metrovoc.residual.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * The Combustion Generator: burns what a furnace burns, making {@link #RATE} FE a tick, a fuel lasting half as long as
 * it would in a furnace; a coal is 64,000 FE. It burns only while it has room for what it makes.
 */
public class GeneratorBlockEntity extends PowerBlockEntity implements MenuProvider {
    public static final int RATE = 80, CAPACITY = 32_000, OUT = 2_000;
    public static final int FUEL = 0, SLOTS = 1;

    private final Items items = new Items();
    private int burn;
    private int burnTotal;
    private final ContainerData data = PowerMenu.data(this, () -> burn, () -> burnTotal, () -> burn > 0 ? RATE : 0);

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENERATOR.get(), pos, state, CAPACITY, 0, OUT);
    }

    @Override
    public Role role() {
        return Role.GENERATOR;
    }

    public WorldlyContainer items() {
        return items;
    }

    /** Ticks a fuel burns here: half as long as in a furnace. */
    static int burnTime(Level level, ItemStack fuel) {
        return fuel.getBurnTime(RecipeType.SMELTING, level.fuelValues()) / 2;
    }


    @Override
    protected void tick(ServerLevel level) {
        super.tick(level);
        int stored = energy().getAmountAsInt();
        if (burn <= 0 && stored + RATE <= CAPACITY) {
            ItemStack fuel = items.getItem(FUEL);
            int time = fuel.isEmpty() ? 0 : burnTime(level, fuel);
            if (time > 0) {
                burn = burnTotal = time;
                // as a furnace does: what the fuel leaves (a lava bucket's bucket) is asked for before it goes
                ItemStackTemplate left = fuel.getCraftingRemainder();
                fuel.shrink(1);
                if (fuel.isEmpty() && left != null) {
                    items.setItem(FUEL, left.create());
                }
                items.setChanged();
            }
        }
        if (burn > 0 && stored + RATE <= CAPACITY) {
            burn--;
            energy().set(stored + RATE);
        }
        lit(burn > 0);
    }

    private void lit(boolean on) {
        BlockState here = getBlockState();
        if (level != null && here.getValue(PowerDeviceBlock.WORKING) != on) {
            level.setBlock(worldPosition, here.setValue(PowerDeviceBlock.WORKING, on), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PowerMenu(id, inventory, worldPosition, PowerMenu.Kind.GENERATOR, items, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        net.minecraft.world.ContainerHelper.saveAllItems(out.child("items"), items.getItems());
        out.putInt("burn", burn);
        out.putInt("burn_total", burnTotal);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("items").ifPresent(c -> net.minecraft.world.ContainerHelper.loadAllItems(c, items.getItems()));
        burn = in.getIntOr("burn", 0);
        burnTotal = in.getIntOr("burn_total", 0);
    }

    /** Its fuel slot: anything that burns goes in from any side; nothing comes out but what a fuel leaves. */
    private final class Items extends SimpleContainer implements WorldlyContainer {
        Items() {
            super(SLOTS);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return level != null && burnTime(level, stack) > 0;
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            return new int[] {FUEL};
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
            return canPlaceItem(slot, stack);
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
            return level != null && burnTime(level, stack) == 0;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            GeneratorBlockEntity.this.setChanged();
        }
    }
}
