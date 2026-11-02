package io.github.metrovoc.residual.energy;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.LinkItem;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Control;
import io.github.metrovoc.residual.learn.Played;
import io.github.metrovoc.residual.learn.Precision;
import io.github.metrovoc.residual.learn.Reckoning;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.model.Models;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * The Smelter: smelts anything a furnace does on power instead of fuel, {@link #RATE} FE a tick for {@link #TIME}
 * ticks an item, and makes two of what one ore or raw ore gives. With a controller (a model in its controller slot), each
 * ore is a melt it holds in its band, asked for the power every {@link #LOOK_EVERY} ticks; a melt held
 * ({@link Control.Melt#held}) makes three.
 */
public class SmelterBlockEntity extends PowerBlockEntity implements MenuProvider {
    public static final int RATE = 80, TIME = 100, CAPACITY = 16_000, IN_RATE = 2_000;
    public static final int IN = 0, OUT = 1, CONTROLLER = 2, SLOTS = 3;
    public static final int LOOK_EVERY = 4;

    private static final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> RECIPES = RecipeManager.createCheck(RecipeType.SMELTING);
    private final Items items = new Items();
    private int progress;
    /** The melt its controller is holding, while it smelts an ore with one. */
    @Nullable
    private Played.Episode melt;
    /** An assay waiting for its controller's reckoning, once a melt at the mark has run its course. */
    @Nullable
    private Reckoning.Sum assay;
    private final Random assays = new Random();
    private final Random rng = new Random();
    private boolean asking;
    private int request;
    /** The most an ore can give, and what the melt under way has been told it gives; 0 until then. */
    public static final int MOST = 9;
    private int factor;
    /** Assays reckoned right, of all it has asked about since it loaded, and how many an ore gave last. */
    private int assayed, assessed, lastGave;
    /** How much of its last melt the controller held in the band, in percent; -1 for none. */
    private int lastHeld = -1;
    private final ContainerData data = PowerMenu.data(this, () -> progress, () -> TIME, () -> progress > 0 ? RATE : 0,
            this::meltData);

    public SmelterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMELTER.get(), pos, state, CAPACITY, IN_RATE, 0);
    }

    @Override
    public Role role() {
        return Role.MACHINE;
    }

    public WorldlyContainer items() {
        return items;
    }

    /** The step its melt's temperature is on, or -1 with no melt. */
    public int temperature() {
        return melt == null || melt.look() == 0 ? -1 : melt.band(melt.look() - 1);
    }

    /**
     * What smelting {@code stack} gives here, {@code factor} of what a furnace gives from an ore: two with no melt or one that got away, three from a melt held in its band, six from one
     * held at its mark, nine if the assay after that was reckoned right.
     */
    ItemStack result(ServerLevel level, ItemStack stack, int factor) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        SingleRecipeInput input = new SingleRecipeInput(stack);
        Optional<ItemStack> made = RECIPES.getRecipeFor(input, level).map(r -> r.value().assemble(input));
        return made.map(out -> doubled(stack) ? out.copyWithCount(out.getCount() * factor) : out).orElse(ItemStack.EMPTY);
    }

    ItemStack result(ServerLevel level, ItemStack stack) {
        return result(level, stack, 2);
    }

    private static boolean doubled(ItemStack stack) {
        return stack.is(Tags.Items.ORES) || stack.is(Tags.Items.RAW_MATERIALS);
    }


    @Override
    protected void tick(ServerLevel level) {
        super.tick(level);
        ItemStack in = items.getItem(IN), out = items.getItem(OUT);
        // room for the most a melt could make, so a held melt never stalls
        ItemStack most = result(level, in, MOST);
        boolean room = !most.isEmpty() && (out.isEmpty() || ItemStack.isSameItemSameComponents(out, most)
                && out.getCount() + most.getCount() <= out.getMaxStackSize());
        int stored = energy().getAmountAsInt();
        if (!room) {
            progress = 0;
            melt = null;
        } else if (stored >= RATE) {
            if (progress == 0 && melt == null && doubled(in) && !items.getItem(CONTROLLER).isEmpty()) {
                ModelInfo info = ModelLink.of(level, worldPosition, items.getItem(CONTROLLER)).info();
                melt = info != null && info.passes(Benchmark.PRECISION) ? new Precision.Melt(Control.ore(in)) : new Control.Melt(Control.ore(in));
            }
            if (melt != null && !melt.over() && progress == melt.look() * LOOK_EVERY) {
                // the melt needs its next power before it goes on
                if (!asking) {
                    ask(level);
                }
                working(true);
                return;
            }
            if (progress + 1 >= TIME && melt instanceof Precision.Melt && melt.held() && factor == 0) {
                // at the mark: one thing more decides how much it gives, if the controller can reckon
                if (!asking) {
                    assess(level);
                }
                working(true);
                return;
            }
            energy().set(stored - RATE);
            if (++progress >= TIME) {
                progress = 0;
                boolean held = melt != null && melt.held();
                int gives = !held ? 2 : melt instanceof Precision.Melt ? Math.max(factor, 6) : 3;
                lastGave = doubled(in) ? gives : 0;
                lastHeld = melt == null ? -1 : (int) Math.round(100 * melt.score());
                melt = null;
                factor = 0;
                ItemStack made = result(level, in, gives);
                if (doubled(in)) {
                    for (ServerPlayer player : level.players()) {
                        if (player.distanceToSqr(worldPosition.getCenter()) < 16 * 16) {
                            Awards.grant(player, "power/smelter");
                        }
                    }
                }
                in.shrink(1);
                if (out.isEmpty()) {
                    items.setItem(OUT, made);
                } else {
                    out.grow(made.getCount());
                }
                items.setChanged();
            }
        }
        working(room && stored >= RATE);
    }

    /**
     * An assay comes off a melt held at its mark, a line of numbers to add up, and a controller that reckons can use it: if it does, the melt gives nine for an ore. Anything else gives six.
     * A controller of Reckoning's standard is asked; its answer is checked, here, against the sum.
     */
    private void assess(ServerLevel level) {
        ModelLink link = ModelLink.of(level, worldPosition, items.getItem(CONTROLLER));
        ModelInfo info = link.info();
        if (link instanceof ModelLink.Missing || info == null || !info.passes(Benchmark.RECKONING)) {
            factor = 6;
            return;
        }
        asking = true;
        int id = ++request;
        Reckoning.Sum sum = Reckoning.sum(assays, false);
        link.ask(level, Reckoning.prompt(sum), Reckoning.WRITTEN, '\n', answer -> {
            if (id != request || isRemoved()) {
                return;
            }
            asking = false;
            boolean right = answer != null && Reckoning.right(sum, answer.text());
            factor = right ? MOST : 6;
            assayed += right ? 1 : 0;
            assessed++;
            if (right) {
                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(worldPosition.getCenter()) < 16 * 16) {
                        Awards.grant(player, "reason/nine");
                    }
                }
            }
        });
    }

    /** Asks its controller for the melt's next power; with no controller to ask, the melt goes on uncontrolled. */
    private void ask(ServerLevel level) {
        ModelLink link = ModelLink.of(level, worldPosition, items.getItem(CONTROLLER));
        if (link instanceof ModelLink.Missing) {
            melt = null;
            return;
        }
        asking = true;
        int id = ++request;
        String prompt = Control.prompt(melt);
        Function<CompiledGraph, Integer> work = model -> Control.act(model, prompt);
        Consumer<@Nullable Integer> reply = power -> {
            if (id != request || isRemoved()) {
                return;
            }
            asking = false;
            if (power == null) {
                melt = null;
            } else if (melt != null) {
                melt.act(power, rng);
            }
        };
        switch (link) {
            case ModelLink.Served s -> s.server().submit(work, reply);
            case ModelLink.Onboard o -> Models.submit(level.getServer(), o.info().id(), work, reply);
            case ModelLink.Missing m -> asking = false;
        }
    }

    /**
     * The melt for its screen: looks taken, the last melt's share held (+1, 0 for none), then the temperatures so far,
     * five 3-bit bands to a data slot.
     */
    /** How many of what a furnace makes its last ore gave, 0 if none has yet. */
    public int lastGave() {
        return lastGave;
    }

    /** Assays reckoned right since it loaded. */
    public int assayed() {
        return assayed;
    }

    /** Assays asked about since it loaded. */
    public int assessed() {
        return assessed;
    }

    /** Ticks into the ingot it is making, for a Forecaster's look. */
    public int progress() {
        return progress;
    }

    private int meltData(int i) {
        if (i == 0) {
            return melt == null ? 0 : melt.look();
        }
        if (i == 1) {
            return lastHeld + 1;
        }
        int packed = 0;
        if (melt != null) {
            for (int k = 0; k < 5; k++) {
                int look = (i - 2) * 5 + k;
                if (look < melt.look()) {
                    packed |= melt.band(look) << (3 * k);
                }
            }
        }
        return packed;
    }

    private void working(boolean on) {
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
        return new PowerMenu(id, inventory, worldPosition, PowerMenu.Kind.SMELTER, items, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out.child("items"), items.getItems());
        out.putInt("progress", progress);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("items").ifPresent(c -> ContainerHelper.loadAllItems(c, items.getItems()));
        progress = in.getIntOr("progress", 0);
    }

    /** What goes in from any side is what smelts; only what it made comes out. */
    private final class Items extends SimpleContainer implements WorldlyContainer {
        Items() {
            super(SLOTS);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (slot == CONTROLLER) {
                return stack.getItem() instanceof CrystalItem || stack.getItem() instanceof LinkItem;
            }
            return slot == IN && level instanceof ServerLevel server ? !result(server, stack).isEmpty() : slot == IN;
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            return new int[] {IN, OUT};
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
            return slot == IN && canPlaceItem(slot, stack);
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
            return slot == OUT;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            SmelterBlockEntity.this.setChanged();
        }
    }
}
