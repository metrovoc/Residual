package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** A Storage Terminal asks its network how it would make an amount of something, and, with {@code start}, to make it. */
public record CraftAsk(int menu, ItemResource item, long amount, boolean start) implements CustomPacketPayload {
    public static final Type<CraftAsk> TYPE = new Type<>(Residual.id("craft_ask"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftAsk> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CraftAsk::menu,
            ItemResource.STREAM_CODEC, CraftAsk::item,
            ByteBufCodecs.VAR_LONG, CraftAsk::amount,
            ByteBufCodecs.BOOL, CraftAsk::start,
            CraftAsk::new);

    @Override
    public Type<CraftAsk> type() {
        return TYPE;
    }
}
