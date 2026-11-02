package io.github.metrovoc.residual.energy;

import io.github.metrovoc.residual.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
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
    protected int burn;
    protected int burnTotal;
    private final ContainerData data = PowerMenu.data(this, () -> burn, () -> burnTotal, this::rateNow);

    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.GENERATOR.get(), pos, state, CAPACITY, OUT);
    }

    protected GeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int capacity, int out) {
        super(type, pos, state, capacity, 0, out);
    }

    /** What it makes a tick while it burns. */
    protected int rate() {
        return RATE;
    }

    /** What it makes a tick now. */
    private int rateNow() {
        return burn > 0 ? rate() : 0;
    }

    /** Ticks {@code fuel} keeps it burning. */
    protected int burnTicks(Level level, ItemStack fuel) {
        return burnTime(level, fuel);
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
        int rate = rate();
        int room = (int) energy().getCapacityAsLong() - rate;
        if (burn <= 0 && rate > 0 && stored <= room) {
            ItemStack fuel = items.getItem(FUEL);
            int time = fuel.isEmpty() ? 0 : burnTicks(level, fuel);
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
        if (burn > 0 && rate > 0 && stored <= room) {
            burn--;
            energy().set(stored + rate);
        }
        lit(burn > 0 && rate > 0);
    }

    protected void lit(boolean on) {
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
        ContainerHelper.saveAllItems(out.child("items"), items.getItems());
        out.putInt("burn", burn);
        out.putInt("burn_total", burnTotal);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("items").ifPresent(c -> ContainerHelper.loadAllItems(c, items.getItems()));
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
