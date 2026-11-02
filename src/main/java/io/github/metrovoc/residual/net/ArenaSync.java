package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What a model just did on an Arena's floor, for those near enough to watch: a number for each fight, packed by {@link #pack},
 * and each pad's newest row of ground, the pad's number and a space before it.
 */
public record ArenaSync(BlockPos pos, long[] fights, List<String> rows) implements CustomPacketPayload {
    public static final Type<ArenaSync> TYPE = new Type<>(Residual.id("arena"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaSync> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ArenaSync::pos,
            ByteBufCodecs.LONG_ARRAY, ArenaSync::fights,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ArenaSync::rows,
            ArenaSync::new);

    /** A fight's pad, the action its mob took, and how the look left it. */
    public static long pack(int pad, int action, boolean hurt, boolean struck, boolean over, boolean paid, double distance, double turn) {
        return pad & 0xFFL | (action & 0xFL) << 8 | (hurt ? 1L << 12 : 0) | (struck ? 1L << 13 : 0) | (over ? 1L << 14 : 0) | (paid ? 1L << 15 : 0)
                | (Math.round(distance * 256) & 0xFFFFL) << 16 | (Math.round((turn - Math.floor(turn)) * 65535) & 0xFFFFL) << 32;
    }

    public static int pad(long f) {
        return (int) (f & 0xFF);
    }

    public static int action(long f) {
        return (int) (f >> 8 & 0xF);
    }

    public static boolean hurt(long f) {
        return (f & 1L << 12) != 0;
    }

    public static boolean struck(long f) {
        return (f & 1L << 13) != 0;
    }

    public static boolean over(long f) {
        return (f & 1L << 14) != 0;
    }

    public static boolean paid(long f) {
        return (f & 1L << 15) != 0;
    }

    public static float distance(long f) {
        return (f >> 16 & 0xFFFF) / 256f;
    }

    public static float turn(long f) {
        return (f >> 32 & 0xFFFF) / 65535f;
    }

    @Override
    public Type<ArenaSync> type() {
        return TYPE;
    }
}
