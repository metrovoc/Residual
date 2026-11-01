package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * A recipe laid into a Storage Terminal's crafting grid from the recipe viewer: for each of its nine cells, the kinds that would
 * do, first choice first; {@code stacks} to fill them as far as a stack rather than with one each.
 */
public record StorageTransfer(int menu, List<List<ItemResource>> cells, boolean stacks) implements CustomPacketPayload {
    public static final Type<StorageTransfer> TYPE = new Type<>(Residual.id("storage_transfer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageTransfer> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageTransfer::menu,
            ItemResource.STREAM_CODEC.apply(ByteBufCodecs.list()).apply(ByteBufCodecs.list()), StorageTransfer::cells,
            ByteBufCodecs.BOOL, StorageTransfer::stacks,
            StorageTransfer::new);

    @Override
    public Type<StorageTransfer> type() {
        return TYPE;
    }
}
