package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.agent.SignalBlock;
import io.github.metrovoc.residual.bench.Unit;
import io.github.metrovoc.residual.block.AcceleratorBlock;
import io.github.metrovoc.residual.block.BenchBlock;
import io.github.metrovoc.residual.block.CableBlock;
import io.github.metrovoc.residual.block.CoreBlock;
import io.github.metrovoc.residual.block.FabricatorBlock;
import io.github.metrovoc.residual.block.ForecasterBlock;
import io.github.metrovoc.residual.energy.CellBlock;
import io.github.metrovoc.residual.energy.GeneratorBlock;
import io.github.metrovoc.residual.energy.PowerCableBlock;
import io.github.metrovoc.residual.energy.PowerDeviceBlock;
import io.github.metrovoc.residual.energy.SmelterBlock;
import io.github.metrovoc.residual.block.ForgeBlock;
import io.github.metrovoc.residual.block.OperatorBlock;
import io.github.metrovoc.residual.block.ReaderBlock;
import io.github.metrovoc.residual.block.GateBlock;
import io.github.metrovoc.residual.block.RelayBlock;
import io.github.metrovoc.residual.block.RelayLinkBlock;
import io.github.metrovoc.residual.block.ServerBlock;
import io.github.metrovoc.residual.block.StreamBlock;
import io.github.metrovoc.residual.block.SurveyorBlock;
import io.github.metrovoc.residual.block.UnitBlock;
import io.github.metrovoc.residual.refine.RefineryBlock;
import io.github.metrovoc.residual.refine.Stage;
import io.github.metrovoc.residual.block.WatchPostBlock;
import io.github.metrovoc.residual.graph.Op;
import io.github.metrovoc.residual.storage.AccessPointBlock;
import io.github.metrovoc.residual.storage.DockBlock;
import io.github.metrovoc.residual.storage.LinkDriverBlock;
import io.github.metrovoc.residual.storage.AssemblerBlock;
import io.github.metrovoc.residual.storage.LinkBlock;
import io.github.metrovoc.residual.storage.PortBlock;
import io.github.metrovoc.residual.storage.ProcessorBlock;
import io.github.metrovoc.residual.storage.RackBlock;
import io.github.metrovoc.residual.storage.TerminalBlock;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Residual.MOD_ID);

    public static final Map<Op, DeferredBlock<OperatorBlock>> OPERATORS = new EnumMap<>(Op.class);
    /** What a Model Core's recipe is built from. */
    public static final Map<Unit, DeferredBlock<UnitBlock>> UNITS = new EnumMap<>(Unit.class);
    /** The Refinery's machines, a block each. */
    public static final Map<Stage, DeferredBlock<RefineryBlock>> REFINERY = new EnumMap<>(Stage.class);
    /** Uncolored cable, which joins cables of any color. */
    public static final DeferredBlock<CableBlock> CABLE = BLOCKS.registerBlock("cable", p -> new CableBlock(null, p), ModBlocks::cable);
    public static final Map<DyeColor, DeferredBlock<CableBlock>> COLORED_CABLES = new EnumMap<>(DyeColor.class);
    public static final DeferredBlock<CoreBlock> CORE = BLOCKS.registerBlock("model_core", CoreBlock::new,
            p -> machine(p).strength(3f, 8f).lightLevel(s -> s.getValue(CoreBlock.RUNNING) ? 10 : 3));
    public static final DeferredBlock<AcceleratorBlock> ACCELERATOR = BLOCKS.registerBlock("accelerator", AcceleratorBlock::new,
            p -> machine(p).lightLevel(s -> 4));
    public static final DeferredBlock<Block> CASING = BLOCKS.registerSimpleBlock("tensor_casing", ModBlocks::machine);
    public static final DeferredBlock<ServerBlock> SERVER = BLOCKS.registerBlock("model_server", ServerBlock::new,
            p -> machine(p).strength(3f, 8f).lightLevel(s -> s.getValue(ServerBlock.SERVING) ? 10 : s.getValue(ServerBlock.LOADED) ? 5 : 2));
    public static final DeferredBlock<ReaderBlock> READER = BLOCKS.registerBlock("reader", ReaderBlock::new,
            p -> machine(p).noOcclusion().lightLevel(s -> s.getValue(ReaderBlock.READING) ? 8 : 0));
    public static final DeferredBlock<BenchBlock> BENCH = BLOCKS.registerBlock("bench", BenchBlock::new,
            p -> machine(p).noOcclusion().lightLevel(s -> 5));
    public static final DeferredBlock<FabricatorBlock> FABRICATOR = BLOCKS.registerBlock("fabricator", FabricatorBlock::new,
            p -> machine(p).strength(3f, 8f).lightLevel(s -> s.getValue(FabricatorBlock.WORKING) ? 11 : 3));
    public static final DeferredBlock<ForgeBlock> FORGE = BLOCKS.registerBlock("bpe_forge", ForgeBlock::new,
            p -> machine(p).strength(3f, 8f).lightLevel(s -> s.getValue(ForgeBlock.WORKING) ? 11 : 4));
    public static final DeferredBlock<ForecasterBlock> FORECASTER = BLOCKS.registerBlock("forecaster", ForecasterBlock::new,
            p -> machine(p).strength(2f, 6f).lightLevel(s -> s.getValue(ForecasterBlock.WORKING) ? 10 : 3));
    /** The Lab tier's Forecaster: the same watching, up to sixteen times as fast. */
    public static final DeferredBlock<ForecasterBlock> FORECASTER_II = BLOCKS.registerBlock("forecaster_ii", ForecasterBlock::new,
            p -> machine(p).strength(2f, 6f).lightLevel(s -> s.getValue(ForecasterBlock.WORKING) ? 12 : 3));
    /** The Cluster tier's Forecaster: the same again, and bursts over all it watches, paid in power and compute. */
    public static final DeferredBlock<ForecasterBlock> FORECASTER_III = BLOCKS.registerBlock("forecaster_iii", ForecasterBlock::new,
            p -> machine(p).strength(2f, 6f).lightLevel(s -> s.getValue(ForecasterBlock.WORKING) ? 14 : 4));
    /** The Lab tier's Surveyor: reads the ground around it with a model and keeps where each ore lies. */
    public static final DeferredBlock<SurveyorBlock> SURVEYOR = BLOCKS.registerBlock("surveyor", SurveyorBlock::new,
            p -> machine(p).strength(2f, 6f).lightLevel(s -> s.getValue(SurveyorBlock.WORKING) ? 10 : 3));
    /** The Cluster tier's Watch Post: notices with a model when something around it has gone wrong. */
    public static final DeferredBlock<WatchPostBlock> WATCH_POST = BLOCKS.registerBlock("watch_post", WatchPostBlock::new,
            p -> machine(p).strength(2f, 6f).lightLevel(s -> s.getValue(WatchPostBlock.WRONG) ? 10 : 3));
    public static final DeferredBlock<StreamBlock> STREAM = BLOCKS.registerBlock("stream", StreamBlock::new,
            p -> p.mapColor(MapColor.COLOR_CYAN).strength(1f, 6f).sound(SoundType.AMETHYST).lightLevel(s -> 7));
    public static final DeferredBlock<RelayBlock> RELAY = BLOCKS.registerBlock("relay", RelayBlock::new,
            p -> machine(p).strength(1.5f, 6f).noOcclusion().lightLevel(s -> s.getValue(RelayBlock.LIVE) && !s.getValue(RelayBlock.UP) ? 9 : 0));
    /** The Datacenter tier's way across the world: stand on one and be sent to another on its network. */
    public static final DeferredBlock<GateBlock> RELAY_GATE = BLOCKS.registerBlock("relay_gate", GateBlock::new,
            p -> machine(p).strength(3f, 8f).noOcclusion().lightLevel(s -> s.getValue(GateBlock.OPEN) ? 10 : 2));
    /** Joins the storage and power networks touching it to those at every other link on its relay network. */
    public static final DeferredBlock<RelayLinkBlock> RELAY_LINK = BLOCKS.registerBlock("relay_link", RelayLinkBlock::new,
            p -> machine(p).strength(3f, 8f).lightLevel(s -> s.getValue(RelayLinkBlock.WORKING) ? 9 : 2));
    public static final DeferredBlock<LinkBlock> STORAGE_LINK = BLOCKS.registerBlock("storage_link", LinkBlock::new,
            p -> p.mapColor(MapColor.COLOR_GRAY).strength(0.5f).sound(SoundType.COPPER).noOcclusion().forceSolidOn());
    public static final DeferredBlock<RackBlock> DRIVE_RACK = BLOCKS.registerBlock("drive_rack", RackBlock::new,
            p -> machine(p).strength(2.5f, 6f));
    public static final DeferredBlock<TerminalBlock> STORAGE_TERMINAL = BLOCKS.registerBlock("storage_terminal", TerminalBlock::new,
            p -> machine(p).lightLevel(s -> s.getValue(TerminalBlock.LIT) ? 9 : 2));
    /** Where an agent gives out a redstone signal; no one holds it or sees it. */
    public static final DeferredBlock<SignalBlock> SIGNAL = BLOCKS.registerBlock("signal", SignalBlock::new,
            p -> p.noCollision().noOcclusion().replaceable().instabreak().noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));
    public static final DeferredBlock<PortBlock> PORT = BLOCKS.registerBlock("port", PortBlock::new,
            p -> machine(p).lightLevel(s -> s.getValue(PortBlock.ACTIVE) ? 7 : 2));
    public static final DeferredBlock<DockBlock> DRIVE_DOCK = BLOCKS.registerBlock("drive_dock", DockBlock::new,
            p -> machine(p).lightLevel(s -> s.getValue(DockBlock.WORKING) ? 9 : 2));
    public static final DeferredBlock<AccessPointBlock> ACCESS_POINT = BLOCKS.registerBlock("access_point", AccessPointBlock::new,
            p -> machine(p).lightLevel(s -> s.getValue(AccessPointBlock.LIT) ? 9 : 2));
    public static final DeferredBlock<LinkDriverBlock> LINK_DRIVER = BLOCKS.registerBlock("link_driver", LinkDriverBlock::new,
            p -> machine(p).lightLevel(s -> s.getValue(PowerDeviceBlock.WORKING) ? 11 : 2));
    public static final DeferredBlock<PowerCableBlock> POWER_CABLE = BLOCKS.registerBlock("power_cable", PowerCableBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(0.5f).sound(SoundType.COPPER).noOcclusion().forceSolidOn());
    public static final DeferredBlock<GeneratorBlock> COMBUSTION_GENERATOR = BLOCKS.registerBlock("combustion_generator",
            GeneratorBlock::new, p -> machine(p).lightLevel(s -> s.getValue(PowerDeviceBlock.WORKING) ? 12 : 0));
    public static final DeferredBlock<CellBlock> ENERGY_CELL = BLOCKS.registerBlock("energy_cell", CellBlock::new,
            p -> machine(p).lightLevel(s -> 2 * s.getValue(CellBlock.CHARGE)));
    public static final DeferredBlock<SmelterBlock> SMELTER = BLOCKS.registerBlock("smelter", SmelterBlock::new,
            p -> machine(p).lightLevel(s -> s.getValue(PowerDeviceBlock.WORKING) ? 13 : 0));
    public static final DeferredBlock<AssemblerBlock> ASSEMBLER = BLOCKS.registerBlock("assembler", AssemblerBlock::new,
            p -> machine(p).lightLevel(s -> s.getValue(AssemblerBlock.WORKING) ? 9 : 2));
    public static final DeferredBlock<ProcessorBlock> PROCESSOR = BLOCKS.registerBlock("processor", ProcessorBlock::new,
            p -> machine(p).lightLevel(s -> s.getValue(ProcessorBlock.WORKING) ? 9 : 2));

    static {
        for (Op op : Op.values()) {
            OPERATORS.put(op, BLOCKS.registerBlock(op.id, p -> new OperatorBlock(op, p), ModBlocks::machine));
        }
        for (Unit unit : Unit.values()) {
            UNITS.put(unit, BLOCKS.registerBlock(unit.id, p -> new UnitBlock(unit, p),
                    p -> machine(p).lightLevel(s -> s.getValue(UnitBlock.WORKING) ? 9 : 2)));
        }
        for (Stage stage : Stage.values()) {
            REFINERY.put(stage, BLOCKS.registerBlock(stage.id, p -> new RefineryBlock(stage, p), p -> stage == Stage.TANK
                    ? machine(p).noOcclusion().lightLevel(s -> s.getValue(RefineryBlock.WORKING) ? 11 : 6)
                    : machine(p).lightLevel(s -> s.getValue(RefineryBlock.WORKING) ? 11 : 3)));
        }
        for (DyeColor color : DyeColor.values()) {
            COLORED_CABLES.put(color, BLOCKS.registerBlock(color.getSerializedName() + "_cable", p -> new CableBlock(color, p), ModBlocks::cable));
        }
    }

    private ModBlocks() {
    }

    private static BlockBehaviour.Properties machine(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.METAL).strength(2f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties cable(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.4f).sound(SoundType.GLASS).noOcclusion().forceSolidOn();
    }
}
