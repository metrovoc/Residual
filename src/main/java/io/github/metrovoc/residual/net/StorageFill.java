package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** A click on a fluid in a Storage Terminal's grid: fill the bucket or tank the cursor holds with it. */
public record StorageFill(int menu, FluidResource fluid) implements CustomPacketPayload {
    public static final Type<StorageFill> TYPE = new Type<>(Residual.id("storage_fill"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageFill> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageFill::menu,
            FluidResource.STREAM_CODEC, StorageFill::fluid,
            StorageFill::new);

    @Override
    public Type<StorageFill> type() {
        return TYPE;
    }
}
