package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.item.ProgramCardItem;
import io.github.metrovoc.residual.net.RelaySync;
import io.github.metrovoc.residual.net.TerminalAgentsSync;
import io.github.metrovoc.residual.net.TerminalAnswer;
import io.github.metrovoc.residual.net.TerminalAsk;
import io.github.metrovoc.residual.net.TerminalBaseSync;
import io.github.metrovoc.residual.net.TerminalCall;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.world.Relays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The base, from anywhere its relays reach. Map: the relay network as it stands, and a click on a gate goes there. Base:
 * what its storage networks hold, to open as if standing at them, and what its machines are doing. Agents: what each is
 * doing, to stop, start, hand a Program Card's program or give an order that the served model writes as a program. Writing:
 * the served model carries a text on. Each call takes its turn at the Model Server, and the screen shows what came back.
 */
public class TerminalScreen extends Screen {
    private enum Tab { MAP, BASE, AGENTS, WRITE }

    private static final int W = 316, H = 204;
    private static final int DISPLAY_X = 8, DISPLAY_Y = 20, DISPLAY_W = W - 16, DISPLAY_H = H - 28;
    private static final int LIST_W = 84, MAP_W = DISPLAY_W - LIST_W - 4;
    private static final int ROW = 18, GATE_ROW = 13;
    /** Ticks between two calls about what the tab shows. */
    private static final int POLL = 40;
    /** The agents' tab: the panel under their list, how many Program Cards it shows, and where those start from the left. */
    private static final int PANEL_H = 62, CARDS = 9, CARDS_X = 46;

    private static Tab lastTab = Tab.MAP;

    private final InteractionHand hand;
    private Tab tab = lastTab;
    @Nullable
    private RelayMap map;
    private boolean draggingMap;
    private double pressX, pressY;
    private int ticks;
    private int scroll;
    @Nullable
    private UUID selected;
    private EditBox prompt, order;
    private Button write, doIt, cancel;
    private IconButton stop, run, tell;
    @Nullable
    private IconButton dimension;
    /** What the order box holds, kept across tabs. */
    private String said = "";
    /** The lines a tab has laid out from what came back, and what they were laid out from. */
    private final List<AbstractWidget> rows = new ArrayList<>();
    @Nullable
    private Object shown;
    private int shownScroll = -1;
    private List<ItemStack> shownCards = List.of();

