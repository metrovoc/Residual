package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * One training step of a model, drawn as two waves through its blocks: activations forward from the tokens to the
 * loss, then gradients back. Each block lights at its node's place in the order, as brightly as the vector it carries.
 *
 * @param slots    places in the order: the tokens, each node, then the loss
 * @param slot     each block's place
 * @param forward  how big the activation it carries is, 0 to 255 on a log scale
 * @param backward how big the gradient it carries is, likewise
 */
public record WaveSync(BlockPos core, int slots, long[] blocks, int[] slot, byte[] forward, byte[] backward)
        implements CustomPacketPayload {
    public static final Type<WaveSync> TYPE = new Type<>(Residual.id("waves"));
    public static final StreamCodec<FriendlyByteBuf, WaveSync> STREAM_CODEC = StreamCodec.of((buf, w) -> {
        buf.writeBlockPos(w.core);
        buf.writeVarInt(w.slots);
        buf.writeVarInt(w.blocks.length);
        for (int i = 0; i < w.blocks.length; i++) {
            buf.writeLong(w.blocks[i]);
            buf.writeVarInt(w.slot[i]);
            buf.writeByte(w.forward[i]);
            buf.writeByte(w.backward[i]);
        }
    }, buf -> {
        BlockPos core = buf.readBlockPos();
        int slots = buf.readVarInt();
        int n = buf.readVarInt();
        long[] blocks = new long[n];
        int[] slot = new int[n];
        byte[] forward = new byte[n], backward = new byte[n];
        for (int i = 0; i < n; i++) {
            blocks[i] = buf.readLong();
            slot[i] = buf.readVarInt();
            forward[i] = buf.readByte();
            backward[i] = buf.readByte();
        }
        return new WaveSync(core, slots, blocks, slot, forward, backward);
    });

    @Override
    public Type<WaveSync> type() {
        return TYPE;
    }
}
