package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.world.GroundProcessor;
import io.github.metrovoc.residual.world.LegProcessor;
import io.github.metrovoc.residual.world.RuinStructure;
import io.github.metrovoc.residual.world.ShoreStructure;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Residual.MOD_ID);
    public static final DeferredRegister<StructureProcessorType<?>> PROCESSORS = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR,
            Residual.MOD_ID);

    /** Ruins on open ground, only where it is near level and dry. */
    public static final Supplier<StructureType<RuinStructure>> RUIN = TYPES.register("ruin", () -> () -> RuinStructure.CODEC);
    /** Ruins at the water's edge, turned so their far end stands over water. */
    public static final Supplier<StructureType<ShoreStructure>> SHORE = TYPES.register("shore", () -> () -> ShoreStructure.CODEC);
    /** Stilts and foundations run down to the ground. */
    public static final Supplier<StructureProcessorType<LegProcessor>> LEGS = PROCESSORS.register("legs", () -> () -> LegProcessor.CODEC);
    /** A ruin's ground laid on the land's own. */
    public static final Supplier<StructureProcessorType<GroundProcessor>> GROUND = PROCESSORS.register("ground", () -> () -> GroundProcessor.CODEC);

    private ModStructures() {
    }
}
