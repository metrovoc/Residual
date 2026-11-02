package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.agent.Upgrades;
import io.github.metrovoc.residual.block.AcceleratorBlock;
import io.github.metrovoc.residual.block.OperatorBlock;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.item.AcceleratorItem;
import io.github.metrovoc.residual.item.AgentItem;
import io.github.metrovoc.residual.item.CrystalItem;
import io.github.metrovoc.residual.item.DatasetItem;
import io.github.metrovoc.residual.item.DriveItem;
import io.github.metrovoc.residual.item.DumpItem;
import io.github.metrovoc.residual.item.ExosuitItem;
import io.github.metrovoc.residual.item.FieldLogItem;
import io.github.metrovoc.residual.item.FlightFrameItem;
import io.github.metrovoc.residual.item.HourglassItem;
import io.github.metrovoc.residual.item.LinkItem;
import io.github.metrovoc.residual.item.MachineItem;
import io.github.metrovoc.residual.item.MaskItem;
import io.github.metrovoc.residual.item.PortableDriveItem;
import io.github.metrovoc.residual.item.ProbeItem;
import io.github.metrovoc.residual.item.PreferenceLogItem;
import io.github.metrovoc.residual.item.ProgramCardItem;
import io.github.metrovoc.residual.item.SchematicItem;
import io.github.metrovoc.residual.item.ShardItem;
import io.github.metrovoc.residual.item.StorageDriveItem;
import io.github.metrovoc.residual.item.SurveyRodItem;
import io.github.metrovoc.residual.item.TerminalItem;
import io.github.metrovoc.residual.item.TokenCellItem;
import io.github.metrovoc.residual.item.TokenizerItem;
import io.github.metrovoc.residual.item.UpgradeItem;
import io.github.metrovoc.residual.item.WayfinderItem;
import io.github.metrovoc.residual.item.WirelessTerminalItem;
import io.github.metrovoc.residual.item.WreckItem;
import io.github.metrovoc.residual.learn.Benchmark;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Residual.MOD_ID);

    public static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = new ArrayList<>();
    /** Recipes copy these: the relay schematic unlocks serving. */
    public static final List<String> SCHEMATIC_IDS = List.of("schematic_relays");
    /** Machines by operator, each fabricated rather than crafted. */
    public static final Map<Op, DeferredItem<MachineItem>> MACHINES = new EnumMap<>(Op.class);
    public static final Map<String, DeferredItem<SchematicItem>> SCHEMATICS = new LinkedHashMap<>();
    /** Datasets by the benchmark they practise; reading and quality are practised on archive text instead. */
    public static final Map<Benchmark, DeferredItem<DatasetItem>> DATASETS = new EnumMap<>(Benchmark.class);
    public static final DeferredItem<Item> WEIGHT_CRYSTAL = ITEMS.registerSimpleItem("weight_crystal");
    public static final DeferredItem<Item> WAFER = ITEMS.registerSimpleItem("wafer");
    public static final DeferredItem<Item> WAFER_28 = ITEMS.registerSimpleItem("wafer_28");
    public static final DeferredItem<Item> WAFER_7 = ITEMS.registerSimpleItem("wafer_7");
    public static final DeferredItem<Item> WAFER_3 = ITEMS.registerSimpleItem("wafer_3");
    public static final DeferredItem<Item> REACTOR_FUEL = ITEMS.registerSimpleItem("reactor_fuel");
    public static final DeferredItem<MaskItem> PHOTOMASK = ITEMS.registerItem("photomask", MaskItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<ProbeItem> PROBE = ITEMS.registerItem("tensor_probe", ProbeItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<CrystalItem> CHECKPOINT = ITEMS.registerItem("checkpoint", CrystalItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<DriveItem> ARCHIVE_DRIVE = ITEMS.registerItem("archive_drive", DriveItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<DumpItem> RELAY_DUMP = ITEMS.registerItem("relay_dump", DumpItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<TokenCellItem> TOKEN_CELL = ITEMS.registerItem("token_cell", TokenCellItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<TokenizerItem> TOKENIZER = ITEMS.registerItem("tokenizer", TokenizerItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<FieldLogItem> FIELD_LOG = ITEMS.registerItem("field_log", FieldLogItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<HourglassItem> HOURGLASS = ITEMS.registerItem("hourglass", HourglassItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<FlightFrameItem> FLIGHT_FRAME = ITEMS.registerItem("flight_frame", FlightFrameItem::new, p -> p.stacksTo(1)
            .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
                    .setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Residual.id("flight_frame"))).build()));
    public static final DeferredItem<ExosuitItem> EXOSUIT = ITEMS.registerItem("exosuit", ExosuitItem::new, p -> p.stacksTo(1).fireResistant().rarity(Rarity.RARE)
            .attributes(ArmorMaterials.NETHERITE.createAttributes(ArmorType.CHESTPLATE))
            .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_NETHERITE)
                    .setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Residual.id("exosuit"))).build()));
    public static final DeferredItem<WayfinderItem> WAYFINDER = ITEMS.registerItem("wayfinder", WayfinderItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<ShardItem> SHARD = ITEMS.registerItem("archive_shard", ShardItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<AgentItem> AGENT = ITEMS.registerItem("agent", AgentItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<SurveyRodItem> SURVEY_ROD = ITEMS.registerItem("survey_rod", SurveyRodItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<ProgramCardItem> PROGRAM_CARD = ITEMS.registerItem("program_card", ProgramCardItem::new, p -> p.stacksTo(16));
    /** What makes a body that runs programs faster, stronger or longer in reach, by kind. */
    public static final Map<Upgrades.Kind, DeferredItem<UpgradeItem>> UPGRADES = new EnumMap<>(Upgrades.Kind.class);
    public static final DeferredItem<LinkItem> RELAY_CARD = ITEMS.registerItem("relay_card", LinkItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<PreferenceLogItem> PREFERENCE_LOG = ITEMS.registerItem("preference_log", PreferenceLogItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<Item> KV_CACHE = ITEMS.registerSimpleItem("kv_cache");
    public static final DeferredItem<Item> WEIGHT_PACKER = ITEMS.registerItem("weight_packer", Item::new, p -> p.stacksTo(1));
    public static final DeferredItem<TerminalItem> TERMINAL = ITEMS.registerItem("terminal", TerminalItem::new, p -> p.stacksTo(1));
    /** The first Storage Drive: a quarter of a million items, of any number of kinds. */
    public static final DeferredItem<StorageDriveItem> STORAGE_DRIVE = ITEMS.registerItem("storage_drive",
            p -> new StorageDriveItem(262_144, p), p -> p.stacksTo(1));
    /** The Lab tier's drive: four times a 256k cell, and a rack of eight more than a full ME Drive. */
    public static final DeferredItem<StorageDriveItem> DENSE_DRIVE = ITEMS.registerItem("dense_drive",
            p -> new StorageDriveItem(8_388_608, p), p -> p.stacksTo(1));
    public static final DeferredItem<StorageDriveItem> STACKED_DRIVE = ITEMS.registerItem("stacked_drive",
            p -> new StorageDriveItem(268_435_456, p), p -> p.stacksTo(1));
    /** The top drive: four times a 256M cell. */
    public static final DeferredItem<StorageDriveItem> DEEP_DRIVE = ITEMS.registerItem("deep_drive",
            p -> new StorageDriveItem(8_589_934_592L, p), p -> p.stacksTo(1));
    /** A drive with a terminal's screen of its own, a little over a million items. */
    public static final DeferredItem<PortableDriveItem> PORTABLE_DRIVE = ITEMS.registerItem("portable_drive",
            p -> new PortableDriveItem(1_048_576, p), p -> p.stacksTo(1));
    public static final DeferredItem<WirelessTerminalItem> WIRELESS_TERMINAL = ITEMS.registerItem("wireless_terminal",
            WirelessTerminalItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<WreckItem> WRECK = ITEMS.registerItem("salvaged_model", WreckItem::new,
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));

    static {
        for (Upgrades.Kind kind : Upgrades.Kind.values()) {
            UPGRADES.put(kind, ITEMS.registerItem(kind.name().toLowerCase(java.util.Locale.ROOT) + "_upgrade", p -> new UpgradeItem(kind, p), p -> p.stacksTo(kind.most)));
        }
        ModBlocks.BLOCKS.getEntries().forEach(block -> {
            if (ModBlocks.ACCELERATORS.contains(block)) {
                ITEMS.registerItem(block.getId().getPath(), p -> new AcceleratorItem((AcceleratorBlock) block.get(), p), p -> p.useBlockDescriptionPrefix());
            } else if (!ModBlocks.OPERATORS.containsValue(block) && block != ModBlocks.SIGNAL) {
                BLOCK_ITEMS.add(ITEMS.registerSimpleBlockItem(block));
            }
        });
        ModBlocks.OPERATORS.forEach((op, block) -> MACHINES.put(op, ITEMS.registerItem(op.id,
                p -> new MachineItem((OperatorBlock) block.get(), p), p -> p.useBlockDescriptionPrefix())));
        for (String id : SCHEMATIC_IDS) {
            SCHEMATICS.put(id, ITEMS.registerItem(id, SchematicItem::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON)));
        }
        for (Benchmark a : Benchmark.values()) {
            if (a != Benchmark.READING && a != Benchmark.QUALITY && a != Benchmark.PROGRAMS && a != Benchmark.PRECISION) {
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
            case ORDERS -> "orders_dataset";
            case CONTROL -> "process_dataset";
            case SURVEY -> "survey_dataset";
            case PILOT -> "flight_dataset";
            case DEMAND -> "demand_dataset";
            case WATCH -> "watch_dataset";
            case RETRIEVAL -> "retrieval_dataset";
            case REACTOR -> "reactor_dataset";
            case RECKONING -> "reckoning_dataset";
            case MODULAR -> "modular_dataset";
            case READING, QUALITY, PROGRAMS, PRECISION -> throw new IllegalArgumentException();
        };
    }
}
