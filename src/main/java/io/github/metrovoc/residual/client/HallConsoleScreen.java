package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.compute.ClusterStatus;
import io.github.metrovoc.residual.compute.HallConsoleMenu;
import io.github.metrovoc.residual.net.HallSync;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Hall Console's screen, one display: what the hall is, what it is doing, and what the largest run it could hold would
 * cost, by the sums labs plan with. Its racks stand along the bottom as columns, as on a Load Wall.
 */
public class HallConsoleScreen extends AbstractContainerScreen<HallConsoleMenu> implements Parts.Drawn {
    private static final int W = 300, H = 166, SHOW_X = 8, SHOW_Y = 18, SHOW_W = 284, SHOW_H = 140, ROW = 11, BARS_Y = 76, BARS_H = 58;

    public HallConsoleScreen(HallConsoleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, W, H);
        Ui.screen(g, leftPos + SHOW_X, topPos + SHOW_Y, SHOW_W, SHOW_H);
    }

    private void row(GuiGraphicsExtractor g, int line, Component text, int color) {
        g.text(font, text, SHOW_X + 5, SHOW_Y + 5 + line * ROW, color, false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, 8, 6, Ui.LABEL, false);
        HallSync s = menu.reading;
        if (s == null) {
            return;
        }
        row(g, 0, Component.translatable(s.formed() ? "residual.hall.formed" : "residual.hall.not_formed", s.racks(), s.pods(), s.devices()), s.formed() ? Ui.TEAL : Ui.AMBER);
        row(g, 1, Component.translatable("residual.hall.rated", ClusterStatus.flops(s.flopsRated()), ClusterStatus.bytes(s.memory())), Ui.TEXT);
        row(g, 2, Component.translatable("residual.hall.now", ClusterStatus.flops(s.flopsNow()), s.busy(), s.devices()), s.busy() > 0 ? Ui.TEAL : Ui.DIM);
        row(g, 3, Component.translatable("residual.hall.power", String.format(Locale.ROOT, "%,d", s.feNow()), String.format(Locale.ROOT, "%.2f", s.pue())), Ui.TEXT);
        row(g, 4, Component.translatable("residual.hall.largest", String.format(Locale.ROOT, "%,d", s.largest())), Ui.DIM);
        row(g, 5, Component.translatable("residual.hall.run", ClusterStatus.flops(s.runFlops()), days(s.runDays()), ClusterStatus.flops(s.runEnergy())), Ui.DIM);
        byte[] racks = s.rackLoads();
        int x0 = SHOW_X + 5, w = SHOW_W - 10;
        for (int r = 0; r < racks.length; r++) {
            int x = x0 + w * r / racks.length, x1 = x0 + w * (r + 1) / racks.length - 1;
            g.fill(x, SHOW_Y + BARS_Y, x1, SHOW_Y + BARS_Y + BARS_H, Ui.GRID);
            int filled = (racks[r] & 0xFF) * BARS_H / 255;
            g.fill(x, SHOW_Y + BARS_Y + BARS_H - filled, x1, SHOW_Y + BARS_Y + BARS_H, filled > BARS_H * 9 / 10 ? Ui.AMBER : Ui.TEAL);
        }
    }

    private static String days(double days) {
        if (!Double.isFinite(days)) {
            return "–";
        }
        return days >= 10 ? String.format(Locale.ROOT, "%,.0f", days) : String.format(Locale.ROOT, "%.2f", days);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        HallSync s = menu.reading;
        int mx = mouseX - leftPos, my = mouseY - topPos;
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (s != null && Ui.inside(mx, my, SHOW_X, SHOW_Y + 5, SHOW_W, ROW)) {
            if (s.racksDark() > 0) {
                lines.addAll(font.split(Component.translatable(s.pods() == 0 ? "residual.cluster.needs_pod" : "residual.cluster.needs_hall", s.racksDark()), 220));
            }
            if (!s.formed()) {
                lines.addAll(font.split(Component.translatable("residual.hall.how").withStyle(ChatFormatting.GRAY), 220));
            }
        } else if (s != null && Ui.inside(mx, my, SHOW_X, SHOW_Y + 5 + 3 * ROW, SHOW_W, ROW)) {
            lines.addAll(font.split(Component.translatable("residual.hall.pue").withStyle(ChatFormatting.GRAY), 220));
        } else if (s != null && Ui.inside(mx, my, SHOW_X, SHOW_Y + 5 + 4 * ROW, SHOW_W, 2 * ROW)) {
            lines.addAll(font.split(Component.translatable("residual.hall.prediction").withStyle(ChatFormatting.GRAY), 220));
        }
        if (!lines.isEmpty()) {
            g.setTooltipForNextFrame(lines, mouseX, mouseY);
        }
    }

    @Override
    public void parts(Parts out) {
        int x = leftPos + SHOW_X, y = topPos + SHOW_Y;
        out.add("reading", x, y + 5, SHOW_W, 4 * ROW);
        out.add("largest", x, y + 5 + 4 * ROW, SHOW_W, 2 * ROW);
        out.add("racks", x + 5, y + BARS_Y, SHOW_W - 10, BARS_H);
    }
}
