package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.agent.Afloat;
import io.github.metrovoc.residual.net.StepSync;
import io.github.metrovoc.residual.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Vector3fc;

/**
 * The steps floating over bodies at work, in the words of their programs. Only those within {@link #SEEN} blocks float, and
 * of those only the ones nearest where the player is looking, at most {@link #MOST}, and never one over another: a wall of
 * Ports reads as the few being looked at, with their sides lit to say the rest are working too. Text goes through the game's
 * own text submission, and F1 hides it as it hides name tags.
 */
public final class FloatingSteps {
    private static final ContextKey<List<Label>> LABELS = new ContextKey<>(Residual.id("floating_steps"));
    /** How far off a step can be read, and from where it starts to fade. */
    private static final double SEEN = 16, FADES_FROM = 12;
    /** How many float at once. */
    public static final int MOST = 5;
    /** Blocks to a font pixel, a little smaller than a name tag's. */
    private static final float SCALE = 0.02f;
    /** How wide a label can be, in font pixels. */
    private static final int WIDEST = 260;
    /** Fading in or out takes the inverse of this, in seconds. */
    private static final float FADE = 6;
    /** How far to one side of the view a body can be, as the tangent of the angle, and still float. */
    private static final double OFF_CENTRE = 1.2;
    private static final int FULL_BRIGHT = 0xF000F0;

    private static final class Shown {
        final int entity;
        final BlockPos place;
        FormattedCharSequence line;
        String text;
        int width;
        int kind;
        float fade;

        Shown(int entity, BlockPos place) {
            this.entity = entity;
            this.place = place;
        }
    }

    private record Label(Vec3 at, FormattedCharSequence line, int width, int argb, int back, String text) {
    }

    private static final Map<Object, Shown> SHOWN = new HashMap<>();
    private static final List<String> LAST = new ArrayList<>();
    private static ClientLevel seenIn;
    private static long lastFrame;
    private static int heard;

    private FloatingSteps() {
    }

    public static void receive(StepSync sync) {
        heard++;
        Object key = sync.entity() >= 0 ? (Object) sync.entity() : sync.pos().immutable();
        if (sync.step().isEmpty()) {
            SHOWN.remove(key);
            return;
        }
        Shown s = SHOWN.computeIfAbsent(key, k -> new Shown(sync.entity(), k instanceof BlockPos p ? p : null));
        Font font = Minecraft.getInstance().font;
        Component text = Component.literal(sync.step());
        if (sync.more() > 0) {
            text = text.copy().append(Component.literal("  +" + sync.more()).withColor(0x9AA7B2));
        }
        List<FormattedCharSequence> lines = font.split(text, WIDEST);
        s.line = lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.getFirst();
        s.width = font.width(s.line);
        s.text = sync.step();
        s.kind = sync.kind();
    }

    /** What floated in the last frame drawn, for the rehearsal harness. */
    public static List<String> shown() {
        return List.copyOf(LAST);
    }

    /** How many steps have been heard of since last asked, for the rehearsal harness. */
    public static int heard() {
        int n = heard;
        heard = 0;
        return n;
    }

    private static int color(int kind) {
        return kind == Afloat.ACTING ? 0x3CD6C6 : kind == Afloat.HELD ? 0xFFB547 : 0x9AA7B2;
    }

    /** Where a body's label floats, or null if the body is gone. */
    private static Vec3 where(Shown s, ClientLevel level, float partial) {
        if (s.place != null) {
            return level.getBlockState(s.place).is(ModBlocks.PORT.get()) ? Vec3.atCenterOf(s.place).add(0, 0.85, 0) : null;
        }
        Entity e = level.getEntity(s.entity);
        return e == null ? null : e.getPosition(partial).add(0, e.getBbHeight() + 0.75, 0);
    }

