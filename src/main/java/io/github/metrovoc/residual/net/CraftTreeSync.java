package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The plan of the job a Storage Terminal follows, and how many crafts each of its rows has made. A job's plan never
 * changes, so its {@code rows} come once and are empty after that.
 */
public record CraftTreeSync(int menu, UUID job, List<CraftPlanSync.Row> rows, List<Long> done) implements CustomPacketPayload {
    public static final Type<CraftTreeSync> TYPE = new Type<>(Residual.id("craft_tree"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftTreeSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CraftTreeSync::menu,
            UUIDUtil.STREAM_CODEC, CraftTreeSync::job,
            CraftPlanSync.Row.STREAM_CODEC.apply(ByteBufCodecs.list()), CraftTreeSync::rows,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), CraftTreeSync::done,
            CraftTreeSync::new);

    @Override
    public Type<CraftTreeSync> type() {
        return TYPE;
    }
}
