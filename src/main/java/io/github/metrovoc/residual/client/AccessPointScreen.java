package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.storage.AccessPointBlockEntity;
import io.github.metrovoc.residual.storage.AccessPointMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** An Access Point: its Relay Card's slot, a mark for how far it reaches, and that number in the mark's tooltip. */
public class AccessPointScreen extends AbstractContainerScreen<AccessPointMenu> {
    private static final int LIGHT_X = 106, LIGHT_Y = 31, LIGHT_S = 10;

    public AccessPointScreen(AccessPointMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, AccessPointMenu.INVENTORY_Y + 82);
        inventoryLabelY = AccessPointMenu.INVENTORY_Y - 10;
        inventoryLabelX = AccessPointMenu.INVENTORY_X;
    }

    private boolean relayed() {
        return menu.data.get(AccessPointBlockEntity.RELAYED) != 0;
    }

    private boolean driven() {
        return menu.data.get(AccessPointBlockEntity.DRIVEN) != 0;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        Slot card = menu.getSlot(0);
        if (!card.hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.RELAY_CARD.get()), leftPos + card.x, topPos + card.y);
        }
        int color = relayed() ? Ui.AMBER : driven() ? Ui.TEAL : Ui.DIM;
        Ui.screen(g, leftPos + LIGHT_X, topPos + LIGHT_Y, LIGHT_S, LIGHT_S);
        g.fill(leftPos + LIGHT_X + 3, topPos + LIGHT_Y + 3, leftPos + LIGHT_X + LIGHT_S - 3, topPos + LIGHT_Y + LIGHT_S - 3, color);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        Slot card = menu.getSlot(0);
        if (!card.hasItem() && menu.getCarried().isEmpty() && Ui.inside(mouseX - leftPos, mouseY - topPos, card.x, card.y, 16, 16)) {
            g.setTooltipForNextFrame(font.split(Component.translatable("residual.access_point.card_hint"), 200), mouseX, mouseY);
        }
        if (Ui.inside(mouseX - leftPos, mouseY - topPos, LIGHT_X, LIGHT_Y, LIGHT_S, LIGHT_S)) {
            List<FormattedCharSequence> lines = new ArrayList<>(font.split(Component.translatable("residual.access_point.reach",
                    Ui.count(driven() ? AccessPointBlockEntity.DRIVEN_RANGE : AccessPointBlockEntity.RANGE)), 200));
            if (relayed()) {
                lines.addAll(font.split(Component.translatable("residual.access_point.relayed"), 200));
            }
            g.setTooltipForNextFrame(lines, mouseX, mouseY);
        }
    }
}
