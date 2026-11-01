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
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Pick a machine and watch it being made: the mask's machines to choose from, then the materials, an arrow and the
 * product, faint in the output slot until it is made. What each material slot needs shows under it, red when short; the
 * product's name, what it does and how long it takes show when the output or the arrow is pointed at.
 */
public class FabricatorScreen extends AbstractContainerScreen<FabricatorMenu> {
    private static final int CHOICES_X = 40, CHOICES_Y = 24, CELL = 19, PER_ROW = 11;
    private static final int ARROW_X = 133;

    public FabricatorScreen(FabricatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 256, FabricatorMenu.INVENTORY_Y + 82);
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
        Ui.arrow(g, leftPos + ARROW_X, topPos + FabricatorMenu.OUTPUT_Y, done);
        if (chosen >= 0) {
            ghost(g, FabricatorBlockEntity.OUTPUT, FabricatorBlockEntity.product(choices.get(chosen), menu.mask()));
        }
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
        int chosen = choice();
        if (chosen < 0) {
            int ly = CHOICES_Y + 4;
            for (FormattedCharSequence line : font.split(Component.translatable("residual.fabricator.no_machines"), imageWidth - CHOICES_X - 8)) {
                g.text(font, line, CHOICES_X, ly, Ui.LABEL_DIM, false);
                ly += 9;
            }
            return;
        }
        // what each material slot needs, under it
        Cost cost = FabricatorBlockEntity.cost(choices().get(chosen), menu.mask());
        need(g, FabricatorMenu.CASING_X, FabricatorBlockEntity.CASING, cost.casings());
        need(g, FabricatorMenu.WAFER_X, FabricatorBlockEntity.WAFER, cost.wafers());
        need(g, FabricatorMenu.CRYSTAL_X, FabricatorBlockEntity.CRYSTAL, cost.crystals());
    }

    private void need(GuiGraphicsExtractor g, int x, int slot, int count) {
        if (count == 0) {
            return;
        }
        String c = "×" + count;
        boolean enough = menu.getSlot(slot).getItem().getCount() >= count;
        g.text(font, c, x + 8 - font.width(c) / 2, FabricatorMenu.MATERIALS_Y + 19, enough ? Ui.LABEL_DIM : 0xFFB3261E, false);
    }

    /** What the chosen machine is, what it does, and how long it takes to make. */
    private List<Component> product() {
        int chosen = choice();
        Item item = choices().get(chosen);
        ItemStack product = FabricatorBlockEntity.product(item, menu.mask());
        Cost cost = FabricatorBlockEntity.cost(item, menu.mask());
        MaskData m = MaskItem.mask(menu.mask());
        Component note;
        if (item == ModItems.HOURGLASS.get()) {
            note = Component.translatable("residual.fabricator.hourglass");
        } else if (item == ModBlocks.FORECASTER_II.asItem()) {
            note = Component.translatable("residual.fabricator.forecaster_ii");
        } else if (item == ModBlocks.FORECASTER_III.asItem()) {
            note = Component.translatable("residual.fabricator.forecaster_iii");
        } else if (item == ModBlocks.SMELTER.asItem()) {
            note = Component.translatable("residual.fabricator.smelter");
        } else if (item == ModBlocks.SURVEYOR.asItem()) {
            note = Component.translatable("residual.fabricator.surveyor");
        } else if (item == ModItems.FLIGHT_FRAME.get()) {
            note = Component.translatable("residual.fabricator.flight_frame");

        } else if (!(item instanceof MachineItem)) {
            note = Component.translatable("residual.fabricator.accelerator");
        } else if (m != null) {
            note = Component.translatable(m.salvaged() ? "residual.fabricator.unfused" : "residual.fabricator.fused", m.area());
        } else {
            note = Component.translatable("residual.fabricator.plain");
        }
        boolean unfused = item instanceof MachineItem && m != null && m.salvaged();
        return List.of(product.getHoverName(), note.copy().withStyle(unfused ? ChatFormatting.GOLD : ChatFormatting.GRAY),
                Component.translatable("residual.fabricator.time", String.format(Locale.ROOT, "%.1f", cost.ticks() / 20f))
                        .withStyle(ChatFormatting.GRAY));
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
        int mx = mouseX - leftPos, my = mouseY - topPos;
        boolean product = Ui.inside(mx, my, ARROW_X, FabricatorMenu.OUTPUT_Y, 24, 16)
                || Ui.inside(mx, my, FabricatorMenu.OUTPUT_X, FabricatorMenu.OUTPUT_Y, 16, 16) && !menu.getSlot(FabricatorBlockEntity.OUTPUT).hasItem();
        if (product && choice() >= 0 && menu.getCarried().isEmpty()) {
            g.setComponentTooltipForNextFrame(font, product(), mouseX, mouseY);
        }
        List<Item> choices = choices();
        for (int i = 0; i < choices.size(); i++) {
            int x = leftPos + CHOICES_X + (i % PER_ROW) * CELL, y = topPos + CHOICES_Y + (i / PER_ROW) * CELL;
            if (Ui.inside(mouseX, mouseY, x, y, 18, 18)) {
                g.setTooltipForNextFrame(font, FabricatorBlockEntity.product(choices.get(i), menu.mask()), mouseX, mouseY);
            }
        }
    }
}
