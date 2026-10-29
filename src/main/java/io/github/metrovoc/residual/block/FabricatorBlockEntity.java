package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.bench.MaskData;
import io.github.metrovoc.residual.bench.Spec;
import io.github.metrovoc.residual.bench.Specs;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.item.MachineItem;
import io.github.metrovoc.residual.item.MaskItem;
import io.github.metrovoc.residual.menu.FabricatorMenu;
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

    /** What it can make: the machines a mask's spec unlocks, or with no mask the machines that need no design. */
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
            if (spec == Specs.MATMUL) {
                out.add(ModBlocks.ACCELERATOR.asItem());
            }
        }
        return out;
    }

    public static Cost cost(Item item, ItemStack mask) {
        if (item == ModBlocks.ACCELERATOR.asItem()) {
            return new Cost(2, 6, 0, 100);
        }
        MaskData m = MaskItem.mask(mask);
        int wafers = Math.max(1, ((m == null ? 0 : m.area()) + 3) / 4);
        int crystals = item instanceof MachineItem machine ? switch (machine.op()) {
            case EMBEDDING, POSITION, LINEAR, NORM -> 1;
            case ATTENTION, MLP -> 2;
            case TRANSFORMER -> 4;
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
        boolean ready = target != null && enough(cost) && room(product);
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
