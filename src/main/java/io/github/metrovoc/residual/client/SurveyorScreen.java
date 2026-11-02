package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.block.SurveyorBlockEntity;
import io.github.metrovoc.residual.block.SurveyorBlockEntity.State;
import io.github.metrovoc.residual.learn.Survey;
import io.github.metrovoc.residual.menu.SurveyorMenu;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Surveyor's screen: one display, with each kind of find, the eight ores and caches, three by three and how many of
 * each, or why it cannot survey, and along its foot how far the survey has gone. Its model slot sits in a panel beside
 * the window.
 */
public class SurveyorScreen extends AbstractContainerScreen<SurveyorMenu> implements Parts.Drawn {
    private static final int WINDOW_W = 176, SHOW_X = 8, SHOW_Y = 18, SHOW_W = 160, SHOW_H = 68, ROW_H = 20, COL_W = 52;
    /** The finds, in the survey's order: coal, iron, copper, gold, redstone, lapis, diamond, emerald, a cache. */
    private static final ItemStack[] FINDS = {new ItemStack(Items.COAL_ORE), new ItemStack(Items.IRON_ORE), new ItemStack(Items.COPPER_ORE),
            new ItemStack(Items.GOLD_ORE), new ItemStack(Items.REDSTONE_ORE), new ItemStack(Items.LAPIS_ORE),
            new ItemStack(Items.DIAMOND_ORE), new ItemStack(Items.EMERALD_ORE), new ItemStack(Items.BARREL)};

