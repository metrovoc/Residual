package io.github.metrovoc.residual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.metrovoc.residual.Residual;
import io.github.metrovoc.residual.item.FlightFrameItem;
import io.github.metrovoc.residual.registry.ModAttachments;
import io.github.metrovoc.residual.registry.ModItems;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Avatar;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/** The same frame on players, mannequins and humanoid armour stands, through vanilla's submission pipeline. */
public final class FlightFrameLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    private static final Identifier TEXTURE = Residual.id("textures/entity/flight_frame.png");
    private static final ContextKey<Motion> MOTION = new ContextKey<>(Residual.id("flight_frame_motion"));
    private static final Map<Avatar, Smoothing> MOTIONS = new WeakHashMap<>();
    public record Motion(float thrust, float pitch, float bank) {
        static final Motion IDLE = new Motion(0, 0, 0);
    }
    private static final class Smoothing {
        float time = -1, thrust;
    }
    private final FlightFrameModel body, lights, exhaust;

    public FlightFrameLayer(RenderLayerParent<S, M> parent, EntityModelSet models) {
        super(parent);
        body = new FlightFrameModel(models.bakeLayer(FlightFrameModel.BODY));
        lights = new FlightFrameModel(models.bakeLayer(FlightFrameModel.LIGHTS));
        exhaust = new FlightFrameModel(models.bakeLayer(FlightFrameModel.EXHAUST));
    }

    public static void modifiers(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                boolean wearing = state.chestEquipment.is(ModItems.FLIGHT_FRAME.get());
                if (!wearing) {
                    MOTIONS.remove(avatar);
                    state.setRenderData(MOTION, Motion.IDLE);
                    return;
                }
                boolean active = avatar.getData(ModAttachments.FRAME_FLYING) && FlightFrameItem.holds(state.chestEquipment);
                Smoothing motion = MOTIONS.computeIfAbsent(avatar, a -> new Smoothing());
                float now = state.ageInTicks;
                float dt = motion.time < 0 ? 1 : Mth.clamp(now - motion.time, 0, 10);
                motion.time = now;
                motion.thrust = Mth.approach(motion.thrust, active ? 1 : 0, dt / 6);
                float yaw = avatar.yBodyRot * Mth.DEG_TO_RAD;
                var velocity = avatar.getDeltaMovement();
                float forward = (float)(-velocity.x * Mth.sin(yaw) + velocity.z * Mth.cos(yaw));
                float sideways = (float)(velocity.x * Mth.cos(yaw) + velocity.z * Mth.sin(yaw));
                state.setRenderData(MOTION, new Motion(motion.thrust, Mth.clamp(forward, -0.25f, 0.25f),
                        Mth.clamp(sideways, -0.2f, 0.2f)));
            }
        });
    }

    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            add(event.getPlayerRenderer(skin), event.getEntityModels());
            add(event.getMannequinRenderer(skin), event.getEntityModels());
        }
        for (var type : event.getEntityTypes()) {
            if (event.getRenderer(type) instanceof LivingEntityRenderer<?, ?, ?> renderer) {
                add(renderer, event.getEntityModels());
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void add(LivingEntityRenderer renderer, EntityModelSet models) {
        if (renderer != null && renderer.getModel() instanceof HumanoidModel) {
            renderer.addLayer(new FlightFrameLayer(renderer, models));
        }
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, S state, float yaw, float pitch) {
        if (!state.chestEquipment.is(ModItems.FLIGHT_FRAME.get()) || state.isInvisible) {
            return;
        }
        Motion motion = state.getRenderData(MOTION);
        if (motion == null) motion = Motion.IDLE;
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        collector.submitModel(body, motion, pose, RenderTypes.entityCutout(TEXTURE), light,
                LivingEntityRenderer.getOverlayCoords(state, 0), -1, null, state.outlineColor, null);
        if (FlightFrameItem.holds(state.chestEquipment)) {
            collector.submitModel(lights, motion, pose, RenderTypes.entityCutout(TEXTURE), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null);
        }
        if (motion.thrust() > 0.01f && FlightFrameItem.holds(state.chestEquipment)) {
            collector.submitModel(exhaust, motion, pose, RenderTypes.entityCutout(TEXTURE), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null);
        }
        pose.popPose();
    }
}
