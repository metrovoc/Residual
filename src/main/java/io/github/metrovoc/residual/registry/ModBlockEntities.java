package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.block.CoreBlockEntity;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Residual.MOD_ID);

    public static final Supplier<BlockEntityType<CoreBlockEntity>> CORE = BLOCK_ENTITIES.register("model_core",
            () -> new BlockEntityType<>(CoreBlockEntity::new, Set.of(ModBlocks.CORE.get())));

    private ModBlockEntities() {
    }
}
