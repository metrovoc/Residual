package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.item.TerminalItem;
import io.github.metrovoc.residual.net.RelaySync;
import io.github.metrovoc.residual.net.TerminalAgentsSync;
import io.github.metrovoc.residual.net.TerminalBaseSync;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.world.Relays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jetbrains.annotations.Nullable;

/**
 * What the client knows of the relay networks, and what a Terminal in hand shows of them: bars for how well it reaches its
 * server, pulses running from mast to mast of its network while its servers answer, and posts along the edge of the
 * network's reach when that is near, so that a player sees where it ends.
 */
public final class RelayView {
    private static final ContextKey<List<Dot>> KEY = new ContextKey<>(Residual.id("relays"));
    private static final int TEAL = 0x3CD6C6;
    private static final int FULL_BRIGHT = 0xF000F0;
    /** How far off the edge of reach and the pulses between masts are drawn. */
    private static final double SEEN = 64, EDGE_SEEN = 24;
    /** Blocks between two posts along the edge, and between pulses along a way between masts. */
    private static final double POSTS = 2, PULSES = 12;
    private static final double SPEED = 16;
    private static final int MOST = 512;

    public static List<RelaySync.Part> parts = List.of();
    public static Map<Integer, RelaySync.Stats> nets = Map.of();
    /** Counts how often {@link #parts} changed. */
    public static int revision;
    /** What the last calls about the base and the agents came back with. */
    @Nullable
    public static TerminalBaseSync base;
    @Nullable
    public static TerminalAgentsSync agents;

    private record Dot(AABB box, int argb) {
    }

    private RelayView() {
    }

    public static void receive(RelaySync sync) {
        if (sync.full()) {
            parts = sync.parts();
            revision++;
        }
        Map<Integer, RelaySync.Stats> stats = new HashMap<>();
        sync.nets().forEach(s -> stats.put(s.id(), s));
        nets = stats;
    }

    /** The network of the part at {@code at}, or -1. */
    public static int netOf(@Nullable GlobalPos at) {
        for (RelaySync.Part p : parts) {
            if (p.at().equals(at)) {
                return p.net();
            }
        }
        return -1;
    }

    /** How far inside the reach of a network a place is; below zero, how far out of it. */
    public static double margin(int net, ResourceKey<Level> dimension, double x, double z) {
        double best = Double.NEGATIVE_INFINITY;
        for (RelaySync.Part p : parts) {
            if (p.net() == net && p.reach() > 0 && p.at().dimension().equals(dimension)) {
                best = Math.max(best, p.reach() - Math.hypot(p.at().pos().getX() - x, p.at().pos().getZ() - z));
            }
        }
        return best;
    }

    /** The network the Terminal in the player's hands is tuned to, or -1. */
    public static int terminalNet(LocalPlayer player) {
        ItemStack held = TerminalItem.held(player);
        return held == null ? -1 : netOf(held.get(ModComponents.LINK.get()));
    }

    /** Why a network's gates and links are shut, as the block says it. */
    public static Component closed(@Nullable RelaySync.Stats net) {
        if (net == null || !net.live()) {
            return Component.translatable("residual.relay_gate.no_model");
        }
        return net.reads() ? Component.translatable("residual.relay_gate.too_slow", net.capacity(), Relays.OPEN_AT)
                : Component.translatable("residual.relay_gate.cannot_read");
    }

    // ---------------------------------------------------------------- on the screen

