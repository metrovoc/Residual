package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.energy.PowerMenu;
import io.github.metrovoc.residual.learn.Control;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A generator's or a powered machine's screen. The window holds the work alone, fuel under its flame or ore to ingots,
 * and the stored power as a bar at its right edge; a Smelter's controller slot sits in a panel beside it. Numbers show
 * in tooltips on what they describe.
 */
public class PowerScreen extends AbstractContainerScreen<PowerMenu> implements Parts.Drawn {
    private static final int WINDOW_W = 176, WINDOW_H = PowerMenu.INVENTORY_Y + 82;
    private static final int BAR_X = 156, BAR_Y = 17, BAR_W = 10, BAR_H = 52;
    /** Between ore and ingots: an arrow, or with a controller the melt, a pixel a look and two a band. */
    private static final int WORK_X = 82, WORK_Y = PowerMenu.SLOT_Y, WORK_W = 24, WORK_H = 16;
    /** A reactor's cycle, three pixels a look and five a reading of its gauge. */
    private static final int TRACE_LOOK = 3, TRACE_BAND = 5, TRACE_X = 56, TRACE_Y = 22, TRACE_W = 72, TRACE_H = 40;

    public PowerScreen(PowerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.kind != PowerMenu.Kind.GENERATOR ? PowerMenu.SIDE_X + PowerMenu.SIDE_W : WINDOW_W, WINDOW_H);
        inventoryLabelY = PowerMenu.INVENTORY_Y - 10;
        inventoryLabelX = PowerMenu.INVENTORY_X;
    }

    private boolean controlled() {
        return menu.kind == PowerMenu.Kind.SMELTER && menu.getSlot(PowerMenu.CONTROLLER_SLOT).hasItem();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        if (menu.kind != PowerMenu.Kind.GENERATOR) {
            // the controller's panel, tucked under the window's edge
            Ui.window(g, leftPos + PowerMenu.SIDE_X, topPos + PowerMenu.SIDE_Y, PowerMenu.SIDE_W, PowerMenu.SIDE_H);
        }
        Ui.window(g, leftPos, topPos, WINDOW_W, WINDOW_H);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        // stored power, filling from the bottom
        int x = leftPos + BAR_X, y = topPos + BAR_Y;
        Ui.screen(g, x, y, BAR_W, BAR_H);
        long cap = menu.capacity();
        int filled = cap > 0 ? (int) Math.round((double) BAR_H * menu.energy() / cap) : 0;
        g.fill(x, y + BAR_H - filled, x + BAR_W, y + BAR_H, Ui.TEAL);
        int work = menu.data.get(PowerMenu.WORK), total = menu.data.get(PowerMenu.WORK_TOTAL);
        if (menu.kind == PowerMenu.Kind.REACTOR) {
            reactor(g, work, total);
            return;
        }
        if (menu.kind == PowerMenu.Kind.GENERATOR) {
            // the flame above the fuel, burning down
            Ui.flame(g, leftPos + PowerMenu.FUEL_X + 1, topPos + PowerMenu.SLOT_Y - 17, total > 0 ? (float) work / total : 0);
            return;
        }
        if (controlled()) {
            melt(g, leftPos + WORK_X, topPos + WORK_Y);
        } else {
            Ui.arrow(g, leftPos + WORK_X, topPos + WORK_Y, total > 0 ? (float) work / total : 0);
        }
        Slot controller = menu.getSlot(PowerMenu.CONTROLLER_SLOT);
        if (!controller.hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.CHECKPOINT.get()), leftPos + controller.x, topPos + controller.y);
        }
    }

    /** A reactor: its fuel burning down under a flame, then its cycle as a Smelter's melt, on a larger gauge. */
    private void reactor(GuiGraphicsExtractor g, int work, int total) {
        Ui.flame(g, leftPos + PowerMenu.REACTOR_FUEL_X + 1, topPos + PowerMenu.SLOT_Y - 17, total > 0 ? (float) work / total : 0);
        int x = leftPos + TRACE_X, y = topPos + TRACE_Y;
        Ui.screen(g, x, y, TRACE_W, TRACE_H);
        g.fill(x, y + TRACE_H - 5 * TRACE_BAND, x + TRACE_W, y + TRACE_H - 3 * TRACE_BAND, 0xFF1E3D33);
        int looks = menu.data.get(PowerMenu.MELT_LOOKS);
        for (int k = 0; k < looks; k++) {
            int band = menu.data.get(PowerMenu.MELT_BANDS + k / 5) >> (3 * (k % 5)) & 7;
            int my = y + TRACE_H - (band + 1) * TRACE_BAND;
            g.fill(x + k * TRACE_LOOK, my, x + (k + 1) * TRACE_LOOK, my + TRACE_BAND, band == 3 || band == 4 ? Ui.GREEN : band == 0 || band == 7 ? Ui.RED : Ui.AMBER);
        }
        Slot controller = menu.getSlot(PowerMenu.REACTOR_CONTROLLER_SLOT);
        if (!controller.hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.CHECKPOINT.get()), leftPos + controller.x, topPos + controller.y);
        }
    }

    /** The melt its controller holds: a column a look, the band shaded across, each look's temperature as a mark. */
    private void melt(GuiGraphicsExtractor g, int x, int y) {
        Ui.screen(g, x, y, WORK_W, WORK_H);
        // the band, d and e
        g.fill(x, y + WORK_H - 5 * 2, x + WORK_W, y + WORK_H - 3 * 2, 0xFF1E3D33);
        int looks = menu.data.get(PowerMenu.MELT_LOOKS);
        for (int k = 0; k < looks; k++) {
            int band = menu.data.get(PowerMenu.MELT_BANDS + k / 5) >> (3 * (k % 5)) & 7;
            int my = y + WORK_H - (band + 1) * 2;
            g.fill(x + k, my, x + k + 1, my + 2, band == 3 || band == 4 ? Ui.GREEN : Ui.AMBER);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (menu.kind == PowerMenu.Kind.REACTOR && Ui.inside(mx, my, TRACE_X, TRACE_Y, TRACE_W, TRACE_H)) {
            int status = menu.data.get(PowerMenu.REACTOR_STATUS), looks = menu.data.get(PowerMenu.MELT_LOOKS), held = menu.data.get(PowerMenu.MELT_HELD) - 1;
            lines.addAll(font.split(Component.translatable("residual.reactor.status." + status), 220));
            lines.addAll(font.split(Component.translatable("residual.reactor.cycle", looks, Control.LOOKS).withStyle(ChatFormatting.GRAY), 220));
            if (held >= 0) {
                lines.addAll(font.split(Component.translatable("residual.reactor.held", held, menu.data.get(PowerMenu.REACTOR_SURE)).withStyle(ChatFormatting.GRAY), 220));
            }
        } else if (menu.kind == PowerMenu.Kind.REACTOR && !menu.getSlot(PowerMenu.REACTOR_CONTROLLER_SLOT).hasItem() && menu.getCarried().isEmpty()
                && Ui.inside(mx, my, PowerMenu.CONTROLLER_X, PowerMenu.CONTROLLER_Y, 16, 16)) {
            lines.add(Component.translatable("residual.power.controller").getVisualOrderText());
            lines.addAll(font.split(Component.translatable("residual.reactor.controller_hint").withStyle(ChatFormatting.GRAY), 180));
        } else if (Ui.inside(mx, my, BAR_X, BAR_Y, BAR_W, BAR_H)) {
            lines.add(Component.translatable("residual.power.stored", Ui.count(menu.energy()), Ui.count(menu.capacity())).getVisualOrderText());
            int rate = menu.data.get(PowerMenu.RATE) * (menu.kind == PowerMenu.Kind.REACTOR ? PowerMenu.REACTOR_RATE_UNIT : 1);
            String key = menu.kind == PowerMenu.Kind.SMELTER ? "residual.power.draws" : "residual.power.makes";
            lines.add((rate > 0 ? Component.translatable(key, rate) : Component.translatable("residual.power.idle"))
                    .withStyle(ChatFormatting.GRAY).getVisualOrderText());
        } else if (controlled() && Ui.inside(mx, my, WORK_X, WORK_Y, WORK_W, WORK_H)) {
            int looks = menu.data.get(PowerMenu.MELT_LOOKS), held = menu.data.get(PowerMenu.MELT_HELD) - 1;
            lines.add(Component.translatable("residual.power.melt", looks, Control.LOOKS).getVisualOrderText());
            lines.add((held < 0 ? Component.translatable("residual.power.no_melt")
                    : Component.translatable(held >= 100 * Control.HELD ? "residual.power.held_three" : "residual.power.held_two", held))
                    .withStyle(ChatFormatting.GRAY).getVisualOrderText());
        } else if (menu.kind == PowerMenu.Kind.SMELTER && !menu.getSlot(PowerMenu.CONTROLLER_SLOT).hasItem() && menu.getCarried().isEmpty()
                && Ui.inside(mx, my, PowerMenu.CONTROLLER_X, PowerMenu.CONTROLLER_Y, 16, 16)) {
            lines.add(Component.translatable("residual.power.controller").getVisualOrderText());
            lines.addAll(font.split(Component.translatable("residual.power.controller_hint").withStyle(ChatFormatting.GRAY), 180));
        }
        if (!lines.isEmpty()) {
            g.setTooltipForNextFrame(lines, mouseX, mouseY);
        }
    }

    @Override
    public void parts(Parts out) {
        out.add("power", leftPos + BAR_X, topPos + BAR_Y, BAR_W, BAR_H);
        if (menu.kind == PowerMenu.Kind.GENERATOR) {
            out.add("fuel", this, menu.getSlot(0));
            out.add("flame", leftPos + PowerMenu.FUEL_X + 1, topPos + PowerMenu.SLOT_Y - 17, 14, 14);
        } else if (menu.kind == PowerMenu.Kind.REACTOR) {
            out.add("fuel", this, menu.getSlot(0));
            out.add("flame", leftPos + PowerMenu.REACTOR_FUEL_X + 1, topPos + PowerMenu.SLOT_Y - 17, 14, 14);
            out.add("controller", this, menu.getSlot(PowerMenu.REACTOR_CONTROLLER_SLOT));
            out.add("cycle", leftPos + TRACE_X, topPos + TRACE_Y, TRACE_W, TRACE_H);
        } else {
            out.add("ore", this, menu.getSlot(0));
            out.add("ingots", this, menu.getSlot(1));
            out.add("controller", this, menu.getSlot(PowerMenu.CONTROLLER_SLOT));
            out.add("work", leftPos + WORK_X, topPos + WORK_Y, WORK_W, WORK_H);
        }
    }
}
