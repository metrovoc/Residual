package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.metrovoc.residual.block.ArenaBlock;
import io.github.metrovoc.residual.block.ArenaBlockEntity;
import io.github.metrovoc.residual.block.ArenaRoom;
import io.github.metrovoc.residual.learn.Imitation;
import io.github.metrovoc.residual.learn.Survey;
import io.github.metrovoc.residual.registry.ModEntities;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What an Arena's chamber shows: a lit pad on its floor for each fight it can run, and on each a small likeness of the mob and
 * a sparring frame, moving between looks as the model said. They are drawn the way a spawner draws what it holds, through the
 * entity renderers' own states, as holograms the eye can see through.
 */
public class ArenaRenderer implements BlockEntityRenderer<ArenaBlockEntity, ArenaRenderer.State> {
    /** How big the fights are drawn: the figures at most this scale and this tall, the room between them this many times what the bout says. */
    private static final float FIGURE = 0.5f, TALLEST = 1.15f, SPREAD = 0.22f, NEAREST = 0.7f;
    private static final int FULL_BRIGHT = 0xF000F0;
    /** The colour of the likeness, and the paler one of the frame it spars with. */
    private static final int LIKENESS = 0xC84CE8D8, SPARRING = 0x90C8E0E8;
    private static final int PAD_IDLE = 0x143CD6C6, PAD_LIVE = 0x4C3CD6C6, PAD_FLOOR = 0x0C3CD6C6;
    private static final float EDGE = 0.04f, RISE = 0.02f;
    /** Ticks a fallen mob lies, and a fight not heard of stands, before it goes. */
    private static final int FALLEN = 24, FORGOTTEN = 100;
    /** How the rows of ground are drawn over a pad: a cube's width, and the colour of each letter. */
    private static final float CUBE = 0.14f;
    /** The trace from the Arena to a pad its answers run along: its width, and the bright stretch that runs along it each look. */
    private static final float TRACE = 0.05f, PULSE = 0.5f;
    private static final int TRACE_IDLE = 0x103CD6C6, TRACE_PULSE = 0xB03CD6C6;
    /** The faint sheet the rows of ground stand on. */
    private static final int WALL = 0x183CD6C6;
    private final EntityRenderDispatcher entities;
    @Nullable
    private Mob frame;

    public static class State extends BlockEntityRenderState {
        final List<Figure> figures = new ArrayList<>();
        final List<float[]> pads = new ArrayList<>();
        final List<float[]> cubes = new ArrayList<>();
        final List<float[]> walls = new ArrayList<>();
        /** Where the Arena's face is, along the floor, from the block. */
        float fromX, fromZ;
    }

    /** One thing to draw, at this place from the block, this big. */
    private record Figure(EntityRenderState state, float x, float y, float z, float scale, int tint) {
    }

    public ArenaRenderer(BlockEntityRendererProvider.Context context) {
        entities = context.entityRenderer();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ArenaBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        state.figures.clear();
        state.pads.clear();
        state.cubes.clear();
        state.walls.clear();
        ArenaRoom room = be.room();
        if (room == null || be.getLevel() == null) {
            return;
        }
        long tick = be.getLevel().getGameTime();
        float now = tick + partialTicks;
        Map<Integer, ArenaBlockEntity.View> views = be.views();
        views.values().removeIf(v -> now - v.at > (v.over ? FALLEN : FORGOTTEN));
        Vec3 origin = Vec3.atLowerCornerOf(be.getBlockPos());
        Direction facing = be.getBlockState().getValue(ArenaBlock.FACING);
        state.fromX = 0.5f + facing.getStepX() * 0.5f;
        state.fromZ = 0.5f + facing.getStepZ() * 0.5f;
        Map<Integer, Deque<String>> ground = be.rows(tick);
        for (int i = 0; i < room.pads(); i++) {
            Vec3 pad = room.pad(i).subtract(origin);
            ArenaBlockEntity.View v = views.get(i);
            boolean live = v != null || ground.containsKey(i);
            // how far this look's answer has run along the trace, or -1 with nothing running
            float ran = v != null && !v.over ? Math.clamp((now - v.at) / Imitation.LOOK, 0f, 1f) : live ? (now % Imitation.LOOK) / Imitation.LOOK : -1;
            state.pads.add(new float[] {(float) pad.x, (float) pad.y, (float) pad.z, live ? 1 : 0, ran});
            if (v != null) {
                fight(be, state, v, pad, now, partialTicks);
            }
            Deque<String> rows = ground.get(i);
            if (rows != null) {
                rows(state, rows, pad);
            }
        }
    }

