package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.agent.Agent;
import io.github.metrovoc.residual.agent.Debug;
import io.github.metrovoc.residual.agent.Job;
import io.github.metrovoc.residual.agent.Program;
import io.github.metrovoc.residual.agent.Things;
import io.github.metrovoc.residual.agent.Trace;
import io.github.metrovoc.residual.item.ProgramCardItem;
import io.github.metrovoc.residual.menu.AgentMenu;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.net.AgentAction;
import io.github.metrovoc.residual.net.AgentSync;
import io.github.metrovoc.residual.net.ProgramSync;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;

/**
 * An agent's screen. Work: its job, what it is doing and the last thing it asked its model, and the order box to tell it
 * something. Program: the program it runs, to read and rewrite, checked as it is typed, with a palette beside it of the
 * pieces to click together. Trace: what it has been doing, step by step. Below: what it carries. Its model slots sit in
 * a panel beside the window, with setting its home under them.
 */
public class AgentScreen extends AbstractContainerScreen<AgentMenu> implements Parts.Drawn {
    private enum Tab { WORK, PROGRAM, TRACE }

    /** How wide what sits beside the window on its left is: the palette, or the debugger. */
    private static final int PALETTE = Palette.WIDTH;
    private static final int WINDOW_W = 176;
    /** How much the program can be given of a wide screen, when its window takes the slots' room too. */
    private static final int WIDEST = 340;

    private static Tab lastTab = Tab.WORK;
    /** Whether the palette is open beside the Program tab; it stays so while the game runs. */
    private static boolean palette;
    /** Whether the program takes the whole window, with the slots out of the way. */
    private static boolean wide;
    private Tab tab = lastTab;
    private final Palette chips = new Palette();
    private DebugPanel debugger;
    /** Who may use it, as last shown. */
    @Nullable
    private IconButton access;
    private int shownAccess;
    private final List<IconButton> jobs = new ArrayList<>();
    /** Yes and no to an order it read back, shown while it waits for one. */
    @Nullable
    private Button doIt, cancel;
    @Nullable
    private EditBox order;
    @Nullable
    private IconButton tell;
    /** What the order box holds, kept across tabs. */
    private String said = "";
    @Nullable
    private MultiLineEditBox code;
    @Nullable
    private IconButton run;
    /** What the program box holds, kept across tabs; null until the agent's program arrives. */
    @Nullable
    private String draft;
    /** Whether the player has changed the box since it last showed the agent's program. */
    private boolean edited;
    private boolean syncing;
    private Program.Parsed parsed = Program.parse("");

