package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * How a Stream Scope or Trial Rig stands, for its screen.
 *
 * @param note   what it says of its model, or of how the last work went
 * @param points the points of the model's stream, by name
 */
public record ScopeSync(BlockPos pos, boolean working, float fraction, Component note, List<Component> points) implements CustomPacketPayload {
    public static final Type<ScopeSync> TYPE = new Type<>(Residual.id("scope_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScopeSync> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ScopeSync::pos,
            ByteBufCodecs.BOOL, ScopeSync::working,
            ByteBufCodecs.FLOAT, ScopeSync::fraction,
            ComponentSerialization.STREAM_CODEC, ScopeSync::note,
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), ScopeSync::points,
            ScopeSync::new);

    @Override
    public Type<ScopeSync> type() {
        return TYPE;
    }
}