    private void fight(ArenaBlockEntity be, State state, ArenaBlockEntity.View v, Vec3 pad, float now, float partialTicks) {
        Mob mob = be.likeness();
        Mob frame = frame(be);
        if (mob == null || frame == null) {
            return;
        }
        float t = Math.clamp((now - v.at) / Imitation.LOOK, 0f, 1f);
        float distance = v.distanceBefore + (v.distance - v.distanceBefore) * t;
        double angle = 2 * Math.PI * (v.turnBefore + (v.turn - v.turnBefore) * t);
        double dx = Math.sin(angle), dz = Math.cos(angle);
        float apart = Math.max(NEAREST, distance * SPREAD);
        float walked = v.walkedBefore + (v.walked - v.walkedBefore) * t;
        boolean moved = v.distance != v.distanceBefore || v.turn != v.turnBefore;
        char action = Imitation.ACTIONS.charAt(Math.clamp(v.action, 0, Imitation.ACTIONS.length() - 1));
        float swing = (float) Math.sin(Math.PI * t);

        // the mob: toward the frame, unless it is running from it
        double mx = dx * apart / 2, mz = dz * apart / 2;
        float mobYaw = yaw(action == Imitation.AWAY ? mx : -mx, action == Imitation.AWAY ? mz : -mz);
        float lunge = action == Imitation.STRIKE ? 0.18f * swing : 0;
        float hop = action == Imitation.LEAP ? 0.35f * swing : 0;
        double tremble = action == Imitation.READY ? 0.015 * Math.sin(now * 3) : 0;
        LivingEntityRenderState mobState = living(mob, partialTicks, mobYaw, walked, moved, now);
        if (mobState != null) {
            if (mobState instanceof ArmedEntityRenderState armed && action == Imitation.STRIKE) {
                armed.attackTime = t;
            }
            if (mobState instanceof ZombieRenderState zombie) {
                zombie.isAggressive = true;
            }
            if (mobState instanceof SkeletonRenderState skeleton) {
                skeleton.isAggressive = action == Imitation.READY;
            }
            if (mobState instanceof CreeperRenderState creeper && action == Imitation.READY) {
                creeper.swelling = t;
            }
            mobState.hasRedOverlay = v.hurt && t < 0.5f;
            float scale = Math.min(FIGURE, TALLEST / Math.max(mob.getBbHeight(), mob.getBbWidth()));
            if (v.over && v.paid) {
                mobState.deathTime = Math.max(0.01f, (now - v.at) * 1.2f);
                mobState.hasRedOverlay = true;
            } else if (v.over) {
                // it was no good: it goes out rather than falling
                scale *= Math.max(0, 1 - (now - v.at) / 10);
            }
            if (scale > 0) {
                state.figures.add(new Figure(mobState, (float) (pad.x + mx - dx * lunge + tremble), (float) pad.y + RISE + hop, (float) (pad.z + mz - dz * lunge), scale, LIKENESS));
            }
        }

        // the sparring frame, always facing the mob
        LivingEntityRenderState frameState = living(frame, partialTicks, yaw(mx, mz), walked * 0.7f, v.distance < v.distanceBefore, now);
        if (frameState != null) {
            if (frameState instanceof AgentRenderState agent && v.struck) {
                agent.swing = t;
            }
            float strike = v.struck ? 0.15f * swing : 0;
            state.figures.add(new Figure(frameState, (float) (pad.x - dx * apart / 2 + dx * strike), (float) pad.y + RISE, (float) (pad.z - dz * apart / 2 + dz * strike), FIGURE, SPARRING));
        }
    }

