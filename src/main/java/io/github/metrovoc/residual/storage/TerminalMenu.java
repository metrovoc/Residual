package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.net.CraftAsk;
import io.github.metrovoc.residual.net.CraftCancel;
import io.github.metrovoc.residual.net.CraftJobsSync;
import io.github.metrovoc.residual.net.CraftPlanSync;
import io.github.metrovoc.residual.net.CraftTreeSync;
import io.github.metrovoc.residual.net.CraftWatch;
import io.github.metrovoc.residual.net.StorageClick;
import io.github.metrovoc.residual.net.StoragePin;
import io.github.metrovoc.residual.net.StorageSync;
import io.github.metrovoc.residual.registry.ModMenus;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

/**
 * A Storage Terminal's screen: the player's inventory, and on the server, the network it shows. What the network keeps
 * is sent to the player whenever it changes, a few times a second at most.
 */
public class TerminalMenu extends AbstractContainerMenu {
    public static final int INVENTORY_X = 17, INVENTORY_Y = 152;
    /** Ticks between two looks at what the network keeps. */
    private static final int EVERY = 5;

    public final BlockPos pos;
    private final Player player;
    @Nullable
    private Object2LongLinkedOpenHashMap<ItemResource> sent;
    private long[] sentUsage = new long[2];
    private Set<ItemResource> sentCraftable = Set.of();
    private List<CraftJobsSync.Job> sentJobs = List.of();
    private List<ItemResource> sentPins = List.of();
    /** The job whose plan it follows, whether it has sent that plan, and what it last said of its rows. */
    @Nullable
    private UUID watching;
    private boolean sentTree;
    private List<Long> sentDone = List.of();
    private int ticks;
    /** On the client: what the network keeps, as last sent; what it can make; the last plan; its running jobs. */
    public List<StorageSync.Entry> items = List.of();
    public long stored, capacity;
    public Set<ItemResource> craftable = Set.of();
    @Nullable
    public CraftPlanSync plan;
    public List<CraftJobsSync.Job> jobs = List.of();
    public List<ItemResource> pins = List.of();
    /** On the client: the job it follows, that job's plan, and how many crafts each row has made. */
    @Nullable
    public UUID treeJob;
    public List<CraftPlanSync.Row> tree = List.of();
    public List<Long> treeDone = List.of();

