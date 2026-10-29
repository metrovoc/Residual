package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.learn.Archive;
import io.github.metrovoc.residual.mind.Recovered;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

/** Reads a shard: its record's text, with the lost blocks shown as worn-away marks. */
public class ShardScreen extends Screen {
    private static final int W = 260;
    private static final int H = 210;
    private static final int TEXT_W = W - 28;
    private static final int LINES = 16;

    private final Recovered record;
    private List<FormattedCharSequence> lines = List.of();
    private int scroll;

    public ShardScreen(Recovered record) {
        super(Component.translatable("item.residual.archive_shard"));
        this.record = record;
    }

    @Override
    protected void init() {
        Archive.Record r = record.text();
        if (r == null) {
            lines = List.of();
            return;
        }
        String text = r.text();
        int nl = text.indexOf('\n');
        MutableComponent body = Component.empty();
        int from = nl < 0 ? text.length() : nl + 1;
        StringBuilder run = new StringBuilder();
        boolean runLost = false;
        for (int i = from; i < text.length(); i++) {
            boolean lost = record.lost().get(i / Archive.BLOCK);
            if (lost != runLost && !run.isEmpty()) {
                body.append(Component.literal(run.toString()).withColor(runLost ? 0xFF4A5560 : Ui.TEXT));
                run.setLength(0);
            }
            runLost = lost;
            char c = text.charAt(i);
            run.append(lost && c != '\n' && c != ' ' ? '▒' : c);
        }
        if (!run.isEmpty()) {
            body.append(Component.literal(run.toString()).withColor(runLost ? 0xFF4A5560 : Ui.TEXT));
        }
        lines = font.split(body, TEXT_W);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int x = (width - W) / 2, y = (height - H) / 2;
        Ui.window(g, x, y, W, H);
        Archive.Record r = record.text();
        g.text(font, Ui.fit(font, r == null ? "?" : r.title(), W - 70), x + 8, y + 7, Ui.LABEL, false);
        String legible = String.format(Locale.ROOT, "%.0f%%", 100 * record.legible());
        g.text(font, legible, x + W - 8 - font.width(legible), y + 7, record.whole() ? 0xFF2E7D32 : Ui.LABEL_DIM, false);
        Ui.screen(g, x + 7, y + 19, W - 14, H - 27);
        scroll = Math.max(0, Math.min(scroll, lines.size() - LINES));
        for (int i = 0; i < LINES && scroll + i < lines.size(); i++) {
            g.text(font, lines.get(scroll + i), x + 13, y + 24 + i * 11, Ui.TEXT, false);
        }
        if (lines.size() > LINES) {
            int track = H - 35;
            int knob = Math.max(10, track * LINES / lines.size());
            int top = y + 23 + (track - knob) * scroll / Math.max(1, lines.size() - LINES);
            g.fill(x + W - 11, top, x + W - 9, top + knob, 0xFF3B4B5A);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * 3);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
