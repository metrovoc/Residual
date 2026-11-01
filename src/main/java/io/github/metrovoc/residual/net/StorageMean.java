package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A Storage Terminal asks its search slot's model what {@code query} means among what its network keeps; empty to stop asking. */
public record StorageMean(int menu, String query) implements CustomPacketPayload {
    public static final Type<StorageMean> TYPE = new Type<>(Residual.id("storage_mean"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageMean> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageMean::menu,
            ByteBufCodecs.STRING_UTF8, StorageMean::query,
            StorageMean::new);

    @Override
    public Type<StorageMean> type() {
        return TYPE;
    }
}
