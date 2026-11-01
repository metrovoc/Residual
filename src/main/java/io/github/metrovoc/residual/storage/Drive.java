package io.github.metrovoc.residual.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ObjLongConsumer;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * What one Storage Drive holds: any number of kinds, up to its capacity in all, an item or a bucket of fluid taking the
 * same room. A kind costs nothing but what there is of it. The contents live in the world's {@link StorageData}, so the
 * drive item itself only carries their id.
 */
public final class Drive {
    /** Fluids are kept in millibuckets: this many take the room of one item. */
    public static final long BUCKET = 1000;

    private record Entry(ItemResource item, long count) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemResource.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.LONG.fieldOf("count").forGetter(Entry::count)).apply(i, Entry::new));
    }

    private record FluidEntry(FluidResource fluid, long amount) {
        static final Codec<FluidEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                FluidResource.CODEC.fieldOf("fluid").forGetter(FluidEntry::fluid),
                Codec.LONG.fieldOf("amount").forGetter(FluidEntry::amount)).apply(i, FluidEntry::new));
    }

    static final Codec<Drive> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("capacity").forGetter(d -> d.capacity),
            Entry.CODEC.listOf().fieldOf("items").forGetter(Drive::entries),
            FluidEntry.CODEC.listOf().optionalFieldOf("fluids", List.of()).forGetter(Drive::fluidEntries)).apply(i, Drive::new));

    public final long capacity;
    private final Object2LongLinkedOpenHashMap<ItemResource> counts = new Object2LongLinkedOpenHashMap<>();
    private final Object2LongLinkedOpenHashMap<FluidResource> fluids = new Object2LongLinkedOpenHashMap<>();
    private long items;
    private long millibuckets;
    /** Told whenever the contents change, so they are saved. */
    Runnable changed = () -> {
    };

    Drive(long capacity) {
        this.capacity = capacity;
    }

    private Drive(long capacity, List<Entry> entries, List<FluidEntry> liquids) {
        this(capacity);
        for (Entry e : entries) {
            if (!e.item().isEmpty() && e.count() > 0) {
                counts.addTo(e.item(), e.count());
                items += e.count();
            }
        }
        for (FluidEntry e : liquids) {
            if (!e.fluid().isEmpty() && e.amount() > 0) {
                fluids.addTo(e.fluid(), e.amount());
                millibuckets += e.amount();
            }
        }
    }

    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>(counts.size());
        for (Object2LongMap.Entry<ItemResource> e : counts.object2LongEntrySet()) {
            out.add(new Entry(e.getKey(), e.getLongValue()));
        }
        return out;
    }

    private List<FluidEntry> fluidEntries() {
        List<FluidEntry> out = new ArrayList<>(fluids.size());
        for (Object2LongMap.Entry<FluidResource> e : fluids.object2LongEntrySet()) {
            out.add(new FluidEntry(e.getKey(), e.getLongValue()));
        }
        return out;
    }

    /** How much of its capacity is taken: its items, and its fluids by the bucket, a part of one counting whole. */
    public long stored() {
        return items + (millibuckets + BUCKET - 1) / BUCKET;
    }

    public int kinds() {
        return counts.size() + fluids.size();
    }

    public long count(ItemResource item) {
        return counts.getLong(item);
    }

    public long amount(FluidResource fluid) {
        return fluids.getLong(fluid);
    }

    /** Stores up to {@code amount} of {@code item}; returns how many fit. */
    public long insert(ItemResource item, long amount, boolean simulate) {
        long n = Math.min(amount, capacity - stored());
        if (n <= 0 || item.isEmpty()) {
            return 0;
        }
        if (!simulate) {
            counts.addTo(item, n);
            items += n;
            changed.run();
        }
        return n;
    }

    /** Takes up to {@code amount} of {@code item} out; returns how many there were. */
    public long extract(ItemResource item, long amount, boolean simulate) {
        long have = counts.getLong(item), n = Math.min(amount, have);
        if (n <= 0) {
            return 0;
        }
        if (!simulate) {
            if (n == have) {
                counts.removeLong(item);
            } else {
                counts.put(item, have - n);
            }
            items -= n;
            changed.run();
        }
        return n;
    }

    /** Stores up to {@code amount} millibuckets of {@code fluid}; returns how much fit. */
    public long insert(FluidResource fluid, long amount, boolean simulate) {
        long n = Math.min(amount, (capacity - items) * BUCKET - millibuckets);
        if (n <= 0 || fluid.isEmpty()) {
            return 0;
        }
        if (!simulate) {
            fluids.addTo(fluid, n);
            millibuckets += n;
            changed.run();
        }
        return n;
    }

    /** Takes up to {@code amount} millibuckets of {@code fluid} out; returns how much there was. */
    public long extract(FluidResource fluid, long amount, boolean simulate) {
        long have = fluids.getLong(fluid), n = Math.min(amount, have);
        if (n <= 0) {
            return 0;
        }
        if (!simulate) {
            if (n == have) {
                fluids.removeLong(fluid);
            } else {
                fluids.put(fluid, have - n);
            }
            millibuckets -= n;
            changed.run();
        }
        return n;
    }

    public void forEach(ObjLongConsumer<ItemResource> each) {
        for (Object2LongMap.Entry<ItemResource> e : counts.object2LongEntrySet()) {
            each.accept(e.getKey(), e.getLongValue());
        }
    }

    public void forEachFluid(ObjLongConsumer<FluidResource> each) {
        for (Object2LongMap.Entry<FluidResource> e : fluids.object2LongEntrySet()) {
            each.accept(e.getKey(), e.getLongValue());
        }
    }
}
