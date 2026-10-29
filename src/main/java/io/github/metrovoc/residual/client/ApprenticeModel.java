package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.Residual;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/** A small brass body with a glass lantern for a head; the mind sits in the lantern. */
public class ApprenticeModel extends EntityModel<ApprenticeRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Residual.id("apprentice"), "main");
    private final ModelPart head = root.getChild("head");
    private final ModelPart core = head.getChild("core");
    private final ModelPart rightArm = root.getChild("right_arm");
    private final ModelPart leftArm = root.getChild("left_arm");
    private final ModelPart rightLeg = root.getChild("right_leg");
    private final ModelPart leftLeg = root.getChild("left_leg");

    public ApprenticeModel(ModelPart root) {
        super(root.getChild("root"), RenderTypes::entityTranslucent);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -7, -2.5f, 8, 7, 5), PartPose.offset(0, -5, 0));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 14).addBox(-3.5f, -7, -3.5f, 7, 7, 7)
                .texOffs(30, 22).addBox(-0.5f, -10, -0.5f, 1, 3, 1), PartPose.offset(0, -12, 0));
        head.addOrReplaceChild("core", CubeListBuilder.create().texOffs(30, 14).addBox(-1.5f, -1.5f, -1.5f, 3, 3, 3), PartPose.offset(0, -3.5f, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 30).addBox(-1.5f, 0, -1.5f, 3, 5, 3), PartPose.offset(-2, -5, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(14, 30).addBox(-1.5f, 0, -1.5f, 3, 5, 3), PartPose.offset(2, -5, 0));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(28, 30).addBox(-2, -0.5f, -1, 2, 6, 2), PartPose.offset(-4, -11.5f, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(38, 30).addBox(0, -0.5f, -1, 2, 6, 2), PartPose.offset(4, -11.5f, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(ApprenticeRenderState s) {
        super.setupAnim(s);
        float walk = s.walkAnimationPos * 0.6662f, speed = s.walkAnimationSpeed;
        rightLeg.xRot = Mth.cos(walk) * 1.4f * speed;
        leftLeg.xRot = Mth.cos(walk + Mth.PI) * 1.4f * speed;
        rightArm.xRot = Mth.cos(walk + Mth.PI) * speed;
        leftArm.xRot = Mth.cos(walk) * speed;
        if (s.swing > 0) {
            rightArm.xRot -= Mth.sin(Mth.sqrt(s.swing) * Mth.PI) * 1.6f;
        }
        head.yRot = s.yRot * Mth.DEG_TO_RAD;
        head.xRot = s.xRot * Mth.DEG_TO_RAD;
        core.yRot = s.ageInTicks * (s.thinking ? 0.35f : 0.05f);
        core.y = -3.5f + (s.thinking ? Mth.sin(s.ageInTicks * 0.5f) * 0.4f : 0);
        core.visible = s.minded;
    }
}
