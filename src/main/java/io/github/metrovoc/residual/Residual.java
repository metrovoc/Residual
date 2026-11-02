package io.github.metrovoc.residual;

import com.mojang.logging.LogUtils;
import guideme.Guides;
import io.github.metrovoc.residual.agent.Agents;
import io.github.metrovoc.residual.agent.Senses;
import io.github.metrovoc.residual.block.Bursts;
import io.github.metrovoc.residual.block.CoreBlockEntity;
import io.github.metrovoc.residual.block.ForecasterBlockEntity;
import io.github.metrovoc.residual.item.ExosuitItem;
import io.github.metrovoc.residual.item.FlightFrameItem;
import io.github.metrovoc.residual.item.Vein;
import io.github.metrovoc.residual.item.WayfinderItem;
import io.github.metrovoc.residual.net.Network;
import io.github.metrovoc.residual.registry.ModAttachments;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModBlocks;
import io.github.metrovoc.residual.registry.ModComponents;
import io.github.metrovoc.residual.registry.ModEntities;
import io.github.metrovoc.residual.registry.ModItems;
import io.github.metrovoc.residual.registry.ModLoot;
import io.github.metrovoc.residual.registry.ModMenus;
import io.github.metrovoc.residual.registry.ModStructures;
import io.github.metrovoc.residual.registry.ModTabs;
import io.github.metrovoc.residual.train.Jobs;
import io.github.metrovoc.residual.train.TrainingJob;
import io.github.metrovoc.residual.world.Gates;
import io.github.metrovoc.residual.world.Relays;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
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
        ModLoot.FUNCTIONS.register(modBus);
        ModStructures.TYPES.register(modBus);
        ModStructures.PROCESSORS.register(modBus);
        modBus.addListener(Network::register);
        modBus.addListener(Residual::capabilities);
        modBus.addListener(Relays::controllers);
        container.registerConfig(ModConfig.Type.SERVER, ResidualConfig.SPEC);

        NeoForge.EVENT_BUS.addListener(Residual::onServerStopping);
        NeoForge.EVENT_BUS.addListener(Residual::onLogIn);
        NeoForge.EVENT_BUS.addListener(Agents::onChat);
        NeoForge.EVENT_BUS.addListener(Agents::commands);
        NeoForge.EVENT_BUS.addListener(Senses::played);
        NeoForge.EVENT_BUS.addListener(ForecasterBlockEntity::grew);
        NeoForge.EVENT_BUS.addListener(Bursts::tick);
        NeoForge.EVENT_BUS.addListener(FlightFrameItem::tick);
        NeoForge.EVENT_BUS.addListener(ExosuitItem::tick);
        NeoForge.EVENT_BUS.addListener(ExosuitItem::shield);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Vein::breaking);
        NeoForge.EVENT_BUS.addListener(Vein::drops);
        NeoForge.EVENT_BUS.addListener(Vein::tick);
        NeoForge.EVENT_BUS.addListener(Vein::loggedOut);
        NeoForge.EVENT_BUS.addListener(WayfinderItem::tick);
        NeoForge.EVENT_BUS.addListener(Relays::tick);
        NeoForge.EVENT_BUS.addListener(Gates::tick);
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

    /** Power blocks and exosuits speak Forge Energy, and power blocks' slots take and give items, to other mods' cables, pipes and hoppers. */
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.GENERATOR.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.CELL.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.BOILER.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.SOLAR.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.REACTOR.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.CHILLER.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.SMELTER.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.FORECASTER.get(), (be, side) -> be.third() ? be.energy() : null);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.LINK_DRIVER.get(), (be, side) -> be.energy());
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new ItemAccessEnergyHandler(access, ModComponents.CHARGE.get(), ExosuitItem.CAPACITY, ExosuitItem.TRANSFER),
                ModItems.EXOSUIT.get());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.REFINERY.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.GENERATOR.get(), (be, side) -> new WorldlyContainerWrapper(be.items(), side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.PORT.get(), (be, side) -> be.work.battery());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ModBlockEntities.PORT.get(), (be, side) -> be.work.tank());
        event.registerEntity(Capabilities.Energy.ENTITY, ModEntities.AGENT.get(), (agent, side) -> agent.battery());
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.BOILER.get(), (be, side) -> new WorldlyContainerWrapper(be.items(), side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.REACTOR.get(), (be, side) -> new WorldlyContainerWrapper(be.items(), side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.SMELTER.get(), (be, side) -> new WorldlyContainerWrapper(be.items(), side));
    }
}
