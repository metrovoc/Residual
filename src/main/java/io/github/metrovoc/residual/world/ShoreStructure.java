package io.github.metrovoc.residual.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.registry.ModStructures;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * A ruin built at the water's edge, a slip or a mill: its template's north end belongs on land and its south end over
 * water. Of the four ways it can face, it takes one where the ground bears that out, and appears nowhere it does not.
 */
public class ShoreStructure extends Structure {
    public static final MapCodec<ShoreStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
            Codec.INT.optionalFieldOf("offset", 0).forGetter(s -> s.offset)).apply(i, ShoreStructure::new));
    /** How deep the water must be under the far end. */
    private static final int DEPTH = 2;

    private final Holder<StructureTemplatePool> startPool;
    /** Where the template's floor sits, against the ground at its near end. */
    private final int offset;

    public ShoreStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int offset) {
        super(settings);
        this.startPool = startPool;
        this.offset = offset;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        StructurePoolElement element = startPool.value().getRandomTemplate(context.random());
        if (element == EmptyPoolElement.INSTANCE) {
            return Optional.empty();
        }
        StructureTemplateManager templates = context.structureTemplateManager();
        ChunkGenerator generator = context.chunkGenerator();
        BlockPos origin = new BlockPos(context.chunkPos().getMinBlockX(), 0, context.chunkPos().getMinBlockZ());
        List<Rotation> turns = new ArrayList<>(List.of(Rotation.values()));
        Collections.shuffle(turns, new Random(context.random().nextLong()));
        for (Rotation turn : turns) {
            BoundingBox box = element.getBoundingBox(templates, origin, turn);
            Direction out = turn.rotate(Direction.SOUTH);
            BlockPos near = edge(box, out.getOpposite()), far = edge(box, out);
            int ground = generator.getFirstOccupiedHeight(near.getX(), near.getZ(), Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(),
                    context.randomState());
            int floor = generator.getFirstOccupiedHeight(far.getX(), far.getZ(), Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(),
                    context.randomState());
            if (ground < generator.getSeaLevel() || floor > generator.getSeaLevel() - DEPTH) {
                continue;
            }
            BlockPos at = origin.atY(ground + 1 + offset);
            PoolElementStructurePiece piece = new PoolElementStructurePiece(templates, element, at, element.getGroundLevelDelta(), turn,
                    element.getBoundingBox(templates, at, turn), LiquidSettings.APPLY_WATERLOGGING);
            return Optional.of(new GenerationStub(at, builder -> builder.addPiece(piece)));
        }
        return Optional.empty();
    }

    /** The middle of the side of {@code box} that faces {@code side}, two blocks in from it. */
    private static BlockPos edge(BoundingBox box, Direction side) {
        int cx = (box.minX() + box.maxX()) / 2, cz = (box.minZ() + box.maxZ()) / 2;
        return switch (side) {
            case NORTH -> new BlockPos(cx, 0, box.minZ() + 2);
            case SOUTH -> new BlockPos(cx, 0, box.maxZ() - 2);
            case WEST -> new BlockPos(box.minX() + 2, 0, cz);
            default -> new BlockPos(box.maxX() - 2, 0, cz);
        };
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.SHORE.get();
    }
}
