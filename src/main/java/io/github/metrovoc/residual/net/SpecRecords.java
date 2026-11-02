package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.scope.Records;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Asked of the server with an empty list, and answered with one: everyone's verified designs for a spec. */
public record SpecRecords(String spec, List<Records.Laid> designs) implements CustomPacketPayload {
    public static final Type<SpecRecords> TYPE = new Type<>(Residual.id("spec_records"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpecRecords> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), SpecRecords::spec,
            Records.Laid.STREAM_CODEC.apply(ByteBufCodecs.list()), SpecRecords::designs,
            SpecRecords::new);

    @Override
    public Type<SpecRecords> type() {
        return TYPE;
    }
}
