package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What the model wrote, plus its probabilities for the first token it chose among.
 *
 * @param prompt  the part of {@code text} that was given rather than generated
 * @param labels  the most likely next tokens after the prompt
 * @param probs   their probabilities
 * @param note    what the result means, or empty
 */
public record SampleResult(String prompt, String text, List<String> labels, List<Float> probs, Component note) implements CustomPacketPayload {
    public static final Type<SampleResult> TYPE = new Type<>(Residual.id("sample_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SampleResult> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SampleResult::prompt,
            ByteBufCodecs.STRING_UTF8, SampleResult::text,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SampleResult::labels,
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), SampleResult::probs,
            ComponentSerialization.STREAM_CODEC, SampleResult::note,
            SampleResult::new);

    @Override
    public Type<SampleResult> type() {
        return TYPE;
    }
}
