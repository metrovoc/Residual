package io.github.metrovoc.residual.registry;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.agent.Agent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Residual.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<Agent>> AGENT = ENTITIES.registerEntityType("agent",
            Agent::new, MobCategory.MISC, b -> b.sized(0.6f, 1.25f).eyeHeight(1.05f).clientTrackingRange(10));

    private ModEntities() {
    }

    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(AGENT.get(), Agent.createAttributes().build());
    }
}