    /** Up to four bars for how far inside its network's reach a Terminal in hand is, or a red one out of it. */
    public static void signal(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || mc.screen != null || mc.level == null) {
            return;
        }
        int net = terminalNet(player);
        if (net < 0) {
            return;
        }
        double margin = margin(net, mc.level.dimension(), player.getX(), player.getZ());
        int bars = margin >= 32 ? 4 : margin >= 16 ? 3 : margin >= 8 ? 2 : margin >= 0 ? 1 : 0;
        int x = g.guiWidth() - 22, y = 6;
        for (int i = 0; i < 4; i++) {
            int high = 3 + 2 * i;
            g.fill(x + i * 4, y + 9 - high, x + i * 4 + 3, y + 9, i < bars ? 0xFF000000 | TEAL : bars == 0 && i == 0 ? Ui.RED : 0x80404040);
        }
    }

    // ---------------------------------------------------------------- in the world

    public static void extract(ExtractLevelRenderStateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }
        int net = terminalNet(player);
        RelaySync.Stats stats = nets.get(net);
        if (net < 0 || stats == null) {
            return;
        }
        ResourceKey<Level> dimension = level.dimension();
        List<RelaySync.Part> here = new ArrayList<>();
        for (RelaySync.Part p : parts) {
            if (p.net() == net && p.reach() > 0 && p.at().dimension().equals(dimension)) {
                here.add(p);
            }
        }
        Vec3 camera = event.getCamera().position();
        List<Dot> dots = new ArrayList<>();
        if (stats.answers() > 0) {
            double now = System.nanoTime() / 1e9;
            for (int i = 0; i < here.size(); i++) {
                for (int j = i + 1; j < here.size(); j++) {
                    pulses(here.get(i), here.get(j), now, camera, dots);
                }
            }
        }
        for (RelaySync.Part p : here) {
            edge(level, p, here, player, dots);
        }
        if (!dots.isEmpty()) {
            event.getRenderState().setRenderData(KEY, dots);
        }
    }

    private static Vec3 lamp(RelaySync.Part p) {
        int height = p.type() == Relays.Kind.RELAY ? p.reach() / Relays.RANGE : 1;
        return new Vec3(p.at().pos().getX() + 0.5, p.at().pos().getY() + height - 0.2, p.at().pos().getZ() + 0.5);
    }

    /** Requests going to and fro between two masts whose reaches touch. */
    private static void pulses(RelaySync.Part a, RelaySync.Part b, double now, Vec3 camera, List<Dot> dots) {
        Vec3 from = lamp(a), to = lamp(b);
        double length = Math.hypot(from.x - to.x, from.z - to.z);
        if (length > a.reach() + b.reach() || length < 1) {
            return;
        }
        double offset = now * SPEED % PULSES;
        for (double d = offset; d < length && dots.size() < MOST; d += PULSES) {
            Vec3 at = from.lerp(to, d / length);
            if (at.distanceToSqr(camera) < SEEN * SEEN) {
                dots.add(new Dot(new AABB(at.subtract(0.08, 0.08, 0.08), at.add(0.08, 0.08, 0.08)), 0xB0000000 | TEAL));
            }
        }
    }

    /** Posts along the near part of a part's reach, where no other part of the network reaches past it. */
    private static void edge(ClientLevel level, RelaySync.Part p, List<RelaySync.Part> all, LocalPlayer player, List<Dot> dots) {
        double cx = p.at().pos().getX(), cz = p.at().pos().getZ(), r = p.reach();
        double away = Math.hypot(player.getX() - cx, player.getZ() - cz);
        if (Math.abs(away - r) > EDGE_SEEN) {
            return;
        }
        double facing = Math.atan2(player.getZ() - cz, player.getX() - cx);
        double span = Math.min(Math.PI, 2 * EDGE_SEEN / r), step = POSTS / r;
        for (double t = facing - span; t <= facing + span && dots.size() < MOST; t += step) {
            double x = cx + r * Math.cos(t), z = cz + r * Math.sin(t);
            if (Math.hypot(player.getX() - x, player.getZ() - z) > EDGE_SEEN) {
                continue;
            }
            boolean inside = false;
            for (RelaySync.Part o : all) {
                if (o != p && Math.hypot(o.at().pos().getX() - x, o.at().pos().getZ() - z) < o.reach()) {
                    inside = true;
                    break;
                }
            }
            if (!inside) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
                dots.add(new Dot(new AABB(x - 0.06, y, z - 0.06, x + 0.06, y + 0.7, z + 0.06), 0xA0000000 | TEAL));
            }
        }
    }

    public static void submit(SubmitCustomGeometryEvent event) {
        List<Dot> dots = event.getLevelRenderState().getRenderData(KEY);
        if (dots == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), RenderTypes.textBackground(), (pose, buffer) -> {
            for (Dot d : dots) {
                box(pose, buffer, d.box().move(-camera.x, -camera.y, -camera.z), d.argb());
            }
        });
    }

    /** A box's six faces, each wound both ways, at full light. */
    static void box(PoseStack.Pose pose, VertexConsumer buffer, AABB b, int argb) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ;
        float x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        float[][] faces = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0},
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1},
        };
        for (float[] q : faces) {
            for (int v = 0; v < 4; v++) {
                buffer.addVertex(pose, q[v * 3], q[v * 3 + 1], q[v * 3 + 2]).setColor(argb).setLight(FULL_BRIGHT);
            }
            for (int v = 3; v >= 0; v--) {
                buffer.addVertex(pose, q[v * 3], q[v * 3 + 1], q[v * 3 + 2]).setColor(argb).setLight(FULL_BRIGHT);
            }
        }
    }
}