    /** The degrees a body turns to look along (x, z), as an entity's yaw counts them. */
    private static float yaw(double x, double z) {
        return (float) Math.toDegrees(Math.atan2(-x, z));
    }

    /** A figure's state, taken from its entity as a spawner takes one and set to where the look has it. */
    @Nullable
    private LivingEntityRenderState living(Mob mob, float partialTicks, float yaw, float walked, boolean moving, float now) {
        if (!(entities.extractEntity(mob, partialTicks) instanceof LivingEntityRenderState s)) {
            return null;
        }
        s.bodyRot = yaw;
        s.yRot = 0;
        s.xRot = 0;
        s.walkAnimationPos = walked * 2.5f;
        s.walkAnimationSpeed = moving ? 0.8f : 0;
        s.ageInTicks = now;
        s.deathTime = 0;
        s.hasRedOverlay = false;
        s.lightCoords = FULL_BRIGHT;
        s.nameTag = null;
        s.scoreText = null;
        s.shadowPieces.clear();
        s.leashStates = null;
        s.displayFireAnimation = false;
        // drawn as an invisible mob is to those who may see it: see-through, in the colour Tinted gives it
        s.isInvisible = true;
        s.isInvisibleToPlayer = false;
        s.outlineColor = 0;
        return s;
    }

    @Nullable
    private Mob frame(ArenaBlockEntity be) {
        if (frame == null || frame.level() != be.getLevel()) {
            frame = ModEntities.AGENT.get().create(be.getLevel(), EntitySpawnReason.LOAD);
        }
        return frame;
    }

    /** A pad's newest rows of ground, standing over it as a wall of small cubes, the ores lit. */
    private static void rows(State state, Deque<String> rows, Vec3 pad) {
        state.walls.add(new float[] {(float) pad.x, (float) pad.y, (float) pad.z});
        int r = 0;
        Iterator<String> newest = rows.descendingIterator();
        while (newest.hasNext()) {
            String row = newest.next();
            for (int c = 0; c < row.length(); c++) {
                int color = color(row.charAt(c));
                if (color != 0) {
                    float x = (float) pad.x + (c - row.length() / 2f) * CUBE;
                    float y = (float) pad.y + 0.15f + r * CUBE;
                    state.cubes.add(new float[] {x, y, (float) pad.z, color});
                }
            }
            r++;
        }
    }

