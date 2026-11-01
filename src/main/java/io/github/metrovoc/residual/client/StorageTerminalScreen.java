package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.net.CraftAsk;
import io.github.metrovoc.residual.net.CraftCancel;
import io.github.metrovoc.residual.net.CraftJobsSync;
import io.github.metrovoc.residual.net.CraftPlanSync;
import io.github.metrovoc.residual.net.CraftWatch;
import io.github.metrovoc.residual.net.StorageClick;
import io.github.metrovoc.residual.net.StorageFill;
import io.github.metrovoc.residual.net.StorageMean;
import io.github.metrovoc.residual.net.StoragePin;
import io.github.metrovoc.residual.net.StorageSync;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.storage.CraftJobs;
import io.github.metrovoc.residual.storage.Drive;
import io.github.metrovoc.residual.storage.StorageSearch;
import io.github.metrovoc.residual.storage.TerminalMenu;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Everything a storage network keeps, in a grid that searches and sorts. Clicking a kind takes a stack, right-clicking
 * half, shift-clicking moves a stack to the inventory; clicking with a stack puts it in, right-clicking one of it.
 * What the network's Assemblers and Processors can make shows too: asking for an amount of it shows the plan, and starting
 * it runs a job, which the jobs view follows, down to the plan drawn as a tree. Alt-clicking a kind pins it to the top of
 * the grid; what running jobs make comes after the pins by itself, with how far it has come. A search that starts with ? asks
 * the model beside the window what it means; a crafting grid in a panel below it draws on the network.
 */
public class StorageTerminalScreen extends AbstractContainerScreen<TerminalMenu> implements Parts.Drawn {
    private static final int GRID_X = 8, GRID_Y = 20, COLS = 9, ROWS = 6, CELL = 18;
    private static final int BAR_X = GRID_X + COLS * CELL + 4, BAR_W = 12, BAR_H = ROWS * CELL;
    private static final int WINDOW_W = BAR_X + BAR_W + 8;
    private static final int USAGE_Y = GRID_Y + ROWS * CELL + 3;
    /** The top bar: the search box, the Jobs button and the sort button. */
    private static final int SEARCH_W = 118, JOBS_X = GRID_X + SEARCH_W + 2, JOBS_W = 34;
    /** The ask and jobs views take the grid's place, its scroll bar's width included. */
    private static final int PANEL_W = BAR_X + BAR_W - GRID_X;
    private static final int PLAN_Y = 62, PLAN_ROW = 11, PLAN_ROWS = 5;
    private static final int JOB_Y = 36, JOB_ROW = 18, JOB_ROWS = 5;
    private static final int TREE_Y = 38, TREE_ROW = 11, TREE_ROWS = 8, TREE_INDENT = 8, TREE_DEEPEST = 7;
    /** Ticks an amount must stay unchanged before it is planned, or a query before the model is asked. */
    private static final int SETTLE = 6;
    /** What starts a search by meaning. */
    private static final String MEANING = "?";

    private enum Sort { NAME, COUNT, MOD }

    private enum View { GRID, ASK, JOBS, JOB }

    /** Kept while the game runs, so the terminal opens the way it was left. */
    private static String lastSearch = "";
    private static Sort sort = Sort.NAME;
    private static boolean descending;

    private EditBox search, amount;
    private Button sortButton, jobsButton, backButton, startButton;
    private final List<Button> steps = new ArrayList<>();
    private List<Kept> shown = List.of();
    private List<StorageSync.Entry> seen;
    private List<StorageSync.Fluid> seenFluids;
    private Map<ItemResource, Float> seenMeanings;
    /** The query the model was last asked about, and how long the box has stood still. */
    private String meantLast = "";
    private String typed = "";
    private int typedFor;
    /** What the recipe viewer's search box was last seen to hold. */
    @Nullable
    private String viewerSaid;
    private Set<ItemResource> seenCraftable;
    private List<ItemResource> seenPins;
    private List<CraftJobsSync.Job> seenJobs;
    private String seenQuery;
    private Sort seenSort;
    private boolean seenDescending;
    private int scroll;
    private boolean dragging;
    private View view = View.GRID;
    /** What is being asked for, and since when its amount has stood still. */
    private ItemResource asking;
    private long asked = -1;
    private int settled;
    private boolean starting;
    /** The job whose plan the tree view draws. */
    private UUID following;

