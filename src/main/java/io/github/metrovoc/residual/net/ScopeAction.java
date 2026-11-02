package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A Stream Scope or Trial Rig asked to do something: an instrument's place in its enum, or -1 to stop what it is doing.
 *
 * @param a,b what the screen's choices say, as {@code scope.Work} reads them
 */
public record ScopeAction(BlockPos pos, int instrument, int benchmark, int a, int b, String text) implements CustomPacketPayload {
    public static final Type<ScopeAction> TYPE = new Type<>(Residual.id("scope_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScopeAction> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ScopeAction::pos,
            ByteBufCodecs.VAR_INT, ScopeAction::instrument,
            ByteBufCodecs.VAR_INT, ScopeAction::benchmark,
            ByteBufCodecs.VAR_INT, ScopeAction::a,
            ByteBufCodecs.VAR_INT, ScopeAction::b,
            ByteBufCodecs.stringUtf8(256), ScopeAction::text,
            ScopeAction::new);

    @Override
    public Type<ScopeAction> type() {
        return TYPE;
    }
}