    /** A candidate this frame: where it would sit in the view, and how much of the view it takes. */
    private record Seen(Shown shown, Vec3 at, double x, double y, double halfWidth, double halfHeight, double near) {
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        LAST.clear();
        if (SHOWN.isEmpty()) {
            return;
        }
        ClientLevel level = event.getLevel();
        if (level != seenIn) {
            seenIn = level;
            SHOWN.clear();
            return;
        }
        if (!Minecraft.renderNames()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 eye = camera.position();
        Vector3fc forward = camera.forwardVector(), up = camera.upVector(), left = camera.leftVector();
        float partial = camera.getCameraEntityPartialTicks(event.getDeltaTracker());
        List<Seen> seen = new ArrayList<>();
        for (Iterator<Shown> it = SHOWN.values().iterator(); it.hasNext(); ) {
            Shown s = it.next();
            Vec3 at = where(s, level, partial);
            if (at == null || at.distanceToSqr(eye) > 4 * SEEN * SEEN) {
                it.remove();
                continue;
            }
            Vec3 to = at.subtract(eye);
            double depth = to.x * forward.x() + to.y * forward.y() + to.z * forward.z();
            if (depth < 0.5 || to.length() > SEEN) {
                continue;
            }
            double x = (to.x * left.x() + to.y * left.y() + to.z * left.z()) / depth;
            double y = (to.x * up.x() + to.y * up.y() + to.z * up.z()) / depth;
            if (Math.abs(x) <= OFF_CENTRE && Math.abs(y) <= OFF_CENTRE) {
                seen.add(new Seen(s, at, x, y, (s.width + 4) * SCALE / 2 / depth, 11 * SCALE / 2 / depth, x * x + y * y));
            }
        }
        // nearest the middle of the view first, then whatever fits around what is already up
        seen.sort(Comparator.comparingDouble(Seen::near));
        List<Seen> chosen = new ArrayList<>();
        for (Seen s : seen) {
            if (chosen.size() >= MOST) {
                break;
            }
            boolean clear = true;
            for (Seen c : chosen) {
                clear &= Math.abs(s.x - c.x) > s.halfWidth + c.halfWidth || Math.abs(s.y - c.y) > s.halfHeight + c.halfHeight;
            }
            if (clear) {
                chosen.add(s);
            }
        }
        long now = System.nanoTime();
        float step = Math.min(0.25f, (now - lastFrame) / 1e9f) * FADE;
        lastFrame = now;
        List<Label> labels = new ArrayList<>();
        for (Seen s : seen) {
            boolean picked = chosen.contains(s);
            Shown shown = s.shown;
            shown.fade = picked ? Math.min(1, shown.fade + step) : Math.max(0, shown.fade - step);
            double far = s.at.distanceTo(eye);
            float alpha = shown.fade * (float) Math.clamp((SEEN - far) / (SEEN - FADES_FROM), 0, 1);
            if (alpha > 0.04f) {
                int a = Math.round(alpha * 255);
                labels.add(new Label(s.at, shown.line, shown.width, a << 24 | color(shown.kind), Math.round(alpha * 110) << 24, shown.text));
                if (picked) {
                    LAST.add(shown.text);
                }
            }
        }
        if (!labels.isEmpty()) {
            event.getRenderState().setRenderData(LABELS, labels);
        }
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        List<Label> labels = event.getLevelRenderState().getRenderData(LABELS);
        if (labels == null) {
            return;
        }
        CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
        PoseStack stack = event.getPoseStack();
        for (Label l : labels) {
            stack.pushPose();
            stack.translate(l.at.x - camera.pos.x, l.at.y - camera.pos.y, l.at.z - camera.pos.z);
            stack.mulPose(camera.orientation);
            stack.scale(SCALE, -SCALE, SCALE);
            event.getSubmitNodeCollector().submitText(stack, -l.width / 2f, 0, l.line, false, Font.DisplayMode.NORMAL, FULL_BRIGHT, l.argb, l.back, 0);
            stack.popPose();
        }
    }
}
