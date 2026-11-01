package io.github.metrovoc.residual.client;

import guideme.Guide;
import guideme.GuideItemSettings;
import guideme.compiler.TagCompiler;
import guideme.siteexport.ExportPostProcessor;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.menu.CoreMenu;
import io.github.metrovoc.residual.net.AgentSync;
import io.github.metrovoc.residual.net.CoreSync;
import io.github.metrovoc.residual.net.FlowSync;
import io.github.metrovoc.residual.net.GhostSync;
import io.github.metrovoc.residual.net.SurveySync;
import io.github.metrovoc.residual.net.SampleResult;
import io.github.metrovoc.residual.net.CraftJobsSync;
import io.github.metrovoc.residual.net.CraftPlanSync;
import io.github.metrovoc.residual.net.CraftTreeSync;
import io.github.metrovoc.residual.net.OpenTerminal;
import io.github.metrovoc.residual.net.ShimmerSync;
import io.github.metrovoc.residual.net.StorageMeaningSync;
import io.github.metrovoc.residual.net.StorageSync;
import io.github.metrovoc.residual.net.SuitFlight;
import io.github.metrovoc.residual.net.TerminalAnswer;
import io.github.metrovoc.residual.net.WatchSync;
import io.github.metrovoc.residual.net.WaveSync;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModEntities;
import io.github.metrovoc.residual.registry.ModMenus;
import io.github.metrovoc.residual.menu.WatchPostMenu;
import io.github.metrovoc.residual.storage.TerminalMenu;
import java.util.List;
import java.util.Optional;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Residual.MOD_ID, dist = Dist.CLIENT)
public final class ResidualClient {
    public ResidualClient(IEventBus modBus) {
        modBus.addListener(ResidualClient::screens);
        modBus.addListener(ResidualClient::payloads);
        modBus.addListener(ResidualClient::renderers);
        modBus.addListener(ResidualClient::layers);
        modBus.addListener(ResidualClient::models);
        modBus.addListener(ResidualClient::keys);
        NeoForge.EVENT_BUS.addListener(ResidualClient::openTerminalKey);
        modBus.addListener(ExosuitScreen::keys);
        NeoForge.EVENT_BUS.addListener(Waves::extract);
        NeoForge.EVENT_BUS.addListener(Waves::submit);
        NeoForge.EVENT_BUS.addListener(FlowView::extract);
        NeoForge.EVENT_BUS.addListener(FlowView::submit);
        NeoForge.EVENT_BUS.addListener(GhostView::extract);
        NeoForge.EVENT_BUS.addListener(GhostView::submit);
        NeoForge.EVENT_BUS.addListener(ShimmerView::extract);
        NeoForge.EVENT_BUS.addListener(ShimmerView::submit);
        NeoForge.EVENT_BUS.addListener(SurveyView::extract);
        NeoForge.EVENT_BUS.addListener(SurveyView::submit);
        NeoForge.EVENT_BUS.addListener(VeinView::extract);
        NeoForge.EVENT_BUS.addListener(VeinView::submit);
        NeoForge.EVENT_BUS.addListener(ExosuitScreen::pressed);
        NeoForge.EVENT_BUS.addListener(Clips::tick);
        // the field notes
        Guide.builder(Residual.GUIDE)
                .itemSettings(new GuideItemSettings(Optional.of(Component.translatable("residual.guide_name")),
                        List.of(Component.translatable("residual.guide_tooltip").withStyle(ChatFormatting.DARK_GRAY)),
                        Optional.of(Residual.id("item/guide"))))
                .extension(TagCompiler.EXTENSION_POINT, new Milestones.Tag())
                .extension(TagCompiler.EXTENSION_POINT, new Clips.Tag())
                .extension(ExportPostProcessor.EXTENSION_POINT, new Milestones.Web())
                .extension(ExportPostProcessor.EXTENSION_POINT, new Clips.Web())
                .build();
    }

    /** Opens a Wireless Terminal or Portable Drive from anywhere in the inventory; no key is taken until a player gives it one. */
    private static final KeyMapping OPEN_TERMINAL = new KeyMapping("key.residual.open_terminal", KeyConflictContext.IN_GAME, InputConstants.UNKNOWN,
            KeyMapping.Category.INVENTORY);

