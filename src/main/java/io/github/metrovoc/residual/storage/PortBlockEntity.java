package io.github.metrovoc.residual.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.agent.Things;
import io.github.metrovoc.residual.item.FieldLogItem;
import io.github.metrovoc.residual.learn.Demand;
import io.github.metrovoc.residual.learn.Kinds;
import io.github.metrovoc.residual.model.ModelLink;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ObjLongConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
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
 * empty filter imports everything. With a filter model in its model slot, a filter entry renamed for a kind (Food,
 * Plants, Wood, Stone, Ores, Tools) takes every thing the model, asked as the sorting test asks, is sure is of that
 * kind; what it is unsure of counts as other. With a target model too, it reads what it has moved out of each thing it
 * asks for and has what it expects to move next made ahead of time; what the model is unsure of, it asks for only as it runs short.
 */
public class PortBlockEntity extends BlockEntity implements MenuProvider {
    private record Line(ItemResource kind, String looks) {
        static final Codec<Line> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemResource.CODEC.fieldOf("kind").forGetter(Line::kind),
                Codec.STRING.fieldOf("looks").forGetter(Line::looks)
        ).apply(i, Line::new));
    }

    public static final int FILTERS = 9;
    /** Items a Port moves a tick: 1,280 a second. */
    public static final int RATE = 64;
    public static final int MOST = 1 << 20;
    /**
     * Container data: how many of each kind to stock, the window's priority, items moved in the last second, whether it
     * asks for what it lacks, and how much its target model expects it to move next, or -1 with no answer it trusts.
     */
    public static final int AMOUNT = 0, PRIORITY = 1, MOVED = 2, CRAFT = 3, EXPECTS = 4, DATA = 5;
    /** The slots beside its screen besides the filter model's: its target model and the Field Log it writes to. */
    public static final int TARGET = 0, LOG = 1;
    /** Ticks between two looks at what it lacks. */
    private static final int ASK_EVERY = 20;

    private final SimpleContainer filter = new SimpleContainer(FILTERS) {
        @Override
        public void setChanged() {
            super.setChanged();
            PortBlockEntity.this.setChanged();
        }
    };
    /** Its filter model, a checkpoint or a relay card. */
    private final SimpleContainer model = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            judged.clear();
            PortBlockEntity.this.setChanged();
        }
    };
    private final SimpleContainer extras = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            PortBlockEntity.this.setChanged();
        }
    };
    /** How much of each thing it moved out since its last look. */
    private final Map<ItemResource, Long> movedOut = new HashMap<>();
    /** What it moved out of each thing its filter names, a digit a look, newest last. */
    private final Map<ItemResource, String> lines = new LinkedHashMap<>();
    /** What its target model is sure each thing's next looks will move. */
    private final Map<ItemResource, Long> expected = new HashMap<>();
    /** Whether its target model is reading a line now, and looks since it last wrote to its Field Log. */
    private boolean forecasting;
    private int looksUnlogged;
    /** What kind its filter model judged each thing, or what it fell back to when unsure. */
    private final Map<Item, Kinds.Kind> judged = new HashMap<>();
    /** The thing its model is being asked about, or null. */
    @Nullable
    private Item asking;
    private int amount = 64;
    private int priority;
    /** Whether it has the network's Assemblers and Processors make what it lacks. */
    private boolean craft;
    /** The kinds it asked to have planned and has not heard back about. */
    private final Set<ItemResource> planning = new HashSet<>();
    private int movedThisSecond;
    /** Items moved since it last showed a flow, and whether the last of them went into the network. */
    private int movedSinceFlow;
    private boolean flowIn;
    @Nullable
    private BlockPos flowTo;
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
                case CRAFT -> craft ? 1 : 0;
                case EXPECTS -> expected.isEmpty() ? -1 : (int) Math.min(Integer.MAX_VALUE, expected.values().stream().mapToLong(Long::longValue).sum());
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
        judgeNext(level);
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
        if (mode != PortMode.IMPORT && level.getGameTime() % Demand.LOOK == Math.floorMod(worldPosition.asLong(), Demand.LOOK)) {
            look(level);
        }
        if (craft && mode != PortMode.IMPORT && level.getGameTime() % ASK_EVERY == Math.floorMod(worldPosition.asLong(), ASK_EVERY)) {
            askForWhatItLacks(level, net, inv, mode);
        }
        int budget = RATE;
        switch (mode) {
            case IMPORT -> budget -= importFrom(inv, net, budget, null);
            case EXPORT -> {
                for (int i = 0; i < FILTERS && budget > 0; i++) {
                    ItemStack f = filter.getItem(i);
                    Kinds.Kind kind = kindOf(f);
                    if (kind != null) {
                        for (ItemResource held : net.contents().keySet()) {
                            if (budget <= 0) {
                                break;
                            }
                            if (judged.get(held.getItem()) == kind) {
                                budget -= exportTo(inv, net, held, budget);
                            }
                        }
                    } else if (!f.isEmpty()) {
                        budget -= exportTo(inv, net, ItemResource.of(f), budget);
                    }
                }
            }
            case STOCK -> {
                for (int i = 0; i < FILTERS && budget > 0; i++) {
                    ItemStack f = filter.getItem(i);
                    // it stocks things, not kinds
                    if (f.isEmpty() || kindOf(f) != null) {
                        continue;
                    }
                    ItemResource kind = ItemResource.of(f);
                    long have = count(inv, kind);
                    if (have < amount) {
                        budget -= exportTo(inv, net, kind, (int) Math.min(budget, amount - have));
                        flowIn = false;
                    } else if (have > amount) {
                        budget -= importFrom(inv, net, (int) Math.min(budget, have - amount), kind);
                        flowIn = true;
                    }
                }
            }
            default -> {
            }
        }
        int moved = RATE - budget;
        movedThisSecond += moved;
        movedSinceFlow += moved;
        if (moved > 0) {
            flowTo = net.lastStore;
        }
        if (movedSinceFlow > 0 && level.getGameTime() % Flows.EVERY == Math.floorMod(worldPosition.asLong(), Flows.EVERY)) {
            Flows.send(level, worldPosition, flowTo, mode == PortMode.IMPORT || mode == PortMode.STOCK && flowIn, movedSinceFlow);
            movedSinceFlow = 0;
        }
    }

    /**
     * Has the network make what its filter names and neither the network nor the inventory in front has enough of: what
     * is short of its stock and of what its target model expects it to move next, or when exporting that much, or a stack if
     * it expects nothing and the network has none and the inventory has room. One job at a time for each kind; a job that
     * lacks something waits for it where the terminal shows it.
     */
    private void askForWhatItLacks(ServerLevel level, StorageNetwork net, ResourceHandler<ItemResource> inv, PortMode mode) {
        Set<ItemResource> makes = CraftJobs.craftable(net);
        if (makes.isEmpty()) {
            return;
        }
        List<CraftJob> running = CraftingData.of(level.getServer()).on(level, net);
        for (int i = 0; i < FILTERS; i++) {
            ItemStack f = filter.getItem(i);
            if (f.isEmpty() || kindOf(f) != null) {
                continue;
            }
            ItemResource kind = ItemResource.of(f);
            if (!makes.contains(kind) || planning.contains(kind)
                    || running.stream().anyMatch(j -> j.anchor.equals(worldPosition) && j.target.equals(kind))) {
                continue;
            }
            long next = expected.getOrDefault(kind, 0L);
            long lacking = mode == PortMode.STOCK ? amount + next - count(inv, kind) - net.count(kind)
                    : next > 0 ? next - net.count(kind)
                    : net.count(kind) == 0 && hasRoom(inv, kind) ? kind.getMaxStackSize() : 0;
            if (lacking > 0) {
                planning.add(kind);
                CraftJobs.plan(level, net, kind, lacking, planned -> {
                    planning.remove(kind);
                    if (planned.plan().root.recipe != null && !isRemoved()) {
                        CraftJobs.start(level, Util.NIL_UUID, worldPosition, planned);
                    }
                });
            }
        }
    }

    private static boolean hasRoom(ResourceHandler<ItemResource> inv, ItemResource kind) {
        try (Transaction tx = Transaction.openRoot()) {
            return inv.insert(kind, 1, tx) > 0;
        }
    }

    /**
     * A look at what it moved out: a digit on the line of each thing its filter names, and those lines in its Field Log
     * whenever they have all turned over. Its target model then reads the next line, one a look.
     */
    private void look(ServerLevel level) {
        Set<ItemResource> named = new LinkedHashSet<>();
        for (int i = 0; i < FILTERS; i++) {
            ItemStack f = filter.getItem(i);
            if (!f.isEmpty() && kindOf(f) == null) {
                named.add(ItemResource.of(f));
            }
        }
        lines.keySet().retainAll(named);
        expected.keySet().retainAll(named);
        ItemStack log = extras.getItem(LOG);
        ItemStack model = extras.getItem(TARGET);
        if (!craft && model.isEmpty() && log.isEmpty()) {
            movedOut.clear();
            return;
        }
        for (ItemResource kind : named) {
            String line = lines.getOrDefault(kind, "") + Demand.look(movedOut.getOrDefault(kind, 0L));
            lines.put(kind, line.length() > Demand.LINE ? line.substring(line.length() - Demand.LINE) : line);
        }
        movedOut.clear();
        if (log.getItem() instanceof FieldLogItem && ++looksUnlogged >= Demand.LINE) {
            looksUnlogged = 0;
            StringBuilder text = new StringBuilder(FieldLogItem.log(log));
            lines.values().forEach(line -> text.append(Demand.START).append(line).append('\n'));
            int cut = Math.max(0, text.length() - FieldLogItem.CAPACITY);
            cut = cut == 0 ? 0 : text.indexOf(String.valueOf(Demand.START), cut);
            log.set(ModComponents.FIELD_LOG.get(), cut < 0 ? "" : text.substring(cut));
            setChanged();
        }
        ModelLink link = model.isEmpty() ? null : ModelLink.of(level, worldPosition, model);
        if (link == null || link instanceof ModelLink.Missing) {
            expected.clear();
            return;
        }
        forecastNext(level, link);
    }

    /** Has its target model read the next line long enough to go on from; an answer it is not sure of drops what it expected. */
    private void forecastNext(ServerLevel level, ModelLink link) {
        if (forecasting) {
            return;
        }
        List<ItemResource> kinds = new ArrayList<>(lines.keySet());
        for (int i = 0; i < kinds.size(); i++) {
            ItemResource kind = kinds.get((int) (level.getGameTime() / Demand.LOOK + i) % kinds.size());
            String line = lines.get(kind);
            if (line.length() >= Demand.READS) {
                forecasting = true;
                link.ask(level, line, Demand.AHEAD, '\n', answer -> {
                    forecasting = false;
                    if (answer != null && answer.confidence() >= Demand.SURE) {
                        expected.put(kind, Demand.moved(answer.text()));
                    } else {
                        expected.remove(kind);
                    }
                });
                return;
            }
        }
    }

    /** Whether its filter lets {@code kind} through: anything when the filter is empty. */
    private boolean allows(ItemResource kind) {
        boolean any = false;
        for (int i = 0; i < FILTERS; i++) {
            ItemStack f = filter.getItem(i);
            if (!f.isEmpty()) {
                any = true;
                Kinds.Kind named = kindOf(f);
                if (named != null ? judged.get(kind.getItem()) == named : kind.matches(f)) {
                    return true;
                }
            }
        }
        return !any;
    }

    /** The kind a filter entry stands for: one renamed Food, Plants, Wood, Stone, Ores or Tools, or other. */
    @Nullable
    private static Kinds.Kind kindOf(ItemStack f) {
        Component name = f.get(DataComponents.CUSTOM_NAME);
        return name == null ? null : Kinds.Kind.labelled(name.getString());
    }

    /**
     * Asks its filter model about the next thing a kind entry needs judged, one at a time: what lies in the inventory in
     * front when importing, what the network holds when exporting. An answer it is not sure of falls back to other.
     */
    private void judgeNext(ServerLevel level) {
        if (asking != null || model.getItem(0).isEmpty()) {
            return;
        }
        boolean kinds = false;
        for (int i = 0; i < FILTERS && !kinds; i++) {
            kinds = kindOf(filter.getItem(i)) != null;
        }
        if (!kinds || mode() == PortMode.WINDOW || mode() == PortMode.STOCK) {
            return;
        }
        Item next = null;
        if (mode() == PortMode.IMPORT) {
            ResourceHandler<ItemResource> inv = target();
            for (int slot = 0; inv != null && slot < inv.size() && next == null; slot++) {
                Item item = inv.getResource(slot).getItem();
                if (!inv.getResource(slot).isEmpty() && !judged.containsKey(item)) {
                    next = item;
                }
            }
        } else {
            for (ItemResource held : StorageNetwork.at(level, worldPosition).contents().keySet()) {
                if (!judged.containsKey(held.getItem())) {
                    next = held.getItem();
                    break;
                }
            }
        }
        ModelLink link = ModelLink.of(level, worldPosition, model.getItem(0));
        if (next == null || link instanceof ModelLink.Missing) {
            return;
        }
        Item item = next;
        asking = item;
        link.ask(level, Kinds.prompt(Things.name(new ItemStack(item))), 1, '\n', answer -> {
            asking = null;
            Kinds.Kind kind = answer == null || answer.text().isEmpty() ? null : Kinds.Kind.of(answer.text().charAt(0));
            judged.put(item, kind != null && answer.confidence() >= Kinds.SURE ? kind : Kinds.Kind.OTHER);
        });
    }

    /** The filter model's slot, for its menu. */
    public SimpleContainer model() {
        return model;
    }

    /** How much its target model expects it to move next, over all it names; -1 with no answer it trusts. */
    public int expects() {
        return data.get(EXPECTS);
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
        movedOut.merge(kind, (long) put, Long::sum);
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
            public BlockPos pos() {
                return worldPosition;
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

    public void setCraft(boolean craft) {
        this.craft = craft;
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
        return new PortMenu(id, inventory, worldPosition, filter, model, extras, data);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, model);
            Containers.dropContents(level, pos, extras);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        ContainerHelper.saveAllItems(out.child("filter"), filter.getItems());
        ContainerHelper.saveAllItems(out.child("model"), model.getItems());
        ContainerHelper.saveAllItems(out.child("extras"), extras.getItems());
        List<Line> saved = new ArrayList<>();
        lines.forEach((kind, looks) -> saved.add(new Line(kind, looks)));
        out.store("lines", Line.CODEC.listOf(), saved);
        out.putInt("amount", amount);
        out.putInt("priority", priority);
        out.putBoolean("craft", craft);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("filter").ifPresent(c -> ContainerHelper.loadAllItems(c, filter.getItems()));
        in.child("model").ifPresent(c -> ContainerHelper.loadAllItems(c, model.getItems()));
        in.child("extras").ifPresent(c -> ContainerHelper.loadAllItems(c, extras.getItems()));
        lines.clear();
        in.read("lines", Line.CODEC.listOf()).ifPresent(saved -> saved.forEach(l -> lines.put(l.kind(), l.looks())));
        amount = in.getIntOr("amount", 64);
        priority = in.getIntOr("priority", 0);
        craft = in.getBooleanOr("craft", false);
    }
}
