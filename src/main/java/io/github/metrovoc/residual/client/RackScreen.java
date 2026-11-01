package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.storage.RackBlockEntity;
import io.github.metrovoc.residual.storage.RackMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A Drive Rack's eight bays. */
public class RackScreen extends AbstractContainerScreen<RackMenu> implements Parts.Drawn {
    public RackScreen(RackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, RackMenu.INVENTORY_Y + 82);
        inventoryLabelY = RackMenu.INVENTORY_Y - 10;
        inventoryLabelX = RackMenu.INVENTORY_X;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        for (int i = 0; i < RackBlockEntity.BAYS; i++) {
            Slot slot = menu.getSlot(i);
            if (!slot.hasItem()) {
                int sx = leftPos + slot.x, sy = topPos + slot.y;
                g.item(new ItemStack(ModItems.STORAGE_DRIVE.get()), sx, sy);
                g.fill(sx, sy, sx + 16, sy + 16, 0xB08B8B8B);
            }
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
        for (int i = 0; i < RackBlockEntity.BAYS; i++) {
            Slot slot = menu.getSlot(i);
            if (!slot.hasItem() && Ui.inside(mouseX - leftPos, mouseY - topPos, slot.x, slot.y, 16, 16)) {
                g.setTooltipForNextFrame(font.split(Component.translatable("residual.rack.bay_hint"), 200), mouseX, mouseY);
            }
        }
    }

    @Override
    public void parts(Parts out) {
        for (int i = 0; i < RackBlockEntity.BAYS; i++) {
            out.add("bay:" + i, this, menu.getSlot(i));
        }
        out.group("bays", this, 0, RackBlockEntity.BAYS);
    }
}
