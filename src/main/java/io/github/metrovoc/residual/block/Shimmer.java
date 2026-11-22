package io.github.metrovoc.residual.block;

import io.github.metrovoc.residual.net.ShimmerSync;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** What is being run ahead in this half second, and how fast, for the shimmer players see. */
public final class Shimmer {
    private static final Map<ServerLevel, Map<Long, Integer>> SHINING = new WeakHashMap<>();
    private static final int EVERY = 10;

    private Shimmer() {
    }

    /** The block at {@code p} is being run ahead {@code pace} times as fast. */
    public static void at(ServerLevel level, BlockPos p, int pace) {
        SHINING.computeIfAbsent(level, l -> new HashMap<>()).merge(p.asLong(), pace, Math::max);
    }

    public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % EVERY != 0) {
            return;
        }
        Map<Long, Integer> shining = SHINING.get(level);
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
}