    public TerminalMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.TERMINAL.get(), id);
        this.pos = pos;
        this.player = inventory.player;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
    }

    private StorageNetwork network() {
        return StorageNetwork.at((ServerLevel) player.level(), pos);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer server) || ticks++ % EVERY != 0) {
            return;
        }
        StorageNetwork net = network();
        ServerLevel level = (ServerLevel) player.level();
        List<CraftJob> running = CraftingData.of(level.getServer()).on(level, net);
        List<CraftJobsSync.Job> jobs = running.stream().map(CraftJobsSync.Job::of).toList();
        if (!CraftJobsSync.same(jobs, sentJobs)) {
            sentJobs = jobs;
            PacketDistributor.sendToPlayer(server, new CraftJobsSync(containerId, jobs));
        }
        if (watching != null) {
            for (CraftJob job : running) {
                if (job.id.equals(watching)) {
                    List<Long> done = job.rowsDone();
                    if (!sentTree || !done.equals(sentDone)) {
                        PacketDistributor.sendToPlayer(server, new CraftTreeSync(containerId, job.id, sentTree ? List.of() : job.plan, done));
                        sentTree = true;
                        sentDone = done;
                    }
                }
            }
        }
        Object2LongLinkedOpenHashMap<ItemResource> now = net.contents();
        long[] usage = net.usage();
        Set<ItemResource> craftable = CraftJobs.craftable(net);
        List<ItemResource> pins = level.getBlockEntity(pos) instanceof TerminalBlockEntity t ? t.pins() : List.of();
        if (now.equals(sent) && usage[0] == sentUsage[0] && usage[1] == sentUsage[1] && craftable.equals(sentCraftable) && pins.equals(sentPins)) {
            return;
        }
        sent = now;
        sentUsage = usage;
        sentCraftable = craftable;
        sentPins = List.copyOf(pins);
        List<StorageSync.Entry> items = new ArrayList<>(now.size());
        for (Object2LongMap.Entry<ItemResource> e : now.object2LongEntrySet()) {
            items.add(new StorageSync.Entry(e.getKey(), e.getLongValue()));
        }
        PacketDistributor.sendToPlayer(server, new StorageSync(containerId, items, usage[0], usage[1], List.copyOf(craftable), sentPins));
    }

    /** Plans what the player asked for off the server thread, starts it if they said so, and sends the plan back. */
    public void ask(CraftAsk ask) {
        ServerLevel level = (ServerLevel) player.level();
        CraftJobs.plan(level, network(), ask.item(), ask.amount(), planned -> {
            if (!(player instanceof ServerPlayer server) || server.containerMenu != this) {
                return;
            }
            boolean start = ask.start() && planned.plan().root.recipe != null;
            if (start) {
                CraftJobs.start(level, server.getUUID(), pos, planned);
            }
            PacketDistributor.sendToPlayer(server, CraftPlanSync.of(containerId, planned.plan(), start));
        });
    }

    public void cancel(CraftCancel cancel) {
        CraftJobs.cancel((ServerLevel) player.level(), network(), cancel.job());
    }

    /** Follows a job's plan from now on, or none. */
    public void watch(CraftWatch watch) {
        watching = watch.job().orElse(null);
        sentTree = false;
        sentDone = List.of();
    }

    public void pin(StoragePin pin) {
        if (player.level().getBlockEntity(pos) instanceof TerminalBlockEntity terminal && !pin.item().isEmpty()) {
            terminal.pin(pin.item());
        }
    }

    /** Takes a kind out or puts the cursor's stack in, as the player clicked the grid. */
    public void click(StorageClick click) {
        StorageNetwork net = network();
        ItemStack carried = getCarried();
        switch (click.action()) {
            case TAKE, TAKE_HALF -> {
                ItemResource kind = ItemResource.of(click.item());
                if (kind.isEmpty() || !carried.isEmpty() && !kind.matches(carried)) {
                    return;
                }
                int max = kind.getMaxStackSize() - carried.getCount();
                long have = net.extract(kind, max, true);
                int n = (int) (click.action() == StorageClick.Action.TAKE_HALF ? (have + 1) / 2 : have);
                n = (int) net.extract(kind, n, false);
                if (n > 0) {
                    setCarried(carried.isEmpty() ? kind.toStack(n) : carried.copyWithCount(carried.getCount() + n));
                }
            }
            case TO_INVENTORY -> {
                ItemResource kind = ItemResource.of(click.item());
                if (kind.isEmpty()) {
                    return;
                }
                int n = (int) net.extract(kind, kind.getMaxStackSize(), false);
                ItemStack stack = kind.toStack(n);
                player.getInventory().add(stack);
                if (!stack.isEmpty()) {
                    net.insert(kind, stack.getCount(), false);
                }
            }
            case PUT, PUT_ONE -> {
                if (carried.isEmpty()) {
                    return;
                }
                int n = click.action() == StorageClick.Action.PUT_ONE ? 1 : carried.getCount();
                long stored = net.insert(ItemResource.of(carried), n, false);
                carried.shrink((int) stored);
                setCarried(carried);
            }
        }
    }

    /** Shift-clicking a stack in the inventory puts it into the network. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || !(player.level() instanceof ServerLevel)) {
            return ItemStack.EMPTY;
        }
        long stored = network().insert(ItemResource.of(stack), stack.getCount(), false);
        if (stored > 0) {
            stack.shrink((int) stored);
            slot.setChanged();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof TerminalBlockEntity && player.distanceToSqr(pos.getCenter()) < 64;
    }
}
