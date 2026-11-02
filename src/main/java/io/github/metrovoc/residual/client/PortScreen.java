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
public class PortScreen extends AbstractContainerScreen<PortMenu> implements Parts.Drawn {
    private static final int WINDOW_W = 176, TOOLBAR_Y = 78, TOOLBAR_STEP = 22;
    private static final int MODE_X = 178, NUMBER_X = 197, NUMBER_W = 22, STATUS_X = 76, STATUS_Y = 72;
    private static final int[] STEPS = {-16, -1, 1, 16};
    private final List<IconButton> modes = new ArrayList<>();
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
        ItemStack[] icons = {new ItemStack(Items.HOPPER), new ItemStack(Items.DROPPER), new ItemStack(Items.CHEST), new ItemStack(Items.GLASS)};
        for (PortMode m : PortMode.values()) {
            Component name = Component.translatable("residual.port.mode." + m.id);
            IconButton button = new IconButton(leftPos + MODE_X, topPos + TOOLBAR_Y + m.ordinal() * TOOLBAR_STEP,
                    IconButton.SIZE, IconButton.SIZE, icons[m.ordinal()], name, () -> send(PortEdit.MODE, m.ordinal()));
            button.setTooltip(Tooltip.create(name.copy().append("\n").append(Component.translatable("residual.port.mode." + m.id + ".help")
                    .withStyle(ChatFormatting.GRAY))));
            modes.add(addRenderableWidget(button));
        }
        int y = topPos + TOOLBAR_Y;
        for (int step : STEPS) {
            nudges.add(addRenderableWidget(Button.builder(Component.literal((step > 0 ? "+" : "−") + Math.abs(step)), b -> nudge(step))
                    .bounds(leftPos + NUMBER_X, y, NUMBER_W, 18).build()));
            y += TOOLBAR_STEP;
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
        for (int i = 0; i < nudges.size(); i++) {
            Button button = nudges.get(i);
            button.visible = mode == PortMode.STOCK || mode == PortMode.WINDOW;
            int step = mode == PortMode.WINDOW ? Math.min(Math.abs(STEPS[i]), 4) : Math.abs(STEPS[i]);
            button.setMessage(Component.literal((STEPS[i] > 0 ? "+" : "−") + step));
        }
        Ui.arrow(g, leftPos + STATUS_X, topPos + STATUS_Y,
                menu.data.get(PortBlockEntity.MOVED) > 0 || menu.data.get(PortBlockEntity.FLUID_MOVED) > 0 ? 1 : 0);
        boolean asks = menu.data.get(PortBlockEntity.CRAFT) != 0;
        craft.visible = mode == PortMode.STOCK || mode == PortMode.EXPORT;
        craft.setTooltip(Tooltip.create(Component.translatable(asks ? "residual.port.craft.on" : "residual.port.craft.off")));
        if (craft.visible && asks) {
            g.fill(craft.getX() + 2, craft.getY() + craft.getHeight(), craft.getX() + craft.getWidth() - 2,
                    craft.getY() + craft.getHeight() + 1, Ui.TEAL);
        }
        IconButton chosen = modes.get(mode.ordinal());
        g.fill(chosen.getX() + 2, chosen.getY() + chosen.getHeight() + 1, chosen.getX() + chosen.getWidth() - 2,
                chosen.getY() + chosen.getHeight() + 2, Ui.TEAL);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
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

    /** What an empty side slot is for. */
    private void side(GuiGraphicsExtractor g, int index, String name, Component hint, int mouseX, int mouseY) {
        Slot slot = menu.getSlot(index);
        if (!slot.hasItem() && Ui.inside(mouseX - leftPos, mouseY - topPos, slot.x, slot.y, 16, 16)) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(Component.translatable(name).getVisualOrderText());
            lines.addAll(font.split(hint.copy().withStyle(ChatFormatting.GRAY), 200));
            g.setTooltipForNextFrame(lines, mouseX, mouseY);
        }
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        int expects = menu.data.get(PortBlockEntity.EXPECTS);
        if (hoveredSlot == menu.getSlot(PortMenu.TARGET) && expects >= 0) {
            lines.add(Math.min(1, lines.size()), Component.translatable("residual.port.expects", Ui.count(expects)).withStyle(ChatFormatting.AQUA));
        }
        return lines;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int x = mouseX - leftPos, y = mouseY - topPos;
        PortMode mode = mode();
        if ((mode == PortMode.STOCK || mode == PortMode.WINDOW)
                && Ui.inside(x, y, NUMBER_X, TOOLBAR_Y, NUMBER_W, 3 * TOOLBAR_STEP + 18)) {
            Component value = mode == PortMode.STOCK
                    ? Component.translatable("residual.port.amount", Ui.count(menu.data.get(PortBlockEntity.AMOUNT)))
                    : Component.translatable("residual.port.priority", menu.data.get(PortBlockEntity.PRIORITY));
            value = value.copy().append("\n").append(Component.translatable(mode == PortMode.STOCK
                    ? "residual.port.amount_help" : "residual.port.priority_help").withStyle(ChatFormatting.GRAY));
            g.setTooltipForNextFrame(font.split(value, 200), mouseX, mouseY);
        } else if (Ui.inside(x, y, STATUS_X, STATUS_Y, 24, 16)) {
            g.setTooltipForNextFrame(font.split(status(), 200), mouseX, mouseY);
        }
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

    @Override
    public void parts(Parts out) {
        for (int i = 0; i < PortBlockEntity.FILTERS; i++) {
            out.add("filter:" + i, this, menu.getSlot(i));
        }
        out.add("filter", leftPos + PortMenu.FILTER_X - 1, topPos + PortMenu.FILTER_Y - 1, PortBlockEntity.FILTERS * 18, 18);
        out.add("model", this, menu.getSlot(PortMenu.MODEL));
        out.add("target", this, menu.getSlot(PortMenu.TARGET));
        out.add("log", this, menu.getSlot(PortMenu.LOG));
        out.add("status", leftPos + STATUS_X, topPos + STATUS_Y, 24, 16);
        for (PortMode m : PortMode.values()) {
            IconButton button = modes.get(m.ordinal());
            out.add("port.mode." + m.id, button.getX(), button.getY(), button.getWidth(), button.getHeight(), mode() == m);
        }
        if (mode() == PortMode.STOCK || mode() == PortMode.WINDOW) {
            out.add("value", leftPos + NUMBER_X, topPos + TOOLBAR_Y, NUMBER_W, 3 * TOOLBAR_STEP + 18);
            out.add(mode() == PortMode.STOCK ? "keep" : "priority", leftPos + NUMBER_X, topPos + TOOLBAR_Y, NUMBER_W, 3 * TOOLBAR_STEP + 18);
        }
        String[] names = {"less:16", "less:1", "more:1", "more:16"};
        for (int i = 0; i < nudges.size(); i++) {
            out.add(names[i], nudges.get(i));
            out.add((STEPS[i] > 0 ? "+" : "−") + Math.abs(STEPS[i]), nudges.get(i));
        }
    }
}
