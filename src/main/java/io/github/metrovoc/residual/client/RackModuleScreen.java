package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.compute.RackModuleBlockEntity;
import io.github.metrovoc.residual.compute.RackModuleMenu;
import io.github.metrovoc.residual.registry.ModBlocks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A Rack Module's eight bays. */
public class RackModuleScreen extends AbstractContainerScreen<RackModuleMenu> implements Parts.Drawn {
    public RackModuleScreen(RackModuleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, RackModuleMenu.INVENTORY_Y + 82);
        inventoryLabelY = RackModuleMenu.INVENTORY_Y - 10;
        inventoryLabelX = RackModuleMenu.INVENTORY_X;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        for (int i = 0; i < RackModuleBlockEntity.BAYS; i++) {
            Slot slot = menu.getSlot(i);
            if (!slot.hasItem()) {
                Ui.ghost(g, new ItemStack(ModBlocks.ACCELERATOR.asItem()), leftPos + slot.x, topPos + slot.y);
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
        for (int i = 0; i < RackModuleBlockEntity.BAYS; i++) {
            Slot slot = menu.getSlot(i);
            if (!slot.hasItem() && Ui.inside(mouseX - leftPos, mouseY - topPos, slot.x, slot.y, 16, 16)) {
                g.setTooltipForNextFrame(font.split(Component.translatable("residual.rack_module.bay_hint"), 200), mouseX, mouseY);
            }
        }
    }

    @Override
    public void parts(Parts out) {
        for (int i = 0; i < RackModuleBlockEntity.BAYS; i++) {
            out.add("bay:" + i, this, menu.getSlot(i));
        }
    }
}
