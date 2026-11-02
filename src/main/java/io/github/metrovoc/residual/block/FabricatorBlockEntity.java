package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.agent.Upgrades;
import io.github.metrovoc.residual.bench.MaskData;
import io.github.metrovoc.residual.bench.Spec;
import io.github.metrovoc.residual.bench.Specs;
import io.github.metrovoc.residual.bench.Unit;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.item.MachineItem;
import io.github.metrovoc.residual.item.MaskItem;
import io.github.metrovoc.residual.item.UpgradeItem;
import io.github.metrovoc.residual.menu.FabricatorMenu;
import io.github.metrovoc.residual.refine.RefineryBlock;
import io.github.metrovoc.residual.refine.Stage;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Makes machines. A photomask decides what it can make and how many wafers each takes: a design's area, four to a
 * wafer. Without a mask it makes the machines that need no design.
 */
public class FabricatorBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MASK = 0, CASING = 1, WAFER = 2, CRYSTAL = 3, OUTPUT = 4, SLOTS = 5;
    /** What its screen shows, as container data. */
    public static final int CHOICE = 0, PROGRESS = 1, TOTAL = 2, DATA = 3;

    /** What one machine takes: casings, wafers, weight crystals, and ticks of work. */
    public record Cost(int casings, int wafers, int crystals, int ticks) {
    }

    private final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            FabricatorBlockEntity.this.setChanged();
        }
    };
    private int choice;
    private int progress;
    @Nullable
    private Item making;
    @Nullable
    private Cost makingCost;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case CHOICE -> choice;
                case PROGRESS -> progress;
                case TOTAL -> makingCost == null ? 0 : makingCost.ticks();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return DATA;
        }
    };

    public FabricatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FABRICATOR.get(), pos, state);
    }

    /**
     * What it can make: the machines a mask's spec unlocks, or with no mask the machines that need no design. A matrix
     * multiply mask also makes the Accelerator and the Drive Dock, a linear layer mask the Dense and Portable Drives, an attention
     * mask the Hourglass, the Surveyor and the Access Point, and a transformer mask Forecaster II, the Smelter, the flight frame,
     * the Stacked Drive, the relay network's gates and links and the machines of a Refinery.
     */
    public static List<Item> choices(ItemStack mask) {
        List<Item> out = new ArrayList<>();
        if (mask.isEmpty()) {
            for (Op op : Op.values()) {
                if (Specs.unlocking(op) == null) {
                    out.add(ModItems.MACHINES.get(op).get());
                }
            }
            return out;
        }
        MaskData m = MaskItem.mask(mask);
        Spec spec = m == null ? null : m.specOrNull();
        if (spec != null) {
            for (Op op : spec.unlocks()) {
                out.add(ModItems.MACHINES.get(op).get());
            }
            if (Unit.of(spec) != null) {
                out.add(ModBlocks.UNITS.get(Unit.of(spec)).asItem());
            }
            if (spec == Specs.MATMUL) {
                ModBlocks.ACCELERATORS.forEach(a -> out.add(a.asItem()));
                out.add(ModBlocks.DRIVE_DOCK.asItem());
            }
            if (spec == Specs.LINEAR) {
                out.add(ModItems.DENSE_DRIVE.get());
                out.add(ModItems.PORTABLE_DRIVE.get());
            }
            if (spec == Specs.ATTENTION) {
                out.add(ModItems.HOURGLASS.get());
                out.add(ModBlocks.SURVEYOR.asItem());
                out.add(ModBlocks.ACCESS_POINT.asItem());
                for (Upgrades.Kind kind : List.of(Upgrades.Kind.SPEED, Upgrades.Kind.STACK, Upgrades.Kind.RANGE, Upgrades.Kind.FLUID, Upgrades.Kind.CARRY)) {
                    out.add(ModItems.UPGRADES.get(kind).get());
                }
            }
            if (spec == Specs.TRANSFORMER) {
                out.add(ModBlocks.FORECASTER_II.asItem());
                out.add(ModBlocks.FORECASTER_III.asItem());
                out.add(ModBlocks.SMELTER.asItem());
                out.add(ModItems.FLIGHT_FRAME.get());
                out.add(ModItems.STACKED_DRIVE.get());
                out.add(ModItems.UPGRADES.get(Upgrades.Kind.ENERGY).get());
                out.add(ModItems.UPGRADES.get(Upgrades.Kind.LIFT).get());
                out.add(ModBlocks.RELAY_GATE.asItem());
                out.add(ModBlocks.RELAY_LINK.asItem());
                for (Stage stage : Stage.values()) {
                    if (stage != Stage.INTAKE && stage != Stage.TANK) {
                        out.add(ModBlocks.REFINERY.get(stage).asItem());
                    }
                }
                out.add(ModBlocks.RACK_SWITCH.asItem());
                out.add(ModBlocks.POD_SWITCH.asItem());
                out.add(ModBlocks.HALL_CONSOLE.asItem());
                out.add(ModBlocks.REACTOR_CORE.asItem());
            }
        }
        return out;
    }

    /** Ticks into what it is making, and what that takes in all: 0 while it makes nothing. */
    public int progress() {
        return progress;
    }

    public int total() {
        return makingCost == null ? 0 : makingCost.ticks();
    }

    /** The mask in it. */
    public ItemStack mask() {
        return items.getItem(MASK);
    }

    /** The wafers {@code item} has to be printed from: a finer process node takes finer wafers. */
    public static Item wafer(Item item) {
        if (item == ModBlocks.ACCELERATOR_28.asItem()) {
            return ModItems.WAFER_28.get();
        }
        if (item == ModBlocks.ACCELERATOR_7.asItem()) {
            return ModItems.WAFER_7.get();
        }
        return item == ModBlocks.ACCELERATOR_3.asItem() ? ModItems.WAFER_3.get() : ModItems.WAFER.get();
    }

    public static Cost cost(Item item, ItemStack mask) {
        if (item == ModBlocks.ACCELERATOR.asItem()) {
            return new Cost(2, 6, 0, 100);
        }
        if (item == ModBlocks.ACCELERATOR_28.asItem()) {
            return new Cost(2, 6, 0, 200);
        }
        if (item == ModBlocks.ACCELERATOR_7.asItem()) {
            return new Cost(2, 6, 0, 400);
        }
        if (item == ModBlocks.ACCELERATOR_3.asItem()) {
            return new Cost(2, 6, 0, 800);
        }
        if (item == ModBlocks.RACK_SWITCH.asItem()) {
            return new Cost(2, 4, 2, 120);
        }
        if (item == ModBlocks.POD_SWITCH.asItem()) {
            return new Cost(4, 8, 4, 240);
        }
        if (item == ModBlocks.HALL_CONSOLE.asItem()) {
            return new Cost(8, 16, 8, 400);
        }
        if (item == ModBlocks.REACTOR_CORE.asItem()) {
            return new Cost(16, 32, 16, 800);
        }
        if (item == ModItems.HOURGLASS.get()) {
            return new Cost(1, 4, 2, 100);
        }
        if (item instanceof UpgradeItem upgrade) {
            return upgrade.kind == Upgrades.Kind.ENERGY || upgrade.kind == Upgrades.Kind.LIFT ? new Cost(1, 6, 3, 120) : new Cost(0, 3, 1, 60);
        }
        if (item == ModBlocks.FORECASTER_II.asItem()) {
            return new Cost(4, 8, 4, 200);
        }
        if (item == ModBlocks.FORECASTER_III.asItem()) {
            return new Cost(8, 16, 8, 400);
        }
        if (item == ModBlocks.SURVEYOR.asItem()) {
            return new Cost(2, 6, 3, 160);
        }
        if (item == ModItems.FLIGHT_FRAME.get()) {
            return new Cost(4, 8, 4, 200);
        }
        if (item == ModBlocks.SMELTER.asItem()) {
            return new Cost(2, 6, 2, 160);
        }
        if (item == ModBlocks.DRIVE_DOCK.asItem()) {
            return new Cost(4, 8, 2, 160);
        }
        if (item == ModItems.DENSE_DRIVE.get()) {
            return new Cost(1, 8, 2, 120);
        }
        if (item == ModItems.PORTABLE_DRIVE.get()) {
            return new Cost(1, 6, 1, 100);
        }
        if (item == ModBlocks.ACCESS_POINT.asItem()) {
            return new Cost(2, 6, 2, 160);
        }
        if (item == ModItems.STACKED_DRIVE.get()) {
            return new Cost(2, 16, 8, 240);
        }
        if (item == ModBlocks.RELAY_GATE.asItem()) {
            return new Cost(8, 16, 8, 400);
        }
        if (item == ModBlocks.RELAY_LINK.asItem()) {
            return new Cost(4, 8, 4, 200);
        }
        if (item instanceof BlockItem b && b.getBlock() instanceof RefineryBlock) {
            return new Cost(2, 4, 2, 160);
        }
        MaskData m = MaskItem.mask(mask);
        int wafers = Math.max(1, ((m == null ? 0 : m.area()) + 3) / 4);
        int crystals = item instanceof MachineItem machine ? switch (machine.op()) {
            case EMBEDDING, POSITION, LINEAR, NORM, RMS_NORM, QK_NORM -> 1;
            case ATTENTION, MLP, SWIGLU, MODERN_ATTENTION -> 2;
            case TRANSFORMER, MODERN_BLOCK -> 4;
            case TWO_TOKEN_HEAD -> 2;
            default -> 0;
        } : 0;
        return new Cost(1, wafers, crystals, 40 + 10 * wafers);
    }

    /** The machine as it comes out: unfused when the mask's design was salvaged rather than verified. */
    public static ItemStack product(Item item, ItemStack mask) {
        ItemStack stack = new ItemStack(item);
        MaskData m = MaskItem.mask(mask);
        if (item instanceof MachineItem && m != null && m.salvaged()) {
            stack.set(ModComponents.FUSED.get(), false);
        }
        return stack;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FabricatorBlockEntity be) {
        be.tick((ServerLevel) level);
    }

    private void tick(ServerLevel level) {
        ItemStack mask = items.getItem(MASK);
        List<Item> choices = choices(mask);
        Item target = choices.isEmpty() ? null : choices.get(Math.min(choice, choices.size() - 1));
        Cost cost = target == null ? null : cost(target, mask);
        if (target != making || !Objects.equals(cost, makingCost)) {
            making = target;
            makingCost = cost;
            progress = 0;
        }
        ItemStack product = target == null ? ItemStack.EMPTY : product(target, mask);
        boolean ready = target != null && enough(cost) && items.getItem(WAFER).is(wafer(target)) && room(product);
        working(ready);
        if (!ready) {
            progress = 0;
            return;
        }
        if (++progress < cost.ticks()) {
            return;
        }
        progress = 0;
        items.getItem(CASING).shrink(cost.casings());
        items.getItem(WAFER).shrink(cost.wafers());
        items.getItem(CRYSTAL).shrink(cost.crystals());
        ItemStack out = items.getItem(OUTPUT);
        if (out.isEmpty()) {
            items.setItem(OUTPUT, product);
        } else {
            out.grow(1);
        }
        items.setChanged();
        level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.5f, 1.8f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, worldPosition.getX() + 0.5, worldPosition.getY() + 1.05,
                worldPosition.getZ() + 0.5, 10, 0.25, 0.05, 0.25, 0.05);
    }

    private boolean enough(Cost cost) {
        return items.getItem(CASING).getCount() >= cost.casings() && items.getItem(WAFER).getCount() >= cost.wafers()
                && items.getItem(CRYSTAL).getCount() >= cost.crystals();
    }

    private boolean room(ItemStack product) {
        ItemStack out = items.getItem(OUTPUT);
        return out.isEmpty() || ItemStack.isSameItemSameComponents(out, product) && out.getCount() < out.getMaxStackSize();
    }

    private void working(boolean on) {
        BlockState here = getBlockState();
        if (level != null && here.getValue(FabricatorBlock.WORKING) != on) {
            level.setBlock(worldPosition, here.setValue(FabricatorBlock.WORKING, on), Block.UPDATE_CLIENTS);
        }
    }

    /** Picks what to make, by its place among {@link #choices}. */
    public void choose(int index) {
        if (index >= 0 && index < choices(items.getItem(MASK)).size() && index != choice) {
            choice = index;
            progress = 0;
            setChanged();
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.residual.fabricator");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new FabricatorMenu(id, inventory, worldPosition, items, data);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, items);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out.child("items"), items.getItems());
        out.putInt("choice", choice);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("items").ifPresent(c -> ContainerHelper.loadAllItems(c, items.getItems()));
        choice = in.getIntOr("choice", 0);
    }
}
