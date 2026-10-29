package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.apprentice.Apprentice;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** Draws an apprentice, with what it last decided over its head for a moment. */
public class ApprenticeRenderer extends MobRenderer<Apprentice, ApprenticeRenderState, ApprenticeModel> {
    private static final Identifier TEXTURE = Residual.id("textures/entity/apprentice.png");

    public ApprenticeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ApprenticeModel(ctx.bakeLayer(ApprenticeModel.LAYER)), 0.35f);
    }

    @Override
    public ApprenticeRenderState createRenderState() {
        return new ApprenticeRenderState();
    }

    @Override
    public void extractRenderState(Apprentice a, ApprenticeRenderState s, float partialTicks) {
        super.extractRenderState(a, s, partialTicks);
        s.minded = a.minded();
        s.thinking = a.thinking();
        s.swing = a.getAttackAnim(partialTicks);
    }

    @Override
    public Identifier getTextureLocation(ApprenticeRenderState s) {
        return TEXTURE;
    }

    private static boolean speaking(Apprentice a) {
        return a.tickCount - a.saidAt < Apprentice.SHOWN && !a.said().getString().isEmpty();
    }

    @Override
    protected boolean shouldShowName(Apprentice a, double distanceToCameraSq) {
        return speaking(a) || super.shouldShowName(a, distanceToCameraSq);
    }

    @Override
    protected @Nullable Component getNameTag(Apprentice a) {
        return speaking(a) ? a.said() : super.getNameTag(a);
    }
}
