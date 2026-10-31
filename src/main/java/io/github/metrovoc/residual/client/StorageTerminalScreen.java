package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.net.StorageClick;
import io.github.metrovoc.residual.net.StorageSync;
import io.github.metrovoc.residual.storage.StorageSearch;
import io.github.metrovoc.residual.storage.TerminalMenu;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.lwjgl.glfw.GLFW;

/**
 * Everything a storage network keeps, in a grid that searches and sorts. Clicking a kind takes a stack, right-clicking
 * half, shift-clicking moves a stack to the inventory; clicking with a stack puts it in, right-clicking one of it.
 */
public class StorageTerminalScreen extends AbstractContainerScreen<TerminalMenu> {
    private static final int GRID_X = 8, GRID_Y = 20, COLS = 9, ROWS = 6, CELL = 18;
    private static final int BAR_X = GRID_X + COLS * CELL + 4, BAR_W = 12, BAR_H = ROWS * CELL;
    private static final int USAGE_Y = GRID_Y + ROWS * CELL + 3;

    private enum Sort { NAME, COUNT, MOD }

    /** Kept while the game runs, so the terminal opens the way it was left. */
    private static String lastSearch = "";
    private static Sort sort = Sort.NAME;
    private static boolean descending;

    private EditBox search;
    private Button sortButton;
    private List<StorageSync.Entry> shown = List.of();
    private List<StorageSync.Entry> seen;
    private String seenQuery;
    private Sort seenSort;
    private boolean seenDescending;
    private int scroll;
    private boolean dragging;

