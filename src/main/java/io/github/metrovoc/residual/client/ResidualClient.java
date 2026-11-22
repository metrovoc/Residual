package io.github.metrovoc.residual.client;

import guideme.Guide;
import guideme.GuideItemSettings;
import guideme.compiler.TagCompiler;
import guideme.siteexport.ExportPostProcessor;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.menu.CoreMenu;
import io.github.metrovoc.residual.menu.InstrumentMenu;
import io.github.metrovoc.residual.net.ComputeSync;
import io.github.metrovoc.residual.net.CoreSync;
import io.github.metrovoc.residual.net.EvaluationSync;
import io.github.metrovoc.residual.net.SampleResult;
import io.github.metrovoc.residual.net.ScopeReading;
import io.github.metrovoc.residual.net.ScopeSync;
import io.github.metrovoc.residual.net.ShimmerSync;
import io.github.metrovoc.residual.net.StreamMarks;
import io.github.metrovoc.residual.net.WaveSync;
import io.github.metrovoc.residual.registry.ModBlockEntities;
import io.github.metrovoc.residual.registry.ModMenus;
import io.github.metrovoc.residual.scope.Instrument;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
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
        NeoForge.EVENT_BUS.addListener(Waves::extract);
        NeoForge.EVENT_BUS.addListener(Waves::submit);
        HallView.register(modBus);
        NeoForge.EVENT_BUS.addListener(ShimmerView::extract);
        NeoForge.EVENT_BUS.addListener(ShimmerView::submit);
        NeoForge.EVENT_BUS.addListener(StreamView::extract);
        NeoForge.EVENT_BUS.addListener(StreamView::submit);
        NeoForge.EVENT_BUS.addListener(Clips::tick);
        // the field notes
        Guide.builder(Residual.GUIDE)
                .itemSettings(new GuideItemSettings(Optional.of(Component.translatable("residual.guide_name")),
                        List.of(Component.translatable("residual.guide_tooltip").withStyle(ChatFormatting.DARK_GRAY)),
                        Optional.of(Residual.id("item/guide"))))
                .extension(TagCompiler.EXTENSION_POINT, new Milestones.Tag())
                .extension(TagCompiler.EXTENSION_POINT, new Clips.Tag())
                .extension(TagCompiler.EXTENSION_POINT, new Steps.Tag())
                .extension(TagCompiler.EXTENSION_POINT, new Shots.Tag())
                .extension(TagCompiler.EXTENSION_POINT, new Live.SoftmaxTag())
                .extension(TagCompiler.EXTENSION_POINT, new Live.AttentionTag())
                .extension(TagCompiler.EXTENSION_POINT, new Live.CurveTag())
                .extension(ExportPostProcessor.EXTENSION_POINT, new Milestones.Web())
                .extension(ExportPostProcessor.EXTENSION_POINT, new Clips.Web())
                .extension(ExportPostProcessor.EXTENSION_POINT, new NotesWeb())
                .build();
    }

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CORE.get(), CoreScreen::new);
        event.register(ModMenus.OPERATOR.get(), OperatorScreen::new);
        event.register(ModMenus.READER.get(), ReaderScreen::new);
        event.register(ModMenus.INSTRUMENT.get(), InstrumentScreen::new);
        event.register(ModMenus.FORGE.get(), ForgeScreen::new);
        event.register(ModMenus.FORECASTER.get(), ForecasterScreen::new);
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
        event.register(WaveSync.TYPE, (wave, ctx) -> Waves.receive(wave));
        event.register(ShimmerSync.TYPE, (shimmer, ctx) -> ShimmerView.receive(shimmer));
        event.register(ComputeSync.TYPE, (sync, ctx) -> {
            if (ctx.player().containerMenu instanceof CoreMenu menu && menu.pos.equals(sync.pos())) {
                CoreView.compute = sync;
            }
        });
        event.register(ScopeSync.TYPE, (sync, ctx) -> {
            if (ctx.player().containerMenu instanceof InstrumentMenu menu && menu.pos.equals(sync.pos())) {
                InstrumentScreen.sync = sync;
            }
        });
        event.register(ScopeReading.TYPE, (reading, ctx) -> {
            if (ctx.player().containerMenu instanceof InstrumentMenu menu && menu.pos.equals(reading.pos())) {
                InstrumentScreen.PICTURES.put(Instrument.values()[reading.instrument()], reading.picture());
            }
        });
        event.register(StreamMarks.TYPE, (marks, ctx) -> StreamView.receive(marks));
        event.register(EvaluationSync.TYPE, (evaluation, ctx) -> {
            if (ctx.player().containerMenu instanceof CoreMenu menu && menu.pos.equals(evaluation.pos())) {
                CoreView.evaluation = evaluation;
            }
        });
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.OPERATOR.get(), context -> new MachineRenderer());
    }

    private static void layers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, Residual.id("lens"), Lens::render);
    }
}
