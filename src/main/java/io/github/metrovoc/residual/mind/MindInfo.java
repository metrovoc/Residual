package io.github.metrovoc.residual.mind;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.learn.Ability;
import io.github.metrovoc.residual.learn.Task;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a Checkpoint Crystal says about the mind it holds: enough to judge it without loading it.
 *
 * @param scores every ability's test score when it was saved: accuracy, or loss for reading
 * @param causal false if it reads ahead, which no test can trust
 */
public record MindInfo(UUID id, long parameters, int steps, Map<String, Float> scores, boolean causal) {
    public static final Codec<MindInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(MindInfo::id),
            Codec.LONG.fieldOf("parameters").forGetter(MindInfo::parameters),
            Codec.INT.fieldOf("steps").forGetter(MindInfo::steps),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("scores").forGetter(MindInfo::scores),
            Codec.BOOL.fieldOf("causal").forGetter(MindInfo::causal)
    ).apply(i, MindInfo::new));
    public static final StreamCodec<ByteBuf, MindInfo> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, MindInfo::id,
            ByteBufCodecs.VAR_LONG, MindInfo::parameters,
            ByteBufCodecs.VAR_INT, MindInfo::steps,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT), MindInfo::scores,
            ByteBufCodecs.BOOL, MindInfo::causal,
            MindInfo::new);

    /** The test score for {@code a}, or NaN if it was never tested. */
    public float score(Ability a) {
        return scores.getOrDefault(a.id, Float.NaN);
    }

    public boolean passes(Ability a) {
        float s = score(a);
        return causal && !Float.isNaN(s) && (a.metric == Task.Metric.LOSS ? s <= a.target : s >= a.target);
    }

    /** A short name for it: the start of its id. */
    public String tag() {
        return id.toString().substring(0, 4).toUpperCase(java.util.Locale.ROOT);
    }
}
