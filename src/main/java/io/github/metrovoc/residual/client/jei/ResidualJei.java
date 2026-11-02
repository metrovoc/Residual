package io.github.metrovoc.residual.client.jei;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.client.AgentScreen;
import io.github.metrovoc.residual.client.AssemblerScreen;
import io.github.metrovoc.residual.client.PortProgramScreen;
import io.github.metrovoc.residual.client.PortScreen;
import io.github.metrovoc.residual.client.ProcessorScreen;
import io.github.metrovoc.residual.client.RecipeViewer;
import io.github.metrovoc.residual.client.StorageTerminalScreen;
import io.github.metrovoc.residual.net.GhostSlot;
import io.github.metrovoc.residual.net.PatternTransfer;
import io.github.metrovoc.residual.net.StorageTransfer;
import io.github.metrovoc.residual.registry.ModMenus;
import io.github.metrovoc.residual.storage.AssemblerBlockEntity;
import io.github.metrovoc.residual.storage.AssemblerMenu;
import io.github.metrovoc.residual.storage.PortBlockEntity;
import io.github.metrovoc.residual.storage.ProcessorMenu;
import io.github.metrovoc.residual.storage.TerminalMenu;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * What the recipe viewer can do with storage, when it is there: the same search in both boxes, a crafting recipe laid into a
 * Storage Terminal's grid from what the network keeps, marking what is missing and with Ctrl asking for it, any recipe drawn
 * on an Assembler or a Processor, things dragged onto slots of pictures, and recipes looked up from a terminal's grid.
 */
@JeiPlugin
public class ResidualJei implements IModPlugin {
    private static final int MISSING = 0x66FF6B6B;

    @Override
    public Identifier getPluginUid() {
        return Residual.id("jei");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        RecipeViewer.connect(runtime.getIngredientFilter()::getFilterText, runtime.getIngredientFilter()::setFilterText);
    }

