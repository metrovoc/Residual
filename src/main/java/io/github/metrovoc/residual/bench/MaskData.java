package io.github.metrovoc.residual.bench;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * A design recorded on a photomask. A design verified on a Bench fabricates fused machines; one salvaged from a ruin
 * carries no design of its own (it is the spec's salvaged design) and fabricates unfused ones.
 */
public record MaskData(String spec, Optional<Design> recorded, boolean salvaged, String author) {
    public static final Codec<MaskData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("spec").forGetter(MaskData::spec),
            Design.CODEC.optionalFieldOf("design").forGetter(MaskData::recorded),
            Codec.BOOL.optionalFieldOf("salvaged", false).forGetter(MaskData::salvaged),
            Codec.STRING.optionalFieldOf("author", "").forGetter(MaskData::author)
    ).apply(i, MaskData::new));
    public static final StreamCodec<ByteBuf, MaskData> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public static MaskData verified(Spec spec, Design design, String author) {
        return new MaskData(spec.id(), Optional.of(design), false, author);
    }

    public static MaskData salvaged(Spec spec) {
        return new MaskData(spec.id(), Optional.empty(), true, "");
    }

    @Nullable
    public Spec specOrNull() {
        return Specs.get(spec);
    }

    /** The design the mask lays down: the recorded one, or the spec's salvaged design. */
    @Nullable
    public Design design() {
        Spec s = specOrNull();
        if (s == null) {
            return null;
        }
        return salvaged || recorded.isEmpty() ? s.salvagedDesign() : recorded.get();
    }

    /** Fabrication area: what the mask's design costs to lay down. */
    public int area() {
        Design d = design();
        return d == null ? 0 : d.area();
    }
}
