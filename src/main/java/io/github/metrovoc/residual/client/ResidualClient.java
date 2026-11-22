package io.github.metrovoc.residual.client;

import guideme.Guide;
import guideme.GuideItemSettings;
import guideme.compiler.TagCompiler;
import guideme.siteexport.ExportPostProcessor;
import io.github.metrovoc.residual.Residual;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Residual.MOD_ID, dist = Dist.CLIENT)
public final class ResidualClient {
    public ResidualClient(IEventBus modBus) {
        SpaceView.register(modBus);
        NeoForge.EVENT_BUS.addListener(Clips::tick);
        // the field notes
        Guide.builder(Residual.GUIDE)
                .itemSettings(new GuideItemSettings(Optional.of(Component.translatable("residual.guide_name")),
                        List.of(Component.translatable("residual.guide_tooltip").withStyle(ChatFormatting.DARK_GRAY)),
                        Optional.of(Residual.id("item/guide"))))
                .extension(TagCompiler.EXTENSION_POINT, new Milestones.Tag())
                .extension(TagCompiler.EXTENSION_POINT, new Clips.Tag())
                .extension(TagCompiler.EXTENSION_POINT, new Shots.Tag())
                .extension(ExportPostProcessor.EXTENSION_POINT, new Milestones.Web())
                .extension(ExportPostProcessor.EXTENSION_POINT, new Clips.Web())
                .extension(ExportPostProcessor.EXTENSION_POINT, new NotesWeb())
                .build();
    }
}
