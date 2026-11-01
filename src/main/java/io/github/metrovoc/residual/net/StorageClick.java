package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** A click on a Storage Terminal's grid: take a kind out (shown by one of it), or put what the cursor holds in. */
public record StorageClick(int menu, ItemStack item, Action action) implements CustomPacketPayload {
    public enum Action {
        /** A stack onto the cursor, or onto the stack it holds. */
        TAKE,
        /** Half a stack onto the cursor. */
        TAKE_HALF,
        /** A stack into the player's inventory. */
        TO_INVENTORY,
        /** Everything the cursor holds into the network. */
        PUT,
        /** One of what the cursor holds into the network. */
        PUT_ONE,
        /** The fluid in the bucket or tank the cursor holds into the network, the container staying on the cursor. */
        POUR
    }

    public static final Type<StorageClick> TYPE = new Type<>(Residual.id("storage_click"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageClick> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageClick::menu,
            ItemStack.OPTIONAL_STREAM_CODEC, StorageClick::item,
            ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), StorageClick::action,
            StorageClick::new);

    @Override
    public Type<StorageClick> type() {
        return TYPE;
    }
}
