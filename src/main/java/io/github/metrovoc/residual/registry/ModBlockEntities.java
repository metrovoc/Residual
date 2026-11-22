package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.block.ForecasterBlockEntity;
import io.github.metrovoc.residual.block.ForgeBlockEntity;
import io.github.metrovoc.residual.block.InstrumentBlockEntity;
import io.github.metrovoc.residual.block.OperatorBlockEntity;
import io.github.metrovoc.residual.block.ReaderBlockEntity;
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
    public static final Supplier<BlockEntityType<ReaderBlockEntity>> READER = BLOCK_ENTITIES.register("reader",
            () -> new BlockEntityType<>(ReaderBlockEntity::new, Set.of(ModBlocks.READER.get())));

    public static final Supplier<BlockEntityType<ForgeBlockEntity>> FORGE = BLOCK_ENTITIES.register("bpe_forge",
            () -> new BlockEntityType<>(ForgeBlockEntity::new, Set.of(ModBlocks.FORGE.get())));
    public static final Supplier<BlockEntityType<ForecasterBlockEntity>> FORECASTER = BLOCK_ENTITIES.register("forecaster",
            () -> new BlockEntityType<>(ForecasterBlockEntity::new, Set.of(ModBlocks.FORECASTER.get())));


    public static final Supplier<BlockEntityType<InstrumentBlockEntity>> INSTRUMENT = BLOCK_ENTITIES.register("instrument",
            () -> new BlockEntityType<>(InstrumentBlockEntity::new, Set.of(ModBlocks.STREAM_SCOPE.get(), ModBlocks.TRIAL_RIG.get())));

    private ModBlockEntities() {
    }
}
