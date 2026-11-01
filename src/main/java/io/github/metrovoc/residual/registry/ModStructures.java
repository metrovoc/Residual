package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.world.ShoreStructure;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Residual.MOD_ID);

    /** Ruins at the water's edge, turned so their far end stands over water. */
    public static final Supplier<StructureType<ShoreStructure>> SHORE = TYPES.register("shore", () -> () -> ShoreStructure.CODEC);

    private ModStructures() {
    }
}
