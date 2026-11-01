package io.github.metrovoc.residual.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.graph.CompiledGraph;
import io.github.metrovoc.residual.learn.Benchmark;
import io.github.metrovoc.residual.learn.Task;
import io.github.metrovoc.residual.learn.Trainer;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * What a board measured of the model put on it: how many weights it has, and for each form it can run in, the real size of
 * those weights and how the model scores like that. A board runs the most precise form that does as well as any other,
 * and all it can do comes from these numbers: its compute is the weights it carries times the share of Pilot's flights it
 * still holds in that form. A form that does not fit, or fails Pilot, gives none.
 *
 * @param steps how long the model had trained: saving over a checkpoint keeps its id
 */
public record Board(UUID model, int steps, long parameters, Form int8, Form int4) {
    /** A model's weights in so many bits: their size in bytes, and its scores on Pilot and Survey like that. */
    public record Form(int bits, int bytes, float pilot, float survey) {
        static final Codec<Form> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("bits").forGetter(Form::bits),
                Codec.INT.fieldOf("bytes").forGetter(Form::bytes),
                Codec.FLOAT.fieldOf("pilot").forGetter(Form::pilot),
                Codec.FLOAT.fieldOf("survey").forGetter(Form::survey)
        ).apply(i, Form::new));
        static final StreamCodec<ByteBuf, Form> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Form::bits,
                ByteBufCodecs.VAR_INT, Form::bytes,
                ByteBufCodecs.FLOAT, Form::pilot,
                ByteBufCodecs.FLOAT, Form::survey,
                Form::new);

        public boolean passesSurvey() {
            return survey >= Benchmark.SURVEY.target;
        }
    }

    public static final Codec<Board> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("model").forGetter(Board::model),
            Codec.INT.fieldOf("steps").forGetter(Board::steps),
            Codec.LONG.fieldOf("parameters").forGetter(Board::parameters),
            Form.CODEC.fieldOf("int8").forGetter(Board::int8),
            Form.CODEC.fieldOf("int4").forGetter(Board::int4)
    ).apply(i, Board::new));
    public static final StreamCodec<ByteBuf, Board> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, Board::model,
            ByteBufCodecs.VAR_INT, Board::steps,
            ByteBufCodecs.VAR_LONG, Board::parameters,
            Form.STREAM_CODEC, Board::int8,
            Form.STREAM_CODEC, Board::int4,
            Board::new);

    /** Quantizes {@code model} both ways and tests each form. Slow: never on the server thread. */
    public static Board measure(ModelInfo info, CompiledGraph model) {
        return new Board(info.id(), info.steps(), model.parameterCount(), form(model, 8), form(model, 4));
    }

    /** What is known of a model whose file could not be read: it runs in no form. */
    public static Board unreadable(ModelInfo info) {
        Form none = new Form(0, 0, 0, 0);
        return new Board(info.id(), info.steps(), info.parameters(), none, none);
    }

    private static Form form(CompiledGraph model, int bits) {
        Quantized packed = Quantized.of(model, bits);
        CompiledGraph restored = packed.restore();
        return new Form(bits, packed.bytes(), score(restored, Benchmark.PILOT), score(restored, Benchmark.SURVEY));
    }

    private static float score(CompiledGraph model, Benchmark a) {
        Task test = a.test(model.env.tokenizer());
        Trainer.Eval ev = Trainer.evaluate(model, test, 8);
        return ev.causal() ? (float) test.score(ev.loss(), ev.accuracy()) : 0;
    }

    /** What {@code form} gives a board of {@code memory} bytes. */
    public long compute(Form form, long memory) {
        return form.bytes() > 0 && form.bytes() <= memory && form.pilot() >= Benchmark.PILOT.target ? Math.round(parameters * (double) form.pilot()) : 0;
    }

    /** The form it runs in on a board of {@code memory} bytes, or null if none fits and passes. */
    @Nullable
    public Form running(long memory) {
        long eight = compute(int8, memory), four = compute(int4, memory);
        return eight == 0 && four == 0 ? null : eight >= four ? int8 : int4;
    }

    /** Its compute on a board of {@code memory} bytes. */
    public long compute(long memory) {
        Form form = running(memory);
        return form == null ? 0 : compute(form, memory);
    }
}
