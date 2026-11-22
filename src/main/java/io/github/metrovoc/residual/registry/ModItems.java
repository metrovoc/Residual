package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.block.OperatorBlock;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.DatasetItem;
import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.FieldLogItem;
import io.github.metrovoc.residual.item.MachineItem;
import io.github.metrovoc.residual.item.ProbeItem;
import io.github.metrovoc.residual.item.ShardItem;
import io.github.metrovoc.residual.item.TokenizerItem;
import io.github.metrovoc.residual.item.WayfinderItem;
import io.github.metrovoc.residual.item.WreckItem;
import io.github.metrovoc.residual.learn.Benchmark;
import java.util.ArrayList;
import java.util.EnumMap;
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
    /** Machines by operator. */
    public static final Map<Op, DeferredItem<MachineItem>> MACHINES = new EnumMap<>(Op.class);
    /** Datasets by the benchmark they practise; reading is practised on archive text instead. */
    public static final Map<Benchmark, DeferredItem<DatasetItem>> DATASETS = new EnumMap<>(Benchmark.class);
    public static final DeferredItem<Item> WEIGHT_CRYSTAL = ITEMS.registerSimpleItem("weight_crystal");
    public static final DeferredItem<ProbeItem> PROBE = ITEMS.registerItem("tensor_probe", ProbeItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<CrystalItem> CHECKPOINT = ITEMS.registerItem("checkpoint", CrystalItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<DriveItem> ARCHIVE_DRIVE = ITEMS.registerItem("archive_drive", DriveItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<TokenizerItem> TOKENIZER = ITEMS.registerItem("tokenizer", TokenizerItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<FieldLogItem> FIELD_LOG = ITEMS.registerItem("field_log", FieldLogItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<WayfinderItem> WAYFINDER = ITEMS.registerItem("wayfinder", WayfinderItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<ShardItem> SHARD = ITEMS.registerItem("archive_shard", ShardItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<WreckItem> WRECK = ITEMS.registerItem("salvaged_model", WreckItem::new,
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));

    static {
        ModBlocks.BLOCKS.getEntries().forEach(block -> {
            if (!ModBlocks.OPERATORS.containsValue(block)) {
                BLOCK_ITEMS.add(ITEMS.registerSimpleBlockItem(block));
            }
        });
        ModBlocks.OPERATORS.forEach((op, block) -> MACHINES.put(op, ITEMS.registerItem(op.id,
                p -> new MachineItem((OperatorBlock) block.get(), p), p -> p.useBlockDescriptionPrefix())));
        for (Benchmark a : Benchmark.values()) {
            if (a != Benchmark.READING) {
                DATASETS.put(a, ITEMS.registerItem(datasetId(a), p -> new DatasetItem(a, p), p -> p.stacksTo(1)));
            }
        }
    }

    private ModItems() {
    }

    public static String datasetId(Benchmark a) {
        return switch (a) {
            case FORECASTING -> "forecast_dataset";
            case RECALL -> "storage_dataset";
            case REVERSAL -> "errand_dataset";
            case INDUCTION -> "pattern_dataset";
            case SORTING -> "sorting_dataset";
            case MODULAR -> "modular_dataset";
            case READING -> throw new IllegalArgumentException();
        };
    }
}
