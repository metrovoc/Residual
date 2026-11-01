package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The key that opens the Wireless Terminal or Portable Drive first found in the player's inventory, without holding it. */
public record OpenTerminal() implements CustomPacketPayload {
    public static final Type<OpenTerminal> TYPE = new Type<>(Residual.id("open_terminal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTerminal> STREAM_CODEC = StreamCodec.unit(new OpenTerminal());

    @Override
    public Type<OpenTerminal> type() {
        return TYPE;
    }
}
