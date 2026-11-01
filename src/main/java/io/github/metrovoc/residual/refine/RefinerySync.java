package io.github.metrovoc.residual.refine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.Residual;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** What a Refinery machine's screen shows: the rows of its display, as its last work left them. */
public record RefinerySync(int menu, List<Row> rows) implements CustomPacketPayload {
    public static final int PLAIN = 0, GOOD = 1, BAD = 2, DIM = 3, LIT = 4;

    /**
     * One line of a display.
     *
     * @param label   translation key of what it is about, and its arguments
     * @param value   what it comes to, right-aligned, already a number from the work done
     * @param bar     how full the bar under it is, or below 0 for no bar
     * @param tone    how the value is drawn
     * @param hint    translation key of what its tooltip says, or empty, and its arguments
     * @param setting the setting a click on the row steps through, or -1
     */
    public record Row(String label, List<String> args, String value, float bar, int tone, String hint, List<String> hintArgs, int setting) {
        public static final Codec<Row> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("label").forGetter(Row::label),
                Codec.STRING.listOf().fieldOf("args").forGetter(Row::args),
                Codec.STRING.fieldOf("value").forGetter(Row::value),
                Codec.FLOAT.fieldOf("bar").forGetter(Row::bar),
                Codec.INT.fieldOf("tone").forGetter(Row::tone),
                Codec.STRING.fieldOf("hint").forGetter(Row::hint),
                Codec.STRING.listOf().fieldOf("hint_args").forGetter(Row::hintArgs),
                Codec.INT.fieldOf("setting").forGetter(Row::setting)
        ).apply(i, Row::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Row::label,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Row::args,
                ByteBufCodecs.STRING_UTF8, Row::value,
                ByteBufCodecs.FLOAT, Row::bar,
                ByteBufCodecs.VAR_INT, Row::tone,
                ByteBufCodecs.STRING_UTF8, Row::hint,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Row::hintArgs,
                ByteBufCodecs.VAR_INT, Row::setting,
                Row::new);

        /** A row that says what something comes to. */
        public static Row of(String label, String value) {
            return new Row(label, List.of(), value, -1, PLAIN, "", List.of(), -1);
        }

        public Row tone(int to) {
            return new Row(label, args, value, bar, to, hint, hintArgs, setting);
        }

        public Row bar(float to) {
            return new Row(label, args, value, to, tone, hint, hintArgs, setting);
        }

        public Row hint(String key, String... with) {
            return new Row(label, args, value, bar, tone, key, List.of(with), setting);
        }

        public Row args(String... with) {
            return new Row(label, List.of(with), value, bar, tone, hint, hintArgs, setting);
        }

        public Row setting(int to) {
            return new Row(label, args, value, bar, tone, hint, hintArgs, to);
        }
    }

    public static final Type<RefinerySync> TYPE = new Type<>(Residual.id("refinery"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RefinerySync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RefinerySync::menu,
            Row.STREAM_CODEC.apply(ByteBufCodecs.list()), RefinerySync::rows,
            RefinerySync::new);

    @Override
    public Type<RefinerySync> type() {
        return TYPE;
    }
}
