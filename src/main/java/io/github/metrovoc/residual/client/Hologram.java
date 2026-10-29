package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.view.Heatmap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where a machine projects its output: a panel above it, turned to face the viewer, a vector drawn as bars and a
 * matrix as cells. The renderer and the crosshair readout share this geometry.
 */
public final class Hologram {
    /** Gap between the machine's top and the panel, and the panel's longest side. */
    static final double LIFT = 0.4;
    static final double SIZE = 1.1;
    private static final long SHOWN_NS = 250_000_000L;
    private static final Map<BlockPos, Shown> SHOWN = new HashMap<>();

    /** A panel in world space: its center, unit vectors along its width and height, and its size. */
    public record Frame(Vec3 center, Vec3 right, Vec3 up, double width, double height) {
    }

    /** What a hologram drawn this frame shows, for the readout. */
    public record Shown(Heatmap map, Component title, String shape, long at) {
    }

    private Hologram() {
    }

    public static boolean bars(Heatmap map) {
        return map.rows() == 1;
    }

    public static Frame frame(BlockPos pos, Heatmap map, Vec3 camera) {
        double w = SIZE, h = SIZE;
        if (bars(map)) {
            h = SIZE * 0.45;
        } else if (map.height() > map.width()) {
            w = Math.max(0.35, SIZE * map.width() / map.height());
        } else {
            h = Math.max(0.35, SIZE * map.height() / map.width());
        }
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 1 + LIFT + h / 2, pos.getZ() + 0.5);
        Vec3 toward = new Vec3(camera.x - center.x, 0, camera.z - center.z);
        Vec3 n = toward.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : toward.normalize();
        return new Frame(center, new Vec3(n.z, 0, -n.x), new Vec3(0, 1, 0), w, h);
    }

    static void shown(BlockPos pos, Heatmap map, Component title, String shape) {
        SHOWN.put(pos.immutable(), new Shown(map, title, shape, System.nanoTime()));
    }

    /** The holograms drawn in the last moment. */
    static Map<BlockPos, Shown> shown() {
        long now = System.nanoTime();
        SHOWN.values().removeIf(s -> now - s.at > SHOWN_NS);
        return SHOWN;
    }

    /** Where a ray from {@code eye} along {@code look} meets the panel, as distance and cell, or null. */
    @Nullable
    public static Hit hit(Frame f, Heatmap map, Vec3 eye, Vec3 look) {
        Vec3 normal = f.right.cross(f.up);
        double facing = look.dot(normal);
        if (Math.abs(facing) < 1e-6) {
            return null;
        }
        double t = f.center.subtract(eye).dot(normal) / facing;
        if (t <= 0) {
            return null;
        }
        Vec3 d = eye.add(look.scale(t)).subtract(f.center);
        double x = d.dot(f.right) / f.width + 0.5, y = 0.5 - d.dot(f.up) / f.height;
        if (x < 0 || x >= 1 || y < 0 || y >= 1) {
            return null;
        }
        int col = (int) (x * map.cols());
        int row = bars(map) ? 0 : (int) (y * map.rows());
        return new Hit(t, row, col);
    }

    public record Hit(double distance, int row, int col) {
    }
}
