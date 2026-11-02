package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.block.ArenaBlockEntity;
import io.github.metrovoc.residual.block.ArenaBlockEntity.State;
import io.github.metrovoc.residual.item.MobLogItem;
import io.github.metrovoc.residual.learn.Ground;
import io.github.metrovoc.residual.learn.Imitation;
import io.github.metrovoc.residual.menu.ArenaMenu;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The Arena's screen. The window holds the flow alone: the log, a display of the mob being played with its tier beneath it, what
 * came of the fights. The model's slot and the store of power sit in a panel beside it, and every number shows on what it
 * describes.
 */
public class ArenaScreen extends AbstractContainerScreen<ArenaMenu> implements Parts.Drawn {
    private static final int WINDOW_W = 176, WINDOW_H = ArenaMenu.INVENTORY_Y + 82;
    private static final int SHOW_X = 28, SHOW_Y = 17, SHOW_W = 48, SHOW_H = 52, PIP_W = 7, PIP_H = 2, PIP_GAP = 2;
    private static final int BAR_X = 181, BAR_Y = 30, BAR_W = 10, BAR_H = 44;
    /** The lit tiers, weakest to strongest. */
    private static final int LIT = Ui.TEAL, UNLIT = Ui.GRID;
    /** The ores ground can pay with, in {@link Ground#ORES}'s order: coal, iron, copper, gold, redstone, lapis, diamond, emerald. */
    private static final ItemStack[] ORES = {new ItemStack(Items.COAL_ORE), new ItemStack(Items.IRON_ORE), new ItemStack(Items.COPPER_ORE),
            new ItemStack(Items.GOLD_ORE), new ItemStack(Items.REDSTONE_ORE), new ItemStack(Items.LAPIS_ORE),
            new ItemStack(Items.DIAMOND_ORE), new ItemStack(Items.EMERALD_ORE)};

    @Nullable
    private CompoundTag shownTag;
    @Nullable
    private LivingEntity shown;

