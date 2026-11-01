package io.github.metrovoc.residual.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.net.CraftPlanSync;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * A crafting job a player asked a network for: what it makes, how many crafts each Assembler or Processor owes it, and
 * what it has made so far. What it makes is held apart from the network until the job is done, so nothing else takes it in
 * between; what it lacks, it takes from the network as it arrives.
 */
public final class CraftJob {
    /**
     * Crafts an Assembler or Processor owes the job, and what they make. A Processor's are rounds in a machine: {@code sent}
     * of those it owes are in it now, and {@code back} items have come back of the next one to finish.
     */
    public static final class Task {
        public final ItemResource makes;
        long left, sent, back;

        Task(ItemResource makes, long left) {
            this(makes, left, 0, 0);
        }

        Task(ItemResource makes, long left, long sent, long back) {
            this.makes = makes;
            this.left = left;
            this.sent = sent;
            this.back = back;
        }
    }

    private record SavedTask(BlockPos at, ItemResource makes, long left, long sent, long back) {
        static final Codec<SavedTask> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("at").forGetter(SavedTask::at),
                ItemResource.CODEC.fieldOf("makes").forGetter(SavedTask::makes),
                Codec.LONG.fieldOf("left").forGetter(SavedTask::left),
                Codec.LONG.optionalFieldOf("sent", 0L).forGetter(SavedTask::sent),
                Codec.LONG.optionalFieldOf("back", 0L).forGetter(SavedTask::back)
        ).apply(i, SavedTask::new));
    }

    private record Held(ItemResource item, long count) {
        static final Codec<Held> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemResource.CODEC.fieldOf("item").forGetter(Held::item),
                Codec.LONG.fieldOf("count").forGetter(Held::count)
        ).apply(i, Held::new));
    }

    public static final Codec<CraftJob> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(j -> j.id),
            UUIDUtil.CODEC.fieldOf("requester").forGetter(j -> j.requester),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(j -> j.dimension),
            BlockPos.CODEC.fieldOf("anchor").forGetter(j -> j.anchor),
            ItemResource.CODEC.fieldOf("target").forGetter(j -> j.target),
            Codec.LONG.fieldOf("amount").forGetter(j -> j.amount),
            SavedTask.CODEC.listOf().fieldOf("tasks").forGetter(CraftJob::savedTasks),
            Held.CODEC.listOf().fieldOf("held").forGetter(CraftJob::savedHeld),
            Codec.LONG.fieldOf("total").forGetter(j -> j.total),
            Codec.LONG.fieldOf("done").forGetter(j -> j.done),
            CraftPlanSync.Row.CODEC.listOf().optionalFieldOf("plan", List.of()).forGetter(j -> j.plan)
    ).apply(i, CraftJob::new));

    public final UUID id, requester;
    public final ResourceKey<Level> dimension;
    /** The terminal it was asked from. */
    public final BlockPos anchor;
    public final ItemResource target;
    public final long amount, total;
    long done;
    final Map<BlockPos, Task> tasks = new LinkedHashMap<>();
    /** The plan it started from, drawn as a tree. */
    public final List<CraftPlanSync.Row> plan;
    final Object2LongLinkedOpenHashMap<ItemResource> held = new Object2LongLinkedOpenHashMap<>();
    /** What it last had to wait for, and how many; not saved, it is found again at the next craft. */
    @Nullable
    ItemResource waitingFor;
    long waitingCount;
    /** Whether an Assembler it owes crafts to was given another recipe since it started. */
    boolean redrawn;

    CraftJob(UUID id, UUID requester, ResourceKey<Level> dimension, BlockPos anchor, ItemResource target, long amount,
             Map<BlockPos, Task> tasks, long total, List<CraftPlanSync.Row> plan) {
        this.id = id;
        this.requester = requester;
        this.dimension = dimension;
        this.anchor = anchor.immutable();
        this.target = target;
        this.amount = amount;
        this.total = total;
        this.plan = List.copyOf(plan);
        this.tasks.putAll(tasks);
    }

    private CraftJob(UUID id, UUID requester, ResourceKey<Level> dimension, BlockPos anchor, ItemResource target,
                     long amount, List<SavedTask> tasks, List<Held> held, long total, long done, List<CraftPlanSync.Row> plan) {
        this(id, requester, dimension, anchor, target, amount, Map.of(), total, plan);
        for (SavedTask t : tasks) {
            this.tasks.put(t.at(), new Task(t.makes(), t.left(), t.sent(), t.back()));
        }
        for (Held h : held) {
            this.held.addTo(h.item(), h.count());
        }
        this.done = done;
    }

    private List<SavedTask> savedTasks() {
        List<SavedTask> out = new ArrayList<>();
        tasks.forEach((at, t) -> out.add(new SavedTask(at, t.makes, t.left, t.sent, t.back)));
        return out;
    }

    private List<Held> savedHeld() {
        List<Held> out = new ArrayList<>();
        for (Object2LongMap.Entry<ItemResource> e : held.object2LongEntrySet()) {
            out.add(new Held(e.getKey(), e.getLongValue()));
        }
        return out;
    }

    public long done() {
        return done;
    }

    /**
     * How many crafts each row of the plan has made. Crafts of a kind are shared out to its rows from the last one on, as
     * the deepest are made first.
     */
    public List<Long> rowsDone() {
        Map<ItemResource, Long> left = new HashMap<>(), planned = new HashMap<>();
        tasks.values().forEach(t -> left.merge(t.makes, t.left, Long::sum));
        plan.forEach(r -> planned.merge(r.kind(), r.crafts(), Long::sum));
        Map<ItemResource, Long> made = new HashMap<>();
        planned.forEach((kind, n) -> made.put(kind, Math.max(0, n - left.getOrDefault(kind, 0L))));
        Long[] out = new Long[plan.size()];
        for (int i = plan.size() - 1; i >= 0; i--) {
            CraftPlanSync.Row row = plan.get(i);
            long n = Math.min(row.crafts(), made.getOrDefault(row.kind(), 0L));
            made.merge(row.kind(), -n, Long::sum);
            out[i] = n;
        }
        return List.of(out);
    }

    public boolean finished() {
        return tasks.values().stream().allMatch(t -> t.left == 0);
    }

    /** Whether one of its own crafts still makes {@code kind}: waiting for it is only waiting for itself. */
    boolean makes(ItemResource kind) {
        return tasks.values().stream().anyMatch(t -> t.left > 0 && t.makes.equals(kind));
    }

    /** Takes up to {@code n} of a kind from what it holds; returns how many it had. */
    long take(ItemResource kind, long n) {
        long took = Math.min(n, held.getLong(kind));
        if (took > 0) {
            long left = held.addTo(kind, -took) - took;
            if (left == 0) {
                held.removeLong(kind);
            }
        }
        return took;
    }

    void hold(ItemResource kind, long n) {
        held.addTo(kind, n);
    }

    /** The kind it waits for that none of its own crafts will make, or null if it is only waiting for itself. */
    @Nullable
    public ItemResource lacking() {
        return waitingFor != null && !makes(waitingFor) ? waitingFor : null;
    }

    public long lackingCount() {
        return lacking() == null ? 0 : waitingCount;
    }

    public boolean redrawn() {
        return redrawn;
    }
}
