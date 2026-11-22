package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.block.CoreBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Residual.MOD_ID);

    public static final DeferredBlock<CoreBlock> CORE = BLOCKS.registerBlock("model_core", CoreBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3f, 8f).sound(SoundType.METAL).requiresCorrectToolForDrops()
                    .lightLevel(s -> s.getValue(CoreBlock.DRIVE) ? 10 : 3));

    private ModBlocks() {
    }
}
