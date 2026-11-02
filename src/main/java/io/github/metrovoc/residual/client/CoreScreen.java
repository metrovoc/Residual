package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.bench.Unit;
import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.compute.Accounts;
import io.github.metrovoc.residual.compute.ClusterStatus;
import io.github.metrovoc.residual.compute.Parallelism;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.WreckItem;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Kinds;
import io.github.metrovoc.residual.learn.Recipe;
import io.github.metrovoc.residual.learn.Retrieval;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Vocab;
import io.github.metrovoc.residual.learn.Wrecks;
import io.github.metrovoc.residual.menu.CoreMenu;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.net.ComputeSync;
import io.github.metrovoc.residual.net.CoreAction;
import io.github.metrovoc.residual.net.CoreSync;
import io.github.metrovoc.residual.net.SampleResult;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.tensor.Numerics;
import io.github.metrovoc.residual.train.TrainingJob;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
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

/**
 * The Model Core's screen: load data, train, test and save, try the model, and inspect it. Training's actions and
 * settings are icon buttons in a toolbar beside the window, their values and what they do in their tooltips.
 */
public class CoreScreen extends AbstractContainerScreen<CoreMenu> implements Parts.Drawn {
    private enum Tab { DATA, TRAIN, TESTS, TRY, INSPECT, RECIPE, COMPUTE }

    private static final int WINDOW_W = 320, TOOLBAR_X = WINDOW_W + 3;

    private static final List<String> TEMPERATURES = List.of("0", "0.5", "0.8", "1.0", "1.5");
    private static final int[] TEMPERATURE_VALUES = {0, 50, 80, 100, 150};
    private static Tab lastTab = Tab.DATA;
    private static int lastTemperature = 2;
    private static Benchmark lastBenchmark = Benchmark.FORECASTING;

    private Tab tab = lastTab;
    private int scroll;
    private Button train;
    private Button save;
    private Button rebuild;
    private Button testAll;
    private IconButton learningRate;
    private IconButton batchSize;
    private IconButton clip;
    private IconButton speed;
    private IconButton schedule;
    private IconButton warmup;
    private IconButton length;
    private IconButton precision;
    private IconButton moves;
    private IconButton packing;
    private EditBox prompt;
    private Button generate;

