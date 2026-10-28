package io.github.metrovoc.residual.client;

import guideme.GuidesCommon;
import guideme.PageAnchor;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.learn.Curriculum;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.menu.CoreMenu;
import io.github.metrovoc.residual.net.CoreAction;
import io.github.metrovoc.residual.net.CoreSync;
import io.github.metrovoc.residual.net.SampleResult;
import io.github.metrovoc.residual.progress.Progress;
import io.github.metrovoc.residual.registry.ModAttachments;
import io.github.metrovoc.residual.train.TrainingJob;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

/** The Model Core's screen: pick a lesson, train, talk to the model, inspect it and read it as PyTorch. */
public class CoreScreen extends AbstractContainerScreen<CoreMenu> {
    private enum Tab { LESSON, TRAIN, SAMPLE, INSPECT, CODE }

    private static final List<String> TEMPERATURES = List.of("0", "0.5", "0.8", "1.0", "1.5");
    private static final int[] TEMPERATURE_VALUES = {0, 50, 80, 100, 150};
    // the lesson tab: the list of lessons on the left, the browsed one on the right
    private static final int LIST_W = 136;
    private static final int DETAIL_X = 148;
    private static final int DETAIL_W = 166;
    private static Tab lastTab = Tab.LESSON;
    private static int lastTemperature = 2;

    private Tab tab = lastTab;
    private int browsed = -1;
    private int scroll;
    /** The code the Code tab last showed: a new copy opens at the model itself rather than at the imports. */
    @Nullable
    private String shownCode;
    private Button train;
    private Button load;
    private Button build;
    private Cycler learningRate;
    private Cycler batchSize;
    private Cycler clip;
    private Cycler speed;
    private EditBox prompt;
    private Button generate;
    private Cycler temperature;

