package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.block.WatchPostBlockEntity;
import io.github.metrovoc.residual.menu.WatchPostMenu;
import io.github.metrovoc.residual.net.WatchSync;
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
 * The Watch Post's screen: one display, with each thing it watches, its line of looks drawn as bars, newest at the right, red
 * while something is wrong with it. Its model slot sits in a panel beside the window.
 */
public class WatchPostScreen extends AbstractContainerScreen<WatchPostMenu> implements Parts.Drawn {
    private static final int WINDOW_W = 176, SHOW_X = 8, SHOW_Y = 18, SHOW_W = 160, SHOW_H = 68, ROW_H = 13, ROWS = 5;
    private static final int BARS_X = 18, BAR_W = 2, BAR_H = 10;
    private int scroll;

    public WatchPostScreen(WatchPostMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WatchPostMenu.SIDE_X + WatchPostMenu.SIDE_W, WatchPostMenu.INVENTORY_Y + 82);
        inventoryLabelY = WatchPostMenu.INVENTORY_Y - 10;
        inventoryLabelX = WatchPostMenu.INVENTORY_X;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos + WatchPostMenu.SIDE_X, topPos + WatchPostMenu.SIDE_Y, WatchPostMenu.SIDE_W, WatchPostMenu.SIDE_H);
        Ui.window(g, leftPos, topPos, WINDOW_W, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        if (!menu.getSlot(WatchPostBlockEntity.MODEL).hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.CHECKPOINT.get()), leftPos + WatchPostMenu.MODEL_X, topPos + WatchPostMenu.MODEL_Y);
        }
        Ui.screen(g, leftPos + SHOW_X, topPos + SHOW_Y, SHOW_W, SHOW_H);
        scroll = Math.clamp(scroll, 0, Math.max(0, menu.rows.size() - ROWS));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
        if (menu.rows.isEmpty()) {
            int y = SHOW_Y + 4;
            for (FormattedCharSequence line : font.split(Component.translatable("residual.watch.nothing"), SHOW_W - 8)) {
                g.text(font, line, SHOW_X + 4, y, Ui.DIM, false);
                y += 10;
            }
            return;
        }
        int width = SHOW_W - BARS_X - 4, most = width / (BAR_W + 1);
        for (int i = 0; i < ROWS && scroll + i < menu.rows.size(); i++) {
            WatchSync.Row row = menu.rows.get(scroll + i);
            int y = SHOW_Y + 2 + i * ROW_H;
            g.pose().pushMatrix();
            g.pose().translate(SHOW_X + 3, y + 1);
            g.pose().scale(0.625f, 0.625f);
            g.item(row.icon(), 0, 0);
            g.pose().popMatrix();
            int color = row.verdict() == WatchPostBlockEntity.FINE ? Ui.TEAL : Ui.RED;
            String line = row.line();
            int shown = Math.min(most, line.length());
            for (int b = 0; b < shown; b++) {
                int d = Math.clamp(line.charAt(line.length() - shown + b) - '0', 0, 4);
                int x = SHOW_X + BARS_X + (most - shown + b) * (BAR_W + 1), h = d == 0 ? 1 : d * BAR_H / 4;
                g.fill(x, y + BAR_H + 1 - h, x + BAR_W, y + BAR_H + 1, d == 0 ? Ui.GRID : color);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (!menu.getSlot(WatchPostBlockEntity.MODEL).hasItem() && menu.getCarried().isEmpty()
                && Ui.inside(mx, my, WatchPostMenu.MODEL_X, WatchPostMenu.MODEL_Y, 16, 16)) {
            lines.add(Component.translatable("residual.agent.model_slot").getVisualOrderText());
            lines.addAll(font.split(Component.translatable("residual.watch.model_hint").withStyle(ChatFormatting.GRAY), 200));
        } else if (Ui.inside(mx, my, SHOW_X, SHOW_Y, SHOW_W, SHOW_H - 2)) {
            int row = scroll + (my - SHOW_Y - 2) / ROW_H;
            if (row >= 0 && row < menu.rows.size()) {
                WatchSync.Row r = menu.rows.get(row);
                lines.add(r.icon().getHoverName().getVisualOrderText());
                lines.add(Component.translatable(switch (r.verdict()) {
                    case WatchPostBlockEntity.WRONG -> "residual.watch.wrong";
                    case WatchPostBlockEntity.BY_RULE -> "residual.watch.stalled";
                    default -> "residual.watch.fine";
                }).withStyle(ChatFormatting.GRAY).getVisualOrderText());
            }
        }
        if (!lines.isEmpty()) {
            g.setTooltipForNextFrame(lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (Ui.inside(x - leftPos, y - topPos, SHOW_X, SHOW_Y, SHOW_W, SHOW_H)) {
            scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, menu.rows.size() - ROWS));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    /** {@code row:0} on, from the first row shown. */
    @Override
    public void parts(Parts out) {
        out.add("model", this, menu.getSlot(WatchPostBlockEntity.MODEL));
        out.add("rows", leftPos + SHOW_X, topPos + SHOW_Y, SHOW_W, SHOW_H);
        for (int i = 0; i < ROWS && scroll + i < menu.rows.size(); i++) {
            out.add("row:" + i, leftPos + SHOW_X, topPos + SHOW_Y + 2 + i * ROW_H, SHOW_W, ROW_H);
        }
    }
}
