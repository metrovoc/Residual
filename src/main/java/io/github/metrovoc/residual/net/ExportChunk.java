package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * One piece of an exported model. The first piece carries the folder name and the source code; the weights
 * follow in pieces of at most {@link #CHUNK} bytes.
 */
public record ExportChunk(String folder, String code, int index, int total, byte[] data) implements CustomPacketPayload {
    public static final int CHUNK = 256 * 1024;
    public static final Type<ExportChunk> TYPE = new Type<>(Residual.id("export_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExportChunk> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ExportChunk::folder,
            ByteBufCodecs.stringUtf8(1 << 20), ExportChunk::code,
            ByteBufCodecs.VAR_INT, ExportChunk::index,
            ByteBufCodecs.VAR_INT, ExportChunk::total,
            ByteBufCodecs.byteArray(CHUNK), ExportChunk::data,
            ExportChunk::new);

    @Override
    public Type<ExportChunk> type() {
        return TYPE;
    }
}
