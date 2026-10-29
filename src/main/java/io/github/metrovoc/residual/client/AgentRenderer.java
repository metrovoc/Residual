package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.agent.Agent;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** Draws an agent, with what it last decided over its head for a moment. */
public class AgentRenderer extends MobRenderer<Agent, AgentRenderState, AgentFrameModel> {
    private static final Identifier TEXTURE = Residual.id("textures/entity/agent.png");

    public AgentRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new AgentFrameModel(ctx.bakeLayer(AgentFrameModel.LAYER)), 0.35f);
    }

    @Override
    public AgentRenderState createRenderState() {
        return new AgentRenderState();
    }

    @Override
    public void extractRenderState(Agent a, AgentRenderState s, float partialTicks) {
        super.extractRenderState(a, s, partialTicks);
        s.modeled = a.modeled();
        s.thinking = a.thinking();
        s.swing = a.getAttackAnim(partialTicks);
    }

    @Override
    public Identifier getTextureLocation(AgentRenderState s) {
        return TEXTURE;
    }

    private static boolean speaking(Agent a) {
        return a.tickCount - a.saidAt < Agent.SHOWN && !a.said().getString().isEmpty();
    }

    @Override
    protected boolean shouldShowName(Agent a, double distanceToCameraSq) {
        return speaking(a) || super.shouldShowName(a, distanceToCameraSq);
    }

    @Override
    protected @Nullable Component getNameTag(Agent a) {
        return speaking(a) ? a.said() : super.getNameTag(a);
    }
}
