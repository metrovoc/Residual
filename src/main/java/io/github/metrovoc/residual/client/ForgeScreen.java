package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.block.ForgeBlockEntity;
import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.TokenizerItem;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.menu.ForgeMenu;
import io.github.metrovoc.residual.model.Recovered;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Watch a vocabulary being learned: the size to learn up to, how much text each token now carries, a passage of the
 * drive's text cut into tokens, and the latest merges.
 */
public class ForgeScreen extends AbstractContainerScreen<ForgeMenu> {
    private static final int SIZES_X = 100, SIZES_Y = 28, SIZE_W = 28, SIZE_GAP = 2, SIZE_H = 12;
    private static final int START_X = 192, START_Y = 46, START_W = 56;
    private static final int PANEL_Y = 78, PANEL_H = 60, SAMPLE_X = 8, SAMPLE_W = 158, MERGES_X = 172, MERGES_W = 76;
    private static final int LINE = 10;
    /** Token backgrounds, taken in turn so neighbouring tokens stand apart. */
    private static final int[] CHIPS = {0xFF2F5D7C, 0xFF5E4B86, 0xFF2D6E62, 0xFF7C5A2A, 0xFF7A3E55};

    /** Something under the mouse that explains itself. */
    private record Hit(int x, int y, int w, int h, List<Component> lines) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private List<Recovered> sampled = List.of();
    private String sample = "";
    private Vocab encodedWith;
    private int[] tokens = new int[0];