    /** What a letter of ground looks like over a pad, or 0 for what shows as nothing. */
    private static int color(char letter) {
        return switch (letter) {
            case 'C' -> 0xFF4A4A4A;
            case 'I' -> 0xFFD8AF93;
            case 'U' -> 0xFFE07A4A;
            case 'O' -> 0xFFFCDB3A;
            case 'R' -> 0xFFFF4A4A;
            case 'Z' -> 0xFF3A6AE8;
            case 'X' -> 0xFF4AE8E0;
            case 'E' -> 0xFF3AE86A;
            default -> 0;
        };
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.pads.isEmpty()) {
            return;
        }
        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
            for (float[] p : state.pads) {
                pad(pose, buffer, p[0], p[1], p[2], p[3] > 0);
                trace(pose, buffer, state, p[0], p[1], p[2], p[4]);
            }
            for (float[] w : state.walls) {
                float half = Survey.ROW * CUBE / 2;
                Waves.box(pose, buffer, new AABB(w[0] - half, w[1] + 0.15f, w[2] - 0.01f, w[0] + half, w[1] + 0.15f + 8 * CUBE, w[2] + 0.01f), WALL);
            }
            for (float[] c : state.cubes) {
                Waves.box(pose, buffer, new AABB(c[0], c[1], c[2] - CUBE / 2, c[0] + CUBE * 0.9, c[1] + CUBE * 0.9, c[2] + CUBE / 2), (int) c[3]);
            }
        });
        for (Figure f : state.figures) {
            poseStack.pushPose();
            poseStack.translate(f.x, f.y, f.z);
            poseStack.scale(f.scale, f.scale, f.scale);
            entities.submit(f.state, camera, 0, 0, 0, poseStack, new Tinted(collector, f.tint));
            poseStack.popPose();
        }
    }

    /** A pad on the floor, its middle at (x, y, z): a square of light, brighter while it holds a fight. */
    private static void pad(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, boolean live) {
        float r = ArenaRoom.PAD / 2f - 0.1f, lift = RISE / 2;
        int edge = live ? PAD_LIVE : PAD_IDLE;
        Waves.box(pose, buffer, new AABB(x - r, y, z - r, x + r, y + lift, z + r), PAD_FLOOR);
        Waves.box(pose, buffer, new AABB(x - r, y, z - r, x + r, y + lift * 2, z - r + EDGE), edge);
        Waves.box(pose, buffer, new AABB(x - r, y, z + r - EDGE, x + r, y + lift * 2, z + r), edge);
        Waves.box(pose, buffer, new AABB(x - r, y, z - r, x - r + EDGE, y + lift * 2, z + r), edge);
        Waves.box(pose, buffer, new AABB(x + r - EDGE, y, z - r, x + r, y + lift * 2, z + r), edge);
    }

    /**
     * The way along the floor from the Arena to a pad, first straight out and then across, faint; and running along it, each look, the
     * model's answer on its way.
     */
    private static void trace(PoseStack.Pose pose, VertexConsumer buffer, State state, float x, float y, float z, float ran) {
        float y0 = y + RISE / 4, y1 = y + RISE;
        float x0 = state.fromX, z0 = state.fromZ;
        boolean outAlongZ = Math.abs(z0 - 0.5f) > 0;
        // the corner: it leaves the face straight, then turns toward the pad
        float cx = outAlongZ ? x0 : x, cz = outAlongZ ? z : z0;
        segment(pose, buffer, x0, y0, z0, cx, y1, cz, TRACE_IDLE);
        segment(pose, buffer, cx, y0, cz, x, y1, z, TRACE_IDLE);
        if (ran < 0) {
            return;
        }
        float first = Math.abs(cx - x0) + Math.abs(cz - z0), second = Math.abs(x - cx) + Math.abs(z - cz), whole = first + second;
        if (whole <= 0) {
            return;
        }
        float head = ran * (whole + PULSE), tail = head - PULSE;
        pulse(pose, buffer, x0, y0, z0, cx, y1, cz, Math.max(0, tail) / first, Math.min(first, head) / first);
        if (head > first) {
            pulse(pose, buffer, cx, y0, cz, x, y1, z, Math.max(0, tail - first) / second, Math.min(second, head - first) / second);
        }
    }

    private static void pulse(PoseStack.Pose pose, VertexConsumer buffer, float ax, float y0, float az, float bx, float y1, float bz, float from, float to) {
        if (to > from) {
            segment(pose, buffer, ax + (bx - ax) * from, y0, az + (bz - az) * from, ax + (bx - ax) * to, y1, az + (bz - az) * to, TRACE_PULSE);
        }
    }

    /** A thin strip of light on the floor between two points on one axis. */
    private static void segment(PoseStack.Pose pose, VertexConsumer buffer, float ax, float y0, float az, float bx, float y1, float bz, int argb) {
        float w = TRACE / 2;
        Waves.box(pose, buffer, new AABB(Math.min(ax, bx) - w, y0, Math.min(az, bz) - w, Math.max(ax, bx) + w, y1, Math.max(az, bz) + w), argb);
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public AABB getRenderBoundingBox(ArenaBlockEntity be) {
        ArenaRoom room = be.room();
        return room == null ? new AABB(be.getBlockPos()) : room.box().minmax(new AABB(be.getBlockPos()));
    }
}
