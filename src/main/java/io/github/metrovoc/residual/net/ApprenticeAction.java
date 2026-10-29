package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A button on an apprentice's screen: give it a job, say something to it, or make where it stands its home. */
public record ApprenticeAction(int entity, Kind kind, int job, String said) implements CustomPacketPayload {
    public enum Kind { JOB, SAY, HOME }

    public static final Type<ApprenticeAction> TYPE = new Type<>(Residual.id("apprentice_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ApprenticeAction> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ApprenticeAction::entity,
            ByteBufCodecs.idMapper(i -> Kind.values()[i], Kind::ordinal), ApprenticeAction::kind,
            ByteBufCodecs.VAR_INT, ApprenticeAction::job,
            ByteBufCodecs.stringUtf8(200), ApprenticeAction::said,
            ApprenticeAction::new);

    @Override
    public Type<ApprenticeAction> type() {
        return TYPE;
    }
}
