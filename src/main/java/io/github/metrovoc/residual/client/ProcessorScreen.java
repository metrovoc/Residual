package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.net.AssemblerEdit;
import io.github.metrovoc.residual.storage.ProcessorBlock;
import io.github.metrovoc.residual.storage.ProcessorBlockEntity;
import io.github.metrovoc.residual.storage.ProcessorMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** A Processor's pattern: what goes in, the machine in front of it, what comes out; how many to keep, and what it is doing. */
public class ProcessorScreen extends AbstractContainerScreen<ProcessorMenu> implements Parts.Drawn {
    private static final int PAGES_Y = ProcessorMenu.GRID_Y + 56, KEEP_Y = PAGES_Y + 16, GRID_W = 54;

    public ProcessorScreen(ProcessorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, ProcessorMenu.INVENTORY_Y + 82);
        inventoryLabelY = ProcessorMenu.INVENTORY_Y - 10;
        inventoryLabelX = ProcessorMenu.INVENTORY_X;
    }

    @Override
    protected void init() {
        super.init();
        pages(ProcessorMenu.IN_X, ProcessorMenu.IN_BACK, ProcessorMenu.IN_ON);
        pages(ProcessorMenu.OUT_X, ProcessorMenu.OUT_BACK, ProcessorMenu.OUT_ON);
        int x = leftPos + 76;
        for (int step : new int[]{-16, -1, 1, 16}) {
            addRenderableWidget(Button.builder(Component.literal((step > 0 ? "+" : "−") + Math.abs(step)), b -> {
                int by = step * (Minecraft.getInstance().hasShiftDown() ? 64 : 1);
                ClientPacketDistributor.sendToServer(new AssemblerEdit(menu.pos, menu.data.get(ProcessorBlockEntity.KEEP) + by));
            }).bounds(x, topPos + KEEP_Y - 2, 22, 12).build());
            x += 23;
        }
    }

    /** Back and on, at the two ends under a grid; the page shows between them. */
    private void pages(int x, int back, int on) {
        addRenderableWidget(Button.builder(Component.literal("‹"), b -> press(back)).bounds(leftPos + x - 1, topPos + PAGES_Y, 11, 11).build());
        addRenderableWidget(Button.builder(Component.literal("›"), b -> press(on)).bounds(leftPos + x + GRID_W - 11, topPos + PAGES_Y, 11, 11).build());
    }

    private void press(int button) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    /** What stands in front of the Processor, as an item, or nothing. */
    private ItemStack machine() {
        Minecraft mc = Minecraft.getInstance();
        BlockState here = mc.level.getBlockState(menu.pos);
        if (!(here.getBlock() instanceof ProcessorBlock)) {
            return ItemStack.EMPTY;
        }
        BlockPos at = menu.pos.relative(here.getValue(ProcessorBlock.FACING));
        return new ItemStack(mc.level.getBlockState(at).getBlock());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        // the machine in the middle of the flow, with an arrow under it from what goes in to what comes out
        int gap = ProcessorMenu.OUT_X - ProcessorMenu.IN_X - GRID_W;
        int mid = leftPos + ProcessorMenu.IN_X + GRID_W + gap / 2;
        ItemStack machine = machine();
        if (!machine.isEmpty()) {
            g.item(machine, mid - 8, topPos + ProcessorMenu.GRID_Y + 8);
        }
        Ui.arrow(g, mid - 11, topPos + ProcessorMenu.GRID_Y + 28, 0);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
        page(g, ProcessorMenu.IN_X, ProcessorMenu.IN_PAGE);
        page(g, ProcessorMenu.OUT_X, ProcessorMenu.OUT_PAGE);
        g.text(font, Component.translatable("residual.assembler.keep", Ui.count(menu.data.get(ProcessorBlockEntity.KEEP))), 8, KEEP_Y, Ui.LABEL, false);
        int waiting = menu.data.get(ProcessorBlockEntity.WAITING);
        Component status = switch (waiting) {
            case ProcessorBlockEntity.WORKING -> Component.translatable("residual.processor.working", Ui.count(menu.data.get(ProcessorBlockEntity.MADE)));
            case ProcessorBlockEntity.NO_MACHINE -> Component.translatable("residual.processor.no_machine");
            case ProcessorBlockEntity.ENOUGH -> Component.translatable("residual.processor.ready");
            case ProcessorBlockEntity.MISSING -> Component.translatable("residual.assembler.missing");
            case ProcessorBlockEntity.FULL -> Component.translatable("residual.processor.full");
            case ProcessorBlockEntity.PAUSED -> Component.translatable("residual.assembler.paused");
            default -> Component.translatable("residual.processor.no_pattern");
        };
        g.text(font, status, 8, KEEP_Y + 16, waiting == ProcessorBlockEntity.WORKING ? 0xFF2E7D32 : Ui.LABEL_DIM, false);
    }

    private void page(GuiGraphicsExtractor g, int x, int which) {
        String shown = (menu.data.get(which) + 1) + "/" + ProcessorBlockEntity.PAGES;
        g.text(font, shown, x + (GRID_W - font.width(shown)) / 2, PAGES_Y + 2, Ui.LABEL_DIM, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        for (int i = 0; i < ProcessorMenu.CELLS; i++) {
            Slot slot = menu.getSlot(i);
            if (!slot.hasItem() && menu.getCarried().isEmpty() && Ui.inside(mouseX - leftPos, mouseY - topPos, slot.x, slot.y, 16, 16)) {
                String key = i < ProcessorBlockEntity.PAGE ? "residual.processor.in_hint" : "residual.processor.out_hint";
                g.setTooltipForNextFrame(font.split(Component.translatable(key), 200), mouseX, mouseY);
            }
        }
    }

    @Override
    public void parts(Parts out) {
        int page = ProcessorBlockEntity.PAGE;
        for (int i = 0; i < page; i++) {
            out.add("in:" + i, this, menu.getSlot(i));
            out.add("out:" + i, this, menu.getSlot(page + i));
        }
        out.add("in", leftPos + ProcessorMenu.IN_X - 1, topPos + ProcessorMenu.GRID_Y - 1, GRID_W, GRID_W);
        out.add("out", leftPos + ProcessorMenu.OUT_X - 1, topPos + ProcessorMenu.GRID_Y - 1, GRID_W, GRID_W);
        out.add("in:back", leftPos + ProcessorMenu.IN_X - 1, topPos + PAGES_Y, 11, 11);
        out.add("in:on", leftPos + ProcessorMenu.IN_X + GRID_W - 11, topPos + PAGES_Y, 11, 11);
        out.add("out:back", leftPos + ProcessorMenu.OUT_X - 1, topPos + PAGES_Y, 11, 11);
        out.add("out:on", leftPos + ProcessorMenu.OUT_X + GRID_W - 11, topPos + PAGES_Y, 11, 11);
        out.add("machine", leftPos + (ProcessorMenu.IN_X + GRID_W + ProcessorMenu.OUT_X) / 2 - 12, topPos + ProcessorMenu.GRID_Y + 8, 24, 36);
        out.add("keep", leftPos + 8, topPos + KEEP_Y - 2, imageWidth - 16, 12);
        out.add("status", leftPos + 8, topPos + KEEP_Y + 14, imageWidth - 16, 11);
    }
}
