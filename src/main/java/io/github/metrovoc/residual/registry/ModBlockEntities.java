package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.block.BenchBlockEntity;
import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.block.FabricatorBlockEntity;
import io.github.metrovoc.residual.block.OperatorBlockEntity;
import io.github.metrovoc.residual.block.ReaderBlockEntity;
import io.github.metrovoc.residual.block.RelayBlockEntity;
import io.github.metrovoc.residual.block.ServerBlockEntity;
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
    public static final Supplier<BlockEntityType<ServerBlockEntity>> SERVER = BLOCK_ENTITIES.register("model_server",
            () -> new BlockEntityType<>(ServerBlockEntity::new, Set.of(ModBlocks.SERVER.get())));
    public static final Supplier<BlockEntityType<ReaderBlockEntity>> READER = BLOCK_ENTITIES.register("reader",
            () -> new BlockEntityType<>(ReaderBlockEntity::new, Set.of(ModBlocks.READER.get())));
    public static final Supplier<BlockEntityType<RelayBlockEntity>> RELAY = BLOCK_ENTITIES.register("relay",
            () -> new BlockEntityType<>(RelayBlockEntity::new, Set.of(ModBlocks.RELAY.get())));

    public static final Supplier<BlockEntityType<BenchBlockEntity>> BENCH = BLOCK_ENTITIES.register("bench",
            () -> new BlockEntityType<>(BenchBlockEntity::new, Set.of(ModBlocks.BENCH.get())));
    public static final Supplier<BlockEntityType<FabricatorBlockEntity>> FABRICATOR = BLOCK_ENTITIES.register("fabricator",
            () -> new BlockEntityType<>(FabricatorBlockEntity::new, Set.of(ModBlocks.FABRICATOR.get())));

    private ModBlockEntities() {
    }
}
