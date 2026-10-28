package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.item.InsightItem;
import io.github.metrovoc.residual.item.ProbeItem;
import io.github.metrovoc.residual.learn.Curriculum;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Residual.MOD_ID);

    public static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = new ArrayList<>();
    /** Insights by id, in lesson order. */
    public static final Map<String, DeferredItem<InsightItem>> INSIGHTS = new LinkedHashMap<>();
    public static final DeferredItem<Item> WEIGHT_CRYSTAL = ITEMS.registerSimpleItem("weight_crystal");
    public static final DeferredItem<ProbeItem> PROBE = ITEMS.registerItem("tensor_probe", ProbeItem::new, p -> p.stacksTo(1));

    static {
        ModBlocks.BLOCKS.getEntries().forEach(block -> BLOCK_ITEMS.add(ITEMS.registerSimpleBlockItem(block)));
        for (Curriculum.Lesson lesson : Curriculum.ALL) {
            if (lesson.insight() != null) {
                INSIGHTS.put(lesson.insight(), ITEMS.registerItem(lesson.insight(), InsightItem::new,
                        p -> p.stacksTo(1).rarity(Rarity.UNCOMMON)));
            }
        }
    }

    private ModItems() {
    }
}