    private static void keys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_TERMINAL);
    }

    private static void openTerminalKey(ClientTickEvent.Post event) {
        while (OPEN_TERMINAL.consumeClick()) {
            ClientPacketDistributor.sendToServer(new OpenTerminal());
        }
    }

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CORE.get(), CoreScreen::new);
        event.register(ModMenus.OPERATOR.get(), OperatorScreen::new);
        event.register(ModMenus.AGENT.get(), AgentScreen::new);
        event.register(ModMenus.READER.get(), ReaderScreen::new);
        event.register(ModMenus.SURVEYOR.get(), SurveyorScreen::new);
        event.register(ModMenus.WATCH_POST.get(), WatchPostScreen::new);
        event.register(ModMenus.FABRICATOR.get(), FabricatorScreen::new);
        event.register(ModMenus.POWER.get(), PowerScreen::new);
        event.register(ModMenus.FORGE.get(), ForgeScreen::new);
        event.register(ModMenus.FORECASTER.get(), ForecasterScreen::new);
        event.register(ModMenus.RACK.get(), RackScreen::new);
        event.register(ModMenus.TERMINAL.get(), StorageTerminalScreen::new);
        event.register(ModMenus.PORT.get(), PortScreen::new);
        event.register(ModMenus.ASSEMBLER.get(), AssemblerScreen::new);
        event.register(ModMenus.PROCESSOR.get(), ProcessorScreen::new);
        event.register(ModMenus.DOCK.get(), DockScreen::new);
        event.register(ModMenus.ACCESS_POINT.get(), AccessPointScreen::new);
    }

    private static void payloads(RegisterClientPayloadHandlersEvent event) {
        event.register(CoreSync.TYPE, (sync, ctx) -> {
            if (ctx.player().containerMenu instanceof CoreMenu menu && menu.pos.equals(sync.pos())) {
                CoreView.sync = sync;
            }
        });
        event.register(SampleResult.TYPE, (result, ctx) -> {
            CoreView.sample = result;
            CoreView.sampling = false;
        });
        event.register(AgentSync.TYPE, (sync, ctx) -> AgentView.sync = sync);
        event.register(WaveSync.TYPE, (wave, ctx) -> Waves.receive(wave));
        event.register(FlowSync.TYPE, (flow, ctx) -> FlowView.receive(flow));
        event.register(GhostSync.TYPE, (ghost, ctx) -> GhostView.receive(ghost));
        event.register(ShimmerSync.TYPE, (shimmer, ctx) -> ShimmerView.receive(shimmer));
        event.register(SurveySync.TYPE, (survey, ctx) -> SurveyView.receive(survey));
        event.register(StorageSync.TYPE, (sync, ctx) -> {
            if (ctx.player().containerMenu instanceof TerminalMenu menu && menu.containerId == sync.menu()) {
                menu.apply(sync);
            }
        });
        event.register(StorageMeaningSync.TYPE, (sync, ctx) -> {
            if (ctx.player().containerMenu instanceof TerminalMenu menu && menu.containerId == sync.menu()) {
                menu.apply(sync);
            }
        });
        event.register(CraftPlanSync.TYPE, (plan, ctx) -> {
            if (ctx.player().containerMenu instanceof TerminalMenu menu && menu.containerId == plan.menu()) {
                menu.plan = plan;
            }
        });
        event.register(CraftJobsSync.TYPE, (jobs, ctx) -> {
            if (ctx.player().containerMenu instanceof TerminalMenu menu && menu.containerId == jobs.menu()) {
                menu.jobs = jobs.jobs();
            }
        });
        event.register(CraftTreeSync.TYPE, (tree, ctx) -> {
            if (ctx.player().containerMenu instanceof TerminalMenu menu && menu.containerId == tree.menu()) {
                if (!tree.job().equals(menu.treeJob)) {
                    menu.treeJob = tree.job();
                    menu.tree = java.util.List.of();
                }
                if (!tree.rows().isEmpty()) {
                    menu.tree = tree.rows();
                }
                menu.treeDone = tree.done();
            }
        });
        event.register(WatchSync.TYPE, (sync, ctx) -> {
            if (ctx.player().containerMenu instanceof WatchPostMenu menu && menu.containerId == sync.menu()) {
                menu.rows = sync.rows();
            }
        });
        event.register(SuitFlight.TYPE, (flight, ctx) -> SuitHud.receive(flight));
        event.register(TerminalAnswer.TYPE, (answer, ctx) -> {
            AgentView.answer = answer;
            AgentView.asking = false;
        });
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.OPERATOR.get(), context -> new MachineRenderer());
        event.registerBlockEntityRenderer(ModBlockEntities.RACK.get(), context -> new RackRenderer());
        event.registerEntityRenderer(ModEntities.AGENT.get(), AgentRenderer::new);
    }

    private static void models(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AgentFrameModel.LAYER, AgentFrameModel::createLayer);
    }

    private static void layers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, Residual.id("lens"), Lens::render);
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, Residual.id("exosuit"), SuitHud::render);
    }
}
