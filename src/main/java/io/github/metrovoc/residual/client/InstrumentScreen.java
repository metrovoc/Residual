package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.block.InstrumentBlockEntity;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.menu.InstrumentMenu;
import io.github.metrovoc.residual.net.ScopeAction;
import io.github.metrovoc.residual.net.ScopeSync;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.scope.Instrument;
import io.github.metrovoc.residual.scope.Picture;
import io.github.metrovoc.residual.scope.Toy;
import io.github.metrovoc.residual.scope.Trials;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * A Stream Scope's or Trial Rig's screen. Its first tab holds the slots and the player's inventory, as the Model Core's does; each other
 * tab is one instrument: its choices in a row, one dark display of what it found, with details in tooltips.
 */
public class InstrumentScreen extends AbstractContainerScreen<InstrumentMenu> implements Parts.Drawn {
    private static final int WINDOW_W = 320, DISPLAY_X = 6, DISPLAY_Y = 42, ROW_Y = 22, TAB_GAP = 5;
    /** What each instrument was last set to, kept while the game runs as the Model Core's tabs are. */
    private static final Map<Instrument, int[]> CHOICES = new EnumMap<>(Instrument.class);
    private static final Map<Instrument, String> TYPED = new EnumMap<>(Instrument.class);
    private static int lastBenchmark = Benchmark.READING.ordinal();
    private static int lastTab;

    /** What the server last told this screen. */
    public static ScopeSync sync;
    public static final Map<Instrument, Picture> PICTURES = new EnumMap<>(Instrument.class);

    private final List<Instrument> instruments;
    /** 0 for the slots; otherwise an instrument's place, counted from 1. */
    private int tab;
    private Button run;
    private EditBox prompt;
    private int page;
    /** The points of the stream the row of choices was built with. */
    private List<Component> builtWith = List.of();

