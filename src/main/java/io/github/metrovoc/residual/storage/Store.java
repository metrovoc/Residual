package io.github.metrovoc.residual.storage;

import java.util.function.ObjLongConsumer;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Somewhere a storage network keeps items: a drive in a rack, or an inventory a Port opens to it. */
public interface Store {
    /** Where items go first: higher first. */
    int priority();

    /** The block it belongs to: a drive's rack, or a window's Port. */
    BlockPos pos();

    long count(ItemResource item);

    long insert(ItemResource item, long amount, boolean simulate);

    long extract(ItemResource item, long amount, boolean simulate);

    void forEach(ObjLongConsumer<ItemResource> each);

    /** A drive in the rack at {@code rack}, as a store. */
    static Store of(Drive drive, BlockPos rack) {
        return new Store() {
            @Override
            public int priority() {
                return 0;
            }

            @Override
            public BlockPos pos() {
                return rack;
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
