package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.bench.MaskData;
import io.github.metrovoc.residual.block.FabricatorBlockEntity;
import io.github.metrovoc.residual.block.FabricatorBlockEntity.Cost;
import io.github.metrovoc.residual.item.MachineItem;
import io.github.metrovoc.residual.item.MaskItem;
import io.github.metrovoc.residual.menu.FabricatorMenu;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Pick a machine, see what it takes, and watch it being made. */
public class FabricatorScreen extends AbstractContainerScreen<FabricatorMenu> {
    private static final int CHOICES_X = 40, CHOICES_Y = 24, CELL = 19, PER_ROW = 11;
    private static final int BAR_X = 76, BAR_W = 134;

    public FabricatorScreen(FabricatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 256, 220);
        inventoryLabelY = FabricatorMenu.INVENTORY_Y - 10;
        inventoryLabelX = FabricatorMenu.INVENTORY_X;
    }

    private List<Item> choices() {
        return FabricatorBlockEntity.choices(menu.mask());
    }

    private int choice() {
        List<Item> c = choices();
        return c.isEmpty() ? -1 : Math.min(menu.data.get(FabricatorBlockEntity.CHOICE), c.size() - 1);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        ghost(g, FabricatorBlockEntity.MASK, new ItemStack(ModItems.PHOTOMASK.get()));
        ghost(g, FabricatorBlockEntity.CASING, new ItemStack(ModBlocks.CASING.asItem()));
        ghost(g, FabricatorBlockEntity.WAFER, new ItemStack(ModItems.WAFER.get()));
        ghost(g, FabricatorBlockEntity.CRYSTAL, new ItemStack(ModItems.WEIGHT_CRYSTAL.get()));
        List<Item> choices = choices();
        int chosen = choice();
        for (int i = 0; i < choices.size(); i++) {
            int x = leftPos + CHOICES_X + (i % PER_ROW) * CELL, y = topPos + CHOICES_Y + (i / PER_ROW) * CELL;
            g.fill(x, y, x + 18, y + 18, i == chosen ? Ui.TEAL : Ui.inside(mouseX, mouseY, x, y, 18, 18) ? 0xFFDADADA : 0xFF8B8B8B);
            g.item(FabricatorBlockEntity.product(choices.get(i), menu.mask()), x + 1, y + 1);
        }
        int total = menu.data.get(FabricatorBlockEntity.TOTAL);
        float done = total == 0 ? 0 : menu.data.get(FabricatorBlockEntity.PROGRESS) / (float) total;
        Ui.bar(g, leftPos + BAR_X, topPos + FabricatorMenu.OUTPUT_Y + 6, BAR_W, 6, done, Ui.TEAL);
    }

    /** A faint picture of what belongs in an empty slot. */
    private void ghost(GuiGraphicsExtractor g, int index, ItemStack stack) {
        Slot slot = menu.getSlot(index);
        if (slot.getItem().isEmpty()) {
            int sx = leftPos + slot.x, sy = topPos + slot.y;
            g.item(stack, sx, sy);
            g.fill(sx, sy, sx + 16, sy + 16, 0xB08B8B8B);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
        g.text(font, Component.translatable("residual.fabricator.materials"), FabricatorMenu.CASING_X - 2, FabricatorMenu.MATERIALS_Y - 10, Ui.LABEL, false);
        List<Item> choices = choices();
        int chosen = choice();
        int y = CHOICES_Y + ((choices.size() + PER_ROW - 1) / PER_ROW) * CELL + 3;
        if (chosen < 0) {
            int ly = CHOICES_Y + 4;
            for (FormattedCharSequence line : font.split(Component.translatable("residual.fabricator.no_machines"), imageWidth - CHOICES_X - 8)) {
                g.text(font, line, CHOICES_X, ly, Ui.LABEL_DIM, false);
                ly += 9;
            }
            return;
        }
        Item item = choices.get(chosen);
        ItemStack product = FabricatorBlockEntity.product(item, menu.mask());
        Cost cost = FabricatorBlockEntity.cost(item, menu.mask());
        g.text(font, product.getHoverName(), CHOICES_X, y, Ui.LABEL, false);
        y += 11;
        int x = CHOICES_X;
        x = need(g, x, y, "residual.fabricator.casings", cost.casings(), FabricatorBlockEntity.CASING);
        x = need(g, x, y, "residual.fabricator.wafers", cost.wafers(), FabricatorBlockEntity.WAFER);
        need(g, x, y, "residual.fabricator.crystals", cost.crystals(), FabricatorBlockEntity.CRYSTAL);
        y += 11;
        MaskData m = MaskItem.mask(menu.mask());
        Component note;
        if (!(item instanceof MachineItem)) {
            note = Component.translatable("residual.fabricator.accelerator");
        } else if (m != null) {
            note = Component.translatable(m.salvaged() ? "residual.fabricator.unfused" : "residual.fabricator.fused", m.area());
        } else {
            note = Component.translatable("residual.fabricator.plain");
        }
        g.text(font, Ui.fit(font, note.getString(), imageWidth - CHOICES_X - 8), CHOICES_X, y, m != null && m.salvaged() ? 0xFF9A5B00 : Ui.LABEL_DIM, false);
        g.text(font, Component.translatable("residual.fabricator.time", String.format(Locale.ROOT, "%.1f", cost.ticks() / 20f)),
                BAR_X, FabricatorMenu.OUTPUT_Y - 4, Ui.LABEL_DIM, false);
    }

    private int need(GuiGraphicsExtractor g, int x, int y, String key, int count, int slot) {
        if (count == 0) {
            return x;
        }
        boolean enough = menu.getSlot(slot).getItem().getCount() >= count;
        Component c = Component.translatable(key, count);
        g.text(font, c, x, y, enough ? Ui.LABEL : 0xFFB3261E, false);
        return x + font.width(c) + 10;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        List<Item> choices = choices();
        for (int i = 0; i < choices.size(); i++) {
            int x = leftPos + CHOICES_X + (i % PER_ROW) * CELL, y = topPos + CHOICES_Y + (i / PER_ROW) * CELL;
            if (Ui.inside(event.x(), event.y(), x, y, 18, 18)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        List<Item> choices = choices();
        for (int i = 0; i < choices.size(); i++) {
            int x = leftPos + CHOICES_X + (i % PER_ROW) * CELL, y = topPos + CHOICES_Y + (i / PER_ROW) * CELL;
            if (Ui.inside(mouseX, mouseY, x, y, 18, 18)) {
                g.setTooltipForNextFrame(font, FabricatorBlockEntity.product(choices.get(i), menu.mask()), mouseX, mouseY);
            }
        }
    }
}
