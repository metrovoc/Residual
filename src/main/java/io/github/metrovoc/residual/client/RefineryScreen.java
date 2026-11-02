package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.refine.Domain;
import io.github.metrovoc.residual.refine.Duplicates;
import io.github.metrovoc.residual.refine.RefineryBlockEntity;
import io.github.metrovoc.residual.refine.RefineryMenu;
import io.github.metrovoc.residual.refine.RefinerySync;
import io.github.metrovoc.residual.refine.Stage;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A Refinery machine's screen. The window holds one dark display, a row an idea, with what each number means in its tooltip. Its
 * slots sit in a panel beside the window and its settings in a toolbar outside it; a Mixer's domains are set on their own rows,
 * and a Sieve draws the curve its bands make in a panel of its own.
 */
public class RefineryScreen extends AbstractContainerScreen<RefineryMenu> implements Parts.Drawn {
    private static final int SHOW_X = 8, SHOW_Y = 18, SHOW_W = 240, ROW_H = 11, ROWS = 8, SHOW_H = ROWS * ROW_H + 2;
    /** The side panel's room around its slots, and the Sieve's curve. */
    private static final int PAD = 7, CURVE = 56;
    private int scroll;
    private final Map<Integer, IconButton> toolbar = new HashMap<>();
    private final Map<Integer, Integer> shownValues = new HashMap<>();
    private final int sideW, sideH;

