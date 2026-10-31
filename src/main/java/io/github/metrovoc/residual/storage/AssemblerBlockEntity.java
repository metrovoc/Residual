package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.List;
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

/**
 * An Assembler at work. Its recipe is a 3×3 grid of kinds, drawn with ghost items and read by the game's own crafting
 * recipes. While its network holds fewer of the result than it is told to keep, it takes the ingredients from the
 * network and puts the result back, up to {@link #CRAFTS} crafts a tick; leftovers such as empty buckets go back too.
 */
public class AssemblerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int GRID = 9;
    /** Crafts an Assembler makes a tick: 80 a second. */
    public static final int CRAFTS = 4;
    public static final int MOST = 1 << 20;
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
    private Optional<RecipeHolder<CraftingRecipe>> recipe = Optional.empty();
    private boolean looked;
    /** Players with its screen open. */
    int viewers;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case KEEP -> keep;
                case HELD -> (int) Math.min(Integer.MAX_VALUE, held);
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
        result.setItem(0, recipe.map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY));
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
            return;
        }
        if (viewers > 0) {
            waiting = PAUSED;
            return;
        }
        StorageNetwork net = StorageNetwork.at(level, worldPosition);
        ItemResource made = ItemResource.of(out);
        long have = net.count(made);
        held = (int) Math.min(Integer.MAX_VALUE, have);
        if (have >= keep) {
            waiting = ENOUGH;
            return;
        }
        // what one craft takes, kind by kind
        Object2IntLinkedOpenHashMap<ItemResource> needs = new Object2IntLinkedOpenHashMap<>();
        for (int i = 0; i < GRID; i++) {
            ItemStack s = grid.getItem(i);
            if (!s.isEmpty()) {
                needs.addTo(ItemResource.of(s), 1);
            }
        }
        long crafts = Math.min(CRAFTS, (keep - have + out.getCount() - 1) / out.getCount());
        for (Object2IntMap.Entry<ItemResource> need : needs.object2IntEntrySet()) {
            crafts = Math.min(crafts, net.extract(need.getKey(), need.getIntValue() * crafts, true) / need.getIntValue());
        }
        if (crafts <= 0) {
            waiting = MISSING;
            return;
        }
        waiting = CRAFTING;
        CraftingInput input = input();
        NonNullList<ItemStack> leftovers = recipe.get().value().getRemainingItems(input);
        for (Object2IntMap.Entry<ItemResource> need : needs.object2IntEntrySet()) {
            net.extract(need.getKey(), need.getIntValue() * crafts, false);
        }
        put(level, net, made, out.getCount() * crafts);
        for (ItemStack left : leftovers) {
            if (!left.isEmpty()) {
                put(level, net, ItemResource.of(left), left.getCount() * crafts);
            }
        }
        madeThisSecond += (int) crafts;
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
