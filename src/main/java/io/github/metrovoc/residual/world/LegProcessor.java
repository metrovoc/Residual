package io.github.metrovoc.residual.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.registry.ModStructures;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * Legs to the ground: each marker block in a template becomes the leg's block, and the leg runs on down through air,
 * water, plants and leaves to the first ground that bears it, as stilts and foundations do.
 */
public class LegProcessor extends StructureProcessor {
    public static final MapCodec<LegProcessor> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("marker").forGetter(p -> p.marker),
            BlockState.CODEC.fieldOf("leg").forGetter(p -> p.leg),
            Codec.intRange(1, 64).optionalFieldOf("depth", 32).forGetter(p -> p.depth)).apply(i, LegProcessor::new));

    private final Block marker;
    private final BlockState leg;
    /** How far below the marker a leg may reach. */
    private final int depth;

    public LegProcessor(Block marker, BlockState leg, int depth) {
        this.marker = marker;
        this.leg = leg;
        this.depth = depth;
    }

    @Override
    public List<StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos position, BlockPos referencePos,
                                                       List<StructureBlockInfo> original, List<StructureBlockInfo> processed,
                                                       StructurePlaceSettings settings) {
        BoundingBox box = settings.getBoundingBox();
        Set<BlockPos> taken = new HashSet<>();
        processed.forEach(info -> taken.add(info.pos()));
        List<StructureBlockInfo> out = new ArrayList<>(processed.size());
        for (StructureBlockInfo info : processed) {
            if (!info.state().is(marker)) {
                out.add(info);
                continue;
            }
            out.add(new StructureBlockInfo(info.pos(), leg, null));
            // only the chunk being placed is there to look at
            if (box == null || !box.isInside(info.pos())) {
                continue;
            }
            BlockPos.MutableBlockPos at = info.pos().mutable();
            for (int k = 0; k < depth; k++) {
                at.move(0, -1, 0);
                if (taken.contains(at) || at.getY() < level.getMinY()) {
                    break;
                }
                BlockState ground = level.getBlockState(at);
                if (!ground.isAir() && !ground.canBeReplaced() && ground.getFluidState().isEmpty() && !ground.is(BlockTags.LEAVES)) {
                    break;
                }
                out.add(new StructureBlockInfo(at.immutable(), leg, null));
            }
        }
        return out;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructures.LEGS.get();
    }
}