    public InstrumentScreen(InstrumentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WINDOW_W, 212);
        instruments = Instrument.of(menu.rig);
        tab = lastTab <= instruments.size() ? lastTab : 0;
    }

    /** Forgets what screens remember between worlds. */
    public static void forget() {
        CHOICES.clear();
        TYPED.clear();
        lastTab = 0;
        lastBenchmark = Benchmark.READING.ordinal();
        sync = null;
        PICTURES.clear();
    }

    private Instrument instrument() {
        return tab == 0 ? null : instruments.get(tab - 1);
    }

    private int[] choices(Instrument i) {
        return CHOICES.computeIfAbsent(i, k -> new int[4]);
    }

    private static List<String> counted(int[] values) {
        List<String> out = new ArrayList<>();
        for (int v : values) {
            out.add(String.format(Locale.ROOT, "%,d", v));
        }
        return out;
    }

    private static List<String> shares(float[] values) {
        List<String> out = new ArrayList<>();
        for (float v : values) {
            out.add(String.format(Locale.ROOT, "%.0f%%", 100 * v));
        }
        return out;
    }

    private List<String> points() {
        return sync == null || sync.points().isEmpty() ? List.of("–") : sync.points().stream().map(Component::getString).toList();
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        run = null;
        prompt = null;
        page = 0;
        menu.slotsShown = tab == 0;
        Instrument i = instrument();
        if (i == null) {
            return;
        }
        int x = leftPos + 6, y = topPos + ROW_Y;
        int[] c = choices(i);
        // the choices of each, left to right, and whatever room is left for text
        switch (i) {
            case LENS, PATCH, GRAPH -> {
                x = benchmark(x, y, 112);
                prompt = addRenderableWidget(new EditBox(font, x, y, leftPos + WINDOW_W - 62 - x, 16, Component.translatable("residual.sample.prompt")));
                prompt.setMaxLength(200);
                prompt.setHint(Component.translatable("residual.scope.text_hint").withColor(Ui.DIM));
                prompt.setValue(TYPED.getOrDefault(i, ""));
                prompt.setResponder(s -> TYPED.put(i, s));
            }
            case PROBES -> benchmark(x, y, 160);
            case HEADS -> {
            }
            case TOY -> {
                x = choice(x, y, 120, "residual.scope.toy.absent", shares(Toy.SPARSITIES), c, 0);
                choice(x, y, 100, "residual.scope.toy.shape", List.of(I18n.get("residual.scope.toy.plane"), I18n.get("residual.scope.toy.space")), c, 1);
            }
            case STEER -> {
                x = benchmark(x, y, 84);
                x = choice(x, y, 72, "residual.scope.point", points(), c, 0);
                x = choice(x, y, 44, "residual.scope.steer.token", List.of("1", "2", "3", "4", "5", "6", "7", "8"), c, 1);
                choice(x, y, 44, "residual.scope.steer.strength", List.of("×1", "×2", "×4", "×8"), c, 2);
            }
            case FEATURES -> {
                x = benchmark(x, y, 112);
                choice(x, y, 112, "residual.scope.point", points(), c, 0);
            }
            case MEMORY -> choice(x, y, 130, "residual.trial.steps", counted(Trials.STEPS), c, 0);
            case BACKDOOR -> {
                x = choice(x, y, 112, "residual.trial.backdoor.tune", List.of(I18n.get("residual.trial.backdoor.clean"), I18n.get("residual.trial.backdoor.near")), c, 0);
                x = choice(x, y, 72, "residual.trial.steps", counted(Trials.STEPS), c, 1);
                choice(x, y, 66, "residual.trial.forget.rate", List.of("3e-4", "1e-3", "3e-3"), c, 2);
            }
            case FORGET -> {
                x = choice(x, y, 84, "residual.trial.forget.replay", shares(Trials.REPLAY), c, 0);
                x = choice(x, y, 84, "residual.trial.steps", counted(Trials.STEPS), c, 1);
                choice(x, y, 76, "residual.trial.forget.rate", List.of("3e-3", "3e-4"), c, 2);
            }
            case COLLAPSE -> {
                x = choice(x, y, 120, "residual.trial.collapse.kept", shares(Trials.KEPT), c, 0);
                choice(x, y, 110, "residual.trial.steps", counted(Trials.STEPS), c, 1);
            }
            case CONTAMINATE -> {
                x = choice(x, y, 64, "residual.trial.contaminate.leak", shares(Trials.LEAK), c, 0);
                x = choice(x, y, 78, "residual.trial.steps", counted(Trials.STEPS), c, 1);
                benchmark(x, y, 104);
            }
        }
        run = addRenderableWidget(Button.builder(Component.empty(), b -> press()).bounds(leftPos + WINDOW_W - 58, y, 52, 16).build());
        refresh();
    }

    private int benchmark(int x, int y, int w) {
        List<String> names = new ArrayList<>();
        for (Benchmark a : Benchmark.values()) {
            names.add(I18n.get("residual.benchmark." + a.id));
        }
        addRenderableWidget(new Cycler(x, y, w, Component.translatable("residual.try.benchmark"), names, lastBenchmark, v -> lastBenchmark = v));
        return x + w + 3;
    }

    private int choice(int x, int y, int w, String key, List<String> values, int[] c, int slot) {
        addRenderableWidget(new Cycler(x, y, w, Component.translatable(key), values, c[slot], v -> c[slot] = v));
        return x + w + 3;
    }

    private void press() {
        Instrument i = instrument();
        if (sync != null && sync.working()) {
            ClientPacketDistributor.sendToServer(new ScopeAction(menu.pos, -1, 0, 0, 0, ""));
            return;
        }
        page = 0;
        send(i);
    }

    private void send(Instrument i) {
        int[] c = choices(i);
        int a = c[0], b = c[1];
        switch (i) {
            case STEER -> b = c[1] * 8 + c[2];
            case FORGET -> b = c[1] * 2 + c[2];
            case BACKDOOR -> b = c[1] * Trials.TUNE_RATES.length + c[2];
            case FEATURES -> b = page;
            default -> {
            }
        }
        ClientPacketDistributor.sendToServer(new ScopeAction(menu.pos, i.ordinal(), lastBenchmark, a, b, prompt == null ? "" : prompt.getValue()));
    }

    private void refresh() {
        if (run != null) {
            boolean working = sync != null && sync.working();
            run.setMessage(Component.translatable(working ? "residual.scope.stop" : "residual.scope.run." + instrument().id));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (sync != null && !sync.points().equals(builtWith)) {
            builtWith = sync.points();
            rebuild();
        }
        refresh();
    }

    // ---------------------------------------------------------------- input

    private Component tabTitle(int t) {
        return Component.translatable(t == 0 ? "residual.scope.tab.model" : "residual.scope.tab." + instruments.get(t - 1).id);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int tx = leftPos + 8;
        for (int t = 0; t <= instruments.size(); t++) {
            int w = font.width(tabTitle(t)) + TAB_GAP;
            if (Ui.inside(event.x(), event.y(), tx - 3, topPos + 4, w, 14)) {
                if (t != tab) {
                    tab = lastTab = t;
                    rebuild();
                }
                return true;
            }
            tx += w;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (instrument() == Instrument.FEATURES && PICTURES.containsKey(Instrument.FEATURES) && (sync == null || !sync.working())) {
            page = Math.max(0, page - (int) Math.signum(scrollY) * 6);
            send(Instrument.FEATURES);
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
            if (event.isConfirmation()) {
                press();
                return true;
            }
            return prompt.keyPressed(event) || prompt.canConsumeInput() || super.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        Ui.window(g, leftPos, topPos, WINDOW_W, imageHeight);
        int tx = leftPos + 8;
        for (int t = 0; t <= instruments.size(); t++) {
            Component title = tabTitle(t);
            int w = font.width(title) + TAB_GAP;
            boolean on = t == tab;
            if (on) {
                g.fill(tx - 3, topPos + 17, tx - 3 + w - 2, topPos + 19, 0xFF2A9D92);
            }
            g.text(font, title, tx, topPos + 7, on ? Ui.LABEL : Ui.inside(mouseX, mouseY, tx - 3, topPos + 4, w, 14) ? 0xFF222222 : Ui.LABEL_DIM, false);
            tx += w;
        }
        if (tab == 0) {
            for (Slot slot : menu.slots) {
                if (slot.isActive()) {
                    Ui.slot(g, leftPos + slot.x, topPos + slot.y);
                }
            }
            for (int i = 0; i < InstrumentBlockEntity.SLOTS; i++) {
                Slot slot = menu.getSlot(i);
                if (slot.isActive() && !slot.hasItem()) {
                    Ui.ghost(g, new ItemStack(i == InstrumentBlockEntity.DATA ? ModItems.DATASETS.get(Benchmark.FORECASTING).get() : ModItems.CHECKPOINT.get()), leftPos + slot.x, topPos + slot.y);
                }
            }
        } else {
            Ui.screen(g, leftPos + DISPLAY_X, topPos + DISPLAY_Y, Picture.W, Picture.H);
        }
        Ui.screen(g, leftPos + 6, topPos + 194, WINDOW_W - 12, 12);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int mx = mouseX - leftPos, my = mouseY - topPos;
        status(g, mx, my);
        Instrument i = instrument();
        if (i == null) {
            slots(g, mx, my);
            return;
        }
        Picture picture = PICTURES.get(i);
        if (picture == null) {
            int y = DISPLAY_Y + 4;
            for (FormattedCharSequence line : font.split(Component.translatable("residual.scope.empty." + i.id), Picture.W - 8)) {
                g.text(font, line, DISPLAY_X + 4, y, Ui.DIM, false);
                y += 10;
            }
            return;
        }
        draw(g, picture, mx, my);
    }

    /** What the screen says and how far the work has come, along the bottom. */
    private void status(GuiGraphicsExtractor g, int mx, int my) {
        int y = 196;
        if (sync == null) {
            g.text(font, Component.translatable("residual.status.connecting"), 10, y, Ui.DIM, false);
            return;
        }
        if (sync.working()) {
            g.text(font, Component.translatable("residual.scope.working"), 10, y, Ui.TEAL, false);
            Ui.bar(g, 90, y + 1, WINDOW_W - 106, 6, sync.fraction(), Ui.TEAL);
            return;
        }
        String text = sync.note().getString();
        int max = WINDOW_W - 24;
        g.text(font, Ui.fit(font, text, max), 10, y, Ui.TEXT, false);
        if (font.width(text) > max && Ui.inside(mx, my, 6, 194, WINDOW_W - 12, 12)) {
            g.setTooltipForNextFrame(font.split(Component.literal(text), 240), mx + leftPos, my + topPos);
        }
    }

    private void slots(GuiGraphicsExtractor g, int mx, int my) {
        int y = 62;
        for (FormattedCharSequence line : font.split(Component.translatable(menu.rig ? "residual.trial.slots" : "residual.scope.slots"), WINDOW_W - 16)) {
            g.text(font, line, 8, y, Ui.LABEL, false);
            y += 10;
        }
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        String[] keys = {"residual.scope.slot.model", "residual.trial.slot.data", "residual.trial.slot.output"};
        for (int i = 0; i < InstrumentBlockEntity.SLOTS; i++) {
            Slot slot = menu.getSlot(i);
            if (slot.isActive() && !slot.hasItem() && Ui.inside(mx, my, slot.x, slot.y, 16, 16)) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                lines.add(Component.translatable(keys[i]).getVisualOrderText());
                lines.addAll(font.split(Component.translatable(keys[i] + "_hint").withStyle(ChatFormatting.GRAY), 200));
                g.setTooltipForNextFrame(lines, mx + leftPos, my + topPos);
            }
        }
    }

    private void draw(GuiGraphicsExtractor g, Picture p, int mx, int my) {
        int ox = DISPLAY_X, oy = DISPLAY_Y;
        Component tip = null;
        for (Picture.Box b : p.boxes()) {
            if ((b.color() >>> 24) != 0) {
                g.fill(ox + b.x(), oy + b.y(), ox + b.x() + b.w(), oy + b.y() + b.h(), b.color());
            }
            if (!b.tip().getString().isEmpty() && Ui.inside(mx, my, ox + b.x(), oy + b.y(), b.w(), b.h())) {
                tip = b.tip();
            }
        }
        for (Picture.Line l : p.lines()) {
            line(g, ox + l.x0(), oy + l.y0(), ox + l.x1(), oy + l.y1(), l.color());
        }
        for (Picture.Text t : p.texts()) {
            if (t.width() < 0) {
                int y = oy + t.y();
                for (FormattedCharSequence line : font.split(t.text(), -t.width())) {
                    g.text(font, line, ox + t.x(), y, t.color(), false);
                    y += 10;
                }
            } else {
                g.text(font, firstLine(t.text(), t.width() > 0 ? t.width() : Picture.W - t.x() - 2), ox + t.x(), oy + t.y(), t.color(), false);
            }
        }
        if (tip != null) {
            g.setTooltipForNextFrame(font.split(tip, 260), mx + leftPos, my + topPos);
        }
    }

    private FormattedCharSequence firstLine(Component text, int width) {
        List<FormattedCharSequence> lines = font.split(text, width);
        return lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.getFirst();
    }

    /** A line of one pixel, stepping along the longer way. */
    private static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps == 0) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps, y = y0 + (y1 - y0) * i / steps;
            g.fill(x, y, x + 1, y + 1, color);
        }
    }

    @Override
    public void removed() {
        super.removed();
        sync = null;
        PICTURES.clear();
    }

    @Override
    public void parts(Parts out) {
        int tx = leftPos + 8;
        for (int t = 0; t <= instruments.size(); t++) {
            int w = font.width(tabTitle(t)) + TAB_GAP;
            out.add("tab:" + (t == 0 ? "model" : instruments.get(t - 1).id), tx - 3, topPos + 4, w, 14, t == tab);
            tx += w;
        }
        out.add("status", leftPos + 6, topPos + 194, WINDOW_W - 12, 12);
        if (tab == 0) {
            out.add("instrument.model", this, menu.getSlot(InstrumentBlockEntity.MODEL));
            out.add("instrument.data", this, menu.getSlot(InstrumentBlockEntity.DATA));
            out.add("instrument.output", this, menu.getSlot(InstrumentBlockEntity.OUTPUT));
        } else {
            out.add("instrument.run", run);
            out.add("display", leftPos + DISPLAY_X, topPos + DISPLAY_Y, Picture.W, Picture.H);
        }
    }
}
