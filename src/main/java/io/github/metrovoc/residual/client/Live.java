package io.github.metrovoc.residual.client;

import guideme.compiler.PageCompiler;
import guideme.compiler.tags.BlockTagCompiler;
import guideme.document.LytRect;
import guideme.document.block.LytBlock;
import guideme.document.block.LytBlockContainer;
import guideme.document.interaction.InteractiveElement;
import guideme.layout.LayoutContext;
import guideme.libs.mdast.mdx.model.MdxJsxElementFields;
import guideme.render.RenderContext;
import guideme.ui.GuideUiHost;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.notes.LiveMath;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;

/**
 * Widgets on guide pages that run the real math as a reader plays with them, on the engine models run on
 * ({@code notes/LiveMath}): {@code <residual:Softmax scores="2 1 0 -1" labels="a b c d" />} to drag scores and a temperature,
 * {@code <residual:Attention tokens="the old mind" points="1 0 0 1 1 1" />} to drag tokens about and see what each reads,
 * {@code <residual:Curve practice="..." test="..." />} to train a small table at the learning rate chosen. The website shows the
 * same widgets ({@code src/website/live.js}), with the same arithmetic.
 */
final class Live {
    private Live() {
    }

    private static float[] numbers(String text) {
        String[] words = text.strip().split("\\s+");
        float[] out = new float[words.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = Float.parseFloat(words[i]);
        }
        return out;
    }

    static final class SoftmaxTag extends BlockTagCompiler {
        @Override
        public Set<String> getTagNames() {
            return Set.of("residual:Softmax");
        }

