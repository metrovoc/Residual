package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.energy.PowerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * A generator's or a powered machine's screen: its stored power as a bar on the left, its slots, how far its fuel or
 * its item has gone, and the power it makes or draws.
 */
public class PowerScreen extends AbstractContainerScreen<PowerMenu> {
    private static final int BAR_X = 10, BAR_Y = 18, BAR_W = 10, BAR_H = 52;

    public PowerScreen(PowerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, PowerMenu.INVENTORY_Y + 82);
        inventoryLabelY = PowerMenu.INVENTORY_Y - 10;
        inventoryLabelX = PowerMenu.INVENTORY_X;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        // stored power, filling from the bottom
        int x = leftPos + BAR_X, y = topPos + BAR_Y;
        Ui.screen(g, x - 1, y - 1, BAR_W + 2, BAR_H + 2);
        long cap = menu.capacity();
        int filled = cap > 0 ? (int) Math.round((double) BAR_H * menu.energy() / cap) : 0;
        g.fill(x, y + BAR_H - filled, x + BAR_W, y + BAR_H, Ui.TEAL);
        int work = menu.data.get(PowerMenu.WORK), total = menu.data.get(PowerMenu.WORK_TOTAL);
        if (menu.kind == PowerMenu.Kind.GENERATOR) {
            // the flame below the fuel, burning down
            int fx = leftPos + PowerMenu.FUEL_X + 1, fy = topPos + PowerMenu.SLOT_Y - 16;
            int h = total > 0 ? (int) Math.ceil(13.0 * work / total) : 0;
            g.fill(fx + 3, fy + 13 - h, fx + 13, fy + 13, h > 0 ? Ui.AMBER : 0x00000000);
        } else {
            // the arrow from ore to ingots, filling as it smelts
            int ax = leftPos + PowerMenu.IN_X + 24, ay = topPos + PowerMenu.SLOT_Y + 6;
            int w = PowerMenu.OUT_X - PowerMenu.IN_X - 32;
            Ui.bar(g, ax, ay, w, 4, total > 0 ? (float) work / total : 0, Ui.AMBER);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
        int rate = menu.data.get(PowerMenu.RATE);
        String key = menu.kind == PowerMenu.Kind.GENERATOR ? "residual.power.makes" : "residual.power.draws";
        Component line = rate > 0 ? Component.translatable(key, rate) : Component.translatable("residual.power.idle");
        g.text(font, line, BAR_X + BAR_W + 8, 60, rate > 0 ? Ui.LABEL : Ui.LABEL_DIM, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (Ui.inside(mouseX - leftPos, mouseY - topPos, BAR_X - 1, BAR_Y - 1, BAR_W + 2, BAR_H + 2)) {
            g.setTooltipForNextFrame(font.split(Component.translatable("residual.power.stored", Ui.count(menu.energy()),
                    Ui.count(menu.capacity())), 200), mouseX, mouseY);
        }
    }
}
