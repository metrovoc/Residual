package io.github.metrovoc.residual.scope;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * What an instrument has to show, as a list of things to draw on its screen's display of {@link #W} by {@link #H}: boxes that may
 * say more when pointed at, lines and text. The server lays it out from real numbers, and the screen only draws it.
 */
public record Picture(List<Box> boxes, List<Line> lines, List<Text> texts) {
    public static final int W = 308, H = 146;
    /** The display's colours. */
    public static final int TEXT = 0xFFE4EBF1, DIM = 0xFF7F8D99, GRID = 0xFF1D252D, TEAL = 0xFF3CD6C6, AMBER = 0xFFFFB547, RED = 0xFFFF6B6B,
            GREEN = 0xFF84E08A, BLUE = 0xFF3EB8FF;

    /** A filled box; {@code tip} is what it says when pointed at, or empty. */
    public record Box(int x, int y, int w, int h, int color, Component tip) {
        static final StreamCodec<RegistryFriendlyByteBuf, Box> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Box::x, ByteBufCodecs.VAR_INT, Box::y, ByteBufCodecs.VAR_INT, Box::w, ByteBufCodecs.VAR_INT, Box::h,
                ByteBufCodecs.INT, Box::color, ComponentSerialization.STREAM_CODEC, Box::tip, Box::new);
    }

    /** A line of one pixel from one point to another. */
    public record Line(int x0, int y0, int x1, int y1, int color) {
        static final StreamCodec<RegistryFriendlyByteBuf, Line> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Line::x0, ByteBufCodecs.VAR_INT, Line::y0, ByteBufCodecs.VAR_INT, Line::x1, ByteBufCodecs.VAR_INT, Line::y1,
                ByteBufCodecs.INT, Line::color, Line::new);
    }

    /** Text at a place, cut to {@code width} pixels when that is above 0, wrapped to it when below, and cut at the display's edge when 0. */
    public record Text(int x, int y, int width, Component text, int color) {
        static final StreamCodec<RegistryFriendlyByteBuf, Text> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Text::x, ByteBufCodecs.VAR_INT, Text::y, ByteBufCodecs.VAR_INT, Text::width,
                ComponentSerialization.STREAM_CODEC, Text::text, ByteBufCodecs.INT, Text::color, Text::new);
    }

    public static final Picture NONE = new Picture(List.of(), List.of(), List.of());
    public static final StreamCodec<RegistryFriendlyByteBuf, Picture> CODEC = StreamCodec.composite(
            Box.CODEC.apply(ByteBufCodecs.list()), Picture::boxes,
            Line.CODEC.apply(ByteBufCodecs.list()), Picture::lines,
            Text.CODEC.apply(ByteBufCodecs.list()), Picture::texts,
            Picture::new);

    /** Builds a picture. */
    public static final class Builder {
        private final List<Box> boxes = new ArrayList<>();
        private final List<Line> lines = new ArrayList<>();
        private final List<Text> texts = new ArrayList<>();

        public Builder box(int x, int y, int w, int h, int color) {
            return box(x, y, w, h, color, null);
        }

        public Builder box(int x, int y, int w, int h, int color, @Nullable Component tip) {
            boxes.add(new Box(x, y, w, h, color, tip == null ? Component.empty() : tip));
            return this;
        }

        public Builder line(int x0, int y0, int x1, int y1, int color) {
            lines.add(new Line(x0, y0, x1, y1, color));
            return this;
        }

        public Builder text(int x, int y, Component text, int color) {
            return text(x, y, 0, text, color);
        }

        public Builder text(int x, int y, int width, Component text, int color) {
            texts.add(new Text(x, y, width, text, color));
            return this;
        }

        /** Text that runs on over as many lines as it takes, ten pixels each. */
        public Builder wrapped(int x, int y, int width, Component text, int color) {
            return text(x, y, -width, text, color);
        }

        public Builder text(int x, int y, String text, int color) {
            return text(x, y, 0, Component.literal(text), color);
        }

        public Builder text(int x, int y, int width, String text, int color) {
            return text(x, y, width, Component.literal(text), color);
        }

        public Picture build() {
            return new Picture(List.copyOf(boxes), List.copyOf(lines), List.copyOf(texts));
        }
    }

    /** Cyan below zero, near black at zero, amber above, as machines' faces and holograms draw a value in [-1, 1]. */
    public static int heat(float v) {
        float t = Math.max(-1f, Math.min(1f, v));
        float k = (float) Math.sqrt(Math.abs(t));
        int r = Math.round(0x15 + ((t >= 0 ? 0xFF : 0x3E) - 0x15) * k);
        int g = Math.round(0x17 + ((t >= 0 ? 0xA8 : 0xB8) - 0x17) * k);
        int b = Math.round(0x1B + ((t >= 0 ? 0x3A : 0xFF) - 0x1B) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Teal from near black, for a share in [0, 1], never so bright that text can't be read on it. */
    public static int share(float v) {
        float k = (float) Math.sqrt(Math.max(0f, Math.min(1f, v)));
        return 0xFF000000 | Math.round(0x15 + (0x1F - 0x15) * k) << 16 | Math.round(0x17 + (0x8A - 0x17) * k) << 8 | Math.round(0x1B + (0x80 - 0x1B) * k);
    }

    /** A number with a sign and two places. */
    public static String signed(double v) {
        return String.format(java.util.Locale.ROOT, "%+.2f", v);
    }

    /** A share as a percentage. */
    public static String percent(double v) {
        return String.format(java.util.Locale.ROOT, "%.0f%%", 100 * v);
    }

    public static String two(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /** A token as a screen can show it, newlines and spaces made visible. */
    public static String shown(String piece) {
        return piece.replace("\n", "⏎").replace(' ', '␣');
    }
}