        @Override
        protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
            String[] labels = el.getAttributeString("labels", "").strip().split("\\s+");
            float[] scores = numbers(el.getAttributeString("scores", ""));
            if (labels.length != scores.length) {
                parent.appendError(compiler, labels.length + " labels for " + scores.length + " scores", el);
                return;
            }
            parent.append(new SoftmaxWidget(labels, scores));
        }
    }

    static final class AttentionTag extends BlockTagCompiler {
        @Override
        public Set<String> getTagNames() {
            return Set.of("residual:Attention");
        }

        @Override
        protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
            String[] tokens = el.getAttributeString("tokens", "").strip().split("\\s+");
            float[] points = numbers(el.getAttributeString("points", ""));
            if (points.length != tokens.length * AttentionWidget.D) {
                parent.appendError(compiler, tokens.length + " tokens for " + points.length + " numbers", el);
                return;
            }
            parent.append(new AttentionWidget(tokens, points));
        }
    }

    static final class CurveTag extends BlockTagCompiler {
        @Override
        public Set<String> getTagNames() {
            return Set.of("residual:Curve");
        }

        @Override
        protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
            parent.append(new CurveWidget(el.getAttributeString("practice", ""), el.getAttributeString("test", "")));
        }
    }

    /** A dark display on a page, as wide as it wants or as the page allows. */
    private abstract static class Widget extends LytBlock implements InteractiveElement, Parts.Drawn {
        final Font font = Minecraft.getInstance().font;

        Widget() {
            setMarginTop(4);
            setMarginBottom(6);
        }

        abstract int wanted();

        abstract int height(int width);

        @Override
        protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
            int width = Math.min(wanted(), availableWidth);
            return new LytRect(x, y, width, height(width));
        }

        @Override
        protected void onLayoutMoved(int deltaX, int deltaY) {
        }

        /** The display behind it all. */
        final void display(GuiGraphicsExtractor g) {
            g.fill(bounds.x() - 1, bounds.y() - 1, bounds.right() + 1, bounds.bottom() + 1, 0xFF373737);
            g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), Ui.SCREEN);
        }

        final void text(GuiGraphicsExtractor g, String text, int x, int y, int color) {
            g.text(font, text, x, y, color, false);
        }

        final void textRight(GuiGraphicsExtractor g, String text, int right, int y, int color) {
            g.text(font, text, right - font.width(text), y, color, false);
        }

        /** A slider's line and its knob at {@code fraction}, inputs being amber. */
        final void slider(GuiGraphicsExtractor g, int x, int y, int w, float fraction) {
            g.fill(x, y + 4, x + w, y + 5, Ui.GRID);
            int at = x + Math.round((w - 3) * Math.max(0, Math.min(1, fraction)));
            g.fill(at, y, at + 3, y + 9, Ui.AMBER);
        }
    }

    /** Scores on sliders on the left, their chances as bars on the right, and the temperature under them. */
    private static final class SoftmaxWidget extends Widget {
        private static final int ROW = 13, TOP = 14, LABEL = 34, VALUE = 26, PERCENT = 26, PAD = 5;
        private static final float MOST = 6, HOTTEST = 3, COLDEST = 0.1f;
        private static final int TEMPERATURE = -1, NONE = -2;
        private final String[] labels;
        private final float[] scores;
        private float temperature = 1;
        private float[] chances;
        private int dragging = NONE;

        SoftmaxWidget(String[] labels, float[] scores) {
            this.labels = labels;
            this.scores = scores;
            chances = LiveMath.softmax(scores, temperature);
        }

        @Override
        int wanted() {
            return 270;
        }

        @Override
        int height(int width) {
            return TOP + scores.length * ROW + ROW + 3;
        }

        private int slider() {
            return (bounds.width() - 2 * PAD - LABEL - VALUE - PERCENT - 12) / 2;
        }

        private int sliderX() {
            return bounds.x() + PAD + LABEL;
        }

        private int rowY(int row) {
            return bounds.y() + TOP + (row == TEMPERATURE ? scores.length : row) * ROW;
        }

        @Override
        public void render(RenderContext context) {
            GuiGraphicsExtractor g = context.guiGraphics();
            display(g);
            int w = slider(), bar = sliderX() + w + VALUE + 12;
            text(g, I18n.get("residual.notes.score"), sliderX(), bounds.y() + 3, Ui.DIM);
            text(g, I18n.get("residual.notes.chance"), bar, bounds.y() + 3, Ui.DIM);
            for (int i = 0; i < scores.length; i++) {
                int y = rowY(i);
                text(g, Ui.fit(font, labels[i], LABEL - 3), bounds.x() + PAD, y + 1, Ui.TEXT);
                slider(g, sliderX(), y, w, (scores[i] + MOST) / (2 * MOST));
                textRight(g, String.format(Locale.ROOT, "%.1f", scores[i]), sliderX() + w + VALUE + 2, y + 1, Ui.AMBER);
                Ui.bar(g, bar, y + 2, w, 5, chances[i], Ui.TEAL);
                textRight(g, String.format(Locale.ROOT, "%.0f%%", 100 * chances[i]), bounds.right() - PAD, y + 1, Ui.TEAL);
            }
            int y = rowY(TEMPERATURE);
            g.fill(bounds.x() + 1, y - 2, bounds.right() - 1, y - 1, Ui.GRID);
            text(g, Ui.fit(font, I18n.get("residual.sample.temperature"), LABEL - 3), bounds.x() + PAD, y + 1, Ui.DIM);
            slider(g, sliderX(), y, w, (temperature - COLDEST) / (HOTTEST - COLDEST));
            textRight(g, String.format(Locale.ROOT, "%.1f", temperature), sliderX() + w + VALUE + 2, y + 1, Ui.AMBER);
        }

        private void set(int row, int x) {
            float f = Math.max(0, Math.min(1, (x - sliderX() - 1) / (float) (slider() - 3)));
            if (row == TEMPERATURE) {
                temperature = Math.round((COLDEST + f * (HOTTEST - COLDEST)) * 10) / 10f;
            } else {
                scores[row] = Math.round((-MOST + f * 2 * MOST) * 10) / 10f;
            }
            chances = LiveMath.softmax(scores, temperature);
        }

        @Override
        public boolean mouseClicked(GuideUiHost screen, int x, int y, MouseButtonInfo button, boolean doubleClick) {
            for (int row = TEMPERATURE; row < scores.length; row++) {
                if (Ui.inside(x, y, sliderX() - 2, rowY(row), slider() + 4, ROW)) {
                    dragging = row;
                    screen.captureMouse(this);
                    set(row, x);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean mouseMoved(GuideUiHost screen, int x, int y) {
            if (dragging != NONE) {
                set(dragging, x);
            }
            return dragging != NONE;
        }

        @Override
        public boolean mouseReleased(GuideUiHost screen, int x, int y, MouseButtonInfo button) {
            boolean was = dragging != NONE;
            dragging = NONE;
            return was;
        }

        @Override
        public void mouseCaptureLost() {
            dragging = NONE;
        }

        /** {@code softmax:top:LABEL} names the row with the largest chance, to check a drag from a script. */
        @Override
        public void parts(Parts out) {
            int top = 0;
            for (int i = 0; i < scores.length; i++) {
                out.add("softmax:score:" + labels[i], sliderX(), rowY(i), slider(), ROW);
                top = chances[i] > chances[top] ? i : top;
            }
            out.add("softmax:temperature", sliderX(), rowY(TEMPERATURE), slider(), ROW);
            out.add("softmax:top:" + labels[top], bounds.x(), rowY(top), bounds.width(), ROW);
        }
    }

    /**
     * A head of attention over tokens that are points in a plane. Dragging a point changes what it offers every token and what
     * it asks of them; the table shows the weights, row by row, and the hollow mark is what the chosen token reads: their
     * weighted mean.
     */
    private static final class AttentionWidget extends Widget {
        static final int D = 2;
        private static final int PLANE = 104, PAD = 5, LABEL = 30, CELL = 25, ROW = 12;
        private static final float REACH = 2.5f;
        private final String[] tokens;
        private final float[] points;
        private int chosen;
        private boolean causal = true;
        private int dragging = -1;
        private LiveMath.Attention result;

        AttentionWidget(String[] tokens, float[] points) {
            this.tokens = tokens;
            this.points = points;
            chosen = tokens.length - 1;
            compute();
        }

        private void compute() {
            result = LiveMath.attention(points, D, causal);
        }

        @Override
        int wanted() {
            return PLANE + 3 * PAD + LABEL + tokens.length * CELL;
        }

        @Override
        int height(int width) {
            return PLANE + 2 * PAD;
        }

        private int planeX() {
            return bounds.x() + PAD;
        }

        private int planeY() {
            return bounds.y() + PAD;
        }

        private int px(float v) {
            return planeX() + Math.round((v + REACH) / (2 * REACH) * (PLANE - 1));
        }

        private int py(float v) {
            return planeY() + Math.round((REACH - v) / (2 * REACH) * (PLANE - 1));
        }

        private int tableX() {
            return planeX() + PLANE + PAD + LABEL;
        }

        private int tableY() {
            return planeY() + ROW;
        }

        private int maskY() {
            return tableY() + tokens.length * ROW + 3;
        }

        @Override
        public void render(RenderContext context) {
            GuiGraphicsExtractor g = context.guiGraphics();
            display(g);
            int x0 = planeX(), y0 = planeY();
            g.fill(x0, y0, x0 + PLANE, y0 + PLANE, 0xFF0B0F13);
            g.fill(x0 + PLANE / 2, y0, x0 + PLANE / 2 + 1, y0 + PLANE, Ui.GRID);
            g.fill(x0, y0 + PLANE / 2, x0 + PLANE, y0 + PLANE / 2 + 1, Ui.GRID);
            for (int i = 0; i < tokens.length; i++) {
                int x = px(points[i * D]), y = py(points[i * D + 1]);
                g.fill(x - 2, y - 2, x + 3, y + 3, i == chosen ? Ui.AMBER : Ui.TEXT);
                text(g, tokens[i], Math.min(x + 4, x0 + PLANE - font.width(tokens[i])), Math.min(y - 4, y0 + PLANE - 9), i == chosen ? Ui.AMBER : Ui.DIM);
            }
            int rx = px(result.out()[chosen * D]), ry = py(result.out()[chosen * D + 1]);
            g.outline(rx - 3, ry - 3, 7, 7, Ui.TEAL);
            int t = tokens.length;
            for (int j = 0; j < t; j++) {
                int cx = tableX() + j * CELL;
                text(g, Ui.fit(font, tokens[j], CELL - 2), cx + 1, planeY() + 2, Ui.DIM);
            }
            for (int i = 0; i < t; i++) {
                int y = tableY() + i * ROW;
                text(g, Ui.fit(font, tokens[i], LABEL - 2), planeX() + PLANE + PAD, y + 2, i == chosen ? Ui.AMBER : Ui.DIM);
                for (int j = 0; j < t; j++) {
                    int cx = tableX() + j * CELL;
                    if (causal && j > i) {
                        g.fill(cx, y, cx + CELL - 1, y + ROW - 1, 0xFF0B0F13);
                        continue;
                    }
                    float w = result.weights()[i * t + j];
                    g.fill(cx, y, cx + CELL - 1, y + ROW - 1, Ui.heat(w));
                    text(g, String.format(Locale.ROOT, "%.2f", w), cx + 2, y + 2, w > 0.45f ? 0xFF11161B : Ui.TEXT);
                }
                if (i == chosen) {
                    g.outline(tableX() - 1, y - 1, t * CELL + 1, ROW + 1, Ui.AMBER);
                }
            }
            int my = maskY();
            g.outline(tableX(), my, 9, 9, Ui.DIM);
            if (causal) {
                g.fill(tableX() + 2, my + 2, tableX() + 7, my + 7, Ui.AMBER);
            }
            text(g, I18n.get("block.residual.causal_mask"), tableX() + 12, my + 1, Ui.DIM);
        }

        @Override
        public boolean mouseClicked(GuideUiHost screen, int x, int y, MouseButtonInfo button, boolean doubleClick) {
            for (int i = tokens.length - 1; i >= 0; i--) {
                if (Math.abs(x - px(points[i * D])) <= 4 && Math.abs(y - py(points[i * D + 1])) <= 4) {
                    chosen = i;
                    dragging = i;
                    screen.captureMouse(this);
                    return true;
                }
            }
            for (int i = 0; i < tokens.length; i++) {
                if (Ui.inside(x, y, planeX() + PLANE + PAD, tableY() + i * ROW, LABEL + tokens.length * CELL, ROW)) {
                    chosen = i;
                    return true;
                }
            }
            if (Ui.inside(x, y, tableX(), maskY(), LABEL + 40, 10)) {
                causal = !causal;
                compute();
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseMoved(GuideUiHost screen, int x, int y) {
            if (dragging < 0) {
                return false;
            }
            points[dragging * D] = Math.round(Math.max(-REACH, Math.min(REACH, (x - planeX()) / (float) (PLANE - 1) * 2 * REACH - REACH)) * 10) / 10f;
            points[dragging * D + 1] = Math.round(Math.max(-REACH, Math.min(REACH, REACH - (y - planeY()) / (float) (PLANE - 1) * 2 * REACH)) * 10) / 10f;
            compute();
            return true;
        }

        @Override
        public boolean mouseReleased(GuideUiHost screen, int x, int y, MouseButtonInfo button) {
            boolean was = dragging >= 0;
            dragging = -1;
            return was;
        }

        @Override
        public void mouseCaptureLost() {
            dragging = -1;
        }

        /** {@code attention:looks:TOKEN} names what the chosen token weighs most, to check a drag from a script. */
        @Override
        public void parts(Parts out) {
            int t = tokens.length, top = 0;
            for (int i = 0; i < t; i++) {
                out.add("attention:point:" + tokens[i], px(points[i * D]) - 2, py(points[i * D + 1]) - 2, 5, 5, i == chosen);
                out.add("attention:row:" + tokens[i], tableX(), tableY() + i * ROW, t * CELL, ROW, i == chosen);
                top = result.weights()[chosen * t + i] > result.weights()[chosen * t + top] ? i : top;
            }
            out.add("attention:plane", planeX(), planeY(), PLANE, PLANE);
            out.add("attention:mask", tableX(), maskY(), 9, 9, causal);
            out.add("attention:looks:" + tokens[top], tableX() + top * CELL, tableY() + chosen * ROW, CELL, ROW);
        }
    }

    /**
     * A table of what comes after each character, training as a Core trains: loss on the practice text and on the test as the
     * steps go by, in the Core's colours. It starts when first seen and runs only while it is on the page shown.
     */
    private static final class CurveWidget extends Widget {
        private static final float[] RATES = {1e-3f, 1e-2f, 0.1f, 1.5f};
        private static final int STEPS = 400, A_TICK = 2, CHART = 96, PAD = 4, BUTTON = 18;
        private final String practice, test;
        private int rate = 1;
        private boolean running, started;
        private long drawn;
        private LiveMath.Curve curve;
        private final float[] train = new float[STEPS], tested = new float[STEPS];
        private int done;

        CurveWidget(String practice, String test) {
            this.practice = practice;
            this.test = test;
            restart();
        }

        private void restart() {
            curve = new LiveMath.Curve(practice, test, RATES[rate]);
            done = 0;
        }

        @Override
        int wanted() {
            return 300;
        }

        @Override
        int height(int width) {
            return CHART + 12 + BUTTON + 2 * PAD;
        }

        @Override
        public void tick() {
            if (!running || System.nanoTime() - drawn > 200_000_000L) {
                return;
            }
            for (int i = 0; i < A_TICK && done < STEPS; i++) {
                float[] losses = curve.step();
                train[done] = losses[0];
                tested[done] = losses[1];
                done++;
            }
            running = done < STEPS;
        }

        private int chartX() {
            return bounds.x() + PAD;
        }

        private int chartY() {
            return bounds.y() + PAD;
        }

        private int chartW() {
            return bounds.width() - 2 * PAD;
        }

        private int buttonY() {
            return chartY() + CHART + 12;
        }

        private int runX() {
            return chartX();
        }

        private int resetX() {
            return chartX() + BUTTON + 2;
        }

        private int rateX() {
            return chartX() + 2 * BUTTON + 6;
        }

        @Override
        public void render(RenderContext context) {
            drawn = System.nanoTime();
            if (!started) {
                started = true;
                running = true;
            }
            GuiGraphicsExtractor g = context.guiGraphics();
            display(g);
            int x = chartX(), y = chartY(), w = chartW();
            g.fill(x, y, x + w, y + CHART, 0xFF0B0F13);
            for (int i = 0; i <= 4; i++) {
                int gy = y + i * (CHART - 1) / 4;
                g.fill(x, gy, x + w, gy + 1, Ui.GRID);
            }
            float hi = 0;
            for (int i = 0; i < done; i++) {
                hi = Math.max(hi, Math.max(train[i], tested[i]));
            }
            hi = Math.max(hi, 1f);
            plot(g, tested, hi, Ui.AMBER);
            plot(g, train, hi, Ui.TEAL);
            text(g, Ui.num(hi), x + 2, y + 2, Ui.DIM);
            textRight(g, I18n.get("residual.core.steps", Ui.count(done)), x + w - 2, y + CHART - 10, Ui.DIM);
            int ly = y + CHART + 2;
            int nx = legend(g, x + 2, ly, I18n.get("residual.metric.train_loss"), done == 0 ? "–" : Ui.num(train[done - 1]), Ui.TEAL);
            legend(g, nx, ly, I18n.get("residual.metric.test_loss"), done == 0 ? "–" : Ui.num(tested[done - 1]), Ui.AMBER);
            button(g, runX(), running ? "stop" : "run");
            button(g, resetX(), "reset");
            String label = I18n.get("residual.cycler", I18n.get("residual.core.learning_rate"), rateLabel());
            int rw = font.width(label) + 8;
            g.fill(rateX(), buttonY(), rateX() + rw, buttonY() + BUTTON, Ui.PANEL);
            text(g, label, rateX() + 4, buttonY() + 5, Ui.LABEL);
        }

        private String rateLabel() {
            float r = RATES[rate];
            return r >= 0.1f ? String.format(Locale.ROOT, "%.1f", r) : String.format(Locale.ROOT, "%.3f", r);
        }

        private int legend(GuiGraphicsExtractor g, int x, int y, String name, String value, int color) {
            g.fill(x, y + 2, x + 4, y + 6, color);
            text(g, name, x + 7, y, Ui.DIM);
            int vx = x + 7 + font.width(name) + 4;
            text(g, value, vx, y, color);
            return vx + font.width(value) + 12;
        }

        private void button(GuiGraphicsExtractor g, int x, String icon) {
            g.fill(x, buttonY(), x + BUTTON, buttonY() + BUTTON, Ui.PANEL);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Residual.id("icon/" + icon), x + 1, buttonY() + 1, 16, 16);
        }

        /** A 1px line: each column is filled between the values it spans, so steep drops stay connected. */
        private void plot(GuiGraphicsExtractor g, float[] values, float hi, int color) {
            // the scale's labels get a row above the curves
            int x = chartX(), y = chartY() + 12, h = CHART - 12, w = chartW(), py = -1;
            for (int i = 0; i < done; i++) {
                int px = x + i * (w - 1) / (STEPS - 1);
                int vy = y + Math.round((hi - Math.max(0, Math.min(hi, values[i]))) / hi * (h - 1));
                int from = py < 0 ? vy : Math.min(py, vy), to = py < 0 ? vy : Math.max(py, vy);
                g.fill(px, from, px + 1, to + 1, color);
                py = vy;
            }
        }

        @Override
        public boolean mouseClicked(GuideUiHost screen, int x, int y, MouseButtonInfo button, boolean doubleClick) {
            if (Ui.inside(x, y, runX(), buttonY(), BUTTON, BUTTON)) {
                if (done >= STEPS) {
                    restart();
                }
                running = !running;
                return true;
            }
            if (Ui.inside(x, y, resetX(), buttonY(), BUTTON, BUTTON)) {
                restart();
                running = false;
                return true;
            }
            if (Ui.inside(x, y, rateX(), buttonY(), 140, BUTTON)) {
                rate = (rate + 1) % RATES.length;
                restart();
                running = true;
                return true;
            }
            return false;
        }

        /** {@code curve:learned} is there once the practice loss has fallen by a tenth from where it started. */
        @Override
        public void parts(Parts out) {
            out.add("curve:run", runX(), buttonY(), BUTTON, BUTTON, running);
            out.add("curve:reset", resetX(), buttonY(), BUTTON, BUTTON);
            out.add("curve:rate", rateX(), buttonY(), 80, BUTTON);
            out.add("curve:chart", chartX(), chartY(), chartW(), CHART);
            if (done > 0 && train[done - 1] < 0.9f * train[0]) {
                out.add("curve:learned", chartX(), chartY(), chartW(), CHART);
            }
        }
    }

    /** The words a widget shows, in the export's language, as the data attributes the website's script reads them from. */
    static String[][] webText(String tag) {
        return switch (tag) {
            case "residual:Softmax" -> new String[][]{{"data-score", I18n.get("residual.notes.score")}, {"data-chance", I18n.get("residual.notes.chance")},
                    {"data-temperature", I18n.get("residual.sample.temperature")}};
            case "residual:Attention" -> new String[][]{{"data-mask", I18n.get("block.residual.causal_mask")}};
            case "residual:Curve" -> new String[][]{{"data-train-word", I18n.get("residual.metric.train_loss")}, {"data-test-word", I18n.get("residual.metric.test_loss")},
                    {"data-rate-word", I18n.get("residual.core.learning_rate")}, {"data-steps-word", I18n.get("residual.core.steps", "%s")}};
            default -> new String[0][];
        };
    }
}
