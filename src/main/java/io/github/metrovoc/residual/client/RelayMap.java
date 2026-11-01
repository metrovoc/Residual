package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.net.RelaySync;
import io.github.metrovoc.residual.world.Relays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * A map of the relay networks, seen from above: the network in focus as the ground its reach covers, on the ground as
 * far as the client has it; its parts over that, and the player. It is reach as it is: level, at any height. Drawn into a
 * texture a cell at a time, and again only when the view or what it shows has changed.
 */
final class RelayMap {
    /** Pixels of the screen to a cell of the map. */
    private static final int CELL = 2;
    private static final double[] ZOOMS = {0.25, 0.5, 1, 2, 4, 8, 16, 32, 64, 128};
    /** Ticks a drawing of the ground is good for, as the player walks on and chunks come. */
    private static final int GROUND = 40;
    /** What sets the parts off from what lies under them. */
    private static final int UNDER = 0xFF0B0F13;
    private static int made;

    private final int x, y, w, h;
    private final IntSupplier focus;
    private final Identifier id = Residual.id("relay_map/" + made++);
    private final DynamicTexture texture;
    private ResourceKey<Level> dimension;
    private double cx, cz;
    private int zoom = 2;
    private boolean stale = true;
    private int seen = -1, seenFocus = -2;
    private long drawnAt;
    /** Whether it has framed the network since it first heard of it. */
    private boolean framed;

