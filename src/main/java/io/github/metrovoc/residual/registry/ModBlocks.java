package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.block.AcceleratorBlock;
import io.github.metrovoc.residual.block.CableBlock;
import io.github.metrovoc.residual.block.CoreBlock;
import io.github.metrovoc.residual.block.OperatorBlock;
import io.github.metrovoc.residual.graph.Op;
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
    /** Uncolored cable, which joins cables of any color. */
    public static final DeferredBlock<CableBlock> CABLE = BLOCKS.registerBlock("cable", p -> new CableBlock(null, p), ModBlocks::cable);
    public static final Map<DyeColor, DeferredBlock<CableBlock>> COLORED_CABLES = new EnumMap<>(DyeColor.class);
    public static final DeferredBlock<CoreBlock> CORE = BLOCKS.registerBlock("model_core", CoreBlock::new,
            p -> machine(p).strength(3f, 8f).lightLevel(s -> s.getValue(CoreBlock.RUNNING) ? 10 : 3));
    public static final DeferredBlock<AcceleratorBlock> ACCELERATOR = BLOCKS.registerBlock("accelerator", AcceleratorBlock::new,
            p -> machine(p).lightLevel(s -> 4));
    public static final DeferredBlock<Block> CASING = BLOCKS.registerSimpleBlock("tensor_casing", ModBlocks::machine);

    static {
        for (Op op : Op.values()) {
            OPERATORS.put(op, BLOCKS.registerBlock(op.id, p -> new OperatorBlock(op, p), ModBlocks::machine));
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
