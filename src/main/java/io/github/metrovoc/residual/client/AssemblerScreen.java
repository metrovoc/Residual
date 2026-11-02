package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.net.AssemblerEdit;
import io.github.metrovoc.residual.storage.AssemblerBlockEntity;
import io.github.metrovoc.residual.storage.AssemblerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** An Assembler's recipe, what it makes, how many of that to keep, and what it is doing. */
public class AssemblerScreen extends AbstractContainerScreen<AssemblerMenu> implements Parts.Drawn {
    private static final int WINDOW_W = 176, TOOLBAR_X = 178, TOOLBAR_Y = 20, BUTTON_W = 22, BUTTON_STEP = 22;

    public AssemblerScreen(AssemblerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TOOLBAR_X + BUTTON_W, AssemblerMenu.INVENTORY_Y + 82);
        inventoryLabelY = AssemblerMenu.INVENTORY_Y - 10;
        inventoryLabelX = AssemblerMenu.INVENTORY_X;
    }

    @Override
    protected void init() {
        super.init();
        int y = topPos + TOOLBAR_Y;
        for (int step : new int[]{-16, -1, 1, 16}) {
            addRenderableWidget(Button.builder(Component.literal((step > 0 ? "+" : "−") + Math.abs(step)), b -> {
                int by = step * (Minecraft.getInstance().hasShiftDown() ? 64 : 1);
                ClientPacketDistributor.sendToServer(new AssemblerEdit(menu.pos, menu.data.get(AssemblerBlockEntity.KEEP) + by));
            }).bounds(leftPos + TOOLBAR_X, y, BUTTON_W, 18).build());
            y += BUTTON_STEP;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, WINDOW_W, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        // an arrow from the grid to what it makes, halfway between them
        Ui.arrow(g, leftPos + arrowX(), topPos + AssemblerMenu.RESULT_Y,
                menu.data.get(AssemblerBlockEntity.WAITING) == AssemblerBlockEntity.CRAFTING ? 1 : 0);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
    }

    private Component status() {
        int waiting = menu.data.get(AssemblerBlockEntity.WAITING);
        return switch (waiting) {
            case AssemblerBlockEntity.CRAFTING -> Component.translatable("residual.assembler.crafting", Ui.count(menu.data.get(AssemblerBlockEntity.MADE)));
            case AssemblerBlockEntity.ENOUGH -> Component.translatable("residual.assembler.enough", Ui.count(menu.data.get(AssemblerBlockEntity.HELD)));
            case AssemblerBlockEntity.MISSING -> Component.translatable("residual.assembler.missing");
            case AssemblerBlockEntity.PAUSED -> Component.translatable("residual.assembler.paused");
            default -> Component.translatable("residual.assembler.no_recipe");
        };
    }

    private static int arrowX() {
        int gridEnd = AssemblerMenu.GRID_X + 3 * 18 - 1;
        return (gridEnd + AssemblerMenu.RESULT_X - 1) / 2 - 12;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int x = mouseX - leftPos, y = mouseY - topPos;
        if (Ui.inside(x, y, TOOLBAR_X, TOOLBAR_Y, BUTTON_W, 3 * BUTTON_STEP + 18)) {
            Component keep = Component.translatable("residual.assembler.keep", Ui.count(menu.data.get(AssemblerBlockEntity.KEEP)))
                    .append("\n").append(Component.translatable("residual.assembler.keep_help"));
            g.setTooltipForNextFrame(font.split(keep, 200), mouseX, mouseY);
        } else if (Ui.inside(x, y, arrowX(), AssemblerMenu.RESULT_Y, 24, 16)) {
            g.setTooltipForNextFrame(font.split(status(), 200), mouseX, mouseY);
        }
        for (int i = 0; i < AssemblerBlockEntity.GRID; i++) {
            Slot slot = menu.getSlot(i);
            if (!slot.hasItem() && menu.getCarried().isEmpty() && Ui.inside(mouseX - leftPos, mouseY - topPos, slot.x, slot.y, 16, 16)) {
                g.setTooltipForNextFrame(font.split(Component.translatable("residual.assembler.grid_hint"), 200), mouseX, mouseY);
            }
        }
    }

    @Override
    public void parts(Parts out) {
        for (int i = 0; i < AssemblerBlockEntity.GRID; i++) {
            out.add("grid:" + i, this, menu.getSlot(i));
        }
        out.group("grid", this, 0, AssemblerBlockEntity.GRID);
        out.add("result", this, menu.getSlot(AssemblerBlockEntity.GRID));
        out.add("keep", leftPos + TOOLBAR_X, topPos + TOOLBAR_Y, BUTTON_W, 3 * BUTTON_STEP + 18);
        int y = topPos + TOOLBAR_Y;
        for (int step : new int[]{-16, -1, 1, 16}) {
            out.add("keep:" + step, leftPos + TOOLBAR_X, y, BUTTON_W, 18);
            y += BUTTON_STEP;
        }
        out.add("status", leftPos + arrowX(), topPos + AssemblerMenu.RESULT_Y, 24, 16);
    }
}
