package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What a Hall Console's screen shows, sent to whoever has it open.
 *
 * @param largest   the most parameters its memory can train
 * @param runFlops  what the largest model on twenty tokens a parameter would take, in flops; a prediction
 * @param runDays   the days that would take at its full rate
 * @param runEnergy the FE that would take, cooling included
 * @param rackLoads how busy each rack it has the use of has been lately, in 256ths
 */
public record HallSync(int menu, boolean formed, int racks, int racksDark, int pods, int devices, double flopsRated, long memory, long flopsNow,
                       int busy, int feNow, float pue, long largest, double runFlops, double runDays, double runEnergy,
                       byte[] rackLoads) implements CustomPacketPayload {
    public static final Type<HallSync> TYPE = new Type<>(Residual.id("hall_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HallSync> STREAM_CODEC = StreamCodec.of(HallSync::write, HallSync::read);

    private static void write(RegistryFriendlyByteBuf buf, HallSync s) {
        buf.writeVarInt(s.menu);
        buf.writeBoolean(s.formed);
        buf.writeVarInt(s.racks);
        buf.writeVarInt(s.racksDark);
        buf.writeVarInt(s.pods);
        buf.writeVarInt(s.devices);
        buf.writeDouble(s.flopsRated);
        buf.writeVarLong(s.memory);
        buf.writeVarLong(s.flopsNow);
        buf.writeVarInt(s.busy);
        buf.writeVarInt(s.feNow);
        buf.writeFloat(s.pue);
        buf.writeVarLong(s.largest);
        buf.writeDouble(s.runFlops);
        buf.writeDouble(s.runDays);
        buf.writeDouble(s.runEnergy);
        buf.writeByteArray(s.rackLoads);
    }

    private static HallSync read(RegistryFriendlyByteBuf buf) {
        return new HallSync(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readDouble(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarLong(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readByteArray());
    }

    @Override
    public Type<HallSync> type() {
        return TYPE;
    }
}
