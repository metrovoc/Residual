package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.block.ReaderBlockEntity;
import io.github.metrovoc.residual.item.ShardItem;
import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.learn.Reading;
import io.github.metrovoc.residual.menu.ReaderMenu;
import io.github.metrovoc.residual.model.Recovered;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Shows the shard's record as the Reader works through it: lost blocks worn away, the block being read marked, and
 * restored blocks lighting up as they come back.
 */
public class ReaderScreen extends AbstractContainerScreen<ReaderMenu> {
    private static final int TEXT_X = 52, TEXT_Y = 16, TEXT_W = 196, LINES = 10;
    private static final int WORN = 0xFF4A5560;
    /** When each restored block came back, by block, to light it up for a while. */
    private final Map<Integer, Long> lit = new HashMap<>();
    private int litRecord = -1;
    private BitSet lastLost;

    public ReaderScreen(ReaderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 256, 232);
    }

    private int data(int i) {
        return menu.data.get(i);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, imageWidth, imageHeight);
        Ui.screen(g, leftPos + TEXT_X, topPos + TEXT_Y, TEXT_W, LINES * 10 + 4);
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        g.text(font, Component.translatable("residual.reader.shard"), ReaderMenu.SHARD_X - 2, ReaderMenu.SHARD_Y - 10, Ui.LABEL, false);
        g.text(font, Component.translatable("residual.agent.model_slot"), ReaderMenu.MODEL_X - 2, ReaderMenu.MODEL_Y - 10, Ui.LABEL, false);
        Recovered r = ShardItem.record(menu.shard());
        Archive.Record record = r == null ? null : r.text();
        if (record != null) {
            text(g, r, record);
        }
        ReaderBlockEntity.State state = ReaderBlockEntity.State.values()[Math.floorMod(data(ReaderBlockEntity.STATE), ReaderBlockEntity.State.values().length)];
        Component line = switch (state) {
            case NO_SHARD -> Component.translatable("residual.reader.no_shard");
            case NO_MODEL -> Component.translatable(ReaderBlockEntity.PROBLEMS.get(Math.floorMod(data(ReaderBlockEntity.PROBLEM), ReaderBlockEntity.PROBLEMS.size())));
            case PEEKS -> Component.translatable("residual.reader.peeks");
            case READING -> Component.translatable("residual.reader.reading", data(ReaderBlockEntity.BLOCK) + 1,
                    record == null ? 0 : record.blocks(), String.format(Locale.ROOT, "%.0f%%", r == null ? 0 : 100 * r.legible()));
            case WHOLE -> Component.translatable("residual.reader.whole");
            case STUCK -> Component.translatable("residual.reader.stuck");
        };
        int color = state == ReaderBlockEntity.State.NO_MODEL || state == ReaderBlockEntity.State.PEEKS ? 0xFFB3261E : Ui.LABEL;
        int y = TEXT_Y + LINES * 10 + 7;
        for (var l : font.split(line, imageWidth - TEXT_X - 8)) {
            g.text(font, l, TEXT_X, y, color, false);
            y += 9;
        }
        int acc = data(ReaderBlockEntity.ACCURACY);
        if (acc >= 0 && record != null) {
            boolean back = acc >= Math.round(Reading.RESTORE * 1000);
            Component last = Component.translatable(back ? "residual.reader.last_restored" : "residual.reader.last_lost",
                    String.format(Locale.ROOT, "%.0f%%", acc / 10f), String.format(Locale.ROOT, "%.0f%%", 100 * Reading.RESTORE));
            g.text(font, Ui.fit(font, last.getString(), imageWidth - TEXT_X - 8), TEXT_X, y, back ? 0xFF2E7D32 : Ui.LABEL_DIM, false);
        }
    }

    /** Draws the record, wrapped by hand so each character keeps its place and its block's look. */
    private void text(GuiGraphicsExtractor g, Recovered r, Archive.Record record) {
        String text = record.text();
        BitSet lost = r.lost();
        long now = minecraft.level.getGameTime();
        if (litRecord != r.record()) {
            litRecord = r.record();
            lit.clear();
            lastLost = (BitSet) lost.clone();
        } else if (lastLost != null && !lastLost.equals(lost)) {
            BitSet back = (BitSet) lastLost.clone();
            back.andNot(lost);
            for (int b = back.nextSetBit(0); b >= 0; b = back.nextSetBit(b + 1)) {
                lit.put(b, now);
            }
            lastLost = (BitSet) lost.clone();
        }
        StringBuilder shown = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            shown.append(lost.get(i / Archive.BLOCK) && c != '\n' && c != ' ' ? '▒' : c);
        }
        List<int[]> lines = wrap(shown.toString(), TEXT_W - 8);
        boolean reading = data(ReaderBlockEntity.STATE) == ReaderBlockEntity.State.READING.ordinal();
        int current = reading ? data(ReaderBlockEntity.BLOCK) : -1;
        int focus = 0;
        for (int i = 0; i < lines.size(); i++) {
            int[] l = lines.get(i);
            if (current >= 0 && l[0] <= current * Archive.BLOCK && current * Archive.BLOCK < Math.max(l[1], l[0] + 1)) {
                focus = i;
            }
        }
        int scroll = Math.max(0, Math.min(focus - LINES / 2, lines.size() - LINES));
        for (int i = 0; i < LINES && scroll + i < lines.size(); i++) {
            int[] l = lines.get(scroll + i);
            int x = TEXT_X + 4, y = TEXT_Y + 3 + i * 10;
            int from = l[0];
            while (from < l[1]) {
                int block = from / Archive.BLOCK;
                int to = Math.min(l[1], (block + 1) * Archive.BLOCK);
                String run = shown.substring(from, to);
                int color = block == current ? Ui.AMBER : lost.get(block) ? WORN : glow(lit.get(block), now);
                g.text(font, run, x, y, color, false);
                x += font.width(run);
                from = to;
            }
        }
    }

    /** Teal for a block just restored, fading back to plain text. */
    private static int glow(Long since, long now) {
        if (since == null || now - since > 60) {
            return Ui.TEXT;
        }
        float t = (now - since) / 60f;
        int r = (int) (0x3C + (0xE4 - 0x3C) * t), gr = (int) (0xD6 + (0xEB - 0xD6) * t), b = (int) (0xC6 + (0xF1 - 0xC6) * t);
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    /** Line breaks for {@code text} at word boundaries: each line as {start, end} character indices. */
    private List<int[]> wrap(String text, int width) {
        List<int[]> out = new ArrayList<>();
        int start = 0, lastSpace = -1;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                out.add(new int[]{start, i});
                start = i + 1;
                lastSpace = -1;
                continue;
            }
            if (text.charAt(i) == ' ') {
                lastSpace = i;
            }
            if (font.width(text.substring(start, i + 1)) > width) {
                int cut = lastSpace > start ? lastSpace : i;
                out.add(new int[]{start, cut});
                start = lastSpace > start ? cut + 1 : cut;
                lastSpace = -1;
            }
        }
        return out;
    }
}
