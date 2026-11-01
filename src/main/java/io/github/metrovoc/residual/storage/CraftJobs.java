package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.net.CraftPlanSync;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/** Planning, starting, running, finishing and cancelling the crafting jobs a network's Assemblers and Processors work on. */
public final class CraftJobs {
    /** The most a player may ask for at once. */
    public static final long MOST = 1_000_000_000L;

    /** A plan, and which Assemblers and Processors make each of its recipes. */
    public record Planned(CraftPlan<ItemResource> plan, Map<CraftPlan.Recipe<ItemResource>, List<BlockPos>> where) {
    }

    private CraftJobs() {
    }

    /**
     * The recipes a network's Assemblers draw and its Processors' patterns say, and where; those with the same recipe share
     * its crafts. An Assembler comes first when both make a thing: it needs no machine and is done at once.
     */
    public static Map<CraftPlan.Recipe<ItemResource>, List<BlockPos>> recipes(StorageNetwork net) {
        Map<CraftPlan.Recipe<ItemResource>, List<BlockPos>> out = new LinkedHashMap<>();
        for (AssemblerBlockEntity a : net.assemblers()) {
            CraftPlan.Recipe<ItemResource> r = a.planned();
            if (r != null) {
                out.computeIfAbsent(r, k -> new ArrayList<>()).add(a.getBlockPos());
            }
        }
        for (ProcessorBlockEntity p : net.processors()) {
            CraftPlan.Recipe<ItemResource> r = p.planned();
            if (r != null) {
                out.computeIfAbsent(r, k -> new ArrayList<>()).add(p.getBlockPos());
            }
        }
        return out;
    }

    /** What a network can make. */
    public static Set<ItemResource> craftable(StorageNetwork net) {
        Set<ItemResource> out = new LinkedHashSet<>();
        recipes(net).keySet().forEach(r -> out.add(r.output()));
        return out;
    }

    /**
     * Plans {@code amount} of {@code target} on a copy of what the network keeps, off the server thread, and hands the
     * plan back on it.
     */
    public static void plan(ServerLevel level, StorageNetwork net, ItemResource target, long amount, Consumer<Planned> then) {
        Map<ItemResource, Long> stock = new HashMap<>(net.contents());
        Map<CraftPlan.Recipe<ItemResource>, List<BlockPos>> where = recipes(net);
        Map<ItemResource, List<CraftPlan.Recipe<ItemResource>>> byOutput = new HashMap<>();
        where.keySet().forEach(r -> byOutput.computeIfAbsent(r.output(), k -> new ArrayList<>()).add(r));
        long n = Math.clamp(amount, 1, MOST);
        CompletableFuture.supplyAsync(() -> CraftPlan.of(target, n, stock, byOutput), Util.backgroundExecutor())
                .thenAcceptAsync(plan -> then.accept(new Planned(plan, where)), level.getServer());
    }

    /**
     * Starts a job from a plan, each recipe's crafts shared among the Assemblers or Processors that make it.
     * {@code requester} hears when it is done: a player, or nobody for a job a Port or an agent asked for.
     */
    public static CraftJob start(ServerLevel level, UUID requester, BlockPos anchor, Planned planned) {
        Map<BlockPos, CraftJob.Task> tasks = new LinkedHashMap<>();
        planned.plan().crafts.forEach((recipe, crafts) -> {
            List<BlockPos> at = planned.where().get(recipe);
            long share = crafts / at.size(), extra = crafts % at.size();
            for (int i = 0; i < at.size(); i++) {
                long n = share + (i < extra ? 1 : 0);
                if (n > 0) {
                    tasks.put(at.get(i), new CraftJob.Task(recipe.output(), n));
                }
            }
        });
        CraftPlan.Step<ItemResource> root = planned.plan().root;
        CraftJob job = new CraftJob(UUID.randomUUID(), requester, level.dimension(), anchor, root.kind, root.needed,
                tasks, planned.plan().totalCrafts(), CraftPlanSync.rows(planned.plan()));
        CraftingData.of(level.getServer()).add(job);
        return job;
    }

    /**
     * Crafts for the jobs the Assembler owes crafts to, up to {@code budget} crafts; returns the budget left. Without
     * a network it only marks those jobs as waiting for a recipe it no longer draws.
     */
    static int work(ServerLevel level, AssemblerBlockEntity assembler, @Nullable StorageNetwork net, int budget) {
        CraftingData data = CraftingData.of(level.getServer());
        BlockPos pos = assembler.getBlockPos();
        for (CraftJob job : data.owedBy(level, pos)) {
            if (budget == 0) {
                break;
            }
            CraftJob.Task task = job.tasks.get(pos);
            CraftPlan.Recipe<ItemResource> recipe = assembler.planned();
            job.redrawn = recipe == null || !recipe.output().equals(task.makes);
            if (job.redrawn || net == null) {
                continue;
            }
            long made = assembler.craft(level, net, Math.min(budget, task.left), job);
            if (made > 0) {
                task.left -= made;
                job.done += made;
                budget -= (int) made;
                data.changed();
                if (job.finished()) {
                    finish(level, job, net, data);
                }
            }
        }
        return budget;
    }

    /** A job is done: what it holds goes into the network, and whoever asked for it hears so. */
    static void finish(ServerLevel level, CraftJob job, StorageNetwork net, CraftingData data) {
        deliver(level, job, net);
        data.remove(job);
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(job.requester);
        if (player != null) {
            player.sendSystemMessage(Component.translatable("residual.craft.done", job.amount, job.target.toStack().getHoverName()));
            Awards.grant(player, "storage/request");
        }
    }

    /** Puts everything a job holds into the network, dropping what has no room by the terminal it was asked from. */
    private static void deliver(ServerLevel level, CraftJob job, StorageNetwork net) {
        for (Object2LongMap.Entry<ItemResource> e : job.held.object2LongEntrySet()) {
            long left = e.getLongValue() - net.insert(e.getKey(), e.getLongValue(), false);
            while (left > 0) {
                int n = (int) Math.min(left, e.getKey().getMaxStackSize());
                Block.popResource(level, job.anchor.above(), e.getKey().toStack(n));
                left -= n;
            }
        }
        job.held.clear();
    }

    /** Stops a job running on the network, putting back what it has made so far. */
    public static void cancel(ServerLevel level, StorageNetwork net, UUID id) {
        CraftingData data = CraftingData.of(level.getServer());
        for (CraftJob job : data.on(level, net)) {
            if (job.id.equals(id)) {
                deliver(level, job, net);
                data.remove(job);
                return;
            }
        }
    }
}
