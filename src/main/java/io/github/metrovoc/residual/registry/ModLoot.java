package io.github.metrovoc.residual.registry;

import com.mojang.serialization.MapCodec;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.item.ShardAbout;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModLoot {
    public static final DeferredRegister<MapCodec<? extends LootItemFunction>> FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE,
            Residual.MOD_ID);

    public static final Supplier<MapCodec<ShardAbout>> SHARD_ABOUT = FUNCTIONS.register("shard_about", () -> ShardAbout.MAP_CODEC);

    private ModLoot() {
    }
}
