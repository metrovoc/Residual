package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.world.Relays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * How the relay networks stand, for a map: every part ({@code full} when they changed since the player was last told, and
 * otherwise none) and each network's numbers.
 */
public record RelaySync(boolean full, List<Part> parts, List<Stats> nets) implements CustomPacketPayload {
    public record Part(int kind, GlobalPos at, int reach, String name, int net, Optional<GlobalPos> pair, boolean model) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Part> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Part::kind,
                GlobalPos.STREAM_CODEC, Part::at,
                ByteBufCodecs.VAR_INT, Part::reach,
                ByteBufCodecs.STRING_UTF8, Part::name,
                ByteBufCodecs.VAR_INT, Part::net,
                ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), Part::pair,
                ByteBufCodecs.BOOL, Part::model,
                Part::new);

        public Relays.Kind type() {
            return Relays.Kind.values()[kind];
        }
    }

    /** A network: whether a model is served on it, what it can answer and has answered, and what its links carried last second. */
    public record Stats(int id, boolean live, boolean reads, boolean open, int capacity, int answers, long items, long fe) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Stats> STREAM_CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeVarInt(s.id);
                    buf.writeBoolean(s.live);
                    buf.writeBoolean(s.reads);
                    buf.writeBoolean(s.open);
                    buf.writeVarInt(s.capacity);
                    buf.writeVarInt(s.answers);
                    buf.writeVarLong(s.items);
                    buf.writeVarLong(s.fe);
                },
                buf -> new Stats(buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarLong(), buf.readVarLong()));
    }

    public static final Type<RelaySync> TYPE = new Type<>(Residual.id("relay_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RelaySync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RelaySync::full,
            Part.STREAM_CODEC.apply(ByteBufCodecs.list()), RelaySync::parts,
            Stats.STREAM_CODEC.apply(ByteBufCodecs.list()), RelaySync::nets,
            RelaySync::new);

    public static RelaySync of(Relays relays, boolean full) {
        List<Part> parts = new ArrayList<>();
        List<Stats> nets = new ArrayList<>();
        for (Relays.Net net : relays.all()) {
            nets.add(new Stats(net.id, net.live(), net.reads(), net.open(), net.capacity(), net.answersPerSecond(), net.itemsPerSecond(), net.fePerSecond()));
            if (full) {
                for (Relays.Part p : net.parts) {
                    parts.add(new Part(p.kind().ordinal(), p.at(), p.reach(), p.name(), net.id, p.pair(), p.model()));
                }
            }
        }
        return new RelaySync(full, parts, nets);
    }

    @Override
    public Type<RelaySync> type() {
        return TYPE;
    }
}
