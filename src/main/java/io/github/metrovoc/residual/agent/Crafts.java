package io.github.metrovoc.residual.agent;

import io.github.metrovoc.residual.block.FabricatorBlockEntity;
import io.github.metrovoc.residual.energy.SmelterBlockEntity;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import org.jetbrains.annotations.Nullable;

/** The game's crafting recipes as a body uses them in its hands, and as a program asks about them. */
final class Crafts {
    /** A recipe laid out on a grid, with what it makes from its first choice of each ingredient. */
    record Entry(CraftingRecipe recipe, int width, int height, List<Optional<Ingredient>> grid, ItemStack result, List<ItemStack> inputs) {
    }

    /** A recipe as a program asks about it: what each of its ingredients can be, what it takes by first choice, what it makes. */
    record Made(List<Ingredient> takes, List<ItemStack> inputs, ItemStack result) {
        boolean uses(ItemStack thing) {
            return takes.stream().anyMatch(i -> i.test(thing));
        }
    }

    private static final Map<RecipeManager, List<Entry>> KNOWN = new WeakHashMap<>();
    private static final Map<RecipeManager, Map<RecipeType<?>, List<Made>>> COOKED = new WeakHashMap<>();

    private Crafts() {
    }

    /** Every crafting recipe that is plain enough to lay out. */
    static List<Entry> all(ServerLevel level) {
        return KNOWN.computeIfAbsent(level.recipeAccess(), m -> read(level, m));
    }

