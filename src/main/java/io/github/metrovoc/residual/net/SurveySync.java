package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** What a Surveyor found, for a player near it holding a Survey Rod: each place, packed, and its ore by index. */
public record SurveySync(BlockPos surveyor, long[] places, byte[] ores) implements CustomPacketPayload {
    /** The most places sent at once. */
    public static final int MOST = 4096;
    public static final Type<SurveySync> TYPE = new Type<>(Residual.id("survey"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SurveySync> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SurveySync::surveyor,
            ByteBufCodecs.LONG_ARRAY, SurveySync::places,
            ByteBufCodecs.BYTE_ARRAY, SurveySync::ores,
            SurveySync::new);

    @Override
    public Type<SurveySync> type() {
        return TYPE;
    }
}
