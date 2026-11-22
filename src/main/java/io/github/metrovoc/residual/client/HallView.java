package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.metrovoc.residual.interior.Arch;
import io.github.metrovoc.residual.interior.HallPlan;
import io.github.metrovoc.residual.interior.HallSync;
import io.github.metrovoc.residual.interior.Halls;
import io.github.metrovoc.residual.interior.Snapshot;
import io.github.metrovoc.residual.learn.Vocab;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Draws what stands in a model's hall and is not blocks: the tokens on their lanes, each lane's stream as a pane of
 * cells wherever a part has changed it, what each attention head reads as arcs from lane to lane, the chances at the
 * far end, the run's curve on the end wall, and a label over every console.
 */
public final class HallView {
    private static final int FULL_BRIGHT = 0xF000F0;
    /** How tall a lane's pane stands, and how far above the floor it begins. */
    private static final float PANE = 3.2f, LIFT = 1.25f;
    private static final int[] HEAD_COLORS = {0xB48CFF, 0x3CD6C6, 0xFFB547, 0xFF6F8E, 0x7DDC6B, 0x6CA8FF, 0xE6D75A, 0xD98CFF};
    private static final int TEAL = 0x3CD6C6, AMBER = 0xFFB547, GREEN = 0x6EE08A, RED = 0xFF6B5E, DIM = 0x9AA6A2, BLUE = 0x4FA3FF;
    /** A label a block tall is drawn out to this far, a smaller one in proportion. */
    private static final double RANGE = 110;

    @Nullable
    private static HallSync latest;
    @Nullable
    private static Snapshot.Tables tables;

