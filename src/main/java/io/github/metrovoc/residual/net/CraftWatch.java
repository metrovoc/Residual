package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A Storage Terminal starts following one job's plan, or stops following any. */
public record CraftWatch(int menu, Optional<UUID> job) implements CustomPacketPayload {
    public static final Type<CraftWatch> TYPE = new Type<>(Residual.id("craft_watch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftWatch> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CraftWatch::menu,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), CraftWatch::job,
            CraftWatch::new);

    @Override
    public Type<CraftWatch> type() {
        return TYPE;
    }
}
