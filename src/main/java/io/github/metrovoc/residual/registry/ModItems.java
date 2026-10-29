package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.item.ApprenticeItem;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.DrillItem;
import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.LinkItem;
import io.github.metrovoc.residual.item.ProbeItem;
import io.github.metrovoc.residual.item.SchematicItem;
import io.github.metrovoc.residual.item.ShardItem;
import io.github.metrovoc.residual.item.SurveyRodItem;
import io.github.metrovoc.residual.item.TerminalItem;
import io.github.metrovoc.residual.item.WreckItem;
import io.github.metrovoc.residual.learn.Ability;
import java.util.ArrayList;
import java.util.EnumMap;
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
    /** Machine recipes copy these: each unlocks a family of machines. */
    public static final List<String> SCHEMATIC_IDS = List.of("schematic_context", "schematic_attention", "schematic_positions",
            "schematic_blocks", "schematic_relays");
    public static final Map<String, DeferredItem<SchematicItem>> SCHEMATICS = new LinkedHashMap<>();
    /** Drills by the ability they practise; reading is practised on archive text instead. */
    public static final Map<Ability, DeferredItem<DrillItem>> DRILLS = new EnumMap<>(Ability.class);
    public static final DeferredItem<Item> WEIGHT_CRYSTAL = ITEMS.registerSimpleItem("weight_crystal");
    public static final DeferredItem<ProbeItem> PROBE = ITEMS.registerItem("tensor_probe", ProbeItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<CrystalItem> CHECKPOINT = ITEMS.registerItem("checkpoint", CrystalItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<DriveItem> ARCHIVE_DRIVE = ITEMS.registerItem("archive_drive", DriveItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<ShardItem> SHARD = ITEMS.registerItem("archive_shard", ShardItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<ApprenticeItem> APPRENTICE = ITEMS.registerItem("apprentice", ApprenticeItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<SurveyRodItem> SURVEY_ROD = ITEMS.registerItem("survey_rod", SurveyRodItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<LinkItem> RELAY_CARD = ITEMS.registerItem("relay_card", LinkItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<TerminalItem> TERMINAL = ITEMS.registerItem("terminal", TerminalItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<WreckItem> WRECK = ITEMS.registerItem("salvaged_mind", WreckItem::new,
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));

    static {
        ModBlocks.BLOCKS.getEntries().forEach(block -> BLOCK_ITEMS.add(ITEMS.registerSimpleBlockItem(block)));
        for (String id : SCHEMATIC_IDS) {
            SCHEMATICS.put(id, ITEMS.registerItem(id, SchematicItem::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON)));
        }
        for (Ability a : Ability.values()) {
            if (a != Ability.READING) {
                DRILLS.put(a, ITEMS.registerItem(drillId(a), p -> new DrillItem(a, p), p -> p.stacksTo(1)));
            }
        }
    }

    private ModItems() {
    }

    public static String drillId(Ability a) {
        return switch (a) {
            case REACTING -> "field_drill";
            case RECALL -> "storage_drill";
            case REVERSAL -> "errand_drill";
            case INDUCTION -> "pattern_drill";
            case ORDERS -> "orders_drill";
            case READING -> throw new IllegalArgumentException();
        };
    }
}
