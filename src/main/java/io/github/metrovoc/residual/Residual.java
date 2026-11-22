package io.github.metrovoc.residual;

import com.mojang.logging.LogUtils;
import guideme.Guides;
import io.github.metrovoc.residual.item.WayfinderItem;
import io.github.metrovoc.residual.net.Network;
import io.github.metrovoc.residual.registry.ModAttachments;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.registry.ModLoot;
import io.github.metrovoc.residual.registry.ModStructures;
import io.github.metrovoc.residual.registry.ModTabs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

@Mod(Residual.MOD_ID)
public final class Residual {
    public static final String MOD_ID = "residual";
    public static final Logger LOG = LogUtils.getLogger();
    public static final Identifier GUIDE = id("guide");

    public Residual(IEventBus modBus) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        ModTabs.TABS.register(modBus);
        ModLoot.FUNCTIONS.register(modBus);
        ModStructures.TYPES.register(modBus);
        ModStructures.PROCESSORS.register(modBus);
        modBus.addListener(Network::register);
        io.github.metrovoc.residual.interior.HallSetup.register(modBus);

        NeoForge.EVENT_BUS.addListener(Residual::onLogIn);
        NeoForge.EVENT_BUS.addListener(WayfinderItem::tick);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
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
