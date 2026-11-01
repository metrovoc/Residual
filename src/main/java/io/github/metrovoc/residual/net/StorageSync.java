package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** Everything a Storage Terminal's network keeps, kind by kind, how full its drives are, what it can make, and what the terminal has pinned. */
public record StorageSync(int menu, List<Entry> items, long stored, long capacity, List<ItemResource> craftable, List<ItemResource> pins)
        implements CustomPacketPayload {
    public record Entry(ItemResource item, long count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemResource.STREAM_CODEC, Entry::item, ByteBufCodecs.VAR_LONG, Entry::count, Entry::new);
    }

    public static final Type<StorageSync> TYPE = new Type<>(Residual.id("storage_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageSync::menu,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), StorageSync::items,
            ByteBufCodecs.VAR_LONG, StorageSync::stored,
            ByteBufCodecs.VAR_LONG, StorageSync::capacity,
            ItemResource.STREAM_CODEC.apply(ByteBufCodecs.list()), StorageSync::craftable,
            ItemResource.STREAM_CODEC.apply(ByteBufCodecs.list()), StorageSync::pins,
            StorageSync::new);

    @Override
    public Type<StorageSync> type() {
        return TYPE;
    }
}
