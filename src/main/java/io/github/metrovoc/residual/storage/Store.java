package io.github.metrovoc.residual.storage;

import java.util.function.ObjLongConsumer;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Somewhere a storage network keeps items: a drive in a rack, or an inventory a Port opens to it. */
public interface Store {
    /** Where items go first: higher first. */
    int priority();

    long count(ItemResource item);

    long insert(ItemResource item, long amount, boolean simulate);

    long extract(ItemResource item, long amount, boolean simulate);

    void forEach(ObjLongConsumer<ItemResource> each);

    /** A drive as a store. */
    static Store of(Drive drive) {
        return new Store() {
            @Override
            public int priority() {
                return 0;
            }

            @Override
            public long count(ItemResource item) {
                return drive.count(item);
            }

            @Override
            public long insert(ItemResource item, long amount, boolean simulate) {
                return drive.insert(item, amount, simulate);
            }

            @Override
            public long extract(ItemResource item, long amount, boolean simulate) {
                return drive.extract(item, amount, simulate);
            }

            @Override
            public void forEach(ObjLongConsumer<ItemResource> each) {
                drive.forEach(each);
            }
        };
    }
}