    public ArenaScreen(ArenaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ArenaMenu.SIDE_X + ArenaMenu.SIDE_W, WINDOW_H);
        inventoryLabelY = ArenaMenu.INVENTORY_Y - 10;
        inventoryLabelX = ArenaMenu.INVENTORY_X;
    }

    private State state() {
        State[] all = State.values();
        return all[Math.floorMod(menu.data.get(ArenaBlockEntity.STATE), all.length)];
    }

    /** The mob in the log, to show: the one the log was first used on. */
    @Nullable
    private LivingEntity shown() {
        MobLogItem.Log log = MobLogItem.log(menu.getSlot(ArenaBlockEntity.LOG).getItem());
        CompoundTag tag = log == null ? null : log.specimen();
        if (tag == null) {
            shownTag = null;
            shown = null;
        } else if (!tag.equals(shownTag) && minecraft != null && minecraft.level != null) {
            shownTag = tag;
            shown = EntityType.loadEntityRecursive(tag.copy(), minecraft.level, EntitySpawnReason.LOAD, EntityProcessor.NOP) instanceof LivingEntity mob ? mob : null;
        }
        return shown;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos + ArenaMenu.SIDE_X, topPos + ArenaMenu.SIDE_Y, ArenaMenu.SIDE_W, ArenaMenu.SIDE_H);
        Ui.window(g, leftPos, topPos, WINDOW_W, WINDOW_H);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        if (!menu.getSlot(ArenaBlockEntity.LOG).hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.MOB_LOG.get()), leftPos + ArenaMenu.LOG_X, topPos + ArenaMenu.LOG_Y);
        }
        if (!menu.getSlot(ArenaBlockEntity.MODEL).hasItem()) {
            Ui.ghost(g, new ItemStack(ModItems.CHECKPOINT.get()), leftPos + ArenaMenu.MODEL_X, topPos + ArenaMenu.MODEL_Y);
        }
        int x = leftPos + SHOW_X, y = topPos + SHOW_Y;
        Ui.screen(g, x, y, SHOW_W, SHOW_H);
        LivingEntity mob = shown();
        if (mob != null) {
            float tall = Math.max(mob.getBbHeight(), mob.getBbWidth());
            int size = Math.min(22, Math.round(38 / Math.max(1f, tall)));
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + 1, y + 1, x + SHOW_W - 1, y + SHOW_H - 6, size, 0.0625f, mouseX, mouseY, mob);
        } else if (menu.data.get(ArenaBlockEntity.GROUND) != 0) {
            g.item(new ItemStack(Items.DEEPSLATE_DIAMOND_ORE), x + SHOW_W / 2 - 8, y + SHOW_H / 2 - 10);
        }
        // the tier the model has reached, a pip a tier along the display's foot
        int pips = Imitation.TIERS.length, tier = menu.data.get(ArenaBlockEntity.TIER);
        int w = pips * PIP_W + (pips - 1) * PIP_GAP, px = x + (SHOW_W - w) / 2, py = y + SHOW_H - PIP_H - 2;
        for (int i = 0; i < pips; i++) {
            int at = px + i * (PIP_W + PIP_GAP);
            g.fill(at, py, at + PIP_W, py + PIP_H, i < tier ? LIT : UNLIT);
        }
        if (state() != State.READY && state() != State.NO_LOG) {
            // something stands in its way: a mark in the corner, and the tooltip says what
            g.fill(x + SHOW_W - 5, y + 2, x + SHOW_W - 2, y + 5, state() == State.CHECKING ? Ui.AMBER : Ui.RED);
        }
        // stored power, filling from the bottom
        int bx = leftPos + BAR_X, by = topPos + BAR_Y;
        Ui.screen(g, bx, by, BAR_W, BAR_H);
        int filled = Math.round(BAR_H * menu.data.get(ArenaBlockEntity.POWER_SHOWN) / 1000f);
        g.fill(bx, by + BAR_H - filled, bx + BAR_W, by + BAR_H, Ui.TEAL);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        if (!menu.getSlot(ArenaBlockEntity.MODEL).hasItem() && Ui.inside(mx, my, ArenaMenu.MODEL_X, ArenaMenu.MODEL_Y, 16, 16)) {
            lines.add(Component.translatable("residual.agent.model_slot").getVisualOrderText());
            lines.addAll(font.split(Component.translatable("residual.arena.model_hint").withStyle(ChatFormatting.GRAY), 200));
        } else if (!menu.getSlot(ArenaBlockEntity.LOG).hasItem() && Ui.inside(mx, my, ArenaMenu.LOG_X, ArenaMenu.LOG_Y, 16, 16)) {
            lines.add(Component.translatable("item.residual.mob_log").getVisualOrderText());
            lines.addAll(font.split(Component.translatable("residual.arena.log_hint").withStyle(ChatFormatting.GRAY), 200));
        } else if (Ui.inside(mx, my, BAR_X, BAR_Y, BAR_W, BAR_H)) {
            lines.add(Component.translatable("residual.arena.power", Ui.count(menu.data.get(ArenaBlockEntity.POWER_SHOWN) * (long) ArenaBlockEntity.POWER / 1000),
                    Ui.count(ArenaBlockEntity.POWER)).getVisualOrderText());
            lines.add(Component.translatable("residual.arena.power_rate", Ui.count(menu.data.get(ArenaBlockEntity.RATE))).withStyle(ChatFormatting.GRAY).getVisualOrderText());
        } else if (Ui.inside(mx, my, SHOW_X, SHOW_Y, SHOW_W, SHOW_H)) {
            shownLines(lines);
        }
        if (!lines.isEmpty()) {
            g.setTooltipForNextFrame(lines, mouseX, mouseY);
        }
    }

    /** What the display stands for: how the model does, what that earns, and what it runs at. */
    private void shownLines(List<FormattedCharSequence> lines) {
        State state = state();
        ItemStack log = menu.getSlot(ArenaBlockEntity.LOG).getItem();
        if (!log.isEmpty()) {
            lines.add(log.getHoverName().getVisualOrderText());
        }
        int score = menu.data.get(ArenaBlockEntity.SCORE), tier = menu.data.get(ArenaBlockEntity.TIER);
        if (score >= 0) {
            lines.addAll(split(Component.translatable("residual.arena.score", String.format(Locale.ROOT, "%.1f", score / 10f),
                    Ui.count(menu.data.get(ArenaBlockEntity.LOOKS)), String.format(Locale.ROOT, "%.1f", menu.data.get(ArenaBlockEntity.PLAIN) / 10f),
                    String.format(Locale.ROOT, "%.1f", menu.data.get(ArenaBlockEntity.READ_BACK) / 10f))));
            double reach = new Imitation.Check(0, score / 1000d, menu.data.get(ArenaBlockEntity.PLAIN) / 1000d, menu.data.get(ArenaBlockEntity.READ_BACK) / 1000d).reach();
            lines.addAll(split(Component.translatable("residual.arena.reach", Math.round(reach * 100))));
            if (tier < Imitation.TIERS.length) {
                lines.addAll(split(reach >= Imitation.TIERS[tier]
                        ? Component.translatable("residual.arena.short_log", Ui.count(Imitation.LOOKS_FOR[tier]), Ui.count(menu.data.get(ArenaBlockEntity.LOOKS)))
                        : Component.translatable("residual.arena.reach_next", Math.round(Imitation.TIERS[tier] * 100))));
            }
        }
        if (tier > 0) {
            lines.add(Component.translatable("residual.arena.tier." + tier).withStyle(ChatFormatting.AQUA).getVisualOrderText());
            lines.addAll(split(Component.translatable("residual.arena.pays." + tier)));
        }
        if (menu.data.get(ArenaBlockEntity.GROUND) != 0 && menu.data.get(ArenaBlockEntity.TRUST + 1) + menu.data.get(ArenaBlockEntity.TRUST) > 0) {
            lines.addAll(split(Component.translatable("residual.arena.ground")));
            for (int i = 0; i < Ground.ORES.length(); i++) {
                lines.add(Component.translatable("residual.arena.trust", ORES[i].getHoverName(), menu.data.get(ArenaBlockEntity.TRUST + i) / 10)
                        .withStyle(ChatFormatting.GRAY).getVisualOrderText());
            }
        }
        if (state == State.READY || state == State.FULL || state == State.NO_POWER) {
            lines.addAll(split(Component.translatable("residual.arena.fights", menu.data.get(ArenaBlockEntity.RUNNING),
                    menu.data.get(ArenaBlockEntity.PADS), menu.data.get(ArenaBlockEntity.COMPUTE))));
        }
        if (state != State.READY) {
            Component why = state == State.NO_MODEL
                    ? Component.translatable(ArenaBlockEntity.PROBLEMS.get(Math.floorMod(menu.data.get(ArenaBlockEntity.PROBLEM), ArenaBlockEntity.PROBLEMS.size())))
                    : state == State.NO_ROOM ? noRoom()
                    : Component.translatable("residual.arena.state." + state.name().toLowerCase(Locale.ROOT));
            lines.addAll(font.split(why.copy().withStyle(state == State.CHECKING ? ChatFormatting.GOLD : ChatFormatting.RED), 200));
        }
    }

    /** Where the room goes wrong, or that the Arena has none in front of it. */
    private Component noRoom() {
        int dx = menu.data.get(ArenaBlockEntity.FAULT), dy = menu.data.get(ArenaBlockEntity.FAULT + 1), dz = menu.data.get(ArenaBlockEntity.FAULT + 2);
        BlockPos at = menu.pos.offset(dx - 1024, dy - 1024, dz - 1024);
        return Component.translatable("residual.arena.state.no_room", at.getX(), at.getY(), at.getZ());
    }

    private List<FormattedCharSequence> split(Component text) {
        return font.split(text.copy().withStyle(ChatFormatting.GRAY), 200);
    }

    @Override
    public void parts(Parts out) {
        out.add("log", this, menu.getSlot(ArenaBlockEntity.LOG));
        out.add("model", this, menu.getSlot(ArenaBlockEntity.MODEL));
        out.add("fights", leftPos + SHOW_X, topPos + SHOW_Y, SHOW_W, SHOW_H);
        out.group("drops", this, ArenaBlockEntity.OUT, ArenaBlockEntity.SLOTS);
        out.add("power", leftPos + BAR_X, topPos + BAR_Y, BAR_W, BAR_H);
    }
}
