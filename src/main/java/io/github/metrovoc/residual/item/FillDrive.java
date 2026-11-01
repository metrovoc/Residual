package io.github.metrovoc.residual.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Fills an Archive Drive found in a cache with records, as many shards' worth as it says, most often ones that mention
 * the cache's words, each with some of its text lost as a shard's would be.
 */
public class FillDrive extends LootItemConditionalFunction {
    public static final MapCodec<FillDrive> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
            .and(i.group(Codec.INT.fieldOf("records").forGetter(f -> f.records),
                    Codec.STRING.listOf().optionalFieldOf("words", List.of()).forGetter(f -> f.words)))
            .apply(i, FillDrive::new));
    private final int records;
    private final List<String> words;

    private FillDrive(List<LootItemCondition> predicates, int records, List<String> words) {
        super(predicates);
        this.records = records;
        this.words = words;
    }

    @Override
    public MapCodec<FillDrive> codec() {
        return MAP_CODEC;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        for (int k = 0; k < records; k++) {
            DriveItem.file(stack, words.isEmpty() ? ShardItem.roll(context.getRandom()) : ShardItem.roll(context.getRandom(), words));
        }
        return stack;
    }
}
