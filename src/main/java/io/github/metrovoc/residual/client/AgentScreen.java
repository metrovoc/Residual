package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.agent.Agent;
import io.github.metrovoc.residual.agent.Job;
import io.github.metrovoc.residual.agent.Program;
import io.github.metrovoc.residual.agent.Things;
import io.github.metrovoc.residual.agent.Trace;
import io.github.metrovoc.residual.item.ProgramCardItem;
import io.github.metrovoc.residual.menu.AgentMenu;
import io.github.metrovoc.residual.model.ModelInfo;
import io.github.metrovoc.residual.net.AgentAction;
import io.github.metrovoc.residual.net.AgentSync;
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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * An agent's screen. Work: its job, what it is doing and the last thing it asked its model. Program: the program it runs,
 * to read and rewrite, checked as it is typed, with a palette beside it of the pieces to click together. Trace: what it
 * has been doing, step by step. Below: what it carries.
 */
public class AgentScreen extends AbstractContainerScreen<AgentMenu> {
    private enum Tab { WORK, PROGRAM, TRACE }

    /** How wide the palette is, beside the window on its left. */
    private static final int PALETTE = 150;

    private static Tab lastTab = Tab.WORK;
    /** Whether the palette is open beside the Program tab; it stays so while the game runs. */
    private static boolean palette;
    private Tab tab = lastTab;
    /** Where the palette's headings are, down from its top. */
    private final List<Integer> headings = new ArrayList<>();
    private final List<IconButton> jobs = new ArrayList<>();
    /** Yes and no to an order it read back, shown while it waits for one. */
    @Nullable
    private Button doIt, cancel;
    private EditBox order;
    private Button tell;
    @Nullable
    private MultiLineEditBox code;
    @Nullable
    private Button run;
    /** What the program box holds, kept across tabs; null until the agent's program arrives. */
    @Nullable
    private String draft;
    /** Whether the player has changed the box since it last showed the agent's program. */
    private boolean edited;
    private boolean syncing;
    private Program.Parsed parsed = Program.parse("");

