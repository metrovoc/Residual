package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.scope.Picture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** What an instrument shows now. */
public record ScopeReading(BlockPos pos, int instrument, Picture picture) implements CustomPacketPayload {
    public static final Type<ScopeReading> TYPE = new Type<>(Residual.id("scope_reading"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScopeReading> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ScopeReading::pos,
            ByteBufCodecs.VAR_INT, ScopeReading::instrument,
            Picture.CODEC, ScopeReading::picture,
            ScopeReading::new);

    @Override
    public Type<ScopeReading> type() {
        return TYPE;
    }
}
