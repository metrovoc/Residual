package io.github.metrovoc.residual.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.metrovoc.residual.registry.ModStructures;
import java.util.Arrays;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
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
 * A ruin on open ground, turned any of four ways. It stands only where the ground under its corners and middle lies
 * within {@code max_slope} blocks of level and, unless it is built for the water, is dry; its floor goes at the middle
 * height of the ground there, {@code offset} blocks up.
 */
public class RuinStructure extends Structure {
    public static final MapCodec<RuinStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
            Codec.INT.optionalFieldOf("offset", 0).forGetter(s -> s.offset),
            Codec.intRange(0, 64).optionalFieldOf("max_slope", 4).forGetter(s -> s.maxSlope),
            Heightmap.Types.CODEC.optionalFieldOf("heightmap", Heightmap.Types.WORLD_SURFACE_WG).forGetter(s -> s.heightmap),
            Codec.BOOL.optionalFieldOf("dry", true).forGetter(s -> s.dry)).apply(i, RuinStructure::new));

    private final Holder<StructureTemplatePool> startPool;
    private final int offset;
    private final int maxSlope;
    private final Heightmap.Types heightmap;
    private final boolean dry;

    public RuinStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int offset, int maxSlope,
                         Heightmap.Types heightmap, boolean dry) {
        super(settings);
        this.startPool = startPool;
        this.offset = offset;
        this.maxSlope = maxSlope;
        this.heightmap = heightmap;
        this.dry = dry;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        StructurePoolElement element = startPool.value().getRandomTemplate(context.random());
        if (element == EmptyPoolElement.INSTANCE) {
            return Optional.empty();
        }
        StructureTemplateManager templates = context.structureTemplateManager();
        ChunkGenerator generator = context.chunkGenerator();
        Rotation turn = Rotation.getRandom(context.random());
        BlockPos origin = new BlockPos(context.chunkPos().getMinBlockX(), 0, context.chunkPos().getMinBlockZ());
        BoundingBox box = element.getBoundingBox(templates, origin, turn);
        int x0 = box.minX() + 1, x1 = box.maxX() - 1, z0 = box.minZ() + 1, z1 = box.maxZ() - 1;
        int[][] samples = {{(x0 + x1) / 2, (z0 + z1) / 2}, {x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}};
        int[] ground = new int[samples.length];
        for (int k = 0; k < samples.length; k++) {
            int x = samples[k][0], z = samples[k][1];
            ground[k] = generator.getFirstFreeHeight(x, z, heightmap, context.heightAccessor(), context.randomState());
            // the biome before the dearer samples: most places a search looks at are the wrong land
            if (k == 0 && !biome(context, origin.atY(ground[0] + offset))) {
                return Optional.empty();
            }
            if (dry && ground[k] != generator.getFirstFreeHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(),
                    context.randomState())) {
                return Optional.empty();
            }
        }
        int[] sorted = ground.clone();
        Arrays.sort(sorted);
        if (sorted[sorted.length - 1] - sorted[0] > maxSlope) {
            return Optional.empty();
        }
        BlockPos at = origin.atY(sorted[sorted.length / 2] + offset);
        PoolElementStructurePiece piece = new PoolElementStructurePiece(templates, element, at, element.getGroundLevelDelta(), turn,
                element.getBoundingBox(templates, at, turn), LiquidSettings.APPLY_WATERLOGGING);
        return Optional.of(new GenerationStub(at, builder -> builder.addPiece(piece)));
    }

    /**
     * Whether {@code pos} lies in one of {@code context}'s biomes, the test vanilla puts to a structure's start after
     * {@link #findGenerationPoint}, asked early because it is cheap beside sampling the ground.
     */
    static boolean biome(GenerationContext context, BlockPos pos) {
        return context.validBiome().test(context.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(pos.getX()),
                QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()), context.randomState().sampler()));
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.RUIN.get();
    }
}