    public StorageTerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BAR_X + BAR_W + 8, TerminalMenu.INVENTORY_Y + 82);
        inventoryLabelY = TerminalMenu.INVENTORY_Y - 10;
        inventoryLabelX = TerminalMenu.INVENTORY_X;
    }

    @Override
    protected void init() {
        super.init();
        // the search box takes the title's place: the screen is what it finds
        search = addRenderableWidget(new EditBox(font, leftPos + GRID_X, topPos + 5, COLS * CELL - 8, 12, Component.translatable("residual.storage.search")));
        search.setMaxLength(80);
        search.setValue(lastSearch);
        search.setHint(Component.translatable("residual.storage.search_hint"));
        search.setTooltip(Tooltip.create(Component.translatable("residual.storage.search_help")));
        search.setResponder(s -> {
            lastSearch = s;
            scroll = 0;
        });
        sortButton = addRenderableWidget(Button.builder(sortLabel(), b -> {
            Sort[] all = Sort.values();
            if (Minecraft.getInstance().hasShiftDown()) {
                descending = !descending;
            } else {
                sort = all[(sort.ordinal() + 1) % all.length];
            }
            b.setMessage(sortLabel());
        }).bounds(leftPos + BAR_X - 10, topPos + 5, BAR_W + 10, 12).tooltip(Tooltip.create(Component.translatable("residual.storage.sort_help"))).build());
    }

    private Component sortLabel() {
        return Component.translatable("residual.storage.sort." + sort.name().toLowerCase(Locale.ROOT)).append(descending ? "↓" : "↑");
    }

    /** The kinds that match the search, in the chosen order; worked out again only when something changed. */
    private List<StorageSync.Entry> shown() {
        String query = search == null ? "" : search.getValue();
        if (menu.items != seen || !query.equals(seenQuery) || sort != seenSort || descending != seenDescending) {
            seen = menu.items;
            seenQuery = query;
            seenSort = sort;
            seenDescending = descending;
            Predicate<StorageSearch.Facts> wanted = StorageSearch.parse(query);
            List<StorageSync.Entry> out = new ArrayList<>();
            for (StorageSync.Entry e : menu.items) {
                if (wanted.test(new Facts(e))) {
                    out.add(e);
                }
            }
            Comparator<StorageSync.Entry> byName = Comparator.comparing(e -> e.item().getHoverName().getString().toLowerCase(Locale.ROOT));
            Comparator<StorageSync.Entry> order = switch (sort) {
                case NAME -> byName;
                case COUNT -> Comparator.<StorageSync.Entry>comparingLong(StorageSync.Entry::count).reversed().thenComparing(byName);
                case MOD -> Comparator.<StorageSync.Entry, String>comparing(e -> mod(e.item())).thenComparing(byName);
            };
            out.sort(descending ? order.reversed() : order);
            shown = out;
        }
        return shown;
    }

    private static String mod(ItemResource item) {
        return BuiltInRegistries.ITEM.getKey(item.getItem()).getNamespace();
    }

    /** What a search can ask about a kind, worked out only if it asks. */
    private record Facts(StorageSync.Entry e) implements StorageSearch.Facts {
        @Override
        public String name() {
            return e.item().getHoverName().getString();
        }

        @Override
        public String mod() {
            return StorageTerminalScreen.mod(e.item());
        }

        @Override
        public String modName() {
            return ModList.get().getModContainerById(mod()).map(c -> c.getModInfo().getDisplayName()).orElse("");
        }

        @Override
        public Collection<String> tags() {
            return e.item().typeHolder().tags().map(t -> t.location().toString()).toList();
        }

        @Override
        public String tooltip() {
            Minecraft mc = Minecraft.getInstance();
            StringBuilder out = new StringBuilder();
            for (Component line : e.item().toStack().getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)) {
                out.append(line.getString()).append(' ');
            }
            return out.toString();
        }

        @Override
        public String id() {
            return BuiltInRegistries.ITEM.getKey(e.item().getItem()).toString();
        }

        @Override
        public long count() {
            return e.count();
        }
    }

    private int rows() {
        return (shown().size() + COLS - 1) / COLS;
    }

    private int maxScroll() {
        return Math.max(0, rows() - ROWS);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        Ui.screen(g, leftPos + GRID_X - 1, topPos + GRID_Y - 1, COLS * CELL + 2, ROWS * CELL + 2);
        Ui.screen(g, leftPos + BAR_X, topPos + GRID_Y - 1, BAR_W, BAR_H + 2);
        scroll = Math.clamp(scroll, 0, maxScroll());
        int rows = Math.max(1, rows());
        int thumb = Math.max(10, BAR_H * ROWS / Math.max(ROWS, rows));
        int ty = topPos + GRID_Y + (maxScroll() == 0 ? 0 : (BAR_H - thumb) * scroll / maxScroll());
        g.fill(leftPos + BAR_X + 2, ty, leftPos + BAR_X + BAR_W - 2, ty + thumb, maxScroll() == 0 ? Ui.GRID : Ui.TEAL);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
        List<StorageSync.Entry> list = shown();
        for (int i = 0; i < COLS * ROWS; i++) {
            int index = scroll * COLS + i;
            int x = GRID_X + i % COLS * CELL, y = GRID_Y + i / COLS * CELL;
            if (index >= list.size()) {
                break;
            }
            StorageSync.Entry e = list.get(index);
            ItemStack stack = e.item().toStack();
            g.item(stack, x + 1, y + 1);
            count(g, abbreviate(e.count()), x, y);
            if (Ui.inside(mouseX - leftPos, mouseY - topPos, x, y, CELL, CELL)) {
                g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x40FFFFFF);
            }
        }
        if (list.isEmpty()) {
            Component empty = Component.translatable(menu.items.isEmpty() ? "residual.storage.empty" : "residual.storage.no_match");
            int y = GRID_Y + 6;
            for (var line : font.split(empty, COLS * CELL - 12)) {
                g.text(font, line, GRID_X + 6, y, Ui.DIM, false);
                y += 10;
            }
        }
        Component usage = menu.capacity == 0 ? Component.translatable("residual.storage.no_drives")
                : Component.translatable("residual.storage.usage", abbreviate(menu.stored), abbreviate(menu.capacity), Ui.count(menu.items.size()));
        g.text(font, usage, GRID_X, USAGE_Y + 1, Ui.LABEL_DIM, false);
    }

    /** A count in the cell's corner, small enough to sit under the item. */
    private void count(GuiGraphicsExtractor g, String text, int x, int y) {
        g.pose().pushMatrix();
        g.pose().translate(x + CELL - 1, y + CELL - 1);
        g.pose().scale(0.5f, 0.5f);
        g.text(font, text, -font.width(text), -font.lineHeight + 1, 0xFFFFFFFF, true);
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
        StorageSync.Entry e = at(mouseX, mouseY);
        if (e != null && menu.getCarried().isEmpty()) {
            List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(e.item().toStack()));
            lines.add(Component.translatable("residual.storage.stored", Ui.count(e.count())).withColor(Ui.TEAL));
            g.setTooltipForNextFrame(font, lines, java.util.Optional.empty(), e.item().toStack(), mouseX, mouseY);
        }
    }

    private StorageSync.Entry at(double mouseX, double mouseY) {
        int x = (int) mouseX - leftPos - GRID_X, y = (int) mouseY - topPos - GRID_Y;
        if (x < 0 || y < 0 || x >= COLS * CELL || y >= ROWS * CELL) {
            return null;
        }
        int index = (scroll + y / CELL) * COLS + x / CELL;
        List<StorageSync.Entry> list = shown();
        return index < list.size() ? list.get(index) : null;
    }

    private boolean onGrid(double mouseX, double mouseY) {
        return Ui.inside(mouseX - leftPos, mouseY - topPos, GRID_X, GRID_Y, COLS * CELL, ROWS * CELL);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (onGrid(event.x(), event.y())) {
            ItemStack carried = menu.getCarried();
            StorageSync.Entry e = at(event.x(), event.y());
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
        if (Ui.inside(event.x() - leftPos, event.y() - topPos, BAR_X, GRID_Y, BAR_W, BAR_H)) {
            dragging = true;
            dragTo(event.y());
            return true;
        }
        return super.mouseClicked(event, doubled);
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
        if (onGrid(x, y) || Ui.inside(x - leftPos, y - topPos, BAR_X, GRID_Y, BAR_W, BAR_H)) {
            scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    /** Typing goes to the search box while it is focused, rather than closing the screen on the inventory key. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (search.isFocused() && event.key() != GLFW.GLFW_KEY_ESCAPE) {
            return search.keyPressed(event) || true;
        }
        return super.keyPressed(event);
    }
}