    public AgentScreen(AgentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AgentMenu.SIDE_X + AgentMenu.SIDE_W, 248);
    }

    private final DebugPanel.Host debugHost = new DebugPanel.Host() {
        @Override
        public void debug(Debug.Move move, int number, String text) {
            debugMove(move, number, text);
        }

        @Override
        public String program() {
            AgentSync s = sync();
            return s == null ? "" : s.program();
        }

        @Override
        public ProgramSync view() {
            ProgramSync p = AgentView.program;
            return p != null && p.entity() == menu.entityId ? p : null;
        }
    };

    private AgentSync sync() {
        AgentSync s = AgentView.sync;
        return s != null && s.entity() == menu.entityId ? s : null;
    }

    private void send(AgentAction.Kind kind, int job, String said) {
        ClientPacketDistributor.sendToServer(new AgentAction(menu.entityId, kind, job, said));
    }

    @Override
    protected void init() {
        super.init();
        debugger = new DebugPanel(debugHost, font);
        rebuild();
    }

    /** The program as it reads in its box: over lines when there is room for them. */
    private String shown(@Nullable String text) {
        if (text == null) {
            return "";
        }
        Program.Parsed p = wideShown() ? Program.parse(text) : null;
        return p != null && p.ok() && !edited ? p.program().pretty() : text;
    }

    private boolean wideShown() {
        return wide && tab == Tab.PROGRAM;
    }

    private int windowWidth() {
        return wideShown() ? Math.clamp(width - (palette ? PALETTE + 12 : 8), WINDOW_W, WIDEST) : WINDOW_W;
    }

    /** Panels beyond the container, including their gap to it, kept clear by recipe viewers. */
    public List<Rect2i> extraAreas() {
        List<Rect2i> areas = new ArrayList<>();
        if (wideShown()) {
            areas.add(new Rect2i(leftPos, topPos, windowWidth(), imageHeight));
        }
        if (leftShown()) {
            areas.add(new Rect2i(leftPos - PALETTE - 4, topPos, PALETTE + 4, imageHeight));
        }
        if (!wideShown() && menu.carried() > Agent.CARRY) {
            areas.add(new Rect2i(leftPos + imageWidth, topPos + AgentMenu.MORE_Y - 6,
                    AgentMenu.MORE_X - 7 + 122 - imageWidth, moreHeight()));
        }
        return areas;
    }

    private void rebuild() {
        clearWidgets();
        jobs.clear();
        doIt = cancel = null;
        code = null;
        run = null;
        order = null;
        tell = null;
        menu.hideSlots(wideShown());
        leftPos = (width - imageWidth) / 2;
        if (wideShown()) {
            leftPos = (width - windowWidth()) / 2;
        }
        if (leftShown()) {
            // room on the left for what sits there, the way the recipe book makes room
            leftPos = Math.min(Math.max(leftPos, PALETTE + 8), wideShown() ? width - windowWidth() : width - imageWidth);
        }
        int x = leftPos, y = topPos;
        switch (tab) {
            case WORK -> {
                for (Job j : Job.values()) {
                    Component name = Component.translatable("residual.job." + j.id);
                    IconButton b = addRenderableWidget(new IconButton(x + 8 + j.ordinal() * 27, y + 19, 25, 18, icon(j), name,
                            () -> send(AgentAction.Kind.JOB, j.ordinal(), "")));
                    b.setTooltip(Tooltip.create(Component.empty().append(name.copy().withColor(Ui.TEAL)).append(" ")
                            .append(Component.translatable("residual.job." + j.id + ".help"))));
                    jobs.add(b);
                }
                doIt = addRenderableWidget(Button.builder(Component.translatable("residual.agent.do_it"), b -> send(AgentAction.Kind.ANSWER, 1, ""))
                        .bounds(x + 90, y + 78, 34, 12).tooltip(Tooltip.create(Component.translatable("residual.agent.choice.yes.help"))).build());
                cancel = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> send(AgentAction.Kind.ANSWER, 0, ""))
                        .bounds(x + 126, y + 78, 40, 12).tooltip(Tooltip.create(Component.translatable("residual.agent.choice.no.help"))).build());
                doIt.visible = cancel.visible = sync() != null && sync().asking();
                order = addRenderableWidget(new EditBox(font, x + 8, y + 96, 138, 14, Component.translatable("residual.agent.order")));
                order.setMaxLength(60);
                order.setValue(said);
                order.setResponder(v -> said = v);
                order.setHint(Component.translatable("residual.agent.order_hint"));
                tell = addRenderableWidget(IconButton.action(x + 150, y + 94, "tell", Component.translatable("residual.agent.tell"),
                        Component.translatable("residual.agent.tell.help"), () -> {
                            if (!order.getValue().isBlank()) {
                                send(AgentAction.Kind.SAY, 0, order.getValue());
                                order.setValue("");
                            }
                        }));
            }
            case PROGRAM -> {
                boolean big = wideShown();
                code = addRenderableWidget(MultiLineEditBox.builder().setX(x + 8).setY(y + 20).setShowBackground(false).setShowDecorations(false)
                        .setTextColor(Ui.TEXT).setCursorColor(Ui.TEAL).setPlaceholder(Component.translatable("residual.program.hint"))
                        .build(font, windowWidth() - 16, big ? imageHeight - 62 : 56, Component.translatable("residual.tab.program")));
                code.setCharacterLimit(Program.LONGEST * 2);
                code.setValue(shown(draft));
                parsed = Program.parse(code.getValue());
                code.setValueListener(v -> {
                    parsed = Program.parse(v);
                    if (!syncing) {
                        draft = v;
                        edited = true;
                    }
                });
                int row = rowY();
                run = addRenderableWidget(IconButton.action(x + 8, y + row, "run", Component.translatable("residual.program.run"),
                        Component.translatable("residual.program.run.help"), () -> {
                            send(AgentAction.Kind.PROGRAM, 0, code.getValue());
                            edited = false;
                        }));
                addRenderableWidget(IconButton.action(x + 28, y + row, "stop", Component.translatable("residual.program.stop"),
                        Component.translatable("residual.program.stop.help"), () -> send(AgentAction.Kind.STOP, 0, "")));
                addRenderableWidget(IconButton.action(x + 48, y + row, "palette", Component.translatable("residual.palette"),
                        Component.translatable(palette ? "residual.palette.hide" : "residual.palette.show"), () -> {
                            palette = !palette;
                            rebuild();
                        }));
                addRenderableWidget(IconButton.action(x + 68, y + row, "expand", Component.translatable("residual.program.wide"),
                        Component.translatable(big ? "residual.program.wide.off" : "residual.program.wide.on"), () -> {
                            wide = !wide;
                            rebuild();
                        }));
                if (palette) {
                    chips.lay(x - PALETTE - 4, y, imageHeight, this::addRenderableWidget, this::add, this::things);
                }
            }
            case TRACE -> debugger.lay(x - DebugPanel.WIDTH - 4, y, imageHeight, this::addRenderableWidget);
        }
        if (wideShown()) {
            return;
        }
        // under the slots' panel, as a toolbar
        int toolbar = y + AgentMenu.SIDE_Y + AgentMenu.SIDE_H + 4;
        addRenderableWidget(IconButton.action(x + AgentMenu.SLOTS_X - 1, toolbar, "home",
                Component.translatable("residual.agent.set_home"), Component.translatable("residual.agent.set_home.help"),
                () -> send(AgentAction.Kind.HOME, 0, "")));
        AgentSync s = sync();
        shownAccess = s == null ? 0 : s.access();
        access = addRenderableWidget(IconButton.setting(x + AgentMenu.SLOTS_X + 17, toolbar, List.of("open", "team", "locked"), Component.translatable("residual.owned.access"),
                List.of(Component.translatable("residual.owned.anyone").getString(), Component.translatable("residual.owned.team").getString(),
                        Component.translatable("residual.owned.owner").getString()),
                shownAccess, Component.translatable("residual.owned.help"), i -> send(AgentAction.Kind.ACCESS, i, "")));
    }

    /** Where the row of buttons under the program stands. */
    private int rowY() {
        return wideShown() ? imageHeight - 26 : 94;
    }

    /** Everything at hand to name by a click on the palette's last page. */
    private List<ItemStack> things() {
        List<ItemStack> out = new ArrayList<>();
        menu.slots.forEach(slot -> out.add(slot.getItem()));
        return out;
    }

    private boolean paletteShown() {
        return palette && tab == Tab.PROGRAM;
    }

    /** Whether something sits on the window's left: the palette, or beside the trace the debugger. */
    private boolean leftShown() {
        return paletteShown() || tab == Tab.TRACE;
    }

    private void debugMove(Debug.Move move, int number, String text) {
        send(AgentAction.Kind.DEBUG, move.ordinal() + number * Debug.Move.values().length, text);
    }

    /** Adds a piece, or the name of a thing, to the end of the program, and goes on writing there. */
    private void add(String piece, Program.Join join) {
        if (code != null) {
            code.setValue(Program.append(code.getValue(), piece, join));
            setFocused(code);
        }
    }

    /**
     * Clicking the program holding something: a written card reads into it, and a blank one, or a written one with shift
     * held, takes the program down; anything else is named at its end.
     */
    private void take(ItemStack held, boolean shift) {
        String written = ProgramCardItem.program(held);
        if (held.is(ModItems.PROGRAM_CARD.get()) && (written.isEmpty() || shift)) {
            if (parsed.ok() && !code.getValue().isBlank()) {
                send(AgentAction.Kind.CARD, 0, code.getValue());
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2f));
            }
        } else if (!written.isEmpty()) {
            code.setValue(written);
            setFocused(code);
        } else {
            add(Things.name(held), Program.Join.WORD);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        AgentSync s = sync();
        if (s == null) {
            return;
        }
        if (!edited && !s.program().equals(draft)) {
            draft = s.program();
            if (code != null) {
                syncing = true;
                code.setValue(shown(draft));
                syncing = false;
            }
        }
        if (access != null && s.access() != shownAccess) {
            shownAccess = s.access();
            access.set(shownAccess);
        }
        if (run != null) {
            run.active = parsed.ok();
        }
        if (doIt != null) {
            doIt.visible = cancel.visible = s.asking();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (paletteShown() && chips.clickedTab(font, event.x(), event.y())) {
            rebuild();
            return true;
        }
        if (tab == Tab.TRACE && debugger.clicked(event.x(), event.y())) {
            return true;
        }
        int tx = leftPos + 8;
        for (Tab t : Tab.values()) {
            int w = font.width(tabTitle(t)) + 10;
            if (Ui.inside(event.x(), event.y(), tx - 4, topPos + 4, w, 14)) {
                if (t != tab) {
                    tab = lastTab = t;
                    rebuild();
                }
                return true;
            }
            tx += w + 2;
        }
        ItemStack held = menu.getCarried();
        if (!held.isEmpty() && event.button() == 0) {
            if (code != null && code.isMouseOver(event.x(), event.y())) {
                take(held, event.hasShiftDown());
                return true;
            }
            if (order != null && order.isMouseOver(event.x(), event.y())) {
                String said = order.getValue().stripTrailing();
                order.setValue(said.isEmpty() ? Things.name(held) : said + " " + Things.name(held));
                setFocused(order);
                return true;
            }
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (getFocused() instanceof Palette.Chip && code != null) {
            // the program keeps the keys after a piece is picked
            setFocused(code);
            setDragging(false);
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (paletteShown() && chips.scrolled(x, y, dy) || tab == Tab.TRACE && debugger.scrolled(x, y, dy)) {
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        if (wideShown() && Ui.inside(mx, my, xo, yo, windowWidth(), imageHeight)) {
            return false;
        }
        boolean more = menu.carried() > Agent.CARRY && Ui.inside(mx, my, xo + AgentMenu.MORE_X - 7, yo + AgentMenu.MORE_Y - 6, 122, moreHeight());
        return super.hasClickedOutside(mx, my, xo, yo) && !more && !(leftShown() && Ui.inside(mx, my, xo - PALETTE - 4, yo, PALETTE, imageHeight));
    }

    /** How high the panel of what it carries beyond its first slots is. */
    private int moreHeight() {
        return (menu.carried() - Agent.CARRY + 5) / 6 * 18 + 12;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            return super.keyPressed(event);
        }
        if (order != null && order.isFocused()) {
            if (event.isConfirmation() && tell != null) {
                tell.onPress(event);
                return true;
            }
            return order.keyPressed(event) || order.canConsumeInput() || super.keyPressed(event);
        }
        if (code != null && code.isFocused()) {
            // every key is the program's while it is being written, the inventory key included
            code.keyPressed(event);
            return true;
        }
        if (tab == Tab.TRACE && debugger.typing()) {
            if (event.isConfirmation()) {
                debugger.submit();
                return true;
            }
            return debugger.keyPressed(event) || super.keyPressed(event);
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
        if (!wideShown()) {
            // the model slots' panel, tucked under the window's edge, and what it carries beyond its first slots beside that
            if (menu.carried() > Agent.CARRY) {
                Ui.window(g, leftPos + AgentMenu.MORE_X - 7, topPos + AgentMenu.MORE_Y - 6, 122, moreHeight());
            }
            Ui.window(g, leftPos + AgentMenu.SIDE_X, topPos + AgentMenu.SIDE_Y, AgentMenu.SIDE_W, AgentMenu.SIDE_H);
        }
        Ui.window(g, leftPos, topPos, windowWidth(), imageHeight);
        if (paletteShown()) {
            chips.draw(g, font, mouseX, mouseY);
        } else if (tab == Tab.TRACE) {
            debugger.draw(g, mouseX, mouseY);
        }
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
        switch (tab) {
            case WORK -> Ui.screen(g, leftPos + 8, topPos + 38, 160, 52);
            case PROGRAM -> Ui.screen(g, leftPos + 8, topPos + 20, windowWidth() - 16, rowY() - 24);
            case TRACE -> Ui.screen(g, leftPos + 8, topPos + 20, 160, 70);
        }
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                Ui.slot(g, leftPos + slot.x, topPos + slot.y);
            }
        }
        AgentSync s = sync();
        for (int i = 0; i < jobs.size(); i++) {
            jobs.get(i).active = s == null || s.job() != i;
        }
        // an empty model slot shows faintly what goes in it
        boolean noModel = !wideShown() && !menu.getSlot(AgentMenu.MODEL).hasItem(), noFilter = !wideShown() && !menu.getSlot(AgentMenu.FILTER).hasItem();
        int gx = leftPos + AgentMenu.SLOTS_X;
        if (noModel) {
            g.item(new ItemStack(ModItems.CHECKPOINT.get()), gx, topPos + AgentMenu.MODEL_Y);
        }
        if (noFilter) {
            g.item(new ItemStack(Items.HOPPER), gx, topPos + AgentMenu.FILTER_Y);
        }
        g.nextStratum();
        if (noModel) {
            g.fill(gx, topPos + AgentMenu.MODEL_Y, gx + 16, topPos + AgentMenu.MODEL_Y + 16, 0xB08B8B8B);
        }
        if (noFilter) {
            g.fill(gx, topPos + AgentMenu.FILTER_Y, gx + 16, topPos + AgentMenu.FILTER_Y + 16, 0xB08B8B8B);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int tabs = 8;
        for (Tab t : Tab.values()) {
            tabs += font.width(tabTitle(t)) + 12;
        }
        int right = windowWidth() - 8;
        String name = Ui.fit(font, title.getString(), right - tabs);
        g.text(font, name, right - font.width(name), 7, Ui.LABEL_DIM, false);
        int x = mouseX - leftPos, y = mouseY - topPos;
        for (int slot : new int[]{AgentMenu.MODEL, AgentMenu.FILTER}) {
            Slot at = menu.getSlot(slot);
            if (!wideShown() && !at.hasItem() && Ui.inside(x, y, at.x - 1, at.y - 1, 18, 18)) {
                String key = slot == AgentMenu.MODEL ? "residual.agent.model_slot.help" : "residual.agent.filter_slot.help";
                g.setTooltipForNextFrame(font.split(Component.translatable(key), 200), mouseX, mouseY);
            }
        }
        if (!wideShown() && hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty() && menu.slots.indexOf(hoveredSlot) >= AgentMenu.UPGRADES
                && menu.slots.indexOf(hoveredSlot) < AgentMenu.MORE_CARRIED) {
            g.setTooltipForNextFrame(font.split(Component.translatable("residual.upgrades.help"), 200), mouseX, mouseY);
        }
        AgentSync s = sync();
        if (s == null) {
            g.text(font, Component.translatable("residual.status.connecting"), 12, 40, Ui.DIM, false);
            return;
        }
        int mx = mouseX - leftPos, my = mouseY - topPos;
        switch (tab) {
            case WORK -> workTab(g, s, mx, my, mouseX, mouseY);
            case PROGRAM -> programTab(g, s, mx, my, mouseX, mouseY);
            case TRACE -> TraceView.draw(g, font, s.trace(), 20, 7, mx, my, mouseX, mouseY);
        }
        if (order != null && !menu.getCarried().isEmpty() && Ui.inside(mx, my, 8, 96, 138, 14)) {
            g.setTooltipForNextFrame(font.split(named(menu.getCarried()), 220), mouseX, mouseY);
        }
    }

    private void workTab(GuiGraphicsExtractor g, AgentSync s, int mx, int my, int mouseX, int mouseY) {
        int w = 152;
        Component status = status(s);
        ModelInfo info = s.model();
        String tag = info == null ? "?" : info.tag();
        boolean working = s.problem().isEmpty();
        // the model that made its last decision: its filter, when that was sorting
        Component modelLine = !working ? Component.translatable(s.problem(), s.problemArgs().toArray())
                : s.served() ? Component.translatable(s.filter() ? "residual.agent.filter_served" : "residual.agent.model_served", tag)
                : Component.translatable(s.filter() ? "residual.agent.filter_onboard" : "residual.agent.model_onboard", tag,
                info == null ? "?" : Ui.count(info.parameters()));
        int modelColor = working ? Ui.TEAL : Ui.AMBER;
        if (s.asking()) {
            // what it read back comes before anything else: as much as fits, and its steps on lines when pointed at
            List<FormattedCharSequence> said = font.split(status, w);
            for (int i = 0; i < Math.min(4, said.size()); i++) {
                g.text(font, said.get(i), 12, 42 + i * 10, Ui.TEXT, false);
            }
        } else if (s.decisions() == 0 || !working) {
            // no decision worth showing: the status and the model may take the four lines above the record
            List<FormattedCharSequence> said = font.split(status, w);
            said = said.subList(0, Math.min(2, said.size()));
            List<FormattedCharSequence> modelLines = font.split(modelLine, w);
            int y = 42;
            for (FormattedCharSequence line : said) {
                g.text(font, line, 12, y, Ui.TEXT, false);
                y += 10;
            }
            for (int i = 0; i < modelLines.size() && y <= 72; i++, y += 10) {
                g.text(font, modelLines.get(i), 12, y, modelColor, false);
            }
        } else {
            g.text(font, fit(status, w), 12, 42, Ui.TEXT, false);
            g.text(font, fit(modelLine, w), 12, 52, modelColor, false);
            String asked = s.prompt().replace("\n", "⏎");
            Component label = Component.translatable("residual.agent.asked");
            g.text(font, label, 12, 62, Ui.DIM, false);
            int lw = font.width(label) + 3;
            g.text(font, tail(asked, w - lw), 12 + lw, 62, Ui.TEXT, false);
            label = Component.translatable("residual.agent.answered");
            g.text(font, label, 12, 72, Ui.DIM, false);
            lw = font.width(label) + 3;
            String answer = s.answer().replace("\n", "⏎") + (Float.isNaN(s.confidence()) ? ""
                    : String.format(Locale.ROOT, "  · %.0f%%", 100 * s.confidence()));
            g.text(font, Ui.fit(font, answer, w - lw), 12 + lw, 72, Ui.TEXT, false);
        }
        // only decisions whose right answer is known are counted for or against it
        Component record = s.decisions() == 0 ? Component.translatable("residual.agent.record_none")
                : s.checked() == 0 ? Component.translatable("residual.agent.record_unchecked", s.decisions())
                : Component.translatable("residual.agent.record", s.right(), s.checked());
        if (s.marks() > 0) {
            record = s.marks() == 1 ? Component.translatable("residual.agent.with_mark", record)
                    : Component.translatable("residual.agent.with_marks", record, s.marks());
        }
        g.text(font, fit(record, s.asking() ? w - 78 : w), 12, 82, Ui.DIM, false);
        // whatever had to be cut short, in full
        if (s.asking() && Ui.inside(mx, my, 8, 38, 160, 42)) {
            Program.Parsed read = s.statusArgs().isEmpty() ? null : Program.parse(s.statusArgs().getFirst());
            if (read != null && read.ok()) {
                g.setTooltipForNextFrame(font.split(Component.literal(read.program().pretty()), 220), mouseX, mouseY);
            }
        } else if (Ui.inside(mx, my, 8, 38, 160, 52) && (font.width(status) > w || font.width(modelLine) > w)) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component c : List.of(status, modelLine)) {
                lines.addAll(font.split(c, 220));
            }
            g.setTooltipForNextFrame(lines, mouseX, mouseY);
        }
    }

    private void programTab(GuiGraphicsExtractor g, AgentSync s, int mx, int my, int mouseX, int mouseY) {
        Component check;
        int color;
        if (!parsed.ok()) {
            Program.Problem p = parsed.problem();
            check = Component.translatable("residual.program.slip", Component.translatable(p.key(), p.word(), Program.LONGEST), p.at() + 1);
            color = Ui.AMBER;
        } else {
            Program p = parsed.program();
            check = Component.translatable(p.steps().isEmpty() ? "residual.program.empty"
                    : p.endless() ? "residual.program.ok_endless" : "residual.program.ok");
            color = Ui.TEAL;
        }
        int row = rowY();
        int checkW = windowWidth() - 24;
        g.text(font, fit(check, checkW), 12, row - 14, color, false);
        Component status = status(s);
        int statusW = windowWidth() - 100;
        g.text(font, fit(status, statusW), 92, row + 5, Ui.LABEL, false);
        List<FormattedCharSequence> tip = new ArrayList<>();
        ItemStack held = menu.getCarried();
        if (Ui.inside(mx, my, 8, row - 16, windowWidth() - 16, 12) && font.width(check) > checkW) {
            tip.addAll(font.split(check, 220));
        } else if (Ui.inside(mx, my, 90, row, statusW + 2, 18) && font.width(status) > statusW) {
            tip.addAll(font.split(status, 220));
        } else if (Ui.inside(mx, my, 8, 20, 160, 56) && !held.isEmpty()) {
            Component hint = !held.is(ModItems.PROGRAM_CARD.get()) ? named(held)
                    : Component.translatable(ProgramCardItem.program(held).isEmpty() ? "residual.program.drop.blank" : "residual.program.drop.card");
            tip.addAll(font.split(hint, 220));
        } else if (Ui.inside(mx, my, 8, 20, 160, 56) && code != null && code.getValue().isEmpty()) {
            tip.addAll(font.split(Component.translatable("residual.program.help"), 240));
        }
        if (!tip.isEmpty()) {
            g.setTooltipForNextFrame(font, tip, (sw, sh, x, y, w, h) -> {
                var at = DefaultTooltipPositioner.INSTANCE.positionTooltip(width, height, x, y, w, h);
                return new Vector2i(Math.max(4, Math.min(at.x(), width - w - 8)), Math.max(4, Math.min(at.y(), height - h - 8)));
            }, mouseX, mouseY, false);
        }
    }

    /** What clicking the program or the order with {@code held} writes. */
    private static Component named(ItemStack held) {
        return Component.translatable("residual.program.drop.thing", Component.literal(Things.name(held)).withColor(Ui.TEAL));
    }

    private static ItemStack icon(Job j) {
        return new ItemStack(switch (j) {
            case REST -> Items.RED_BED;
            case FARM -> Items.WHEAT;
            case STORE -> Items.CHEST;
            case HAUL -> Items.MINECART;
            case BUILD -> Items.BRICKS;
            case SORT -> Items.HOPPER;
        });
    }

    private static Component status(AgentSync s) {
        return Component.translatable(s.status(), s.statusArgs().toArray());
    }

    private String fit(Component c, int w) {
        return Ui.fit(font, c.getString(), w);
    }

    /** The end of {@code text}, cut from the front to fit: the latest part of a prompt matters most. */
    private String tail(String text, int w) {
        if (font.width(text) <= w) {
            return text;
        }
        String s = text;
        while (!s.isEmpty() && font.width("…" + s) > w) {
            s = s.substring(1);
        }
        return "…" + s;
    }

    @Override
    public void parts(Parts out) {
        out.add("window", leftPos, topPos, windowWidth(), imageHeight);
        int tx = leftPos + 8;
        for (Tab t : Tab.values()) {
            int w = font.width(tabTitle(t)) + 10;
            out.add("tab:" + t.name().toLowerCase(Locale.ROOT), tx - 4, topPos + 4, w, 14, t == tab);
            tx += w + 2;
        }
        out.add("model", this, menu.getSlot(AgentMenu.MODEL));
        out.add("filter", this, menu.getSlot(AgentMenu.FILTER));
        for (int i = 0; i < Agent.CARRY; i++) {
            out.add("carry:" + i, this, menu.getSlot(i));
        }
        if (!wideShown()) {
            out.add("carry", leftPos + AgentMenu.CARRY_X - 1, topPos + AgentMenu.CARRY_Y - 1, 108, 36);
            if (menu.carried() > Agent.CARRY) {
                out.add("more_carry", leftPos + AgentMenu.MORE_X - 7, topPos + AgentMenu.MORE_Y - 6, 122, moreHeight());
            }
        }
        switch (tab) {
            case WORK -> out.add("display", leftPos + 8, topPos + 38, 160, 52);
            case PROGRAM -> {
                out.add("program", code);
                out.add("check", leftPos + 8, topPos + rowY() - 16, windowWidth() - 16, 12);
                if (palette) {
                    chips.parts(out, font);
                }
            }
            case TRACE -> {
                out.add("trace", leftPos + 8, topPos + 20, 160, 70);
                debugger.parts(out);
            }
        }
    }
}
