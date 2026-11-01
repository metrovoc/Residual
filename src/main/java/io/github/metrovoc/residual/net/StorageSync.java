package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * What changed in everything a Storage Terminal's network keeps: the kinds whose counts changed, none of a kind meaning it
 * is gone, or with {@code reset} the start of the whole list again; how full its drives are, what it can make, and what the
 * terminal has pinned. A long list comes in several of these.
 */
public record StorageSync(int menu, boolean reset, List<Entry> items, List<Fluid> fluids, long stored, long capacity,
                          List<ItemResource> craftable, List<ItemResource> pins)
        implements CustomPacketPayload {
    /** Kinds one packet carries, so a network of any number of kinds stays under what a packet may hold. */
    public static final int MOST = 2_048;

    public record Entry(ItemResource item, long count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ItemResource.STREAM_CODEC, Entry::item, ByteBufCodecs.VAR_LONG, Entry::count, Entry::new);
    }

    /** A fluid and how many millibuckets there are of it. */
    public record Fluid(FluidResource fluid, long amount) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Fluid> STREAM_CODEC = StreamCodec.composite(
                FluidResource.STREAM_CODEC, Fluid::fluid, ByteBufCodecs.VAR_LONG, Fluid::amount, Fluid::new);
    }

    public static final Type<StorageSync> TYPE = new Type<>(Residual.id("storage_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageSync::menu,
            ByteBufCodecs.BOOL, StorageSync::reset,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), StorageSync::items,
            Fluid.STREAM_CODEC.apply(ByteBufCodecs.list()), StorageSync::fluids,
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
