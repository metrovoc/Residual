package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.WreckItem;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Kinds;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.learn.Wrecks;
import io.github.metrovoc.residual.menu.CoreMenu;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.net.CoreAction;
import io.github.metrovoc.residual.net.CoreSync;
import io.github.metrovoc.residual.net.SampleResult;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.train.TrainingJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

/** The Model Core's screen: load data, train, test and save, try the model, and inspect it. */
public class CoreScreen extends AbstractContainerScreen<CoreMenu> {
    private enum Tab { DATA, TRAIN, TESTS, TRY, INSPECT }

    private static final List<String> TEMPERATURES = List.of("0", "0.5", "0.8", "1.0", "1.5");
    private static final int[] TEMPERATURE_VALUES = {0, 50, 80, 100, 150};
    private static Tab lastTab = Tab.DATA;
    private static int lastTemperature = 2;
    private static Benchmark lastBenchmark = Benchmark.REACTING;

    private Tab tab = lastTab;
    private int scroll;
    private Button train;
    private Button save;
    private Button rebuild;
    private Button testAll;
    private Cycler learningRate;
    private Cycler batchSize;
    private Cycler clip;
    private Cycler speed;
    private EditBox prompt;
    private Button generate;

    public CoreScreen(CoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 320, 212);
    }

    private static CoreSync sync() {
        return CoreView.sync;
    }

    private void send(CoreAction.Kind kind, int value) {
        ClientPacketDistributor.sendToServer(new CoreAction(menu.pos, kind, value));
    }

    @Nullable
    private Wrecks.Wreck wreck() {
        for (int i = 0; i < CoreMenu.DATA_SLOTS; i++) {
            Wrecks.Wreck w = WreckItem.wreck(menu.getSlot(i).getItem());
            if (w != null) {
                return w;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        train = save = rebuild = testAll = generate = null;
        learningRate = batchSize = clip = speed = null;
        prompt = null;
        menu.slotsShown = tab == Tab.DATA;
        int x = leftPos, y = topPos;
        switch (tab) {
            case DATA -> {
                save = addRenderableWidget(Button.builder(Component.translatable("residual.core.save"), b -> send(CoreAction.Kind.SAVE, 0))
                        .bounds(x + 198, y + 35, 116, 18).build());
                rebuild = addRenderableWidget(Button.builder(Component.translatable("residual.core.rebuild"),
                        b -> send(CoreAction.Kind.BUILD_WRECK, 0)).bounds(x + 96, y + 35, 74, 18).build());
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
            case TESTS -> {
                testAll = addRenderableWidget(Button.builder(Component.translatable("residual.core.test_all"),
                        b -> send(CoreAction.Kind.TEST, 0)).bounds(x + 6, y + 172, 110, 16)
                        .tooltip(Tooltip.create(Component.translatable("residual.tests.held_out"))).build());
                save = addRenderableWidget(Button.builder(Component.translatable("residual.core.save"), b -> send(CoreAction.Kind.SAVE, 0))
                        .bounds(x + 120, y + 172, 110, 16).build());
            }
            case TRY -> {
                List<String> names = new ArrayList<>();
                for (Benchmark a : Benchmark.values()) {
                    names.add(I18n.get("residual.benchmark." + a.id));
                }
                addRenderableWidget(new Cycler(x + 6, y + 24, 140, Component.translatable("residual.try.benchmark"), names,
                        lastBenchmark.ordinal(), i -> {
                    lastBenchmark = Benchmark.values()[i];
                    CoreView.sample = null;
                    rebuild();
                }));
                boolean typed = lastBenchmark == Benchmark.ORDERS || lastBenchmark == Benchmark.SORTING || lastBenchmark == Benchmark.READING;
                if (lastBenchmark == Benchmark.READING) {
                    addRenderableWidget(new Cycler(x + 150, y + 24, 76, Component.translatable("residual.sample.temperature"),
                            TEMPERATURES, lastTemperature, i -> lastTemperature = i));
                }
                if (typed) {
                    prompt = addRenderableWidget(new EditBox(font, x + 6, y + 44, 308, 16, Component.translatable("residual.sample.prompt")));
                    prompt.setMaxLength(lastBenchmark == Benchmark.ORDERS ? 60 : lastBenchmark == Benchmark.SORTING ? Kinds.LONGEST : 200);
                    prompt.setHint(Component.translatable(lastBenchmark == Benchmark.ORDERS ? "residual.try.order_hint"
                            : lastBenchmark == Benchmark.SORTING ? "residual.try.thing_hint" : "residual.try.text_hint").withColor(Ui.DIM));
                }
                String label = lastBenchmark == Benchmark.READING ? "residual.try.write" : typed ? "residual.try.ask" : "residual.try.example";
                generate = addRenderableWidget(Button.builder(Component.translatable(label), b -> {
                    CoreView.sampling = true;
                    CoreView.sample = null;
                    int value = lastBenchmark.ordinal() * 1000 + TEMPERATURE_VALUES[lastTemperature];
                    ClientPacketDistributor.sendToServer(new CoreAction(menu.pos, CoreAction.Kind.SAMPLE, value,
                            prompt == null ? "" : prompt.getValue()));
                }).bounds(x + 230, y + 24, 84, 16).build());
            }
            case INSPECT -> {
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

    /** Brings the widgets in line with the latest state from the server. */
    private void refresh() {
        CoreSync s = sync();
        boolean running = s != null && s.running();
        boolean busy = s != null && s.busy();
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
        if (save != null) {
            save.active = s != null && !busy && menu.getSlot(CoreMenu.CRYSTAL).getItem().getItem() instanceof CrystalItem;
        }
        if (testAll != null) {
            testAll.active = s != null && !busy;
        }
        if (rebuild != null) {
            rebuild.visible = wreck() != null;
            rebuild.active = !running;
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
                    rebuild();
                }
                return true;
            }
            tx += w + 2;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (tab == Tab.INSPECT) {
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
        if (tab == Tab.DATA) {
            for (Slot slot : menu.slots) {
                Ui.slot(g, leftPos + slot.x, topPos + slot.y);
            }
            Slot tokens = menu.getSlot(CoreMenu.TOKENIZER);
            if (!tokens.hasItem()) {
                int sx = leftPos + tokens.x, sy = topPos + tokens.y;
                g.item(new ItemStack(ModItems.TOKENIZER.get()), sx, sy);
                g.fill(sx, sy, sx + 16, sy + 16, 0xB08B8B8B);
            }
        }
        Ui.screen(g, leftPos + 6, topPos + 194, imageWidth - 12, 12);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        CoreSync s = sync();
        int mx = mouseX - leftPos, my = mouseY - topPos;
        switch (tab) {
            case DATA -> dataTab(g, s, mx, my);
            case TRAIN -> trainTab(g, s, mx, my);
            case TESTS -> testsTab(g, s, mx, my);
            case TRY -> tryTab(g);
            case INSPECT -> inspectTab(g, s);
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
        Object[] args = s.args().stream().map(a -> a.startsWith("residual.") ? Component.translatable(a) : a).toArray();
        String message = Component.translatable(s.message(), args).getString();
        String steps = I18n.get("residual.core.steps", Ui.count(s.steps()));
        int max = imageWidth - 34 - font.width(steps);
        g.text(font, Ui.fit(font, message, max), 18, y, Ui.TEXT, false);
        g.text(font, steps, imageWidth - 10 - font.width(steps), y, Ui.DIM, false);
        if (font.width(message) > max && Ui.inside(mx, my, 6, 194, imageWidth - 12, 12)) {
            g.setTooltipForNextFrame(font.split(Component.literal(message), 240), mx + leftPos, my + topPos);
        }
    }

    // ---------------------------------------------------------------- data tab

    private void dataTab(GuiGraphicsExtractor g, CoreSync s, int mx, int my) {
        g.text(font, Component.translatable("residual.core.data"), 8, 24, Ui.LABEL, false);
        g.text(font, Component.translatable("residual.core.tokenizer"), CoreMenu.TOKENIZER_X - 6, 24, Ui.LABEL, false);
        if (!menu.getSlot(CoreMenu.TOKENIZER).hasItem() && Ui.inside(mx, my, CoreMenu.TOKENIZER_X, CoreMenu.DATA_Y, 16, 16)) {
            g.setTooltipForNextFrame(font.split(Component.translatable("residual.core.tokenizer_hint"), 200), mx + leftPos, my + topPos);
        }
        g.text(font, Component.translatable("residual.core.crystal"), 176, 24, Ui.LABEL, false);
        int y = 60;
        boolean any = false;
        if (s != null) {
            for (Benchmark a : Benchmark.values()) {
                if (s.practising().get(a.ordinal())) {
                    any = true;
                    Component line = Component.translatable("residual.benchmark." + a.id).withColor(0xFF1F7A72)
                            .append(Component.literal(" · ").withColor(Ui.LABEL_DIM))
                            .append(Component.translatable("residual.benchmark." + a.id + ".job").withColor(Ui.LABEL));
                    g.text(font, line, 8, y, Ui.LABEL, false);
                    y += 10;
                }
            }
        }
        if (!any) {
            for (FormattedCharSequence line : font.split(Component.translatable("residual.core.nothing_loaded"), 160)) {
                g.text(font, line, 8, y, Ui.LABEL_DIM, false);
                y += 10;
            }
        }
        Wrecks.Wreck w = wreck();
        if (w != null && s != null) {
            Component line = Component.translatable("residual.core.wreck_line", Component.translatable("residual.wreck." + w.id() + ".title"),
                    Ui.count(Math.max(0, s.budgetLeft())));
            for (FormattedCharSequence l : font.split(line, 160)) {
                g.text(font, l, 8, y + 2, 0xFFB7791F, false);
                y += 10;
            }
        }
        ItemStack crystal = menu.getSlot(CoreMenu.CRYSTAL).getItem();
        ModelInfo model = CrystalItem.model(crystal);
        int cy = 60;
        if (crystal.isEmpty()) {
            for (FormattedCharSequence line : font.split(Component.translatable("residual.core.crystal_hint"), 136)) {
                g.text(font, line, 176, cy, Ui.LABEL_DIM, false);
                cy += 10;
            }
        } else if (model == null) {
            g.text(font, Component.translatable("residual.crystal.empty"), 176, cy, Ui.LABEL_DIM, false);
        } else {
            int passed = 0;
            for (Benchmark a : Benchmark.values()) {
                passed += model.passes(a) ? 1 : 0;
            }
            g.text(font, Component.translatable("residual.core.model_tag", model.tag()), 176, cy, Ui.LABEL, false);
            g.text(font, Component.translatable("residual.core.model_parameters", Ui.count(model.parameters())), 176, cy + 10,
                    Ui.LABEL_DIM, false);
            g.text(font, Component.translatable("residual.core.model_steps", Ui.count(model.steps())), 176, cy + 20,
                    Ui.LABEL_DIM, false);
            g.text(font, Component.translatable("residual.core.model_passes", passed, Benchmark.values().length), 176, cy + 30,
                    passed > 0 ? 0xFF2E7D32 : Ui.LABEL_DIM, false);
        }
    }

    // ---------------------------------------------------------------- train tab

    private void trainTab(GuiGraphicsExtractor g, CoreSync s, int mx, int my) {
        Wrecks.Wreck w = wreck();
        chart(g, s, w, 6, 22, 202, 112, mx, my);

        Ui.screen(g, 214, 22, 100, 112);
        int y = 26;
        y = metric(g, y, "residual.metric.train_loss", s == null ? "–" : Ui.num(s.trainLoss()), Ui.TEAL);
        y = metric(g, y, "residual.metric.test_loss", s == null ? "–" : Ui.num(s.valLoss()), Ui.AMBER);
        y = metric(g, y, "residual.metric.accuracy", s == null || Float.isNaN(s.accuracy()) ? "–"
                : String.format(Locale.ROOT, "%.1f%%", 100 * s.accuracy()), Ui.GREEN);
        y = metric(g, y, "residual.metric.grad_norm", s == null ? "–" : Ui.num(s.gradNorm()), Ui.TEXT);
        y = metric(g, y, "residual.metric.parameters", s == null ? "–" : Ui.num(s.parameters()), Ui.TEXT);
        String threads = s == null ? "–" : s.throttled() > 0 ? I18n.get("residual.metric.threads_hot", s.threads(), s.throttled())
                : Integer.toString(s.threads());
        int ty = y;
        y = metric(g, y, "residual.metric.threads", threads, s != null && s.throttled() > 0 ? Ui.AMBER : Ui.TEXT);
        if (s != null && s.throttled() > 0 && Ui.inside(mx, my, 214, ty - 1, 100, 12)) {
            g.setTooltipForNextFrame(font.split(Component.translatable("residual.metric.hot_help"), 220), mx + leftPos, my + topPos);
        }
        if (w != null && s != null) {
            y = metric(g, y, "residual.metric.budget", Ui.count(Math.max(0, s.budgetLeft())), Ui.AMBER);
        }
        if (s != null && !s.causal()) {
            g.text(font, Component.translatable("residual.metric.leak"), 218, y + 2, Ui.RED, false);
            if (Ui.inside(mx, my, 214, y, 100, 12)) {
                g.setTooltipForNextFrame(font.split(Component.translatable("residual.metric.leak_help"), 220), mx + leftPos, my + topPos);
            }
        }

        if (w != null) {
            Component goal = Component.translatable("residual.core.wreck_goal", Component.translatable("residual.benchmark." + w.benchmark().id),
                    goal(w.benchmark(), w.target()));
            g.text(font, goal, 6, 137, Ui.LABEL, false);
            CoreSync.Score score = s == null ? CoreSync.Score.NONE : s.tests().get(w.benchmark().ordinal());
            float fraction = progress(w.benchmark(), w.target(), score);
            int bx = 12 + font.width(goal);
            Ui.bar(g, bx, 138, 208 - bx, 6, fraction, fraction >= 1 ? Ui.GREEN : 0xFF2A9D92);
        } else if (s != null) {
            List<String> names = new ArrayList<>();
            for (Benchmark a : Benchmark.values()) {
                if (s.practising().get(a.ordinal())) {
                    names.add(I18n.get("residual.benchmark." + a.id));
                }
            }
            Component line = names.isEmpty() ? Component.translatable("residual.core.nothing_short")
                    : Component.translatable("residual.core.practising", String.join(", ", names));
            g.text(font, Ui.fit(font, line.getString(), 202), 6, 137, Ui.LABEL, false);
        }
    }

    private int metric(GuiGraphicsExtractor g, int y, String key, String value, int color) {
        g.text(font, Component.translatable(key), 218, y, Ui.DIM, false);
        g.text(font, value, 310 - font.width(value), y, color, false);
        return y + 13;
    }

    private static String goal(Benchmark a, double target) {
        return a.metric == Task.Metric.LOSS ? String.format(Locale.ROOT, "≤ %.2f", target)
                : String.format(Locale.ROOT, "%d%%", Math.round(target * 100));
    }

    /** Fraction of the way from a fresh model to {@code target}. */
    private static float progress(Benchmark a, double target, CoreSync.Score s) {
        double f;
        if (a.metric == Task.Metric.ACCURACY) {
            f = Float.isNaN(s.accuracy()) ? 0 : s.accuracy() / target;
        } else {
            // a fresh model that reads by the character; a tokenizer is a head start
            double start = Math.log(Vocab.CHARACTERS);
            f = Float.isNaN(s.loss()) ? 0 : (start - s.loss()) / (start - target);
        }
        return (float) Math.max(0, Math.min(1, f));
    }

    private static boolean passes(Benchmark a, CoreSync.Score s) {
        return a.metric == Task.Metric.LOSS ? s.loss() <= a.target : s.accuracy() >= a.target;
    }

    /** The learning curve: training and test loss, and test accuracy on its own scale, against training steps. */
    private void chart(GuiGraphicsExtractor g, CoreSync s, @Nullable Wrecks.Wreck w, int x, int y, int w0, int h, int mx, int my) {
        int wd = w0;
        Ui.screen(g, x, y, wd, h);
        List<TrainingJob.Point> pts = s == null ? List.of() : s.history();
        // the scale's labels get a row above and below the curves, which start high on the left and end low on the right
        int px0 = x + 2, pw = wd - 4, py0 = y + 13, ph = h - 26;
        for (int i = 0; i <= 4; i++) {
            int gy = py0 + i * (ph - 1) / 4;
            g.fill(x + 1, gy, x + wd - 1, gy + 1, Ui.GRID);
        }
        if (pts.size() < 2) {
            Component hint = Component.translatable("residual.chart.empty");
            g.text(font, hint, x + (wd - font.width(hint)) / 2, y + h / 2 - 4, Ui.DIM, false);
            return;
        }
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        boolean anyAccuracy = false;
        for (TrainingJob.Point p : pts) {
            for (float v : new float[]{p.train(), p.val()}) {
                if (Float.isFinite(v)) {
                    lo = Math.min(lo, v);
                    hi = Math.max(hi, v);
                }
            }
            anyAccuracy |= Float.isFinite(p.accuracy());
        }
        boolean lossGoal = w != null && w.benchmark().metric == Task.Metric.LOSS;
        if (lossGoal) {
            lo = Math.min(lo, w.target());
            hi = Math.max(hi, w.target());
        }
        if (lo > hi) {
            lo = 0;
            hi = 1;
        }
        double pad = (hi - lo) * 0.08 + 1e-3;
        lo = Math.max(0, lo - pad);
        hi += pad;
        int first = pts.getFirst().step(), last = pts.getLast().step();
        int span = Math.max(1, last - first);

        if (w != null) {
            double frac = lossGoal ? (hi - w.target()) / (hi - lo) : 1 - w.target();
            int ty = py0 + (int) Math.round(frac * (ph - 1));
            for (int i = 0; i < pw; i += 4) {
                g.fill(px0 + i, ty, px0 + i + 2, ty + 1, 0xFF4F9E57);
            }
        }
        if (anyAccuracy) {
            plot(g, pts, TrainingJob.Point::accuracy, 0, 1, first, span, px0, py0, pw, ph, Ui.GREEN);
        }
        plot(g, pts, TrainingJob.Point::train, lo, hi, first, span, px0, py0, pw, ph, Ui.TEAL);
        plot(g, pts, TrainingJob.Point::val, lo, hi, first, span, px0, py0, pw, ph, Ui.AMBER);
        g.text(font, Ui.num(hi), x + 3, y + 3, Ui.DIM, false);
        g.text(font, Ui.num(lo), x + 3, y + h - 11, Ui.DIM, false);
        if (anyAccuracy) {
            g.text(font, "100%", x + wd - 3 - font.width("100%"), y + 3, 0xFF5E9E63, false);
        }
        String steps = Ui.count(last);
        g.text(font, steps, x + wd - 3 - font.width(steps), y + h - 11, Ui.DIM, false);

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
            lines.add(Component.translatable("residual.metric.test_loss").append(" " + Ui.num(best.val())).withColor(Ui.AMBER));
            if (Float.isFinite(best.accuracy())) {
                lines.add(Component.translatable("residual.metric.accuracy")
                        .append(String.format(Locale.ROOT, " %.1f%%", 100 * best.accuracy())).withColor(Ui.GREEN));
            }
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
                int d = Math.max(1, px - prevX);
                for (int cx = prevX; cx <= px; cx++) {
                    // where the line enters and leaves this column, never past the point it runs to
                    int a = prevY + (py - prevY) * (cx - prevX) / d;
                    int b = prevY + (py - prevY) * Math.min(cx + 1 - prevX, d) / d;
                    int top = Math.min(a, b), bottom = Math.max(a, b);
                    g.fill(cx, top, cx + 1, bottom + 1, color);
                }
            }
            prevX = px;
            prevY = py;
        }
    }

    // ---------------------------------------------------------------- tests tab

    private void testsTab(GuiGraphicsExtractor g, CoreSync s, int mx, int my) {
        Ui.screen(g, 6, 22, 308, 146);
        for (Benchmark a : Benchmark.values()) {
            int y = 24 + a.ordinal() * 18;
            CoreSync.Score score = s == null ? CoreSync.Score.NONE : s.tests().get(a.ordinal());
            boolean practising = s != null && s.practising().get(a.ordinal());
            boolean tested = score.step() >= 0;
            boolean pass = tested && s.causal() && passes(a, score);
            g.text(font, Component.translatable("residual.benchmark." + a.id), 10, y, practising ? Ui.TEAL : Ui.TEXT, false);
            g.text(font, Component.translatable("residual.benchmark." + a.id + ".job"), 10, y + 9, Ui.DIM, false);
            float fraction = tested ? progress(a, a.target, score) : 0;
            Ui.bar(g, 108, y + 2, 116, 6, fraction, pass ? Ui.GREEN : 0xFF2A9D92);
            String detail = tested ? I18n.get("residual.tests.tested", Ui.count(score.step())) : I18n.get("residual.tests.never");
            g.text(font, detail, 108, y + 9, Ui.DIM, false);
            String value = !tested ? "–" : a.metric == Task.Metric.LOSS ? Ui.num(score.loss())
                    : String.format(Locale.ROOT, "%.0f%%", 100 * score.accuracy());
            g.text(font, value, 268 - font.width(value), y, pass ? Ui.GREEN : Ui.TEXT, false);
            String target = goal(a, a.target);
            g.text(font, target, 268 - font.width(target), y + 9, Ui.DIM, false);
            if (pass) {
                g.text(font, "✔", 276, y, Ui.GREEN, false);
            } else if (tested && !s.causal()) {
                g.text(font, "⚠", 276, y, Ui.RED, false);
            }
            if (Ui.inside(mx, my, 6, y - 1, 308, 18)) {
                // split here rather than by the tooltip, which would wrap the lines again near the screen's edge
                List<FormattedCharSequence> lines = new ArrayList<>();
                lines.add(Component.translatable("residual.benchmark." + a.id).withColor(Ui.TEAL).getVisualOrderText());
                lines.addAll(font.split(Component.translatable("residual.benchmark." + a.id + ".test"), 220));
                if (tested && !s.causal()) {
                    lines.addAll(font.split(Component.translatable("residual.metric.leak_help").withColor(Ui.RED), 220));
                }
                g.setTooltipForNextFrame(lines, mx + leftPos, my + topPos);
            }
        }
        ItemStack crystal = menu.getSlot(CoreMenu.CRYSTAL).getItem();
        Component where = !(crystal.getItem() instanceof CrystalItem) ? Component.translatable("residual.tests.no_crystal")
                : CrystalItem.model(crystal) instanceof ModelInfo m ? Component.translatable("residual.tests.over", m.tag())
                : Component.translatable("residual.tests.into_empty");
        g.text(font, Ui.fit(font, where.getString(), 80), 234, 176, Ui.LABEL_DIM, false);
    }

    // ---------------------------------------------------------------- try tab

    private void tryTab(GuiGraphicsExtractor g) {
        boolean typed = prompt != null;
        if (!typed) {
            for (FormattedCharSequence line : font.split(Component.translatable("residual.try.example_hint." + lastBenchmark.id), 308)) {
                g.text(font, line, 6, 48, Ui.LABEL, false);
                break;
            }
        }
        Ui.screen(g, 6, 64, 220, 90);
        SampleResult r = CoreView.sample;
        if (CoreView.sampling) {
            g.text(font, Component.translatable("residual.sample.thinking"), 10, 68, Ui.DIM, false);
            return;
        }
        if (r == null) {
            int y = 68;
            for (FormattedCharSequence line : font.split(Component.translatable("residual.try.empty"), 212)) {
                g.text(font, line, 10, y, Ui.DIM, false);
                y += 10;
            }
            return;
        }
        Component shown = Component.literal(r.prompt()).withColor(Ui.DIM)
                .append(Component.literal(r.text().substring(Math.min(r.prompt().length(), r.text().length()))).withColor(Ui.TEXT));
        int y = 68;
        for (FormattedCharSequence line : font.split(shown, 212)) {
            if (y > 144) {
                break;
            }
            g.text(font, line, 10, y, Ui.TEXT, false);
            y += 10;
        }
        int ny = 158;
        Component note = r.note().getString().isEmpty() ? Component.translatable("residual.sample.legend") : r.note();
        for (FormattedCharSequence line : font.split(note, 308)) {
            if (ny > 182) {
                break;
            }
            g.text(font, line, 6, ny, Ui.LABEL, false);
            ny += 10;
        }
        if (!r.labels().isEmpty()) {
            int bx = 230, by = 66;
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
            MutableComponent text = Component.literal("⚠ ").append(issue.message());
            if (issue.pos() != null) {
                text.append(" (" + issue.pos().toShortString() + ")");
            }
            for (FormattedCharSequence line : font.split(text, 298)) {
                rows.add((gg, y) -> gg.text(font, line, 10, y, Ui.RED, false));
            }
        }
        if (!s.issues().isEmpty()) {
            rows.add((gg, y) -> { });
        }
        String summary = s.nodes().size() == 1 ? I18n.get("residual.inspect.summary_one", Ui.count(s.parameters()))
                : I18n.get("residual.inspect.summary", s.nodes().size(), Ui.count(s.parameters()));
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
            String name = n.name().isEmpty() ? (op == null ? n.op() : I18n.get("block.residual." + op.id)) : n.name();
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
}
