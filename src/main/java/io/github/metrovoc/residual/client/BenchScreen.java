package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.bench.Design;
import io.github.metrovoc.residual.bench.Design.Node;
import io.github.metrovoc.residual.bench.Prim;
import io.github.metrovoc.residual.bench.Spec;
import io.github.metrovoc.residual.bench.Specs;
import io.github.metrovoc.residual.block.BenchBlockEntity;
import io.github.metrovoc.residual.net.BenchEdit;
import io.github.metrovoc.residual.net.BenchRecord;
import io.github.metrovoc.residual.registry.ModAttachments;
import io.github.metrovoc.residual.tensor.Tensor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The Bench: wire a spec's inputs through primitives and verified modules until every test case passes, then record the
 * design to a photomask. The design runs as it is wired; each node shows what it computes on the chosen case.
 */
public class BenchScreen extends Screen {
    private static final int PALETTE_X = 7, CANVAS_Y = 20, INSPECT_H = 60;
    private static final int NODE_H = 18, PORT_GAP = 7, SINK_W = 34, SINK_H = 18, CHIP_W = 14;
    /** The result: a node index in hit tests and selection. */
    private static final int SINK = -2;
    private static final int[] GROUP_COLORS = {0xFFB08A3E, 0xFF2F8F86, 0xFF7A5BB0, 0xFF3D6FB0};
    private static final int MODULE_COLOR = 0xFF4E8F3A, INPUT_COLOR = 0xFF8A6A2A, PARAM_COLOR = 0xFF6A4A8A;
    private static final int WIRE = 0xFF5D7A8C, WIRE_LIVE = 0xFF3CD6C6;

    private enum Mode { MOVE, WIRE, PAN }

    /** A drag in progress: a node moving (a fresh one from the palette is dropped if released off the canvas), a wire, or the view. */
    private record Drag(Mode mode, int node, double grabX, double grabY, Design before, boolean fresh) {
    }

    private record Chip(String kind, Component label, int x, int y, int w) {
    }

    /** Text drawn cut short, shown whole while the mouse is over it. */
    private record Cut(int x, int y, int w, int h, Component text) {
    }

    private final BlockPos pos;
    private Spec spec;
    private Design design;
    /** The Bench's design as last seen, to notice someone else's edit. */
    private Design seen;
    /** The design last sent to the Bench, until the Bench has it: what it holds before then is older. */
    @Nullable
    private Design sent;
    private List<Spec.Result> results = List.of();
    private Design.Run run;
    private int caseIndex;
    private int selected = -1;
    private int panX, panY;
    private final Deque<Design> undo = new ArrayDeque<>();
    private boolean choosing;
    @Nullable
    private Drag drag;
    private double mouseX, mouseY;
    private final List<Chip> chips = new ArrayList<>();
    private final List<Cut> cuts = new ArrayList<>();
    private int paletteScroll, paletteHeight;
    private EditBox constant;
    private Button record;
    private boolean opened;
    /** The window's corner and size, and where its parts go: it grows with the screen. */
    private int left, top, W, H, paletteW, canvasX, canvasW, canvasH, inspectY, panelX, panelW;

    public BenchScreen(BlockPos pos) {
        super(Component.translatable("block.residual.bench"));
        this.pos = pos;
        this.spec = Specs.all().getFirst();
    }

    @Override
    protected void init() {
        W = Math.max(320, Math.min(width - 8, 480));
        H = Math.max(232, Math.min(height - 8, 300));
        left = (width - W) / 2;
        top = (height - H) / 2;
        paletteW = W >= 400 ? 74 : 64;
        panelW = W >= 400 ? 140 : 100;
        canvasX = PALETTE_X + paletteW + 4;
        canvasW = W - canvasX - panelW - 12;
        panelX = canvasX + canvasW + 6;
        canvasH = H - CANVAS_Y - INSPECT_H - 10;
        inspectY = CANVAS_Y + canvasH + 4;
        constant = addRenderableWidget(new EditBox(font, left + canvasX + canvasW - 64, top + inspectY + 2, 60, 12,
                Component.translatable("residual.bench.value")));
        constant.setMaxLength(16);
        constant.setResponder(this::typed);
        record = addRenderableWidget(Button.builder(Component.empty(),
                b -> ClientPacketDistributor.sendToServer(new BenchRecord(pos, spec.id()))).bounds(left + panelX, top + H - 24, panelW, 16).build());
        if (!opened) {
            opened = true;
            for (Spec s : Specs.all()) {
                if (s.openTo(verified()) && !verified().contains(s.id())) {
                    spec = s;
                    break;
                }
            }
            open(spec);
        } else {
            syncConstant();
        }
    }

    // ---------------------------------------------------------------- state

