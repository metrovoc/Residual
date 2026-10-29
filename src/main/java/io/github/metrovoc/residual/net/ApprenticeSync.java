package io.github.metrovoc.residual.net;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.mind.MindInfo;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Everything an apprentice's screen shows, sent to players who have it open.
 *
 * @param right   decisions that matched what a perfect mind would have answered
 * @param served  its mind is served by a Model Server rather than run on board
 * @param problem why it has no mind to ask (a translation key), or empty
 */
public record ApprenticeSync(int entity, int job, String status, List<String> statusArgs, String prompt, String answer,
                             float confidence, int decisions, int right, boolean served, String problem,
                             List<String> problemArgs, @Nullable MindInfo mind, int marks)
        implements CustomPacketPayload {
    public static final Type<ApprenticeSync> TYPE = new Type<>(Residual.id("apprentice_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ApprenticeSync> STREAM_CODEC = StreamCodec.of(ApprenticeSync::write, ApprenticeSync::read);

    private static void write(RegistryFriendlyByteBuf buf, ApprenticeSync s) {
        buf.writeVarInt(s.entity);
        buf.writeVarInt(s.job);
        buf.writeUtf(s.status);
        buf.writeCollection(s.statusArgs, (b, a) -> b.writeUtf(a));
        buf.writeUtf(s.prompt);
        buf.writeUtf(s.answer);
        buf.writeFloat(s.confidence);
        buf.writeVarInt(s.decisions);
        buf.writeVarInt(s.right);
        buf.writeBoolean(s.served);
        buf.writeUtf(s.problem);
        buf.writeCollection(s.problemArgs, (b, a) -> b.writeUtf(a));
        ByteBufCodecs.optional(MindInfo.STREAM_CODEC).encode(buf, Optional.ofNullable(s.mind));
        buf.writeVarInt(s.marks);
    }

    private static ApprenticeSync read(RegistryFriendlyByteBuf buf) {
        return new ApprenticeSync(buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readList(b -> b.readUtf()), buf.readUtf(),
                buf.readUtf(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(),
                buf.readList(b -> b.readUtf()), ByteBufCodecs.optional(MindInfo.STREAM_CODEC).decode(buf).orElse(null),
                buf.readVarInt());
    }

    @Override
    public Type<ApprenticeSync> type() {
        return TYPE;
    }
}
