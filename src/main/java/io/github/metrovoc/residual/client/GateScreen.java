package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.net.RelaySync;
import io.github.metrovoc.residual.net.TerminalCall;
import io.github.metrovoc.residual.world.Relays;
import io.github.metrovoc.residual.world.TerminalApi;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;

/** A Relay Gate's screen: its name, and its network on a map, with every other gate on it to go to. */
public class GateScreen extends Screen implements Parts.Drawn {
    private static final int W = 316, H = 204;
    private static final int DISPLAY_X = 8, DISPLAY_Y = 32, DISPLAY_W = W - 16, DISPLAY_H = H - 40;
    private static final int LIST_W = 84, MAP_W = DISPLAY_W - LIST_W - 4, GATE_ROW = 13, NAME_W = 90;
    private static final int POLL = 40;

    private final GlobalPos gate;
    @Nullable
    private RelayMap map;
    private EditBox name;
    private IconButton dimension;
    private boolean draggingMap;
    private double pressX, pressY;
    private int ticks;
    private int scroll;
    private final List<AbstractWidget> rows = new ArrayList<>();
    @Nullable
    private List<RelaySync.Part> shown;
    private int shownScroll = -1;
    /** The name last sent, and whether the box has been filled from what the network says. */
    private String sentName = "";
    private boolean named;

    public GateScreen(GlobalPos gate) {
        super(Component.translatable("block.residual.relay_gate"));
        this.gate = gate;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int net() {
        return RelayView.netOf(gate);
    }

    private static void send(TerminalCall call) {
        ClientPacketDistributor.sendToServer(call);
    }

    @Override
    protected void init() {
        if (map != null) {
            map.close();
        }
        int x = left(), y = top();
        map = new RelayMap(x + DISPLAY_X, y + DISPLAY_Y, MAP_W, DISPLAY_H, this::net, gate.dimension(), gate.pos().getX(), gate.pos().getZ());
        send(TerminalCall.of(TerminalCall.Kind.MAP));
        rows.clear();
        shown = null;
        name = addRenderableWidget(new EditBox(font, x + DISPLAY_X, y + 16, NAME_W, 14, Component.translatable("residual.relay_gate.name")));
        name.setMaxLength(32);
        name.setHint(Component.translatable("residual.relay_gate.name"));
        name.setValue(sentName);
        addRenderableWidget(IconButton.action(x - 21, y + 4, "terminal_fit", Component.translatable("residual.map.fit"),
                Component.translatable("residual.map.fit.help"), () -> map.fit()));
        dimension = addRenderableWidget(IconButton.action(x - 21, y + 24, "terminal_dimension", Component.translatable("residual.map.next_dimension"),
                Component.translatable("residual.map.next_dimension.help"), () -> {
                    List<ResourceKey<Level>> all = map.dimensions();
                    map.show(all.get((all.indexOf(map.dimension()) + 1) % all.size()));
                }));
        dimension.visible = false;
    }

    @Override
    public void tick() {
        if (++ticks % POLL == 0) {
            send(TerminalCall.of(TerminalCall.Kind.MAP));
        }
        if (!named) {
            for (RelaySync.Part p : RelayView.parts) {
                if (p.at().equals(gate)) {
                    named = true;
                    sentName = p.name();
                    name.setValue(sentName);
                }
            }
        }
        dimension.visible = map.dimensions().size() > 1;
        List<RelaySync.Part> gates = map.gates();
        if (!gates.equals(shown) || shownScroll != scroll) {
            shown = gates;
            shownScroll = scroll;
            layGates(gates);
        }
    }

    private void layGates(List<RelaySync.Part> gates) {
        rows.forEach(this::removeWidget);
        rows.clear();
        int x = left() + DISPLAY_X + MAP_W + 4, y = top() + DISPLAY_Y, fit = DISPLAY_H / GATE_ROW;
        scroll = Math.clamp(scroll, 0, Math.max(0, gates.size() - fit));
        for (int i = scroll; i < gates.size() && i - scroll < fit; i++) {
            RelaySync.Part to = gates.get(i);
            boolean here = to.at().equals(gate);
            ListRow row = new ListRow(x, y + (i - scroll) * GATE_ROW, LIST_W, GATE_ROW, Component.literal(RelayMap.name(to)), here ? Ui.DIM : Ui.TEXT,
                    here ? null : () -> jump(to));
            row.setTooltip(Tooltip.create(RelayMap.tooltip(to, !here)));
            rows.add(addRenderableWidget(row));
        }
    }

    private void saveName() {
        String now = name.getValue().strip();
        if (named && !now.equals(sentName)) {
            sentName = now;
            send(TerminalCall.at(TerminalCall.Kind.GATE_NAME, gate, now));
        }
    }

    private void jump(RelaySync.Part to) {
        if (to.type() == Relays.Kind.GATE && !to.at().equals(gate)) {
            saveName();
            send(TerminalCall.at(TerminalCall.Kind.GATE_JUMP, gate, TerminalApi.place(to.at())));
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (map.inside(event.x(), event.y())) {
            draggingMap = true;
            pressX = event.x();
            pressY = event.y();
            return true;
        }
        return false;
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
        if (map.inside(mx, my)) {
            map.scrolled(mx, my, scrollY);
        } else {
            scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && name.isFocused()) {
            saveName();
            setFocused(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = left(), y = top();
        Ui.window(g, x, y, W, H);
        g.text(font, title, x + 8, y + 6, Ui.LABEL, false);
        RelaySync.Stats stats = RelayView.nets.get(net());
        Component status = stats != null && stats.open() ? Component.translatable("residual.relay_gate.open", stats.capacity()) : RelayView.closed(stats);
        int statusW = W - 16 - NAME_W - 6;
        String text = Ui.fit(font, status.getString(), statusW);
        g.text(font, text, x + W - 8 - font.width(text), y + 19, stats != null && stats.open() ? Ui.LABEL_DIM : 0xFFB25A00, false);
        Ui.screen(g, x + DISPLAY_X, y + DISPLAY_Y, MAP_W, DISPLAY_H);
        map.draw(g, mouseX, mouseY);
        Ui.screen(g, x + DISPLAY_X + MAP_W + 4, y + DISPLAY_Y, LIST_W, DISPLAY_H);
        super.extractRenderState(g, mouseX, mouseY, a);
        RelaySync.Part part = map.under(mouseX, mouseY);
        if (part != null) {
            g.setTooltipForNextFrame(font.split(RelayMap.tooltip(part, !part.at().equals(gate)), 220), mouseX, mouseY);
        } else if (Ui.inside(mouseX, mouseY, x + W - 8 - statusW, y + 16, statusW, 14) && !text.equals(status.getString())) {
            g.setTooltipForNextFrame(font.split(status, 220), mouseX, mouseY);
        }
    }

    @Override
    public void removed() {
        super.removed();
        saveName();
        if (map != null) {
            map.close();
            map = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void parts(Parts out) {
        int x = left(), y = top();
        out.add("window", x, y, W, H);
        out.add("status", x + DISPLAY_X + NAME_W + 6, y + 18, W - 16 - NAME_W - 6, 10);
        out.add("map", x + DISPLAY_X, y + DISPLAY_Y, MAP_W, DISPLAY_H);
        out.add("gates", x + DISPLAY_X + MAP_W + 4, y + DISPLAY_Y, LIST_W, DISPLAY_H);
    }
}