    private HallView() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> event.register(HallSync.TYPE, (sync, ctx) -> receive(sync)));
        NeoForge.EVENT_BUS.addListener(HallView::submit);
    }

    public static void receive(HallSync sync) {
        if (sync.shot().tables() != null) {
            tables = sync.shot().tables();
        }
        latest = sync;
    }

    /** What the hall the player stands in last sent, or null outside one. */
    @Nullable
    public static HallSync shown() {
        Minecraft mc = Minecraft.getInstance();
        if (latest == null || mc.level == null || mc.player == null || mc.level.dimension() != Halls.LEVEL
                || !latest.origin().closerToCenterThan(mc.player.position(), 512)) {
            return null;
        }
        return latest;
    }

    /** How a label stands: turned to whoever looks, or fixed to face the walkway or the entrance. */
    private enum Facing {
        VIEWER, WALKWAY, ENTRANCE
    }

    /**
     * @param size how tall a line of it stands, in blocks
     */
    private record Label(Vec3 at, List<Component> lines, float size, Facing facing, int back) {
        Label(Vec3 at, Component text, float size, Facing facing) {
            this(at, List.of(text), size, facing, 0);
        }
    }

    /** A lane's stream drawn across the hall at {@code z}; {@code what} names it in a label's key. */
    private record Pane(Snapshot.Grid grid, double z, String what) {
    }

    /**
     * What the crosshair rests on.
     *
     * @param frame the cell or bar it is, as x0, y0, x1, y1 and z
     */
    private record Looked(Label label, float[] frame) {
    }

    /** How far a number can be read from. */
    private static final double REACH = 24;

    /** The cell of a pane, or the bar at the far end, that the player looks at: what it is and its number. */
    @Nullable
    private static Looked looked(HallPlan plan, Snapshot s, List<Pane> panes, Vec3 eye, Vector3f look) {
        if (Math.abs(look.z) < 1e-4) {
            return null;
        }
        float half = plan.pitch() * 0.3f;
        double nearest = REACH;
        Looked found = null;
        for (Pane p : panes) {
            double t = (p.z - eye.z) / look.z;
            if (t <= 0 || t >= nearest) {
                continue;
            }
            double x = eye.x + look.x * t, y = eye.y + look.y * t;
            int lane = plan.laneAt(x);
            if (lane < 0 || lane >= p.grid.rows() || Math.abs(x - plan.lane(lane)) > half || y < LIFT || y >= LIFT + PANE) {
                continue;
            }
            float cell = PANE / p.grid.cols();
            int i = Math.min(p.grid.cols() - 1, (int) ((LIFT + PANE - y) / cell));
            float cx = (float) plan.lane(lane), top = LIFT + PANE - i * cell;
            List<Component> lines = List.of(
                    Component.translatable("residual.inside.look." + p.what, show(Vocab.WORLD, s.tokens()[lane])).withColor(DIM),
                    Component.translatable("residual.inside.look.number", i + 1, p.grid.cols(), String.format(Locale.ROOT, "%+.2f", p.grid.at(lane, i))));
            nearest = t;
            found = new Looked(new Label(new Vec3(x - look.x * 0.4, y + 0.6, p.z - look.z * 0.4), lines, 0.2f, Facing.VIEWER, 0xC0000000),
                    new float[]{cx - half, top - cell, cx + half, top, (float) p.z});
        }
        HallPlan.Station readout = plan.first(HallPlan.Type.READOUT);
        double z = readout.mid() + 1, t = (z - eye.z) / look.z;
        if (t > 0 && t < nearest) {
            double x = eye.x + look.x * t, y = eye.y + look.y * t;
            int lane = plan.laneAt(x);
            float bar = plan.pitch() * 0.3f;
            if (lane >= 0 && lane < s.lanes() && Math.abs(x - plan.lane(lane)) <= bar && y >= LIFT && y < LIFT + PANE) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("residual.inside.look.chances", show(Vocab.WORLD, s.tokens()[lane])).withColor(DIM));
                boolean[] taken = new boolean[s.vocab()];
                for (int n = 0; n < 3; n++) {
                    int best = -1;
                    for (int v = 0; v < s.vocab(); v++) {
                        if (!taken[v] && (best < 0 || s.chance(lane, v) > s.chance(lane, best))) {
                            best = v;
                        }
                    }
                    taken[best] = true;
                    lines.add(Component.literal(show(Vocab.WORLD, best) + "  " + Math.round(s.chance(lane, best) * 100) + "%")
                            .withColor(best == s.targets()[lane] ? GREEN : 0xFFFFFF));
                }
                float cx = (float) plan.lane(lane);
                found = new Looked(new Label(new Vec3(x - look.x * 0.6, y + 1.1, z - look.z * 0.6), lines, 0.2f, Facing.VIEWER, 0xC0000000),
                        new float[]{cx - bar, LIFT, cx + bar, LIFT + PANE, (float) z});
            }
        }
        return found;
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        HallSync sync = shown();
        if (sync == null) {
            return;
        }
        Snapshot s = sync.shot();
        HallPlan plan = HallPlan.of(sync.arch(), s.lanes());
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        BlockPos o = sync.origin();
        // everything is drawn from the hall's corner
        Vec3 base = new Vec3(o.getX() - camera.x, o.getY() - camera.y, o.getZ() - camera.z);
        Vec3 eye = camera.subtract(o.getX(), o.getY(), o.getZ());
        int standing = plan.laneAt(eye.x);
        List<Label> labels = new ArrayList<>();
        Vocab vocab = Vocab.WORLD;

        List<Pane> panes = new ArrayList<>();
        for (HallPlan.Station st : plan.stations()) {
            switch (st.type()) {
                case EMBED -> panes.add(new Pane(s.embed(), st.mid(), "embed"));
                case POSITION -> {
                    if (s.position() != null) {
                        panes.add(new Pane(s.position(), st.mid(), "position"));
                    }
                }
                case ATTENTION, FEED_FORWARD -> {
                    Snapshot.PartView part = part(s, st.part());
                    if (part != null) {
                        panes.add(new Pane(part.added(), st.mid(), st.type() == HallPlan.Type.ATTENTION ? "added_attention" : "added_feed_forward"));
                        panes.add(new Pane(part.after(), st.end() - 0.5, "after"));
                    }
                }
                default -> {
                }
            }
        }
        Looked looked = looked(plan, s, panes, eye, event.getLevelRenderState().cameraRenderState.orientation.transform(new Vector3f(0, 0, -1)));
        if (looked != null) {
            labels.add(looked.label);
        }

        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.textBackground(), (pose, buffer) -> {
            Quads q = new Quads(pose, buffer, base);
            for (Pane p : panes) {
                panes(q, plan, p.grid, p.z);
            }
            readout(q, plan, s, plan.first(HallPlan.Type.READOUT));
            curve(q, plan, s, plan.first(HallPlan.Type.END));
            scoredLanes(q, plan, s);
            if (looked != null) {
                float[] f = looked.frame;
                float t = 0.03f;
                for (float z : new float[]{f[4] - 0.03f, f[4] + 0.03f}) {
                    q.wall(f[0] - t, f[1] - t, f[2] + t, f[1], z, 0xFFFFFFFF);
                    q.wall(f[0] - t, f[3], f[2] + t, f[3] + t, z, 0xFFFFFFFF);
                    q.wall(f[0] - t, f[1], f[0], f[3], z, 0xFFFFFFFF);
                    q.wall(f[2], f[1], f[2] + t, f[3], z, 0xFFFFFFFF);
                }
            }
        });
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lightning(), (pose, buffer) -> {
            Quads q = new Quads(pose, buffer, base);
            for (HallPlan.Station st : plan.stations()) {
                Snapshot.PartView part = st.type() == HallPlan.Type.ATTENTION ? part(s, st.part()) : null;
                if (part != null && part.pattern() != null) {
                    arcs(q, plan, part, st, standing, eye);
                }
            }
        });

        // labels: tokens, what is predicted, the consoles, the run
        HallPlan.Station tokens = plan.first(HallPlan.Type.TOKENS), readout = plan.first(HallPlan.Type.READOUT);
        for (int t = 0; t < s.lanes(); t++) {
            labels.add(new Label(new Vec3(plan.lane(t), 1.75, tokens.mid()), Component.literal(show(vocab, s.tokens()[t])).withColor(0xFFFFFF), 0.7f, Facing.VIEWER));
            int best = 0;
            for (int v = 1; v < s.vocab(); v++) {
                if (s.chance(t, v) > s.chance(t, best)) {
                    best = v;
                }
            }
            boolean right = best == s.targets()[t];
            int color = !s.scored()[t] ? DIM : right ? GREEN : RED;
            labels.add(new Label(new Vec3(plan.lane(t), LIFT + PANE + 1.1, readout.mid() + 1), Component.literal(show(vocab, best)).withColor(color), 0.7f, Facing.VIEWER));
            if (s.scored()[t] && !right) {
                labels.add(new Label(new Vec3(plan.lane(t), LIFT + PANE + 0.4, readout.mid() + 1), Component.literal(show(vocab, s.targets()[t])).withColor(DIM), 0.4f, Facing.VIEWER));
            }
        }
        for (HallPlan.Console c : plan.consoles()) {
            labels.add(new Label(new Vec3(c.x() + 1.02, 2.45, c.z() + 0.5), console(c, sync), 0.2f, Facing.WALKWAY));
        }
        HallPlan.Station end = plan.first(HallPlan.Type.END);
        labels.add(new Label(new Vec3(HallPlan.WALK + plan.lanes() * plan.pitch() / 2.0, 9.6, end.end() - 0.6), status(sync), 0.9f, Facing.ENTRANCE));

        PoseStack pose = event.getPoseStack();
        Font font = Minecraft.getInstance().font;
        for (Label l : labels) {
            Vec3 world = l.at.add(o.getX(), o.getY(), o.getZ());
            // text too small to read is left out: a line under about four pixels tall
            if (world.distanceToSqr(camera) > RANGE * RANGE * l.size * l.size) {
                continue;
            }
            pose.pushPose();
            pose.translate(world.x - camera.x, world.y - camera.y, world.z - camera.z);
            switch (l.facing) {
                case VIEWER -> pose.mulPose(event.getLevelRenderState().cameraRenderState.orientation);
                case WALKWAY -> pose.mulPose(Axis.YP.rotationDegrees(90));
                case ENTRANCE -> pose.mulPose(Axis.YP.rotationDegrees(180));
            }
            float scale = l.size / font.lineHeight;
            pose.scale(scale, -scale, scale);
            for (int n = 0; n < l.lines.size(); n++) {
                FormattedCharSequence line = l.lines.get(n).getVisualOrderText();
                // what the player looks at is written over everything else
                (l.back != 0 ? event.getSubmitNodeCollector().order(1) : event.getSubmitNodeCollector()).submitText(pose, -font.width(line) / 2f, -font.lineHeight / 2f + n * (font.lineHeight + 1), line, false,
                        l.back != 0 ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, FULL_BRIGHT, 0xFFFFFFFF, l.back, 0);
            }
            pose.popPose();
        }
    }

    @Nullable
    private static Snapshot.PartView part(Snapshot s, int id) {
        for (Snapshot.PartView p : s.parts()) {
            if (p.id() == id) {
                return p;
            }
        }
        return null;
    }

    private static String show(Vocab vocab, int token) {
        String piece = vocab.piece(token);
        return switch (piece) {
            case "\n" -> "⏎";
            case " " -> "␣";
            default -> piece;
        };
    }

    /** A pane across each lane: one cell for each number of that lane's stream, the first at the top. */
    private static void panes(Quads q, HallPlan plan, Snapshot.Grid grid, double z) {
        float cell = PANE / grid.cols();
        float half = plan.pitch() * 0.3f;
        for (int t = 0; t < grid.rows(); t++) {
            float x = (float) plan.lane(t);
            q.wall(x - half, LIFT, x + half, LIFT + PANE, (float) z, 0x70080B0E);
            for (int i = 0; i < grid.cols(); i++) {
                // a number near zero leaves its cell dark: what stands out is what the lane carries
                float share = grid.share(t, i), size = Math.min(1f, Math.abs(share));
                if (size < 0.08f) {
                    continue;
                }
                float top = LIFT + PANE - i * cell;
                int alpha = Math.round(30 + 170 * size);
                q.wall(x - half, top - cell * 0.92f, x + half, top, (float) z + 0.01f, alpha << 24 | (share > 0 ? AMBER : BLUE));
                q.wall(x - half, top - cell * 0.92f, x + half, top, (float) z - 0.01f, alpha << 24 | (share > 0 ? AMBER : BLUE));
            }
        }
    }

    /** A faint strip on the floor of each lane whose prediction counts. */
    private static void scoredLanes(Quads q, HallPlan plan, Snapshot s) {
        HallPlan.Station readout = plan.first(HallPlan.Type.READOUT);
        for (int t = 0; t < s.lanes(); t++) {
            if (s.scored()[t]) {
                float x = (float) plan.lane(t), half = plan.pitch() * 0.5f;
                q.floor(x - half, readout.z(), x + half, readout.end(), 1.01f, 0x403CD6C6);
            }
        }
    }

    /** At the far end, a bar on each lane as tall as the chance the model gave to what really follows. */
    private static void readout(Quads q, HallPlan plan, Snapshot s, HallPlan.Station st) {
        float z = (float) st.mid() + 1;
        float half = plan.pitch() * 0.3f;
        for (int t = 0; t < s.lanes(); t++) {
            float x = (float) plan.lane(t);
            float chance = s.chance(t, s.targets()[t]);
            int color = s.scored()[t] ? chance > 0.5f ? GREEN : RED : DIM;
            q.wall(x - half, LIFT, x + half, LIFT + PANE, z, 0x50101418);
            q.wall(x - half, LIFT, x + half, LIFT + PANE * chance, z - 0.01f, (s.scored()[t] ? 0xE6000000 : 0x80000000) | color);
        }
    }

    /** On the end wall, the run: the loss in practice and on the test, and the share of answers right. */
    private static void curve(Quads q, HallPlan plan, Snapshot s, HallPlan.Station st) {
        float x0 = HallPlan.WALK, x1 = HallPlan.WALK + plan.lanes() * plan.pitch();
        float y0 = 1.5f, y1 = 8.5f, z = st.end() - 0.4f;
        q.wall(x0, y0, x1, y1, z, 0xC00C1014);
        List<Snapshot.Point> points = s.curve();
        if (points.size() < 2) {
            return;
        }
        int first = points.getFirst().step(), last = Math.max(points.getLast().step(), first + 1);
        float top = 0.1f;
        for (Snapshot.Point p : points) {
            if (Float.isFinite(p.test())) {
                top = Math.max(top, p.test());
            }
            if (Float.isFinite(p.train())) {
                top = Math.max(top, p.train());
            }
        }
        float thick = 0.06f;
        for (int i = 1; i < points.size(); i++) {
            Snapshot.Point a = points.get(i - 1), b = points.get(i);
            // seen from the entrance, the wall's left is its far side across the hall
            float ax = x1 - (x1 - x0) * (a.step() - first) / (last - first), bx = x1 - (x1 - x0) * (b.step() - first) / (last - first);
            line(q, ax, a.train() / top, bx, b.train() / top, y0, y1, z - 0.01f, thick, 0xE0000000 | AMBER);
            line(q, ax, a.test() / top, bx, b.test() / top, y0, y1, z - 0.02f, thick, 0xE0000000 | TEAL);
            line(q, ax, a.score(), bx, b.score(), y0, y1, z - 0.03f, thick, 0xE0000000 | GREEN);
        }
    }

    private static void line(Quads q, float ax, float a, float bx, float b, float y0, float y1, float z, float thick, int argb) {
        if (!Float.isFinite(a) || !Float.isFinite(b)) {
            return;
        }
        float ay = y0 + (y1 - y0) * Math.clamp(a, 0f, 1f), by = y0 + (y1 - y0) * Math.clamp(b, 0f, 1f);
        q.quad(ax, ay - thick, z, bx, by - thick, z, bx, by + thick, z, ax, ay + thick, z, argb);
    }

    /**
     * What each head reads: an arc from the lane that reads to the lane it reads from, as bright as the share. Each
     * head has its own color and its own place along the station. Standing on a lane shows only what that lane reads.
     */
    private static void arcs(Quads q, HallPlan plan, Snapshot.PartView part, HallPlan.Station st, int standing, Vec3 eye) {
        int lanes = part.added().rows();
        boolean near = eye.z >= st.z() - 6 && eye.z <= st.end() + 6;
        for (int head = 0; head < part.heads(); head++) {
            int rgb = HEAD_COLORS[head % HEAD_COLORS.length];
            float z = st.z() + 1.5f + (st.type().depth - 4f) * (part.heads() == 1 ? 0.5f : head / (float) (part.heads() - 1));
            for (int to = 0; to < lanes; to++) {
                if (near && standing >= 0 && to != standing) {
                    continue;
                }
                for (int from = 0; from <= to; from++) {
                    float w = part.weight(head, to, from);
                    if (w < 0.06f) {
                        continue;
                    }
                    arc(q, (float) plan.lane(to), (float) plan.lane(from), z, w, rgb);
                }
            }
        }
    }

    private static void arc(Quads q, float from, float to, float z, float weight, int rgb) {
        int argb = Math.round(Math.min(1f, weight) * 200) << 24 | rgb;
        float thick = 0.04f + 0.1f * weight;
        float base = LIFT + PANE + 0.15f;
        if (from == to) {
            // a lane reading itself: a short post
            q.wall(from - thick, base, from + thick, base + 0.6f, z, argb);
            return;
        }
        float span = Math.abs(to - from), height = 0.6f + Math.min(4f, span * 0.22f);
        int segments = Math.clamp((int) (span * 1.5f), 6, 20);
        float px = from, py = base;
        for (int i = 1; i <= segments; i++) {
            float u = i / (float) segments;
            float x = from + (to - from) * u, y = base + (float) Math.sin(Math.PI * u) * height;
            q.quad(px, py - thick, z, x, y - thick, z, x, y + thick, z, px, py + thick, z, argb);
            px = x;
            py = y;
        }
    }

    private static Component console(HallPlan.Console c, HallSync sync) {
        Snapshot s = sync.shot();
        Arch arch = sync.arch();
        String key = "residual.inside." + c.role().getSerializedName();
        return switch (c.role()) {
            case RUN -> Component.translatable(s.state() == Snapshot.State.RUNNING ? "residual.inside.pause" : "residual.inside.run")
                    .withStyle(s.state() == Snapshot.State.RUNNING ? ChatFormatting.GREEN : ChatFormatting.WHITE);
            case PACE -> Component.translatable(key, s.pace() == 0 ? Component.translatable("residual.inside.pace.most") : Component.literal(s.pace() + "/s"));
            case RATE -> Component.translatable(key, trim(s.rate()));
            case TASK -> Component.translatable(key, Component.translatable("residual.stage." + sync.stage().id()));
            case WIDTH -> Component.translatable(key, arch.width());
            case HEADS -> Component.translatable(key, arch.heads());
            case POSITIONS -> Component.translatable(key, onOff(arch.positions()));
            case NORM -> Component.translatable(key, onOff(arch.norm()));
            case SWITCH -> {
                boolean on = arch.parts().stream().anyMatch(p -> p.id() == c.part() && p.on());
                yield Component.translatable(key, onOff(on)).withStyle(on ? ChatFormatting.WHITE : ChatFormatting.GRAY);
            }
            default -> Component.translatable(key);
        };
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "residual.inside.on" : "residual.inside.off");
    }

    private static String trim(float rate) {
        String text = String.format(Locale.ROOT, "%.4f", rate);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static Component status(HallSync sync) {
        Snapshot s = sync.shot();
        String loss = Float.isFinite(s.testLoss()) ? String.format(Locale.ROOT, "%.2f", s.testLoss()) : "–";
        Component text = Float.isNaN(s.score())
                ? Component.translatable("residual.inside.status.loss", s.step(), loss)
                : Component.translatable("residual.inside.status", s.step(), loss, Math.round(s.score() * 100));
        if (s.state() == Snapshot.State.DIVERGED) {
            return Component.translatable("residual.inside.diverged").withStyle(ChatFormatting.RED);
        }
        return s.passed() ? text.copy().append(" · ").append(Component.translatable("residual.inside.passed")).withStyle(ChatFormatting.GREEN) : text;
    }

    /** Quads in the hall's own frame, seen from both sides. */
    private record Quads(PoseStack.Pose pose, VertexConsumer buffer, Vec3 base) {
        /** Upright, facing along the hall. */
        void wall(float x0, float y0, float x1, float y1, float z, int argb) {
            quad(x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z, argb);
        }

        /** Flat, at height {@code y}. */
        void floor(float x0, float z0, float x1, float z1, float y, int argb) {
            quad(x0, y, z0, x0, y, z1, x1, y, z1, x1, y, z0, argb);
        }

        void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, int argb) {
            vertex(ax, ay, az, argb);
            vertex(bx, by, bz, argb);
            vertex(cx, cy, cz, argb);
            vertex(dx, dy, dz, argb);
            vertex(dx, dy, dz, argb);
            vertex(cx, cy, cz, argb);
            vertex(bx, by, bz, argb);
            vertex(ax, ay, az, argb);
        }

        private void vertex(float x, float y, float z, int argb) {
            buffer.addVertex(pose, (float) (base.x + x), (float) (base.y + y), (float) (base.z + z)).setColor(argb).setLight(FULL_BRIGHT);
        }
    }
}
