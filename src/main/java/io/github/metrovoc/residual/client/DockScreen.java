package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.net.DockEdit;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.storage.DockBlockEntity;
import io.github.metrovoc.residual.storage.DockMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** A Drive Dock: the drives waiting on the left, the arrow they go through, the drives done on the right. */
public class DockScreen extends AbstractContainerScreen<DockMenu> {
    private static final int WINDOW_W = 176, ARROW_X = 80, ARROW_Y = 34;
    private static final List<String> MODES = List.of("residual.dock.empty", "residual.dock.fill");

    public DockScreen(DockMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, DockMenu.TOOLBAR_X + IconButton.SIZE, DockMenu.INVENTORY_Y + 82);
        inventoryLabelY = DockMenu.INVENTORY_Y - 10;
        inventoryLabelX = DockMenu.INVENTORY_X;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(IconButton.setting(leftPos + DockMenu.TOOLBAR_X, topPos + DockMenu.TOOLBAR_Y, List.of("dock_empty", "dock_fill"),
                Component.translatable("residual.dock.mode"), MODES.stream().map(k -> Component.translatable(k).getString()).toList(),
                menu.data.get(DockBlockEntity.FILLING), Component.translatable("residual.dock.mode_help"),
                i -> ClientPacketDistributor.sendToServer(new DockEdit(menu.pos, i == 1))));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, WINDOW_W, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        for (int i = 0; i < DockBlockEntity.BAYS; i++) {
            if (!menu.getSlot(i).hasItem()) {
                Ui.ghost(g, new ItemStack(ModItems.STORAGE_DRIVE.get()), leftPos + menu.getSlot(i).x, topPos + menu.getSlot(i).y);
            }
        }
        boolean moving = menu.moved() > 0 || menu.data.get(DockBlockEntity.MOVED_FLUID) > 0;
        Ui.arrow(g, leftPos + ARROW_X, topPos + ARROW_Y, moving ? 1 : 0);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (Ui.inside(mouseX - leftPos, mouseY - topPos, ARROW_X, ARROW_Y, 18, 16)) {
            g.setTooltipForNextFrame(font, font.split(Component.translatable("residual.dock.moved", Ui.count(menu.moved()),
                    Ui.count(menu.data.get(DockBlockEntity.MOVED_FLUID))), 200), mouseX, mouseY);
        }
    }
}
