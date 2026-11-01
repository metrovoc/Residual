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
import net.minecraft.world.item.Items;
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
    private IconButton craft;

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
        addRenderableWidget(IconButton.action(leftPos + PortMenu.CRAFT_X, topPos + PortMenu.CRAFT_Y + IconButton.SIZE + 2, "program",
                Component.translatable("residual.port.programs"), Component.translatable("residual.port.programs.help"), () -> send(PortEdit.PROGRAMS, 0)));
        craft = addRenderableWidget(new IconButton(leftPos + PortMenu.CRAFT_X, topPos + PortMenu.CRAFT_Y, IconButton.SIZE, IconButton.SIZE,
                new ItemStack(Items.CRAFTING_TABLE), Component.translatable("residual.port.craft"),
                () -> send(PortEdit.CRAFT, menu.data.get(PortBlockEntity.CRAFT) == 0 ? 1 : 0)));
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
        if (!menu.getSlot(PortMenu.TARGET).hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.CHECKPOINT.get()), leftPos + PortMenu.TARGET_X, topPos + PortMenu.TARGET_Y);
        }
        if (!menu.getSlot(PortMenu.LOG).hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.FIELD_LOG.get()), leftPos + PortMenu.LOG_X, topPos + PortMenu.LOG_Y);
        }
        PortMode mode = mode();
        for (int i = 0; i < modes.size(); i++) {
            modes.get(i).active = i != mode.ordinal();
        }
        nudges.forEach(b -> b.visible = mode == PortMode.STOCK || mode == PortMode.WINDOW);
        boolean asks = menu.data.get(PortBlockEntity.CRAFT) != 0;
        craft.visible = mode == PortMode.STOCK || mode == PortMode.EXPORT;
        craft.setTooltip(Tooltip.create(Component.translatable(asks ? "residual.port.craft.on" : "residual.port.craft.off")));
        if (craft.visible && asks) {
            g.fill(craft.getX() + 2, craft.getY() + craft.getHeight(), craft.getX() + craft.getWidth() - 2,
                    craft.getY() + craft.getHeight() + 1, Ui.TEAL);
        }
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
        g.text(font, status(), 8, VALUE_Y + 16, Ui.LABEL_DIM, false);
    }

    /** How much it moved in the last second, or that a full link holds it back. */
    private Component status() {
        if (menu.data.get(PortBlockEntity.LINK_FULL) != 0) {
            return Component.translatable("residual.port.link_full");
        }
        return menu.data.get(PortBlockEntity.FLUID_MOVED) > 0
                ? Component.translatable("residual.port.moved_fluid", Ui.count(menu.data.get(PortBlockEntity.FLUID_MOVED)))
                : Component.translatable("residual.port.moved", Ui.count(menu.data.get(PortBlockEntity.MOVED)));
    }

    /** What a side slot is for, while it is pointed at and empty, or for the target model what it expects. */
    private void side(GuiGraphicsExtractor g, int index, String name, Component hint, int mouseX, int mouseY) {
        Slot slot = menu.getSlot(index);
        boolean says = index == PortMenu.TARGET && menu.data.get(PortBlockEntity.EXPECTS) >= 0;
        if ((!slot.hasItem() || says) && Ui.inside(mouseX - leftPos, mouseY - topPos, slot.x, slot.y, 16, 16)) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(Component.translatable(name).getVisualOrderText());
            lines.addAll(font.split(hint.copy().withStyle(ChatFormatting.GRAY), 200));
            g.setTooltipForNextFrame(lines, mouseX, mouseY);
        }
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
        if (menu.getCarried().isEmpty()) {
            side(g, PortMenu.MODEL, "residual.port.model", Component.translatable("residual.port.model_hint"), mouseX, mouseY);
            int expects = menu.data.get(PortBlockEntity.EXPECTS);
            side(g, PortMenu.TARGET, "residual.port.target", expects < 0 ? Component.translatable("residual.port.target_hint")
                    : Component.translatable("residual.port.expects", Ui.count(expects)), mouseX, mouseY);
            side(g, PortMenu.LOG, "residual.port.log", Component.translatable("residual.port.log_hint"), mouseX, mouseY);
        }
    }
}
