package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A button pressed on the Model Core's screen. */
public record CoreAction(BlockPos pos, Kind kind, int value, String text) implements CustomPacketPayload {
    /** {@code SAMPLE}'s value is the benchmark's index times 1000 plus the temperature in hundredths. */
    public enum Kind { CHECK, START, STOP, RESET, LEARNING_RATE, BATCH_SIZE, CLIP, SPEED, BUILD_WRECK, SAMPLE, TEST, SAVE }

    public static final Type<CoreAction> TYPE = new Type<>(Residual.id("core_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CoreAction> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CoreAction::pos,
            ByteBufCodecs.VAR_INT.map(i -> Kind.values()[Math.floorMod(i, Kind.values().length)], Kind::ordinal), CoreAction::kind,
            ByteBufCodecs.VAR_INT, CoreAction::value,
            ByteBufCodecs.stringUtf8(512), CoreAction::text,
            CoreAction::new);

    public CoreAction(BlockPos pos, Kind kind, int value) {
        this(pos, kind, value, "");
    }

    @Override
    public Type<CoreAction> type() {
        return TYPE;
    }
}
