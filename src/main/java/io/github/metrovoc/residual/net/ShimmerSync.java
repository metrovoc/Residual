package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Blocks being run ahead in this half second, packed, and how many times as fast each. */
public record ShimmerSync(long[] places, List<Integer> paces) implements CustomPacketPayload {
    public static final Type<ShimmerSync> TYPE = new Type<>(Residual.id("shimmer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShimmerSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG_ARRAY, ShimmerSync::places,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), ShimmerSync::paces,
            ShimmerSync::new);

    @Override
    public Type<ShimmerSync> type() {
        return TYPE;
    }
}
