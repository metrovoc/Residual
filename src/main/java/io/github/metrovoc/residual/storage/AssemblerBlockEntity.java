package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.world.Cooling;
import io.github.metrovoc.residual.world.GraphScanner;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * An Assembler at work. Its recipe is a 3×3 grid of kinds, drawn with ghost items and read by the game's own crafting
 * recipes. While its network holds fewer of the result than it is told to keep, it takes the ingredients from the
 * network and puts the result back, up to {@link #CRAFTS} crafts a tick for itself and for each Accelerator touching it,
 * directly or through each other, kept cool; leftovers such as empty buckets go back too.
 */
public class AssemblerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int GRID = 9;
    /** Crafts an Assembler makes a tick: 80 a second. */
    public static final int CRAFTS = 4;
    /** The most it can be told to keep: a menu's numbers travel as shorts. */
    public static final int MOST = Short.MAX_VALUE;
    /** Container data: how many to keep, how many the network holds, crafts in the last second, and why it waits. */
    public static final int KEEP = 0, HELD = 1, MADE = 2, WAITING = 3, DATA = 4;
    /**
     * Why it is not crafting: it is not, it has no recipe, the network has enough, an ingredient is missing, or its
     * screen is open, so that a recipe half drawn never crafts.
     */
    public static final int CRAFTING = 0, NO_RECIPE = 1, ENOUGH = 2, MISSING = 3, PAUSED = 4;

    private final SimpleContainer grid = new SimpleContainer(GRID) {
        @Override
        public void setChanged() {
            super.setChanged();
            AssemblerBlockEntity.this.recipeChanged();
        }
    };
    /** What the recipe makes, shown in the screen's result slot. */
    private final SimpleContainer result = new SimpleContainer(1);
    private int keep = 64;
    private int held;
    private int madeThisSecond, madeLastSecond;
    private int waiting = NO_RECIPE;
    /** Crafts it can make a tick, found again every second. */
    private int crafts = CRAFTS;
    private Optional<RecipeHolder<CraftingRecipe>> recipe = Optional.empty();
    @Nullable
    private CraftPlan.Recipe<ItemResource> planned;
    private boolean looked;
    /** Players with its screen open. */
    int viewers;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case KEEP -> keep;
                case HELD -> Math.min(Short.MAX_VALUE, held);
                case MADE -> madeLastSecond;
                case WAITING -> waiting;
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

    public AssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AssemblerBlockEntity be) {
        be.tick((ServerLevel) level, state);
    }

    private CraftingInput input() {
        List<ItemStack> items = new ArrayList<>(GRID);
        for (int i = 0; i < GRID; i++) {
            items.add(grid.getItem(i).copyWithCount(1));
        }
        return CraftingInput.of(3, 3, items);
    }

    /** Finds the recipe its grid draws, and shows what it makes. */
    private void look(ServerLevel level) {
        looked = true;
        CraftingInput input = input();
        recipe = input.isEmpty() ? Optional.empty() : level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
        ItemStack out = recipe.map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
        result.setItem(0, out);
        planned = null;
        if (recipe.isPresent() && !out.isEmpty()) {
            Map<ItemResource, Integer> inputs = new LinkedHashMap<>(), leftovers = new LinkedHashMap<>();
            for (int i = 0; i < GRID; i++) {
                if (!grid.getItem(i).isEmpty()) {
                    inputs.merge(ItemResource.of(grid.getItem(i)), 1, Integer::sum);
                }
            }
            for (ItemStack left : recipe.get().value().getRemainingItems(input)) {
                if (!left.isEmpty()) {
                    leftovers.merge(ItemResource.of(left), left.getCount(), Integer::sum);
                }
            }
            planned = new CraftPlan.Recipe<>(ItemResource.of(out), out.getCount(), inputs, leftovers);
        }
    }

    /** What it makes and from what, as a network's planner sees it; null without a recipe. */
    @Nullable
    public CraftPlan.Recipe<ItemResource> planned() {
        return planned;
    }

    private void recipeChanged() {
        setChanged();
        if (level instanceof ServerLevel server) {
            look(server);
        }
    }

    private void tick(ServerLevel level, BlockState state) {
        if (!looked) {
            look(level);
        }
        if (level.getGameTime() % 20 == 0) {
            madeLastSecond = madeThisSecond;
            madeThisSecond = 0;
            boolean working = madeLastSecond > 0;
            crafts = CRAFTS * Cooling.compute(level, worldPosition, 1, GraphScanner.MAX_ACCELERATORS + 1).threads();
            Cooling.working(level, worldPosition, working);
            if (working) {
                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(worldPosition.getCenter()) < 32 * 32) {
                        Awards.grant(player, "storage/assembler");
                    }
                }
            }
            if (state.getValue(AssemblerBlock.WORKING) != working) {
                level.setBlock(worldPosition, state.setValue(AssemblerBlock.WORKING, working), Block.UPDATE_CLIENTS);
            }
        }
        ItemStack out = result.getItem(0);
        if (recipe.isEmpty() || out.isEmpty()) {
            waiting = NO_RECIPE;
            // jobs it owes crafts to learn that it no longer makes their part
            CraftJobs.work(level, this, null, crafts);
            return;
        }
        if (viewers > 0) {
            waiting = PAUSED;
            return;
        }
        StorageNetwork net = StorageNetwork.at(level, worldPosition);
        // crafts players asked for come first; what is left of the tick's crafts keeps the network's stock
        int budget = CraftJobs.work(level, this, net, crafts);
        ItemResource made = ItemResource.of(out);
        long have = net.count(made);
        held = (int) Math.min(Integer.MAX_VALUE, have);
        if (budget < crafts) {
            waiting = CRAFTING;
        } else if (have >= keep) {
            waiting = ENOUGH;
            return;
        }
        if (budget == 0 || have >= keep) {
            return;
        }
        long madeNow = craft(level, net, Math.min(budget, (keep - have + out.getCount() - 1) / out.getCount()), null);
        if (madeNow > 0) {
            waiting = CRAFTING;
        } else if (budget == crafts) {
            waiting = MISSING;
        }
    }

    /**
     * Makes up to {@code n} crafts, as many as the ingredients allow, and returns how many. Each ingredient comes from
     * the job's own stock first, then from the network; what the crafts make goes to the job, or without one to the
     * network; what they leave, such as empty buckets, goes to the network.
     */
    long craft(ServerLevel level, StorageNetwork net, long n, @Nullable CraftJob job) {
        CraftPlan.Recipe<ItemResource> r = planned;
        if (r == null || n <= 0) {
            return 0;
        }
        long asked = n;
        for (Map.Entry<ItemResource, Integer> need : r.inputs().entrySet()) {
            long have = (job == null ? 0 : job.held.getLong(need.getKey())) + net.extract(need.getKey(), need.getValue() * asked, true);
            long enough = have / need.getValue();
            if (enough < n) {
                n = enough;
                if (job != null) {
                    // what it lacks for every craft it still owes the job, not only this tick's
                    long owed = job.tasks.get(worldPosition).left;
                    job.waitingFor = need.getKey();
                    job.waitingCount = need.getValue() * owed - job.held.getLong(need.getKey()) - net.count(need.getKey());
                }
            }
        }
        if (n <= 0) {
            return 0;
        }
        if (job != null && n == asked) {
            job.waitingFor = null;
        }
        for (Map.Entry<ItemResource, Integer> need : r.inputs().entrySet()) {
            long want = need.getValue() * n;
            long fromJob = job == null ? 0 : job.take(need.getKey(), want);
            net.extract(need.getKey(), want - fromJob, false);
        }
        if (job != null) {
            job.hold(r.output(), r.made() * n);
        } else {
            put(level, net, r.output(), r.made() * n);
        }
        BlockPos to = net.lastStore;
        for (Map.Entry<ItemResource, Integer> left : r.leftovers().entrySet()) {
            put(level, net, left.getKey(), left.getValue() * n);
        }
        madeThisSecond += (int) n;
        if (level.getGameTime() % Flows.EVERY == Math.floorMod(worldPosition.asLong(), Flows.EVERY)) {
            Flows.send(level, worldPosition, to, true, (int) n * r.made());
        }
        return n;
    }

    /** Puts what it made into the network, dropping what has no room as a last resort. */
    private void put(ServerLevel level, StorageNetwork net, ItemResource item, long n) {
        long stored = net.insert(item, n, false);
        if (stored < n) {
            Block.popResource(level, worldPosition.above(), item.toStack((int) Math.min(n - stored, item.getMaxStackSize())));
        }
    }

    public void setKeep(int keep) {
        this.keep = Math.clamp(keep, 0, MOST);
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            StorageNetwork.changed(server);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel server) {
            StorageNetwork.changed(server);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.residual.assembler");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AssemblerMenu(id, inventory, worldPosition, grid, result, data, this);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out.child("grid"), grid.getItems());
        out.putInt("keep", keep);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("grid").ifPresent(c -> ContainerHelper.loadAllItems(c, grid.getItems()));
        keep = in.getIntOr("keep", 64);
        looked = false;
    }
}