    public CoreScreen(CoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 320, 212);
    }

    private static CoreSync sync() {
        return CoreView.sync;
    }

    private void send(CoreAction.Kind kind, int value) {
        ClientPacketDistributor.sendToServer(new CoreAction(menu.pos, kind, value));
    }

    private Curriculum.Lesson current() {
        CoreSync s = sync();
        Curriculum.Lesson l = s == null ? null : Curriculum.byId(s.lesson());
        return l != null ? l : Curriculum.ALL.getFirst();
    }

    private Progress progress() {
        return minecraft.player.getData(ModAttachments.PROGRESS);
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        super.init();
        if (browsed < 0) {
            browsed = Curriculum.ALL.indexOf(current());
        }
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        train = load = build = generate = null;
        learningRate = batchSize = clip = speed = temperature = null;
        prompt = null;
        int x = leftPos, y = topPos;
        switch (tab) {
            case LESSON -> {
                load = addRenderableWidget(Button.builder(Component.translatable("residual.core.load"), b -> send(CoreAction.Kind.SELECT, browsed))
                        .bounds(x + DETAIL_X, y + 172, 64, 16).build());
                build = addRenderableWidget(Button.builder(Component.translatable("residual.core.build"), b -> send(CoreAction.Kind.BUILD_CASE, 0))
                        .bounds(x + DETAIL_X + 68, y + 172, 78, 16).build());
                addRenderableWidget(Button.builder(Component.literal("?"), b -> openGuide())
                        .bounds(x + imageWidth - 22, y + 172, 16, 16).build());
            }
            case TRAIN -> {
                train = addRenderableWidget(Button.builder(Component.empty(), b -> {
                    CoreSync s = sync();
                    send(s != null && s.running() ? CoreAction.Kind.STOP : CoreAction.Kind.START, 0);
                }).bounds(x + 6, y + 150, 64, 16).build());
                addRenderableWidget(Button.builder(Component.translatable("residual.core.reset"), b -> send(CoreAction.Kind.RESET, 0))
                        .bounds(x + 74, y + 150, 65, 16).build());
                addRenderableWidget(Button.builder(Component.translatable("residual.core.check"), b -> send(CoreAction.Kind.CHECK, 0))
                        .bounds(x + 143, y + 150, 65, 16).build());
                CoreSync s = sync();
                speed = addRenderableWidget(new Cycler(x + 214, y + 150, 100, Component.translatable("residual.core.speed"),
                        List.of(I18n.get("residual.speed.watch"), I18n.get("residual.speed.normal"), I18n.get("residual.speed.max")),
                        s == null ? 1 : s.speed(), i -> send(CoreAction.Kind.SPEED, i)));
                learningRate = addRenderableWidget(new Cycler(x + 6, y + 170, 104, Component.translatable("residual.core.learning_rate"),
                        labels(CoreBlockEntity.LEARNING_RATES), s == null ? 0 : s.learningRate(), i -> send(CoreAction.Kind.LEARNING_RATE, i)));
                batchSize = addRenderableWidget(new Cycler(x + 114, y + 170, 104, Component.translatable("residual.core.batch"),
                        labels(CoreBlockEntity.BATCH_SIZES), s == null ? 0 : s.batchSize(), i -> send(CoreAction.Kind.BATCH_SIZE, i)));
                clip = addRenderableWidget(new Cycler(x + 222, y + 170, 92, Component.translatable("residual.core.clip"),
                        clipLabels(), s == null ? 0 : s.clip(), i -> send(CoreAction.Kind.CLIP, i)));
            }
            case SAMPLE -> {
                boolean text = current().makeTask().metric == Task.Metric.LOSS;
                if (text) {
                    prompt = addRenderableWidget(new EditBox(font, x + 6, y + 24, 220, 16, Component.translatable("residual.sample.prompt")));
                    prompt.setMaxLength(200);
                    prompt.setHint(Component.translatable("residual.sample.hint").withColor(Ui.DIM));
                }
                generate = addRenderableWidget(Button.builder(Component.translatable(text ? "residual.sample.generate" : "residual.sample.puzzle"), b -> {
                    CoreView.sampling = true;
                    CoreView.sample = null;
                    ClientPacketDistributor.sendToServer(new CoreAction(menu.pos, CoreAction.Kind.SAMPLE,
                            TEMPERATURE_VALUES[lastTemperature], prompt == null ? "" : prompt.getValue()));
                }).bounds(x + 230, y + 24, 84, 16).build());
                if (text) {
                    temperature = addRenderableWidget(new Cycler(x + 230, y + 44, 84, Component.translatable("residual.sample.temperature"),
                            TEMPERATURES, lastTemperature, i -> lastTemperature = i));
                }
            }
            case INSPECT -> {
            }
            case CODE -> {
                if (CoreView.code == null) {
                    send(CoreAction.Kind.CODE, 0);
                }
                addRenderableWidget(Button.builder(Component.translatable("residual.code.refresh"), b -> {
                    CoreView.code = null;
                    send(CoreAction.Kind.CODE, 0);
                }).bounds(x + 6, y + 172, 70, 16).build());
                addRenderableWidget(Button.builder(Component.translatable("residual.code.copy"), b -> {
                    if (CoreView.code != null) {
                        minecraft.keyboardHandler.setClipboard(CoreView.code);
                    }
                }).bounds(x + 80, y + 172, 70, 16).build());
                addRenderableWidget(Button.builder(Component.translatable("residual.code.export"), b -> send(CoreAction.Kind.EXPORT, 0))
                        .bounds(x + 154, y + 172, 160, 16).build());
            }
        }
        refresh();
    }

    private static List<String> labels(float[] values) {
        List<String> out = new ArrayList<>();
        for (float v : values) {
            out.add(v >= 0.1f ? String.format(Locale.ROOT, "%.1f", v) : String.format(Locale.ROOT, "%.0e", v).replace("e-0", "e-"));
        }
        return out;
    }

    private static List<String> labels(int[] values) {
        List<String> out = new ArrayList<>();
        for (int v : values) {
            out.add(Integer.toString(v));
        }
        return out;
    }

    private static List<String> clipLabels() {
        List<String> out = new ArrayList<>();
        for (float v : CoreBlockEntity.CLIPS) {
            out.add(v == 0 ? I18n.get("residual.core.clip_off") : String.format(Locale.ROOT, "%.1f", v));
        }
        return out;
    }

    private void openGuide() {
        Curriculum.Lesson l = Curriculum.ALL.get(browsed);
        String folder = l.kind() == Curriculum.Kind.CASE ? "cases/" : "lessons/";
        GuidesCommon.openGuide(minecraft.player, Residual.GUIDE, PageAnchor.page(Residual.id(folder + l.id() + ".md")));
    }

    /** Brings the widgets in line with the latest state from the server. */
    private void refresh() {
        CoreSync s = sync();
        boolean running = s != null && s.running();
        if (train != null) {
            train.setMessage(Component.translatable(running ? "residual.core.stop" : "residual.core.train"));
            train.active = s != null;
        }
        if (learningRate != null && s != null) {
            learningRate.set(s.learningRate());
            batchSize.set(s.batchSize());
            clip.set(s.clip());
            speed.set(s.speed());
        }
        if (load != null) {
            Curriculum.Lesson l = Curriculum.ALL.get(browsed);
            boolean loaded = l == current();
            load.setMessage(Component.translatable(loaded ? "residual.core.loaded" : "residual.core.load"));
            load.active = !loaded && !running && (Curriculum.unlocked(l, progress().done()) || minecraft.player.isCreative());
            build.visible = l.kind() == Curriculum.Kind.CASE;
            build.active = loaded && !running;
        }
        if (generate != null) {
            generate.active = !CoreView.sampling;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        int tx = leftPos + 8;
        for (Tab t : Tab.values()) {
            int w = font.width(tabTitle(t)) + 10;
            if (Ui.inside(mx, my, tx - 4, topPos + 4, w, 14)) {
                if (t != tab) {
                    tab = lastTab = t;
                    scroll = 0;
                    shownCode = null;
                    rebuild();
                }
                return true;
            }
            tx += w + 2;
        }
        if (tab == Tab.LESSON && Ui.inside(mx, my, leftPos + 6, topPos + 22, LIST_W, 166)) {
            int row = (int) (my - topPos - 23) / 15;
            if (row >= 0 && row < Curriculum.ALL.size()) {
                browsed = row;
                refresh();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (tab == Tab.INSPECT || tab == Tab.CODE) {
            scroll = Math.max(0, scroll - (int) Math.signum(scrollY) * 3);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            return super.keyPressed(event);
        }
        if (prompt != null && prompt.isFocused()) {
            if (event.isConfirmation() && generate != null && generate.active) {
                generate.onPress(event);
                return true;
            }
            return prompt.keyPressed(event) || prompt.canConsumeInput() || super.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    // ---------------------------------------------------------------- drawing

    private Component tabTitle(Tab t) {
        return Component.translatable("residual.tab." + t.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, imageWidth, imageHeight);
        int tx = leftPos + 8;
        for (Tab t : Tab.values()) {
            Component title = tabTitle(t);
            int w = font.width(title) + 10;
            boolean on = t == tab;
            boolean hover = Ui.inside(mouseX, mouseY, tx - 4, topPos + 4, w, 14);
            if (on) {
                g.fill(tx - 4, topPos + 17, tx - 4 + w - 2, topPos + 19, 0xFF2A9D92);
            }
            g.text(font, title, tx, topPos + 7, on ? Ui.LABEL : hover ? 0xFF222222 : Ui.LABEL_DIM, false);
            tx += w + 2;
        }
        Ui.screen(g, leftPos + 6, topPos + 194, imageWidth - 12, 12);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        CoreSync s = sync();
        int mx = mouseX - leftPos, my = mouseY - topPos;
        switch (tab) {
            case LESSON -> lessonTab(g, mx, my);
            case TRAIN -> trainTab(g, s, mx, my);
            case SAMPLE -> sampleTab(g);
            case INSPECT -> inspectTab(g, s);
            case CODE -> codeTab(g);
        }
        statusLine(g, s, mx, my);
    }

    private void statusLine(GuiGraphicsExtractor g, CoreSync s, int mx, int my) {
        int y = 196;
        if (s == null) {
            g.text(font, Component.translatable("residual.status.connecting"), 10, y, Ui.DIM, false);
            return;
        }
        boolean running = s.running();
        int dot = running ? (minecraft.level.getGameTime() / 10 % 2 == 0 ? Ui.TEAL : 0xFF1F7A72) : s.issues().isEmpty() ? Ui.DIM : Ui.RED;
        g.fill(10, y + 2, 14, y + 6, dot);
        String message = Component.translatable(s.message(), s.args().toArray()).getString();
        String steps = stepsText(s);
        int max = imageWidth - 34 - font.width(steps);
        g.text(font, Ui.fit(font, message, max), 18, y, Ui.TEXT, false);
        g.text(font, steps, imageWidth - 10 - font.width(steps), y, Ui.DIM, false);
        if (font.width(message) > max && Ui.inside(mx, my, 6, 194, imageWidth - 12, 12)) {
            g.setTooltipForNextFrame(font.split(Component.literal(message), 240), mx + leftPos, my + topPos);
        }
    }

    private String stepsText(CoreSync s) {
        return I18n.get("residual.core.steps", Ui.count(s.steps()));
    }

    // ---------------------------------------------------------------- lesson tab

    private void lessonTab(GuiGraphicsExtractor g, int mx, int my) {
        Ui.screen(g, 6, 22, LIST_W, 166);
        Progress progress = progress();
        Curriculum.Lesson loaded = current();
        int lessonNo = 0, caseNo = 0;
        for (int i = 0; i < Curriculum.ALL.size(); i++) {
            Curriculum.Lesson l = Curriculum.ALL.get(i);
            boolean isCase = l.kind() == Curriculum.Kind.CASE;
            String number = isCase ? "C" + ++caseNo : Integer.toString(++lessonNo);
            int y = 23 + i * 15;
            if (i == browsed) {
                g.fill(7, y, 5 + LIST_W, y + 15, Ui.SELECTION);
            } else if (Ui.inside(mx, my, 7, y, LIST_W - 2, 15)) {
                g.fill(7, y, 5 + LIST_W, y + 15, 0xFF1A222A);
            }
            boolean done = progress.has(l.id());
            boolean open = Curriculum.unlocked(l, progress.done());
            int color = l == loaded ? Ui.TEAL : open ? Ui.TEXT : 0xFF56616B;
            g.text(font, number, 11, y + 4, isCase ? Ui.AMBER : Ui.DIM, false);
            String title = I18n.get("residual.lesson." + l.id() + ".title");
            g.text(font, Ui.fit(font, title, LIST_W - 31), 25, y + 4, color, false);
            String mark = done ? "✔" : l == loaded ? "▶" : "";
            g.text(font, mark, 2 + LIST_W - font.width(mark), y + 4, done ? Ui.GREEN : Ui.DIM, false);
        }

        Curriculum.Lesson l = Curriculum.ALL.get(browsed);
        int x = DETAIL_X;
        boolean isCase = l.kind() == Curriculum.Kind.CASE;
        g.text(font, Component.translatable(isCase ? "residual.core.kind_case" : "residual.core.kind_lesson"), x, 24,
                isCase ? 0xFFB7791F : 0xFF1F8A80, false);
        g.text(font, Component.translatable("residual.lesson." + l.id() + ".title"), x, 35, Ui.LABEL, false);
        int y = 49;
        for (FormattedCharSequence line : font.split(Component.translatable("residual.lesson." + l.id() + ".brief"), DETAIL_W)) {
            if (y > 140) {
                break;
            }
            g.text(font, line, x, y, 0xFF505050, false);
            y += 10;
        }
        Task task = l.makeTask();
        g.text(font, Component.translatable("residual.core.goal", task.goal()), x, 146, Ui.LABEL, false);
        String paper = "residual.lesson." + l.id() + ".paper";
        if (!Curriculum.unlocked(l, progress.done()) && !minecraft.player.isCreative()) {
            g.text(font, Ui.fit(font, I18n.get("residual.core.locked"), DETAIL_W), x, 157, 0xFFA04040, false);
        } else if (I18n.exists(paper)) {
            g.text(font, Ui.fit(font, I18n.get(paper), DETAIL_W), x, 157, Ui.LABEL_DIM, false);
        }
    }

    // ---------------------------------------------------------------- train tab

    private void trainTab(GuiGraphicsExtractor g, CoreSync s, int mx, int my) {
        Curriculum.Lesson l = current();
        Task task = l.makeTask();
        chart(g, s, task, 6, 22, 202, 112, mx, my);

        Ui.screen(g, 214, 22, 100, 112);
        int y = 26;
        y = metric(g, y, "residual.metric.train_loss", s == null ? "–" : Ui.num(s.trainLoss()), Ui.TEAL);
        y = metric(g, y, "residual.metric.val_loss", s == null ? "–" : Ui.num(s.valLoss()), Ui.AMBER);
        y = metric(g, y, "residual.metric.accuracy", s == null || Float.isNaN(s.accuracy()) ? "–"
                : String.format(Locale.ROOT, "%.1f%%", 100 * s.accuracy()), Ui.GREEN);
        y = metric(g, y, "residual.metric.grad_norm", s == null ? "–" : Ui.num(s.gradNorm()), Ui.TEXT);
        y = metric(g, y, "residual.metric.parameters", s == null ? "–" : Ui.num(s.parameters()), Ui.TEXT);
        y = metric(g, y, "residual.metric.threads", s == null ? "–" : Integer.toString(s.threads()), Ui.TEXT);
        if (l.budget() > 0 && s != null) {
            y = metric(g, y, "residual.metric.budget", Ui.count(Math.max(0, l.budget() - s.runSteps())), Ui.AMBER);
        }
        if (s != null && !s.causal()) {
            g.text(font, Component.translatable("residual.metric.leak"), 218, y + 2, Ui.RED, false);
            if (Ui.inside(mx, my, 214, y, 100, 12)) {
                g.setComponentTooltipForNextFrame(font, List.of(Component.translatable("residual.metric.leak_help")),
                        mx + leftPos, my + topPos);
            }
        }

        Component goal = Component.translatable("residual.core.goal", task.goal());
        g.text(font, goal, 6, 137, Ui.LABEL, false);
        double fraction = s == null ? 0 : task.progress(Float.isNaN(s.valLoss()) ? Math.log(task.vocab.size()) : s.valLoss(),
                Float.isNaN(s.accuracy()) ? 0 : s.accuracy());
        int bx = 12 + font.width(goal);
        Ui.bar(g, bx, 138, 208 - bx, 6, (float) fraction, fraction >= 1 ? Ui.GREEN : 0xFF2A9D92);
    }

    private int metric(GuiGraphicsExtractor g, int y, String key, String value, int color) {
        g.text(font, Component.translatable(key), 218, y, Ui.DIM, false);
        g.text(font, value, 310 - font.width(value), y, color, false);
        return y + 13;
    }

    /** The learning curve: loss (or answer accuracy for puzzles) against training steps. */
    private void chart(GuiGraphicsExtractor g, CoreSync s, Task task, int x, int y, int w, int h, int mx, int my) {
        Ui.screen(g, x, y, w, h);
        List<TrainingJob.Point> pts = s == null ? List.of() : s.history();
        boolean accuracy = task.metric == Task.Metric.ACCURACY;
        for (int i = 1; i < 4; i++) {
            g.fill(x + 1, y + i * h / 4, x + w - 1, y + i * h / 4 + 1, Ui.GRID);
        }
        if (pts.size() < 2) {
            Component hint = Component.translatable("residual.chart.empty");
            g.text(font, hint, x + (w - font.width(hint)) / 2, y + h / 2 - 4, Ui.DIM, false);
            return;
        }
        double lo, hi;
        if (accuracy) {
            lo = 0;
            hi = 1;
        } else {
            lo = Math.min(task.target, Double.MAX_VALUE);
            hi = task.target;
            for (TrainingJob.Point p : pts) {
                for (float v : new float[]{p.train(), p.val()}) {
                    if (Float.isFinite(v)) {
                        lo = Math.min(lo, v);
                        hi = Math.max(hi, v);
                    }
                }
            }
            double pad = (hi - lo) * 0.08 + 1e-3;
            lo = Math.max(0, lo - pad);
            hi += pad;
        }
        int first = pts.getFirst().step(), last = pts.getLast().step();
        int span = Math.max(1, last - first);
        int px0 = x + 2, pw = w - 4, py0 = y + 2, ph = h - 4;

        int ty = py0 + (int) Math.round((hi - task.target) / (hi - lo) * (ph - 1));
        for (int i = 0; i < pw; i += 4) {
            g.fill(px0 + i, ty, px0 + i + 2, ty + 1, 0xFF4F9E57);
        }
        if (accuracy) {
            plot(g, pts, p -> p.accuracy(), lo, hi, first, span, px0, py0, pw, ph, Ui.GREEN);
        } else {
            plot(g, pts, p -> p.train(), lo, hi, first, span, px0, py0, pw, ph, Ui.TEAL);
            plot(g, pts, p -> p.val(), lo, hi, first, span, px0, py0, pw, ph, Ui.AMBER);
        }
        g.text(font, Ui.num(hi), x + 3, y + 3, Ui.DIM, false);
        g.text(font, Ui.num(lo), x + 3, y + h - 11, Ui.DIM, false);
        String steps = Ui.count(last);
        g.text(font, steps, x + w - 3 - font.width(steps), y + h - 11, Ui.DIM, false);

        if (Ui.inside(mx, my, px0, py0, pw, ph)) {
            int target = first + (int) ((long) (mx - px0) * span / Math.max(1, pw - 1));
            TrainingJob.Point best = pts.getFirst();
            for (TrainingJob.Point p : pts) {
                if (Math.abs(p.step() - target) < Math.abs(best.step() - target)) {
                    best = p;
                }
            }
            int cx = px0 + (int) ((long) (best.step() - first) * (pw - 1) / span);
            g.fill(cx, py0, cx + 1, py0 + ph, 0x60FFFFFF);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("residual.core.steps", Ui.count(best.step())));
            lines.add(Component.translatable("residual.metric.train_loss").append(" " + Ui.num(best.train())).withColor(Ui.TEAL));
            lines.add(Component.translatable("residual.metric.val_loss").append(" " + Ui.num(best.val())).withColor(Ui.AMBER));
            lines.add(Component.translatable("residual.metric.accuracy")
                    .append(String.format(Locale.ROOT, " %.1f%%", 100 * best.accuracy())).withColor(Ui.GREEN));
            g.setComponentTooltipForNextFrame(font, lines, mx + leftPos, my + topPos);
        }
    }

    private interface Series {
        float of(TrainingJob.Point p);
    }

    /** A 1px polyline: each column is filled between the values it spans, so steep drops stay connected. */
    private static void plot(GuiGraphicsExtractor g, List<TrainingJob.Point> pts, Series series, double lo, double hi,
                             int first, int span, int x, int y, int w, int h, int color) {
        int prevX = -1, prevY = 0;
        for (TrainingJob.Point p : pts) {
            float v = series.of(p);
            if (!Float.isFinite(v)) {
                continue;
            }
            int px = x + (int) ((long) (p.step() - first) * (w - 1) / span);
            int py = y + (int) Math.round((hi - Math.max(lo, Math.min(hi, v))) / (hi - lo) * (h - 1));
            if (prevX < 0) {
                g.fill(px, py, px + 1, py + 1, color);
            } else {
                for (int cx = prevX; cx <= px; cx++) {
                    int a = prevY + (py - prevY) * (cx - prevX) / Math.max(1, px - prevX);
                    int b = prevY + (py - prevY) * (cx + 1 - prevX) / Math.max(1, px - prevX);
                    int top = Math.min(a, b), bottom = Math.max(a, b);
                    g.fill(cx, top, cx + 1, bottom + 1, color);
                }
            }
            prevX = px;
            prevY = py;
        }
    }

    // ---------------------------------------------------------------- sample tab

    private void sampleTab(GuiGraphicsExtractor g) {
        boolean text = prompt != null;
        if (!text) {
            g.text(font, Component.translatable("residual.sample.puzzle_hint"), 6, 28, Ui.LABEL, false);
        }
        Ui.screen(g, 6, 44, 220, 110);
        SampleResult r = CoreView.sample;
        if (CoreView.sampling) {
            g.text(font, Component.translatable("residual.sample.thinking"), 10, 48, Ui.DIM, false);
            return;
        }
        if (r == null) {
            int y = 48;
            for (FormattedCharSequence line : font.split(Component.translatable(text ? "residual.sample.empty" : "residual.sample.empty_puzzle"), 212)) {
                g.text(font, line, 10, y, Ui.DIM, false);
                y += 10;
            }
            return;
        }
        Component shown = Component.literal(r.prompt()).withColor(Ui.DIM)
                .append(Component.literal(r.text().substring(Math.min(r.prompt().length(), r.text().length()))).withColor(Ui.TEXT));
        int y = 48;
        for (FormattedCharSequence line : font.split(shown, 212)) {
            if (y > 144) {
                break;
            }
            g.text(font, line, 10, y, Ui.TEXT, false);
            y += 10;
        }
        int ny = 158;
        Component note = r.note().isEmpty() ? Component.translatable("residual.sample.legend") : Component.literal(r.note());
        for (FormattedCharSequence line : font.split(note, 308)) {
            g.text(font, line, 6, ny, Ui.LABEL, false);
            ny += 10;
        }
        if (!r.labels().isEmpty()) {
            int bx = 230, by = text ? 66 : 46;
            g.text(font, Component.translatable("residual.sample.next"), bx, by, Ui.LABEL_DIM, false);
            for (int i = 0; i < r.labels().size(); i++) {
                int ry = by + 12 + i * 14;
                g.text(font, r.labels().get(i), bx, ry + 1, Ui.LABEL, false);
                Ui.bar(g, bx + 12, ry + 2, 48, 6, r.probs().get(i), Ui.TEAL);
                String pct = String.format(Locale.ROOT, "%.0f%%", 100 * r.probs().get(i));
                g.text(font, pct, bx + 84 - font.width(pct), ry + 1, Ui.LABEL, false);
            }
        }
    }

    @Override
    public void removed() {
        super.removed();
        CoreView.clear();
    }

    // ---------------------------------------------------------------- inspect tab

    private void inspectTab(GuiGraphicsExtractor g, CoreSync s) {
        Ui.screen(g, 6, 22, 308, 166);
        if (s == null) {
            return;
        }
        List<Row> rows = new ArrayList<>();
        for (CoreSync.Issue issue : s.issues()) {
            String where = issue.pos() == null ? "" : " (" + issue.pos().toShortString() + ")";
            for (FormattedCharSequence line : font.split(Component.literal("⚠ " + issue.message() + where), 298)) {
                rows.add((gg, y) -> gg.text(font, line, 10, y, Ui.RED, false));
            }
        }
        if (!s.issues().isEmpty()) {
            rows.add((gg, y) -> { });
        }
        String summary = I18n.get("residual.inspect.summary", s.nodes().size(), Ui.count(s.parameters()));
        rows.add((gg, y) -> gg.text(font, summary, 10, y, Ui.DIM, false));
        rows.add((gg, y) -> {
            gg.text(font, Component.translatable("residual.inspect.machine"), 10, y, Ui.DIM, false);
            gg.text(font, Component.translatable("residual.inspect.output"), 128, y, Ui.DIM, false);
            gg.text(font, Component.translatable("residual.inspect.params"), 212, y, Ui.DIM, false);
            gg.text(font, Component.translatable("residual.inspect.gradient"), 254, y, Ui.DIM, false);
        });
        double maxLog = -6;
        for (CoreSync.Node n : s.nodes()) {
            if (n.gradNorm() > 0) {
                maxLog = Math.max(maxLog, Math.log10(n.gradNorm()));
            }
        }
        double top = maxLog;
        for (CoreSync.Node n : s.nodes()) {
            Op op = Op.byId(n.op());
            String name = n.name().isEmpty() ? (op == null ? n.op() : op.title) : n.name();
            rows.add((gg, y) -> {
                int color = n.active() ? Ui.TEXT : 0xFF56616B;
                gg.text(font, Ui.fit(font, name, 114), 10, y, color, false);
                gg.text(font, Ui.fit(font, n.shape(), 78), 128, y, n.active() ? Ui.TEAL : 0xFF56616B, false);
                String params = n.parameters() == 0 ? "" : Ui.count(n.parameters());
                gg.text(font, params, 248 - font.width(params), y, color, false);
                if (n.gradNorm() > 0) {
                    float f = (float) ((Math.log10(n.gradNorm()) - (top - 6)) / 6);
                    Ui.bar(gg, 254, y + 1, 54, 6, f, f < 0.25f ? Ui.RED : Ui.AMBER);
                } else if (n.parameters() > 0) {
                    gg.text(font, "–", 254, y, Ui.DIM, false);
                }
            });
        }
        int visible = 15;
        scroll = Math.min(scroll, Math.max(0, rows.size() - visible));
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            rows.get(scroll + i).draw(g, 26 + i * 11);
        }
    }

    private interface Row {
        void draw(GuiGraphicsExtractor g, int y);
    }

    // ---------------------------------------------------------------- code tab

    private void codeTab(GuiGraphicsExtractor g) {
        Ui.screen(g, 6, 22, 308, 146);
        String code = CoreView.code;
        if (code == null) {
            g.text(font, Component.translatable("residual.code.loading"), 10, 26, Ui.DIM, false);
            return;
        }
        if (code.isEmpty()) {
            g.text(font, Component.translatable("residual.code.broken"), 10, 26, Ui.RED, false);
            return;
        }
        String[] lines = code.split("\n", -1);
        if (code != shownCode) {
            shownCode = code;
            scroll = Math.max(0, Arrays.asList(lines).indexOf("class ResidualModel(nn.Module):"));
        }
        int visible = 12;
        scroll = Math.min(scroll, Math.max(0, lines.length - visible));
        for (int i = 0; i < visible && scroll + i < lines.length; i++) {
            String line = lines[scroll + i];
            String trimmed = line.strip();
            int color = trimmed.startsWith("#") ? Ui.DIM
                    : trimmed.startsWith("def ") || trimmed.startsWith("class ") ? Ui.TEAL
                    : trimmed.startsWith("import ") || trimmed.startsWith("from ") ? Ui.AMBER : Ui.TEXT;
            g.text(font, Ui.fit(font, line.replace("\t", "    "), 300), 10, 25 + i * 11, color, false);
        }
        String where = I18n.get("residual.code.lines", scroll + 1, Math.min(lines.length, scroll + visible), lines.length);
        g.text(font, where, imageWidth - 10 - font.width(where), 158, Ui.DIM, false);
    }
}
