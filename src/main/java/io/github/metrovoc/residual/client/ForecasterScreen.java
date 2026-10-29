package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.block.ForecasterBlockEntity;
import io.github.metrovoc.residual.learn.Forecast;
import io.github.metrovoc.residual.learn.Growth;
import io.github.metrovoc.residual.menu.ForecasterMenu;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * What a Forecaster sees: the crops around it by age, how fast they grew from each age against what its model
 * expects, and how far apart the two are, which decides whether it may run the field ahead.
 */
public class ForecasterScreen extends AbstractContainerScreen<ForecasterMenu> {
    private static final int SIDE = ForecasterBlockEntity.SIDE, CELL = 8;
    private static final int MAP_X = 44, MAP_Y = 28, MAP_W = SIDE * CELL + 2;
    private static final int BARS_X = 126, BARS_Y = 28, BARS_W = 122, BARS_H = MAP_W, GROUP = 16;
    private static final int STATE_Y = 105, GAUGE_X = 8, GAUGE_Y = 127, GAUGE_W = 240, GAUGE_H = 12;
    /** A crop's color by age, from a seedling to ripe wheat. */
    private static final int[] AGES = {0xFF2F5A2A, 0xFF3A6B2C, 0xFF467C2E, 0xFF568C30, 0xFF6E9A33, 0xFF8CA637, 0xFFAEAF3E,
            0xFFD9B44A};
    /** The gauge runs from half the growth the field showed to twice it. */
    private static final double SPAN = Math.log(2);
    private static final double CLOSE = Math.log(1.11), NEAR = Math.log(1.25);

    /** Something under the mouse that explains itself. */
    private record Hit(int x, int y, int w, int h, List<Component> lines) {
    }

    private final List<Hit> hits = new ArrayList<>();

