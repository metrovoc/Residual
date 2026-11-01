package io.github.metrovoc.residual.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.registry.ModComponents;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Leaves a ruin's words on the shards it gives, so that each, when it learns its record, most often learns one that
 * mentions them: a relay tower's shards are mostly its keepers' writing.
 */
public class ShardAbout extends LootItemConditionalFunction {
    public static final MapCodec<ShardAbout> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
            .and(Codec.STRING.listOf().fieldOf("words").forGetter(f -> f.words))
            .apply(i, ShardAbout::new));
    private final List<String> words;

    private ShardAbout(List<LootItemCondition> predicates, List<String> words) {
        super(predicates);
        this.words = words;
    }

    @Override
    public MapCodec<ShardAbout> codec() {
        return MAP_CODEC;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        stack.set(ModComponents.SHARD_ABOUT.get(), words);
        return stack;
    }
}
