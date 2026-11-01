package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * A Processor at work. Its pattern says what a machine takes and what it makes, in kinds and amounts, drawn with ghost
 * items: up to 81 of each. A round pushes all of what it takes into the machine at once, or none of it, and what the machine
 * makes comes back as it appears, to the job that asked or else to the network. It reaches the machine with no side in
 * particular, so a furnace takes ore and coal and gives ingots through the one Processor, whichever face it stands on.
 * Processors with the same pattern share a job's rounds, each on its own machine.
 */
public class ProcessorBlockEntity extends BlockEntity implements MenuProvider {
    /** A page of the pattern's grids, and how many pages each has: 81 cells. */
    public static final int PAGE = 9, PAGES = 9, CELLS = PAGE * PAGES;
    /** Items it moves a tick, each way, as a Port does: 1,280 a second. */
    public static final int RATE = 64;
    public static final int MOST = 1 << 20;
    /** Container data: how many to keep, how many the network holds, rounds done in the last second, and why it waits. */
    public static final int KEEP = 0, HELD = 1, MADE = 2, WAITING = 3, DATA = 4;
    /** Why it pushes nothing: it is pushing, it has no pattern or no machine, the network has enough, something is missing,
     * the machine has no room, nothing is asked of it, or its screen is open. */
    public static final int WORKING = 0, NO_PATTERN = 1, NO_MACHINE = 2, ENOUGH = 3, MISSING = 4, FULL = 5, PAUSED = 6;

