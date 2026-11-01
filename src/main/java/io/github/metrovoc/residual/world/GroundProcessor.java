package io.github.metrovoc.residual.world;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.registry.ModStructures;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * Lays a ruin's ground on the land's own: each block of ground its template draws as grass becomes what the land has
 * there, sand in a desert, snowy grass on snowy ground; a plant the template set on it gives way to the land's snow,
 * and goes where nothing would grow.
 */
public class GroundProcessor extends StructureProcessor {
    public static final MapCodec<GroundProcessor> CODEC = MapCodec.unit(GroundProcessor::new);

    @Override
    public List<StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos position, BlockPos referencePos,
                                                       List<StructureBlockInfo> original, List<StructureBlockInfo> processed,
                                                       StructurePlaceSettings settings) {
        BoundingBox box = settings.getBoundingBox();
        Map<BlockPos, Integer> at = new HashMap<>();
        for (int i = 0; i < processed.size(); i++) {
            at.put(processed.get(i).pos(), i);
        }
        List<StructureBlockInfo> out = new ArrayList<>(processed);
        for (int i = 0; i < out.size(); i++) {
            StructureBlockInfo info = out.get(i);
            if (!info.state().is(Blocks.GRASS_BLOCK) || box == null || !box.isInside(info.pos())) {
                continue;
            }
            BlockState land = level.getBlockState(info.pos());
            if (!land.is(BlockTags.DIRT) && !land.is(BlockTags.SAND) && !land.is(BlockTags.TERRACOTTA)
                    && !land.is(Blocks.SNOW_BLOCK) && !land.is(Blocks.GRAVEL)) {
                continue;
            }
            out.set(i, new StructureBlockInfo(info.pos(), land, null));
            Integer above = at.get(info.pos().above());
            if (above == null || !out.get(above).state().is(BlockTags.REPLACEABLE)) {
                continue;
            }
            BlockState over = level.getBlockState(info.pos().above());
            if (over.is(Blocks.SNOW) || !land.is(BlockTags.DIRT)) {
                out.set(above, new StructureBlockInfo(info.pos().above(), over.is(Blocks.SNOW) ? over : Blocks.AIR.defaultBlockState(), null));
            }
        }
        return out;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructures.GROUND.get();
    }
}
