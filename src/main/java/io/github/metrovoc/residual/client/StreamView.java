package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.net.StreamMarks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * What an instrument found, over the stream it read, for a minute: a label over each machine, and light running from machine to machine
 * as bright as what passes between them. It fades in its last few seconds.
 */
public final class StreamView {
    private record Shown(List<StreamMarks.Mark> marks, List<Beam> beams, Vec3 camera) {
    }

    private record Beam(Vec3 from, Vec3 to, int argb) {
    }

    private static final ContextKey<Shown> KEY = new ContextKey<>(Residual.id("stream_view"));
    private static final long KEPT = 60_000_000_000L;
    private static final double RANGE = 48, WIDTH = 0.05;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static StreamMarks current;
    private static long at;

    private StreamView() {
    }

    public static void receive(StreamMarks marks) {
        current = marks;
        at = System.nanoTime();
    }

    public static void extract(ExtractLevelRenderStateEvent event) {
        if (current == null) {
            return;
        }
        if (System.nanoTime() - at > KEPT) {
            current = null;
            return;
        }
        Vec3 camera = event.getCamera().position();
        List<StreamMarks.Mark> marks = new ArrayList<>();
        for (StreamMarks.Mark m : current.marks()) {
            if (m.pos().getCenter().distanceToSqr(camera) < RANGE * RANGE) {
                marks.add(m);
            }
        }
        List<Beam> beams = new ArrayList<>();
        for (StreamMarks.Beam b : current.beams()) {
            Vec3 a = b.from().getCenter(), c = b.to().getCenter();
            if (a.distanceToSqr(camera) < RANGE * RANGE) {
                beams.add(new Beam(a.add(0, 0.7, 0), c.add(0, 0.7, 0), b.argb()));
            }
        }
        event.getRenderState().setRenderData(KEY, new Shown(marks, beams, camera));
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        Shown shown = event.getLevelRenderState().getRenderData(KEY);
        if (shown == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack pose = event.getPoseStack();
        if (!shown.beams.isEmpty()) {
            event.getSubmitNodeCollector().submitCustomGeometry(pose, RenderTypes.lightning(), (matrix, buffer) -> {
                for (Beam b : shown.beams) {
                    Vec3 a = b.from.subtract(camera), c = b.to.subtract(camera);
                    Vec3 along = c.subtract(a);
                    Vec3 side = along.cross(a).normalize().scale(WIDTH);
                    if (!Double.isFinite(side.x)) {
                        continue;
                    }
                    float[][] quad = {{(float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)}, {(float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)},
                            {(float) (c.x + side.x), (float) (c.y + side.y), (float) (c.z + side.z)}, {(float) (c.x - side.x), (float) (c.y - side.y), (float) (c.z - side.z)}};
                    for (float[] v : quad) {
                        buffer.addVertex(matrix, v[0], v[1], v[2]).setColor(b.argb);
                    }
                    for (int i = 3; i >= 0; i--) {
                        buffer.addVertex(matrix, quad[i][0], quad[i][1], quad[i][2]).setColor(b.argb);
                    }
                }
            });
        }
        for (StreamMarks.Mark m : shown.marks) {
            pose.pushPose();
            Vec3 p = m.pos().getCenter().subtract(camera);
            pose.translate(p.x, p.y, p.z);
            Component text = m.text().copy().withColor(m.color());
            event.getSubmitNodeCollector().submitNameTag(pose, new Vec3(0, 1.1, 0), 0, text, true, FULL_BRIGHT, m.pos().getCenter().distanceToSqr(camera), event.getLevelRenderState().cameraRenderState);
            pose.popPose();
        }
    }
}
