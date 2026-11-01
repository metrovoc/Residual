package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * How far a search by meaning has come: the things just found to fit and how far each does, how many have been looked at of
 * how many, and the translation key of why the model can't be asked, if it can't.
 */
public record StorageMeaningSync(int menu, String query, List<Hit> hits, int asked, int total, String problem)
        implements CustomPacketPayload {
    public record Hit(ItemResource item, float sure) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Hit> STREAM_CODEC = StreamCodec.composite(
                ItemResource.STREAM_CODEC, Hit::item, ByteBufCodecs.FLOAT, Hit::sure, Hit::new);
    }

    public static final Type<StorageMeaningSync> TYPE = new Type<>(Residual.id("storage_meaning"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageMeaningSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StorageMeaningSync::menu,
            ByteBufCodecs.STRING_UTF8, StorageMeaningSync::query,
            Hit.STREAM_CODEC.apply(ByteBufCodecs.list()), StorageMeaningSync::hits,
            ByteBufCodecs.VAR_INT, StorageMeaningSync::asked,
            ByteBufCodecs.VAR_INT, StorageMeaningSync::total,
            ByteBufCodecs.STRING_UTF8, StorageMeaningSync::problem,
            StorageMeaningSync::new);

    @Override
    public Type<StorageMeaningSync> type() {
        return TYPE;
    }
}
