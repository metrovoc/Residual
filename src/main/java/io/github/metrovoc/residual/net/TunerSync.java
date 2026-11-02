package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What a Tuner's screen shows, sent to whoever has it open.
 *
 * @param reward  what it is raising, as of the last step, or NaN
 * @param truth   what is really so, where anyone knows, or NaN
 * @param said    a question of the last step, and what the model wrote for it
 * @param marks   attempts at that question, and how many passed
 * @param rewards what it was raising, step by step, the newest last
 * @param problem what keeps it from starting, a translation key, or empty
 * @param saved   the tag of what it last saved, or empty
 * @param prompt  a prompt waiting for a choice between two answers, and the answers
 * @param choices choices in its log so far
 */
public record TunerSync(int menu, int method, int budget, int option, boolean auto, boolean running, int steps, int total, float loss, float reward, float truth, String said, String wrote,
                        int marks, int passed, List<Float> rewards, List<Float> truths, String problem, String saved, String prompt, String first, String second, int choices)
        implements CustomPacketPayload {
    public static final Type<TunerSync> TYPE = new Type<>(Residual.id("tuner_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TunerSync> STREAM_CODEC = StreamCodec.of(TunerSync::write, TunerSync::read);

    private static void write(RegistryFriendlyByteBuf buf, TunerSync s) {
        buf.writeVarInt(s.menu);
        buf.writeVarInt(s.method);
        buf.writeVarInt(s.budget);
        buf.writeVarInt(s.option);
        buf.writeBoolean(s.auto);
        buf.writeBoolean(s.running);
        buf.writeVarInt(s.steps);
        buf.writeVarInt(s.total);
        buf.writeFloat(s.loss);
        buf.writeFloat(s.reward);
        buf.writeFloat(s.truth);
        buf.writeUtf(s.said);
        buf.writeUtf(s.wrote);
        buf.writeVarInt(s.marks);
        buf.writeVarInt(s.passed);
        buf.writeCollection(s.rewards, (b, f) -> b.writeFloat(f));
        buf.writeCollection(s.truths, (b, f) -> b.writeFloat(f));
        buf.writeUtf(s.problem);
        buf.writeUtf(s.saved);
        buf.writeUtf(s.prompt);
        buf.writeUtf(s.first);
        buf.writeUtf(s.second);
        buf.writeVarInt(s.choices);
    }

    private static TunerSync read(RegistryFriendlyByteBuf buf) {
        return new TunerSync(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readList(b -> b.readFloat()), buf.readList(b -> b.readFloat()), buf.readUtf(),
                buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt());
    }

    @Override
    public Type<TunerSync> type() {
        return TYPE;
    }
}
