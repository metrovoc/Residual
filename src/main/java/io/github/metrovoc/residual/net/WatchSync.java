package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** What a Watch Post's screen shows: each thing it watches, its line of looks and what it thinks of it. */
public record WatchSync(int menu, List<Row> rows) implements CustomPacketPayload {
    /** {@code verdict} is one of the Watch Post's {@code FINE}, {@code WRONG} and {@code BY_RULE}. */
    public record Row(ItemStack icon, String line, int verdict) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, Row::icon,
                ByteBufCodecs.STRING_UTF8, Row::line,
                ByteBufCodecs.VAR_INT, Row::verdict,
                Row::new);

        boolean same(Row o) {
            return line.equals(o.line) && verdict == o.verdict && ItemStack.matches(icon, o.icon);
        }
    }

    public static final Type<WatchSync> TYPE = new Type<>(Residual.id("watch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WatchSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WatchSync::menu,
            Row.STREAM_CODEC.apply(ByteBufCodecs.list()), WatchSync::rows,
            WatchSync::new);

    public static boolean same(List<Row> a, List<Row> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).same(b.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Type<WatchSync> type() {
        return TYPE;
    }
}
