package io.github.metrovoc.residual.rehearsal;

import io.github.metrovoc.residual.client.Parts.Part;
import io.github.metrovoc.residual.client.RecipeViewer;
import io.github.metrovoc.residual.client.StorageTerminalScreen;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Exercises the installed JEI overlay, recipe layouts, transfer service and ghost-drag handler. */
final class JeiSteps {
    private static Object dragManager, drag;
    private static Screen dragScreen;

    private JeiSteps() {
    }

    static void run(Minecraft mc, String command) throws ReflectiveOperationException {
        String[] words = command.split(" ", 2);
        String arg = words.length == 2 ? words[1] : "";
        Object runtime = call(Class.forName("mezz.jei.common.Internal"), "getJeiRuntime");
        Object overlay = call(runtime, "getIngredientListOverlay");
        switch (words[0]) {
            case "ready" -> {
                require(RecipeViewer.search() != null, "Residual's JEI search bridge is disconnected");
                require(Boolean.TRUE.equals(call(overlay, "isListDisplayed")), "JEI ingredient list is hidden");
            }
            case "clear-area" -> clearArea(mc, runtime, overlay, arg);
            case "search" -> ((EditBox) field(overlay, "searchField")).setValue(arg);
            case "expect-search" -> {
                require(arg.equals(call(call(runtime, "getIngredientFilter"), "getFilterText")), "JEI search does not equal " + arg);
                require(mc.screen instanceof StorageTerminalScreen, "terminal is not open");
                require(arg.equals(((EditBox) field(mc.screen, "search")).getValue()), "terminal search does not equal " + arg);
            }
            case "recipe" -> recipe(runtime, arg);
            case "lookup" -> lookup(mc, runtime, arg);
            case "missing" -> missing(mc, arg);
            case "hover-transfer" -> hoverTransfer(mc);
            case "transfer" -> {
                Object button = transferButton(mc);
                require(Boolean.TRUE.equals(call(button, "transferRecipe", arg.equals("max"), false)), "JEI transfer was rejected");
                require(!mc.screen.getClass().getSimpleName().equals("RecipesGui"), "JEI did not return to its parent screen");
            }
            case "ask" -> ask(mc);
            case "expect-ask" -> {
                require(mc.screen instanceof StorageTerminalScreen, "terminal is not open after asking");
                require(field(mc.screen, "view").toString().equals("ASK"), "terminal did not open its amount view");
                ItemStack asked = ((ItemResource) field(mc.screen, "asking")).toStack(1);
                require(BuiltInRegistries.ITEM.getKey(asked.getItem()).toString().equals(arg), "terminal is asking for a different ingredient");
                require(((StorageTerminalScreen) mc.screen).getMenu().plan != null, "terminal did not receive the crafting plan");
            }
            case "slot" -> {
                String[] args = arg.split(" ");
                ItemStack stack = mc.player.containerMenu.getSlot(Integer.parseInt(args[0])).getItem();
                boolean matches = args[1].startsWith("#")
                        ? stack.is(TagKey.create(Registries.ITEM, Identifier.parse(args[1].substring(1))))
                        : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(args[1]);
                require(matches && stack.getCount() == Integer.parseInt(args[2]),
                        "slot " + args[0] + " holds " + stack + ", expected " + args[1] + " × " + args[2]);
            }
            case "drag", "drag-fluid" -> beginDrag(mc, runtime, overlay, arg, words[0].equals("drag-fluid"));
            case "drop" -> drop(mc, Integer.parseInt(arg));
            case "cancel" -> reset();
            default -> throw new IllegalArgumentException("unknown jei step " + words[0]);
        }
    }

    /** Checks the actual displayed ingredient slots and corner controls against a named screen panel. */
    private static void clearArea(Minecraft mc, Object runtime, Object overlay, String name) throws ReflectiveOperationException {
        Part panel = Named.find(mc, name);
        Object bookmarks = call(runtime, "getBookmarkOverlay");
        for (Object side : List.of(overlay, bookmarks)) {
            if (Boolean.TRUE.equals(call(side, "isListDisplayed"))) {
                for (Object slot : (List<?>) call(field(side, "contents"), "getAllSlots")) {
                    if (!Boolean.TRUE.equals(call(slot, "isBlocked")) && ((Optional<?>) call(slot, "getOptionalElement")).isPresent()) {
                        clear(panel, call(slot, "getRenderArea"), "ingredient");
                    }
                }
            }
        }
        for (String button : List.of("bookmarkButton", "historyButton")) {
            Object widget = field(bookmarks, button);
            if (Boolean.TRUE.equals(call(widget, "isVisible"))) {
                clear(panel, call(widget, "getArea"), button);
            }
        }
        Object config = field(overlay, "configButton");
        if (Boolean.TRUE.equals(call(config, "isVisible"))) {
            clear(panel, call(config, "getArea"), "configButton");
        }
    }

