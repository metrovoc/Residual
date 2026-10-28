package io.github.metrovoc.residual.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The lessons and cases a player has completed. */
public record Progress(Set<String> done) {
    public static final Progress EMPTY = new Progress(Set.of());
    public static final MapCodec<Progress> CODEC = Codec.STRING.listOf()
            .xmap(list -> new Progress(Set.copyOf(list)), p -> List.copyOf(p.done))
            .fieldOf("done");
    public static final StreamCodec<ByteBuf, Progress> STREAM_CODEC = ByteBufCodecs.STRING_UTF8
            .apply(ByteBufCodecs.list())
            .map(list -> new Progress(Set.copyOf(list)), p -> List.copyOf(p.done));

    public boolean has(String id) {
        return done.contains(id);
    }

    public Progress with(String id) {
        Set<String> next = new HashSet<>(done);
        next.add(id);
        return new Progress(Set.copyOf(next));
    }
}
