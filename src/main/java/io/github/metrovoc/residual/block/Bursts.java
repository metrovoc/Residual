package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.net.ShimmerSync;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Blocks an Hourglass runs ahead: how many times as fast, and until when. A machine takes that many ticks a tick; a crop
 * that many chances a tick at the random tick it would have had once. Forecasters never write down what a burst ran.
 * Bursts last seconds, so they are not saved.
 */
public final class Bursts {
    private record Burst(int factor, long until) {
    }

    private static final Map<ServerLevel, Map<BlockPos, Burst>> RUNNING = new WeakHashMap<>();
    /** What is being run ahead in this half second, by any means, and how fast, for the shimmer players see. */
    private static final Map<ServerLevel, Map<Long, Integer>> SHIMMER = new WeakHashMap<>();
    private static final int SHIMMER_EVERY = 10;

    private Bursts() {
    }

    /** How fast a burst on {@code pos} would run: twice as fast, or twice what a burst still running there does. */
    public static int next(ServerLevel level, BlockPos pos) {
        Burst b = RUNNING.getOrDefault(level, Map.of()).get(pos);
        return b == null || b.until <= level.getGameTime() ? 2 : b.factor * 2;
    }

    public static void start(ServerLevel level, BlockPos pos, int factor, int ticks) {
        RUNNING.computeIfAbsent(level, l -> new HashMap<>()).put(pos.immutable(), new Burst(factor, level.getGameTime() + ticks));
    }

    /** The block at {@code p} is being run ahead {@code pace} times as fast. */
    public static void shimmer(ServerLevel level, BlockPos p, int pace) {
        SHIMMER.computeIfAbsent(level, l -> new HashMap<>()).merge(p.asLong(), pace, Math::max);
    }

    private static void sendShimmer(ServerLevel level) {
        Map<Long, Integer> shining = SHIMMER.get(level);
        if (shining == null || shining.isEmpty() || level.players().isEmpty()) {
            return;
        }
        long[] places = new long[shining.size()];
        List<Integer> paces = new ArrayList<>(shining.size());
        int i = 0;
        for (Map.Entry<Long, Integer> e : shining.entrySet()) {
            places[i++] = e.getKey();
            paces.add(e.getValue());
        }
        shining.clear();
        PacketDistributor.sendToPlayersInDimension(level, new ShimmerSync(places, paces));
    }

    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() % SHIMMER_EVERY == 0) {
            sendShimmer(level);
        }
        Map<BlockPos, Burst> running = RUNNING.get(level);
        if (running == null || running.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        int randomTicks = level.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
        for (Iterator<Map.Entry<BlockPos, Burst>> it = running.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Burst> e = it.next();
            BlockPos p = e.getKey();
            Burst b = e.getValue();
            if (b.until <= now || !level.isLoaded(p)) {
                it.remove();
                continue;
            }
            BlockEntity be = level.getBlockEntity(p);
            for (int i = 1; i < b.factor; i++) {
                if (be != null) {
                    ForecasterBlockEntity.tickOnce(level, p, be);
                } else if (level.getRandom().nextInt(4096) < randomTicks) {
                    // a section's 4096 blocks share its random ticks: this is one block's chance of one
                    BlockState s = level.getBlockState(p);
                    if (s.isRandomlyTicking()) {
                        s.randomTick(level, p, level.getRandom());
                    }
                }
            }
            ForecasterBlockEntity.hurried(level, p);
            shimmer(level, p, b.factor);
            if (now % 4 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5, 2, 0.3, 0.05, 0.3, 0);
            }
        }
    }
}