    private static void clear(Part panel, Object area, String what) throws ReflectiveOperationException {
        int x = ((Number) call(area, "getX")).intValue(), y = ((Number) call(area, "getY")).intValue();
        int w = ((Number) call(area, "getWidth")).intValue(), h = ((Number) call(area, "getHeight")).intValue();
        require(w <= 0 || h <= 0 || x + w <= panel.x() || y + h <= panel.y() || x >= panel.x() + panel.w() || y >= panel.y() + panel.h(),
                "JEI " + what + " overlaps " + panel.name() + ": " + area);
    }

    private static void recipe(Object runtime, String args) throws ReflectiveOperationException {
        String[] words = args.split(" ");
        Object type = Class.forName("mezz.jei.api.constants.RecipeTypes").getField(words[0].toUpperCase(Locale.ROOT)).get(null);
        Object manager = call(runtime, "getRecipeManager");
        Object lookup = call(manager, "createRecipeLookup", type);
        Object recipe;
        try (Stream<?> recipes = (Stream<?>) call(lookup, "get")) {
            recipe = recipes.filter(r -> r instanceof RecipeHolder<?> holder && holder.id().identifier().toString().equals(words[1]))
                    .findFirst().orElseThrow(() -> new IllegalStateException("JEI has no recipe " + words[1]));
        }
        call(call(runtime, "getRecipesGui"), "showRecipes", call(manager, "getRecipeCategory", type), List.of(recipe), List.of());
    }

    private static void lookup(Minecraft mc, Object runtime, String id) throws ReflectiveOperationException {
        Part part = Named.find(mc, "kind:" + id);
        Object typed;
        try (Stream<?> ingredients = (Stream<?>) call(call(runtime, "getScreenHelper"), "getClickableIngredientUnderMouse",
                mc.screen, part.x() + part.w() / 2.0, part.y() + part.h() / 2.0)) {
            Object ingredient = ingredients.findFirst().orElseThrow(() -> new IllegalStateException("JEI cannot see terminal ingredient " + id));
            typed = call(ingredient, "getTypedIngredient");
        }
        Object factory = call(call(runtime, "getJeiHelpers"), "getFocusFactory");
        Object output = Class.forName("mezz.jei.api.recipe.RecipeIngredientRole").getField("OUTPUT").get(null);
        call(call(runtime, "getRecipesGui"), "show", List.of(call(factory, "createFocus", output, typed)));
        require(mc.screen.getClass().getSimpleName().equals("RecipesGui"), "JEI did not open recipes from the terminal grid");
    }

    private static Object layout(Minecraft mc) throws ReflectiveOperationException {
        require(mc.screen != null && mc.screen.getClass().getSimpleName().equals("RecipesGui"), "JEI recipes screen is not open");
        List<?> layouts = (List<?>) field(call(mc.screen, "getRecipeLayouts"), "recipeLayoutsWithButtons");
        require(!layouts.isEmpty(), "JEI has no visible recipe layout");
        return layouts.getFirst();
    }

    private static Object transferButton(Minecraft mc) throws ReflectiveOperationException {
        return field(layout(mc), "transferButton");
    }

    private static void hoverTransfer(Minecraft mc) throws ReflectiveOperationException {
        Object layout = layout(mc);
        Object controller = field(layout, "transferButton");
        for (Object button : (List<?>) field(layout, "buttons")) {
            if (field(button, "controller") == controller) {
                require(Boolean.TRUE.equals(call(button, "isVisible")), "JEI transfer button is hidden");
                double x = ((Number) call(button, "getX")).doubleValue() + ((Number) call(button, "getWidth")).doubleValue() / 2;
                double y = ((Number) call(button, "getY")).doubleValue() + ((Number) call(button, "getHeight")).doubleValue() / 2;
                Rehearsal.moveMouse(mc, x, y);
                return;
            }
        }
        throw new IllegalStateException("JEI transfer button has no visible widget");
    }

    private static void missing(Minecraft mc, String args) throws ReflectiveOperationException {
        String[] words = args.split(" ");
        int count = Integer.parseInt(words[0]);
        Object error = call(transferButton(mc), "updateRecipeTransferError");
        if (count == 0) {
            require(error == null, "JEI unexpectedly marks missing ingredients: " + error);
        } else {
            require(error != null && call(error, "getType").toString().equals("COSMETIC"), "JEI missing marker is absent or blocks transfer");
            require(((List<?>) call(error, "missing")).size() == count, "JEI marks the wrong number of missing slots");
            if (words.length > 1) {
                require(call(error, "canAsk").equals(Boolean.valueOf(words[1])), "JEI marks the wrong craft-request availability");
            }
        }
    }