    public AgentScreen(AgentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 248);
    }

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
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        jobs.clear();
        doIt = cancel = null;
        code = null;
        run = null;
        leftPos = (width - imageWidth) / 2;
        if (paletteShown()) {
            // room on the left for the palette, the way the recipe book makes room
            leftPos = Math.min(Math.max(leftPos, PALETTE + 8), width - imageWidth);
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
            }
            case PROGRAM -> {
                code = addRenderableWidget(MultiLineEditBox.builder().setX(x + 8).setY(y + 20).setShowBackground(false).setShowDecorations(false)
                        .setTextColor(Ui.TEXT).setCursorColor(Ui.TEAL).setPlaceholder(Component.translatable("residual.program.hint"))
                        .build(font, 160, 42, Component.translatable("residual.tab.program")));
                code.setCharacterLimit(Program.LONGEST);
                code.setValue(draft == null ? "" : draft);
                parsed = Program.parse(code.getValue());
                code.setValueListener(v -> {
                    parsed = Program.parse(v);
                    if (!syncing) {
                        draft = v;
                        edited = true;
                    }
                });
                run = addRenderableWidget(Button.builder(Component.translatable("residual.program.run"), b -> {
                    send(AgentAction.Kind.PROGRAM, 0, code.getValue());
                    edited = false;
                }).bounds(x + 8, y + 76, 36, 14).tooltip(Tooltip.create(Component.translatable("residual.program.run.help"))).build());
                addRenderableWidget(Button.builder(Component.translatable("residual.program.stop"), b -> send(AgentAction.Kind.STOP, 0, ""))
                        .bounds(x + 46, y + 76, 36, 14).tooltip(Tooltip.create(Component.translatable("residual.program.stop.help"))).build());
                addRenderableWidget(Button.builder(Component.literal(palette ? "−" : "+"), b -> {
                    palette = !palette;
                    rebuild();
                }).bounds(x + 84, y + 76, 14, 14).tooltip(Tooltip.create(Component.translatable(palette ? "residual.palette.hide"
                        : "residual.palette.show"))).build());
                if (palette) {
                    layPalette(x - PALETTE - 4, y);
                }
            }
            case TRACE -> {
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("residual.agent.set_home"), b -> send(AgentAction.Kind.HOME, 0, ""))
                .bounds(x + 122, y + 126, 48, 14).tooltip(Tooltip.create(Component.translatable("residual.agent.set_home.help"))).build());
        String said = order == null ? "" : order.getValue();
        order = addRenderableWidget(new EditBox(font, x + 8, y + 146, 118, 14, Component.translatable("residual.agent.order")));
        order.setMaxLength(60);
        order.setValue(said);
        order.setHint(Component.translatable("residual.agent.order_hint"));
        tell = addRenderableWidget(Button.builder(Component.translatable("residual.agent.tell"), b -> {
            if (!order.getValue().isBlank()) {
                send(AgentAction.Kind.SAY, 0, order.getValue());
                order.setValue("");
                if (tab != Tab.WORK) {
                    // what it makes of the order shows on the Work tab, and its yes or no with it
                    tab = lastTab = Tab.WORK;
                    rebuild();
                    setFocused(order);
                }
            }
        }).bounds(x + 130, y + 145, 40, 16).build());
    }

    /** Lays the chips out in rows, kind by kind, on the palette whose top left is {@code (px, py)}. */
    private void layPalette(int px, int py) {
        headings.clear();
        int y = py + 7;
        for (Palette.Group group : Palette.GROUPS) {
            headings.add(y - py);
            y += 11;
            int x = px + 6;
            for (Palette.Piece piece : group.pieces()) {
                Palette.Chip chip = new Palette.Chip(x, y, piece, group.color(), () -> add(piece.text(), piece.join()));
                if (x > px + 6 && x + chip.getWidth() > px + PALETTE - 6) {
                    x = px + 6;
                    y += Palette.Chip.HEIGHT + 2;
                    chip.setPosition(x, y);
                }
                addRenderableWidget(chip);
                x += chip.getWidth() + 2;
            }
            y += Palette.Chip.HEIGHT + 6;
        }
    }

    private boolean paletteShown() {
        return palette && tab == Tab.PROGRAM;
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
                code.setValue(draft);
                syncing = false;
            }
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
            if (order.isMouseOver(event.x(), event.y())) {
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
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        return super.hasClickedOutside(mx, my, xo, yo) && !(paletteShown() && Ui.inside(mx, my, xo - PALETTE - 4, yo, PALETTE, imageHeight));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            return super.keyPressed(event);
        }
        if (order != null && order.isFocused()) {
            if (event.isConfirmation()) {
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
        if (paletteShown()) {
            int px = leftPos - PALETTE - 4;
            Ui.window(g, px, topPos, PALETTE, imageHeight);
            for (int i = 0; i < headings.size(); i++) {
                g.text(font, Component.translatable(Palette.GROUPS.get(i).title()), px + 7, topPos + headings.get(i), Ui.LABEL, false);
            }
            List<FormattedCharSequence> note = font.split(Component.translatable("residual.palette.note"), PALETTE - 14);
            for (int i = 0; i < note.size(); i++) {
                g.text(font, note.get(i), px + 7, topPos + imageHeight - 7 - 9 * (note.size() - i), Ui.LABEL_DIM, false);
            }
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
            case PROGRAM -> Ui.screen(g, leftPos + 8, topPos + 20, 160, 54);
            case TRACE -> Ui.screen(g, leftPos + 8, topPos + 20, 160, 70);
        }
        for (Slot slot : menu.slots) {
            Ui.slot(g, leftPos + slot.x, topPos + slot.y);
        }
        AgentSync s = sync();
        for (int i = 0; i < jobs.size(); i++) {
            jobs.get(i).active = s == null || s.job() != i;
        }
        // an empty model slot shows faintly what goes in it
        boolean noModel = !menu.getSlot(Agent.MODEL).hasItem(), noFilter = !menu.getSlot(Agent.FILTER).hasItem();
        int gy = topPos + AgentMenu.MODEL_Y;
        if (noModel) {
            g.item(new ItemStack(ModItems.CHECKPOINT.get()), leftPos + AgentMenu.MODEL_X, gy);
        }
        if (noFilter) {
            g.item(new ItemStack(Items.HOPPER), leftPos + AgentMenu.FILTER_X, gy);
        }
        g.nextStratum();
        if (noModel) {
            g.fill(leftPos + AgentMenu.MODEL_X, gy, leftPos + AgentMenu.MODEL_X + 16, gy + 16, 0xB08B8B8B);
        }
        if (noFilter) {
            g.fill(leftPos + AgentMenu.FILTER_X, gy, leftPos + AgentMenu.FILTER_X + 16, gy + 16, 0xB08B8B8B);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int tabs = 8;
        for (Tab t : Tab.values()) {
            tabs += font.width(tabTitle(t)) + 12;
        }
        String name = Ui.fit(font, title.getString(), imageWidth - 8 - tabs);
        g.text(font, name, imageWidth - 8 - font.width(name), 7, Ui.LABEL_DIM, false);
        g.text(font, Component.translatable("residual.agent.carrying"), AgentMenu.CARRY_X, 94, Ui.LABEL, false);
        Component models = Component.translatable("residual.agent.model_slots");
        g.text(font, models, (AgentMenu.FILTER_X + AgentMenu.MODEL_X + 16 - font.width(models)) / 2, 94, Ui.LABEL, false);
        int x = mouseX - leftPos, y = mouseY - topPos;
        for (int slot : new int[]{Agent.MODEL, Agent.FILTER}) {
            Slot at = menu.getSlot(slot);
            if (!at.hasItem() && Ui.inside(x, y, at.x - 1, at.y - 1, 18, 18)) {
                String key = slot == Agent.MODEL ? "residual.agent.model_slot.help" : "residual.agent.filter_slot.help";
                g.setTooltipForNextFrame(font.split(Component.translatable(key), 200), mouseX, mouseY);
            }
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
            case TRACE -> traceTab(g, s, mx, my, mouseX, mouseY);
        }
        if (!menu.getCarried().isEmpty() && Ui.inside(mx, my, 8, 146, 118, 14)) {
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
        if (s.decisions() == 0 || !working) {
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
        if (Ui.inside(mx, my, 8, 38, 160, 52) && (font.width(status) > w || font.width(modelLine) > w)) {
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
        g.text(font, fit(check, 152), 12, 64, color, false);
        Component status = status(s);
        g.text(font, fit(status, 66), 102, 79, Ui.LABEL, false);
        List<FormattedCharSequence> tip = new ArrayList<>();
        ItemStack held = menu.getCarried();
        if (Ui.inside(mx, my, 8, 62, 160, 12) && font.width(check) > 152) {
            tip.addAll(font.split(check, 220));
        } else if (Ui.inside(mx, my, 100, 76, 68, 14)) {
            tip.addAll(font.split(status, 220));
        } else if (Ui.inside(mx, my, 8, 20, 160, 42) && !held.isEmpty()) {
            Component hint = !held.is(ModItems.PROGRAM_CARD.get()) ? named(held)
                    : Component.translatable(ProgramCardItem.program(held).isEmpty() ? "residual.program.drop.blank" : "residual.program.drop.card");
            tip.addAll(font.split(hint, 220));
        } else if (Ui.inside(mx, my, 8, 20, 160, 42) && code != null && code.getValue().isEmpty()) {
            tip.addAll(font.split(Component.translatable("residual.program.help"), 240));
        }
        if (!tip.isEmpty()) {
            g.setTooltipForNextFrame(tip, mouseX, mouseY);
        }
    }

    private void traceTab(GuiGraphicsExtractor g, AgentSync s, int mx, int my, int mouseX, int mouseY) {
        List<Trace> lines = s.trace();
        if (lines.isEmpty()) {
            g.text(font, Component.translatable("residual.trace.none"), 12, 24, Ui.DIM, false);
            return;
        }
        int shown = Math.min(7, lines.size());
        for (int i = 0; i < shown; i++) {
            int at = lines.size() - shown + i;
            Trace t = lines.get(at);
            // more of how a step went, such as each stack sorted, goes under the line that names the step, in line with it
            boolean under = i > 0 && outcome(t) && outcome(lines.get(at - 1)) && lines.get(at - 1).step().equals(t.step());
            Component line = line(t, under);
            int x = under ? 12 + font.width("✔ ") : 12, y = 23 + i * 9;
            g.text(font, fit(line, 164 - x), x, y, color(t.kind()), false);
            if (Ui.inside(mx, my, 8, y - 1, 160, 9) && font.width(line) > 164 - x) {
                g.setTooltipForNextFrame(font.split(line(t, false), 220), mouseX, mouseY);
            }
        }
    }

    private static boolean outcome(Trace t) {
        return (t.kind() == Trace.Kind.DONE || t.kind() == Trace.Kind.FAILED) && !t.key().isEmpty();
    }

    /**
     * One line of the trace: a mark, the step, and how it went. A line {@code under} one naming its step says only how it
     * went, and only a failure keeps its mark.
     */
    private static Component line(Trace t, boolean under) {
        String mark = switch (t.kind()) {
            case RUNNING -> "▸ ";
            case DONE -> under ? "" : "✔ ";
            case FAILED -> "✘ ";
            case YES, NO -> "? ";
            case NOTE -> "• ";
        };
        Component body = switch (t.kind()) {
            case NOTE -> Component.translatable(t.key(), t.step());
            case YES, NO -> Component.translatable(t.kind() == Trace.Kind.YES ? "residual.trace.yes" : "residual.trace.no", t.step());
            default -> t.key().isEmpty() ? Component.literal(t.step())
                    : under ? Component.translatable(t.key(), t.args().toArray())
                    : Component.translatable("residual.trace.outcome", t.step(), Component.translatable(t.key(), t.args().toArray()));
        };
        Component out = Component.literal(mark).append(body);
        return t.times() > 1 ? out.copy().append(Component.translatable("residual.trace.times", t.times())) : out;
    }

    private static int color(Trace.Kind kind) {
        return switch (kind) {
            case RUNNING -> Ui.TEXT;
            case DONE -> Ui.GREEN;
            case FAILED -> Ui.RED;
            case YES, NO -> Ui.DIM;
            case NOTE -> Ui.AMBER;
        };
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
}
