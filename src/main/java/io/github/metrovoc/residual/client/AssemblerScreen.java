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
    private static final int KEEP_Y = 82;

    public AssemblerScreen(AssemblerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, AssemblerMenu.INVENTORY_Y + 82);
        inventoryLabelY = AssemblerMenu.INVENTORY_Y - 10;
        inventoryLabelX = AssemblerMenu.INVENTORY_X;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 76;
        for (int step : new int[]{-16, -1, 1, 16}) {
            addRenderableWidget(Button.builder(Component.literal((step > 0 ? "+" : "−") + Math.abs(step)), b -> {
                int by = step * (Minecraft.getInstance().hasShiftDown() ? 64 : 1);
                ClientPacketDistributor.sendToServer(new AssemblerEdit(menu.pos, menu.data.get(AssemblerBlockEntity.KEEP) + by));
            }).bounds(x, topPos + KEEP_Y - 2, 22, 12).build());
            x += 23;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        // an arrow from the grid to what it makes, halfway between them
        int gridEnd = AssemblerMenu.GRID_X + 3 * 18 - 1;
        Ui.arrow(g, leftPos + (gridEnd + AssemblerMenu.RESULT_X - 1) / 2 - 12, topPos + AssemblerMenu.RESULT_Y, 0);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
        g.text(font, Component.translatable("residual.assembler.keep", Ui.count(menu.data.get(AssemblerBlockEntity.KEEP))), 8, KEEP_Y, Ui.LABEL, false);
        int waiting = menu.data.get(AssemblerBlockEntity.WAITING);
        Component status = switch (waiting) {
            case AssemblerBlockEntity.CRAFTING -> Component.translatable("residual.assembler.crafting", Ui.count(menu.data.get(AssemblerBlockEntity.MADE)));
            case AssemblerBlockEntity.ENOUGH -> Component.translatable("residual.assembler.enough", Ui.count(menu.data.get(AssemblerBlockEntity.HELD)));
            case AssemblerBlockEntity.MISSING -> Component.translatable("residual.assembler.missing");
            case AssemblerBlockEntity.PAUSED -> Component.translatable("residual.assembler.paused");
            default -> Component.translatable("residual.assembler.no_recipe");
        };
        g.text(font, status, 8, KEEP_Y + 16, waiting == AssemblerBlockEntity.CRAFTING ? 0xFF2E7D32 : Ui.LABEL_DIM, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
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
        out.add("keep", leftPos + 8, topPos + KEEP_Y - 2, imageWidth - 16, 12);
        out.add("status", leftPos + 8, topPos + KEEP_Y + 14, imageWidth - 16, 11);
    }
}