    private List<String> verified() {
        return minecraft == null || minecraft.player == null ? List.of() : minecraft.player.getData(ModAttachments.VERIFIED);
    }

    @Nullable
    private BenchBlockEntity bench() {
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof BenchBlockEntity b ? b : null;
    }

    private void open(Spec s) {
        spec = s;
        BenchBlockEntity bench = bench();
        design = bench == null ? Design.blank(s) : bench.design(s);
        seen = design;
        sent = null;
        undo.clear();
        selected = -1;
        panX = panY = 0;
        caseIndex = 0;
        evaluate();
        for (int i = 0; i < results.size(); i++) {
            if (!results.get(i).pass()) {
                caseIndex = i;
                break;
            }
        }
        evaluate();
    }

    private void evaluate() {
        results = spec.check(design);
        caseIndex = Math.min(caseIndex, spec.cases().size() - 1);
        run = design.run(null, spec.cases().get(caseIndex).inputs());
        syncConstant();
    }

    /** Makes an edit: keeps the old design for undo and tells the Bench. */
    private void commit(Design next, Design before) {
        if (next.equals(before)) {
            return;
        }
        undo.push(before);
        while (undo.size() > 50) {
            undo.removeLast();
        }
        design = next;
        seen = next;
        evaluate();
        send(next);
    }

    private void send(Design next) {
        sent = next;
        ClientPacketDistributor.sendToServer(new BenchEdit(pos, spec.id(), next));
    }

    private boolean editable() {
        return spec.openTo(verified());
    }

    @Override
    public void tick() {
        BenchBlockEntity bench = bench();
        if (bench == null) {
            onClose();
            return;
        }
        Design there = bench.design(spec);
        if (there.equals(sent)) {
            sent = null;
        }
        if (drag == null && sent == null && !there.equals(seen) && !there.equals(design)) {
            design = there;
            seen = there;
            selected = Math.min(selected, design.nodes().size() - 1);
            evaluate();
        }
    }

    // ---------------------------------------------------------------- geometry

    private Component label(Node n, int index) {
        if (n.isInput()) {
            return Component.literal(spec.ports().get((int) n.value()).name());
        }
        Prim p = n.prim();
        if (p == Prim.CONST) {
            return Component.literal(Ui.num(n.value()));
        }
        if (p != null) {
            return Component.translatable("residual.prim." + p.id);
        }
        Spec m = n.module();
        return Component.literal(m == null ? "?" : m.id());
    }

    private int nodeW(Node n, int index) {
        return Math.max(26, font.width(label(n, index)) + 10);
    }

    private static int nodeH(Node n) {
        return Math.max(NODE_H, 8 + n.arity() * PORT_GAP);
    }

    private int nx(Node n) {
        return left + canvasX + panX + n.x();
    }

    private int ny(Node n) {
        return top + CANVAS_Y + panY + n.y();
    }

    private static int portY(Node n, int ny, int k) {
        int h = nodeH(n);
        return ny + (h - (n.arity() - 1) * PORT_GAP) / 2 + k * PORT_GAP;
    }

    private int sinkX() {
        return left + canvasX + canvasW - SINK_W - 4;
    }

    private int sinkY() {
        return top + CANVAS_Y + (canvasH - SINK_H) / 2;
    }

    private boolean inCanvas(double x, double y) {
        return Ui.inside(x, y, left + canvasX, top + CANVAS_Y, canvasW, canvasH);
    }

    /** The node under the point, the sink, or -1. */
    private int nodeAt(double x, double y) {
        if (Ui.inside(x, y, sinkX(), sinkY(), SINK_W, SINK_H)) {
            return SINK;
        }
        for (int i = design.nodes().size() - 1; i >= 0; i--) {
            Node n = design.nodes().get(i);
            if (Ui.inside(x, y, nx(n), ny(n), nodeW(n, i), nodeH(n))) {
                return i;
            }
        }
        return -1;
    }

    /** The output port under the point, as a node index, or -1. */
    private int outputAt(double x, double y) {
        for (int i = design.nodes().size() - 1; i >= 0; i--) {
            Node n = design.nodes().get(i);
            int px = nx(n) + nodeW(n, i), py = ny(n) + nodeH(n) / 2;
            if (Math.abs(x - px) <= 4 && Math.abs(y - py) <= 4) {
                return i;
            }
        }
        return -1;
    }

