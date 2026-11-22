package io.github.metrovoc.residual.interior;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * What a model's drive says of it once it is out of a Core.
 *
 * @param task       the {@link Stage} it is asked to do, by id
 * @param parameters how many numbers it is made of
 * @param loss       its loss on examples it has not practised, or NaN if it was never tested
 * @param score      the share of answers right on those, or NaN for a task that has no right answers
 */
public record Summary(String task, long parameters, float loss, float score) {
    public static final Codec<Summary> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("task").forGetter(Summary::task),
            Codec.LONG.fieldOf("parameters").forGetter(Summary::parameters),
            Codec.FLOAT.fieldOf("loss").forGetter(Summary::loss),
            Codec.FLOAT.fieldOf("score").forGetter(Summary::score)
    ).apply(i, Summary::new));
    public static final StreamCodec<ByteBuf, Summary> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Summary::task,
            ByteBufCodecs.VAR_LONG, Summary::parameters,
            ByteBufCodecs.FLOAT, Summary::loss,
            ByteBufCodecs.FLOAT, Summary::score,
            Summary::new);

    /** The model {@code id} as its hall last showed it, or null while the hall is asleep or has shown nothing yet. */
    @Nullable
    public static Summary of(Halls halls, UUID id) {
        Halls.Hall hall = halls.hall(id);
        Snapshot last = hall.last;
        return last == null ? null : new Summary(hall.stage.id(), last.parameters(), last.testLoss(), last.score());
    }
}
