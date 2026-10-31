package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A Storage Terminal stops one of its network's crafting jobs. */
public record CraftCancel(int menu, UUID job) implements CustomPacketPayload {
    public static final Type<CraftCancel> TYPE = new Type<>(Residual.id("craft_cancel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftCancel> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CraftCancel::menu,
            UUIDUtil.STREAM_CODEC, CraftCancel::job,
            CraftCancel::new);

    @Override
    public Type<CraftCancel> type() {
        return TYPE;
    }
}