    public SurveyorScreen(SurveyorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SurveyorMenu.SIDE_X + SurveyorMenu.SIDE_W, SurveyorMenu.INVENTORY_Y + 82);
        inventoryLabelY = SurveyorMenu.INVENTORY_Y - 10;
        inventoryLabelX = SurveyorMenu.INVENTORY_X;
    }

    private State state() {
        State[] all = State.values();
        return all[Math.floorMod(menu.data.get(SurveyorBlockEntity.STATE), all.length)];
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        // the model slot's panel, tucked under the window's edge
        Ui.window(g, leftPos + SurveyorMenu.SIDE_X, topPos + SurveyorMenu.SIDE_Y, SurveyorMenu.SIDE_W, SurveyorMenu.SIDE_H);
        Ui.window(g, leftPos, topPos, WINDOW_W, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        if (!menu.getSlot(SurveyorBlockEntity.MODEL).hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.CHECKPOINT.get()), leftPos + SurveyorMenu.MODEL_X, topPos + SurveyorMenu.MODEL_Y);
        }
        if (!menu.getSlot(SurveyorBlockEntity.LOG).hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.FIELD_LOG.get()), leftPos + SurveyorMenu.MODEL_X, topPos + SurveyorMenu.LOG_Y);
        }
        int x = leftPos + SHOW_X, y = topPos + SHOW_Y;
        Ui.screen(g, x, y, SHOW_W, SHOW_H);
        if (state() == State.SURVEYING) {
            for (int i = 0; i < FINDS.length; i++) {
                g.item(FINDS[i], x + 4 + i % 3 * COL_W, y + 4 + i / 3 * ROW_H);
            }
            // how far this survey has gone, along the display's foot
            float done = menu.data.get(SurveyorBlockEntity.PROGRESS) / 1000f;
            g.fill(x, y + SHOW_H - 2, x + Math.round(SHOW_W * done), y + SHOW_H, Ui.TEAL);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
        State state = state();
        if (state != State.SURVEYING) {
            Component why = state == State.PEEKS ? Component.translatable("residual.surveyor.peeks")
                    : Component.translatable(SurveyorBlockEntity.PROBLEMS.get(Math.floorMod(menu.data.get(SurveyorBlockEntity.PROBLEM),
                    SurveyorBlockEntity.PROBLEMS.size())));
            int y = SHOW_Y + 4;
            for (FormattedCharSequence line : font.split(why, SHOW_W - 8)) {
                g.text(font, line, SHOW_X + 4, y, state == State.PEEKS ? Ui.RED : Ui.AMBER, false);
                y += 10;
            }
            return;
        }
        for (int i = 0; i < FINDS.length; i++) {
            int count = menu.data.get(SurveyorBlockEntity.FOUND + i);
            g.text(font, Ui.count(count), SHOW_X + 23 + i % 3 * COL_W, SHOW_Y + 8 + i / 3 * ROW_H, count > 0 ? Ui.TEXT : Ui.DIM, false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (!menu.getSlot(SurveyorBlockEntity.MODEL).hasItem() && menu.getCarried().isEmpty()
                && Ui.inside(mx, my, SurveyorMenu.MODEL_X, SurveyorMenu.MODEL_Y, 16, 16)) {
            lines.add(Component.translatable("residual.agent.model_slot").getVisualOrderText());
            lines.addAll(font.split(Component.translatable("residual.surveyor.model_hint").withStyle(ChatFormatting.GRAY), 200));
        } else if (!menu.getSlot(SurveyorBlockEntity.LOG).hasItem() && menu.getCarried().isEmpty()
                && Ui.inside(mx, my, SurveyorMenu.MODEL_X, SurveyorMenu.LOG_Y, 16, 16)) {
            lines.add(Component.translatable("item.residual.field_log").getVisualOrderText());
            lines.addAll(font.split(Component.translatable("residual.surveyor.log_hint").withStyle(ChatFormatting.GRAY), 200));
        } else if (state() == State.SURVEYING && Ui.inside(mx, my, SHOW_X, SHOW_Y, SHOW_W, SHOW_H)) {
            int col = (mx - SHOW_X - 2) / COL_W, row = (my - SHOW_Y - 2) / ROW_H;
            int at = col >= 0 && col < 3 && row >= 0 && row < 3 ? row * 3 + col : -1;
            if (my >= SHOW_Y + SHOW_H - 6) {
                lines.add(Component.translatable("residual.surveyor.progress", menu.data.get(SurveyorBlockEntity.PROGRESS) / 10,
                        Ui.count(menu.data.get(SurveyorBlockEntity.MISSED))).getVisualOrderText());
                lines.addAll(font.split(Component.translatable("residual.surveyor.reach", SurveyorBlockEntity.RADIUS,
                        SurveyorBlockEntity.BELOW).withStyle(ChatFormatting.GRAY), 220));
            } else if (at == FINDS.length - 1) {
                lines.add(Component.translatable("residual.surveyor.caches").getVisualOrderText());
                lines.addAll(font.split(Component.translatable("residual.surveyor.caches_hint").withStyle(ChatFormatting.GRAY), 200));
                lines.addAll(font.split(Component.translatable("residual.surveyor.rod_hint").withStyle(ChatFormatting.GRAY), 200));
            } else if (at >= 0) {
                lines.add(FINDS[at].getHoverName().getVisualOrderText());
                lines.addAll(font.split(Component.translatable("residual.surveyor.rod_hint").withStyle(ChatFormatting.GRAY), 200));
            }
        }
        if (!lines.isEmpty()) {
            g.setTooltipForNextFrame(lines, mouseX, mouseY);
        }
    }

    /** {@code find:0} on, in the survey's order. */
    @Override
    public void parts(Parts out) {
        out.add("model", this, menu.getSlot(SurveyorBlockEntity.MODEL));
        out.add("log", this, menu.getSlot(SurveyorBlockEntity.LOG));
        out.add("finds", leftPos + SHOW_X, topPos + SHOW_Y, SHOW_W, SHOW_H - 2);
        out.add("progress", leftPos + SHOW_X, topPos + SHOW_Y + SHOW_H - 6, SHOW_W, 6);
        for (int i = 0; i < FINDS.length; i++) {
            out.add("find:" + i, leftPos + SHOW_X + 2 + i % 3 * COL_W, topPos + SHOW_Y + 2 + i / 3 * ROW_H, COL_W, ROW_H);
        }
    }
}
