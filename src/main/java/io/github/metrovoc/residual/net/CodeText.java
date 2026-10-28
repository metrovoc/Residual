package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The PyTorch source of the model, for the Code tab. */
public record CodeText(String code) implements CustomPacketPayload {
    public static final Type<CodeText> TYPE = new Type<>(Residual.id("code_text"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CodeText> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(1 << 20).map(CodeText::new, CodeText::code).cast();

    @Override
    public Type<CodeText> type() {
        return TYPE;
    }
}
