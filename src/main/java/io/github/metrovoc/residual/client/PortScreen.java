package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.net.PortEdit;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.storage.PortBlock;
import io.github.metrovoc.residual.storage.PortBlockEntity;
import io.github.metrovoc.residual.storage.PortMenu;
import io.github.metrovoc.residual.storage.PortMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * A Port's mode, its filter, how many it stocks or where its window stands, and how much it moved; its filter model's
 * slot sits in a panel beside the window.
 */
public class PortScreen extends AbstractContainerScreen<PortMenu> {
    private static final int WINDOW_W = 176, MODES_Y = 18, VALUE_Y = 66;
    private final List<Button> modes = new ArrayList<>();
    private final List<Button> nudges = new ArrayList<>();

    public PortScreen(PortMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PortMenu.SIDE_X + PortMenu.SIDE_W, PortMenu.INVENTORY_Y + 82);
        inventoryLabelY = PortMenu.INVENTORY_Y - 10;
        inventoryLabelX = PortMenu.INVENTORY_X;
    }

    private PortMode mode() {
        BlockState state = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getBlockState(menu.pos);
        return state != null && state.getBlock() instanceof PortBlock ? state.getValue(PortBlock.MODE) : PortMode.IMPORT;
    }

    private void send(int field, int value) {
        ClientPacketDistributor.sendToServer(new PortEdit(menu.pos, field, value));
    }

    @Override
    protected void init() {
        super.init();
        modes.clear();
        nudges.clear();
        int w = 40;
        for (PortMode m : PortMode.values()) {
            modes.add(addRenderableWidget(Button.builder(Component.translatable("residual.port.mode." + m.id), b -> send(PortEdit.MODE, m.ordinal()))
                    .bounds(leftPos + 8 + m.ordinal() * (w + 1), topPos + MODES_Y, w, 14)
                    .tooltip(Tooltip.create(Component.translatable("residual.port.mode." + m.id + ".help"))).build()));
        }
        int x = leftPos + 76;
        for (int step : new int[]{-16, -1, 1, 16}) {
            nudges.add(addRenderableWidget(Button.builder(Component.literal((step > 0 ? "+" : "−") + Math.abs(step)), b -> nudge(step))
                    .bounds(x, topPos + VALUE_Y - 2, 22, 12).build()));
            x += 23;
        }
    }

    /** Changes the number the mode uses: how many to stock, or the window's priority. */
    private void nudge(int step) {
        if (mode() == PortMode.WINDOW) {
            send(PortEdit.PRIORITY, menu.data.get(PortBlockEntity.PRIORITY) + Integer.signum(step) * Math.min(Math.abs(step), 4));
        } else {
            send(PortEdit.AMOUNT, menu.data.get(PortBlockEntity.AMOUNT) + step * (Minecraft.getInstance().hasShiftDown() ? 64 : 1));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        // the filter model's panel, tucked under the window's edge
        Ui.window(g, leftPos + PortMenu.SIDE_X, topPos + PortMenu.SIDE_Y, PortMenu.SIDE_W, PortMenu.SIDE_H);
        Ui.window(g, leftPos, topPos, WINDOW_W, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        if (!menu.getSlot(PortMenu.MODEL).hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.CHECKPOINT.get()), leftPos + PortMenu.MODEL_X, topPos + PortMenu.MODEL_Y);
        }
        PortMode mode = mode();
        for (int i = 0; i < modes.size(); i++) {
            modes.get(i).active = i != mode.ordinal();
        }
        nudges.forEach(b -> b.visible = mode == PortMode.STOCK || mode == PortMode.WINDOW);
        Button chosen = modes.get(mode.ordinal());
        g.fill(chosen.getX() + 2, chosen.getY() + chosen.getHeight() + 1, chosen.getX() + chosen.getWidth() - 2,
                chosen.getY() + chosen.getHeight() + 2, Ui.TEAL);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
        PortMode mode = mode();
        boolean usesNumber = mode == PortMode.STOCK || mode == PortMode.WINDOW;
        if (usesNumber) {
            Component label = mode == PortMode.STOCK
                    ? Component.translatable("residual.port.amount", Ui.count(menu.data.get(PortBlockEntity.AMOUNT)))
                    : Component.translatable("residual.port.priority", menu.data.get(PortBlockEntity.PRIORITY));
            g.text(font, label, 8, VALUE_Y, Ui.LABEL, false);
        }
        Component status = Component.translatable("residual.port.moved", Ui.count(menu.data.get(PortBlockEntity.MOVED)));
        g.text(font, status, 8, VALUE_Y + 16, Ui.LABEL_DIM, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        for (int i = 0; i < PortBlockEntity.FILTERS; i++) {
            Slot slot = menu.getSlot(i);
            if (Ui.inside(mouseX - leftPos, mouseY - topPos, slot.x, slot.y, 16, 16) && !slot.hasItem()) {
                g.setTooltipForNextFrame(font.split(Component.translatable("residual.port.filter_hint"), 200), mouseX, mouseY);
            }
        }
        if (!menu.getSlot(PortMenu.MODEL).hasItem() && menu.getCarried().isEmpty()
                && Ui.inside(mouseX - leftPos, mouseY - topPos, PortMenu.MODEL_X, PortMenu.MODEL_Y, 16, 16)) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(Component.translatable("residual.port.model").getVisualOrderText());
            lines.addAll(font.split(Component.translatable("residual.port.model_hint").withStyle(ChatFormatting.GRAY), 200));
            g.setTooltipForNextFrame(lines, mouseX, mouseY);
        }
    }
}
