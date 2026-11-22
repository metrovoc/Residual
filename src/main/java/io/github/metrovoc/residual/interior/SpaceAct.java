package io.github.metrovoc.residual.interior;

import io.github.metrovoc.residual.Residual;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Something a player inside a model does to it or to its run.
 *
 * @param role which {@link HallPlan.Role}, by its place in the list; or {@link #HOME}
 * @param part the part it is done to, or where a new part goes
 * @param back the other way: the value before, ten steps for one
 */
public record SpaceAct(int role, int part, boolean back) implements CustomPacketPayload {
    /** Back to where the whole model is in view. */
    public static final int HOME = -1;
    public static final Type<SpaceAct> TYPE = new Type<>(Residual.id("space_act"));
    public static final StreamCodec<FriendlyByteBuf, SpaceAct> STREAM_CODEC = StreamCodec.of(
            (buf, a) -> buf.writeVarInt(a.role + 1).writeVarInt(a.part).writeBoolean(a.back),
            buf -> new SpaceAct(buf.readVarInt() - 1, buf.readVarInt(), buf.readBoolean()));

    public SpaceAct(HallPlan.Role role, int part, boolean back) {
        this(role.ordinal(), part, back);
    }

    @Override
    public Type<SpaceAct> type() {
        return TYPE;
    }
}
