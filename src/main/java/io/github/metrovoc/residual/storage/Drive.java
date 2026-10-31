package io.github.metrovoc.residual.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ObjLongConsumer;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * What one Storage Drive holds: any number of kinds, up to its capacity in items in all. A kind costs nothing but its
 * items. The contents live in the world's {@link StorageData}, so the drive item itself only carries their id.
 */
public final class Drive {
    private record Entry(ItemResource item, long count) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemResource.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.LONG.fieldOf("count").forGetter(Entry::count)).apply(i, Entry::new));
    }

    static final Codec<Drive> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("capacity").forGetter(d -> d.capacity),
            Entry.CODEC.listOf().fieldOf("items").forGetter(Drive::entries)).apply(i, Drive::new));

    public final long capacity;
    private final Object2LongLinkedOpenHashMap<ItemResource> counts = new Object2LongLinkedOpenHashMap<>();
    private long stored;
    /** Told whenever the contents change, so they are saved. */
    Runnable changed = () -> {
    };

    Drive(long capacity) {
        this.capacity = capacity;
    }

    private Drive(long capacity, List<Entry> entries) {
        this(capacity);
        for (Entry e : entries) {
            if (!e.item().isEmpty() && e.count() > 0) {
                counts.addTo(e.item(), e.count());
                stored += e.count();
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

    public long stored() {
        return stored;
    }

    public int kinds() {
        return counts.size();
    }

    public long count(ItemResource item) {
        return counts.getLong(item);
    }

    /** Stores up to {@code amount} of {@code item}; returns how many fit. */
    public long insert(ItemResource item, long amount, boolean simulate) {
        long n = Math.min(amount, capacity - stored);
        if (n <= 0 || item.isEmpty()) {
            return 0;
        }
        if (!simulate) {
            counts.addTo(item, n);
            stored += n;
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
            stored -= n;
            changed.run();
        }
        return n;
    }

    public void forEach(ObjLongConsumer<ItemResource> each) {
        for (Object2LongMap.Entry<ItemResource> e : counts.object2LongEntrySet()) {
            each.accept(e.getKey(), e.getLongValue());
        }
    }
}
