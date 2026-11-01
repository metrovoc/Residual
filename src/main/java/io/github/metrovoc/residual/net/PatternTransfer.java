package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * A recipe drawn on an Assembler or a Processor from the recipe viewer: pictures of what goes in, cell by cell, and of what comes
 * out. An empty stack leaves a cell empty.
 */
public record PatternTransfer(int menu, List<ItemStack> inputs, List<ItemStack> outputs) implements CustomPacketPayload {
    public static final Type<PatternTransfer> TYPE = new Type<>(Residual.id("pattern_transfer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PatternTransfer> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PatternTransfer::menu,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), PatternTransfer::inputs,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), PatternTransfer::outputs,
            PatternTransfer::new);

    @Override
    public Type<PatternTransfer> type() {
        return TYPE;
    }
}
