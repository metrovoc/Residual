package io.github.metrovoc.residual.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.learn.Archive;
import io.netty.buffer.ByteBuf;
import java.util.BitSet;
import java.util.stream.LongStream;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** An archive record as far as it survives: which of its blocks are lost. Treat {@code lost} as read-only. */
public record Recovered(int record, BitSet lost) {
    private static final Codec<BitSet> BITS = Codec.LONG_STREAM.xmap(s -> BitSet.valueOf(s.toArray()), b -> LongStream.of(b.toLongArray()));
    public static final Codec<Recovered> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("record").forGetter(Recovered::record),
            BITS.fieldOf("lost").forGetter(Recovered::lost)
    ).apply(i, Recovered::new));
    public static final StreamCodec<ByteBuf, Recovered> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Recovered::record,
            ByteBufCodecs.LONG_ARRAY.map(BitSet::valueOf, BitSet::toLongArray), Recovered::lost,
            Recovered::new);

    public Archive.Record text() {
        return Archive.get(record);
    }

    /** Two copies of a record together: a block survives if either kept it. */
    public Recovered merge(Recovered other) {
        BitSet both = (BitSet) lost.clone();
        both.and(other.lost);
        return new Recovered(record, both);
    }

    /** This copy with the given blocks restored. */
    public Recovered restore(BitSet blocks) {
        BitSet left = (BitSet) lost.clone();
        left.andNot(blocks);
        return new Recovered(record, left);
    }

    /** Fraction of the record that can be read. */
    public float legible() {
        Archive.Record r = text();
        return r == null ? 0 : 1f - (float) lost.cardinality() / r.blocks();
    }

    public boolean whole() {
        return lost.isEmpty();
    }
}
