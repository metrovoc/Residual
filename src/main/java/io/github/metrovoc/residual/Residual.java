package io.github.metrovoc.residual;

import com.mojang.logging.LogUtils;
import guideme.Guides;
import io.github.metrovoc.residual.apprentice.Apprentices;
import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.net.Network;
import io.github.metrovoc.residual.registry.ModAttachments;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.registry.ModEntities;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.registry.ModMenus;
import io.github.metrovoc.residual.registry.ModTabs;
import io.github.metrovoc.residual.train.Jobs;
import io.github.metrovoc.residual.train.TrainingJob;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@Mod(Residual.MOD_ID)
public final class Residual {
    public static final String MOD_ID = "residual";
    public static final Logger LOG = LogUtils.getLogger();
    public static final Identifier GUIDE = id("guide");

    public Residual(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        modBus.addListener(ModEntities::attributes);
        ModAttachments.ATTACHMENTS.register(modBus);
        ModTabs.TABS.register(modBus);
        modBus.addListener(Network::register);
        container.registerConfig(ModConfig.Type.SERVER, ResidualConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(Residual::onServerStopping);
        NeoForge.EVENT_BUS.addListener(Residual::onLogIn);
        NeoForge.EVENT_BUS.addListener(Apprentices::onChat);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Lets every training run hand back its weights before the world is saved. */
    private static void onServerStopping(ServerStoppingEvent event) {
        for (TrainingJob job : Jobs.stopAll()) {
            if (job.level.getBlockEntity(job.core) instanceof CoreBlockEntity core) {
                core.tick();
            }
        }
    }

    private static void onLogIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.getData(ModAttachments.GUIDE_GIVEN)) {
            player.setData(ModAttachments.GUIDE_GIVEN, true);
            ItemStack guide = Guides.createGuideItem(GUIDE);
            if (!player.getInventory().add(guide)) {
                player.drop(guide, false);
            }
        }
    }
}
