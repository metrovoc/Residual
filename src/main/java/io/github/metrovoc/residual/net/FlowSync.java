package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Items or a fluid moved between a storage block and where the network keeps them, for players near enough to see them go:
 * what went, how much, and whether a link on the way was full.
 */
public record FlowSync(BlockPos from, BlockPos to, boolean in, int count, ItemResource item, FluidResource fluid, boolean full)
        implements CustomPacketPayload {
    public static final Type<FlowSync> TYPE = new Type<>(Residual.id("flow"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlowSync> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, FlowSync::from,
            BlockPos.STREAM_CODEC, FlowSync::to,
            ByteBufCodecs.BOOL, FlowSync::in,
            ByteBufCodecs.VAR_INT, FlowSync::count,
            ItemResource.STREAM_CODEC, FlowSync::item,
            FluidResource.STREAM_CODEC, FlowSync::fluid,
            ByteBufCodecs.BOOL, FlowSync::full,
            FlowSync::new);

    @Override
    public Type<FlowSync> type() {
        return TYPE;
    }
}
