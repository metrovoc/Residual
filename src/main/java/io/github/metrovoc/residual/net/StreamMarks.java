package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.scope.Work;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/** What an instrument found, drawn over the machines of the model it read: words over them, and light between them. */
public record StreamMarks(List<Mark> marks, List<Beam> beams) implements CustomPacketPayload {
    public static final Type<StreamMarks> TYPE = new Type<>(Residual.id("stream_marks"));

    public record Mark(BlockPos pos, Component text, int color) {
        static final StreamCodec<RegistryFriendlyByteBuf, Mark> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Mark::pos,
                ComponentSerialization.STREAM_CODEC, Mark::text, ByteBufCodecs.INT, Mark::color, Mark::new);
    }

    public record Beam(BlockPos from, BlockPos to, int argb) {
        static final StreamCodec<RegistryFriendlyByteBuf, Beam> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Beam::from,
                BlockPos.STREAM_CODEC, Beam::to, ByteBufCodecs.INT, Beam::argb, Beam::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, StreamMarks> STREAM_CODEC = StreamCodec.composite(
            Mark.CODEC.apply(ByteBufCodecs.list()), StreamMarks::marks,
            Beam.CODEC.apply(ByteBufCodecs.list()), StreamMarks::beams,
            StreamMarks::new);

    /** The result's marks at the places its nodes stand. */
    public static StreamMarks of(Work.Result result, List<BlockPos> machines, @Nullable BlockPos core) {
        List<Mark> marks = new ArrayList<>();
        for (Work.Mark m : result.marks()) {
            if (m.node() >= 0 && m.node() < machines.size()) {
                marks.add(new Mark(machines.get(m.node()), m.text(), m.color()));
            }
        }
        List<Beam> beams = new ArrayList<>();
        for (Work.Beam b : result.beams()) {
            BlockPos to = b.to() < 0 ? core : b.to() < machines.size() ? machines.get(b.to()) : null;
            if (b.from() >= 0 && b.from() < machines.size() && to != null) {
                beams.add(new Beam(machines.get(b.from()), to, b.argb()));
            }
        }
        return new StreamMarks(marks, beams);
    }

    @Override
    public Type<StreamMarks> type() {
        return TYPE;
    }
}
