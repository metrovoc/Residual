package io.github.metrovoc.residual.storage;

import io.github.metrovoc.residual.registry.ModBlockEntities;
import java.util.function.ObjLongConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * A Port at work: every tick it moves up to {@link #RATE} items between the inventory in front of it and its network,
 * as its mode says, or opens that inventory to the network as a store. Its filter names the kinds it works on; an
 * empty filter imports everything.
 */
public class PortBlockEntity extends BlockEntity implements MenuProvider {
    public static final int FILTERS = 9;
    /** Items a Port moves a tick: 1,280 a second. */
    public static final int RATE = 64;
    public static final int MOST = 1 << 20;
    /** Container data: how many of each kind to stock, the window's priority, and items moved in the last second. */
    public static final int AMOUNT = 0, PRIORITY = 1, MOVED = 2, DATA = 3;

    private final SimpleContainer filter = new SimpleContainer(FILTERS) {
        @Override
        public void setChanged() {
            super.setChanged();
            PortBlockEntity.this.setChanged();
        }
    };
    private int amount = 64;
    private int priority;
    private int movedThisSecond;
    private int movedLastSecond;
    @Nullable
    private BlockCapabilityCache<ResourceHandler<ItemResource>, Direction> target;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case AMOUNT -> amount;
                case PRIORITY -> priority;
                case MOVED -> movedLastSecond;
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

    public PortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PORT.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PortBlockEntity be) {
        be.tick((ServerLevel) level, state);
    }

    public PortMode mode() {
        return getBlockState().getValue(PortBlock.MODE);
    }

    private Direction front() {
        return getBlockState().getValue(PortBlock.FACING);
    }

    /** The inventory in front of it, or null. */
    @Nullable
    private ResourceHandler<ItemResource> target() {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        BlockPos at = worldPosition.relative(front());
        if (target == null || !target.pos().equals(at) || target.context() != front().getOpposite()) {
            target = BlockCapabilityCache.create(Capabilities.Item.BLOCK, server, at, front().getOpposite());
        }
        return target.getCapability();
    }

    private void tick(ServerLevel level, BlockState state) {
        if (level.getGameTime() % 20 == 0) {
            movedLastSecond = movedThisSecond;
            movedThisSecond = 0;
            boolean active = movedLastSecond > 0;
            if (state.getValue(PortBlock.ACTIVE) != active) {
                level.setBlock(worldPosition, state.setValue(PortBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
            }
        }
        PortMode mode = state.getValue(PortBlock.MODE);
        if (mode == PortMode.WINDOW) {
            return;
        }
        ResourceHandler<ItemResource> inv = target();
        if (inv == null) {
            return;
        }
        StorageNetwork net = StorageNetwork.at(level, worldPosition);
        int budget = RATE;
        switch (mode) {
            case IMPORT -> budget -= importFrom(inv, net, budget, null);
            case EXPORT -> {
                for (int i = 0; i < FILTERS && budget > 0; i++) {
                    ItemStack f = filter.getItem(i);
                    if (!f.isEmpty()) {
                        budget -= exportTo(inv, net, ItemResource.of(f), budget);
                    }
                }
            }
            case STOCK -> {
                for (int i = 0; i < FILTERS && budget > 0; i++) {
                    ItemStack f = filter.getItem(i);
                    if (f.isEmpty()) {
                        continue;
                    }
                    ItemResource kind = ItemResource.of(f);
                    long have = count(inv, kind);
                    if (have < amount) {
                        budget -= exportTo(inv, net, kind, (int) Math.min(budget, amount - have));
                    } else if (have > amount) {
                        budget -= importFrom(inv, net, (int) Math.min(budget, have - amount), kind);
                    }
                }
            }
            default -> {
            }
        }
        movedThisSecond += RATE - budget;
    }

    /** Whether its filter lets {@code kind} through: anything when the filter is empty. */
    private boolean allows(ItemResource kind) {
        boolean any = false;
        for (int i = 0; i < FILTERS; i++) {
            ItemStack f = filter.getItem(i);
            if (!f.isEmpty()) {
                any = true;
                if (kind.matches(f)) {
                    return true;
                }
            }
        }
        return !any;
    }

    private static long count(ResourceHandler<ItemResource> inv, ItemResource kind) {
        long n = 0;
        for (int slot = 0; slot < inv.size(); slot++) {
            if (inv.getResource(slot).equals(kind)) {
                n += inv.getAmountAsLong(slot);
            }
        }
        return n;
    }

    /**
     * Moves up to {@code most} items the filter allows (or only {@code only}) from {@code from} into the network.
     * Nothing is held in a transaction while the network stores it, so drives and windows never nest in one.
     */
    private int importFrom(ResourceHandler<ItemResource> from, StorageNetwork net, int most, @Nullable ItemResource only) {
        int moved = 0;
        for (int slot = 0; slot < from.size() && moved < most; slot++) {
            ItemResource kind = from.getResource(slot);
            if (kind.isEmpty() || (only != null ? !kind.equals(only) : !allows(kind))) {
                continue;
            }
            int want = (int) Math.min(most - moved, from.getAmountAsLong(slot));
            int fits = (int) net.insert(kind, want, true);
            if (fits <= 0) {
                continue;
            }
            int got;
            try (Transaction tx = Transaction.openRoot()) {
                got = from.extract(slot, kind, fits, tx);
                tx.commit();
            }
            long stored = net.insert(kind, got, false);
            if (stored < got) {
                giveBack(from, kind, (int) (got - stored));
            }
            moved += (int) stored;
        }
        return moved;
    }

    /** Moves up to {@code most} of {@code kind} from the network into {@code to}. */
    private int exportTo(ResourceHandler<ItemResource> to, StorageNetwork net, ItemResource kind, int most) {
        int have = (int) net.extract(kind, most, true);
        if (have <= 0) {
            return 0;
        }
        int room;
        try (Transaction tx = Transaction.openRoot()) {
            room = to.insert(kind, have, tx);
        }
        if (room <= 0) {
            return 0;
        }
        int took = (int) net.extract(kind, room, false), put;
        try (Transaction tx = Transaction.openRoot()) {
            put = to.insert(kind, took, tx);
            tx.commit();
        }
        if (put < took) {
            net.insert(kind, took - put, false);
        }
        return put;
    }

    /** Puts items it could not store back where they came from, or drops them as a last resort. */
    private void giveBack(ResourceHandler<ItemResource> to, ItemResource kind, int n) {
        int put;
        try (Transaction tx = Transaction.openRoot()) {
            put = to.insert(kind, n, tx);
            tx.commit();
        }
        if (put < n && level != null) {
            Block.popResource(level, worldPosition, kind.toStack(n - put));
        }
    }

    /** The inventory in front of it as a store, while it is a window onto it. */
    @Nullable
    Store window() {
        if (mode() != PortMode.WINDOW) {
            return null;
        }
        ResourceHandler<ItemResource> inv = target();
        if (inv == null) {
            return null;
        }
        return new Store() {
            @Override
            public int priority() {
                return priority;
            }

            @Override
            public long count(ItemResource item) {
                return PortBlockEntity.count(inv, item);
            }

            @Override
            public long insert(ItemResource item, long n, boolean simulate) {
                if (!allows(item)) {
                    return 0;
                }
                try (Transaction tx = Transaction.openRoot()) {
                    int put = inv.insert(item, (int) Math.min(n, Integer.MAX_VALUE), tx);
                    if (!simulate) {
                        tx.commit();
                        movedThisSecond += put;
                    }
                    return put;
                }
            }

            @Override
            public long extract(ItemResource item, long n, boolean simulate) {
                try (Transaction tx = Transaction.openRoot()) {
                    int took = inv.extract(item, (int) Math.min(n, Integer.MAX_VALUE), tx);
                    if (!simulate) {
                        tx.commit();
                        movedThisSecond += took;
                    }
                    return took;
                }
            }

            @Override
            public void forEach(ObjLongConsumer<ItemResource> each) {
                for (int slot = 0; slot < inv.size(); slot++) {
                    ItemResource kind = inv.getResource(slot);
                    if (!kind.isEmpty()) {
                        each.accept(kind, inv.getAmountAsLong(slot));
                    }
                }
            }
        };
    }

    public void setMode(PortMode mode) {
        if (level != null) {
            level.setBlockAndUpdate(worldPosition, getBlockState().setValue(PortBlock.MODE, mode));
            if (level instanceof ServerLevel server) {
                StorageNetwork.changed(server);
            }
        }
    }

    public void setAmount(int amount) {
        this.amount = Math.clamp(amount, 0, MOST);
        setChanged();
    }

    public void setPriority(int priority) {
        this.priority = Math.clamp(priority, -99, 99);
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
        return Component.translatable("block.residual.port");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PortMenu(id, inventory, worldPosition, filter, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out.child("filter"), filter.getItems());
        out.putInt("amount", amount);
        out.putInt("priority", priority);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("filter").ifPresent(c -> ContainerHelper.loadAllItems(c, filter.getItems()));
        amount = in.getIntOr("amount", 64);
        priority = in.getIntOr("priority", 0);
    }
}