    public TerminalScreen(InteractionHand hand) {
        super(Component.translatable("item.residual.terminal"));
        this.hand = hand;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private static void send(TerminalCall call) {
        ClientPacketDistributor.sendToServer(call);
    }

    private int net() {
        GlobalPos link = minecraft.player.getItemInHand(hand).get(ModComponents.LINK.get());
        return RelayView.netOf(link);
    }

    @Override
    protected void init() {
        if (map != null) {
            map.close();
        }
        int x = left(), y = top();
        map = new RelayMap(x + DISPLAY_X, y + DISPLAY_Y, MAP_W, DISPLAY_H, this::net, minecraft.level.dimension(), minecraft.player.getX(), minecraft.player.getZ());
        send(TerminalCall.of(TerminalCall.Kind.MAP));
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        rows.clear();
        shown = null;
        shownScroll = -1;
        int x = left(), y = top();
        int i = 0;
        for (Tab t : Tab.values()) {
            String name = t.name().toLowerCase(Locale.ROOT);
            IconButton button = IconButton.action(x - 21, y + 4 + i++ * 20, "terminal_" + name, Component.translatable("residual.terminal.tab." + name),
                    Component.translatable("residual.terminal.tab." + name + ".help"), () -> {
                        tab = lastTab = t;
                        scroll = 0;
                        ticks = 0;
                        rebuild();
                    });
            button.active = t != tab;
            addRenderableWidget(button);
        }
        dimension = null;
        switch (tab) {
            case MAP -> {
                addRenderableWidget(IconButton.action(x - 21, y + 4 + i++ * 20 + 6, "terminal_fit", Component.translatable("residual.map.fit"),
                        Component.translatable("residual.map.fit.help"), () -> map.fit()));
                dimension = addRenderableWidget(IconButton.action(x - 21, y + 4 + i * 20 + 6, "terminal_dimension",
                        Component.translatable("residual.map.next_dimension"), Component.translatable("residual.map.next_dimension.help"), () -> {
                            List<ResourceKey<Level>> all = map.dimensions();
                            map.show(all.get((all.indexOf(map.dimension()) + 1) % all.size()));
                        }));
            }
            case AGENTS -> agentWidgets(x, y);
            case WRITE -> writeWidgets(x, y);
            case BASE -> {
            }
        }
    }

    private void agentWidgets(int x, int y) {
        int panel = y + DISPLAY_Y + DISPLAY_H - PANEL_H;
        stop = addRenderableWidget(IconButton.action(x + DISPLAY_X, panel + 16, "stop", Component.translatable("residual.program.stop"),
                Component.translatable("residual.program.stop.help"), () -> toSelected(TerminalCall.Kind.STOP, 0, "")));
        run = addRenderableWidget(IconButton.action(x + DISPLAY_X + 18, panel + 16, "run", Component.translatable("residual.program.run"),
                Component.translatable("residual.terminal.run.help"), () -> toSelected(TerminalCall.Kind.RUN, 0, "")));
        doIt = addRenderableWidget(Button.builder(Component.translatable("residual.agent.do_it"), b -> toSelected(TerminalCall.Kind.ANSWER, 1, ""))
                .bounds(x + W - 8 - 76, panel + 16, 34, 18).tooltip(Tooltip.create(Component.translatable("residual.agent.choice.yes.help"))).build());
        cancel = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> toSelected(TerminalCall.Kind.ANSWER, 0, ""))
                .bounds(x + W - 8 - 40, panel + 16, 40, 18).tooltip(Tooltip.create(Component.translatable("residual.agent.choice.no.help"))).build());
        order = addRenderableWidget(new EditBox(font, x + DISPLAY_X, panel + 42, DISPLAY_W - 22, 16, Component.translatable("residual.agent.order")));
        order.setMaxLength(60);
        order.setValue(said);
        order.setResponder(v -> said = v);
        order.setHint(Component.translatable("residual.terminal.order_hint"));
        setInitialFocus(order);
        tell = addRenderableWidget(IconButton.action(x + W - 8 - 18, panel + 41, "tell", Component.translatable("residual.agent.tell"),
                Component.translatable("residual.terminal.tell.help"), () -> {
                    if (!order.getValue().isBlank() && selected != null) {
                        toSelected(TerminalCall.Kind.ORDER, 0, order.getValue());
                        order.setValue("");
                    }
                }));
        syncAgentWidgets();
    }

    private void writeWidgets(int x, int y) {
        prompt = addRenderableWidget(new EditBox(font, x + DISPLAY_X, y + DISPLAY_Y, DISPLAY_W, 16, Component.translatable("residual.terminal.prompt")));
        prompt.setMaxLength(200);
        prompt.setHint(Component.translatable("residual.terminal.hint"));
        write = addRenderableWidget(Button.builder(Component.translatable("residual.terminal.write"), b -> {
            AgentView.asking = true;
            ClientPacketDistributor.sendToServer(new TerminalAsk(hand == InteractionHand.OFF_HAND, prompt.getValue()));
        }).bounds(x + DISPLAY_X, y + DISPLAY_Y + 20, 70, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("residual.terminal.clear"), b -> {
            prompt.setValue("");
            AgentView.answer = null;
        }).bounds(x + DISPLAY_X + 74, y + DISPLAY_Y + 20, 50, 16).build());
        setInitialFocus(prompt);
    }

    private void toSelected(TerminalCall.Kind kind, int number, String text) {
        if (selected != null) {
            send(TerminalCall.to(kind, selected, number, text));
        }
    }

    @Nullable
    private TerminalAgentsSync.Row selectedRow() {
        TerminalAgentsSync sync = RelayView.agents;
        if (sync == null) {
            return null;
        }
        for (TerminalAgentsSync.Row r : sync.agents()) {
            if (r.id().equals(selected)) {
                return r;
            }
        }
        return null;
    }

    private void syncAgentWidgets() {
        TerminalAgentsSync.Row row = selectedRow();
        boolean any = row != null;
        stop.active = any && !row.stopped();
        run.active = any && !row.program().isEmpty();
        tell.active = any;
        doIt.visible = cancel.visible = any && !row.proposed().isEmpty();
    }

    @Override
    public void tick() {
        if (ticks++ % POLL == 0) {
            if (tab == Tab.BASE) {
                send(TerminalCall.of(TerminalCall.Kind.BASE));
            } else if (tab == Tab.AGENTS) {
                send(TerminalCall.of(TerminalCall.Kind.AGENTS));
            }
        }
        if (tab == Tab.AGENTS) {
            // the first in the list, until one is picked
            if (selectedRow() == null && RelayView.agents != null && !RelayView.agents.agents().isEmpty()) {
                selected = RelayView.agents.agents().getFirst().id();
            }
            syncAgentWidgets();
        }
        if (dimension != null) {
            dimension.visible = map.dimensions().size() > 1;
        }
        layRows();
    }

    // ---------------------------------------------------------------- the lines of a tab

    private void clearRows() {
        rows.forEach(this::removeWidget);
        rows.clear();
    }

    private <T extends AbstractWidget> T addRow(T row) {
        rows.add(addRenderableWidget(row));
        return row;
    }

    /** Lays a tab's lines out again if what they show or how far they are scrolled has changed. */
    private void layRows() {
        switch (tab) {
            case MAP -> {
                List<RelaySync.Part> gates = map.gates();
                if (!gates.equals(shown) || shownScroll != scroll) {
                    shown = gates;
                    shownScroll = scroll;
                    layGates(gates);
                }
            }
            case BASE -> {
                if (RelayView.base != shown || shownScroll != scroll) {
                    shown = RelayView.base;
                    shownScroll = scroll;
                    layBase();
                }
            }
            case AGENTS -> {
                List<ItemStack> cards = cards();
                boolean same = cards.size() == shownCards.size();
                for (int i = 0; same && i < cards.size(); i++) {
                    same = ItemStack.matches(cards.get(i), shownCards.get(i));
                }
                if (RelayView.agents != shown || shownScroll != scroll || !same) {
                    shown = RelayView.agents;
                    shownScroll = scroll;
                    shownCards = cards;
                    layAgents(cards);
                }
            }
            case WRITE -> {
            }
        }
    }

    private void layGates(List<RelaySync.Part> gates) {
        clearRows();
        int x = left() + DISPLAY_X + MAP_W + 4, y = top() + DISPLAY_Y, fit = DISPLAY_H / GATE_ROW;
        scroll = Math.clamp(scroll, 0, Math.max(0, gates.size() - fit));
        for (int i = scroll; i < gates.size() && i - scroll < fit; i++) {
            RelaySync.Part gate = gates.get(i);
            addRow(new ListRow(x, y + (i - scroll) * GATE_ROW, LIST_W, GATE_ROW, Component.literal(RelayMap.name(gate)), Ui.TEXT, () -> jump(gate)))
                    .setTooltip(Tooltip.create(RelayMap.tooltip(gate, true)));
        }
    }

    private record BaseRow(@Nullable TerminalBaseSync.Storage storage, @Nullable TerminalBaseSync.Machine machine) {
    }

    private void layBase() {
        clearRows();
        TerminalBaseSync base = RelayView.base;
        if (base == null || !base.problem().isEmpty()) {
            return;
        }
        List<BaseRow> all = new ArrayList<>();
        base.storage().forEach(s -> all.add(new BaseRow(s, null)));
        base.machines().forEach(m -> all.add(new BaseRow(null, m)));
        int x = left() + DISPLAY_X, y = top() + DISPLAY_Y, fit = DISPLAY_H / ROW;
        scroll = Math.clamp(scroll, 0, Math.max(0, all.size() - fit));
        for (int i = scroll; i < all.size() && i - scroll < fit; i++) {
            BaseRow row = all.get(i);
            int ry = y + (i - scroll) * ROW;
            if (row.storage() != null) {
                TerminalBaseSync.Storage s = row.storage();
                addRow(new ListRow(x, ry, DISPLAY_W, ROW, new ItemStack(ModBlocks.STORAGE_TERMINAL.get()),
                        Component.translatable("residual.terminal.storage", Ui.count(s.stored()), Ui.count(s.kinds())), Ui.TEXT, Component.empty(), 0, Ui.DIM,
                        () -> send(TerminalCall.at(TerminalCall.Kind.STORAGE, s.terminal(), ""))))
                        .setTooltip(Tooltip.create(Component.translatable("residual.terminal.storage.help", s.terminal().pos().toShortString())));
            } else {
                TerminalBaseSync.Machine m = row.machine();
                int color = m.state() == TerminalBaseSync.WORKING ? Ui.TEAL : m.state() == TerminalBaseSync.WAITING ? Ui.AMBER : Ui.DIM;
                addRow(new ListRow(x, ry, DISPLAY_W, ROW, m.icon(), m.icon().getHoverName(), Ui.TEXT, m.doing(), 124, color, null))
                        .tooltip(List.of(m.icon().getHoverName(), m.doing(), where(m.at())));
            }
        }
    }

    /** Where something stands, with its dimension if that is not the player's. */
    private Component where(GlobalPos at) {
        return at.dimension().equals(minecraft.level.dimension()) ? Component.translatable("residual.map.at", at.pos().toShortString())
                : Component.translatable("residual.map.at_in", at.pos().toShortString(), RelayMap.dimensionName(at.dimension()));
    }

    private void layAgents(List<ItemStack> cards) {
        clearRows();
        int x = left() + DISPLAY_X, y = top() + DISPLAY_Y, panel = y + DISPLAY_H - PANEL_H, fit = (DISPLAY_H - PANEL_H - 4) / ROW;
        TerminalAgentsSync sync = RelayView.agents;
        if (sync != null && sync.problem().isEmpty()) {
            scroll = Math.clamp(scroll, 0, Math.max(0, sync.agents().size() - fit));
            for (int i = scroll; i < sync.agents().size() && i - scroll < fit; i++) {
                TerminalAgentsSync.Row row = sync.agents().get(i);
                Component status = Component.translatable(row.status(), row.args().toArray());
                addRow(new ListRow(x, y + (i - scroll) * ROW, DISPLAY_W, ROW, new ItemStack(ModItems.AGENT.get()), Component.literal(row.name()), Ui.TEXT, status, 88,
                        !row.proposed().isEmpty() ? Ui.AMBER : row.stopped() ? Ui.DIM : Ui.TEAL, () -> {
                    selected = row.id();
                    shown = null;
                    syncAgentWidgets();
                })).selected(row.id().equals(selected))
                        .tooltip(List.of(Component.literal(row.name()), status, where(row.at())));
            }
        }
        List<Integer> slots = cardSlots();
        for (int i = 0; i < cards.size(); i++) {
            ItemStack card = cards.get(i);
            int slot = slots.get(i);
            IconButton button = new IconButton(left() + CARDS_X + i * 18, panel + 16, 18, 18, card, card.getHoverName(), () -> toSelected(TerminalCall.Kind.CARD, slot, ""));
            button.setTooltip(Tooltip.create(Component.translatable("residual.terminal.card.help").append("\n").append(Component.literal(ProgramCardItem.program(card)))));
            addRow(button);
        }
    }

    /** The inventory slots holding written Program Cards, as many as the panel shows. */
    private List<Integer> cardSlots() {
        List<Integer> out = new ArrayList<>();
        var inventory = minecraft.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && out.size() < CARDS; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.PROGRAM_CARD.get()) && !ProgramCardItem.program(stack).isEmpty()) {
                out.add(i);
            }
        }
        return out;
    }

    private List<ItemStack> cards() {
        List<ItemStack> out = new ArrayList<>();
        cardSlots().forEach(i -> out.add(minecraft.player.getInventory().getItem(i).copy()));
        return out;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            if (tab == Tab.AGENTS && order != null) {
                // the order box keeps the keys after anything else on the tab is clicked
                setFocused(order);
                setDragging(false);
            }
            return true;
        }
        if (tab == Tab.MAP && map.inside(event.x(), event.y())) {
            draggingMap = true;
            pressX = event.x();
            pressY = event.y();
            return true;
        }
        return false;
    }

    private void jump(RelaySync.Part gate) {
        if (gate.type() == Relays.Kind.GATE) {
            send(TerminalCall.at(TerminalCall.Kind.JUMP, gate.at(), ""));
            onClose();
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingMap) {
            map.dragged(dx, dy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingMap) {
            draggingMap = false;
            if (Math.abs(event.x() - pressX) + Math.abs(event.y() - pressY) < 3) {
                RelaySync.Part part = map.under(event.x(), event.y());
                if (part != null) {
                    jump(part);
                }
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (tab == Tab.MAP && map.inside(mx, my)) {
            map.scrolled(mx, my, scrollY);
        } else if (tab != Tab.WRITE) {
            scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
            layRows();
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (tab == Tab.WRITE && event.isConfirmation() && prompt != null && prompt.isFocused() && write.active) {
            write.onPress(event);
            return true;
        }
        if (tab == Tab.AGENTS && event.isConfirmation() && order != null && order.isFocused()) {
            tell.onPress(event);
            return true;
        }
        return super.keyPressed(event);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = left(), y = top();
        Ui.window(g, x, y, W, H);
        g.text(font, title, x + 8, y + 7, Ui.LABEL, false);
        reachLine(g, x, y);
        switch (tab) {
            case MAP -> mapTab(g, x, y, mouseX, mouseY);
            case BASE -> baseTab(g, x, y);
            case AGENTS -> agentsTab(g, x, y, mouseX, mouseY);
            case WRITE -> writeTab(g, x, y);
        }
        super.extractRenderState(g, mouseX, mouseY, a);
        if (tab == Tab.MAP) {
            RelaySync.Part part = map.under(mouseX, mouseY);
            if (part != null) {
                g.setTooltipForNextFrame(font.split(RelayMap.tooltip(part, true), 220), mouseX, mouseY);
            }
        }
    }

    /** Whether the Terminal reaches its server from here, at the top of the window. */
    private void reachLine(GuiGraphicsExtractor g, int x, int y) {
        ItemStack held = minecraft.player.getItemInHand(hand);
        int net = net();
        RelaySync.Stats stats = RelayView.nets.get(net);
        Component line;
        int color = Ui.LABEL_DIM;
        if (held.get(ModComponents.LINK.get()) == null) {
            line = Component.translatable("residual.link.none");
        } else if (stats == null || RelayView.margin(net, minecraft.level.dimension(), minecraft.player.getX(), minecraft.player.getZ()) < 0) {
            line = Component.translatable("residual.terminal.out_of_reach");
            color = 0xFFB25A00;
        } else {
            int waited = tab == Tab.BASE && RelayView.base != null ? RelayView.base.waited()
                    : tab == Tab.AGENTS && RelayView.agents != null ? RelayView.agents.waited() : -1;
            line = !stats.live() ? Component.translatable("residual.terminal.no_model")
                    : waited >= 0 ? Component.translatable("residual.terminal.answered", stats.capacity(), waited)
                    : Component.translatable("residual.terminal.in_reach", stats.capacity());
        }
        String text = Ui.fit(font, line.getString(), W - 16 - font.width(title) - 8);
        g.text(font, text, x + W - 8 - font.width(text), y + 7, color, false);
    }

    private void mapTab(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        Ui.screen(g, x + DISPLAY_X, y + DISPLAY_Y, MAP_W, DISPLAY_H);
        map.draw(g, mouseX, mouseY);
        Ui.screen(g, x + DISPLAY_X + MAP_W + 4, y + DISPLAY_Y, LIST_W, DISPLAY_H);
        if (map.gates().isEmpty()) {
            lines(g, Component.translatable("residual.map.no_gates"), x + DISPLAY_X + MAP_W + 8, y + DISPLAY_Y + 4, LIST_W - 8, Ui.DIM);
        }
    }

    private void baseTab(GuiGraphicsExtractor g, int x, int y) {
        int dx = x + DISPLAY_X, dy = y + DISPLAY_Y;
        Ui.screen(g, dx, dy, DISPLAY_W, DISPLAY_H);
        TerminalBaseSync base = RelayView.base;
        if (base == null) {
            lines(g, Component.translatable("residual.terminal.thinking"), dx + 4, dy + 4, DISPLAY_W - 8, Ui.DIM);
        } else if (!base.problem().isEmpty()) {
            lines(g, Component.translatable(base.problem()), dx + 4, dy + 4, DISPLAY_W - 8, Ui.AMBER);
        } else if (base.storage().isEmpty() && base.machines().isEmpty()) {
            lines(g, Component.translatable("residual.terminal.no_machines"), dx + 4, dy + 4, DISPLAY_W - 8, Ui.DIM);
        }
    }

    private void agentsTab(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        int dx = x + DISPLAY_X, dy = y + DISPLAY_Y, listH = DISPLAY_H - PANEL_H - 4;
        Ui.screen(g, dx, dy, DISPLAY_W, listH);
        int panel = y + DISPLAY_Y + DISPLAY_H - PANEL_H;
        TerminalAgentsSync.Row chosen = selectedRow();
        Ui.screen(g, dx, panel, DISPLAY_W, 12);
        if (chosen != null) {
            String program = chosen.proposed().isEmpty() ? chosen.program() : chosen.proposed();
            Component line = program.isEmpty() ? Component.translatable("residual.agent.status.resting") : Component.literal(program);
            g.text(font, Ui.fit(font, line.getString(), DISPLAY_W - 8), dx + 4, panel + 2, chosen.proposed().isEmpty() ? Ui.DIM : Ui.AMBER, false);
            if (Ui.inside(mouseX, mouseY, dx, panel, DISPLAY_W, 12) && !program.isEmpty()) {
                g.setTooltipForNextFrame(font.split(Component.literal(program), 220), mouseX, mouseY);
            }
        }
        for (int i = 0; i < CARDS; i++) {
            Ui.slot(g, x + CARDS_X + 1 + i * 18, panel + 17);
        }
        TerminalAgentsSync sync = RelayView.agents;
        if (sync == null) {
            lines(g, Component.translatable("residual.terminal.thinking"), dx + 4, dy + 4, DISPLAY_W - 8, Ui.DIM);
        } else if (!sync.problem().isEmpty()) {
            lines(g, Component.translatable(sync.problem()), dx + 4, dy + 4, DISPLAY_W - 8, Ui.AMBER);
        } else if (sync.agents().isEmpty()) {
            lines(g, Component.translatable("residual.terminal.no_agents"), dx + 4, dy + 4, DISPLAY_W - 8, Ui.DIM);
        }
    }

    private void writeTab(GuiGraphicsExtractor g, int x, int y) {
        write.active = !AgentView.asking;
        int dx = x + DISPLAY_X, dy = y + DISPLAY_Y + 40;
        Ui.screen(g, dx, dy, DISPLAY_W, DISPLAY_H - 40);
        TerminalAnswer answer = AgentView.answer;
        if (AgentView.asking) {
            lines(g, Component.translatable("residual.terminal.thinking"), dx + 4, dy + 4, DISPLAY_W - 8, Ui.DIM);
        } else if (answer != null && !answer.problem().isEmpty()) {
            lines(g, Component.translatable(answer.problem(), answer.args().toArray()), dx + 4, dy + 4, DISPLAY_W - 8, Ui.AMBER);
        } else if (answer != null) {
            Component text = Component.literal(answer.prompt()).withColor(Ui.DIM).append(Component.literal(answer.text()).withColor(Ui.TEXT));
            List<FormattedCharSequence> lines = font.split(text, DISPLAY_W - 8);
            for (int i = 0; i < Math.min(lines.size(), (DISPLAY_H - 44) / 10); i++) {
                g.text(font, lines.get(i), dx + 4, dy + 4 + i * 10, Ui.TEXT, false);
            }
        }
    }

    private void lines(GuiGraphicsExtractor g, Component message, int x, int y, int w, int color) {
        int i = 0;
        for (FormattedCharSequence line : font.split(message, w)) {
            g.text(font, line, x, y + 10 * i++, color, false);
        }
    }

    @Override
    public void removed() {
        super.removed();
        AgentView.asking = false;
        if (map != null) {
            map.close();
            map = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