    RelayMap(int x, int y, int w, int h, IntSupplier focus, ResourceKey<Level> dimension, double cx, double cz) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.focus = focus;
        this.dimension = dimension;
        this.cx = cx;
        this.cz = cz;
        texture = new DynamicTexture(() -> "Relay map", new NativeImage(w / CELL, h / CELL, false));
        Minecraft.getInstance().getTextureManager().register(id, texture);
    }

    void close() {
        Minecraft.getInstance().getTextureManager().release(id);
    }

    private double blocks() {
        return ZOOMS[zoom];
    }

    /** Every dimension the network in focus stands in, the map's own first. */
    List<ResourceKey<Level>> dimensions() {
        Set<ResourceKey<Level>> out = new LinkedHashSet<>();
        out.add(dimension);
        for (RelaySync.Part p : RelayView.parts) {
            if (p.net() == focus.getAsInt()) {
                out.add(p.at().dimension());
            }
        }
        return List.copyOf(out);
    }

    ResourceKey<Level> dimension() {
        return dimension;
    }

    void show(ResourceKey<Level> dimension) {
        this.dimension = dimension;
        fit();
    }

    /** Frames all of the network in focus in this dimension, and the player if they are in it. */
    void fit() {
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (RelaySync.Part p : RelayView.parts) {
            if (p.net() == focus.getAsInt() && p.at().dimension().equals(dimension)) {
                x0 = Math.min(x0, p.at().pos().getX() - p.reach());
                x1 = Math.max(x1, p.at().pos().getX() + p.reach());
                z0 = Math.min(z0, p.at().pos().getZ() - p.reach());
                z1 = Math.max(z1, p.at().pos().getZ() + p.reach());
            }
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.level().dimension().equals(dimension)) {
            x0 = Math.min(x0, player.getX());
            x1 = Math.max(x1, player.getX());
            z0 = Math.min(z0, player.getZ());
            z1 = Math.max(z1, player.getZ());
        }
        if (x0 > x1) {
            return;
        }
        cx = (x0 + x1) / 2;
        cz = (z0 + z1) / 2;
        zoom = ZOOMS.length - 1;
        while (zoom > 0 && (x1 - x0 + 8) / ZOOMS[zoom - 1] < w && (z1 - z0 + 8) / ZOOMS[zoom - 1] < h) {
            zoom--;
        }
        stale = true;
    }

    boolean inside(double mx, double my) {
        return Ui.inside(mx, my, x, y, w, h);
    }

    /** Zooms in or out around the place under the mouse. */
    void scrolled(double mx, double my, double amount) {
        double wx = worldX(mx), wz = worldZ(my);
        zoom = Math.clamp(zoom - (int) Math.signum(amount), 0, ZOOMS.length - 1);
        cx = wx - (mx - x - w / 2.0) * blocks();
        cz = wz - (my - y - h / 2.0) * blocks();
        stale = true;
    }

    void dragged(double dx, double dy) {
        cx -= dx * blocks();
        cz -= dy * blocks();
        stale = true;
    }

    private double worldX(double sx) {
        return cx + (sx - x - w / 2.0) * blocks();
    }

    private double worldZ(double sy) {
        return cz + (sy - y - h / 2.0) * blocks();
    }

    private int screenX(double wx) {
        return (int) Math.round(x + w / 2.0 + (wx - cx) / blocks());
    }

    private int screenZ(double wz) {
        return (int) Math.round(y + h / 2.0 + (wz - cz) / blocks());
    }

    // ---------------------------------------------------------------- the ground and the reach

    private void raster(Minecraft mc) {
        NativeImage image = texture.getPixels();
        int cols = image.getWidth(), rows = image.getHeight(), net = focus.getAsInt();
        List<RelaySync.Part> discs = new ArrayList<>();
        for (RelaySync.Part p : RelayView.parts) {
            if (p.reach() > 0 && p.at().dimension().equals(dimension)) {
                discs.add(p);
            }
        }
        ClientLevel level = mc.level;
        boolean here = level != null && level.dimension().equals(dimension);
        // 0 out of reach, 1 in reach of the network in focus, 2 of another
        byte[] reach = new byte[cols * rows];
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double wx = worldX(x + (c + 0.5) * CELL), wz = worldZ(y + (r + 0.5) * CELL);
                for (RelaySync.Part p : discs) {
                    double dx = p.at().pos().getX() - wx, dz = p.at().pos().getZ() - wz;
                    if (dx * dx + dz * dz <= (double) p.reach() * p.reach()) {
                        reach[r * cols + c] = (byte) (p.net() == net ? 1 : 2);
                        if (p.net() == net) {
                            break;
                        }
                    }
                }
            }
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int color = Ui.SCREEN;
                int bx = (int) Math.floor(worldX(x + (c + 0.5) * CELL)), bz = (int) Math.floor(worldZ(y + (r + 0.5) * CELL));
                if (here && level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) {
                    at.set(bx, level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1, bz);
                    BlockState top = level.getBlockState(at);
                    color = mix(color, 0xFF000000 | top.getMapColor(level, at).col, 0.42);
                }
                byte in = reach[r * cols + c];
                boolean rim = in != 0 && (r == 0 || c == 0 || r == rows - 1 || c == cols - 1 || reach[(r - 1) * cols + c] != in
                        || reach[(r + 1) * cols + c] != in || reach[r * cols + c - 1] != in || reach[r * cols + c + 1] != in);
                if (in == 1) {
                    color = rim ? Ui.TEAL : mix(color, Ui.TEAL, 0.2);
                } else if (in == 2) {
                    color = rim ? Ui.DIM : mix(color, Ui.DIM, 0.12);
                }
                image.setPixel(c, r, color);
            }
        }
        texture.upload();
    }

    private static int mix(int a, int b, double t) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return 0xFF000000 | (int) (ar + (br - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }

    // ---------------------------------------------------------------- drawing

    void draw(GuiGraphicsExtractor g, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        int net = focus.getAsInt();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        if (!framed && net >= 0) {
            framed = true;
            fit();
        }
        if (stale || seen != RelayView.revision || seenFocus != net || now - drawnAt > GROUND) {
            raster(mc);
            stale = false;
            seen = RelayView.revision;
            seenFocus = net;
            drawnAt = now;
        }
        g.blit(id, x, y, x + w / CELL * CELL, y + h / CELL * CELL, 0, 1, 0, 1);
        g.enableScissor(x, y, x + w, y + h);
        List<RelaySync.Part> shown = new ArrayList<>();
        for (RelaySync.Part p : RelayView.parts) {
            if (p.at().dimension().equals(dimension)) {
                shown.add(p);
            }
        }
        RelaySync.Stats stats = RelayView.nets.get(net);
        for (int i = 0; i < shown.size(); i++) {
            RelaySync.Part a = shown.get(i);
            if (a.net() != net || a.reach() == 0) {
                continue;
            }
            for (int j = i + 1; j < shown.size(); j++) {
                RelaySync.Part b = shown.get(j);
                if (b.net() == net && b.reach() > 0
                        && Math.hypot(a.at().pos().getX() - b.at().pos().getX(), a.at().pos().getZ() - b.at().pos().getZ()) <= a.reach() + b.reach()) {
                    way(g, a, b, stats != null && stats.answers() > 0);
                }
            }
        }
        for (RelaySync.Part p : shown) {
            glyph(g, p, p.net() == net, stats);
        }
        LocalPlayer player = mc.player;
        if (player != null && player.level().dimension().equals(dimension)) {
            int sx = screenX(player.getX()), sy = screenZ(player.getZ());
            double yaw = Math.toRadians(player.getYRot());
            g.fill(sx - 1, sy - 1, sx + 2, sy + 2, Ui.TEXT);
            for (int i = 2; i <= 4; i++) {
                int tx = (int) Math.round(sx - Math.sin(yaw) * i), ty = (int) Math.round(sy + Math.cos(yaw) * i);
                g.fill(tx, ty, tx + 1, ty + 1, Ui.TEXT);
            }
        }
        g.disableScissor();
    }

    /** A dotted way between two parts, and requests running along it while their network answers. */
    private void way(GuiGraphicsExtractor g, RelaySync.Part a, RelaySync.Part b, boolean running) {
        int x0 = screenX(a.at().pos().getX()), y0 = screenZ(a.at().pos().getZ());
        int x1 = screenX(b.at().pos().getX()), y1 = screenZ(b.at().pos().getZ());
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i += 3) {
            int px = x0 + (x1 - x0) * i / Math.max(1, steps), py = y0 + (y1 - y0) * i / Math.max(1, steps);
            g.fill(px, py, px + 1, py + 1, 0xFF8FE9DF);
        }
        if (running && steps > 0) {
            double t = (System.currentTimeMillis() / 900.0 + Math.floorMod(a.at().pos().hashCode(), 100) / 100.0) % 1;
            int px = (int) (x0 + (x1 - x0) * t), py = (int) (y0 + (y1 - y0) * t);
            g.fill(px - 1, py - 1, px + 1, py + 1, Ui.TEXT);
        }
    }

    private void glyph(GuiGraphicsExtractor g, RelaySync.Part p, boolean focused, @Nullable RelaySync.Stats stats) {
        int sx = screenX(p.at().pos().getX()), sy = screenZ(p.at().pos().getZ());
        int color = !focused ? Ui.DIM : switch (p.type()) {
            case SERVER -> p.model() ? Ui.BLUE : Ui.DIM;
            case RELAY -> stats != null && stats.live() ? Ui.TEAL : Ui.DIM;
            case GATE -> stats != null && stats.open() ? Ui.AMBER : Ui.DIM;
            case LINK -> stats != null && stats.open() ? Ui.GREEN : Ui.DIM;
        };
        switch (p.type()) {
            case SERVER -> {
                g.fill(sx - 3, sy - 3, sx + 4, sy + 4, UNDER);
                g.fill(sx - 2, sy - 2, sx + 3, sy + 3, color);
            }
            case RELAY -> {
                for (int i = 0; i <= 3; i++) {
                    g.fill(sx - 3 + i, sy - i, sx + 4 - i, sy + i + 1, UNDER);
                }
                for (int i = 0; i <= 2; i++) {
                    g.fill(sx - 2 + i, sy - i, sx + 3 - i, sy + i + 1, color);
                }
            }
            case GATE -> {
                g.fill(sx - 4, sy - 4, sx + 5, sy + 5, UNDER);
                g.fill(sx - 3, sy - 3, sx + 4, sy + 4, color);
                g.fill(sx - 1, sy - 1, sx + 2, sy + 2, UNDER);
            }
            case LINK -> {
                g.fill(sx - 3, sy - 2, sx + 4, sy + 3, UNDER);
                g.fill(sx - 2, sy - 3, sx + 3, sy + 4, UNDER);
                g.fill(sx - 2, sy - 1, sx + 3, sy + 2, color);
                g.fill(sx - 1, sy - 2, sx + 2, sy + 3, color);
            }
        }
    }

    // ---------------------------------------------------------------- what is under the mouse

    /** The part under the mouse, gates before anything else, or null. */
    @Nullable
    RelaySync.Part under(double mx, double my) {
        if (!inside(mx, my)) {
            return null;
        }
        RelaySync.Part best = null;
        double nearest = 36;
        for (RelaySync.Part p : RelayView.parts) {
            if (p.at().dimension().equals(dimension)) {
                double d = Math.pow(screenX(p.at().pos().getX()) - mx, 2) + Math.pow(screenZ(p.at().pos().getZ()) - my, 2);
                if (p.type() == Relays.Kind.GATE) {
                    d /= 2;
                }
                if (d < nearest) {
                    nearest = d;
                    best = p;
                }
            }
        }
        return best;
    }

    static String name(RelaySync.Part p) {
        return p.name().isEmpty() ? Component.translatable("residual.map.part." + p.type().name().toLowerCase(Locale.ROOT), p.at().pos().getX(), p.at().pos().getZ()).getString() : p.name();
    }

    /** What a part is and how it stands, for its tooltip. */
    static List<Component> about(RelaySync.Part p) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(name(p)));
        RelaySync.Stats net = RelayView.nets.get(p.net());
        ClientLevel level = Minecraft.getInstance().level;
        out.add((level != null && level.dimension().equals(p.at().dimension()) ? Component.translatable("residual.map.at", p.at().pos().toShortString())
                : Component.translatable("residual.map.at_in", p.at().pos().toShortString(), dimensionName(p.at().dimension()))).withColor(Ui.DIM));
        switch (p.type()) {
            case SERVER -> out.add(net == null || !p.model() ? Component.translatable("residual.server.empty").withColor(Ui.DIM)
                    : Component.translatable("residual.map.answers", net.capacity(), net.answers()).withColor(Ui.DIM));
            case RELAY -> out.add(Component.translatable("residual.map.reach", p.reach()).withColor(Ui.DIM));
            case GATE -> {
                if (p.pair().isPresent()) {
                    GlobalPos pair = p.pair().get();
                    out.add(Component.translatable("residual.map.tuned", pair.pos().toShortString(), dimensionName(pair.dimension())).withColor(Ui.DIM));
                }
                out.add((net != null && net.open() ? Component.translatable("residual.map.open") : RelayView.closed(net)).copy().withColor(Ui.DIM));
            }
            case LINK -> out.add(net == null || !net.open() ? RelayView.closed(net).copy().withColor(Ui.DIM)
                    : Component.translatable("residual.map.carrying", String.format(Locale.ROOT, "%,d", net.items()),
                            String.format(Locale.ROOT, "%,d", net.fe() / 20)).withColor(Ui.DIM));
        }
        return out;
    }

    /** What a part is, as a tooltip; {@code click} says a click on a gate goes there. */
    static Component tooltip(RelaySync.Part part, boolean click) {
        Component text = Component.empty();
        List<Component> lines = about(part);
        for (int i = 0; i < lines.size(); i++) {
            text = i == 0 ? lines.get(i) : text.copy().append("\n").append(lines.get(i));
        }
        return click && part.type() == Relays.Kind.GATE ? text.copy().append("\n").append(Component.translatable("residual.terminal.jump.help").withColor(Ui.TEAL)) : text;
    }

    static Component dimensionName(ResourceKey<Level> dimension) {
        String path = dimension.identifier().getPath();
        return Component.translatableWithFallback("residual.map.dimension." + path, path.replace('_', ' '));
    }

    /** The gates of the network in focus, by name. */
    List<RelaySync.Part> gates() {
        List<RelaySync.Part> out = new ArrayList<>();
        for (RelaySync.Part p : RelayView.parts) {
            if (p.net() == focus.getAsInt() && p.type() == Relays.Kind.GATE) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparing(RelayMap::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }
}
