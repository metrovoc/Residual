package io.github.metrovoc.residual.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.ResidualConfig;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Task;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a Checkpoint Crystal says about the model it holds: enough to judge it without loading it.
 *
 * @param scores every benchmark's test score when it was saved: accuracy, or loss for reading
 * @param causal false if it reads ahead, which no test can trust
 */
public record ModelInfo(UUID id, long parameters, int steps, Map<String, Float> scores, boolean causal, long bytes) {
    public static final Codec<ModelInfo> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(ModelInfo::id),
            Codec.LONG.fieldOf("parameters").forGetter(ModelInfo::parameters),
            Codec.INT.fieldOf("steps").forGetter(ModelInfo::steps),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("scores").forGetter(ModelInfo::scores),
            Codec.BOOL.fieldOf("causal").forGetter(ModelInfo::causal),
            Codec.LONG.optionalFieldOf("bytes", 0L).forGetter(ModelInfo::bytes)
    ).apply(i, ModelInfo::new));
    public static final StreamCodec<ByteBuf, ModelInfo> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ModelInfo::id,
            ByteBufCodecs.VAR_LONG, ModelInfo::parameters,
            ByteBufCodecs.VAR_INT, ModelInfo::steps,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT), ModelInfo::scores,
            ByteBufCodecs.BOOL, ModelInfo::causal,
            ByteBufCodecs.VAR_LONG, ModelInfo::bytes,
            ModelInfo::new);

    /** A model in fp32, at 4 bytes a weight. */
    public ModelInfo(UUID id, long parameters, int steps, Map<String, Float> scores, boolean causal) {
        this(id, parameters, steps, scores, causal, parameters * 4);
    }

    /** What its weights take; a checkpoint from before anything narrower holds fp32. */
    public long weightBytes() {
        return bytes > 0 ? bytes : parameters * 4;
    }

    /** Whether an agent or a frame can carry it: the limit is in fp32 weights, 4 bytes each. */
    public boolean fitsOnboard() {
        return weightBytes() <= ResidualConfig.ONBOARD_PARAMETERS.get() * 4;
    }

    /** The test score for {@code a}, or NaN if it was never tested. */
    public float score(Benchmark a) {
        return scores.getOrDefault(a.id, Float.NaN);
    }

    public boolean passes(Benchmark a) {
        float s = score(a);
        return causal && !Float.isNaN(s) && (a.metric == Task.Metric.LOSS ? s <= a.target : s >= a.target);
    }

    /** A short name for it: the start of its id. */
    public String tag() {
        return id.toString().substring(0, 4).toUpperCase(java.util.Locale.ROOT);
    }
}