    public StorageTerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TerminalMenu.SIDE_X + TerminalMenu.CRAFT_W, TerminalMenu.INVENTORY_Y + 82);
        inventoryLabelY = TerminalMenu.INVENTORY_Y - 10;
        inventoryLabelX = TerminalMenu.INVENTORY_X;
    }

    @Override
    protected void init() {
        super.init();
        // the search box takes the title's place: the screen is what it finds
        search = addRenderableWidget(new EditBox(font, leftPos + GRID_X, topPos + 5, SEARCH_W, 12, Component.translatable("residual.storage.search")));
        search.setMaxLength(80);
        search.setValue(lastSearch);
        search.setHint(Component.translatable("residual.storage.search_hint"));
        search.setTooltip(Tooltip.create(Component.translatable("residual.storage.search_help")));
        search.setResponder(s -> {
            lastSearch = s;
            scroll = 0;
            tellViewer(s);
        });
        hearViewerFirst();
        sortButton = addRenderableWidget(Button.builder(sortLabel(), b -> {
            Sort[] all = Sort.values();
            if (Minecraft.getInstance().hasShiftDown()) {
                descending = !descending;
            } else {
                sort = all[(sort.ordinal() + 1) % all.length];
            }
            b.setMessage(sortLabel());
        }).bounds(leftPos + BAR_X - 10, topPos + 5, BAR_W + 10, 12).tooltip(Tooltip.create(Component.translatable("residual.storage.sort_help"))).build());
        jobsButton = addRenderableWidget(Button.builder(Component.translatable("residual.craft.jobs"), b -> show(View.JOBS))
                .bounds(leftPos + JOBS_X, topPos + 5, JOBS_W, 12)
                .tooltip(Tooltip.create(Component.translatable("residual.craft.jobs_help"))).build());
        backButton = addRenderableWidget(Button.builder(Component.literal("×"), b -> back())
                .bounds(leftPos + BAR_X + BAR_W - 12, topPos + GRID_Y + 2, 12, 12)
                .tooltip(Tooltip.create(Component.translatable("residual.craft.back"))).build());
        amount = addRenderableWidget(new EditBox(font, leftPos + GRID_X + 1, topPos + GRID_Y + 19, 38, 12, Component.translatable("residual.craft.amount")));
        amount.setMaxLength(10);
        amount.setFilter(s -> s.chars().allMatch(Character::isDigit));
        amount.setResponder(s -> settled = 0);
        steps.clear();
        // a craft, a stack of sixteen, a stack, a thousand
        long[] by = {1, 16, 64, 1000};
        for (int i = 0; i < by.length; i++) {
            long n = by[i];
            steps.add(addRenderableWidget(Button.builder(Component.literal("+" + (n == 1000 ? "1K" : Long.toString(n))), b -> step(n))
                    .bounds(leftPos + GRID_X + 41 + i * 25, topPos + GRID_Y + 19, 24, 12)
                    .tooltip(Tooltip.create(Component.translatable("residual.craft.step_help"))).build()));
        }
        startButton = addRenderableWidget(Button.builder(Component.translatable("residual.craft.start"), b -> start())
                .bounds(leftPos + GRID_X + 142, topPos + GRID_Y + 19, PANEL_W - 142, 12)
                .tooltip(Tooltip.create(Component.translatable("residual.craft.start_help"))).build());
        show(view);
    }

    /** Opening with an empty box takes up what the recipe viewer's holds; else the viewer takes up this one. */
    private void hearViewerFirst() {
        String heard = RecipeViewer.search();
        if (heard != null) {
            if (lastSearch.isEmpty() && !heard.isEmpty()) {
                search.setValue(heard);
            } else {
                tellViewer(lastSearch);
            }
            viewerSaid = RecipeViewer.search();
        }
    }

    private void tellViewer(String text) {
        if (!text.startsWith(MEANING)) {
            RecipeViewer.search(text);
            viewerSaid = RecipeViewer.search();
        }
    }

    /** What the player typed in the recipe viewer's box comes here too. */
    private void hearViewer() {
        String heard = RecipeViewer.search();
        if (heard != null && !heard.equals(viewerSaid)) {
            viewerSaid = heard;
            search.setValue(heard);
        }
    }

    /** A search by meaning goes to the model once its words stand still, and a search that is not one stops it. */
    private void askForMeaning() {
        String query = search.getValue();
        if (!query.equals(typed)) {
            typed = query;
            typedFor = 0;
        }
        String wanted = query.startsWith(MEANING) ? query.substring(MEANING.length()).strip() : "";
        if (!wanted.equals(meantLast) && ++typedFor >= SETTLE) {
            meantLast = wanted;
            ClientPacketDistributor.sendToServer(new StorageMean(menu.containerId, wanted));
        }
    }

    /** What the player sees for a kind under the mouse, for the recipe viewer: an item, a fluid, or null. */
    @Nullable
    public Object under(double mouseX, double mouseY) {
        Kept e = view == View.GRID ? at(mouseX, mouseY) : null;
        return e == null ? null : e.isFluid() ? e.fluid().toStack((int) Drive.BUCKET) : e.item().toStack();
    }

    /** How many kinds the grid shows now, the search taken into account. */
    public int kindsShown() {
        return shown().size();
    }

    /** The grid's cell under the mouse, in screen coordinates. */
    public Rect2i cellUnder(double mouseX, double mouseY) {
        int col = ((int) mouseX - leftPos - GRID_X) / CELL, row = ((int) mouseY - topPos - GRID_Y) / CELL;
        return new Rect2i(leftPos + GRID_X + col * CELL, topPos + GRID_Y + row * CELL, CELL, CELL);
    }

    /** Asks for {@code item}, as clicking it would: for the recipe viewer, what a recipe lacks and the network can make. */
    public void askFor(ItemResource item) {
        ask(item);
    }

    /** A step back: from a job's plan to the jobs, from anything else to the grid. */
    private void back() {
        show(view == View.JOB ? View.JOBS : View.GRID);
    }

    /** Draws a running job's plan, and has the server say how far it has come. */
    private void follow(CraftJobsSync.Job job) {
        following = job.id();
        menu.treeJob = null;
        menu.tree = List.of();
        menu.treeDone = List.of();
        ClientPacketDistributor.sendToServer(new CraftWatch(menu.containerId, Optional.of(job.id())));
        show(View.JOB);
    }

    private CraftJobsSync.Job followed() {
        for (CraftJobsSync.Job job : menu.jobs) {
            if (job.id().equals(following)) {
                return job;
            }
        }
        return null;
    }

    private void show(View v) {
        if (view == View.JOB && v != View.JOB) {
            ClientPacketDistributor.sendToServer(new CraftWatch(menu.containerId, Optional.empty()));
        }
        view = v;
        scroll = 0;
        boolean grid = v == View.GRID, ask = v == View.ASK;
        search.visible = grid;
        sortButton.visible = grid;
        jobsButton.visible = grid;
        backButton.visible = !grid;
        amount.visible = ask;
        steps.forEach(b -> b.visible = ask);
        startButton.visible = ask;
        if (!ask) {
            amount.setFocused(false);
        }
    }

    /** Asks for {@code item}: one craft's worth to begin with, planned as soon as the amount stands still. */
    private void ask(ItemResource item) {
        asking = item;
        asked = -1;
        menu.plan = null;
        starting = false;
        amount.setValue("1");
        show(View.ASK);
        setFocused(amount);
        amount.setFocused(true);
    }

    private long amountValue() {
        try {
            return Math.clamp(Long.parseLong(amount.getValue()), 1, CraftJobs.MOST);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** Adds to the amount; with shift, takes away. */
    private void step(long n) {
        long now = amount.getValue().isEmpty() ? 0 : amountValue();
        amount.setValue(Long.toString(Math.clamp(now + (Minecraft.getInstance().hasShiftDown() ? -n : n), 1, CraftJobs.MOST)));
    }

    private void start() {
        if (asking != null && planFits() && menu.plan.rows().getFirst().crafts() > 0) {
            starting = true;
            ClientPacketDistributor.sendToServer(new CraftAsk(menu.containerId, asking, amountValue(), true));
        }
    }

    /** Whether the last plan is for what is asked for now. */
    private boolean planFits() {
        return menu.plan != null && asking != null && menu.plan.item().equals(asking) && menu.plan.amount() == amountValue();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        hearViewer();
        askForMeaning();
        if (view == View.JOB && followed() == null) {
            show(View.JOBS);
        }
        if (view != View.ASK || asking == null) {
            return;
        }
        long n = amountValue();
        if (n != asked && ++settled >= SETTLE) {
            asked = n;
            ClientPacketDistributor.sendToServer(new CraftAsk(menu.containerId, asking, n, false));
        }
        if (starting && menu.plan != null && menu.plan.started()) {
            starting = false;
            show(View.JOBS);
        }
        startButton.active = planFits() && !starting && menu.plan.rows().getFirst().crafts() > 0;
    }

    private Component sortLabel() {
        return Component.translatable("residual.storage.sort." + sort.name().toLowerCase(Locale.ROOT)).append(descending ? "↓" : "↑");
    }

    /**
     * The kinds that match the search, in the chosen order, with what the network can make but holds none of; worked
     * out again only when something changed.
     */
    private List<Kept> shown() {
        String query = search == null ? "" : search.getValue();
        if (menu.items != seen || menu.fluids != seenFluids || menu.meanings != seenMeanings || menu.craftable != seenCraftable || menu.pins != seenPins || menu.jobs != seenJobs
                || !query.equals(seenQuery) || sort != seenSort || descending != seenDescending) {
            seen = menu.items;
            seenFluids = menu.fluids;
            seenMeanings = menu.meanings;
            seenCraftable = menu.craftable;
            seenPins = menu.pins;
            seenJobs = menu.jobs;
            seenQuery = query;
            seenSort = sort;
            seenDescending = descending;
            boolean byMeaning = query.startsWith(MEANING);
            Predicate<StorageSearch.Facts> wanted = byMeaning ? StorageSearch.anyWord(query.substring(MEANING.length())) : StorageSearch.parse(query);
            List<Kept> all = new ArrayList<>();
            Set<ItemResource> held = new HashSet<>();
            for (StorageSync.Entry e : menu.items) {
                all.add(Kept.of(e.item(), e.count()));
                held.add(e.item());
            }
            for (StorageSync.Fluid f : menu.fluids) {
                all.add(Kept.of(f.fluid(), f.amount()));
            }
            for (ItemResource c : menu.craftable) {
                if (held.add(c)) {
                    all.add(Kept.of(c, 0));
                }
            }
            // what is pinned stays in view with none kept, and what jobs make comes after it by itself
            List<ItemResource> top = new ArrayList<>(menu.pins);
            for (CraftJobsSync.Job job : menu.jobs) {
                if (!top.contains(job.target())) {
                    top.add(job.target());
                }
            }
            for (ItemResource t : top) {
                if (held.add(t)) {
                    all.add(Kept.of(t, 0));
                }
            }
            List<Kept> out = new ArrayList<>();
            for (Kept e : all) {
                if (wanted.test(new Facts(e)) || byMeaning && meant(e) > 0) {
                    out.add(e);
                }
            }
            Comparator<Kept> byName = Comparator.comparing(Kept::sortName);
            Comparator<Kept> order = switch (sort) {
                case NAME -> byName;
                case COUNT -> Comparator.<Kept>comparingLong(Kept::units).reversed().thenComparing(byName);
                case MOD -> Comparator.comparing(Kept::mod).thenComparing(byName);
            };
            out.sort(descending ? order.reversed() : order);
            if (byMeaning) {
                out.sort(Comparator.comparing(e -> -meant(e)));
            }
            List<Kept> pinned = new ArrayList<>(), rest = new ArrayList<>();
            for (ItemResource t : top) {
                out.stream().filter(e -> e.item().equals(t)).findFirst().ifPresent(pinned::add);
            }
            for (Kept e : out) {
                if (!top.contains(e.item())) {
                    rest.add(e);
                }
            }
            pinned.addAll(rest);
            shown = pinned;
        }
        return shown;
    }

    /** How far a kind fits the search by meaning now in the box, going by the model; nothing while the model answers another. */
    private float meant(Kept e) {
        String query = search == null ? "" : search.getValue();
        return query.startsWith(MEANING) && query.substring(MEANING.length()).strip().equals(menu.meant)
                ? menu.meanings.getOrDefault(e.item(), 0f) : 0;
    }

    /** A kind on the grid: an item and how many there are, or a fluid and how many millibuckets. */
    private record Kept(ItemResource item, FluidResource fluid, long count, String sortName) {
        static Kept of(ItemResource item, long count) {
            return new Kept(item, FluidResource.EMPTY, count, item.getHoverName().getString().toLowerCase(Locale.ROOT));
        }

        static Kept of(FluidResource fluid, long amount) {
            return new Kept(ItemResource.EMPTY, fluid, amount, fluid.toStack((int) Drive.BUCKET).getHoverName().getString().toLowerCase(Locale.ROOT));
        }

        boolean isFluid() {
            return !fluid.isEmpty();
        }

        Component name() {
            return isFluid() ? fluid.toStack((int) Drive.BUCKET).getHoverName() : item.getHoverName();
        }

        /** How many there are, a bucket of fluid counted as one. */
        long units() {
            return isFluid() ? count / Drive.BUCKET : count;
        }

        String mod() {
            return id().getNamespace();
        }

        Identifier id() {
            return isFluid() ? BuiltInRegistries.FLUID.getKey(fluid.getFluid()) : BuiltInRegistries.ITEM.getKey(item.getItem());
        }

        /** How much there is, short enough for a cell's corner. */
        String label() {
            return !isFluid() ? abbreviate(count) : count < Drive.BUCKET ? count + "m" : abbreviate(count / Drive.BUCKET) + "B";
        }
    }

    /** What a search can ask about a kind, worked out only if it asks. */
    private record Facts(Kept e) implements StorageSearch.Facts {
        @Override
        public String name() {
            return e.name().getString();
        }

        @Override
        public String mod() {
            return e.mod();
        }

        @Override
        public String modName() {
            return ModList.get().getModContainerById(mod()).map(c -> c.getModInfo().getDisplayName()).orElse("");
        }

        @Override
        public Collection<String> tags() {
            return (e.isFluid() ? e.fluid().typeHolder().tags() : e.item().typeHolder().tags()).map(t -> t.location().toString()).toList();
        }

        @Override
        public String tooltip() {
            if (e.isFluid()) {
                return e.name().getString();
            }
            Minecraft mc = Minecraft.getInstance();
            StringBuilder out = new StringBuilder();
            for (Component line : e.item().toStack().getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)) {
                out.append(line.getString()).append(' ');
            }
            return out.toString();
        }

        @Override
        public String id() {
            return e.id().toString();
        }

        @Override
        public long count() {
            return e.units();
        }
    }

    private int rows() {
        return switch (view) {
            case GRID -> (shown().size() + COLS - 1) / COLS;
            case ASK -> menu.plan == null ? 0 : menu.plan.rows().size();
            case JOBS -> menu.jobs.size();
            case JOB -> menu.tree.size();
        };
    }

    private int visibleRows() {
        return switch (view) {
            case GRID -> ROWS;
            case ASK -> PLAN_ROWS;
            case JOBS -> JOB_ROWS;
            case JOB -> TREE_ROWS;
        };
    }

    private int maxScroll() {
        return Math.max(0, rows() - visibleRows());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        // the search slot's panel and the crafting grid's, tucked under the window's edge
        Ui.window(g, leftPos + TerminalMenu.SIDE_X, topPos + TerminalMenu.SIDE_Y, TerminalMenu.SIDE_W, TerminalMenu.SIDE_H);
        Ui.window(g, leftPos + TerminalMenu.SIDE_X, topPos + TerminalMenu.CRAFT_Y, TerminalMenu.CRAFT_W, TerminalMenu.CRAFT_H);
        Ui.window(g, leftPos, topPos, WINDOW_W, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        if (!menu.getSlot(TerminalMenu.MODEL).hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.CHECKPOINT.get()), leftPos + TerminalMenu.MODEL_X, topPos + TerminalMenu.MODEL_Y);
        }
        scroll = Math.clamp(scroll, 0, maxScroll());
        if (view == View.GRID) {
            Ui.screen(g, leftPos + GRID_X - 1, topPos + GRID_Y - 1, COLS * CELL + 2, ROWS * CELL + 2);
            Ui.screen(g, leftPos + BAR_X, topPos + GRID_Y - 1, BAR_W, BAR_H + 2);
            int rows = Math.max(1, rows());
            int thumb = Math.max(10, BAR_H * ROWS / Math.max(ROWS, rows));
            int ty = topPos + GRID_Y + (maxScroll() == 0 ? 0 : (BAR_H - thumb) * scroll / maxScroll());
            g.fill(leftPos + BAR_X + 2, ty, leftPos + BAR_X + BAR_W - 2, ty + thumb, maxScroll() == 0 ? Ui.GRID : Ui.TEAL);
        } else {
            Ui.screen(g, leftPos + GRID_X - 1, topPos + GRID_Y - 1, PANEL_W + 2, ROWS * CELL + 2);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
        switch (view) {
            case GRID -> grid(g, mouseX, mouseY);
            case ASK -> plan(g, mouseX, mouseY);
            case JOBS -> jobs(g, mouseX, mouseY);
            case JOB -> tree(g);
        }
    }

    private void grid(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!menu.jobs.isEmpty()) {
            // how many jobs run, on the Jobs button's corner
            count(g, Integer.toString(menu.jobs.size()), JOBS_X + JOBS_W - CELL - 1, 5 - CELL + 7, Ui.TEAL);
        }
        List<Kept> list = shown();
        for (int i = 0; i < COLS * ROWS; i++) {
            int index = scroll * COLS + i;
            int x = GRID_X + i % COLS * CELL, y = GRID_Y + i / COLS * CELL;
            if (index >= list.size()) {
                break;
            }
            Kept e = list.get(index);
            if (e.isFluid()) {
                Ui.fluid(g, e.fluid(), x + 1, y + 1, CELL - 2, CELL - 2);
            } else {
                g.item(e.item().toStack(), x + 1, y + 1);
            }
            if (e.count() > 0) {
                count(g, e.label(), x, y, 0xFFFFFFFF);
            } else {
                count(g, Component.translatable("residual.craft.mark").getString(), x, y, Ui.TEAL);
            }
            if (e.count() > 0 && menu.craftable.contains(e.item())) {
                // a corner mark: the network can make more
                g.fill(x + 1, y + 1, x + 3, y + 3, Ui.TEAL);
            }
            if (menu.pins.contains(e.item())) {
                // the other corner: pinned
                g.fill(x + CELL - 3, y + 1, x + CELL - 1, y + 3, Ui.AMBER);
            }
            for (CraftJobsSync.Job job : menu.jobs) {
                if (job.target().equals(e.item())) {
                    // along the foot of the cell: how far the job making it has come
                    Ui.bar(g, x + 1, y + CELL - 2, CELL - 2, 1, job.total() == 0 ? 1 : (float) job.done() / job.total(), Ui.TEAL);
                    break;
                }
            }
            if (Ui.inside(mouseX - leftPos, mouseY - topPos, x, y, CELL, CELL)) {
                g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x40FFFFFF);
            }
        }
        if (list.isEmpty()) {
            Component empty = Component.translatable(menu.items.isEmpty() && menu.fluids.isEmpty() ? "residual.storage.empty" : "residual.storage.no_match");
            int y = GRID_Y + 6;
            for (var line : font.split(empty, COLS * CELL - 12)) {
                g.text(font, line, GRID_X + 6, y, Ui.DIM, false);
                y += 10;
            }
        }
        Component usage = menu.capacity == 0 ? Component.translatable("residual.storage.no_drives")
                : Component.translatable("residual.storage.usage", abbreviate(menu.stored), abbreviate(menu.capacity), Ui.count(menu.items.size() + menu.fluids.size()));
        g.text(font, search.getValue().startsWith(MEANING) ? meaningStatus() : usage, GRID_X, USAGE_Y + 1, Ui.LABEL_DIM, false);
    }

    /** How far a search by meaning has come, or why it goes by the words alone. */
    private Component meaningStatus() {
        if (!menu.meaningProblem.isEmpty()) {
            return Component.translatable("residual.search.words_only");
        }
        return menu.meaningAsked < menu.meaningTotal
                ? Component.translatable("residual.search.asking", Ui.count(menu.meaningAsked), Ui.count(menu.meaningTotal))
                : Component.translatable("residual.search.found", Ui.count(menu.meanings.size()), Ui.count(menu.meaningTotal));
    }

    /** The plan: what is asked for, then its tree, each row indented under the step that needs it. */
    private void plan(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (asking == null) {
            return;
        }
        g.item(asking.toStack(), GRID_X + 1, GRID_Y + 1);
        g.text(font, Ui.fit(font, asking.getHoverName().getString(), PANEL_W - 36), GRID_X + 20, GRID_Y + 5, Ui.TEXT, false);
        g.text(font, Component.translatable("residual.craft.legend"), GRID_X + 2, GRID_Y + 33, Ui.DIM, false);
        CraftPlanSync plan = planFits() ? menu.plan : null;
        if (plan == null) {
            g.text(font, Component.translatable("residual.craft.planning"), GRID_X + 4, PLAN_Y + 2, Ui.DIM, false);
            return;
        }
        List<CraftPlanSync.Row> rows = plan.rows();
        for (int i = 0; i < PLAN_ROWS && scroll + i < rows.size(); i++) {
            CraftPlanSync.Row row = rows.get(scroll + i);
            int y = PLAN_Y + i * PLAN_ROW;
            int x = GRID_X + 2 + Math.min(row.depth(), 6) * 6;
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(0.625f, 0.625f);
            g.item(row.kind().toStack(), 0, 0);
            g.pose().popMatrix();
            Component status = rowStatus(row);
            int right = GRID_X + PANEL_W - 2;
            int nameWidth = right - font.width(status) - 4 - (x + 12);
            g.text(font, Ui.fit(font, row.kind().getHoverName().getString(), nameWidth), x + 12, y + 1, Ui.TEXT, false);
            g.text(font, status, right - font.width(status), y + 1, Ui.TEXT, false);
        }
        Component summary = plan.lackingKinds() == 0
                ? Component.translatable("residual.craft.summary", Ui.count(plan.crafts()))
                : Component.translatable("residual.craft.summary_missing", Ui.count(plan.crafts()), plan.lackingKinds());
        g.text(font, plan.cut() ? Component.translatable("residual.craft.cut") : summary, GRID_X + 2, PLAN_Y + PLAN_ROWS * PLAN_ROW + 1,
                plan.cut() ? Ui.AMBER : plan.lackingKinds() == 0 ? Ui.TEAL : Ui.RED, false);
    }

    /** A row's numbers: how many come from storage, ×crafts, −missing. */
    private static Component rowStatus(CraftPlanSync.Row row) {
        Component out = Component.empty();
        boolean first = true;
        if (row.stored() > 0) {
            out = out.copy().append(Component.literal(abbreviate(row.stored())).withColor(Ui.DIM));
            first = false;
        }
        if (row.crafts() > 0) {
            out = out.copy().append(Component.literal((first ? "" : " ") + "×" + abbreviate(row.crafts())).withColor(Ui.TEAL));
            first = false;
        }
        if (row.missing() > 0) {
            out = out.copy().append(Component.literal((first ? "" : " ") + "−" + abbreviate(row.missing())).withColor(Ui.RED));
        }
        return out;
    }

    /** The jobs running on the network: what each makes, how far it is, and what it waits for. */
    private void jobs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, Component.translatable("residual.craft.jobs_title"), GRID_X + 2, GRID_Y + 4, Ui.TEXT, false);
        List<CraftJobsSync.Job> list = menu.jobs;
        if (list.isEmpty()) {
            int y = JOB_Y + 2;
            for (var line : font.split(Component.translatable("residual.craft.no_jobs"), PANEL_W - 8)) {
                g.text(font, line, GRID_X + 4, y, Ui.DIM, false);
                y += 10;
            }
            return;
        }
        for (int i = 0; i < JOB_ROWS && scroll + i < list.size(); i++) {
            CraftJobsSync.Job job = list.get(scroll + i);
            int y = JOB_Y + i * JOB_ROW;
            g.item(job.target().toStack(), GRID_X + 1, y);
            int right = GRID_X + PANEL_W - 14;
            String name = Ui.count(job.amount()) + " " + job.target().getHoverName().getString();
            g.text(font, Ui.fit(font, name, right - GRID_X - 22), GRID_X + 20, y, Ui.TEXT, false);
            Component state;
            int color;
            if (job.redrawn()) {
                state = Component.translatable("residual.craft.redrawn");
                color = Ui.AMBER;
            } else if (!job.lacking().isEmpty()) {
                state = Component.translatable("residual.craft.waiting", Ui.count(job.lackingCount()), job.lacking().getHoverName());
                color = Ui.RED;
            } else {
                state = Component.translatable("residual.craft.progress", Ui.count(job.done()), Ui.count(job.total()));
                color = Ui.DIM;
            }
            Ui.bar(g, GRID_X + 20, y + 10, right - GRID_X - 22, 2, job.total() == 0 ? 1 : (float) job.done() / job.total(), Ui.TEAL);
            g.pose().pushMatrix();
            g.pose().translate(GRID_X + 20, y + 13);
            g.pose().scale(0.5f, 0.5f);
            g.text(font, Ui.fit(font, state.getString(), (right - GRID_X - 22) * 2), 0, 0, color, false);
            g.pose().popMatrix();
            boolean over = Ui.inside(mouseX - leftPos, mouseY - topPos, right + 2, y + 2, 10, 10);
            g.fill(right + 2, y + 2, right + 12, y + 12, over ? Ui.RED : Ui.GRID);
            g.text(font, "×", right + 4, y + 3, Ui.TEXT, false);
        }
    }

    /** A running job's plan as a tree: each row under the step that needs it, with lines between them, and how far its crafts have come. */
    private void tree(GuiGraphicsExtractor g) {
        CraftJobsSync.Job job = followed();
        if (job == null) {
            return;
        }
        g.item(job.target().toStack(), GRID_X + 1, GRID_Y + 1);
        String name = Ui.count(job.amount()) + " " + job.target().getHoverName().getString();
        g.text(font, Ui.fit(font, name, PANEL_W - 36), GRID_X + 20, GRID_Y + 1, Ui.TEXT, false);
        Ui.bar(g, GRID_X + 20, GRID_Y + 11, PANEL_W - 36, 2, job.total() == 0 ? 1 : (float) job.done() / job.total(), Ui.TEAL);
        List<CraftPlanSync.Row> rows = menu.tree;
        if (rows.isEmpty()) {
            g.text(font, Component.translatable("residual.craft.no_plan"), GRID_X + 4, TREE_Y + 2, Ui.DIM, false);
            return;
        }
        for (int i = 0; i < TREE_ROWS && scroll + i < rows.size(); i++) {
            int index = scroll + i;
            CraftPlanSync.Row row = rows.get(index);
            int y = TREE_Y + i * TREE_ROW, depth = Math.min(row.depth(), TREE_DEEPEST);
            int x = GRID_X + 2 + depth * TREE_INDENT;
            for (int k = 1; k <= depth; k++) {
                int line = GRID_X + 2 + k * TREE_INDENT - 5;
                boolean goesOn = continues(rows, index, k);
                if (k == depth) {
                    g.fill(line, y, line + 1, goesOn ? y + TREE_ROW : y + 5, Ui.GRID);
                    g.fill(line, y + 4, x - 1, y + 5, Ui.GRID);
                } else if (goesOn) {
                    g.fill(line, y, line + 1, y + TREE_ROW, Ui.GRID);
                }
            }
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(0.625f, 0.625f);
            g.item(row.kind().toStack(), 0, 0);
            g.pose().popMatrix();
            long done = index < menu.treeDone.size() ? menu.treeDone.get(index) : 0;
            Component status = treeStatus(row, done, job);
            int right = GRID_X + PANEL_W - 2, nameWidth = right - font.width(status) - 4 - (x + 12);
            g.text(font, Ui.fit(font, row.kind().getHoverName().getString(), nameWidth), x + 12, y, Ui.TEXT, false);
            g.text(font, status, right - font.width(status), y, Ui.TEXT, false);
            if (row.crafts() > 0) {
                Ui.bar(g, x + 12, y + 8, nameWidth, 1, (float) done / row.crafts(), Ui.TEAL);
            }
        }
    }

    /** Whether a row at depth {@code k} follows row {@code i} before the tree leaves that branch. */
    private static boolean continues(List<CraftPlanSync.Row> rows, int i, int k) {
        for (int j = i + 1; j < rows.size(); j++) {
            int d = Math.min(rows.get(j).depth(), TREE_DEEPEST);
            if (d <= k) {
                return d == k;
            }
        }
        return false;
    }

    /** A row of a running job's plan: crafts made of all, how many came from storage, what it still waits for. */
    private static Component treeStatus(CraftPlanSync.Row row, long done, CraftJobsSync.Job job) {
        Component out = Component.empty();
        if (row.stored() > 0) {
            out = out.copy().append(Component.literal(abbreviate(row.stored())).withColor(Ui.DIM));
        }
        if (row.crafts() > 0) {
            String made = abbreviate(done) + "/" + abbreviate(row.crafts());
            out = out.copy().append(Component.literal((row.stored() > 0 ? " " : "") + made).withColor(done >= row.crafts() ? Ui.TEAL : Ui.TEXT));
        }
        if (row.missing() > 0) {
            boolean waiting = !job.lacking().isEmpty() && row.kind().matches(job.lacking());
            out = out.copy().append(Component.literal((row.stored() > 0 || row.crafts() > 0 ? " " : "") + "−" + abbreviate(row.missing()))
                    .withColor(waiting ? Ui.RED : Ui.DIM));
        }
        return out;
    }

    /** A count in the cell's corner, small enough to sit under the item. */
    private void count(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x + CELL - 1, y + CELL - 1);
        g.pose().scale(0.5f, 0.5f);
        g.text(font, text, -font.width(text), -font.lineHeight + 1, color, true);
        g.pose().popMatrix();
    }

    /** 999, 1.2K, 12K, 123K, 1.2M and so on. */
    static String abbreviate(long n) {
        if (n < 1000) {
            return Long.toString(n);
        }
        String[] units = {"K", "M", "G", "T"};
        double v = n;
        int u = -1;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        return (v < 10 ? String.format(Locale.ROOT, "%.1f", Math.floor(v * 10) / 10) : Long.toString((long) v)) + units[u];
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (view == View.JOBS) {
            int row = (mouseY - topPos - JOB_Y) / JOB_ROW;
            if (mouseY - topPos >= JOB_Y && row < JOB_ROWS && scroll + row < menu.jobs.size() && onPanel(mouseX, mouseY)
                    && mouseX - leftPos < GRID_X + PANEL_W - 12) {
                g.setTooltipForNextFrame(font, Component.translatable("residual.craft.job_help"), mouseX, mouseY);
            }
        }
        if (view == View.JOB) {
            int row = (mouseY - topPos - TREE_Y) / TREE_ROW;
            if (mouseY - topPos >= TREE_Y && row < TREE_ROWS && scroll + row < menu.tree.size() && onPanel(mouseX, mouseY)) {
                g.setTooltipForNextFrame(font, menu.tree.get(scroll + row).kind().getHoverName(), mouseX, mouseY);
            }
        }
        Slot model = menu.getSlot(TerminalMenu.MODEL);
        if (!model.hasItem() && menu.getCarried().isEmpty() && Ui.inside(mouseX - leftPos, mouseY - topPos, model.x, model.y, 16, 16)) {
            g.setTooltipForNextFrame(font.split(Component.translatable("residual.search.slot_hint"), 200), mouseX, mouseY);
        }
        if (view != View.GRID) {
            return;
        }
        if (!menu.meaningProblem.isEmpty() && search.getValue().startsWith(MEANING)
                && Ui.inside(mouseX - leftPos, mouseY - topPos, GRID_X, USAGE_Y, COLS * CELL, 10)) {
            g.setTooltipForNextFrame(font.split(Component.translatable(menu.meaningProblem), 200), mouseX, mouseY);
        }
        Kept e = at(mouseX, mouseY);
        if (e != null && e.isFluid()) {
            fluidTooltip(g, e, mouseX, mouseY);
        } else if (e != null && menu.getCarried().isEmpty()) {
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(e.item().toStack()));
            lines.add(Component.translatable("residual.storage.stored", Ui.count(e.count())).withColor(Ui.TEAL));
            if (meant(e) > 0) {
                lines.add(Component.translatable("residual.search.sure", Math.round(100 * meant(e))).withColor(Ui.TEAL));
            }
            if (menu.craftable.contains(e.item())) {
                lines.add(Component.translatable(e.count() == 0 ? "residual.craft.click_help" : "residual.craft.craft_help").withColor(Ui.TEAL));
            }
            lines.add(Component.translatable(menu.pins.contains(e.item()) ? "residual.storage.unpin_help" : "residual.storage.pin_help").withColor(Ui.DIM));
            g.setTooltipForNextFrame(font, lines, java.util.Optional.empty(), e.item().toStack(), mouseX, mouseY);
        }
    }

    /** A fluid's name and how much there is, and what a click does with something to hold it. */
    private void fluidTooltip(GuiGraphicsExtractor g, Kept e, int mouseX, int mouseY) {
        List<Component> lines = List.of(e.name(),
                Component.translatable("residual.storage.stored_fluid", Ui.count(e.units()), Ui.count(e.count())).withColor(Ui.TEAL),
                Component.translatable("residual.storage.fill_help").withColor(Ui.DIM));
        g.setTooltipForNextFrame(font, lines, java.util.Optional.empty(), ItemStack.EMPTY, mouseX, mouseY);
    }

    private Kept at(double mouseX, double mouseY) {
        int x = (int) mouseX - leftPos - GRID_X, y = (int) mouseY - topPos - GRID_Y;
        if (x < 0 || y < 0 || x >= COLS * CELL || y >= ROWS * CELL) {
            return null;
        }
        int index = (scroll + y / CELL) * COLS + x / CELL;
        List<Kept> list = shown();
        return index < list.size() ? list.get(index) : null;
    }

    private boolean onGrid(double mouseX, double mouseY) {
        return Ui.inside(mouseX - leftPos, mouseY - topPos, GRID_X, GRID_Y, COLS * CELL, ROWS * CELL);
    }

    private boolean onPanel(double mouseX, double mouseY) {
        return Ui.inside(mouseX - leftPos, mouseY - topPos, GRID_X, GRID_Y, PANEL_W, ROWS * CELL);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (view == View.JOBS && onPanel(event.x(), event.y())) {
            int right = GRID_X + PANEL_W - 14;
            for (int i = 0; i < JOB_ROWS && scroll + i < menu.jobs.size(); i++) {
                int y = JOB_Y + i * JOB_ROW;
                if (Ui.inside(event.x() - leftPos, event.y() - topPos, right + 2, y + 2, 10, 10)) {
                    ClientPacketDistributor.sendToServer(new CraftCancel(menu.containerId, menu.jobs.get(scroll + i).id()));
                    return true;
                }
                if (Ui.inside(event.x() - leftPos, event.y() - topPos, GRID_X, y, PANEL_W - 14, JOB_ROW)) {
                    follow(menu.jobs.get(scroll + i));
                    return true;
                }
            }
        }
        if (view == View.GRID && onGrid(event.x(), event.y())) {
            ItemStack carried = menu.getCarried();
            Kept e = at(event.x(), event.y());
            if (clickFluid(event, carried, e)) {
                return true;
            }
            if (carried.isEmpty() && e != null && event.hasAltDown()) {
                ClientPacketDistributor.sendToServer(new StoragePin(menu.containerId, e.item()));
                return true;
            }
            if (carried.isEmpty() && e != null && menu.craftable.contains(e.item())
                    && (e.count() == 0 || event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE || event.hasControlDown())) {
                ask(e.item());
                return true;
            }
            StorageClick.Action action;
            if (!carried.isEmpty()) {
                action = event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? StorageClick.Action.PUT_ONE : StorageClick.Action.PUT;
            } else if (e == null) {
                return true;
            } else {
                action = event.hasShiftDown() ? StorageClick.Action.TO_INVENTORY
                        : event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? StorageClick.Action.TAKE_HALF : StorageClick.Action.TAKE;
            }
            ClientPacketDistributor.sendToServer(new StorageClick(menu.containerId, e == null ? ItemStack.EMPTY : e.item().toStack(), action));
            return true;
        }
        if (view == View.GRID && Ui.inside(event.x() - leftPos, event.y() - topPos, BAR_X, GRID_Y, BAR_W, BAR_H)) {
            dragging = true;
            dragTo(event.y());
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    /**
     * What a click does for fluids: on a fluid it fills what the cursor holds, or does nothing with the cursor empty; a left
     * click anywhere with a filled bucket or tank pours it in, the container staying on the cursor.
     */
    private boolean clickFluid(MouseButtonEvent event, ItemStack carried, Kept e) {
        if (e != null && e.isFluid() && (carried.isEmpty() || event.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            if (!carried.isEmpty()) {
                ClientPacketDistributor.sendToServer(new StorageFill(menu.containerId, e.fluid()));
            }
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && !FluidUtil.getFirstStackContained(carried).isEmpty()) {
            ClientPacketDistributor.sendToServer(new StorageClick(menu.containerId, ItemStack.EMPTY, StorageClick.Action.POUR));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            dragTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    private void dragTo(double mouseY) {
        double f = (mouseY - topPos - GRID_Y) / BAR_H;
        scroll = Math.clamp(Math.round(f * maxScroll()), 0, maxScroll());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (onPanel(x, y)) {
            scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    /**
     * Typing goes to the focused box rather than closing the screen on the inventory key; escape steps back to the grid
     * before it closes the screen.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && view != View.GRID) {
            back();
            return true;
        }
        EditBox box = view == View.ASK ? amount : search;
        if (box.isFocused() && event.key() != GLFW.GLFW_KEY_ESCAPE) {
            if (view == View.ASK && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
                start();
                return true;
            }
            return box.keyPressed(event) || true;
        }
        return super.keyPressed(event);
    }

    /**
     * On the grid, {@code cell:0} to {@code cell:53} as shown, and {@code kind:minecraft:coal} for a kind in view whatever its
     * place; {@code plan:0}, {@code job:0}, {@code job:0:cancel} and {@code tree:0} in the other views, from the first row shown.
     */
    @Override
    public void parts(Parts out) {
        out.add("model", this, menu.getSlot(TerminalMenu.MODEL));
        for (int i = 0; i < TerminalMenu.RESULT - TerminalMenu.CRAFT; i++) {
            out.add("craft:" + i, this, menu.getSlot(TerminalMenu.CRAFT + i));
        }
        out.group("craft", this, TerminalMenu.CRAFT, TerminalMenu.RESULT + 1);
        out.add("craft:result", this, menu.getSlot(TerminalMenu.RESULT));
        out.add("search", search);
        out.add("sort", sortButton);
        out.add("jobs", jobsButton);
        out.add("back", backButton);
        out.add("amount", amount);
        out.add("start", startButton);
        int x = leftPos + GRID_X, y = topPos + GRID_Y;
        switch (view) {
            case GRID -> {
                out.add("grid", x, y, COLS * CELL, ROWS * CELL);
                out.add("scrollbar", leftPos + BAR_X, y, BAR_W, BAR_H);
                out.add("usage", x, topPos + USAGE_Y, COLS * CELL, 10);
                List<Kept> list = shown();
                for (int i = 0; i < COLS * ROWS; i++) {
                    int cx = x + i % COLS * CELL, cy = y + i / COLS * CELL, index = scroll * COLS + i;
                    out.add("cell:" + i, cx, cy, CELL, CELL);
                    if (index < list.size()) {
                        out.add("kind:" + list.get(index).id(), cx, cy, CELL, CELL);
                    }
                }
            }
            case ASK -> {
                out.add("plan", x, topPos + PLAN_Y, PANEL_W, PLAN_ROWS * PLAN_ROW);
                for (int i = 0; i < PLAN_ROWS; i++) {
                    out.add("plan:" + i, x, topPos + PLAN_Y + i * PLAN_ROW, PANEL_W, PLAN_ROW);
                }
            }
            case JOBS -> {
                int right = GRID_X + PANEL_W - 14;
                for (int i = 0; i < JOB_ROWS && scroll + i < menu.jobs.size(); i++) {
                    out.add("job:" + i, x, topPos + JOB_Y + i * JOB_ROW, PANEL_W - 14, JOB_ROW);
                    out.add("job:" + i + ":cancel", leftPos + right + 2, topPos + JOB_Y + i * JOB_ROW + 2, 10, 10);
                }
            }
            case JOB -> {
                out.add("tree", x, topPos + TREE_Y, PANEL_W, TREE_ROWS * TREE_ROW);
                for (int i = 0; i < TREE_ROWS && scroll + i < menu.tree.size(); i++) {
                    out.add("tree:" + i, x, topPos + TREE_Y + i * TREE_ROW, PANEL_W, TREE_ROW);
                }
            }
        }
    }
}
