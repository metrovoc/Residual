package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** A thing dragged from the recipe viewer onto a slot that holds pictures, a Port's filter or a pattern's cell, as a click with it would. */
public record GhostSlot(int menu, int slot, ItemStack stack) implements CustomPacketPayload {
    public static final Type<GhostSlot> TYPE = new Type<>(Residual.id("ghost_slot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GhostSlot> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GhostSlot::menu,
            ByteBufCodecs.VAR_INT, GhostSlot::slot,
            ItemStack.OPTIONAL_STREAM_CODEC, GhostSlot::stack,
            GhostSlot::new);

    @Override
    public Type<GhostSlot> type() {
        return TYPE;
    }
}
