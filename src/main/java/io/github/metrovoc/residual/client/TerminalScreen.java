package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.net.RelaySync;
import io.github.metrovoc.residual.net.TerminalAnswer;
import io.github.metrovoc.residual.net.TerminalAsk;
import io.github.metrovoc.residual.net.TerminalCall;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.world.Relays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * A Terminal's screen. Map: the relay network as it stands, and a click on a gate goes there, from anywhere it reaches.
 * Writing: the served model carries a text on.
 */
public class TerminalScreen extends Screen {
    private enum Tab { MAP, WRITE }

    private static final int W = 316, H = 204;
    private static final int DISPLAY_X = 8, DISPLAY_Y = 20, DISPLAY_W = W - 16, DISPLAY_H = H - 28;
    private static final int LIST_W = 84, MAP_W = DISPLAY_W - LIST_W - 4;
    private static final int GATE_ROW = 13;

    private static Tab lastTab = Tab.MAP;

    private final InteractionHand hand;
    private Tab tab = lastTab;
    @Nullable
    private RelayMap map;
    private boolean draggingMap;
    private double pressX, pressY;
    private int scroll;
    private EditBox prompt;
    private Button write;
    @Nullable
    private IconButton dimension;
    /** The lines a tab has laid out from what came back, and what they were laid out from. */
    private final List<AbstractWidget> rows = new ArrayList<>();
    @Nullable
    private Object shown;
    private int shownScroll = -1;

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
            case WRITE -> writeWidgets(x, y);
        }
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

    @Override
    public void tick() {
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

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
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
        } else if (tab == Tab.MAP) {
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
            line = stats.live() ? Component.translatable("residual.terminal.in_reach", stats.capacity())
                    : Component.translatable("residual.terminal.no_model");
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