    private static List<Entry> read(ServerLevel level, RecipeManager manager) {
        List<Entry> out = new ArrayList<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (!(holder.value() instanceof CraftingRecipe recipe)) {
                continue;
            }
            PlacementInfo info = recipe.placementInfo();
            if (info.isImpossibleToPlace() || info.ingredients().isEmpty()) {
                continue;
            }
            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
            int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : 3;
            List<Optional<Ingredient>> grid = new ArrayList<>();
            info.slotsToIngredientIndex().forEach(i -> grid.add(i < 0 ? Optional.empty() : Optional.of(info.ingredients().get(i))));
            while (grid.size() < width * height) {
                grid.add(Optional.empty());
            }
            List<ItemStack> first = new ArrayList<>();
            boolean plain = true;
            for (Optional<Ingredient> slot : grid) {
                ItemStack choice = slot.flatMap(x -> x.items().findFirst()).map(Holder::value).map(Item::getDefaultInstance).orElse(ItemStack.EMPTY);
                plain &= slot.isEmpty() || !choice.isEmpty();
                first.add(choice);
            }
            if (!plain) {
                continue;
            }
            CraftingInput input = CraftingInput.of(width, height, first);
            if (!recipe.matches(input, level)) {
                continue;
            }
            ItemStack result = recipe.assemble(input);
            if (!result.isEmpty()) {
                out.add(new Entry(recipe, width, height, List.copyOf(grid), result, first.stream().filter(s -> !s.isEmpty()).toList()));
            }
        }
        return out;
    }

    /** The recipes a machine works by: a furnace's smelting, a smoker's smoking, a Fabricator's mask; for anything else, crafting. */
    static List<Made> made(ServerLevel level, @Nullable BlockEntity machine) {
        RecipeType<?> type = switch (machine) {
            case BlastFurnaceBlockEntity b -> RecipeType.BLASTING;
            case SmokerBlockEntity s -> RecipeType.SMOKING;
            case AbstractFurnaceBlockEntity f -> RecipeType.SMELTING;
            case SmelterBlockEntity s -> RecipeType.SMELTING;
            case CampfireBlockEntity c -> RecipeType.CAMPFIRE_COOKING;
            case null, default -> null;
        };
        if (machine instanceof FabricatorBlockEntity f) {
            return fabricated(f);
        }
        if (type == null) {
            return all(level).stream().map(e -> new Made(e.grid().stream().flatMap(Optional::stream).toList(), e.inputs(), e.result())).toList();
        }
        return COOKED.computeIfAbsent(level.recipeAccess(), m -> new HashMap<>()).computeIfAbsent(type, t -> cooked(level, t));
    }

    private static List<Made> cooked(ServerLevel level, RecipeType<?> type) {
        List<Made> out = new ArrayList<>();
        for (RecipeHolder<?> holder : level.recipeAccess().getRecipes()) {
            if (holder.value() instanceof SingleItemRecipe recipe && recipe.getType() == type) {
                ItemStack first = recipe.input().items().findFirst().map(Holder::value).map(Item::getDefaultInstance).orElse(ItemStack.EMPTY);
                if (!first.isEmpty()) {
                    out.add(new Made(List.of(recipe.input()), List.of(first), recipe.assemble(new SingleRecipeInput(first))));
                }
            }
        }
        return out;
    }

    /** What a Fabricator can make with the mask in it. */
    private static List<Made> fabricated(FabricatorBlockEntity f) {
        ItemStack mask = f.mask();
        List<Made> out = new ArrayList<>();
        for (Item item : FabricatorBlockEntity.choices(mask)) {
            FabricatorBlockEntity.Cost cost = FabricatorBlockEntity.cost(item, mask);
            List<ItemStack> takes = new ArrayList<>(List.of(mask.copyWithCount(1)));
            if (cost.casings() > 0) {
                takes.add(new ItemStack(ModBlocks.CASING.asItem(), cost.casings()));
            }
            if (cost.wafers() > 0) {
                takes.add(new ItemStack(ModItems.WAFER.get(), cost.wafers()));
            }
            if (cost.crystals() > 0) {
                takes.add(new ItemStack(ModItems.WEIGHT_CRYSTAL.get(), cost.crystals()));
            }
            out.add(new Made(takes.stream().map(s -> Ingredient.of(s.getItem())).toList(), takes, FabricatorBlockEntity.product(item, mask)));
        }
        return out;
    }

    /**
     * Makes up to {@code want} of what {@code which} names with what {@code a} carries, as a crafting turtle does; returns
     * how many it made, and leaves its hands as they were if it could make none.
     */
    static int make(Body a, ServerLevel level, Predicate<ItemStack> which, int want) {
        int made = 0;
        while (made < want) {
            int once = 0;
            for (Entry e : all(level)) {
                if (which.test(e.result()) && (once = craftOnce(a, level, e)) > 0) {
                    break;
                }
            }
            if (once == 0) {
                break;
            }
            made += once;
        }
        return made;
    }

    private static int craftOnce(Body a, ServerLevel level, Entry e) {
        Container items = a.items();
        int[] used = new int[a.carry()];
        List<ItemStack> grid = new ArrayList<>();
        for (Optional<Ingredient> slot : e.grid()) {
            if (slot.isEmpty()) {
                grid.add(ItemStack.EMPTY);
                continue;
            }
            int found = -1;
            for (int i = 0; i < a.carry() && found < 0; i++) {
                ItemStack in = items.getItem(i);
                if (!in.isEmpty() && in.getCount() > used[i] && slot.get().test(in)) {
                    found = i;
                }
            }
            if (found < 0) {
                return 0;
            }
            used[found]++;
            grid.add(items.getItem(found).copyWithCount(1));
        }
        CraftingInput input = CraftingInput.of(e.width(), e.height(), grid);
        if (!e.recipe().matches(input, level)) {
            return 0;
        }
        ItemStack result = e.recipe().assemble(input);
        List<ItemStack> left = new ArrayList<>(e.recipe().getRemainingItems(input));
        for (int i = 0; i < a.carry(); i++) {
            if (used[i] > 0) {
                items.getItem(i).shrink(used[i]);
            }
        }
        items.setChanged();
        int made = result.getCount();
        for (ItemStack rest : left) {
            if (!rest.isEmpty()) {
                spill(a, level, a.keep(rest));
            }
        }
        spill(a, level, a.keep(result));
        return made;
    }

    private static void spill(Body a, ServerLevel level, ItemStack rest) {
        if (!rest.isEmpty()) {
            net.minecraft.world.level.block.Block.popResource(level, a.place(), rest);
        }
    }
}
