package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** How a Model Server is doing, for whoever holds something tuned to it. */
public record ServerLoad(float rate, float load, int waiting, boolean turning) implements CustomPacketPayload {
    public static final Type<ServerLoad> TYPE = new Type<>(Residual.id("server_load"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerLoad> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ServerLoad::rate,
            ByteBufCodecs.FLOAT, ServerLoad::load,
            ByteBufCodecs.VAR_INT, ServerLoad::waiting,
            ByteBufCodecs.BOOL, ServerLoad::turning,
            ServerLoad::new);

    @Override
    public Type<ServerLoad> type() {
        return TYPE;
    }
}