    @Override
    public void onRuntimeUnavailable() {
        RecipeViewer.disconnect();
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new TerminalTransfer(), RecipeTypes.CRAFTING);
        registration.addRecipeTransferHandler(new AssemblerTransfer(), RecipeTypes.CRAFTING);
        registration.addUniversalRecipeTransferHandler(new ProcessorTransfer());
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(AgentScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(AgentScreen screen) {
                return screen.extraAreas();
            }
        });
        registration.addGuiContainerHandler(PortProgramScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(PortProgramScreen screen) {
                return screen.extraAreas();
            }
        });
        registration.addGuiContainerHandler(StorageTerminalScreen.class, new IGuiContainerHandler<>() {
            @Override
            public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(IClickableIngredientFactory builder,
                    StorageTerminalScreen screen, double mouseX, double mouseY) {
                Object under = screen.under(mouseX, mouseY);
                Rect2i area = screen.cellUnder(mouseX, mouseY);
                if (under instanceof ItemStack stack) {
                    return builder.createBuilder(stack).buildWithArea(area);
                }
                return under instanceof FluidStack fluid ? builder.createBuilder(NeoForgeTypes.FLUID_STACK, fluid).buildWithArea(area) : Optional.empty();
            }
        });
        registration.addGhostIngredientHandler(PortScreen.class, new Pictures<>(PortBlockEntity.FILTERS));
        registration.addGhostIngredientHandler(AssemblerScreen.class, new Pictures<>(AssemblerBlockEntity.GRID));
        registration.addGhostIngredientHandler(ProcessorScreen.class, new Pictures<>(ProcessorMenu.CELLS));
    }

    /** What a slot of a recipe can be, first choice first. */
    private static List<ItemResource> options(IRecipeSlotView slot) {
        return slot.getItemStacks().map(ItemResource::of).distinct().toList();
    }

    /** Pictures of what a recipe's slots hold, as shown. */
    private static List<ItemStack> shown(IRecipeSlotsView slots, RecipeIngredientRole role) {
        List<ItemStack> out = new ArrayList<>();
        for (IRecipeSlotView slot : slots.getSlotViews(role)) {
            out.add(slot.getDisplayedItemStack().map(ItemStack::copy).orElse(ItemStack.EMPTY));
        }
        return out;
    }

    /** The slots of the first {@code count} of a screen's, which hold pictures: dragging a thing there clicks with it. */
    private record Pictures<T extends AbstractContainerScreen<?>>(int count) implements IGhostIngredientHandler<T> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(T screen, ITypedIngredient<I> ingredient, boolean doStart) {
            ItemStack stack = ingredient.getIngredient(VanillaTypes.ITEM_STACK).map(ItemStack::copy)
                    .or(() -> ingredient.getIngredient(NeoForgeTypes.FLUID_STACK).map(f -> new ItemStack(f.getFluid().getBucket())))
                    .filter(s -> !s.isEmpty()).orElse(ItemStack.EMPTY);
            List<Target<I>> targets = new ArrayList<>();
            if (!stack.isEmpty()) {
                AbstractContainerMenu menu = screen.getMenu();
                for (int i = 0; i < count; i++) {
                    Slot slot = menu.getSlot(i);
                    int index = i;
                    targets.add(new Target<>() {
                        @Override
                        public Rect2i getArea() {
                            return new Rect2i(screen.getLeftPos() + slot.x, screen.getTopPos() + slot.y, 16, 16);
                        }

                        @Override
                        public void accept(I what) {
                            ClientPacketDistributor.sendToServer(new GhostSlot(menu.containerId, index, stack));
                        }
                    });
                }
            }
            return targets;
        }

        @Override
        public void onComplete() {
        }
    }

    /** What is missing is marked, and the recipe still goes in as far as it can. */
    private record Marked(List<IRecipeSlotView> missing, boolean canAsk) implements IRecipeTransferError {
        @Override
        public Type getType() {
            return Type.COSMETIC;
        }

        @Override
        public void showError(GuiGraphicsExtractor g, int mouseX, int mouseY, IRecipeSlotsView slots, int recipeX, int recipeY) {
            g.pose().pushMatrix();
            g.pose().translate(recipeX, recipeY);
            missing.forEach(slot -> slot.drawHighlight(g, MISSING));
            g.pose().popMatrix();
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip) {
            tooltip.add(Component.translatable("residual.jei.missing"));
            if (canAsk) {
                tooltip.add(Component.translatable("residual.jei.ask"));
            }
        }
    }

    /** A crafting recipe into a terminal's crafting grid. */
    private static final class TerminalTransfer implements IRecipeTransferHandler<TerminalMenu, RecipeHolder<CraftingRecipe>> {
        @Override
        public Class<? extends TerminalMenu> getContainerClass() {
            return TerminalMenu.class;
        }

        @Override
        public Optional<MenuType<TerminalMenu>> getMenuType() {
            return Optional.of(ModMenus.TERMINAL.get());
        }

        @Override
        public IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Nullable
        @Override
        public IRecipeTransferError transferRecipe(TerminalMenu menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slots, Player player,
                                                   boolean maxTransfer, boolean doTransfer) {
            return transfer(menu, slots, player, maxTransfer, doTransfer,
                    Minecraft.getInstance().screen instanceof StorageTerminalScreen screen ? screen : null, Minecraft.getInstance().hasControlDown());
        }

        @Nullable
        @Override
        public IRecipeTransferError transferRecipe(IRecipeTransferContext<RecipeHolder<CraftingRecipe>, TerminalMenu> context, boolean doTransfer) {
            return transfer(context.getContainer(), context.getRecipeSlots(), context.getPlayer(), context.isMaxTransfer(), doTransfer,
                    context.getScreen() instanceof StorageTerminalScreen screen ? screen : null, Minecraft.getInstance().hasControlDown());
        }

        @Nullable
        private IRecipeTransferError transfer(TerminalMenu menu, IRecipeSlotsView slots, Player player, boolean maxTransfer,
                                             boolean doTransfer, @Nullable StorageTerminalScreen screen, boolean requestMissing) {
            List<List<ItemResource>> cells = new ArrayList<>();
            List<IRecipeSlotView> missing = new ArrayList<>();
            Map<ItemResource, Long> remaining = new HashMap<>();
            ItemResource craftable = null;
            for (IRecipeSlotView slot : slots.getSlotViews(RecipeIngredientRole.INPUT)) {
                List<ItemResource> options = options(slot);
                cells.add(options);
                ItemResource chosen = options.stream().filter(kind -> remaining.computeIfAbsent(kind,
                        k -> menu.availableForCrafting(k, player)) > 0).findFirst().orElse(null);
                if (chosen != null) {
                    remaining.put(chosen, remaining.get(chosen) - 1);
                } else if (!options.isEmpty()) {
                    missing.add(slot);
                    if (craftable == null) {
                        craftable = options.stream().filter(menu.craftable::contains).findFirst().orElse(null);
                    }
                }
            }
            if (!doTransfer) {
                return missing.isEmpty() ? null : new Marked(missing, craftable != null);
            }
            if (craftable != null && requestMissing && screen != null) {
                screen.askFor(craftable);
                return null;
            }
            ClientPacketDistributor.sendToServer(new StorageTransfer(menu.containerId, cells, maxTransfer));
            return null;
        }
    }

    /** A crafting recipe drawn on an Assembler's grid. */
    private static final class AssemblerTransfer implements IRecipeTransferHandler<AssemblerMenu, RecipeHolder<CraftingRecipe>> {
        @Override
        public Class<? extends AssemblerMenu> getContainerClass() {
            return AssemblerMenu.class;
        }

        @Override
        public Optional<MenuType<AssemblerMenu>> getMenuType() {
            return Optional.of(ModMenus.ASSEMBLER.get());
        }

        @Override
        public IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Nullable
        @Override
        public IRecipeTransferError transferRecipe(AssemblerMenu menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slots, Player player,
                                                   boolean maxTransfer, boolean doTransfer) {
            if (doTransfer) {
                ClientPacketDistributor.sendToServer(new PatternTransfer(menu.containerId, shown(slots, RecipeIngredientRole.INPUT), List.of()));
            }
            return null;
        }
    }

    /** Any recipe drawn as a Processor's pattern: its inputs and outputs, with their amounts. */
    private static final class ProcessorTransfer implements IUniversalRecipeTransferHandler<ProcessorMenu> {
        @Override
        public Class<? extends ProcessorMenu> getContainerClass() {
            return ProcessorMenu.class;
        }

        @Override
        public Optional<MenuType<ProcessorMenu>> getMenuType() {
            return Optional.of(ModMenus.PROCESSOR.get());
        }

        @Nullable
        @Override
        public IRecipeTransferError transferRecipe(ProcessorMenu menu, Object recipe, IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
            if (doTransfer) {
                ClientPacketDistributor.sendToServer(new PatternTransfer(menu.containerId, shown(slots, RecipeIngredientRole.INPUT),
                        shown(slots, RecipeIngredientRole.OUTPUT)));
            }
            return null;
        }
    }
}