    public RefineryScreen(RefineryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, RefineryMenu.WINDOW_W - 4 + side(menu.stage)[0], RefineryMenu.INVENTORY_Y + 82);
        sideW = side(menu.stage)[0];
        sideH = side(menu.stage)[1];
        inventoryLabelY = RefineryMenu.INVENTORY_Y - 10;
        inventoryLabelX = RefineryMenu.INVENTORY_X;
    }

    /** How wide and high the panel beside the window is. */
    private static int[] side(Stage stage) {
        if (stage == Stage.DUPLICATES) {
            return new int[]{CURVE + 2 * PAD + 2, CURVE + 2 * PAD + 2};
        }
        int slots = stage.slots;
        return slots == 0 ? new int[]{4, 0} : new int[]{Math.min(3, slots) * 18 + 2 * PAD - 3, (slots + 2) / 3 * 18 + 2 * PAD - 3};
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos - IconButton.SIZE - 2, y = topPos + 4, step = IconButton.SIZE + 1;
        if (menu.stage == Stage.INTAKE) {
            addRenderableWidget(IconButton.action(x, y, "refinery_start", Component.translatable("residual.refinery.start"),
                    Component.translatable("residual.refinery.start_hint"), () -> press(RefineryBlockEntity.START)));
            addRenderableWidget(IconButton.action(x, y + step, "refinery_measure", Component.translatable("residual.refinery.measure"),
                    Component.translatable("residual.refinery.measure_hint"), () -> press(RefineryBlockEntity.MEASURE)));
            return;
        }
        int at = 0;
        for (int i = 0; i < menu.stage.settings.size(); i++) {
            if (onRow(i)) {
                continue;
            }
            Stage.Setting s = menu.stage.settings.get(i);
            int index = i;
            toolbar.put(index, addRenderableWidget(IconButton.setting(x, y + at * step, List.of("refinery_" + s.id()), Component.translatable("residual.refinery.setting." + s.id()),
                    values(s), menu.data.get(RefineryBlockEntity.SETTINGS + i), Component.translatable("residual.refinery.setting." + s.id() + ".hint"),
                    v -> press(index * RefineryBlockEntity.SETTING_STEP + v))));
            shownValues.put(index, menu.data.get(RefineryBlockEntity.SETTINGS + i));
            at++;
        }
    }

    /** The server's settings arrive after the screen opens, and a friend can change them. */
    @Override
    protected void containerTick() {
        super.containerTick();
        toolbar.forEach((i, button) -> {
            int value = menu.data.get(RefineryBlockEntity.SETTINGS + i);
            if (shownValues.getOrDefault(i, -1) != value) {
                shownValues.put(i, value);
                button.set(value);
            }
        });
    }

    private boolean onRow(int setting) {
        return menu.stage == Stage.MIXER && setting < Domain.values().length;
    }

    /** What a setting's values are called. */
    private static List<String> values(Stage.Setting s) {
        List<String> out = new ArrayList<>();
        switch (s.id()) {
            case "near" -> {
                for (float t : Duplicates.THRESHOLDS) {
                    out.add(String.format(Locale.ROOT, "%.1f", t));
                }
            }
            case "sure" -> {
                for (float t : Stage.SURE) {
                    out.add(String.format(Locale.ROOT, "%.0f%%", 100 * t));
                }
            }
            case "share" -> {
                for (float t : Stage.SHARES) {
                    out.add(String.format(Locale.ROOT, "%.0f%%", 100 * t));
                }
            }
            case "after" -> {
                for (int t : Stage.AFTER) {
                    out.add(String.format(Locale.ROOT, "%,d", t));
                }
            }
            case "program" -> {
                for (String p : List.of("sums", "orders", "sorting", "book")) {
                    out.add(Component.translatable("residual.refinery.program." + p).getString());
                }
            }
            case "anneal" -> {
                out.add(Component.translatable("residual.refinery.off").getString());
                for (int d = 0; d < Domain.WRITTEN; d++) {
                    out.add(Component.translatable("residual.refinery.domain." + Domain.values()[d].id).getString());
                }
            }
            case "remove" -> {
                out.add(Component.translatable("residual.refinery.only_look").getString());
                out.add(Component.translatable("residual.refinery.takes_out").getString());
            }
            default -> {
                out.add(Component.translatable("residual.refinery.off").getString());
                out.add(Component.translatable("residual.refinery.on").getString());
            }
        }
        return out;
    }

    private void press(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    /** What the display shows: while it works, how far along it is, then the rows. */
    private List<RefinerySync.Row> shown() {
        int state = menu.data.get(RefineryBlockEntity.STATE);
        List<RefinerySync.Row> out = new ArrayList<>();
        if (state != RefineryBlockEntity.State.IDLE.ordinal()) {
            int needed = Math.max(1, menu.data.get(RefineryBlockEntity.NEEDED)), worked = menu.data.get(RefineryBlockEntity.WORKED);
            String key = "residual.refinery.state." + RefineryBlockEntity.State.values()[state].name().toLowerCase(Locale.ROOT);
            out.add(RefinerySync.Row.of(key, state == RefineryBlockEntity.State.MEASURING.ordinal() ? "" : String.format(Locale.ROOT, "%d%%", Math.min(100, 100 * worked / needed)))
                    .tone(state == RefineryBlockEntity.State.NO_POWER.ordinal() ? RefinerySync.BAD : RefinerySync.LIT)
                    .bar(state == RefineryBlockEntity.State.MEASURING.ordinal() ? -1 : (float) worked / needed));
        }
        out.addAll(menu.rows);
        return out;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        if (sideH > 0) {
            Ui.window(g, leftPos + RefineryMenu.SIDE_X, topPos + RefineryMenu.SIDE_Y, sideW, sideH);
        }
        Ui.window(g, leftPos, topPos, RefineryMenu.WINDOW_W, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        ghost(g);
        Ui.screen(g, leftPos + SHOW_X, topPos + SHOW_Y, SHOW_W, SHOW_H);
        if (menu.stage == Stage.DUPLICATES) {
            curve(g, leftPos + RefineryMenu.SIDE_X + PAD + 1, topPos + RefineryMenu.SIDE_Y + PAD + 1);
        }
    }

    /** Faint pictures of what belongs in empty slots. */
    private void ghost(GuiGraphicsExtractor g) {
        switch (menu.stage) {
            case INTAKE -> ghost(g, 0, new ItemStack(ModItems.RELAY_DUMP.get()));
            case CLASSIFIER -> ghost(g, 0, new ItemStack(ModItems.CHECKPOINT.get()));
            case PROGRAMS -> ghost(g, 0, new ItemStack(Items.WRITABLE_BOOK));
            case TANK -> {
                ghost(g, Stage.TOKENIZER, new ItemStack(ModItems.TOKENIZER.get()));
                ghost(g, Stage.CELL, new ItemStack(ModItems.TOKEN_CELL.get()));
            }
            default -> {
            }
        }
    }

    private void ghost(GuiGraphicsExtractor g, int index, ItemStack stack) {
        Slot slot = menu.getSlot(index);
        if (!slot.hasItem()) {
            Ui.ghost(g, stack, leftPos + slot.x, topPos + slot.y);
        }
    }

    /** How likely two docs are to be compared by how similar they are, with the threshold marked, and the pairs a pass compared. */
    private void curve(GuiGraphicsExtractor g, int x, int y) {
        Ui.screen(g, x, y, CURVE, CURVE);
        int setting = menu.data.get(RefineryBlockEntity.SETTINGS);
        int mark = x + Math.round(Duplicates.THRESHOLDS[setting] * CURVE);
        g.fill(mark, y, mark + 1, y + CURVE, Ui.GRID);
        int[] pairs = pairs();
        int most = 1;
        for (int p : pairs) {
            most = Math.max(most, p);
        }
        int cell = CURVE / pairs.length;
        for (int i = 0; i < pairs.length; i++) {
            if (pairs[i] > 0) {
                int h = Math.max(1, pairs[i] * (CURVE - 2) / most);
                g.fill(x + i * cell, y + CURVE - h, x + (i + 1) * cell - 1, y + CURVE, 0xFF5E4B86);
            }
        }
        for (int i = 0; i < CURVE; i++) {
            int h = (int) Math.round(Duplicates.chance(setting, (i + 0.5) / CURVE) * (CURVE - 1));
            g.fill(x + i, y + CURVE - 1 - h, x + i + 1, y + CURVE - h, Ui.TEAL);
        }
    }

    /** The compared pairs by tenth of similarity, from the row that reports them. */
    private int[] pairs() {
        int[] out = new int[10];
        for (RefinerySync.Row r : menu.rows) {
            if (r.hintArgs().size() == 3) {
                String[] counts = r.hintArgs().get(2).split(" ");
                for (int i = 0; i < Math.min(out.length, counts.length); i++) {
                    out[i] = Integer.parseInt(counts[i]);
                }
            }
        }
        return out;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
        List<RefinerySync.Row> rows = shown();
        scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() - ROWS));
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            RefinerySync.Row r = rows.get(scroll + i);
            int y = SHOW_Y + 2 + i * ROW_H;
            int color = switch (r.tone()) {
                case RefinerySync.GOOD -> Ui.GREEN;
                case RefinerySync.BAD -> Ui.AMBER;
                case RefinerySync.DIM -> Ui.DIM;
                case RefinerySync.LIT -> Ui.TEAL;
                default -> Ui.TEXT;
            };
            int valueW = font.width(r.value());
            if (!r.value().isEmpty()) {
                g.text(font, r.value(), SHOW_X + SHOW_W - 4 - valueW, y, color, false);
            }
            Component label = Component.translatable(r.label(), translated(r.args()));
            g.text(font, Ui.fit(font, label.getString(), SHOW_W - 12 - valueW), SHOW_X + 4, y, r.tone() == RefinerySync.DIM ? Ui.DIM : Ui.TEXT, false);
            if (r.bar() >= 0) {
                Ui.bar(g, SHOW_X + 4, y + 9, SHOW_W - 8, 1, r.bar(), Ui.TEAL);
            }
        }
        if (rows.size() > ROWS) {
            int h = SHOW_H - 2, knob = Math.max(4, h * ROWS / rows.size()), at = (h - knob) * scroll / (rows.size() - ROWS);
            g.fill(SHOW_X + SHOW_W - 2, SHOW_Y + 1 + at, SHOW_X + SHOW_W - 1, SHOW_Y + 1 + at + knob, Ui.DIM);
        }
    }

    /** A row's arguments are numbers or names, or keys of what they name. */
    private static Object[] translated(List<String> args) {
        Object[] out = new Object[args.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = args.get(i).startsWith("residual.") || args.get(i).startsWith("block.residual.") ? Component.translatable(args.get(i)) : args.get(i);
        }
        return out;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (Ui.inside(mx, my, SHOW_X, SHOW_Y, SHOW_W, SHOW_H - 2)) {
            List<RefinerySync.Row> rows = shown();
            int row = scroll + (my - SHOW_Y - 2) / ROW_H;
            if (row >= 0 && row < rows.size()) {
                RefinerySync.Row r = rows.get(row);
                lines.add(Component.translatable(r.label(), translated(r.args())).getVisualOrderText());
                if (!r.hint().isEmpty()) {
                    lines.addAll(font.split(Component.translatable(r.hint(), translated(r.hintArgs())).withStyle(ChatFormatting.GRAY), 220));
                }
                if (r.setting() >= 0) {
                    lines.addAll(font.split(Component.translatable("residual.icon.cycle").withStyle(ChatFormatting.DARK_GRAY), 220));
                }
            }
        } else if (menu.getCarried().isEmpty()) {
            for (int i = 0; i < menu.stage.slots; i++) {
                Slot slot = menu.getSlot(i);
                if (!slot.hasItem() && Ui.inside(mx, my, slot.x, slot.y, 16, 16)) {
                    String key = "residual.refinery." + menu.stage.id + ".slot" + (menu.stage == Stage.TANK ? i : 0);
                    lines.addAll(font.split(Component.translatable(key).withStyle(ChatFormatting.GRAY), 200));
                }
            }
        }
        if (menu.stage == Stage.DUPLICATES && Ui.inside(mx, my, RefineryMenu.SIDE_X, RefineryMenu.SIDE_Y, sideW, sideH)) {
            lines.addAll(font.split(Component.translatable("residual.refinery.duplicate_sieve.curve").withStyle(ChatFormatting.GRAY), 200));
        }
        if (!lines.isEmpty()) {
            g.setTooltipForNextFrame(lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x() - leftPos, my = event.y() - topPos;
        if (Ui.inside(mx, my, SHOW_X, SHOW_Y, SHOW_W, SHOW_H - 2)) {
            List<RefinerySync.Row> rows = shown();
            int row = scroll + (int) (my - SHOW_Y - 2) / ROW_H;
            if (row >= 0 && row < rows.size() && rows.get(row).setting() >= 0) {
                int s = rows.get(row).setting();
                int values = menu.stage.settings.get(s).values();
                int step = event.button() == 1 ? -1 : 1;
                press(s * RefineryBlockEntity.SETTING_STEP + Math.floorMod(menu.data.get(RefineryBlockEntity.SETTINGS + s) + step, values));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (Ui.inside(x - leftPos, y - topPos, SHOW_X, SHOW_Y, SHOW_W, SHOW_H)) {
            scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, shown().size() - ROWS));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void parts(Parts out) {
        out.add("rows", leftPos + SHOW_X, topPos + SHOW_Y, SHOW_W, SHOW_H);
        for (int i = 0; i < ROWS && scroll + i < shown().size(); i++) {
            out.add("row:" + i, leftPos + SHOW_X, topPos + SHOW_Y + 2 + i * ROW_H, SHOW_W, ROW_H);
        }
        if (menu.stage == Stage.TANK) {
            out.add("tokenizer", this, menu.getSlot(Stage.TOKENIZER));
            out.add("cell", this, menu.getSlot(Stage.CELL));
        } else if (menu.stage.slots > 0) {
            out.group("slots", this, 0, menu.stage.slots);
        }
        if (menu.stage == Stage.DUPLICATES) {
            out.add("curve", leftPos + RefineryMenu.SIDE_X + PAD + 1, topPos + RefineryMenu.SIDE_Y + PAD + 1, CURVE, CURVE);
        }
    }
}
