package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.Awards;
import io.github.metrovoc.residual.net.CraftAsk;
import io.github.metrovoc.residual.net.CraftCancel;
import io.github.metrovoc.residual.net.CraftJobsSync;
import io.github.metrovoc.residual.net.CraftPlanSync;
import io.github.metrovoc.residual.net.CraftTreeSync;
import io.github.metrovoc.residual.net.CraftWatch;
import io.github.metrovoc.residual.net.StorageClick;
import io.github.metrovoc.residual.net.StoragePin;
import io.github.metrovoc.residual.net.StorageSync;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import io.github.metrovoc.residual.registry.ModMenus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;

/**
 * A Storage Terminal's screen: the player's inventory, and on the server, the network it shows. What changes in what the
 * network keeps is sent to the player a few times a second at most.
 */
public class TerminalMenu extends AbstractContainerMenu {
    public static final int INVENTORY_X = 17, INVENTORY_Y = 152;
    /** Ticks between two looks at what the network keeps. */
    private static final int EVERY = 5;

    public final BlockPos pos;
    private final Player player;
    /** On the server, what it works on. */
    @Nullable
    private final TerminalHost host;
    private Object2LongLinkedOpenHashMap<ItemResource> sent = new Object2LongLinkedOpenHashMap<>();
    private Object2LongLinkedOpenHashMap<FluidResource> sentFluids = new Object2LongLinkedOpenHashMap<>();
    private boolean sentAny;
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
    public List<StorageSync.Fluid> fluids = List.of();
    private final Map<ItemResource, Long> keptItems = new LinkedHashMap<>();
    private final Map<FluidResource, Long> keptFluids = new LinkedHashMap<>();
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
        this(id, inventory, pos, null);
    }

    public TerminalMenu(int id, Inventory inventory, BlockPos pos, @Nullable TerminalHost host) {
        super(ModMenus.TERMINAL.get(), id);
        this.pos = pos;
        this.player = inventory.player;
        this.host = host;
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
        return host.network();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer server) || ticks++ % EVERY != 0) {
            return;
        }
        host.tick();
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
        syncContents(server, net);
    }

    /** Sends what changed in what the network keeps, how full it is, what it can make and what is pinned. */
    private void syncContents(ServerPlayer server, StorageNetwork net) {
        Object2LongLinkedOpenHashMap<ItemResource> now = net.contents();
        Object2LongLinkedOpenHashMap<FluidResource> nowFluids = net.fluids();
        long[] usage = net.usage();
        Set<ItemResource> craftable = CraftJobs.craftable(net);
        List<ItemResource> pins = host.pins();
        if (sentAny && now.equals(sent) && nowFluids.equals(sentFluids) && usage[0] == sentUsage[0] && usage[1] == sentUsage[1]
                && craftable.equals(sentCraftable) && pins.equals(sentPins)) {
            return;
        }
        List<StorageSync.Entry> items = new ArrayList<>();
        for (Object2LongMap.Entry<ItemResource> e : now.object2LongEntrySet()) {
            if (sent.getLong(e.getKey()) != e.getLongValue()) {
                items.add(new StorageSync.Entry(e.getKey(), e.getLongValue()));
            }
        }
        for (ItemResource gone : sent.keySet()) {
            if (!now.containsKey(gone)) {
                items.add(new StorageSync.Entry(gone, 0));
            }
        }
        List<StorageSync.Fluid> fluids = new ArrayList<>();
        for (Object2LongMap.Entry<FluidResource> e : nowFluids.object2LongEntrySet()) {
            if (sentFluids.getLong(e.getKey()) != e.getLongValue()) {
                fluids.add(new StorageSync.Fluid(e.getKey(), e.getLongValue()));
            }
        }
        for (FluidResource gone : sentFluids.keySet()) {
            if (!nowFluids.containsKey(gone)) {
                fluids.add(new StorageSync.Fluid(gone, 0));
            }
        }
        boolean reset = !sentAny;
        sentAny = true;
        sent = now;
        sentFluids = nowFluids;
        sentUsage = usage;
        sentCraftable = craftable;
        sentPins = List.copyOf(pins);
        // a network of many kinds comes in several packets; the first of them starts the list when there is none yet
        int atItem = 0, atFluid = 0;
        do {
            List<StorageSync.Entry> someItems = items.subList(atItem, Math.min(items.size(), atItem + StorageSync.MOST));
            int room = StorageSync.MOST - someItems.size();
            List<StorageSync.Fluid> someFluids = fluids.subList(atFluid, Math.min(fluids.size(), atFluid + room));
            atItem += someItems.size();
            atFluid += someFluids.size();
            PacketDistributor.sendToPlayer(server, new StorageSync(containerId, reset, List.copyOf(someItems), List.copyOf(someFluids),
                    usage[0], usage[1], List.copyOf(craftable), sentPins));
            reset = false;
        } while (atItem < items.size() || atFluid < fluids.size());
    }

    /** On the client: takes in what changed in what the network keeps. */
    public void apply(StorageSync sync) {
        if (sync.reset()) {
            keptItems.clear();
            keptFluids.clear();
        }
        for (StorageSync.Entry e : sync.items()) {
            if (e.count() == 0) {
                keptItems.remove(e.item());
            } else {
                keptItems.put(e.item(), e.count());
            }
        }
        for (StorageSync.Fluid f : sync.fluids()) {
            if (f.amount() == 0) {
                keptFluids.remove(f.fluid());
            } else {
                keptFluids.put(f.fluid(), f.amount());
            }
        }
        items = keptItems.entrySet().stream().map(e -> new StorageSync.Entry(e.getKey(), e.getValue())).toList();
        fluids = keptFluids.entrySet().stream().map(e -> new StorageSync.Fluid(e.getKey(), e.getValue())).toList();
        stored = sync.stored();
        capacity = sync.capacity();
        craftable = Set.copyOf(sync.craftable());
        pins = sync.pins();
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
        if (!pin.item().isEmpty()) {
            host.pin(pin.item());
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
            case POUR -> pour(net);
            case PUT, PUT_ONE -> {
                if (carried.isEmpty() || !host.takes(carried)) {
                    return;
                }
                int n = click.action() == StorageClick.Action.PUT_ONE ? 1 : carried.getCount();
                long stored = net.insert(ItemResource.of(carried), n, false);
                carried.shrink((int) stored);
                setCarried(carried);
            }
        }
    }

    /** Pours the fluid in what the cursor holds into the network, as much of a whole container as there is room for. */
    private void pour(StorageNetwork net) {
        ResourceHandler<FluidResource> tank = ItemAccess.forPlayerCursor(player, this).oneByOne().getCapability(Capabilities.Fluid.ITEM);
        for (int i = 0; tank != null && i < tank.size(); i++) {
            FluidResource fluid = tank.getResource(i);
            int fits = (int) net.insert(fluid, tank.getAmountAsLong(i), true);
            if (!fluid.isEmpty() && fits > 0) {
                int took;
                try (Transaction tx = Transaction.openRoot()) {
                    took = tank.extract(i, fluid, fits, tx);
                    tx.commit();
                }
                net.insert(fluid, took, false);
                if (took > 0 && player instanceof ServerPlayer server) {
                    Awards.grant(server, "storage/fluid");
                }
                return;
            }
        }
    }

    /** Fills the bucket or tank the cursor holds with {@code fluid} from the network, as much as it takes and there is. */
    public void fill(FluidResource fluid) {
        StorageNetwork net = network();
        ResourceHandler<FluidResource> tank = ItemAccess.forPlayerCursor(player, this).oneByOne().getCapability(Capabilities.Fluid.ITEM);
        if (tank == null || fluid.isEmpty()) {
            return;
        }
        int have = (int) net.extract(fluid, Integer.MAX_VALUE, true), put;
        try (Transaction tx = Transaction.openRoot()) {
            put = tank.insert(fluid, have, tx);
        }
        if (put > 0) {
            int took = (int) net.extract(fluid, put, false), done;
            try (Transaction tx = Transaction.openRoot()) {
                done = tank.insert(fluid, took, tx);
                tx.commit();
            }
            if (done < took) {
                net.insert(fluid, took - done, false);
            }
        }
    }

    /** Shift-clicking a stack in the inventory puts it into the network. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty() || !(player.level() instanceof ServerLevel) || !host.takes(stack)) {
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
        return host != null && host.reaches(player);
    }
}
