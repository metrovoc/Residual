package io.github.metrovoc.residual.storage;

import com.mojang.serialization.Codec;
import io.github.metrovoc.residual.Residual;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Every crafting job still running, kept with the world. */
public final class CraftingData extends SavedData {
    private static final Codec<CraftingData> CODEC = CraftJob.CODEC.listOf().xmap(CraftingData::new, d -> d.jobs);
    public static final SavedDataType<CraftingData> TYPE = new SavedDataType<>(Residual.id("crafting"), CraftingData::new, CODEC);

    private final List<CraftJob> jobs = new ArrayList<>();

    private CraftingData() {
    }

    private CraftingData(List<CraftJob> saved) {
        jobs.addAll(saved);
    }

    public static CraftingData of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<CraftJob> jobs() {
        return Collections.unmodifiableList(jobs);
    }

    void add(CraftJob job) {
        jobs.add(job);
        setDirty();
    }

    void remove(CraftJob job) {
        jobs.remove(job);
        setDirty();
    }

    void changed() {
        setDirty();
    }

    /** The jobs the Assembler at {@code pos} owes crafts to, oldest first. */
    List<CraftJob> owedBy(ServerLevel level, BlockPos pos) {
        List<CraftJob> out = new ArrayList<>();
        for (CraftJob job : jobs) {
            CraftJob.Task task = job.tasks.get(pos);
            if (task != null && task.left > 0 && job.dimension.equals(level.dimension())) {
                out.add(job);
            }
        }
        return out;
    }

    /** The jobs running on a network: asked from one of its terminals, or owed by one of its Assemblers. */
    public List<CraftJob> on(ServerLevel level, StorageNetwork net) {
        List<CraftJob> out = new ArrayList<>();
        for (CraftJob job : jobs) {
            if (job.dimension.equals(level.dimension())
                    && (net.nodes.contains(job.anchor) || job.tasks.keySet().stream().anyMatch(net.nodes::contains))) {
                out.add(job);
            }
        }
        return out;
    }
}
