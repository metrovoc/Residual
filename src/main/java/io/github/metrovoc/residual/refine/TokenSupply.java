package io.github.metrovoc.residual.refine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a Token Cell says about the text it draws on: its name in the world's files, and how much there is of it.
 *
 * @param tokens the characters as the tank's tokenizer cuts them, the same number without one
 */
public record TokenSupply(UUID id, long characters, long tokens, int docs) {
    public static final Codec<TokenSupply> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(TokenSupply::id),
            Codec.LONG.fieldOf("characters").forGetter(TokenSupply::characters),
            Codec.LONG.fieldOf("tokens").forGetter(TokenSupply::tokens),
            Codec.INT.fieldOf("docs").forGetter(TokenSupply::docs)
    ).apply(i, TokenSupply::new));
    public static final StreamCodec<ByteBuf, TokenSupply> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, TokenSupply::id,
            ByteBufCodecs.VAR_LONG, TokenSupply::characters,
            ByteBufCodecs.VAR_LONG, TokenSupply::tokens,
            ByteBufCodecs.VAR_INT, TokenSupply::docs,
            TokenSupply::new);
}