    /** Supplies the modifier branch explicitly; native keyboard state is never changed by a rehearsal. */
    private static void ask(Minecraft mc) throws ReflectiveOperationException {
        Object drawable = call(layout(mc), "getRecipeLayout");
        Screen recipes = mc.screen;
        Object parent = ((Optional<?>) call(recipes, "getParentScreen")).orElseThrow();
        require(parent instanceof StorageTerminalScreen, "JEI parent is not the terminal");
        StorageTerminalScreen terminal = (StorageTerminalScreen) parent;
        Class<?> handlerClass = Class.forName("io.github.metrovoc.residual.client.jei.ResidualJei$TerminalTransfer");
        var constructor = handlerClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object handler = constructor.newInstance();
        call(handler, "transfer", terminal.getMenu(), call(drawable, "getRecipeSlotsView"), mc.player, false, true, terminal, true);
        call(recipes, "onClose");
    }

    private static void beginDrag(Minecraft mc, Object runtime, Object overlay, String id, boolean fluid) throws ReflectiveOperationException {
        reset();
        require(mc.screen instanceof AbstractContainerScreen<?>, "a container must be open for a JEI drag");
        double sourceX = -1, sourceY = -1;
        outer: for (int y = 4; y < mc.screen.height; y += 8) {
            for (int x = 4; x < mc.screen.width; x += 8) {
                try (Stream<?> ingredients = (Stream<?>) call(overlay, "getDraggableIngredientUnderMouse", (double) x, (double) y)) {
                    Object ingredient = ingredients.findFirst().orElse(null);
                    if (ingredient == null) {
                        continue;
                    }
                    Object value = call(call(ingredient, "getTypedIngredient"), "getIngredient");
                    boolean matches = fluid
                            ? value instanceof FluidStack fluidStack && BuiltInRegistries.FLUID.getKey(fluidStack.getFluid()).toString().equals(id)
                            : value instanceof ItemStack stack && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id);
                    if (matches) {
                        sourceX = x;
                        sourceY = y;
                        break outer;
                    }
                }
            }
        }
        require(sourceX >= 0, "JEI overlay has no visible draggable " + id);
        Class<?> managerClass = Class.forName("mezz.jei.gui.ghost.GhostIngredientDragManager");
        dragManager = managerClass.getConstructors()[0].newInstance(overlay, call(runtime, "getScreenHelper"),
                call(runtime, "getIngredientManager"), call(Class.forName("mezz.jei.common.Internal"), "getClientToggleState"));
        dragScreen = mc.screen;
        try {
            drag = ((Optional<?>) call(dragManager, "beginDrag", dragScreen, mouse(sourceX, sourceY, "PRESS"))).orElseThrow(
                    () -> new IllegalStateException("JEI did not begin the ghost drag"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            reset();
            throw e;
        }
    }

    private static void drop(Minecraft mc, int index) throws ReflectiveOperationException {
        require(drag != null && mc.screen == dragScreen, "JEI drag has no matching source screen");
        try {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
            Slot slot = screen.getMenu().getSlot(index);
            call(drag, "complete", mouse(screen.getLeftPos() + slot.x + 8.0, screen.getTopPos() + slot.y + 8.0, "RELEASE"));
        } finally {
            reset();
        }
    }

    static void reset() throws ReflectiveOperationException {
        try {
            if (dragManager != null) {
                call(dragManager, "stopDrag");
            }
        } finally {
            drag = null;
            dragManager = null;
            dragScreen = null;
        }
    }

    private static Object mouse(double x, double y, String phase) throws ReflectiveOperationException {
        Object inputPhase = Class.forName("mezz.jei.common.input.InputPhase").getField(phase).get(null);
        return ((Optional<?>) call(Class.forName("mezz.jei.common.input.UserInput"), "fromVanilla",
                new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)), false, inputPhase)).orElseThrow();
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "." + name);
    }

    private static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        Class<?> type = target instanceof Class<?> c ? c : target.getClass();
        Stream<Method> methods = Stream.concat(Arrays.stream(type.getMethods()), Arrays.stream(type.getDeclaredMethods()));
        Method method = methods.filter(m -> m.getName().equals(name) && matches(m.getParameterTypes(), args)).findFirst()
                .orElseThrow(() -> new NoSuchMethodException(type.getName() + "." + name));
        method.setAccessible(true);
        return method.invoke(target instanceof Class<?> ? null : target, args);
    }

    private static boolean matches(Class<?>[] types, Object[] args) {
        if (types.length != args.length) {
            return false;
        }
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i] == boolean.class ? Boolean.class : types[i] == double.class ? Double.class : types[i];
            if (!type.isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
