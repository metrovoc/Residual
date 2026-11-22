package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.interior.Arch;
import io.github.metrovoc.residual.interior.HallPlan;
import io.github.metrovoc.residual.interior.HallSync;
import io.github.metrovoc.residual.interior.Halls;
import io.github.metrovoc.residual.interior.Snapshot;
import io.github.metrovoc.residual.interior.SpaceAct;
import io.github.metrovoc.residual.interior.Stage;
import io.github.metrovoc.residual.learn.Vocab;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Inside a model: the space its numbers live in, drawn round the player, and what the player's hands do there.
 *
 * <p>Every token is two stars, where the model puts it when it reads it and where it puts it as an answer, along the
 * three directions the model's vectors spread most. The text being read is a point for each of its tokens, starting at
 * that token's star and moved by each part in turn; where a point ends, the answers' stars in its direction are the
 * ones the model expects. Nothing here is a block: a player floats, reads what they look at, locks onto it with a
 * click and goes round it, runs the training from keys, and changes the model from a list held open by a key.
 */
public final class SpaceView {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int READ = 0x8FE9DD, ANSWER = 0xFFB547, GREEN = 0x6EE08A, RED = 0xFF6B5E, DIM = 0x9AA6A2, WHITE = 0xFFFFFF,
            ATTENTION = 0xB48CFF, FEED = 0x4FA3FF, PLACE = 0x7A8C88;
    private static final int[] HEAD_COLORS = {0xB48CFF, 0x3CD6C6, 0xFFB547, 0xFF6F8E, 0x7DDC6B, 0x6CA8FF, 0xE6D75A, 0xD98CFF};
    /** How far from the middle the stars stand, taken together. */
    private static final float RADIUS = 13;
    private static final double LABEL_RANGE = 110;

    private static final KeyMapping.Category KEYS = new KeyMapping.Category(Residual.id("inside"));
    private static final KeyMapping RUN = key("run", GLFW.GLFW_KEY_G), STEP = key("step", GLFW.GLFW_KEY_H),
            SLOWER = key("slower", GLFW.GLFW_KEY_COMMA), FASTER = key("faster", GLFW.GLFW_KEY_PERIOD),
            RATE_DOWN = key("rate_down", GLFW.GLFW_KEY_MINUS), RATE_UP = key("rate_up", GLFW.GLFW_KEY_EQUAL),
            BEFORE = key("before", GLFW.GLFW_KEY_LEFT), AFTER = key("after", GLFW.GLFW_KEY_RIGHT),
            EXAMPLE_NEXT = key("example_next", GLFW.GLFW_KEY_UP), EXAMPLE_BACK = key("example_back", GLFW.GLFW_KEY_DOWN),
            HOME = key("home", GLFW.GLFW_KEY_B), CHANGE = key("change", GLFW.GLFW_KEY_LEFT_ALT);

    private static KeyMapping key(String name, int keysym) {
        return new KeyMapping("key.residual.inside." + name, keysym, KEYS);
    }

    /** What a player can look at and lock onto. */
    private enum Kind { READ, ANSWER, POINT, PART }

    private record Target(Kind kind, int index) {
    }

    /** One line of the list that changing the model is chosen from. */
    private record Choice(Component label, @Nullable SpaceAct act) {
    }

    private record Label(Vec3 at, Component text, float size) {
    }

    @Nullable
    private static HallSync latest;
    @Nullable
    private static Snapshot.Tables tables;
    /** Where each star is drawn now, three numbers a token; they drift toward where the model has them. */
    private static float[] readAt = new float[0], answerAt = new float[0];
    private static float scale;
    /** Which of the example's tokens is followed. */
    private static int lane = -1;
    @Nullable
    private static Target looked, locked;
    @Nullable
    private static Vec3 lockedAt;
    private static List<Choice> choices = List.of();
    private static int chosen;
    private static boolean choosing;

