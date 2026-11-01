package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** A Storage Terminal pins a kind to the top of its grid, or lets go of it. */
public record StoragePin(int menu, ItemResource item) implements CustomPacketPayload {
    public static final Type<StoragePin> TYPE = new Type<>(Residual.id("storage_pin"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StoragePin> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StoragePin::menu,
            ItemResource.STREAM_CODEC, StoragePin::item,
            StoragePin::new);

    @Override
    public Type<StoragePin> type() {
        return TYPE;
    }
}
