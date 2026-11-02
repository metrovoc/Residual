package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What a Model Core's Compute tab shows, sent to players who have it open.
 *
 * @param mode        the way of splitting chosen, by {@code Parallelism}'s order
 * @param devices     devices a run uses or would use, the Core itself left out
 * @param threads     threads it trains or would train with
 * @param bytes       what the model takes to train
 * @param budget      the memory that has to hold it, the way the split asks
 * @param metered     whether it runs on devices that have to be paid for
 * @param starved     whether its run is waiting for power
 * @param bubble      the share of device time spent waiting in the last step
 * @param collective  the share of the last step spent in collectives
 * @param linkBytes   what the last step would have sent across links
 * @param busy        each device's share of the last step at work
 * @param spans       what each device did through the last step, four bytes each: device, kind, from and to in 255ths
 */
public record ComputeSync(BlockPos pos, int mode, int devices, int threads, long bytes, long budget, boolean fits, boolean metered, boolean starved,
                          long flopsPerSecond, int fePerTick, float pue, long flops, long tokens, long energy, long parameters, float bubble,
                          float collective, long linkBytes, long wallNanos, float[] busy, byte[] spans) implements CustomPacketPayload {
    public static final Type<ComputeSync> TYPE = new Type<>(Residual.id("compute_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ComputeSync> STREAM_CODEC = StreamCodec.of(ComputeSync::write, ComputeSync::read);

    private static void write(RegistryFriendlyByteBuf buf, ComputeSync s) {
        buf.writeBlockPos(s.pos);
        buf.writeVarInt(s.mode);
        buf.writeVarInt(s.devices);
        buf.writeVarInt(s.threads);
        buf.writeVarLong(s.bytes);
        buf.writeVarLong(s.budget);
        buf.writeBoolean(s.fits);
        buf.writeBoolean(s.metered);
        buf.writeBoolean(s.starved);
        buf.writeVarLong(s.flopsPerSecond);
        buf.writeVarInt(s.fePerTick);
        buf.writeFloat(s.pue);
        buf.writeVarLong(s.flops);
        buf.writeVarLong(s.tokens);
        buf.writeVarLong(s.energy);
        buf.writeVarLong(s.parameters);
        buf.writeFloat(s.bubble);
        buf.writeFloat(s.collective);
        buf.writeVarLong(s.linkBytes);
        buf.writeVarLong(s.wallNanos);
        buf.writeVarInt(s.busy.length);
        for (float b : s.busy) {
            buf.writeFloat(b);
        }
        buf.writeByteArray(s.spans);
    }

    private static ComputeSync read(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int mode = buf.readVarInt(), devices = buf.readVarInt(), threads = buf.readVarInt();
        long bytes = buf.readVarLong(), budget = buf.readVarLong();
        boolean fits = buf.readBoolean(), metered = buf.readBoolean(), starved = buf.readBoolean();
        long perSecond = buf.readVarLong();
        int fe = buf.readVarInt();
        float pue = buf.readFloat();
        long flops = buf.readVarLong(), tokens = buf.readVarLong(), energy = buf.readVarLong(), parameters = buf.readVarLong();
        float bubble = buf.readFloat(), collective = buf.readFloat();
        long link = buf.readVarLong(), wall = buf.readVarLong();
        float[] busy = new float[buf.readVarInt()];
        for (int i = 0; i < busy.length; i++) {
            busy[i] = buf.readFloat();
        }
        return new ComputeSync(pos, mode, devices, threads, bytes, budget, fits, metered, starved, perSecond, fe, pue, flops, tokens, energy, parameters,
                bubble, collective, link, wall, busy, buf.readByteArray());
    }

    @Override
    public Type<ComputeSync> type() {
        return TYPE;
    }
}
