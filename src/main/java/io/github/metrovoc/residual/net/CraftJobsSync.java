package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.storage.CraftJob;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** The crafting jobs running on a Storage Terminal's network. */
public record CraftJobsSync(int menu, List<Job> jobs) implements CustomPacketPayload {
    /**
     * A job: what it makes, its crafts done of all, and what it waits for that it cannot make itself ({@code lacking}
     * empty if nothing); {@code redrawn} says an Assembler it counts on was given another recipe.
     */
    public record Job(UUID id, ItemResource target, long amount, long done, long total, ItemStack lacking, long lackingCount,
                      boolean redrawn) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Job> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Job::id,
                ItemResource.STREAM_CODEC, Job::target,
                ByteBufCodecs.VAR_LONG, Job::amount,
                ByteBufCodecs.VAR_LONG, Job::done,
                ByteBufCodecs.VAR_LONG, Job::total,
                ItemStack.OPTIONAL_STREAM_CODEC, Job::lacking,
                ByteBufCodecs.VAR_LONG, Job::lackingCount,
                ByteBufCodecs.BOOL, Job::redrawn,
                Job::new);

        public static Job of(CraftJob job) {
            ItemResource lacking = job.lacking();
            return new Job(job.id, job.target, job.amount, job.done(), job.total, lacking == null ? ItemStack.EMPTY : lacking.toStack(),
                    job.lackingCount(), job.redrawn());
        }

        /** Whether two views of a job show the same thing; item stacks have no equality of their own. */
        boolean same(Job o) {
            return id.equals(o.id) && done == o.done && total == o.total && lackingCount == o.lackingCount && redrawn == o.redrawn
                    && ItemStack.matches(lacking, o.lacking);
        }
    }

    public static final Type<CraftJobsSync> TYPE = new Type<>(Residual.id("craft_jobs"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftJobsSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CraftJobsSync::menu,
            Job.STREAM_CODEC.apply(ByteBufCodecs.list()), CraftJobsSync::jobs,
            CraftJobsSync::new);

    /** Whether two lists show the same jobs in the same state. */
    public static boolean same(List<Job> a, List<Job> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).same(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Type<CraftJobsSync> type() {
        return TYPE;
    }
}
