package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.block.ForecasterBlockEntity;
import io.github.metrovoc.residual.block.ForecasterBlockEntity.Phase;
import io.github.metrovoc.residual.learn.Forecast;
import io.github.metrovoc.residual.learn.Forecast.Kind;
import io.github.metrovoc.residual.learn.Growth;
import io.github.metrovoc.residual.menu.ForecasterMenu;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * What a Forecaster sees: the crops and machines around it, how far they moved from each place against what its model
 * expects, and how far apart the two are for each kind, which decides whether it may run them ahead. Its log and model
 * slots sit in a panel beside the window.
 */
public class ForecasterScreen extends AbstractContainerScreen<ForecasterMenu> implements Parts.Drawn {
    private static final int SIDE = ForecasterBlockEntity.SIDE, CELL = 8;
    private static final int WINDOW_W = 256;
    private static final int MAP_X = 8, MAP_Y = 28, MAP_W = SIDE * CELL + 2;
    private static final int BARS_X = 82, BARS_Y = 28, BARS_W = 166, BARS_H = MAP_W, GROUP = 14;
    /** One display under them: what it is doing, then a row for each kind of block. */
    private static final int STATE_Y = 104, ROW_X = 12, ROW_Y = 114, ROW_W = 232, ROW_H = 12, ROW_STEP = 14, LABEL_W = 62;
    /** A crop's color by age, from a seedling to ripe wheat. */
    private static final int[] AGES = {0xFF2F5A2A, 0xFF3A6B2C, 0xFF467C2E, 0xFF568C30, 0xFF6E9A33, 0xFF8CA637, 0xFFAEAF3E,
            0xFFD9B44A};
    private static final int MACHINE = 0xFF6F7880, FIRE = 0xFFFF8A3D;
    /** The gauge runs from half the progress the blocks showed to twice it. */
    private static final double SPAN = Math.log(2), SPAN_II = Math.log(1.43);
    /** The bands of {@link Forecast#BANDS}: sixteen, eight and four looks per look in greens, two in amber. */
    private static final int[] BAND_COLORS = {0xFF3F9A62, 0xFF357A4A, 0xFF2E5E33, 0xFF6B5320};
    private static final double CLOSE = Math.log(1.11), NEAR = Math.log(1.25);

    /** Something under the mouse that explains itself. */
    private record Hit(int x, int y, int w, int h, List<Component> lines) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private final List<Button> paces = new ArrayList<>();
    /** The kind of block the bars show, once a tab is clicked. */
    private Kind shown;