    public ForgeScreen(ForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 256, 234);
        inventoryLabelY = ForgeMenu.INVENTORY_Y - 10;
        inventoryLabelX = ForgeMenu.INVENTORY_X;
    }

    private int state() {
        return menu.data.get(ForgeBlockEntity.STATE);
    }

    private ItemStack card() {
        return menu.slotItem(ForgeBlockEntity.CARD);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        ghost(g, ForgeBlockEntity.TEXT, new ItemStack(ModItems.ARCHIVE_DRIVE.get()));
        ghost(g, ForgeBlockEntity.CARD, new ItemStack(ModItems.TOKENIZER.get()));
        // text runs from the drive into the tokenizer while it learns
        int x0 = leftPos + ForgeMenu.TEXT_X + 22, x1 = leftPos + ForgeMenu.CARD_X - 6, y = topPos + ForgeMenu.SLOT_Y + 8;
        g.fill(x0, y, x1, y + 1, Ui.LABEL_DIM);
        if (state() == ForgeBlockEntity.LEARNING) {
            int run = x1 - x0 - 4;
            int at = x0 + (int) (minecraft.level.getGameTime() * 2 % run);
            g.fill(at, y - 1, at + 4, y + 2, 0xFF1F9A8C);
        }
        g.fill(x1, y - 2, x1 + 1, y + 3, Ui.LABEL_DIM);
        g.fill(x1 + 1, y - 1, x1 + 2, y + 2, Ui.LABEL_DIM);
        int target = menu.data.get(ForgeBlockEntity.TARGET);
        for (int i = 0; i < ForgeBlockEntity.SIZES.length; i++) {
            int bx = leftPos + SIZES_X + i * (SIZE_W + SIZE_GAP), by = topPos + SIZES_Y;
            boolean hover = Ui.inside(mouseX, mouseY, bx, by, SIZE_W, SIZE_H);
            g.fill(bx, by, bx + SIZE_W, by + SIZE_H, i == target ? Ui.TEAL : hover ? 0xFFDADADA : 0xFF8B8B8B);
        }
        if (TokenizerItem.vocab(card()).merges() > 0) {
            boolean hover = Ui.inside(mouseX, mouseY, leftPos + START_X, topPos + START_Y, START_W, SIZE_H);
            g.fill(leftPos + START_X, topPos + START_Y, leftPos + START_X + START_W, topPos + START_Y + SIZE_H,
                    hover ? 0xFFDADADA : 0xFF8B8B8B);
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
        hits.clear();
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Ui.LABEL, false);
        g.text(font, Component.translatable("residual.forge.vocabulary"), SIZES_X, SIZES_Y - 10, Ui.LABEL, false);
        int target = menu.data.get(ForgeBlockEntity.TARGET);
        for (int i = 0; i < ForgeBlockEntity.SIZES.length; i++) {
            String size = Integer.toString(ForgeBlockEntity.SIZES[i]);
            int bx = SIZES_X + i * (SIZE_W + SIZE_GAP);
            g.text(font, size, bx + (SIZE_W - font.width(size)) / 2, SIZES_Y + 2, i == target ? 0xFF0B2A27 : Ui.LABEL, false);
        }
        hits.add(new Hit(SIZES_X, SIZES_Y, ForgeBlockEntity.SIZES.length * (SIZE_W + SIZE_GAP), SIZE_H,
                List.of(Component.translatable("residual.forge.vocabulary_hint", Vocab.CHARACTERS))));
        slotHint(ForgeBlockEntity.TEXT, "residual.forge.text_hint");
        slotHint(ForgeBlockEntity.CARD, "residual.forge.card_hint");

        ItemStack card = card();
        Vocab vocab = TokenizerItem.vocab(card);
        int state = state();
        int bar = state == ForgeBlockEntity.IDLE || card.isEmpty() ? 0
                : Math.round(240f * (vocab.size() - Vocab.CHARACTERS) / (ForgeBlockEntity.SIZES[target] - Vocab.CHARACTERS));
        g.fill(8, 42, 248, 44, Ui.GRID);
        g.fill(8, 42, 8 + Math.max(0, Math.min(240, bar)), 44, state == ForgeBlockEntity.DONE ? 0xFF2E7D32 : 0xFF1F9A8C);
        if (!card.isEmpty()) {
            Component line = Component.translatable("residual.forge.tokens", vocab.size());
            if (state != ForgeBlockEntity.IDLE) {
                line = line.copy().append(Component.translatable("residual.forge.each",
                        String.format(Locale.ROOT, "%.2f", menu.data.get(ForgeBlockEntity.CHARS) / 100f)));
            }
            g.text(font, line, 8, 48, Ui.LABEL, false);
        }
        int color = switch (state) {
            case ForgeBlockEntity.LEARNING -> 0xFF1F7A72;
            case ForgeBlockEntity.DONE -> 0xFF2E7D32;
            case ForgeBlockEntity.EXHAUSTED -> 0xFF9A5B00;
            default -> Ui.LABEL_DIM;
        };
        g.text(font, Ui.fit(font, Component.translatable("residual.forge.state." + state).getString(), 240), 8, 59, color, false);
        if (vocab.merges() > 0) {
            Component start = Component.translatable("residual.forge.start_over");
            g.text(font, start, START_X + (START_W - font.width(start)) / 2, START_Y + 2, Ui.LABEL, false);
            hits.add(new Hit(START_X, START_Y, START_W, SIZE_H, List.of(Component.translatable("residual.forge.start_over_hint"))));
        }

        g.text(font, Component.translatable("residual.forge.sample"), SAMPLE_X, PANEL_Y - 10, Ui.LABEL, false);
        g.text(font, Component.translatable("residual.forge.merges"), MERGES_X, PANEL_Y - 10, Ui.LABEL, false);
        Ui.screen(g, SAMPLE_X, PANEL_Y, SAMPLE_W, PANEL_H);
        Ui.screen(g, MERGES_X, PANEL_Y, MERGES_W, PANEL_H);
        sample(g, vocab);
        merges(g, vocab);
    }

    private void slotHint(int index, String key) {
        Slot slot = menu.getSlot(index);
        if (!slot.hasItem()) {
            hits.add(new Hit(slot.x, slot.y, 16, 16, List.of(Component.translatable(key))));
        }
    }

    /** A passage of the drive's text, each token on its own colour. */
    private void sample(GuiGraphicsExtractor g, Vocab vocab) {
        List<Recovered> records = DriveItem.records(menu.slotItem(ForgeBlockEntity.TEXT));
        if (records != sampled) {
            sampled = records;
            sample = passage(records);
            encodedWith = null;
        }
        if (sample.isEmpty()) {
            int y = PANEL_Y + 4;
            for (FormattedCharSequence line : font.split(Component.translatable("residual.forge.no_text"), SAMPLE_W - 8)) {
                g.text(font, line, SAMPLE_X + 4, y, Ui.DIM, false);
                y += LINE;
            }
            return;
        }
        if (!vocab.equals(encodedWith)) {
            encodedWith = vocab;
            tokens = vocab.encode(sample);
        }
        int left = SAMPLE_X + 3, right = SAMPLE_X + SAMPLE_W - 3, x = left, y = PANEL_Y + 3;
        for (int i = 0; i < tokens.length && y + 9 <= PANEL_Y + PANEL_H; i++) {
            String piece = vocab.piece(tokens[i]);
            boolean newline = piece.equals("\n");
            String shown = newline ? "⏎" : piece;
            int w = font.width(shown);
            if (x + w > right && x > left) {
                x = left;
                y += LINE;
                if (y + 9 > PANEL_Y + PANEL_H) {
                    break;
                }
            }
            g.fill(x, y - 1, x + w, y + 9, CHIPS[i % CHIPS.length]);
            g.text(font, shown, x, y, newline ? Ui.DIM : Ui.TEXT, false);
            hits.add(new Hit(x, y - 1, w, 10, explain(vocab, tokens[i])));
            x += w;
            if (newline) {
                x = left;
                y += LINE;
            }
        }
    }

    /** The newest merges first, each as the two tokens it joined. */
    private void merges(GuiGraphicsExtractor g, Vocab vocab) {
        if (vocab.merges() == 0) {
            int y = PANEL_Y + 4;
            for (FormattedCharSequence line : font.split(Component.translatable("residual.forge.no_merges"), MERGES_W - 8)) {
                g.text(font, line, MERGES_X + 4, y, Ui.DIM, false);
                y += LINE;
            }
            return;
        }
        int[] m = vocab.mergeIds();
        int y = PANEL_Y + 3;
        for (int k = vocab.merges() - 1; k >= 0 && y + 9 <= PANEL_Y + PANEL_H; k--, y += LINE) {
            int x = MERGES_X + 3;
            for (int part = 0; part < 2; part++) {
                String label = vocab.label(m[2 * k + part]);
                int w = Math.min(font.width(label), (MERGES_W - 8) / 2);
                g.fill(x, y - 1, x + w, y + 9, CHIPS[part]);
                g.text(font, Ui.fit(font, label, w), x, y, Ui.TEXT, false);
                x += w + 1;
            }
            hits.add(new Hit(MERGES_X + 2, y - 1, MERGES_W - 4, 10, explain(vocab, Vocab.CHARACTERS + k)));
        }
    }

    private static List<Component> explain(Vocab vocab, int id) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("residual.forge.token", vocab.label(id), id));
        if (id < Vocab.CHARACTERS) {
            lines.add(Component.translatable("residual.forge.character").withColor(Ui.DIM));
        } else {
            int k = id - Vocab.CHARACTERS;
            int[] m = vocab.mergeIds();
            lines.add(Component.translatable("residual.forge.made", k + 1, vocab.label(m[2 * k]), vocab.label(m[2 * k + 1])).withColor(Ui.DIM));
        }
        return lines;
    }

    /** The first long stretch of legible text on the drive, cut at a word near its end. */
    private static String passage(List<Recovered> records) {
        Map<Integer, BitSet> contents = new TreeMap<>();
        for (Recovered r : records) {
            contents.put(r.record(), r.lost());
        }
        String best = "";
        for (String s : Archive.legible(contents)) {
            if (s.length() > best.length()) {
                best = s;
            }
            if (best.length() >= 240) {
                break;
            }
        }
        if (best.length() <= 240) {
            return best;
        }
        int cut = best.lastIndexOf(' ', 240);
        return best.substring(0, cut > 160 ? cut : 240);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x() - leftPos, my = event.y() - topPos;
        for (int i = 0; i < ForgeBlockEntity.SIZES.length; i++) {
            if (Ui.inside(mx, my, SIZES_X + i * (SIZE_W + SIZE_GAP), SIZES_Y, SIZE_W, SIZE_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                return true;
            }
        }
        if (TokenizerItem.vocab(card()).merges() > 0 && Ui.inside(mx, my, START_X, START_Y, START_W, SIZE_H)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ForgeBlockEntity.START_OVER);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        for (Hit h : hits) {
            if (Ui.inside(mx, my, h.x(), h.y(), h.w(), h.h())) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                h.lines().forEach(c -> lines.addAll(font.split(c, 220)));
                g.setTooltipForNextFrame(lines, mouseX, mouseY);
                break;
            }
        }
    }
}