    /** The input port under the point as {node, slot}, or null; the sink's port is {SINK, 0}. */
    @Nullable
    private int[] inputAt(double x, double y) {
        if (Math.abs(x - sinkX()) <= 5 && Math.abs(y - (sinkY() + SINK_H / 2)) <= 5) {
            return new int[]{SINK, 0};
        }
        for (int i = design.nodes().size() - 1; i >= 0; i--) {
            Node n = design.nodes().get(i);
            for (int k = 0; k < n.arity(); k++) {
                if (Math.abs(x - nx(n)) <= 4 && Math.abs(y - portY(n, ny(n), k)) <= 3) {
                    return new int[]{i, k};
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x(), y = event.y();
        if (choosing) {
            choosing = false;
            List<Spec> all = Specs.all();
            for (int i = 0; i < all.size(); i++) {
                if (Ui.inside(x, y, left + canvasX, top + CANVAS_Y + 2 + i * 12, canvasW, 12)) {
                    open(all.get(i));
                }
            }
            return true;
        }
        if (Ui.inside(x, y, left + 6, top + 4, 200, 12)) {
            choosing = true;
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        setFocused(null);
        if (!editable()) {
            return false;
        }
        for (Chip c : chips) {
            if (event.button() == 0 && inPalette(x, y) && Ui.inside(x, y, c.x, c.y, c.w, 11) && design.nodes().size() < Design.MAX_NODES) {
                int arity = c.kind.startsWith("module:") ? Specs.get(c.kind.substring(7)).ports().size() : Prim.byId(c.kind).arity;
                Node n = Design.node(c.kind, c.kind.equals(Prim.CONST.id) ? 1f : 0f, 0, 0, arity);
                Design before = design;
                design = design.plus(n);
                selected = design.nodes().size() - 1;
                int w = nodeW(n, selected);
                drag = new Drag(Mode.MOVE, selected, w / 2.0, NODE_H / 2.0, before, true);
                moveDragged(x, y);
                evaluate();
                return true;
            }
        }
        for (int i = 0; i < spec.cases().size(); i++) {
            if (Ui.inside(x, y, chipX(i), chipsY(), CHIP_W, 11)) {
                caseIndex = i;
                evaluate();
                return true;
            }
        }
        if (!inCanvas(x, y)) {
            return false;
        }
        if (event.button() == 1) {
            int hit = nodeAt(x, y);
            if (hit >= 0 && !design.nodes().get(hit).isInput()) {
                commit(design.without(hit), design);
                selected = -1;
                evaluate();
            }
            return true;
        }
        int out = outputAt(x, y);
        if (out >= 0) {
            drag = new Drag(Mode.WIRE, out, x, y, design, false);
            return true;
        }
        int[] in = inputAt(x, y);
        if (in != null) {
            int source = in[0] == SINK ? design.output() : design.nodes().get(in[0]).inputs().get(in[1]);
            if (source >= 0) {
                Design before = design;
                design = in[0] == SINK ? design.withOutput(Design.OPEN) : design.with(in[0], design.nodes().get(in[0]).wired(in[1], Design.OPEN));
                drag = new Drag(Mode.WIRE, source, x, y, before, false);
                evaluate();
            }
            return true;
        }
        int hit = nodeAt(x, y);
        selected = hit;
        syncConstant();
        if (hit >= 0) {
            Node n = design.nodes().get(hit);
            drag = new Drag(Mode.MOVE, hit, x - nx(n), y - ny(n), design, false);
        } else if (hit != SINK) {
            drag = new Drag(Mode.PAN, -1, x - panX, y - panY, design, false);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        mouseX = event.x();
        mouseY = event.y();
        if (drag == null) {
            return super.mouseDragged(event, dx, dy);
        }
        switch (drag.mode) {
            case MOVE -> moveDragged(event.x(), event.y());
            case PAN -> {
                panX = (int) (event.x() - drag.grabX);
                panY = (int) (event.y() - drag.grabY);
            }
            case WIRE -> {
            }
        }
        return true;
    }

    private void moveDragged(double x, double y) {
        Node n = design.nodes().get(drag.node);
        int cx = (int) Math.round(x - drag.grabX - left - canvasX - panX);
        int cy = (int) Math.round(y - drag.grabY - top - CANVAS_Y - panY);
        design = design.with(drag.node, n.moved(cx, cy));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (drag == null) {
            return super.mouseReleased(event);
        }
        Drag d = drag;
        drag = null;
        double x = event.x(), y = event.y();
        switch (d.mode) {
            case MOVE -> {
                if (d.fresh && !inCanvas(x, y)) {
                    design = d.before;
                    selected = -1;
                    evaluate();
                } else {
                    commit(design, d.before);
                }
            }
            case WIRE -> {
                int[] in = inputAt(x, y);
                if (in != null && in[0] == SINK) {
                    design = design.withOutput(d.node);
                } else if (in != null && in[0] != d.node) {
                    design = design.with(in[0], design.nodes().get(in[0]).wired(in[1], d.node));
                }
                commit(design, d.before);
            }
            case PAN -> {
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (inPalette(x, y)) {
            int room = H - CANVAS_Y - 6;
            paletteScroll = Math.max(0, Math.min(paletteHeight - room, paletteScroll - (int) (scrollY * 12)));
            return true;
        }
        if (inCanvas(x, y)) {
            panX += (int) (scrollX * 12);
            panY += (int) (scrollY * 12);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (constant.isFocused()) {
            if (event.isConfirmation()) {
                setFocused(null);
                return true;
            }
            return super.keyPressed(event);
        }
        if (event.key() == GLFW.GLFW_KEY_Z && event.hasControlDownWithQuirk() && !undo.isEmpty()) {
            Design back = undo.pop();
            design = back;
            seen = back;
            selected = -1;
            evaluate();
            send(back);
            return true;
        }
        if ((event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_BACKSPACE) && selected >= 0 && editable()
                && !design.nodes().get(selected).isInput()) {
            commit(design.without(selected), design);
            selected = -1;
            evaluate();
            return true;
        }
        return super.keyPressed(event);
    }

    /** Shows the value box for a selected constant, holding its number. */
    private void syncConstant() {
        if (constant == null) {
            return;
        }
        boolean show = selected >= 0 && selected < design.nodes().size() && design.nodes().get(selected).prim() == Prim.CONST && editable();
        constant.visible = show;
        if (show && !constant.isFocused()) {
            constant.setValue(plain(design.nodes().get(selected).value()));
        }
    }

    private void typed(String text) {
        if (selected < 0 || selected >= design.nodes().size() || !constant.isFocused()) {
            return;
        }
        Node n = design.nodes().get(selected);
        try {
            float v = Float.parseFloat(text.trim());
            if (n.prim() == Prim.CONST && v != n.value() && Float.isFinite(v)) {
                commit(design.with(selected, n.withValue(v)), design);
            }
        } catch (NumberFormatException ignored) {
            // keep the last number until the text is one
        }
    }

    private static String plain(float v) {
        return v == Math.rint(v) && Math.abs(v) < 1e7 ? Integer.toString((int) v) : Float.toString(v);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        mouseX = mx;
        mouseY = my;
        cuts.clear();
        Ui.window(g, left, top, W, H);
        Component status = verified().contains(spec.id()) ? Component.translatable("residual.bench.status.verified")
                : editable() ? Component.empty() : Component.translatable("residual.bench.status.locked");
        g.text(font, Component.translatable("residual.bench.title", Component.translatable("residual.spec." + spec.id())).append(" ▾"),
                left + 7, top + 7, Ui.LABEL, false);
        g.text(font, status, left + canvasX + canvasW - font.width(status), top + 7,
                verified().contains(spec.id()) ? 0xFF2E7D32 : Ui.LABEL_DIM, false);
        palette(g);
        canvas(g);
        inspector(g);
        panel(g);
        record.active = editable() && Spec.passes(results);
        record.setMessage(Component.translatable(spec.unlocks().isEmpty() ? "residual.bench.verify" : "residual.bench.record"));
        super.extractRenderState(g, mx, my, a);
        if (choosing) {
            chooser(g);
        }
        tooltips(g, mx, my);
    }

    private void palette(GuiGraphicsExtractor g) {
        chips.clear();
        int x = left + PALETTE_X, y0 = top + CANVAS_Y, y = y0 - paletteScroll;
        g.enableScissor(x, y0, x + paletteW + 2, top + H - 6);
        Prim.Group group = null;
        int cx = x;
        for (Prim p : Prim.values()) {
            if (p.group != group) {
                group = p.group;
                if (cx != x) {
                    y += 12;
                }
                g.text(font, Component.translatable("residual.bench.group." + group.name().toLowerCase(Locale.ROOT)), x, y, Ui.LABEL_DIM, false);
                y += 10;
                cx = x;
            }
            Component label = Component.translatable("residual.prim." + p.id);
            int w = font.width(label) + 6;
            if (cx + w > x + paletteW) {
                cx = x;
                y += 12;
            }
            chip(g, new Chip(p.id, label, cx, y, w), GROUP_COLORS[p.group.ordinal()]);
            cx += w + 2;
        }
        List<String> modules = verified().stream().filter(id -> !id.equals(spec.id()) && Specs.get(id) != null).toList();
        y += 14;
        g.text(font, Component.translatable("residual.bench.group.modules"), x, y, Ui.LABEL_DIM, false);
        y += 10;
        cx = x;
        if (modules.isEmpty()) {
            for (FormattedCharSequence line : font.split(Component.translatable("residual.bench.no_modules"), paletteW)) {
                g.text(font, line, x, y, Ui.LABEL_DIM, false);
                y += 9;
            }
        }
        for (String id : modules) {
            Component label = Component.literal(id);
            int w = Math.min(paletteW, font.width(label) + 6);
            if (cx + w > x + paletteW) {
                cx = x;
                y += 12;
            }
            chip(g, new Chip("module:" + id, label, cx, y, w), MODULE_COLOR);
            cx += w + 2;
        }
        g.disableScissor();
        paletteHeight = y + 12 - (y0 - paletteScroll);
    }

    private boolean inPalette(double x, double y) {
        return Ui.inside(x, y, left + PALETTE_X, top + CANVAS_Y, paletteW + 2, H - CANVAS_Y - 6);
    }

    private void chip(GuiGraphicsExtractor g, Chip c, int color) {
        chips.add(c);
        boolean hot = editable() && Ui.inside(mouseX, mouseY, c.x, c.y, c.w, 11);
        g.fill(c.x, c.y, c.x + c.w, c.y + 11, editable() ? color : 0xFF8B8B8B);
        g.outline(c.x, c.y, c.w, 11, hot ? Ui.HIGHLIGHT : 0xFF373737);
        g.text(font, c.label, c.x + 3, c.y + 2, Ui.TEXT, false);
    }

    private void canvas(GuiGraphicsExtractor g) {
        int x0 = left + canvasX, y0 = top + CANVAS_Y;
        Ui.screen(g, x0, y0, canvasW, canvasH);
        g.enableScissor(x0, y0, x0 + canvasW, y0 + canvasH);
        for (int gx = Math.floorMod(panX, 12); gx < canvasW; gx += 12) {
            for (int gy = Math.floorMod(panY, 12); gy < canvasH; gy += 12) {
                g.fill(x0 + gx, y0 + gy, x0 + gx + 1, y0 + gy + 1, Ui.GRID);
            }
        }
        List<Node> nodes = design.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            for (int k = 0; k < n.arity(); k++) {
                int src = n.inputs().get(k);
                if (src >= 0 && src < nodes.size()) {
                    Node s = nodes.get(src);
                    wire(g, nx(s) + nodeW(s, src), ny(s) + nodeH(s) / 2, nx(n), portY(n, ny(n), k), run.values()[src] != null);
                }
            }
        }
        int out = design.output();
        if (out >= 0 && out < nodes.size()) {
            Node s = nodes.get(out);
            wire(g, nx(s) + nodeW(s, out), ny(s) + nodeH(s) / 2, sinkX(), sinkY() + SINK_H / 2, run.result() != null);
        }
        for (int i = 0; i < nodes.size(); i++) {
            node(g, i);
        }
        sink(g);
        if (drag != null && drag.mode == Mode.WIRE) {
            Node s = nodes.get(drag.node);
            wire(g, nx(s) + nodeW(s, drag.node), ny(s) + nodeH(s) / 2, (int) mouseX, (int) mouseY, true);
        }
        if (!editable()) {
            List<Component> needs = spec.requires().stream().filter(r -> !verified().contains(r))
                    .map(r -> (Component) Component.translatable("residual.spec." + r)).toList();
            Component line = Component.translatable("residual.bench.needs", joined(needs));
            List<FormattedCharSequence> lines = font.split(line, canvasW - 16);
            int ty = y0 + canvasH / 2 - lines.size() * 5;
            g.fill(x0, ty - 6, x0 + canvasW, ty + lines.size() * 10 + 4, 0xD0101418);
            for (FormattedCharSequence l : lines) {
                g.centeredText(font, l, x0 + canvasW / 2, ty, Ui.AMBER);
                ty += 10;
            }
        }
        g.disableScissor();
    }

    private static Component joined(List<Component> parts) {
        var out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(parts.get(i));
        }
        return out;
    }

    /** A right-angled wire from an output port to an input port. */
    private static void wire(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, boolean live) {
        int color = live ? WIRE_LIVE : WIRE;
        int mid = x2 > x1 + 8 ? (x1 + x2) / 2 : x1 + 6;
        hline(g, x1, mid, y1, color);
        vline(g, mid, y1, y2, color);
        hline(g, mid, x2, y2, color);
    }

    private static void hline(GuiGraphicsExtractor g, int xa, int xb, int y, int color) {
        g.fill(Math.min(xa, xb), y, Math.max(xa, xb) + 1, y + 1, color);
    }

    private static void vline(GuiGraphicsExtractor g, int x, int ya, int yb, int color) {
        g.fill(x, Math.min(ya, yb), x + 1, Math.max(ya, yb) + 1, color);
    }

    private void node(GuiGraphicsExtractor g, int i) {
        Node n = design.nodes().get(i);
        int x = nx(n), y = ny(n), w = nodeW(n, i), h = nodeH(n);
        int color;
        if (n.isInput()) {
            color = spec.ports().get((int) n.value()).param() ? PARAM_COLOR : INPUT_COLOR;
        } else if (n.prim() != null) {
            color = GROUP_COLORS[n.prim().group.ordinal()];
        } else {
            color = MODULE_COLOR;
        }
        Tensor value = run.values()[i];
        Component error = run.errors()[i];
        boolean broken = error != null || value != null && !finite(value);
        g.fill(x, y, x + w, y + h, 0xFF0B0F13);
        g.fill(x, y, x + w, y + 11, color);
        g.outline(x, y, w, h, i == selected ? Ui.HIGHLIGHT : broken ? Ui.RED : 0xFF2A3540);
        g.text(font, label(n, i), x + 5, y + 2, Ui.TEXT, false);
        if (value != null) {
            strip(g, value, x + 2, y + 12, w - 4, Math.max(3, h - 14));
        }
        for (int k = 0; k < n.arity(); k++) {
            int py = portY(n, y, k);
            boolean open = n.inputs().get(k) == Design.OPEN;
            g.fill(x - 2, py - 1, x + 1, py + 2, open ? Ui.AMBER : WIRE_LIVE);
        }
        int oy = y + h / 2;
        g.fill(x + w - 1, oy - 1, x + w + 2, oy + 2, value != null ? WIRE_LIVE : Ui.DIM);
    }

    /** The values a node computed as a strip of colours, scaled to their largest magnitude; red where not a number. */
    private static void strip(GuiGraphicsExtractor g, Tensor t, int x, int y, int w, int h) {
        int n = Math.min(t.data.length, w);
        if (n == 0) {
            return;
        }
        float max = 0;
        for (float v : t.data) {
            if (Float.isFinite(v)) {
                max = Math.max(max, Math.abs(v));
            }
        }
        for (int c = 0; c < n; c++) {
            float v = t.data[c * t.data.length / n];
            int x0 = x + c * w / n, x1 = x + (c + 1) * w / n;
            int color = Float.isNaN(v) ? Ui.RED : Float.isInfinite(v) ? Ui.AMBER : Ui.heat(max == 0 ? 0 : v / max);
            g.fill(x0, y, Math.max(x0 + 1, x1), y + Math.min(h, 3), color);
        }
    }

    private static boolean finite(Tensor t) {
        for (float v : t.data) {
            if (!Float.isFinite(v)) {
                return false;
            }
        }
        return true;
    }

    private void sink(GuiGraphicsExtractor g) {
        int x = sinkX(), y = sinkY();
        Spec.Result r = results.get(caseIndex);
        int color = r.pass() ? 0xFF2E7D32 : design.output() == Design.OPEN ? 0xFF4A5560 : 0xFF8E2A2A;
        g.fill(x, y, x + SINK_W, y + SINK_H, color);
        g.outline(x, y, SINK_W, SINK_H, selected == SINK ? Ui.HIGHLIGHT : 0xFF2A3540);
        g.centeredText(font, Component.translatable("residual.bench.result"), x + SINK_W / 2, y + 5, Ui.TEXT);
        g.fill(x - 2, y + SINK_H / 2 - 1, x + 1, y + SINK_H / 2 + 2, design.output() == Design.OPEN ? Ui.AMBER : WIRE_LIVE);
    }

    private void inspector(GuiGraphicsExtractor g) {
        int x = left + canvasX, y = top + inspectY;
        Ui.screen(g, x, y, canvasW, INSPECT_H);
        Tensor value;
        Component head;
        Component error = null;
        Spec.Case c = spec.cases().get(caseIndex);
        if (selected == SINK) {
            value = c.expected();
            head = Component.translatable("residual.bench.expected");
        } else if (selected >= 0 && selected < design.nodes().size()) {
            Node n = design.nodes().get(selected);
            value = run.values()[selected];
            error = run.errors()[selected];
            head = n.prim() != null ? Component.translatable("residual.prim." + n.prim().id + ".desc")
                    : n.module() != null ? Component.translatable("residual.spec." + n.module().id())
                    : Component.translatable("residual.bench.input", spec.ports().get((int) n.value()).name());
        } else {
            lines(g, Component.translatable("residual.bench.help"), x + 4, y + 3, canvasW - 8, 6, Ui.DIM);
            return;
        }
        int textW = constant.visible ? canvasW - 72 : canvasW - 8;
        int ty = lines(g, head, x + 4, y + 3, textW, 2, Ui.TEXT) + 2;
        if (error != null) {
            lines(g, error, x + 4, ty, canvasW - 8, (y + INSPECT_H - ty) / 9, Ui.RED);
            return;
        }
        if (value == null) {
            g.text(font, Component.translatable("residual.bench.waiting"), x + 4, ty, Ui.DIM, false);
            return;
        }
        g.text(font, Tensor.shapeString(value.shape), x + 4, ty, Ui.AMBER, false);
        grid(g, value, x + 4, ty + 10, canvasW - 8, (y + INSPECT_H - ty - 10) / 10);
    }

    /** A tensor's numbers, row by row, as many as fit, in cells as wide as the widest of them. */
    private void grid(GuiGraphicsExtractor g, Tensor t, int x, int y, int w, int rows) {
        int cols = t.rank() == 0 ? 1 : t.dim(-1);
        int total = Math.max(1, t.data.length / Math.max(1, cols));
        int cell = 0;
        for (int r = 0; r < Math.min(rows, total); r++) {
            for (int c = 0; c < Math.min(cols, 8); c++) {
                cell = Math.max(cell, font.width(cellText(t.data[r * cols + c])) + 6);
            }
        }
        int fit = Math.max(1, w / Math.max(1, cell));
        for (int r = 0; r < Math.min(rows, total); r++) {
            for (int c = 0; c < Math.min(cols, fit); c++) {
                float v = t.data[r * cols + c];
                String s = c == fit - 1 && cols > fit ? "…" : cellText(v);
                int color = Float.isNaN(v) ? Ui.RED : Float.isInfinite(v) ? Ui.AMBER : Ui.TEXT;
                g.text(font, Ui.fit(font, s, cell - 4), x + c * cell, y + r * 10, color, false);
            }
        }
        if (total > rows) {
            g.text(font, "⋮", x, y + rows * 10 - 2, Ui.DIM, false);
        }
    }

    private static String cellText(float v) {
        return Float.isNaN(v) ? "NaN" : Ui.num(v);
    }

    /**
     * Draws {@code text} wrapped to {@code w} in at most {@code max} lines, the last ending in an ellipsis when more was
     * left; the whole text shows on hover. Returns the y below the last line.
     */
    private int lines(GuiGraphicsExtractor g, Component text, int x, int y, int w, int max, int color) {
        List<FormattedText> all = font.getSplitter().splitLines(text, w, Style.EMPTY);
        int n = Math.min(all.size(), Math.max(0, max));
        for (int i = 0; i < n; i++) {
            String s = all.get(i).getString();
            g.text(font, i == n - 1 && n < all.size() ? Ui.fit(font, s + "…", w) : s, x, y + i * 9, color, false);
        }
        if (n < all.size()) {
            cuts.add(new Cut(x, y, w, Math.max(9, n * 9), text));
        }
        return y + n * 9;
    }

    /** The row of test cases, one chip each, under the goal and the math. */
    private int chipsY() {
        return top + CANVAS_Y + goalShown() * 9 + 3 + mathLines().size() * 9 + 4;
    }

    private int chipX(int i) {
        return left + panelX + i * (CHIP_W + 2);
    }

    private Component goal() {
        return Component.translatable("residual.spec." + spec.id() + ".goal");
    }

    /**
     * Lines of the goal shown, at least two: what is left above the cases once the note under them has room for a
     * two-line reason and the longest hint any case brings. It depends only on the spec, so the cases never move.
     */
    private int goalShown() {
        int note = 0;
        for (Spec.Case c : spec.cases()) {
            String hint = "residual.spec." + spec.id() + ".case." + c.id() + ".hint";
            note = Math.max(note, I18n.exists(hint) ? 2 + font.split(Component.translatable(hint), panelW).size() * 9 : 0);
        }
        int room = H - 28 - CANVAS_Y - 3 - mathLines().size() * 9 - 4 - 26 - 18 - note;
        return Math.min(font.split(goal(), panelW).size(), Math.max(2, room / 9));
    }

    private List<FormattedCharSequence> mathLines() {
        String key = "residual.spec." + spec.id() + ".math";
        return I18n.exists(key) ? font.split(Component.translatable(key), panelW) : List.of();
    }

    private void panel(GuiGraphicsExtractor g) {
        int x = left + panelX, y = top + CANVAS_Y;
        y = lines(g, goal(), x, y, panelW, goalShown(), Ui.LABEL) + 3;
        for (FormattedCharSequence line : mathLines()) {
            g.text(font, line, x, y, 0xFF1E6F68, false);
            y += 9;
        }
        int cy = chipsY();
        for (int i = 0; i < results.size(); i++) {
            Spec.Result r = results.get(i);
            int cx = chipX(i);
            g.fill(cx, cy, cx + CHIP_W, cy + 11, i == caseIndex ? 0x50000000 : 0x1E000000);
            if (i == caseIndex) {
                g.outline(cx, cy, CHIP_W, 11, Ui.LABEL);
            }
            String mark = r.pass() ? "✔" : "✘";
            g.text(font, mark, cx + (CHIP_W - font.width(mark) + 1) / 2, cy + 2, r.pass() ? 0xFF2E7D32 : 0xFFB3261E, false);
        }
        Spec.Result r = results.get(caseIndex);
        int y2 = lines(g, caseName(r.caseId()), x, cy + 14, panelW, 1, Ui.LABEL) + 3;
        int limit = top + H - 28;
        if (!r.pass()) {
            y2 = lines(g, why(r), x, y2, panelW, (limit - y2) / 9, 0xFFB3261E);
            String hint = "residual.spec." + spec.id() + ".case." + r.caseId() + ".hint";
            if (I18n.exists(hint) && design.output() != Design.OPEN) {
                lines(g, Component.translatable(hint), x, y2 + 2, panelW, (limit - y2 - 2) / 9, Ui.LABEL_DIM);
            }
        } else if (Spec.passes(results)) {
            lines(g, Component.translatable("residual.bench.passes", design.area(), spec.area()), x, y2, panelW, (limit - y2) / 9, 0xFF2E7D32);
        }
    }

    private Component caseName(String id) {
        return Component.translatable("residual.spec." + spec.id() + ".case." + id);
    }

    private Component why(Spec.Result r) {
        return switch (r.why()) {
            case "residual.bench.why.shape" -> Component.translatable(r.why(), Tensor.shapeString(r.got().shape), Tensor.shapeString(r.want().shape));
            case "residual.bench.why.nan", "residual.bench.why.inf" -> Component.translatable(r.why(), r.worst());
            case "residual.bench.why.off" -> Component.translatable(r.why(), r.worst(), Ui.num(r.got().data[r.worst()]), Ui.num(r.want().data[r.worst()]));
            default -> Component.translatable(r.why());
        };
    }

    private void chooser(GuiGraphicsExtractor g) {
        int x = left + canvasX, y = top + CANVAS_Y;
        List<Spec> all = Specs.all();
        g.fill(x - 1, y - 1, x + canvasW + 1, y + all.size() * 12 + 5, 0xFF373737);
        g.fill(x, y, x + canvasW, y + all.size() * 12 + 4, Ui.PANEL);
        for (int i = 0; i < all.size(); i++) {
            Spec s = all.get(i);
            int ry = y + 2 + i * 12;
            if (Ui.inside(mouseX, mouseY, x, ry, canvasW, 12)) {
                g.fill(x, ry, x + canvasW, ry + 12, 0xFFDADADA);
            }
            boolean done = verified().contains(s.id());
            boolean open = s.openTo(verified());
            g.text(font, done ? "✔" : open ? "•" : "🔒", x + 4, ry + 2, done ? 0xFF2E7D32 : open ? Ui.LABEL : Ui.LABEL_DIM, false);
            g.text(font, Component.translatable("residual.spec." + s.id()), x + 14, ry + 2, open ? Ui.LABEL : Ui.LABEL_DIM, false);
        }
    }

    private void tooltips(GuiGraphicsExtractor g, int mx, int my) {
        if (choosing || drag != null) {
            return;
        }
        for (Chip c : chips) {
            if (inPalette(mx, my) && Ui.inside(mx, my, c.x, c.y, c.w, 11)) {
                Component tip = c.kind.startsWith("module:") ? Component.translatable("residual.spec." + c.kind.substring(7))
                        : Component.translatable("residual.prim." + c.kind + ".desc");
                g.setTooltipForNextFrame(font, font.split(tip, 180), mx, my);
                return;
            }
        }
        if (Ui.inside(mx, my, left + 7, top + 4, 200, 12)) {
            g.setTooltipForNextFrame(font, Component.translatable("residual.bench.choose"), mx, my);
        }
        if (inCanvas(mx, my)) {
            int hit = nodeAt(mx, my);
            if (hit >= 0 && run.errors()[hit] != null) {
                g.setTooltipForNextFrame(font, font.split(run.errors()[hit], 200), mx, my);
            }
            return;
        }
        for (int i = 0; i < spec.cases().size(); i++) {
            if (Ui.inside(mx, my, chipX(i), chipsY(), CHIP_W, 11)) {
                g.setTooltipForNextFrame(font, caseName(spec.cases().get(i).id()), mx, my);
                return;
            }
        }
        for (Cut c : cuts) {
            if (Ui.inside(mx, my, c.x(), c.y(), c.w(), c.h())) {
                g.setTooltipForNextFrame(font, font.split(c.text(), 200), mx, my);
                return;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