    private final SimpleContainer inputs = pattern(), outputs = pattern();
    private int keep;
    private int held;
    /** Rounds it pushed to keep the network's stock, and what has come back of the next one. */
    private long keepSent, keepBack;
    private int madeThisSecond, madeLastSecond;
    private int waiting = NO_PATTERN;
    @Nullable
    private CraftPlan.Recipe<ItemResource> planned;
    private boolean looked;
    /** Players with its screen open. */
    int viewers;
    @Nullable
    private BlockCapabilityCache<ResourceHandler<ItemResource>, Direction> machine;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case KEEP -> keep;
                case HELD -> held;
                case MADE -> madeLastSecond;
                case WAITING -> waiting;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return DATA;
        }
    };

    public ProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PROCESSOR.get(), pos, state);
    }

    private SimpleContainer pattern() {
        return new SimpleContainer(CELLS) {
            @Override
            public void setChanged() {
                super.setChanged();
                ProcessorBlockEntity.this.setChanged();
                looked = false;
            }
        };
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ProcessorBlockEntity be) {
        be.tick((ServerLevel) level, state);
    }

    /** Kinds and amounts in a grid's order, a kind drawn twice counted together. */
    private static Map<ItemResource, Integer> kinds(SimpleContainer grid) {
        Map<ItemResource, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < CELLS; i++) {
            ItemStack cell = grid.getItem(i);
            if (!cell.isEmpty()) {
                out.merge(ItemResource.of(cell), cell.getCount(), Integer::sum);
            }
        }
        return out;
    }

    /** Reads its pattern: the first kind it makes is what the network can ask it for, and the rest come along with it. */
    private void look() {
        looked = true;
        Map<ItemResource, Integer> in = kinds(inputs), out = kinds(outputs);
        planned = null;
        if (!in.isEmpty() && !out.isEmpty()) {
            ItemResource first = out.keySet().iterator().next();
            int made = out.remove(first);
            planned = new CraftPlan.Recipe<>(first, made, in, out);
        }
    }

    /** What it makes and from what, as a network's planner sees it; null without a whole pattern. */
    @Nullable
    public CraftPlan.Recipe<ItemResource> planned() {
        if (!looked) {
            look();
        }
        return planned;
    }

    private Direction front() {
        return getBlockState().getValue(ProcessorBlock.FACING);
    }

    /** The machine in front of it, with no side in particular, or null. */
    @Nullable
    private ResourceHandler<ItemResource> machine(ServerLevel level) {
        BlockPos at = worldPosition.relative(front());
        if (machine == null || !machine.pos().equals(at)) {
            machine = BlockCapabilityCache.create(Capabilities.Item.BLOCK, level, at, null);
        }
        return machine.getCapability();
    }

    private void tick(ServerLevel level, BlockState state) {
        CraftPlan.Recipe<ItemResource> r = planned();
        if (level.getGameTime() % 20 == 0) {
            madeLastSecond = madeThisSecond;
            madeThisSecond = 0;
            boolean working = madeLastSecond > 0;
            if (working) {
                for (ServerPlayer player : level.players()) {
                    if (player.distanceToSqr(worldPosition.getCenter()) < 32 * 32) {
                        Awards.grant(player, "storage/processor");
                    }
                }
            }
            if (state.getValue(ProcessorBlock.WORKING) != working) {
                level.setBlock(worldPosition, state.setValue(ProcessorBlock.WORKING, working), Block.UPDATE_CLIENTS);
            }
        }
        CraftingData data = CraftingData.of(level.getServer());
        List<CraftJob> jobs = data.owedBy(level, worldPosition);
        for (CraftJob job : jobs) {
            job.redrawn = r == null || !r.output().equals(job.tasks.get(worldPosition).makes);
        }
        if (r == null) {
            waiting = NO_PATTERN;
            return;
        }
        if (viewers > 0) {
            waiting = PAUSED;
            return;
        }
        ResourceHandler<ItemResource> inv = machine(level);
        if (inv == null) {
            waiting = NO_MACHINE;
            return;
        }
        StorageNetwork net = StorageNetwork.at(level, worldPosition);
        held = (int) Math.min(Integer.MAX_VALUE, net.count(r.output()));
        takeBack(level, net, inv, r, jobs, data);
        waiting = held + keepSent * r.made() >= keep ? ENOUGH : WORKING;
        int budget = RATE;
        for (CraftJob job : data.owedBy(level, worldPosition)) {
            CraftJob.Task task = job.tasks.get(worldPosition);
            if (job.redrawn) {
                continue;
            }
            while (budget > 0 && task.left - task.sent > 0) {
                int push = push(net, inv, r, job);
                if (push == 0) {
                    break;
                }
                budget -= push;
                task.sent++;
                data.changed();
                waiting = WORKING;
            }
        }
        // what is left of the tick's items keeps the network's stock
        while (budget > 0 && held + keepSent * r.made() < keep) {
            int push = push(net, inv, r, null);
            if (push == 0) {
                break;
            }
            budget -= push;
            keepSent++;
            setChanged();
        }
    }

    /**
     * Pushes one round into the machine, all of it or none: what it takes comes from the job's holdings first, then from the
     * network. Returns how many items that was, or 0, having said why.
     */
    private int push(StorageNetwork net, ResourceHandler<ItemResource> inv, CraftPlan.Recipe<ItemResource> r, @Nullable CraftJob job) {
        int items = 0;
        for (Map.Entry<ItemResource, Integer> need : r.inputs().entrySet()) {
            long have = (job == null ? 0 : job.held.getLong(need.getKey())) + net.extract(need.getKey(), need.getValue(), true);
            if (have < need.getValue()) {
                waiting = MISSING;
                if (job != null) {
                    CraftJob.Task task = job.tasks.get(worldPosition);
                    job.waitingFor = need.getKey();
                    job.waitingCount = need.getValue() * (task.left - task.sent) - job.held.getLong(need.getKey()) - net.count(need.getKey());
                }
                return 0;
            }
            items += need.getValue();
        }
        try (Transaction tx = Transaction.openRoot()) {
            for (Map.Entry<ItemResource, Integer> need : r.inputs().entrySet()) {
                if (inv.insert(need.getKey(), need.getValue(), tx) < need.getValue()) {
                    waiting = FULL;
                    return 0;
                }
            }
        }
        if (job != null) {
            job.waitingFor = null;
        }
        for (Map.Entry<ItemResource, Integer> need : r.inputs().entrySet()) {
            long fromJob = job == null ? 0 : job.take(need.getKey(), need.getValue());
            net.extract(need.getKey(), need.getValue() - fromJob, false);
        }
        try (Transaction tx = Transaction.openRoot()) {
            for (Map.Entry<ItemResource, Integer> need : r.inputs().entrySet()) {
                inv.insert(need.getKey(), need.getValue(), tx);
            }
            tx.commit();
        }
        return items;
    }

    /** Takes what the machine made back, up to a tick's items. */
    private void takeBack(ServerLevel level, StorageNetwork net, ResourceHandler<ItemResource> inv, CraftPlan.Recipe<ItemResource> r,
                          List<CraftJob> jobs, CraftingData data) {
        int budget = RATE;
        budget -= takeBack(level, net, inv, r, r.output(), true, budget, jobs, data);
        for (ItemResource also : r.leftovers().keySet()) {
            budget -= takeBack(level, net, inv, r, also, false, budget, jobs, data);
        }
    }

    private int takeBack(ServerLevel level, StorageNetwork net, ResourceHandler<ItemResource> inv, CraftPlan.Recipe<ItemResource> r,
                         ItemResource kind, boolean first, int most, List<CraftJob> jobs, CraftingData data) {
        // a kind it also pushes in stays until the machine has moved it where nothing can be put
        boolean alsoIn = r.inputs().containsKey(kind);
        int got = 0;
        try (Transaction tx = Transaction.openRoot()) {
            for (int slot = 0; slot < inv.size() && got < most; slot++) {
                if (inv.getResource(slot).equals(kind) && !(alsoIn && inv.isValid(slot, kind))) {
                    got += inv.extract(slot, kind, most - got, tx);
                }
            }
            tx.commit();
        }
        if (got > 0) {
            give(level, net, r, kind, got, first, jobs, data);
        }
        return got;
    }

    /** What came back goes to the jobs waiting for it, oldest first, then to the network. */
    private void give(ServerLevel level, StorageNetwork net, CraftPlan.Recipe<ItemResource> r, ItemResource kind, int n, boolean first,
                      List<CraftJob> jobs, CraftingData data) {
        long rest = n;
        for (CraftJob job : jobs) {
            CraftJob.Task task = job.tasks.get(worldPosition);
            if (rest == 0 || task.sent == 0 || job.redrawn) {
                continue;
            }
            long take = first ? Math.min(rest, task.sent * r.made() - task.back) : rest;
            job.hold(kind, take);
            rest -= take;
            if (first) {
                task.back += take;
                long rounds = task.back / r.made();
                task.back -= rounds * r.made();
                task.sent -= rounds;
                task.left -= rounds;
                job.done += rounds;
                madeThisSecond += (int) rounds;
                if (job.finished()) {
                    CraftJobs.finish(level, job, net, data);
                }
            }
            data.changed();
        }
        if (rest > 0) {
            long stored = net.insert(kind, rest, false);
            if (stored < rest) {
                Block.popResource(level, worldPosition.above(), kind.toStack((int) Math.min(rest - stored, kind.getMaxStackSize())));
            }
            if (first) {
                keepBack += rest;
                long rounds = keepBack / r.made();
                keepBack -= rounds * r.made();
                keepSent = Math.max(0, keepSent - rounds);
                madeThisSecond += (int) rounds;
                setChanged();
            }
        }
    }

    public void setKeep(int keep) {
        this.keep = Math.clamp(keep, 0, MOST);
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            StorageNetwork.changed(server);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel server) {
            StorageNetwork.changed(server);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.residual.processor");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ProcessorMenu(id, inventory, worldPosition, inputs, outputs, data, this);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out.child("inputs"), inputs.getItems());
        ContainerHelper.saveAllItems(out.child("outputs"), outputs.getItems());
        out.putInt("keep", keep);
        out.putLong("keep_sent", keepSent);
        out.putLong("keep_back", keepBack);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("inputs").ifPresent(c -> ContainerHelper.loadAllItems(c, inputs.getItems()));
        in.child("outputs").ifPresent(c -> ContainerHelper.loadAllItems(c, outputs.getItems()));
        keep = in.getIntOr("keep", 0);
        keepSent = in.getLongOr("keep_sent", 0);
        keepBack = in.getLongOr("keep_back", 0);
        looked = false;
    }
}
