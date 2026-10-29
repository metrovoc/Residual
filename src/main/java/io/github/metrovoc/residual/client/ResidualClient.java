package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.menu.CoreMenu;
import io.github.metrovoc.residual.net.AgentSync;
import io.github.metrovoc.residual.net.CoreSync;
import io.github.metrovoc.residual.net.SampleResult;
import io.github.metrovoc.residual.net.TerminalAnswer;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModEntities;
import io.github.metrovoc.residual.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

@Mod(value = Residual.MOD_ID, dist = Dist.CLIENT)
public final class ResidualClient {
    public ResidualClient(IEventBus modBus) {
        modBus.addListener(ResidualClient::screens);
        modBus.addListener(ResidualClient::payloads);
        modBus.addListener(ResidualClient::renderers);
        modBus.addListener(ResidualClient::layers);
        modBus.addListener(ResidualClient::models);
    }

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CORE.get(), CoreScreen::new);
        event.register(ModMenus.OPERATOR.get(), OperatorScreen::new);
        event.register(ModMenus.AGENT.get(), AgentScreen::new);
        event.register(ModMenus.READER.get(), ReaderScreen::new);
        event.register(ModMenus.FABRICATOR.get(), FabricatorScreen::new);
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
        event.register(TerminalAnswer.TYPE, (answer, ctx) -> {
            AgentView.answer = answer;
            AgentView.asking = false;
        });
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.OPERATOR.get(), context -> new MachineRenderer());
        event.registerEntityRenderer(ModEntities.AGENT.get(), AgentRenderer::new);
    }

    private static void models(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AgentFrameModel.LAYER, AgentFrameModel::createLayer);
    }

    private static void layers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, Residual.id("lens"), Lens::render);
    }
}