    private SpaceView() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> event.register(HallSync.TYPE, (sync, ctx) -> receive(sync)));
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.registerCategory(KEYS);
            for (KeyMapping k : new KeyMapping[] {RUN, STEP, SLOWER, FASTER, RATE_DOWN, RATE_UP, BEFORE, AFTER, EXAMPLE_NEXT, EXAMPLE_BACK, HOME, CHANGE}) {
                event.register(k);
            }
        });
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(Residual.id("inside"), SpaceView::hud));
        NeoForge.EVENT_BUS.addListener(SpaceView::submit);
        NeoForge.EVENT_BUS.addListener(SpaceView::tick);
        NeoForge.EVENT_BUS.addListener(SpaceView::scroll);
        NeoForge.EVENT_BUS.addListener(SpaceView::click);
        NeoForge.EVENT_BUS.addListener(SpaceView::angles);
        // nothing is held up in front of the model
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderHandEvent event) -> {
            if (shown() != null) {
                event.setCanceled(true);
            }
        });
    }

    public static void receive(HallSync sync) {
        if (sync.shot().tables() != null) {
            tables = sync.shot().tables();
        }
        if (latest != null && !latest.origin().equals(sync.origin())) {
            readAt = new float[0];
            locked = null;
        }
        latest = sync;
    }

    /** What the model the player is inside last sent, or null outside one. */
    @Nullable
    public static HallSync shown() {
        Minecraft mc = Minecraft.getInstance();
        if (latest == null || mc.level == null || mc.player == null || mc.level.dimension() != Halls.LEVEL
                || !latest.origin().closerToCenterThan(mc.player.position(), 512)) {
            return null;
        }
        return latest;
    }

    // ---------------------------------------------------------------- where things are

    private static float[] project(Snapshot.Grid grid, int row, float[] basis, @Nullable Snapshot.Grid plus) {
        int width = grid.cols();
        float[] out = new float[3];
        for (int a = 0; a < 3; a++) {
            float sum = 0;
            for (int i = 0; i < width; i++) {
                sum += basis[a * width + i] * (grid.at(row, i) + (plus == null ? 0 : plus.at(row, i)));
            }
            out[a] = sum;
        }
        return out;
    }

    /** Moves the stars a little toward where the model has them now. */
    private static void drift(Snapshot.Tables t) {
        int vocab = t.embed().rows();
        float[] read = new float[vocab * 3], answer = new float[vocab * 3];
        double sum = 1e-9;
        for (int v = 0; v < vocab; v++) {
            float[] r = project(t.embed(), v, t.basis(), null), a = project(t.readout(), v, t.basis(), null);
            System.arraycopy(r, 0, read, v * 3, 3);
            System.arraycopy(a, 0, answer, v * 3, 3);
            sum += r[0] * r[0] + r[1] * r[1] + r[2] * r[2] + a[0] * a[0] + a[1] * a[1] + a[2] * a[2];
        }
        float wanted = RADIUS / (float) Math.sqrt(sum / (2 * vocab));
        if (readAt.length != read.length) {
            scale = wanted;
            for (int i = 0; i < read.length; i++) {
                read[i] *= scale;
                answer[i] *= scale;
            }
            readAt = read;
            answerAt = answer;
            return;
        }
        scale += (wanted - scale) * 0.05f;
        for (int i = 0; i < read.length; i++) {
            readAt[i] += (read[i] * scale - readAt[i]) * 0.12f;
            answerAt[i] += (answer[i] * scale - answerAt[i]) * 0.12f;
        }
    }

    private static Vec3 star(float[] at, int token) {
        return new Vec3(at[token * 3], at[token * 3 + 1], at[token * 3 + 2]);
    }

    /** Where a token's point stands after each thing that moved it: its star first, then each part in turn. */
    private static List<Vec3> path(Snapshot s, Snapshot.Tables t, int lane) {
        List<Vec3> path = new ArrayList<>();
        path.add(scaled(project(s.embed(), lane, t.basis(), null)));
        if (s.position() != null) {
            path.add(scaled(project(s.embed(), lane, t.basis(), s.position())));
        }
        for (Snapshot.PartView part : s.parts()) {
            path.add(scaled(project(part.after(), lane, t.basis(), null)));
        }
        return path;
    }

    private static Vec3 scaled(float[] p) {
        return new Vec3(p[0] * scale, p[1] * scale, p[2] * scale);
    }

    private static int followed(Snapshot s) {
        if (lane < 0 || lane >= s.lanes()) {
            lane = s.lanes() - 1;
        }
        return lane;
    }

    // ---------------------------------------------------------------- drawing

    public static void submit(SubmitCustomGeometryEvent event) {
        HallSync sync = shown();
        Snapshot.Tables t = tables;
        if (sync == null || t == null || t.basis().length != 3 * t.embed().cols() || sync.shot().embed().cols() != t.embed().cols()) {
            looked = null;
            return;
        }
        Snapshot s = sync.shot();
        drift(t);
        Vocab vocab = Vocab.WORLD;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        Quaternionf turn = event.getLevelRenderState().cameraRenderState.orientation;
        BlockPos o = sync.origin();
        Vec3 middle = new Vec3(o.getX() + 0.5, o.getY(), o.getZ() + 0.5);
        Vec3 eye = camera.subtract(middle);
        Vector3f right = turn.transform(new Vector3f(1, 0, 0)), up = turn.transform(new Vector3f(0, 1, 0)), ahead = turn.transform(new Vector3f(0, 0, -1));
        int focus = followed(s);
        int tokens = t.embed().rows();

        List<List<Vec3>> paths = new ArrayList<>();
        for (int l = 0; l < s.lanes(); l++) {
            paths.add(path(s, t, l));
        }
        Vec3 end = paths.get(focus).getLast();

        // what the crosshair is on: the nearest thing to the line of sight
        Target found = null;
        double best = 1;
        Vec3 dir = new Vec3(ahead.x, ahead.y, ahead.z);
        for (int v = 0; v < tokens; v++) {
            double a = aim(eye, dir, star(readAt, v)), b = aim(eye, dir, star(answerAt, v));
            if (a < best) {
                best = a;
                found = new Target(Kind.READ, v);
            }
            if (b < best) {
                best = b;
                found = new Target(Kind.ANSWER, v);
            }
        }
        for (int l = 0; l < s.lanes(); l++) {
            double a = aim(eye, dir, paths.get(l).getLast()) * 0.8;
            if (a < best) {
                best = a;
                found = new Target(Kind.POINT, l);
            }
        }
        List<Vec3> followedPath = paths.get(focus);
        int first = followedPath.size() - s.parts().size();
        for (int i = 0; i < s.parts().size(); i++) {
            Vec3 mid = followedPath.get(first + i - 1).add(followedPath.get(first + i)).scale(0.5);
            double a = aim(eye, dir, mid) * 0.9;
            if (a < best) {
                best = a;
                found = new Target(Kind.PART, s.parts().get(i).id());
            }
        }
        looked = found;
        Target held = locked != null ? locked : looked;
        lockedAt = locked == null ? null : where(locked, s, paths, followedPath).add(middle);

        List<Label> labels = new ArrayList<>();
        Vec3 base = middle.subtract(camera);
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.lightning(), (pose, buffer) -> {
            Shapes q = new Shapes(pose, buffer, base, right, up, eye);
            // every token twice: as it is read, and as an answer, lit by the chance the followed point gives it
            for (int v = 0; v < tokens; v++) {
                boolean inText = false;
                for (int id : s.tokens()) {
                    inText |= id == v;
                }
                Vec3 r = star(readAt, v), a = star(answerAt, v);
                float chance = s.chance(focus, v);
                boolean truth = v == s.targets()[focus];
                q.star(r, inText ? 0.14f : 0.09f, READ, inText ? 1f : 0.6f);
                q.star(a, 0.08f + 0.4f * Mth.sqrt(chance), truth ? GREEN : ANSWER, 0.4f + 0.6f * Mth.sqrt(chance));
                if (chance > 0.02f) {
                    q.ribbon(end, a, 0.02f + 0.12f * chance, truth ? GREEN : ANSWER, Math.min(0.9f, 0.12f + chance));
                }
                if (truth) {
                    q.ring(a, 0.3f + 0.5f * Mth.sqrt(chance), GREEN, 0.9f);
                }
            }
            // each token's point and the way it came: every part's push in that part's colour
            for (int l = 0; l < s.lanes(); l++) {
                List<Vec3> path = paths.get(l);
                boolean on = l == focus;
                int from = path.size() - s.parts().size();
                for (int i = 1; i < path.size(); i++) {
                    int color = i < from ? PLACE : s.parts().get(i - from).heads() > 0 ? ATTENTION : FEED;
                    q.ribbon(path.get(i - 1), path.get(i), on ? 0.09f : 0.025f, color, on ? 0.95f : 0.1f);
                }
                q.star(path.getLast(), on ? 0.2f : 0.08f, WHITE, on ? 1f : 0.5f);
            }
            // what the followed point read through each attention part: a beam from every token it read
            for (int i = 0; i < s.parts().size(); i++) {
                Snapshot.PartView part = s.parts().get(i);
                if (part.pattern() == null) {
                    continue;
                }
                for (int h = 0; h < part.heads(); h++) {
                    for (int l = 0; l <= focus; l++) {
                        float weight = part.weight(h, focus, l);
                        if (weight > 0.04f && l != focus) {
                            q.ribbon(paths.get(l).get(first + i - 1), followedPath.get(first + i), 0.02f + 0.12f * weight, HEAD_COLORS[h % HEAD_COLORS.length], weight * 0.85f);
                        }
                    }
                }
            }
            if (held != null) {
                Vec3 at = where(held, s, paths, followedPath);
                q.ring(at, held.kind == Kind.PART ? 0.6f : 0.45f, WHITE, locked != null ? 1f : 0.55f);
            }
        });

        for (int v = 0; v < tokens; v++) {
            float chance = s.chance(focus, v);
            labels.add(new Label(star(readAt, v).add(0, 0.4, 0), Component.literal(show(vocab, v)).withColor(READ), 0.36f));
            if (chance > 0.03f || held != null && held.kind == Kind.ANSWER && held.index == v) {
                labels.add(new Label(star(answerAt, v).add(0, 0.45 + 0.5 * Mth.sqrt(chance), 0), Component.literal(show(vocab, v)).withColor(v == s.targets()[focus] ? GREEN : ANSWER),
                        0.4f + 0.6f * chance));
            }
        }
        PoseStack pose = event.getPoseStack();
        Font font = Minecraft.getInstance().font;
        for (Label l : labels) {
            Vec3 world = l.at.add(middle);
            if (world.distanceToSqr(camera) > LABEL_RANGE * LABEL_RANGE * l.size * l.size) {
                continue;
            }
            pose.pushPose();
            pose.translate(world.x - camera.x, world.y - camera.y, world.z - camera.z);
            pose.mulPose(turn);
            float size = l.size / font.lineHeight;
            pose.scale(size, -size, size);
            FormattedCharSequence line = l.text.getVisualOrderText();
            event.getSubmitNodeCollector().submitText(pose, -font.width(line) / 2f, -font.lineHeight / 2f, line, false, Font.DisplayMode.NORMAL, FULL_BRIGHT, 0xFFFFFFFF, 0, 0);
            pose.popPose();
        }
    }

    /** How far off the line of sight a point is, against how far off it may be and still be meant; under 1 is on it. */
    private static double aim(Vec3 eye, Vec3 dir, Vec3 point) {
        Vec3 to = point.subtract(eye);
        double along = to.dot(dir);
        if (along < 0.6) {
            return 2;
        }
        return to.subtract(dir.scale(along)).length() / (0.3 + along * 0.022);
    }

    private static Vec3 where(Target target, Snapshot s, List<List<Vec3>> paths, List<Vec3> followedPath) {
        return switch (target.kind) {
            case READ -> star(readAt, Math.min(target.index, readAt.length / 3 - 1));
            case ANSWER -> star(answerAt, Math.min(target.index, answerAt.length / 3 - 1));
            case POINT -> paths.get(Math.min(target.index, paths.size() - 1)).getLast();
            case PART -> {
                int first = followedPath.size() - s.parts().size();
                for (int i = 0; i < s.parts().size(); i++) {
                    if (s.parts().get(i).id() == target.index) {
                        yield followedPath.get(first + i - 1).add(followedPath.get(first + i)).scale(0.5);
                    }
                }
                yield followedPath.getLast();
            }
        };
    }

    private static String show(Vocab vocab, int token) {
        String piece = vocab.piece(token);
        return switch (piece) {
            case "\n" -> "⏎";
            case " " -> "␣";
            default -> piece;
        };
    }

    /** Light in the model's own frame, turned to face the eye. */
    private record Shapes(PoseStack.Pose pose, VertexConsumer buffer, Vec3 base, Vector3f right, Vector3f up, Vec3 eye) {
        /** A star: a bright heart and a wide faint glow. */
        void star(Vec3 at, float size, int rgb, float alpha) {
            square(at, size, rgb, alpha);
            square(at, size * 0.5f, 0xFFFFFF, alpha);
            // four rays, the way a bright star is seen
            Vec3 r = new Vec3(right.x, right.y, right.z).scale(size * 3.4), u = new Vec3(up.x, up.y, up.z).scale(size * 3.4);
            ribbon(at.subtract(r), at.add(r), size * 0.35f, rgb, alpha * 0.45f);
            ribbon(at.subtract(u), at.add(u), size * 0.35f, rgb, alpha * 0.45f);
            square(at, size * 1.8f, rgb, alpha * 0.12f);
        }

        void square(Vec3 at, float half, int rgb, float alpha) {
            int argb = argb(rgb, alpha);
            float x = (float) at.x, y = (float) at.y, z = (float) at.z;
            quad(x - (right.x + up.x) * half, y - (right.y + up.y) * half, z - (right.z + up.z) * half,
                    x + (right.x - up.x) * half, y + (right.y - up.y) * half, z + (right.z - up.z) * half,
                    x + (right.x + up.x) * half, y + (right.y + up.y) * half, z + (right.z + up.z) * half,
                    x - (right.x - up.x) * half, y - (right.y - up.y) * half, z - (right.z - up.z) * half, argb);
        }

        /** A thin square outline round a point. */
        void ring(Vec3 at, float half, int rgb, float alpha) {
            float thick = Math.max(0.03f, half * 0.08f);
            Vec3 r = new Vec3(right.x, right.y, right.z), u = new Vec3(up.x, up.y, up.z);
            Vec3 a = at.add(r.scale(-half)).add(u.scale(-half)), b = at.add(r.scale(half)).add(u.scale(-half)),
                    c = at.add(r.scale(half)).add(u.scale(half)), d = at.add(r.scale(-half)).add(u.scale(half));
            ribbon(a, b, thick, rgb, alpha);
            ribbon(b, c, thick, rgb, alpha);
            ribbon(c, d, thick, rgb, alpha);
            ribbon(d, a, thick, rgb, alpha);
        }

        /** A line of light from {@code a} to {@code b}, its flat side to the eye. */
        void ribbon(Vec3 a, Vec3 b, float width, int rgb, float alpha) {
            Vec3 along = b.subtract(a);
            Vec3 side = along.cross(a.add(b).scale(0.5).subtract(eye));
            if (side.lengthSqr() < 1e-9) {
                return;
            }
            side = side.normalize().scale(width / 2);
            int argb = argb(rgb, alpha);
            quad((float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z), (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z),
                    (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z), (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z), argb);
        }

        private static int argb(int rgb, float alpha) {
            return Math.round(Math.clamp(alpha, 0f, 1f) * 255) << 24 | rgb;
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

    // ---------------------------------------------------------------- hands

    private static void send(HallPlan.Role role, int part, boolean back) {
        ClientPacketDistributor.sendToServer(new SpaceAct(role, part, back));
    }

    public static void tick(ClientTickEvent.Post event) {
        HallSync sync = shown();
        Minecraft mc = Minecraft.getInstance();
        if (sync == null || mc.player == null) {
            locked = null;
            choosing = false;
            for (KeyMapping k : new KeyMapping[] {RUN, STEP, SLOWER, FASTER, RATE_DOWN, RATE_UP, BEFORE, AFTER, EXAMPLE_NEXT, EXAMPLE_BACK, HOME}) {
                while (k.consumeClick()) {
                    // these keys do nothing outside a model
                }
            }
            return;
        }
        Snapshot s = sync.shot();
        while (RUN.consumeClick()) {
            send(HallPlan.Role.RUN, 0, false);
        }
        while (STEP.consumeClick()) {
            send(HallPlan.Role.STEP, 0, mc.player.isShiftKeyDown());
        }
        while (SLOWER.consumeClick()) {
            send(HallPlan.Role.PACE, 0, true);
        }
        while (FASTER.consumeClick()) {
            send(HallPlan.Role.PACE, 0, false);
        }
        while (RATE_DOWN.consumeClick()) {
            send(HallPlan.Role.RATE, 0, true);
        }
        while (RATE_UP.consumeClick()) {
            send(HallPlan.Role.RATE, 0, false);
        }
        while (EXAMPLE_NEXT.consumeClick()) {
            send(HallPlan.Role.EXAMPLE, 0, false);
        }
        while (EXAMPLE_BACK.consumeClick()) {
            send(HallPlan.Role.EXAMPLE, 0, true);
        }
        while (BEFORE.consumeClick()) {
            lane = Math.max(0, followed(s) - 1);
        }
        while (AFTER.consumeClick()) {
            lane = Math.min(s.lanes() - 1, followed(s) + 1);
        }
        while (HOME.consumeClick()) {
            locked = null;
            ClientPacketDistributor.sendToServer(new SpaceAct(SpaceAct.HOME, 0, false));
        }
        // the list of changes is open while its key is held, and the line it is on is done when the key is let go
        if (CHANGE.isDown() && !choosing) {
            choosing = true;
            chosen = 0;
            choices = choices(sync);
        } else if (!CHANGE.isDown() && choosing) {
            choosing = false;
            Choice choice = choices.get(Math.clamp(chosen, 0, choices.size() - 1));
            if (choice.act != null) {
                ClientPacketDistributor.sendToServer(choice.act);
                if (choice.act.role() == HallPlan.Role.REMOVE.ordinal()) {
                    locked = null;
                }
            }
        }
        face(mc.player, 1f);
    }

    /** Turns the player to what they are locked onto, so that moving sideways goes round it. */
    private static void face(LocalPlayer player, float partial) {
        if (locked == null || lockedAt == null) {
            return;
        }
        Vec3 to = lockedAt.subtract(player.getEyePosition(partial));
        if (to.lengthSqr() < 0.04) {
            return;
        }
        float yaw = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG), pitch = (float) (-Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;
    }

    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (shown() == null || locked == null || lockedAt == null || mc.player == null || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        Vec3 to = lockedAt.subtract(event.getCamera().position());
        if (to.lengthSqr() < 0.04) {
            return;
        }
        event.setYaw((float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG));
        event.setPitch((float) (-Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG));
    }

    /** A click locks onto what is looked at, or lets go of it. */
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        HallSync sync = shown();
        if (sync == null) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (!event.isAttack()) {
            return;
        }
        if (locked != null) {
            locked = null;
            return;
        }
        locked = looked;
        if (locked == null) {
            return;
        }
        Snapshot s = sync.shot();
        if (locked.kind == Kind.POINT) {
            lane = locked.index;
        } else if (locked.kind == Kind.READ) {
            // a token of the text: follow it where it stands nearest the end
            for (int l = 0; l < s.lanes(); l++) {
                if (s.tokens()[l] == locked.index) {
                    lane = l;
                }
            }
        }
    }

    /** The wheel: down the list while it is open, the value of a part locked onto, and otherwise how fast the player flies. */
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        HallSync sync = shown();
        Minecraft mc = Minecraft.getInstance();
        if (sync == null || mc.player == null || event.getScrollDeltaY() == 0) {
            return;
        }
        event.setCanceled(true);
        wheel(event.getScrollDeltaY() > 0);
    }

    /** One notch of the wheel, inside a model. */
    public static void wheel(boolean up) {
        HallSync sync = shown();
        Minecraft mc = Minecraft.getInstance();
        if (sync == null || mc.player == null) {
            return;
        }
        if (choosing) {
            chosen = Math.floorMod(chosen + (up ? -1 : 1), choices.size());
        } else if (locked != null && locked.kind == Kind.PART && attention(sync, locked.index)) {
            send(HallPlan.Role.HEADS, locked.index, !up);
        } else if (locked != null && locked.kind == Kind.POINT) {
            lane = Math.clamp(followed(sync.shot()) + (up ? 1 : -1), 0, sync.shot().lanes() - 1);
            locked = new Target(Kind.POINT, lane);
        } else {
            float speed = Math.clamp(mc.player.getAbilities().getFlyingSpeed() * (up ? 1.3f : 1 / 1.3f), 0.01f, 0.5f);
            mc.player.getAbilities().setFlyingSpeed(speed);
        }
    }

    private static boolean attention(HallSync sync, int part) {
        for (Arch.Part p : sync.arch().parts()) {
            if (p.id() == part) {
                return p.kind() == Arch.Kind.ATTENTION;
            }
        }
        return false;
    }

    /** What can be changed from here: the part locked onto first, then the model as a whole. */
    private static List<Choice> choices(HallSync sync) {
        Arch arch = sync.arch();
        List<Choice> list = new ArrayList<>();
        list.add(new Choice(Component.translatable("residual.inside.change.nothing"), null));
        if (locked != null && locked.kind == Kind.PART) {
            boolean on = arch.parts().stream().anyMatch(p -> p.id() == locked.index && p.on());
            list.add(new Choice(Component.translatable("residual.inside.switch", onOff(!on)), new SpaceAct(HallPlan.Role.SWITCH, locked.index, false)));
            list.add(new Choice(Component.translatable("residual.inside.remove"), new SpaceAct(HallPlan.Role.REMOVE, locked.index, false)));
        }
        int last = arch.parts().size();
        list.add(new Choice(Component.translatable("residual.inside.add_attention"), new SpaceAct(HallPlan.Role.ADD_ATTENTION, last, false)));
        list.add(new Choice(Component.translatable("residual.inside.add_feed_forward"), new SpaceAct(HallPlan.Role.ADD_FEED_FORWARD, last, false)));
        for (Arch.Part p : arch.parts()) {
            if (!p.on()) {
                list.add(new Choice(Component.translatable("residual.inside.change.back", Component.translatable(p.kind() == Arch.Kind.ATTENTION
                        ? "residual.inside.part.attention" : "residual.inside.part.feed_forward")), new SpaceAct(HallPlan.Role.SWITCH, p.id(), false)));
            }
        }
        list.add(new Choice(Component.translatable("residual.inside.positions", onOff(!arch.positions())), new SpaceAct(HallPlan.Role.POSITIONS, 0, false)));
        list.add(new Choice(Component.translatable("residual.inside.norm", onOff(!arch.norm())), new SpaceAct(HallPlan.Role.NORM, 0, false)));
        list.add(new Choice(Component.translatable("residual.inside.change.wider", arch.width()), new SpaceAct(HallPlan.Role.WIDTH, 0, false)));
        list.add(new Choice(Component.translatable("residual.inside.change.narrower", arch.width()), new SpaceAct(HallPlan.Role.WIDTH, 0, true)));
        list.add(new Choice(Component.translatable("residual.inside.change.task"), new SpaceAct(HallPlan.Role.TASK, 0, false)));
        list.add(new Choice(Component.translatable("residual.inside.restart"), new SpaceAct(HallPlan.Role.RESTART, 0, false)));
        list.add(new Choice(Component.translatable("residual.inside.exit"), new SpaceAct(HallPlan.Role.EXIT, 0, false)));
        return list;
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "residual.inside.on" : "residual.inside.off");
    }

    // ---------------------------------------------------------------- what is written on the glass

    private static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
        HallSync sync = shown();
        Minecraft mc = Minecraft.getInstance();
        if (sync == null || mc.options.hideGui || mc.screen != null) {
            return;
        }
        Snapshot s = sync.shot();
        Font font = mc.font;
        Vocab vocab = Vocab.WORLD;
        int w = g.guiWidth(), h = g.guiHeight();
        int focus = followed(s);

        // the run, top left
        int y = 6;
        g.text(font, status(sync), 6, y, 0xFFFFFFFF, true);
        y += 11;
        g.text(font, goal(sync.stage()), 6, y, 0xFF000000 | DIM, true);
        y += 11;
        Component state = Component.translatable(s.state() == Snapshot.State.RUNNING ? "residual.inside.state.running" : "residual.inside.state.paused",
                s.pace() == 0 ? Component.translatable("residual.inside.pace.most") : Component.literal(s.pace() + "/s"), trim(s.rate()));
        g.text(font, state, 6, y, 0xFF000000 | (s.state() == Snapshot.State.RUNNING ? READ : DIM), true);
        if (tables != null && tables.written() != null) {
            StringBuilder text = new StringBuilder();
            for (int id : tables.written()) {
                text.append(show(vocab, id));
            }
            y += 14;
            g.text(font, Component.translatable("residual.inside.writes"), 6, y, 0xFF000000 | DIM, true);
            y += 10;
            g.text(font, text.toString(), 6, y, 0xFFFFFFFF, true);
        }

        // the text being read, top middle: each token, and under it what the model expects next
        int cell = 9, x0 = (w - s.lanes() * cell) / 2, ty = 8;
        for (int l = 0; l < s.lanes(); l++) {
            int x = x0 + l * cell;
            if (l == focus) {
                g.fill(x - 1, ty - 2, x + cell - 1, ty + 21, 0x50FFFFFF);
            }
            String token = show(vocab, s.tokens()[l]);
            g.text(font, token, x + (cell - 2 - font.width(token)) / 2, ty, l == focus ? 0xFFFFFFFF : 0xFF000000 | READ, true);
            int top = 0;
            for (int v = 1; v < s.vocab(); v++) {
                if (s.chance(l, v) > s.chance(l, top)) {
                    top = v;
                }
            }
            String guess = show(vocab, top);
            int color = !s.scored()[l] ? DIM : top == s.targets()[l] ? GREEN : sync.stage().rightGoal() > 0 ? RED : ANSWER;
            g.text(font, guess, x + (cell - 2 - font.width(guess)) / 2, ty + 11, 0xFF000000 | color, true);
        }

        // what is looked at or locked onto, beside the crosshair
        Target held = locked != null ? locked : looked;
        if (held != null && !choosing) {
            List<Component> lines = describe(held, sync, focus);
            int ly = h / 2 - lines.size() * 5;
            for (Component line : lines) {
                g.text(font, line, w / 2 + 14, ly, 0xFFFFFFFF, true);
                ly += 10;
            }
        }

        // the list of changes, under the crosshair while its key is held
        if (choosing) {
            int ly = h / 2 + 12, wide = 0;
            for (Choice c : choices) {
                wide = Math.max(wide, font.width(c.label));
            }
            g.fill(w / 2 - wide / 2 - 8, ly - 5, w / 2 + wide / 2 + 8, ly + choices.size() * 11 + 2, 0xC0060A0C);
            for (int i = 0; i < choices.size(); i++) {
                Component label = choices.get(i).label;
                int lw = font.width(label);
                if (i == chosen) {
                    g.fill(w / 2 - lw / 2 - 4, ly - 2, w / 2 + lw / 2 + 4, ly + 9, 0x70FFFFFF);
                }
                g.text(font, label, w / 2 - lw / 2, ly, i == chosen ? 0xFFFFFFFF : 0xFF000000 | DIM, true);
                ly += 11;
            }
        }

        // the keys, bottom left
        Component[] keys = {
                hint("run", RUN), hint("step", STEP), hint("pace", SLOWER, FASTER), hint("rate", RATE_DOWN, RATE_UP), hint("token", BEFORE, AFTER),
                hint("example", EXAMPLE_BACK, EXAMPLE_NEXT), hint("lock", mc.options.keyAttack), hint("change", CHANGE), hint("home", HOME)};
        int ky = h - 8 - keys.length * 9;
        for (Component k : keys) {
            g.text(font, k, 6, ky, 0xB0000000 | DIM, true);
            ky += 9;
        }
    }

    private static Component hint(String what, KeyMapping... keys) {
        Object[] names = new Object[keys.length];
        for (int i = 0; i < keys.length; i++) {
            names[i] = keys[i].getTranslatedKeyMessage();
        }
        return Component.translatable("residual.inside.keys." + what, names);
    }

    private static List<Component> describe(Target target, HallSync sync, int focus) {
        Snapshot s = sync.shot();
        Vocab vocab = Vocab.WORLD;
        List<Component> lines = new ArrayList<>();
        String before = show(vocab, s.tokens()[focus]);
        switch (target.kind) {
            case READ -> {
                lines.add(Component.translatable("residual.inside.see.read", show(vocab, target.index)).withColor(READ));
                if (tables != null && target.index < tables.embed().rows()) {
                    lines.add(Component.literal(numbers(tables.embed(), target.index)).withColor(DIM));
                }
            }
            case ANSWER -> {
                lines.add(Component.translatable("residual.inside.see.answer", show(vocab, target.index)).withColor(ANSWER));
                lines.add(Component.translatable("residual.inside.see.chance", before, percent(s.chance(focus, target.index))));
                if (tables != null && target.index < tables.readout().rows()) {
                    lines.add(Component.literal(numbers(tables.readout(), target.index)).withColor(DIM));
                }
            }
            case POINT -> {
                int l = Math.min(target.index, s.lanes() - 1);
                int top = 0;
                for (int v = 1; v < s.vocab(); v++) {
                    if (s.chance(l, v) > s.chance(l, top)) {
                        top = v;
                    }
                }
                lines.add(Component.translatable("residual.inside.see.point", show(vocab, s.tokens()[l]), l + 1));
                lines.add(Component.translatable("residual.inside.see.expects", show(vocab, top), percent(s.chance(l, top))).withColor(ANSWER));
                lines.add(Component.translatable("residual.inside.see.truth", show(vocab, s.targets()[l]), percent(s.chance(l, s.targets()[l])),
                        String.format(Locale.ROOT, "%.2f", s.laneLoss()[l])).withColor(GREEN));
            }
            case PART -> {
                for (Arch.Part p : sync.arch().parts()) {
                    if (p.id() == target.index) {
                        boolean attention = p.kind() == Arch.Kind.ATTENTION;
                        lines.add(Component.translatable(attention ? "residual.inside.part.attention" : "residual.inside.part.feed_forward").withColor(attention ? ATTENTION : FEED));
                        lines.add(Component.translatable("residual.inside.see.push", before));
                        if (attention) {
                            lines.add(Component.translatable("residual.inside.heads", sync.arch().heads()).withColor(DIM));
                        }
                    }
                }
            }
        }
        if (locked != null) {
            lines.add(Component.translatable(target.kind == Kind.PART ? "residual.inside.see.locked.part" : "residual.inside.see.locked").withColor(DIM));
        }
        return lines;
    }

    private static String numbers(Snapshot.Grid grid, int row) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(grid.cols(), 6); i++) {
            text.append(String.format(Locale.ROOT, "%+.2f ", grid.at(row, i)));
        }
        return grid.cols() > 6 ? text + "…" : text.toString().trim();
    }

    private static String percent(float chance) {
        return chance < 0.01f ? "<1%" : Math.round(chance * 100) + "%";
    }

    private static Component goal(Stage stage) {
        Component name = Component.translatable("residual.stage." + stage.id());
        return stage.lossGoal() > 0
                ? Component.translatable("residual.inside.goal.loss", name, String.format(Locale.ROOT, "%.2f", stage.lossGoal()))
                : Component.translatable("residual.inside.goal.right", name, Math.round(stage.rightGoal() * 100));
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
}
