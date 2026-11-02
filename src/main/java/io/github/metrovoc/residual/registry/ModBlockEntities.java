package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.block.BenchBlockEntity;
import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.block.FabricatorBlockEntity;
import io.github.metrovoc.residual.block.ForecasterBlockEntity;
import io.github.metrovoc.residual.compute.ChillerBlockEntity;
import io.github.metrovoc.residual.compute.HallConsoleBlockEntity;
import io.github.metrovoc.residual.compute.LoadWallBlockEntity;
import io.github.metrovoc.residual.compute.RackModuleBlockEntity;
import io.github.metrovoc.residual.compute.SwitchBlockEntity;
import io.github.metrovoc.residual.energy.BoilerBlockEntity;
import io.github.metrovoc.residual.energy.CellBlockEntity;
import io.github.metrovoc.residual.energy.GeneratorBlockEntity;
import io.github.metrovoc.residual.energy.ReactorBlockEntity;
import io.github.metrovoc.residual.energy.SmelterBlockEntity;
import io.github.metrovoc.residual.energy.SolarBlockEntity;
import io.github.metrovoc.residual.block.ForgeBlockEntity;
import io.github.metrovoc.residual.block.OperatorBlockEntity;
import io.github.metrovoc.residual.block.ReaderBlockEntity;
import io.github.metrovoc.residual.block.SurveyorBlockEntity;
import io.github.metrovoc.residual.block.WatchPostBlockEntity;
import io.github.metrovoc.residual.block.GateBlockEntity;
import io.github.metrovoc.residual.block.RelayBlockEntity;
import io.github.metrovoc.residual.block.RelayLinkBlockEntity;
import io.github.metrovoc.residual.block.ServerBlockEntity;
import io.github.metrovoc.residual.refine.RefineryBlockEntity;
import io.github.metrovoc.residual.block.TunerBlockEntity;
import io.github.metrovoc.residual.storage.AccessPointBlockEntity;
import io.github.metrovoc.residual.storage.DockBlockEntity;
import io.github.metrovoc.residual.storage.LinkDriverBlockEntity;
import io.github.metrovoc.residual.storage.AssemblerBlockEntity;
import io.github.metrovoc.residual.storage.PortBlockEntity;
import io.github.metrovoc.residual.storage.ProcessorBlockEntity;
import io.github.metrovoc.residual.storage.RackBlockEntity;
import io.github.metrovoc.residual.storage.TerminalBlockEntity;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Residual.MOD_ID);

    public static final Supplier<BlockEntityType<OperatorBlockEntity>> OPERATOR = BLOCK_ENTITIES.register("operator",
            () -> new BlockEntityType<>(OperatorBlockEntity::new,
                    ModBlocks.OPERATORS.values().stream().map(b -> (Block) b.get()).collect(Collectors.toUnmodifiableSet())));
    public static final Supplier<BlockEntityType<CoreBlockEntity>> CORE = BLOCK_ENTITIES.register("model_core",
            () -> new BlockEntityType<>(CoreBlockEntity::new, Set.of(ModBlocks.CORE.get())));
    public static final Supplier<BlockEntityType<TunerBlockEntity>> TUNER = BLOCK_ENTITIES.register("tuner",
            () -> new BlockEntityType<>(TunerBlockEntity::new, Set.of(ModBlocks.TUNER.get())));
    public static final Supplier<BlockEntityType<ServerBlockEntity>> SERVER = BLOCK_ENTITIES.register("model_server",
            () -> new BlockEntityType<>(ServerBlockEntity::new, Set.of(ModBlocks.SERVER.get())));
    public static final Supplier<BlockEntityType<ReaderBlockEntity>> READER = BLOCK_ENTITIES.register("reader",
            () -> new BlockEntityType<>(ReaderBlockEntity::new, Set.of(ModBlocks.READER.get())));
    public static final Supplier<BlockEntityType<RelayBlockEntity>> RELAY = BLOCK_ENTITIES.register("relay",
            () -> new BlockEntityType<>(RelayBlockEntity::new, Set.of(ModBlocks.RELAY.get())));
    public static final Supplier<BlockEntityType<GateBlockEntity>> RELAY_GATE = BLOCK_ENTITIES.register("relay_gate",
            () -> new BlockEntityType<>(GateBlockEntity::new, Set.of(ModBlocks.RELAY_GATE.get())));
    public static final Supplier<BlockEntityType<RelayLinkBlockEntity>> RELAY_LINK = BLOCK_ENTITIES.register("relay_link",
            () -> new BlockEntityType<>(RelayLinkBlockEntity::new, Set.of(ModBlocks.RELAY_LINK.get())));

    public static final Supplier<BlockEntityType<BenchBlockEntity>> BENCH = BLOCK_ENTITIES.register("bench",
            () -> new BlockEntityType<>(BenchBlockEntity::new, Set.of(ModBlocks.BENCH.get())));
    public static final Supplier<BlockEntityType<FabricatorBlockEntity>> FABRICATOR = BLOCK_ENTITIES.register("fabricator",
            () -> new BlockEntityType<>(FabricatorBlockEntity::new, Set.of(ModBlocks.FABRICATOR.get())));
    public static final Supplier<BlockEntityType<ForgeBlockEntity>> FORGE = BLOCK_ENTITIES.register("bpe_forge",
            () -> new BlockEntityType<>(ForgeBlockEntity::new, Set.of(ModBlocks.FORGE.get())));
    public static final Supplier<BlockEntityType<ForecasterBlockEntity>> FORECASTER = BLOCK_ENTITIES.register("forecaster",
            () -> new BlockEntityType<>(ForecasterBlockEntity::new, Set.of(ModBlocks.FORECASTER.get(), ModBlocks.FORECASTER_II.get(), ModBlocks.FORECASTER_III.get())));

    public static final Supplier<BlockEntityType<RackBlockEntity>> RACK = BLOCK_ENTITIES.register("drive_rack",
            () -> new BlockEntityType<>(RackBlockEntity::new, Set.of(ModBlocks.DRIVE_RACK.get())));
    public static final Supplier<BlockEntityType<TerminalBlockEntity>> TERMINAL = BLOCK_ENTITIES.register("storage_terminal",
            () -> new BlockEntityType<>(TerminalBlockEntity::new, Set.of(ModBlocks.STORAGE_TERMINAL.get())));
    public static final Supplier<BlockEntityType<PortBlockEntity>> PORT = BLOCK_ENTITIES.register("port",
            () -> new BlockEntityType<>(PortBlockEntity::new, Set.of(ModBlocks.PORT.get())));

    public static final Supplier<BlockEntityType<AssemblerBlockEntity>> ASSEMBLER = BLOCK_ENTITIES.register("assembler",
            () -> new BlockEntityType<>(AssemblerBlockEntity::new, Set.of(ModBlocks.ASSEMBLER.get())));
    public static final Supplier<BlockEntityType<ProcessorBlockEntity>> PROCESSOR = BLOCK_ENTITIES.register("processor",
            () -> new BlockEntityType<>(ProcessorBlockEntity::new, Set.of(ModBlocks.PROCESSOR.get())));
    public static final Supplier<BlockEntityType<DockBlockEntity>> DOCK = BLOCK_ENTITIES.register("drive_dock",
            () -> new BlockEntityType<>(DockBlockEntity::new, Set.of(ModBlocks.DRIVE_DOCK.get())));
    public static final Supplier<BlockEntityType<AccessPointBlockEntity>> ACCESS_POINT = BLOCK_ENTITIES.register("access_point",
            () -> new BlockEntityType<>(AccessPointBlockEntity::new, Set.of(ModBlocks.ACCESS_POINT.get())));
    public static final Supplier<BlockEntityType<LinkDriverBlockEntity>> LINK_DRIVER = BLOCK_ENTITIES.register("link_driver",
            () -> new BlockEntityType<>(LinkDriverBlockEntity::new, Set.of(ModBlocks.LINK_DRIVER.get())));
    public static final Supplier<BlockEntityType<GeneratorBlockEntity>> GENERATOR = BLOCK_ENTITIES.register("combustion_generator",
            () -> new BlockEntityType<>(GeneratorBlockEntity::new, Set.of(ModBlocks.COMBUSTION_GENERATOR.get())));
    public static final Supplier<BlockEntityType<CellBlockEntity>> CELL = BLOCK_ENTITIES.register("energy_cell",
            () -> new BlockEntityType<>(CellBlockEntity::new, Set.of(ModBlocks.ENERGY_CELL.get(), ModBlocks.CAPACITOR_BANK.get())));
    public static final Supplier<BlockEntityType<BoilerBlockEntity>> BOILER = BLOCK_ENTITIES.register("boiler",
            () -> new BlockEntityType<>(BoilerBlockEntity::new, Set.of(ModBlocks.BOILER.get())));
    public static final Supplier<BlockEntityType<SolarBlockEntity>> SOLAR = BLOCK_ENTITIES.register("solar_panel",
            () -> new BlockEntityType<>(SolarBlockEntity::new, Set.of(ModBlocks.SOLAR_PANEL.get())));
    public static final Supplier<BlockEntityType<ReactorBlockEntity>> REACTOR = BLOCK_ENTITIES.register("reactor_core",
            () -> new BlockEntityType<>(ReactorBlockEntity::new, Set.of(ModBlocks.REACTOR_CORE.get())));
    public static final Supplier<BlockEntityType<RackModuleBlockEntity>> RACK_MODULE = BLOCK_ENTITIES.register("rack_module",
            () -> new BlockEntityType<>(RackModuleBlockEntity::new, Set.of(ModBlocks.RACK_MODULE.get())));
    public static final Supplier<BlockEntityType<SwitchBlockEntity>> SWITCH = BLOCK_ENTITIES.register("rack_switch",
            () -> new BlockEntityType<>(SwitchBlockEntity::new, Set.of(ModBlocks.RACK_SWITCH.get(), ModBlocks.POD_SWITCH.get())));
    public static final Supplier<BlockEntityType<ChillerBlockEntity>> CHILLER = BLOCK_ENTITIES.register("chiller",
            () -> new BlockEntityType<>(ChillerBlockEntity::new, Set.of(ModBlocks.CHILLER.get())));
    public static final Supplier<BlockEntityType<HallConsoleBlockEntity>> HALL_CONSOLE = BLOCK_ENTITIES.register("hall_console",
            () -> new BlockEntityType<>(HallConsoleBlockEntity::new, Set.of(ModBlocks.HALL_CONSOLE.get())));
    public static final Supplier<BlockEntityType<LoadWallBlockEntity>> LOAD_WALL = BLOCK_ENTITIES.register("load_wall",
            () -> new BlockEntityType<>(LoadWallBlockEntity::new, Set.of(ModBlocks.LOAD_WALL.get())));
    public static final Supplier<BlockEntityType<SurveyorBlockEntity>> SURVEYOR = BLOCK_ENTITIES.register("surveyor",
            () -> new BlockEntityType<>(SurveyorBlockEntity::new, Set.of(ModBlocks.SURVEYOR.get())));
    public static final Supplier<BlockEntityType<WatchPostBlockEntity>> WATCH_POST = BLOCK_ENTITIES.register("watch_post",
            () -> new BlockEntityType<>(WatchPostBlockEntity::new, Set.of(ModBlocks.WATCH_POST.get())));
    public static final Supplier<BlockEntityType<RefineryBlockEntity>> REFINERY = BLOCK_ENTITIES.register("refinery",
            () -> new BlockEntityType<>(RefineryBlockEntity::new,
                    ModBlocks.REFINERY.values().stream().map(b -> (Block) b.get()).collect(Collectors.toUnmodifiableSet())));
    public static final Supplier<BlockEntityType<SmelterBlockEntity>> SMELTER = BLOCK_ENTITIES.register("smelter",
            () -> new BlockEntityType<>(SmelterBlockEntity::new, Set.of(ModBlocks.SMELTER.get())));

    private ModBlockEntities() {
    }
}