    public ForecasterScreen(ForecasterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 256, 234);
        inventoryLabelY = ForecasterMenu.INVENTORY_Y - 10;
        inventoryLabelX = ForecasterMenu.INVENTORY_X;
    }

    private int data(int i) {
        return menu.data.get(i);
    }

    private ForecasterBlockEntity.State state() {
        ForecasterBlockEntity.State[] all = ForecasterBlockEntity.State.values();
        return all[Math.floorMod(data(ForecasterBlockEntity.STATE), all.length)];
    }

    /** A growth rate sent in ten-thousandths, or NaN. */
    private float rate(int i) {
        int raw = data(i);
        return raw < 0 ? Float.NaN : raw / 10_000f;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        ghost(g, ForecasterBlockEntity.LOG, new ItemStack(ModItems.FIELD_LOG.get()));
        ghost(g, ForecasterBlockEntity.MODEL, new ItemStack(ModItems.CHECKPOINT.get()));
        Ui.screen(g, leftPos + MAP_X, topPos + MAP_Y, MAP_W, MAP_W);
        Ui.screen(g, leftPos + BARS_X, topPos + BARS_Y, BARS_W, BARS_H);
        Ui.screen(g, leftPos + GAUGE_X, topPos + GAUGE_Y, GAUGE_W, GAUGE_H);
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
        g.text(font, Component.translatable("residual.forecaster.log"), ForecasterMenu.LOG_X - 2, ForecasterMenu.LOG_Y - 10, Ui.LABEL, false);
        g.text(font, Component.translatable("residual.agent.model_slot"), ForecasterMenu.MODEL_X - 2, ForecasterMenu.MODEL_Y - 10, Ui.LABEL, false);
        g.text(font, Component.translatable("residual.forecaster.field"), MAP_X, MAP_Y - 10, Ui.LABEL, false);
        g.text(font, Component.translatable("residual.forecaster.growth"), BARS_X, BARS_Y - 10, Ui.LABEL, false);
        slotHint(ForecasterBlockEntity.LOG, "residual.forecaster.log_hint");
        slotHint(ForecasterBlockEntity.MODEL, "residual.forecaster.model_hint");
        field(g);
        bars(g);
        status(g);
    }

    private void slotHint(int index, String key) {
        Slot slot = menu.getSlot(index);
        if (!slot.hasItem()) {
            hits.add(new Hit(slot.x, slot.y, 16, 16, List.of(Component.translatable(key))));
        }
    }

    /** The crops around it from above, north up, each by its age; those the model just grew are outlined. */
    private void field(GuiGraphicsExtractor g) {
        for (int cell = 0; cell < SIDE * SIDE; cell++) {
            int value = data(ForecasterBlockEntity.MAP + cell / 4) >> (cell % 4 * 4) & 0xF;
            boolean flashed = (data(ForecasterBlockEntity.FLASH + cell / 16) >> (cell % 16) & 1) != 0;
            int x = MAP_X + 1 + cell % SIDE * CELL, y = MAP_Y + 1 + cell / SIDE * CELL;
            if (value > 0) {
                g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, AGES[Math.min(value - 1, Growth.RIPE)]);
                if (flashed) {
                    outline(g, x, y, CELL, CELL, Ui.TEAL);
                }
                int age = value - 1;
                hits.add(new Hit(x, y, CELL, CELL, List.of(age >= Growth.RIPE ? Component.translatable("residual.forecaster.ripe")
                        : Component.translatable("residual.forecaster.age", age, Growth.RIPE))));
            } else if (cell == SIDE * SIDE / 2) {
                g.fill(x + 2, y + 2, x + CELL - 2, y + CELL - 2, Ui.TEAL);
            } else {
                g.fill(x + 3, y + 3, x + CELL - 3, y + CELL - 3, Ui.GRID);
            }
        }
        hits.add(new Hit(MAP_X, MAP_Y, MAP_W, MAP_W, List.of(Component.translatable("residual.forecaster.field_hint",
                Ui.count(data(ForecasterBlockEntity.CROPS))))));
    }

    /** Growth per look from each age: what the field did beside what the model expects. */
    private void bars(GuiGraphicsExtractor g) {
        float[] seen = new float[Growth.RIPE], expects = new float[Growth.RIPE];
        float top = 0;
        for (int a = 0; a < Growth.RIPE; a++) {
            seen[a] = rate(ForecasterBlockEntity.SEEN + a);
            expects[a] = rate(ForecasterBlockEntity.EXPECTS + a);
            top = Math.max(top, Math.max(Float.isNaN(seen[a]) ? 0 : seen[a], Float.isNaN(expects[a]) ? 0 : expects[a]));
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
            for (FormattedCharSequence line : font.split(Component.translatable("residual.forecaster.no_growth"), BARS_W - 8)) {
                g.text(font, line, BARS_X + 4, y, Ui.DIM, false);
                y += 10;
            }
            return;
        }
        int base = BARS_Y + BARS_H - 12, height = base - (BARS_Y + 15);
        int x0 = BARS_X + (BARS_W - Growth.RIPE * GROUP) / 2;
        g.fill(x0, base, x0 + Growth.RIPE * GROUP, base + 1, Ui.GRID);
        for (int a = 0; a < Growth.RIPE; a++) {
            int gx = x0 + a * GROUP;
            bar(g, gx + 2, base, height, seen[a] / top, Ui.AMBER);
            bar(g, gx + 9, base, height, expects[a] / top, Ui.TEAL);
            String label = Integer.toString(a);
            g.text(font, label, gx + (GROUP - font.width(label)) / 2 + 1, base + 2, Ui.DIM, false);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("residual.forecaster.age_title", a));
            lines.add(Component.translatable("residual.forecaster.seen_rate", per(seen[a])).withColor(Ui.AMBER));
            lines.add(Component.translatable("residual.forecaster.expected_rate", per(expects[a])).withColor(Ui.TEAL));
            hits.add(new Hit(gx, BARS_Y + 12, GROUP, BARS_H - 12, lines));
        }
    }

    private static void bar(GuiGraphicsExtractor g, int x, int base, int height, float fraction, int color) {
        if (!Float.isNaN(fraction)) {
            int h = Math.max(1, Math.round(height * Math.min(1, fraction)));
            g.fill(x, base - h, x + 5, base, color);
        }
    }

    private static String per(float rate) {
        return Float.isNaN(rate) ? "–" : String.format(Locale.ROOT, "%.3f", rate);
    }

    /** What it is doing, then the growth it has seen so far or how close the model came to it. */
    private void status(GuiGraphicsExtractor g) {
        ForecasterBlockEntity.State state = state();
        int crops = data(ForecasterBlockEntity.CROPS);
        Component line = switch (state) {
            case NO_CROPS -> Component.translatable("residual.forecaster.state.no_crops", ForecasterBlockEntity.RADIUS);
            case WATCHING -> Component.translatable("residual.forecaster.state.watching", Ui.count(crops));
            case NO_MODEL -> Component.translatable(ForecasterBlockEntity.PROBLEMS.get(Math.floorMod(data(ForecasterBlockEntity.PROBLEM),
                    ForecasterBlockEntity.PROBLEMS.size())));
            case PEEKS -> Component.translatable("residual.forecaster.state.peeks");
            case CHECKING -> Component.translatable("residual.forecaster.state.checking");
            case REFUSED -> Component.translatable("residual.forecaster.state.refused");
            case AHEAD -> Component.translatable("residual.forecaster.state.ahead", Ui.count(crops), data(ForecasterBlockEntity.AHEAD));
        };
        int color = switch (state) {
            case NO_CROPS -> Ui.LABEL_DIM;
            case NO_MODEL, PEEKS, REFUSED -> 0xFFB3261E;
            case AHEAD -> 0xFF2E7D32;
            default -> 0xFF1F7A72;
        };
        List<FormattedCharSequence> lines = font.split(line, imageWidth - 16);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.text(font, lines.get(i), 8, STATE_Y + i * 9, color, false);
        }
        int ratio = data(ForecasterBlockEntity.RATIO);
        if (ratio >= 0) {
            gauge(g, Math.log(ratio / 1000.0));
        } else {
            growth(g, data(ForecasterBlockEntity.GROWN));
        }
    }

    /** Growth seen so far, toward what a check needs. */
    private void growth(GuiGraphicsExtractor g, int grown) {
        int y = GAUGE_Y + 2;
        Component label = Component.translatable("residual.forecaster.grown");
        g.text(font, label, GAUGE_X + 4, y, Ui.DIM, false);
        String count = grown < Forecast.MIN_GROWTH ? grown + " / " + Forecast.MIN_GROWTH : Ui.count(grown);
        int x0 = GAUGE_X + 8 + font.width(label), x1 = GAUGE_X + GAUGE_W - 8 - font.width(count);
        Ui.bar(g, x0, y + 2, x1 - x0, 4, (float) grown / Forecast.MIN_GROWTH, grown < Forecast.MIN_GROWTH ? 0xFF1F9A8C : Ui.TEAL);
        g.text(font, count, x1 + 4, y, Ui.TEXT, false);
        hits.add(new Hit(GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H, List.of(Component.translatable("residual.forecaster.grown_hint",
                Forecast.MIN_GROWTH))));
    }

    /**
     * How much growth the model expects against how much there was, on a log scale from half to twice: green where
     * it runs the field four looks per look, amber where it runs two, red where it is refused.
     */
    private void gauge(GuiGraphicsExtractor g, double off) {
        int y = GAUGE_Y + 2;
        Component label = Component.translatable("residual.forecaster.fit");
        g.text(font, label, GAUGE_X + 4, y, Ui.DIM, false);
        String shown = String.format(Locale.ROOT, "%+.0f%%", (Math.exp(off) - 1) * 100);
        int x0 = GAUGE_X + 8 + font.width(label), x1 = GAUGE_X + GAUGE_W - 12 - font.width("+100%");
        int w = x1 - x0;
        int nearL = at(x0, w, -NEAR), closeL = at(x0, w, -CLOSE), closeR = at(x0, w, CLOSE), nearR = at(x0, w, NEAR);
        g.fill(x0, y + 2, nearL, y + 6, 0xFF5E2A2A);
        g.fill(nearL, y + 2, closeL, y + 6, 0xFF6B5320);
        g.fill(closeL, y + 2, closeR, y + 6, 0xFF2E5E33);
        g.fill(closeR, y + 2, nearR, y + 6, 0xFF6B5320);
        g.fill(nearR, y + 2, x1, y + 6, 0xFF5E2A2A);
        int one = at(x0, w, 0);
        g.fill(one, y, one + 1, y + 8, Ui.DIM);
        int mark = at(x0, w, Math.max(-SPAN, Math.min(SPAN, off)));
        g.fill(mark - 1, y - 2, mark + 1, y + 10, Ui.TEXT);
        double a = Math.abs(off);
        int color = a <= CLOSE ? Ui.GREEN : a <= NEAR ? Ui.AMBER : Ui.RED;
        g.text(font, shown, GAUGE_X + GAUGE_W - 4 - font.width(shown), y, color, false);
        String share = String.format(Locale.ROOT, "%.0f%%", Math.exp(off) * 100);
        hits.add(new Hit(GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H, List.of(Component.translatable("residual.forecaster.fit_title", share),
                Component.translatable("residual.forecaster.fit_hint").withColor(Ui.DIM))));
    }

    private static int at(int x0, int w, double off) {
        return x0 + (int) Math.round((off + SPAN) / (2 * SPAN) * w);
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
}