    public CoreScreen(CoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TOOLBAR_X + IconButton.SIZE, 212);
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
        schedule = warmup = length = precision = moves = packing = null;
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
                }).bounds(x + 250, y + 160, 64, 18).build());
                // the toolbar: what to do to the model, then how it trains
                int tx = x + TOOLBAR_X, ty = y + 22, step = IconButton.SIZE + 4;
                addRenderableWidget(IconButton.action(tx, ty, "reset", Component.translatable("residual.core.reset"),
                        Component.translatable("residual.core.reset_hint"), () -> send(CoreAction.Kind.RESET, 0)));
                addRenderableWidget(IconButton.action(tx, ty + step, "check", Component.translatable("residual.core.check"),
                        Component.translatable("residual.core.check_hint"), () -> send(CoreAction.Kind.CHECK, 0)));
                CoreSync s = sync();
                ty += 2 * step + 6;
                speed = addRenderableWidget(IconButton.setting(tx, ty, List.of("speed_watch", "speed_normal", "speed_max"),
                        Component.translatable("residual.core.speed"),
                        List.of(I18n.get("residual.speed.watch"), I18n.get("residual.speed.normal"), I18n.get("residual.speed.max")),
                        s == null ? 1 : s.speed(), Component.translatable("residual.core.speed_hint"), i -> send(CoreAction.Kind.SPEED, i)));
                learningRate = addRenderableWidget(IconButton.setting(tx, ty + step, List.of("learning_rate"),
                        Component.translatable("residual.core.learning_rate"), labels(CoreBlockEntity.LEARNING_RATES),
                        s == null ? 0 : s.learningRate(), Component.translatable("residual.core.learning_rate_hint"),
                        i -> send(CoreAction.Kind.LEARNING_RATE, i)));
                batchSize = addRenderableWidget(IconButton.setting(tx, ty + 2 * step, List.of("batch"),
                        Component.translatable("residual.core.batch"), labels(CoreBlockEntity.BATCH_SIZES), s == null ? 0 : s.batchSize(),
                        Component.translatable("residual.core.batch_hint"), i -> send(CoreAction.Kind.BATCH_SIZE, i)));
                clip = addRenderableWidget(IconButton.setting(tx, ty + 3 * step, List.of("clip"), Component.translatable("residual.core.clip"),
                        clipLabels(), s == null ? 0 : s.clip(), Component.translatable("residual.core.clip_hint"),
                        i -> send(CoreAction.Kind.CLIP, i)));
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
                boolean typed = lastBenchmark == Benchmark.ORDERS || lastBenchmark == Benchmark.SORTING || lastBenchmark == Benchmark.READING
                        || lastBenchmark == Benchmark.RETRIEVAL;
                if (lastBenchmark == Benchmark.READING) {
                    addRenderableWidget(new Cycler(x + 150, y + 24, 76, Component.translatable("residual.sample.temperature"),
                            TEMPERATURES, lastTemperature, i -> lastTemperature = i));
                }
                if (typed) {
                    prompt = addRenderableWidget(new EditBox(font, x + 6, y + 44, 308, 16, Component.translatable("residual.sample.prompt")));
                    prompt.setMaxLength(lastBenchmark == Benchmark.ORDERS ? 60 : lastBenchmark == Benchmark.SORTING ? Kinds.LONGEST
                            : lastBenchmark == Benchmark.RETRIEVAL ? Retrieval.LONGEST : 200);
                    prompt.setHint(Component.translatable(lastBenchmark == Benchmark.ORDERS ? "residual.try.order_hint"
                            : lastBenchmark == Benchmark.SORTING ? "residual.try.thing_hint"
                            : lastBenchmark == Benchmark.RETRIEVAL ? "residual.try.want_hint" : "residual.try.text_hint").withColor(Ui.DIM));
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
            case RECIPE -> {
                CoreSync s = sync();
                CoreSync.Plan p = s == null ? CoreSync.Plan.NONE : s.plan();
                int tx = x + TOOLBAR_X, ty = y + 22, step = IconButton.SIZE + 4;
                schedule = addRenderableWidget(recipeSetting(tx, ty, "schedule", SCHEDULES, p.schedule(), CoreAction.Kind.SCHEDULE));
                warmup = addRenderableWidget(recipeSetting(tx, ty + step, "warmup", warmups(), p.warmup(), CoreAction.Kind.WARMUP));
                length = addRenderableWidget(recipeSetting(tx, ty + 2 * step, "length", lengths(), p.length(), CoreAction.Kind.LENGTH));
                precision = addRenderableWidget(recipeSetting(tx, ty + 3 * step, "precision", PRECISIONS, p.precision(), CoreAction.Kind.PRECISION));
                moves = addRenderableWidget(recipeSetting(tx, ty + 4 * step, "moves", MOVES, p.moves(), CoreAction.Kind.MOVES));
                packing = addRenderableWidget(recipeSetting(tx, ty + 5 * step, "packing", PACKINGS, p.packing(), CoreAction.Kind.PACKING));
            }
            case COMPUTE -> computeToolbar(x + TOOLBAR_X, y + 22);
        }
        refresh();
    }

    private IconButton recipeSetting(int x, int y, String name, List<String> values, int index, CoreAction.Kind kind) {
        return IconButton.setting(x, y, List.of(name), Component.translatable("residual.recipe." + name), values, index,
                Component.translatable("residual.recipe." + name + ".hint"), i -> send(kind, i));
    }

    private static List<String> named(String prefix, String... ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            out.add(prefix + id);
        }
        return out;
    }

    private static final List<String> SCHEDULES = named("residual.recipe.schedule.", "constant", "cosine", "wsd");
    private static final List<String> PRECISIONS = named("residual.recipe.precision.", "fp32", "bf16", "fp8", "fp4_flat", "fp4");
    private static final List<String> MOVES = named("residual.recipe.moves.", "adamw", "muon");
    private static final List<String> PACKINGS = named("residual.recipe.packing.", "windows", "packed", "masked");

    private static List<String> warmups() {
        List<String> out = new ArrayList<>();
        for (int w : Recipe.WARMUPS) {
            out.add(w == 0 ? "residual.recipe.warmup.off" : Integer.toString(w));
        }
        return out;
    }

    private static List<String> lengths() {
        List<String> out = new ArrayList<>();
        for (int l : Recipe.LENGTHS) {
            out.add(l == 0 ? "residual.recipe.length.none" : Ui.count(l));
        }
        return out;
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
        if (schedule != null && s != null) {
            CoreSync.Plan p = s.plan();
            schedule.set(p.schedule());
            warmup.set(p.warmup());
            length.set(p.length());
            precision.set(p.precision());
            moves.set(p.moves());
            packing.set(p.packing());
        }
        if (save != null) {
            ItemStack crystal = menu.getSlot(CoreMenu.CRYSTAL).getItem();
            save.active = s != null && !busy && crystal.getItem() instanceof CrystalItem;
            Component where = !(crystal.getItem() instanceof CrystalItem) ? Component.translatable("residual.tests.no_crystal")
                    : CrystalItem.model(crystal) instanceof ModelInfo m ? Component.translatable("residual.tests.over", m.tag())
                    : Component.translatable("residual.tests.into_empty");
            save.setTooltip(Tooltip.create(where));
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
        Ui.window(g, leftPos, topPos, WINDOW_W, imageHeight);
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
            // what goes in each empty slot, faded: the first free data slot, the tokenizer, the checkpoint
            for (int i = 0; i < CoreMenu.DATA_SLOTS; i++) {
                Slot data = menu.getSlot(i);
                if (!data.hasItem()) {
                    Ui.ghost(g, new ItemStack(ModItems.DATASETS.get(Benchmark.FORECASTING).get()), leftPos + data.x, topPos + data.y);
                    break;
                }
            }
            for (int i : new int[] {CoreMenu.TOKENIZER, CoreMenu.CRYSTAL}) {
                Slot slot = menu.getSlot(i);
                if (!slot.hasItem()) {
                    Ui.ghost(g, new ItemStack(i == CoreMenu.TOKENIZER ? ModItems.TOKENIZER.get() : ModItems.CHECKPOINT.get()),
                            leftPos + slot.x, topPos + slot.y);
                }
            }
        }
        Ui.screen(g, leftPos + 6, topPos + 194, WINDOW_W - 12, 12);
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
            case RECIPE -> recipeTab(g, s, mx, my);
            case COMPUTE -> computeTab(g, mx, my);
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
        Object[] args = s.args().stream().map(a -> a.startsWith("residual.") || a.startsWith("block.residual.") ? Component.translatable(a) : a).toArray();
        String message = Component.translatable(s.message(), args).getString();
        String steps = I18n.get("residual.core.steps", Ui.count(s.steps()));
        int max = WINDOW_W - 34 - font.width(steps);
        g.text(font, Ui.fit(font, message, max), 18, y, Ui.TEXT, false);
        g.text(font, steps, WINDOW_W - 10 - font.width(steps), y, Ui.DIM, false);
        if (font.width(message) > max && Ui.inside(mx, my, 6, 194, WINDOW_W - 12, 12)) {
            g.setTooltipForNextFrame(font.split(Component.literal(message), 240), mx + leftPos, my + topPos);
        }
    }

    // ---------------------------------------------------------------- data tab

    private void dataTab(GuiGraphicsExtractor g, CoreSync s, int mx, int my) {
        // an empty slot says what goes in it when pointed at
        String hint = null;
        for (int i = 0; i < CoreMenu.DATA_SLOTS; i++) {
            Slot data = menu.getSlot(i);
            if (!data.hasItem() && Ui.inside(mx, my, data.x, data.y, 16, 16)) {
                hint = "residual.core.data";
            }
        }
        if (!menu.getSlot(CoreMenu.TOKENIZER).hasItem() && Ui.inside(mx, my, CoreMenu.TOKENIZER_X, CoreMenu.DATA_Y, 16, 16)) {
            hint = "residual.core.tokenizer";
        }
        if (!menu.getSlot(CoreMenu.CRYSTAL).hasItem() && Ui.inside(mx, my, CoreMenu.CRYSTAL_X, CoreMenu.DATA_Y, 16, 16)) {
            hint = "residual.core.crystal";
        }
        if (hint != null && menu.getCarried().isEmpty()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(Component.translatable(hint).getVisualOrderText());
            lines.addAll(font.split(Component.translatable(hint + "_hint").withStyle(ChatFormatting.GRAY), 200));
            g.setTooltipForNextFrame(lines, mx + leftPos, my + topPos);
        }
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
    }

    // ---------------------------------------------------------------- train tab

    private void trainTab(GuiGraphicsExtractor g, CoreSync s, int mx, int my) {
        Wrecks.Wreck w = wreck();
        // one display: the curves, and under them the numbers they end at, in their colours
        Ui.screen(g, 6, 22, 308, 132);
        chart(g, s, w, 6, 22, 308, 118, mx, my);
        g.fill(7, 140, 313, 141, Ui.GRID);
        int x = 12, y = 144;
        x = legend(g, x, y, "residual.metric.train_loss", s == null ? "–" : Ui.num(s.trainLoss()), Ui.TEAL);
        x = legend(g, x, y, "residual.metric.test_loss", s == null ? "–" : Ui.num(s.valLoss()), Ui.AMBER);
        x = legend(g, x, y, "residual.metric.accuracy", s == null || Float.isNaN(s.accuracy()) ? "–"
                : String.format(Locale.ROOT, "%.1f%%", 100 * s.accuracy()), Ui.GREEN);
        if (s != null && !s.causal()) {
            Component leak = Component.translatable("residual.metric.leak");
            g.text(font, leak, 308 - font.width(leak), y, Ui.RED, false);
        }
        if (s != null && Ui.inside(mx, my, 6, 141, 308, 13)) {
            // how training runs, beside the numbers it gives
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(line("residual.metric.grad_norm", Ui.num(s.gradNorm())));
            lines.add(line("residual.metric.parameters", Ui.count(s.parameters())));
            lines.add(line("residual.metric.threads", s.throttled() > 0
                    ? I18n.get("residual.metric.threads_hot", s.threads(), s.throttled()) : Integer.toString(s.threads())));
            if (s.throttled() > 0) {
                lines.addAll(font.split(Component.translatable("residual.metric.hot_help").withStyle(ChatFormatting.GRAY), 220));
            }
            if (w != null) {
                lines.add(line("residual.metric.budget", Ui.count(Math.max(0, s.budgetLeft()))));
            }
            if (!s.causal()) {
                lines.addAll(font.split(Component.translatable("residual.metric.leak_help").withColor(Ui.RED), 220));
            }
            g.setTooltipForNextFrame(lines, mx + leftPos, my + topPos);
        }

        if (w != null) {
            Component goal = Component.translatable("residual.core.wreck_goal", Component.translatable("residual.benchmark." + w.benchmark().id),
                    goal(w.benchmark(), w.target()));
            g.text(font, goal, 6, 165, Ui.LABEL, false);
            CoreSync.Score score = s == null ? CoreSync.Score.NONE : s.tests().get(w.benchmark().ordinal());
            float fraction = progress(w.benchmark(), w.target(), score);
            int bx = 12 + font.width(goal);
            Ui.bar(g, bx, 166, 240 - bx, 6, fraction, fraction >= 1 ? Ui.GREEN : 0xFF2A9D92);
        } else if (s != null) {
            List<String> names = new ArrayList<>();
            for (Benchmark a : Benchmark.values()) {
                if (s.practising().get(a.ordinal())) {
                    names.add(I18n.get("residual.benchmark." + a.id));
                }
            }
            Component line = names.isEmpty() ? Component.translatable("residual.core.nothing_short")
                    : Component.translatable("residual.core.practising", String.join(", ", names));
            g.text(font, Ui.fit(font, line.getString(), 236), 6, 165, Ui.LABEL_DIM, false);
        }
    }

    /** A swatch of {@code color}, the metric's name and its value in that color; returns where the next one starts. */
    private int legend(GuiGraphicsExtractor g, int x, int y, String key, String value, int color) {
        g.fill(x, y + 2, x + 4, y + 6, color);
        Component name = Component.translatable(key);
        g.text(font, name, x + 7, y, Ui.DIM, false);
        int vx = x + 7 + font.width(name) + 4;
        g.text(font, value, vx, y, color, false);
        return vx + font.width(value) + 14;
    }

    private static FormattedCharSequence line(String key, String value) {
        return Component.translatable(key).withStyle(ChatFormatting.GRAY).append(Component.literal(" " + value).withStyle(ChatFormatting.WHITE))
                .getVisualOrderText();
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
        // a spike is drawn as a stroke up from the smoothed curve, cut off by the top of the chart
        for (TrainingJob.Point p : pts) {
            if (p.peak() > p.train() && Float.isFinite(p.train())) {
                int cx = px0 + (int) ((long) (p.step() - first) * (pw - 1) / span);
                int from = py0 + (int) Math.round((hi - Math.max(lo, Math.min(hi, p.train()))) / (hi - lo) * (ph - 1));
                int to = py0 + (int) Math.round((hi - Math.max(lo, Math.min(hi, p.peak()))) / (hi - lo) * (ph - 1));
                g.fill(cx, to, cx + 1, from, 0xA0FF6B6B);
            }
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
            if (best.peak() > best.train()) {
                lines.add(Component.translatable("residual.metric.peak").append(" " + Ui.num(best.peak())).withColor(Ui.RED));
            }
            if (Float.isFinite(best.rate())) {
                lines.add(Component.translatable("residual.metric.rate").append(" " + Ui.num(best.rate())).withColor(Ui.DIM));
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
        // a line each, spaced as widely as the display lets them be: ten at fourteen pixels
        int pitch = Math.min(15, 140 / Benchmark.values().length);
        for (Benchmark a : Benchmark.values()) {
            int top = 26 + a.ordinal() * pitch, y = top + 4;
            CoreSync.Score score = s == null ? CoreSync.Score.NONE : s.tests().get(a.ordinal());
            boolean practising = s != null && s.practising().get(a.ordinal());
            boolean tested = score.step() >= 0;
            boolean pass = tested && s.causal() && passes(a, score);
            g.text(font, Component.translatable("residual.benchmark." + a.id), 12, y, practising ? Ui.TEAL : Ui.TEXT, false);
            float fraction = tested ? progress(a, a.target, score) : 0;
            Ui.bar(g, 112, y + 1, 132, 6, fraction, pass ? Ui.GREEN : 0xFF2A9D92);
            String value = !tested ? "–" : a.metric == Task.Metric.LOSS ? Ui.num(score.loss())
                    : String.format(Locale.ROOT, "%.0f%%", 100 * score.accuracy());
            g.text(font, value, 286 - font.width(value), y, pass ? Ui.GREEN : Ui.TEXT, false);
            if (pass) {
                g.text(font, "✔", 294, y, Ui.GREEN, false);
            } else if (tested && !s.causal()) {
                g.text(font, "⚠", 294, y, Ui.RED, false);
            }
            if (Ui.inside(mx, my, 6, top, 308, pitch)) {
                // what the row leaves out: its job, when it was tested and what passes, then the test itself
                List<FormattedCharSequence> lines = new ArrayList<>();
                lines.add(Component.translatable("residual.benchmark." + a.id).withColor(Ui.TEAL).getVisualOrderText());
                lines.add(Component.translatable("residual.benchmark." + a.id + ".job").withStyle(ChatFormatting.GRAY).getVisualOrderText());
                lines.add(Component.translatable("residual.tests.passes_at", goal(a, a.target)).append(" · ")
                        .append(tested ? Component.translatable("residual.tests.tested", Ui.count(score.step()))
                                : Component.translatable("residual.tests.never")).withStyle(ChatFormatting.GRAY).getVisualOrderText());
                lines.addAll(font.split(Component.translatable("residual.benchmark." + a.id + ".test"), 220));
                if (tested && !s.causal()) {
                    lines.addAll(font.split(Component.translatable("residual.metric.leak_help").withColor(Ui.RED), 220));
                }
                g.setTooltipForNextFrame(lines, mx + leftPos, my + topPos);
            }
        }
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

    // ---------------------------------------------------------------- recipe tab

    /** The recipe: each setting a line, what it comes to in numbers a line each, the rate over the run, and the units there. */
    private void recipeTab(GuiGraphicsExtractor g, CoreSync s, int mx, int my) {
        Ui.screen(g, 6, 22, 308, 166);
        CoreSync.Plan p = s == null ? CoreSync.Plan.NONE : s.plan();
        int x = 12, y = 26;
        y = setting(g, x, y, "schedule", I18n.get(SCHEDULES.get(p.schedule())), Unit.SCHEDULER, p);
        y = setting(g, x, y, "warmup", shown(warmups().get(p.warmup())), Unit.SCHEDULER, p);
        y = setting(g, x, y, "length", shown(lengths().get(p.length())), Unit.SCHEDULER, p);
        y = setting(g, x, y, "precision", I18n.get(PRECISIONS.get(p.precision())), null, p);
        y = setting(g, x, y, "moves", I18n.get(MOVES.get(p.moves())), Unit.ORTHOGONALIZER, p);
        y = setting(g, x, y, "packing", I18n.get(PACKINGS.get(p.packing())), Unit.PACKER, p);
        y += 4;
        g.fill(x, y - 3, 154, y - 2, Ui.GRID);
        List<FormattedCharSequence> tip = null;
        for (String[] row : new String[][]{{"rate", Float.isNaN(p.rate()) ? "–" : Ui.num(p.rate())},
                {"weights", bytes(p.weightBytes()) + " / " + bytes(p.onboardBytes())},
                {"read", Float.isNaN(p.readShare()) ? "–" : String.format(Locale.ROOT, "%.0f%%", 100 * p.readShare())},
                {"spikes", Integer.toString(p.spikes())},
                {"top_score", Float.isNaN(p.topScore()) ? "–" : Ui.num(p.topScore())},
                {"top_logit", Float.isNaN(p.topLogit()) ? "–" : Ui.num(p.topLogit())},
                {"second", Float.isNaN(p.secondRight()) ? "–" : String.format(Locale.ROOT, "%.0f%%", 100 * p.secondRight())}}) {
            g.text(font, Component.translatable("residual.recipe." + row[0]), x, y, Ui.DIM, false);
            g.text(font, row[1], 154 - font.width(row[1]), y, Ui.TEXT, false);
            if (Ui.inside(mx, my, x, y - 1, 144, 11)) {
                tip = font.split(Component.translatable("residual.recipe." + row[0] + ".hint"), 200);
            }
            y += 11;
        }
        ratePlot(g, p, 164, 38, 144, 60);
        int ux = 164;
        for (Unit unit : Unit.values()) {
            boolean there = (p.units() >> unit.ordinal() & 1) != 0;
            g.item(new ItemStack(ModBlocks.UNITS.get(unit).get()), ux, 118);
            if (!there) {
                g.fill(ux, 118, ux + 16, 134, 0xC011161B);
            }
            if (Ui.inside(mx, my, ux, 118, 16, 16)) {
                tip = new ArrayList<>();
                tip.add(Component.translatable("block.residual." + unit.id).withColor(there ? Ui.TEAL : Ui.DIM).getVisualOrderText());
                tip.addAll(font.split(Component.translatable("residual.unit." + unit.id + ".opens").withStyle(ChatFormatting.GRAY), 200));
                tip.addAll(font.split(Component.translatable(there ? "residual.recipe.unit_here" : "residual.recipe.unit_missing").withStyle(ChatFormatting.GRAY), 200));
            }
            ux += 24;
        }
        if (tip != null) {
            g.setTooltipForNextFrame(tip, mx + leftPos, my + topPos);
        }
    }

    private static String shown(String value) {
        return value.startsWith("residual.") ? I18n.get(value) : value;
    }

    /** A setting's line: dimmed when the unit that opens it isn't there. */
    private int setting(GuiGraphicsExtractor g, int x, int y, String name, String value, @Nullable Unit unit, CoreSync.Plan p) {
        boolean open = unit == null || (p.units() >> unit.ordinal() & 1) != 0;
        g.text(font, Component.translatable("residual.recipe." + name), x, y, Ui.DIM, false);
        g.text(font, Ui.fit(font, value, 84), 154 - Math.min(84, font.width(value)), y, open ? Ui.TEAL : Ui.DIM, false);
        return y + 11;
    }

    private static String bytes(long n) {
        return n >= 1_000_000 ? String.format(Locale.ROOT, "%.1f MB", n / 1e6) : String.format(Locale.ROOT, "%.0f KB", n / 1e3);
    }

    /** What the learning rate is multiplied by over the run this recipe plans, with where the run has reached. */
    private void ratePlot(GuiGraphicsExtractor g, CoreSync.Plan p, int x, int y, int w, int h) {
        g.text(font, Component.translatable("residual.recipe.plot"), x, y - 11, Ui.DIM, false);
        for (int i = 0; i <= 2; i++) {
            g.fill(x, y + i * (h - 1) / 2, x + w, y + i * (h - 1) / 2 + 1, Ui.GRID);
        }
        Recipe recipe = new Recipe(Recipe.Schedule.values()[p.schedule()], Recipe.WARMUPS[p.warmup()], Recipe.LENGTHS[p.length()],
                Numerics.Format.values()[p.precision()], Recipe.Moves.values()[p.moves()], Recipe.Packing.values()[p.packing()]);
        int total = recipe.length() > 0 ? recipe.length() : Math.max(1000, Math.max(recipe.warmup() * 4, p.step()));
        int before = -1;
        for (int c = 0; c < w; c++) {
            int at = y + Math.round((1 - recipe.multiplier(c * total / w)) * (h - 1));
            g.fill(x + c, before < 0 ? at : Math.min(before, at), x + c + 1, (before < 0 ? at : Math.max(before, at)) + 1, Ui.AMBER);
            before = at;
        }
        if (p.step() > 0) {
            int c = Math.min(w - 1, (int) ((long) p.step() * w / total));
            g.fill(x + c, y, x + c + 1, y + h, 0x60FFFFFF);
        }
        String end = recipe.length() > 0 ? Ui.count(total) : Ui.count(total) + "…";
        g.text(font, "0", x, y + h + 2, Ui.DIM, false);
        g.text(font, end, x + w - font.width(end), y + h + 2, Ui.DIM, false);
    }

    // ---------------------------------------------------------------- inspect tab

    private void computeToolbar(int tx, int ty) {
        ComputeSync c = CoreView.compute;
        List<String> names = new ArrayList<>();
        List<String> icons = new ArrayList<>();
        for (Parallelism p : Parallelism.values()) {
            String id = p.name().toLowerCase(Locale.ROOT);
            names.add(I18n.get("residual.parallelism." + id));
            icons.add("split_" + id);
        }
        addRenderableWidget(IconButton.setting(tx, ty, icons, Component.translatable("residual.compute.split"), names, c == null ? 0 : c.mode(),
                Component.translatable("residual.compute.split_hint"), i -> send(CoreAction.Kind.PARALLELISM, i)));
    }

    /**
     * What the run really costs and how it shares out: one display, a line an idea, then what each device did through the
     * last step, with the bubbles left dark.
     */
    private void computeTab(GuiGraphicsExtractor g, int mx, int my) {
        ComputeSync c = CoreView.compute;
        int x = 6, y = 22, w = 308, h = 154;
        Ui.screen(g, x, y, w, h);
        if (c == null) {
            return;
        }
        String mode = I18n.get("residual.parallelism." + Parallelism.values()[Math.clamp(c.mode(), 0, 3)].name().toLowerCase(Locale.ROOT));
        int line = y + 4;
        g.text(font, Component.translatable("residual.compute.devices", mode, c.devices(), c.threads()), x + 5, line, Ui.TEXT, false);
        line += 11;
        // memory: what the model takes against what has to hold it
        Component memory = Component.translatable("residual.compute.memory", ClusterStatus.bytes(c.bytes()), ClusterStatus.bytes(c.budget()));
        g.text(font, memory, x + 5, line, c.fits() ? Ui.TEXT : Ui.RED, false);
        int bx = x + 10 + font.width(memory);
        Ui.bar(g, bx, line + 1, x + w - 6 - bx, 6, c.budget() == 0 ? 1 : (float) ((double) c.bytes() / c.budget()), c.fits() ? Ui.TEAL : Ui.RED);
        line += 11;
        g.text(font, Component.translatable("residual.compute.rate", ClusterStatus.flops(c.flopsPerSecond()), String.format(Locale.ROOT, "%,d", c.fePerTick()),
                String.format(Locale.ROOT, "%.2f", c.pue())), x + 5, line, c.starved() ? Ui.AMBER : Ui.TEXT, false);
        line += 11;
        g.text(font, Component.translatable("residual.compute.totals", ClusterStatus.flops(c.flops()), String.format(Locale.ROOT, "%,d", c.tokens()), ClusterStatus.flops(c.energy())),
                x + 5, line, Ui.DIM, false);
        line += 11;
        // 6ND beside what was counted, once there is anything to set it beside
        if (c.tokens() > 0 && c.parameters() > 0) {
            g.text(font, Component.translatable("residual.compute.six", String.format(Locale.ROOT, "%.1f", (double) c.flops() / c.tokens() / c.parameters())),
                    x + 5, line, Ui.DIM, false);
        }
        line += 13;
        int rows = c.busy().length, top = line, bottom = y + h - 16;
        g.fill(x + 1, top - 2, x + w - 1, top - 1, Ui.GRID);
        if (rows == 0) {
            g.text(font, Component.translatable(c.devices() > 0 ? "residual.compute.one_device" : "residual.compute.none"), x + 5, top + 2, Ui.DIM, false);
            return;
        }
        int rowH = Math.max(2, Math.min(9, (bottom - top) / rows));
        int bar = x + 5, barW = w - 10;
        for (int d = 0; d < rows; d++) {
            int ry = top + d * rowH;
            g.fill(bar, ry, bar + barW, ry + rowH - 1, Ui.GRID);
            if (c.spans().length == 0) {
                // too fine to draw: only how much of the step it worked
                g.fill(bar, ry, bar + Math.round(barW * c.busy()[d]), ry + rowH - 1, Ui.TEAL);
            }
        }
        for (int i = 0; i + 3 < c.spans().length; i += 4) {
            int d = c.spans()[i], kind = c.spans()[i + 1];
            if (d < 0 || d >= rows) {
                continue;
            }
            int from = bar + (c.spans()[i + 2] & 0xFF) * barW / 255, to = bar + Math.max((c.spans()[i + 2] & 0xFF) + 1, c.spans()[i + 3] & 0xFF) * barW / 255;
            int ry = top + d * rowH;
            g.fill(from, ry, to, ry + rowH - 1, kind == 0 ? Ui.TEAL : kind == 1 ? Ui.AMBER : 0xFFB48CFF);
        }
        g.text(font, Component.translatable("residual.compute.step", String.format(Locale.ROOT, "%.1f", c.wallNanos() / 1e6), Math.round(100 * c.bubble()),
                Math.round(100 * c.collective()), ClusterStatus.bytes(c.linkBytes())), x + 5, y + h - 11, Ui.DIM, false);
        if (Ui.inside(mx, my, x, y + h - 13, w, 13)) {
            g.setTooltipForNextFrame(font.split(Component.translatable("residual.compute.links", millis(c.linkBytes(), Accounts.RACK_LINK), millis(c.linkBytes(), Accounts.POD_LINK),
                    millis(c.linkBytes(), Accounts.HALL_LINK)).withStyle(ChatFormatting.GRAY), 240), mx + leftPos, my + topPos);
        } else if (Ui.inside(mx, my, x, top, w, y + h - top)) {
            List<FormattedCharSequence> lines = new ArrayList<>(font.split(Component.translatable("residual.compute.gantt").withStyle(ChatFormatting.GRAY), 240));
            g.setTooltipForNextFrame(lines, mx + leftPos, my + topPos);
        } else if (Ui.inside(mx, my, x, y + 15, w, 11)) {
            g.setTooltipForNextFrame(font.split(Component.translatable("residual.compute.memory_hint." + Parallelism.values()[Math.clamp(c.mode(), 0, 3)].name().toLowerCase(Locale.ROOT))
                    .withStyle(ChatFormatting.GRAY), 240), mx + leftPos, my + topPos);
        } else if (c.starved() && Ui.inside(mx, my, x, y + 26, w, 11)) {
            g.setTooltipForNextFrame(font.split(Component.translatable("residual.compute.starved").withStyle(ChatFormatting.GRAY), 240), mx + leftPos, my + topPos);
        }
    }

    private static String millis(long bytes, double perSecond) {
        return String.format(Locale.ROOT, "%.2f", Accounts.linkMillis(bytes, perSecond));
    }

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

    @Override
    public void parts(Parts out) {
        int tx = leftPos + 8;
        for (Tab t : Tab.values()) {
            int w = font.width(tabTitle(t)) + 10;
            out.add("tab:" + t.name().toLowerCase(Locale.ROOT), tx - 4, topPos + 4, w, 14, t == tab);
            tx += w + 2;
        }
        out.add("status", leftPos + 6, topPos + 194, WINDOW_W - 12, 12);
        switch (tab) {
            case DATA -> {
                for (int i = 0; i < CoreMenu.DATA_SLOTS; i++) {
                    out.add("data:" + i, this, menu.getSlot(i));
                }
                out.add("tokenizer", this, menu.getSlot(CoreMenu.TOKENIZER));
                out.add("checkpoint", this, menu.getSlot(CoreMenu.CRYSTAL));
                out.add("practising", leftPos + 8, topPos + 60, 160, 40);
            }
            case TRAIN -> {
                out.add("train", train);
                out.add("chart", leftPos + 6, topPos + 22, 308, 118);
                out.add("legend", leftPos + 6, topPos + 141, 308, 13);
                out.add("practising", leftPos + 6, topPos + 163, 236, 12);
            }
            case TESTS -> {
                int pitch = Math.min(15, 140 / Benchmark.values().length);
                for (Benchmark a : Benchmark.values()) {
                    out.add("row:" + a.id, leftPos + 6, topPos + 26 + a.ordinal() * pitch, 308, pitch);
                }
            }
            case TRY -> {
                out.add("output", leftPos + 6, topPos + 64, 220, 90);
                out.add("next", leftPos + 230, topPos + 66, 84, 88);
            }
            case INSPECT -> out.add("table", leftPos + 6, topPos + 22, 308, 166);
            case RECIPE -> {
                out.add("settings", leftPos + 8, topPos + 24, 150, 68);
                out.add("readings", leftPos + 8, topPos + 94, 150, 80);
                out.add("rate", leftPos + 160, topPos + 24, 152, 88);
                out.add("units", leftPos + 160, topPos + 114, 148, 24);
            }
            case COMPUTE -> {
                out.add("reading", leftPos + 6, topPos + 22, 308, 59);
                out.add("timeline", leftPos + 11, topPos + 83, 298, 77);
            }
        }
    }
}