    public ForecasterScreen(ForecasterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ForecasterMenu.SIDE_X + ForecasterMenu.SIDE_W, ForecasterMenu.INVENTORY_Y + 82);
        inventoryLabelY = ForecasterMenu.INVENTORY_Y - 10;
        inventoryLabelX = ForecasterMenu.INVENTORY_X;
    }

    @Override
    protected void init() {
        super.init();
        paces.clear();
        if (!third()) {
            return;
        }
        int[] all = ForecasterBlockEntity.PACES;
        for (int i = 0; i < all.length; i++) {
            int id = i;
            paces.add(addRenderableWidget(Button.builder(Component.literal("×" + all[i]), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id))
                    .bounds(leftPos + ForecasterMenu.PACE_X, topPos + ForecasterMenu.PACE_Y + i * ForecasterMenu.PACE_STEP, ForecasterMenu.PACE_W, ForecasterMenu.PACE_H).build()));
        }
    }

    /** What each pace would ask, with the compute it has, and whether it can start one. */
    @Override
    protected void containerTick() {
        super.containerTick();
        int[] all = ForecasterBlockEntity.PACES;
        for (int i = 0; i < paces.size(); i++) {
            int pace = all[i], asks = ForecasterBlockEntity.compute(pace);
            long blocks = kind(Kind.MACHINES, ForecasterBlockEntity.COUNT) + kind(Kind.CROPS, ForecasterBlockEntity.COUNT) / ForecasterBlockEntity.CROPS_A_MACHINE;
            Button b = paces.get(i);
            b.active = data(ForecasterBlockEntity.PACE) == 0 && data(ForecasterBlockEntity.COMPUTE) >= asks && data(ForecasterBlockEntity.FASTEST) >= pace;
            b.setTooltip(Tooltip.create(Component.translatable("residual.forecaster.burst_help", pace, Ui.count((pace - 1) * Math.max(1, blocks)), asks,
                    data(ForecasterBlockEntity.COMPUTE), data(ForecasterBlockEntity.FASTEST))));
        }
    }

    private int data(int i) {
        return menu.data.get(i);
    }

    private int kind(Kind kind, int field) {
        return data(ForecasterBlockEntity.KINDS + kind.ordinal() * ForecasterBlockEntity.PER_KIND + field);
    }

    private Phase phase(Kind kind) {
        Phase[] all = Phase.values();
        return all[Math.floorMod(kind(kind, ForecasterBlockEntity.PHASE), all.length)];
    }

    private ForecasterBlockEntity.State state() {
        ForecasterBlockEntity.State[] all = ForecasterBlockEntity.State.values();
        return all[Math.floorMod(data(ForecasterBlockEntity.STATE), all.length)];
    }

    /** A rate sent in ten-thousandths, or NaN. */
    private float rate(int i) {
        int raw = (short) data(i);
        return raw == -1 ? Float.NaN : raw / 10_000f;
    }

    /** The kind of block the bars show: the one picked, or else the one it watches more of. */
    private Kind shown() {
        if (shown != null) {
            return shown;
        }
        return kind(Kind.MACHINES, ForecasterBlockEntity.COUNT) > 0 && kind(Kind.CROPS, ForecasterBlockEntity.COUNT) == 0
                ? Kind.MACHINES : Kind.CROPS;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        // the slots' panel, tucked under the window's edge
        Ui.window(g, leftPos + ForecasterMenu.SIDE_X, topPos + ForecasterMenu.SIDE_Y, ForecasterMenu.SIDE_W, third() ? ForecasterMenu.SIDE_H_III : ForecasterMenu.SIDE_H);
        Ui.window(g, leftPos, topPos, WINDOW_W, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        ghost(g, ForecasterBlockEntity.LOG, new ItemStack(ModItems.FIELD_LOG.get()));
        ghost(g, ForecasterBlockEntity.MODEL, new ItemStack(ModItems.CHECKPOINT.get()));
        if (third()) {
            // its power, and while a burst runs the button at its pace is underlined
            Ui.bar(g, leftPos + ForecasterMenu.PACE_X, topPos + ForecasterMenu.POWER_Y, ForecasterMenu.PACE_W, 4, data(ForecasterBlockEntity.POWER_SHOWN) / 1000f, Ui.AMBER);
            int[] all = ForecasterBlockEntity.PACES;
            for (int i = 0; i < all.length; i++) {
                if (data(ForecasterBlockEntity.PACE) == all[i]) {
                    int y = topPos + ForecasterMenu.PACE_Y + i * ForecasterMenu.PACE_STEP + ForecasterMenu.PACE_H;
                    g.fill(leftPos + ForecasterMenu.PACE_X + 2, y, leftPos + ForecasterMenu.PACE_X + ForecasterMenu.PACE_W - 2, y + 1, Ui.TEAL);
                }
            }
        }
        Ui.screen(g, leftPos + MAP_X, topPos + MAP_Y, MAP_W, MAP_W);
        Ui.screen(g, leftPos + BARS_X, topPos + BARS_Y, BARS_W, BARS_H);
        Ui.screen(g, leftPos + 8, topPos + STATE_Y - 4, WINDOW_W - 16, ROW_Y + 2 * ROW_STEP + 2 - (STATE_Y - 4));
    }

    /** A faint picture of what belongs in an empty slot. */
    private void ghost(GuiGraphicsExtractor g, int index, ItemStack stack) {
        Slot slot = menu.getSlot(index);
        if (slot.getItem().isEmpty()) {
            int sx = leftPos + slot.x, sy = topPos + slot.y;
            g.item(stack, sx, sy);
            g.fill(sx, sy, sx + 16, sy + 16, 0xB08B8B8B);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        hits.clear();
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
        slotHint(ForecasterBlockEntity.LOG, "residual.forecaster.log_hint");
        slotHint(ForecasterBlockEntity.MODEL, "residual.forecaster.model_hint");
        tabs(g);
        field(g);
        bars(g);
        status(g);
        for (Kind kind : Kind.values()) {
            row(g, kind, ROW_Y + kind.ordinal() * ROW_STEP);
        }
    }

    private void slotHint(int index, String key) {
        Slot slot = menu.getSlot(index);
        if (!slot.hasItem()) {
            hits.add(new Hit(slot.x, slot.y, 16, 16, List.of(Component.translatable(key))));
        }
    }

    /** The two kinds the bars can show, as tabs over them. */
    private void tabs(GuiGraphicsExtractor g) {
        int x = BARS_X;
        for (Kind kind : Kind.values()) {
            Component label = name(kind);
            boolean on = shown() == kind;
            g.text(font, label, x, BARS_Y - 10, on ? Ui.LABEL : Ui.LABEL_DIM, false);
            if (on) {
                g.fill(x, BARS_Y - 1, x + font.width(label), BARS_Y, Ui.LABEL);
            }
            x += font.width(label) + 10;
        }
    }

    private static Component name(Kind kind) {
        return Component.translatable(kind == Kind.CROPS ? "residual.forecaster.crops" : "residual.forecaster.machines");
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0 && event.y() >= topPos + BARS_Y - 11 && event.y() < topPos + BARS_Y) {
            int x = leftPos + BARS_X;
            for (Kind kind : Kind.values()) {
                int w = font.width(name(kind));
                if (event.x() >= x && event.x() < x + w) {
                    shown = kind;
                    return true;
                }
                x += w + 10;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    /** The blocks around it from above, north up: crops by their age, machines idle or at work; what it ran ahead is outlined. */
    private void field(GuiGraphicsExtractor g) {
        for (int cell = 0; cell < SIDE * SIDE; cell++) {
            int value = data(ForecasterBlockEntity.MAP + cell / 4) >> (cell % 4 * 4) & 0xF;
            boolean flashed = (data(ForecasterBlockEntity.FLASH + cell / 16) >> (cell % 16) & 1) != 0;
            int x = MAP_X + 1 + cell % SIDE * CELL, y = MAP_Y + 1 + cell / SIDE * CELL;
            if (value >= ForecasterBlockEntity.IDLE_MACHINE) {
                boolean working = value == ForecasterBlockEntity.WORKING_MACHINE;
                g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, MACHINE);
                g.fill(x + 2, y + 4, x + CELL - 2, y + CELL - 2, working ? FIRE : Ui.GRID);
                hits.add(new Hit(x, y, CELL, CELL, List.of(Component.translatable(working ? "residual.forecaster.machine_working"
                        : "residual.forecaster.machine_idle"))));
            } else if (value > 0) {
                g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, AGES[Math.min(value - 1, Growth.RIPE)]);
                int age = value - 1;
                hits.add(new Hit(x, y, CELL, CELL, List.of(age >= Growth.RIPE ? Component.translatable("residual.forecaster.ripe")
                        : Component.translatable("residual.forecaster.age", age, Growth.RIPE))));
            } else if (cell == SIDE * SIDE / 2) {
                g.fill(x + 2, y + 2, x + CELL - 2, y + CELL - 2, Ui.TEAL);
            } else {
                g.fill(x + 3, y + 3, x + CELL - 3, y + CELL - 3, Ui.GRID);
            }
            if (flashed && value > 0) {
                outline(g, x, y, CELL, CELL, Ui.TEAL);
            }
        }
        hits.add(new Hit(MAP_X, MAP_Y, MAP_W, MAP_W, List.of(Component.translatable("residual.forecaster.field_hint"))));
    }

    /** How far the shown kind moved per look from each place: what the blocks did beside what the model expects. */
    private void bars(GuiGraphicsExtractor g) {
        Kind kind = shown();
        int n = kind.from, first = kind == Kind.CROPS ? 0 : Growth.RIPE;
        float[] seen = new float[n], expects = new float[n];
        float top = 0;
        for (int a = 0; a < n; a++) {
            seen[a] = rate(ForecasterBlockEntity.SEEN + first + a);
            expects[a] = rate(ForecasterBlockEntity.EXPECTS + first + a);
            top = Math.max(top, Math.max(Float.isNaN(seen[a]) ? 0 : Math.abs(seen[a]), Float.isNaN(expects[a]) ? 0 : Math.abs(expects[a])));
        }
        int legend = BARS_X + 4;
        g.fill(legend, BARS_Y + 4, legend + 5, BARS_Y + 9, Ui.AMBER);
        Component seenLabel = Component.translatable("residual.forecaster.seen");
        g.text(font, seenLabel, legend + 8, BARS_Y + 3, Ui.DIM, false);
        legend += 8 + font.width(seenLabel) + 8;
        g.fill(legend, BARS_Y + 4, legend + 5, BARS_Y + 9, Ui.TEAL);
        g.text(font, Component.translatable("residual.forecaster.expected"), legend + 8, BARS_Y + 3, Ui.DIM, false);
        if (top == 0) {
            int y = BARS_Y + 18;
            String key = kind == Kind.CROPS ? "residual.forecaster.no_growth" : "residual.forecaster.no_work";
            for (FormattedCharSequence line : font.split(Component.translatable(key), BARS_W - 8)) {
                g.text(font, line, BARS_X + 4, y, Ui.DIM, false);
                y += 10;
            }
            return;
        }
        int base = BARS_Y + BARS_H - 12, height = base - (BARS_Y + 15);
        int x0 = BARS_X + (BARS_W - n * GROUP) / 2;
        g.fill(x0, base, x0 + n * GROUP, base + 1, Ui.GRID);
        for (int a = 0; a < n; a++) {
            int gx = x0 + a * GROUP;
            bar(g, gx + 1, base, height, seen[a] / top, Ui.AMBER);
            bar(g, gx + 7, base, height, expects[a] / top, Ui.TEAL);
            String label = kind == Kind.CROPS ? Integer.toString(a) : String.valueOf((char) (Growth.IDLE + a));
            g.text(font, label, gx + (GROUP - font.width(label)) / 2 + 1, base + 2, Ui.DIM, false);
            List<Component> lines = new ArrayList<>();
            if (kind == Kind.CROPS) {
                lines.add(Component.translatable("residual.forecaster.age_title", a));
                lines.add(Component.translatable("residual.forecaster.seen_rate", per(seen[a])).withColor(Ui.AMBER));
            } else {
                lines.add(Component.translatable("residual.forecaster.eighth_title", label, a));
                lines.add(Component.translatable("residual.forecaster.seen_progress", per(seen[a])).withColor(Ui.AMBER));
            }
            lines.add(Component.translatable("residual.forecaster.expected_rate", per(expects[a])).withColor(Ui.TEAL));
            hits.add(new Hit(gx, BARS_Y + 12, GROUP, BARS_H - 12, lines));
        }
    }

    private static void bar(GuiGraphicsExtractor g, int x, int base, int height, float fraction, int color) {
        if (!Float.isNaN(fraction) && fraction > 0) {
            int h = Math.max(1, Math.round(height * Math.min(1, fraction)));
            g.fill(x, base - h, x + 5, base, color);
        }
    }

    private static String per(float rate) {
        return Float.isNaN(rate) ? "–" : String.format(Locale.ROOT, "%.3f", rate);
    }

    /** What it is doing with its model. */
    private void status(GuiGraphicsExtractor g) {
        ForecasterBlockEntity.State state = state();
        int crops = kind(Kind.CROPS, ForecasterBlockEntity.COUNT), machines = kind(Kind.MACHINES, ForecasterBlockEntity.COUNT);
        boolean ahead = false, checking = false, refused = false;
        for (Kind kind : Kind.values()) {
            ahead |= phase(kind) == Phase.AHEAD;
            checking |= phase(kind) == Phase.CHECKING;
            refused |= phase(kind) == Phase.REFUSED;
        }
        Component line = switch (state) {
            case NOTHING -> Component.translatable("residual.forecaster.state.nothing", ForecasterBlockEntity.RADIUS);
            case WATCHING -> Component.translatable("residual.forecaster.state.watching", Ui.count(crops), Ui.count(machines));
            case NO_MODEL -> Component.translatable(ForecasterBlockEntity.PROBLEMS.get(Math.floorMod(data(ForecasterBlockEntity.PROBLEM),
                    ForecasterBlockEntity.PROBLEMS.size())));
            case PEEKS -> Component.translatable("residual.forecaster.state.peeks");
            case READY -> ahead ? Component.translatable("residual.forecaster.state.ahead")
                    : checking ? Component.translatable("residual.forecaster.state.checking")
                    : refused ? Component.translatable("residual.forecaster.state.refused")
                    : Component.translatable("residual.forecaster.state.watching", Ui.count(crops), Ui.count(machines));
        };
        int color = switch (state) {
            case NOTHING -> Ui.DIM;
            case NO_MODEL, PEEKS -> Ui.RED;
            case READY -> ahead ? Ui.GREEN : refused && !checking ? Ui.RED : Ui.TEAL;
            default -> Ui.TEAL;
        };
        List<FormattedCharSequence> lines = font.split(line, ROW_W);
        for (int i = 0; i < Math.min(1, lines.size()); i++) {
            g.text(font, lines.get(i), ROW_X, STATE_Y, color, false);
        }
        if (lines.size() > 1) {
            hits.add(new Hit(ROW_X, STATE_Y, ROW_W, 9, List.of(line)));
        }
    }

    /** One kind of block: how many it watches, then the progress seen so far or how close the model came to it. */
    private void row(GuiGraphicsExtractor g, Kind kind, int y) {
        int count = kind(kind, ForecasterBlockEntity.COUNT);
        Phase phase = phase(kind);
        Component label = Component.translatable("residual.forecaster.kind_count", name(kind), Ui.count(count));
        g.text(font, label, ROW_X, y + 2, phase == Phase.NONE ? Ui.DIM : Ui.TEXT, false);
        int x0 = ROW_X + LABEL_W;
        int ratio = kind(kind, ForecasterBlockEntity.RATIO);
        if (phase == Phase.NONE) {
            g.text(font, Component.translatable("residual.forecaster.none_near"), x0 + 4, y + 2, Ui.DIM, false);
        } else if ((phase == Phase.AHEAD || phase == Phase.REFUSED) && ratio >= 0) {
            gauge(g, kind, x0, y, Math.log(ratio / 1000.0), kind(kind, ForecasterBlockEntity.AHEAD));
        } else {
            progress(g, kind, x0, y, kind(kind, ForecasterBlockEntity.GROWN), phase == Phase.CHECKING);
        }
    }

    /** Progress seen so far, toward what a check needs. */
    private void progress(GuiGraphicsExtractor g, Kind kind, int x, int y, int grown, boolean checking) {
        String count = checking ? Component.translatable("residual.forecaster.checking_short").getString()
                : grown < Forecast.MIN_GROWTH ? grown + " / " + Forecast.MIN_GROWTH : Ui.count(grown);
        int x0 = x + 4, x1 = ROW_X + ROW_W - 6 - font.width(count);
        Ui.bar(g, x0, y + 4, x1 - x0 - 4, 4, (float) grown / Forecast.MIN_GROWTH, grown < Forecast.MIN_GROWTH ? 0xFF1F9A8C : Ui.TEAL);
        g.text(font, count, x1, y + 2, Ui.TEXT, false);
        hits.add(new Hit(x, y, ROW_X + ROW_W - x, ROW_H, List.of(Component.translatable(kind == Kind.CROPS
                ? "residual.forecaster.grown_hint" : "residual.forecaster.made_hint", Forecast.MIN_GROWTH))));
    }

    /**
     * How much progress the model expects against how much there was, on a log scale: red where it is refused, amber
     * where it runs the blocks two looks per look, greens where it runs them four, and on Forecaster II, closer in, eight
     * and sixteen. Forecaster II's scale is drawn closer, from 70% to 143%, so its narrow bands show.
     */
    private void gauge(GuiGraphicsExtractor g, Kind kind, int x, int y, double off, int ahead) {
        String shown = ahead > 0 ? "×" + ahead : Component.translatable("residual.forecaster.refused_short").getString();
        int x0 = x + 4, x1 = ROW_X + ROW_W - 8 - Math.max(font.width("×16"), font.width(shown));
        int w = x1 - x0 - 4;
        double span = second() ? SPAN_II : SPAN;
        int bands = second() ? Forecast.BANDS.length : 2;
        // from the outside in: refused, then each band a factor faster
        g.fill(x0, y + 4, x0 + w, y + 8, 0xFF5E2A2A);
        for (int i = Forecast.BANDS.length - 1; i >= Forecast.BANDS.length - bands; i--) {
            double edge = Math.log(Forecast.BANDS[i]);
            g.fill(at(x0, w, -edge, span), y + 4, at(x0, w, edge, span), y + 8, BAND_COLORS[i]);
        }
        int one = at(x0, w, 0, span);
        g.fill(one, y + 2, one + 1, y + 10, Ui.DIM);
        int mark = at(x0, w, Math.max(-span, Math.min(span, off)), span);
        g.fill(mark - 1, y + 1, mark + 1, y + 11, Ui.TEXT);
        double a = Math.abs(off);
        int color = a <= CLOSE ? Ui.GREEN : a <= NEAR ? Ui.AMBER : Ui.RED;
        g.text(font, shown, ROW_X + ROW_W - 4 - font.width(shown), y + 2, color, false);
        String share = String.format(Locale.ROOT, "%.0f%%", Math.exp(off) * 100);
        hits.add(new Hit(x, y, ROW_X + ROW_W - x, ROW_H, List.of(Component.translatable(kind == Kind.CROPS
                        ? "residual.forecaster.fit_title" : "residual.forecaster.fit_title_machines", share),
                Component.translatable("residual.forecaster.fit_hint").withColor(Ui.DIM))));
    }

    private static int at(int x0, int w, double off, double span) {
        return x0 + (int) Math.round((off + span) / (2 * span) * w);
    }

    /** Whether this is Forecaster II or III, which run blocks up to sixteen times as fast. */
    private boolean second() {
        return minecraft != null && minecraft.level != null && !minecraft.level.getBlockState(menu.pos).is(ModBlocks.FORECASTER.get());
    }

    private boolean third() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockState(menu.pos).is(ModBlocks.FORECASTER_III.get());
    }

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        for (Hit h : hits) {
            if (Ui.inside(mx, my, h.x(), h.y(), h.w(), h.h())) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                h.lines().forEach(c -> lines.addAll(font.split(c, 220)));
                g.setTooltipForNextFrame(lines, mouseX, mouseY);
                break;
            }
        }
    }

    /** {@code tab:crops} and {@code tab:machines} over the bars, {@code row:crops} and {@code row:machines} under them. */
    @Override
    public void parts(Parts out) {
        out.add("log", this, menu.getSlot(ForecasterBlockEntity.LOG));
        out.add("model", this, menu.getSlot(ForecasterBlockEntity.MODEL));
        out.add("field", leftPos + MAP_X, topPos + MAP_Y, MAP_W, MAP_W);
        out.add("bars", leftPos + BARS_X, topPos + BARS_Y, BARS_W, BARS_H);
        out.add("state", leftPos + ROW_X, topPos + STATE_Y, ROW_W, 9);
        int x = leftPos + BARS_X;
        for (Kind kind : Kind.values()) {
            String id = kind.name().toLowerCase(Locale.ROOT);
            int w = font.width(name(kind));
            out.add("tab:" + id, x, topPos + BARS_Y - 11, w, 11, shown() == kind);
            out.add("row:" + id, leftPos + ROW_X, topPos + ROW_Y + kind.ordinal() * ROW_STEP, ROW_W, ROW_H);
            x += w + 10;
        }
        if (third()) {
            out.add("power", leftPos + ForecasterMenu.PACE_X, topPos + ForecasterMenu.POWER_Y, ForecasterMenu.PACE_W, 4);
            for (int i = 0; i < paces.size(); i++) {
                out.add("pace:" + ForecasterBlockEntity.PACES[i], paces.get(i));
            }
        }
    }
}
