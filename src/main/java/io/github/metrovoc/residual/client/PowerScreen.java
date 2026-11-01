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
public class PowerScreen extends AbstractContainerScreen<PowerMenu> {
    private static final int WINDOW_W = 176, WINDOW_H = PowerMenu.INVENTORY_Y + 82;
    private static final int BAR_X = 156, BAR_Y = 17, BAR_W = 10, BAR_H = 52;
    /** Between ore and ingots: an arrow, or with a controller the melt, a pixel a look and two a band. */
    private static final int WORK_X = 82, WORK_Y = PowerMenu.SLOT_Y, WORK_W = 24, WORK_H = 16;

    public PowerScreen(PowerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.kind == PowerMenu.Kind.SMELTER ? PowerMenu.SIDE_X + PowerMenu.SIDE_W : WINDOW_W, WINDOW_H);
        inventoryLabelY = PowerMenu.INVENTORY_Y - 10;
        inventoryLabelX = PowerMenu.INVENTORY_X;
    }

    private boolean controlled() {
        return menu.kind == PowerMenu.Kind.SMELTER && menu.getSlot(PowerMenu.CONTROLLER_SLOT).hasItem();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        if (menu.kind == PowerMenu.Kind.SMELTER) {
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
        if (Ui.inside(mx, my, BAR_X, BAR_Y, BAR_W, BAR_H)) {
            lines.add(Component.translatable("residual.power.stored", Ui.count(menu.energy()), Ui.count(menu.capacity())).getVisualOrderText());
            int rate = menu.data.get(PowerMenu.RATE);
            String key = menu.kind == PowerMenu.Kind.GENERATOR ? "residual.power.makes" : "residual.power.draws";
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
}
