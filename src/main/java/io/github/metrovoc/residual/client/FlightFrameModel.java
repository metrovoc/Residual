package io.github.metrovoc.residual.client;

import io.github.metrovoc.residual.Residual;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** Twin pods, with independent nozzle and stabilizer pivots. Coordinates are in model pixels. */
public final class FlightFrameModel extends Model<FlightFrameLayer.Motion> {
    public static final ModelLayerLocation BODY = new ModelLayerLocation(Residual.id("flight_frame"), "body");
    public static final ModelLayerLocation LIGHTS = new ModelLayerLocation(Residual.id("flight_frame"), "lights");
    public static final ModelLayerLocation EXHAUST = new ModelLayerLocation(Residual.id("flight_frame"), "exhaust");

    public FlightFrameModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
    }

    public static LayerDefinition createLayer(int pass) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p0 = root.addOrReplaceChild("Back mount", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-3.5f, -5f, -0.5f, 7f, 10f, 1f) : CubeListBuilder.create()), PartPose.offset(0f, 5.5f, 2.6f));
        PartDefinition p1 = root.addOrReplaceChild("Chest bridge", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-2.4f, -0.35f, -0.3f, 4.8f, 0.7f, 0.6f) : CubeListBuilder.create()), PartPose.offset(0f, 6f, -2.5f));
        PartDefinition p2 = root.addOrReplaceChild("Front harness", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-0.5f, -4.5f, -0.3f, 1f, 9f, 0.6f) : CubeListBuilder.create()), PartPose.offset(-2.6f, 4.5f, -2.35f));
        PartDefinition p3 = root.addOrReplaceChild("Front harness.001", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-0.5f, -4.5f, -0.3f, 1f, 9f, 0.6f) : CubeListBuilder.create()), PartPose.offset(2.6f, 4.5f, -2.35f));
        PartDefinition p4 = root.addOrReplaceChild("Harness clasp", (pass == 0 ? CubeListBuilder.create().texOffs(0, 0).addBox(-0.7f, -0.9f, -0.175f, 1.4f, 1.8f, 0.35f) : CubeListBuilder.create()), PartPose.offset(-2.6f, 4f, -2.75f));
        PartDefinition p5 = root.addOrReplaceChild("Harness clasp.001", (pass == 0 ? CubeListBuilder.create().texOffs(0, 0).addBox(-0.7f, -0.9f, -0.175f, 1.4f, 1.8f, 0.35f) : CubeListBuilder.create()), PartPose.offset(2.6f, 4f, -2.75f));
        PartDefinition p6 = root.addOrReplaceChild("Lower cross brace", (pass == 0 ? CubeListBuilder.create().texOffs(32, 0).addBox(-4.5f, -0.5f, -0.5f, 9f, 1f, 1f) : CubeListBuilder.create()), PartPose.offset(0f, 8.5f, 3.3f));
        PartDefinition p7 = root.addOrReplaceChild("Pilot housing", (pass == 0 ? CubeListBuilder.create().texOffs(0, 0).addBox(-1.15f, -2.9f, -1.1f, 2.3f, 5.8f, 2.2f) : CubeListBuilder.create()), PartPose.offset(0f, 5.6f, 3.8f));
        PartDefinition p8 = root.addOrReplaceChild("Pilot inset", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-0.7f, -1.85f, -0.1f, 1.4f, 3.7f, 0.2f) : CubeListBuilder.create()), PartPose.offset(0f, 5.3f, 4.95f));
        PartDefinition p9 = root.addOrReplaceChild("Pilot latch", (pass == 0 ? CubeListBuilder.create().texOffs(0, 32).addBox(-0.45f, -0.25f, -0.15f, 0.9f, 0.5f, 0.3f) : CubeListBuilder.create()), PartPose.offset(0f, 7.7f, 5f));
        PartDefinition p10 = root.addOrReplaceChild("Pilot readout", (pass == 0 || pass == 1 ? CubeListBuilder.create().texOffs(96, 0).addBox(-0.35f, -0.175f, -0.075f, 0.7f, 0.35f, 0.15f) : CubeListBuilder.create()), PartPose.offset(0f, 6f, 5.08f));
        PartDefinition p11 = root.addOrReplaceChild("Pilot readout.001", (pass == 0 || pass == 1 ? CubeListBuilder.create().texOffs(96, 0).addBox(-0.35f, -0.175f, -0.075f, 0.7f, 0.35f, 0.15f) : CubeListBuilder.create()), PartPose.offset(0f, 5f, 5.08f));
        PartDefinition p12 = root.addOrReplaceChild("Pilot readout.002", (pass == 0 || pass == 1 ? CubeListBuilder.create().texOffs(96, 0).addBox(-0.35f, -0.175f, -0.075f, 0.7f, 0.35f, 0.15f) : CubeListBuilder.create()), PartPose.offset(0f, 4f, 5.08f));
        PartDefinition p13 = root.addOrReplaceChild("Port pod", CubeListBuilder.create(), PartPose.offset(-3.7f, 4f, 3.8f));
        PartDefinition p14 = p13.addOrReplaceChild("Cooling louvre", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-1.1f, -0.175f, -0.2f, 2.2f, 0.35f, 0.4f) : CubeListBuilder.create()), PartPose.offset(0f, 2.6f, 2.08f));
        PartDefinition p15 = p13.addOrReplaceChild("Cooling louvre.001", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-1.1f, -0.175f, -0.2f, 2.2f, 0.35f, 0.4f) : CubeListBuilder.create()), PartPose.offset(0f, 1.8f, 2.08f));
        PartDefinition p16 = p13.addOrReplaceChild("Inner shell rail", (pass == 0 ? CubeListBuilder.create().texOffs(0, 0).addBox(-0.325f, -3.9f, -1.85f, 0.65f, 7.8f, 3.7f) : CubeListBuilder.create()), PartPose.offset(1.3f, 0.8f, 0.2f));
        PartDefinition p17 = p13.addOrReplaceChild("Intake crown", (pass == 0 ? CubeListBuilder.create().texOffs(0, 0).addBox(-1.6f, -0.4f, -1.8f, 3.2f, 0.8f, 3.6f) : CubeListBuilder.create()), PartPose.offset(0f, -3.5f, 0f));
        PartDefinition p18 = p13.addOrReplaceChild("Intake slat", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-1.05f, -0.08f, -0.2f, 2.1f, 0.16f, 0.4f) : CubeListBuilder.create()), PartPose.offset(0f, -3.96f, -1f));
        PartDefinition p19 = p13.addOrReplaceChild("Intake slat.001", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-1.05f, -0.08f, -0.2f, 2.1f, 0.16f, 0.4f) : CubeListBuilder.create()), PartPose.offset(0f, -3.96f, 0f));
        PartDefinition p20 = p13.addOrReplaceChild("Intake slat.002", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-1.05f, -0.08f, -0.2f, 2.1f, 0.16f, 0.4f) : CubeListBuilder.create()), PartPose.offset(0f, -3.96f, 1f));
        PartDefinition p21 = p13.addOrReplaceChild("Outer shell rail", (pass == 0 ? CubeListBuilder.create().texOffs(0, 0).addBox(-0.325f, -3.9f, -1.85f, 0.65f, 7.8f, 3.7f) : CubeListBuilder.create()), PartPose.offset(-1.3f, 0.8f, 0.2f));
        PartDefinition p22 = p13.addOrReplaceChild("Pod graphite body", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-1.6f, -4.25f, -1.8f, 3.2f, 8.5f, 3.6f) : CubeListBuilder.create()), PartPose.offset(0f, 1f, 0f));
        PartDefinition p23 = p13.addOrReplaceChild("Rear inset", (pass == 0 ? CubeListBuilder.create().texOffs(32, 0).addBox(-1.05f, -3.25f, -0.075f, 2.1f, 6.5f, 0.15f) : CubeListBuilder.create()), PartPose.offset(0f, 0.4f, 1.85f));
        PartDefinition p24 = p13.addOrReplaceChild("Stabilizer hinge", CubeListBuilder.create(), PartPose.offset(-1.5f, -0.9f, 0f));
        PartDefinition p25 = p24.addOrReplaceChild("Fold-out stabilizer", (pass == 0 ? CubeListBuilder.create().texOffs(32, 0).addBox(-0.8f, -1.5f, -0.275f, 1.6f, 3f, 0.55f) : CubeListBuilder.create()), PartPose.offset(-0.8f, 1.2f, 0f));
        PartDefinition p26 = p24.addOrReplaceChild("Stabilizer tip", (pass == 0 ? CubeListBuilder.create().texOffs(0, 0).addBox(-0.25f, -0.5f, -0.325f, 0.5f, 1f, 0.65f) : CubeListBuilder.create()), PartPose.offset(-1.4f, 2.2f, 0f));
        PartDefinition p27 = p13.addOrReplaceChild("Teal channel", (pass == 0 || pass == 1 ? CubeListBuilder.create().texOffs(96, 0).addBox(-0.325f, -2.1f, -0.075f, 0.65f, 4.2f, 0.15f) : CubeListBuilder.create()), PartPose.offset(0f, -0.4f, 1.98f));
        PartDefinition p28 = p13.addOrReplaceChild("Upper panel", (pass == 0 ? CubeListBuilder.create().texOffs(0, 0).addBox(-1.1f, -0.45f, -0.2f, 2.2f, 0.9f, 0.4f) : CubeListBuilder.create()), PartPose.offset(0f, -2.5f, 2f));
        PartDefinition p29 = p13.addOrReplaceChild("Vector nozzle", CubeListBuilder.create(), PartPose.offset(0f, 4.8f, 0f));
        PartDefinition p30 = p29.addOrReplaceChild("Nozzle collar", (pass == 0 ? CubeListBuilder.create().texOffs(32, 0).addBox(-1.4f, -0.45f, -1.5f, 2.8f, 0.9f, 3f) : CubeListBuilder.create()), PartPose.offset(0f, -0f, 0f));
        PartDefinition p31 = p29.addOrReplaceChild("Nozzle throat", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-1.05f, -0.35f, -1.2f, 2.1f, 0.7f, 2.4f) : CubeListBuilder.create()), PartPose.offset(0f, 0.7f, 0f));
        PartDefinition p32 = p29.addOrReplaceChild("Short thrust core", (pass == 2 ? CubeListBuilder.create().texOffs(32, 32).addBox(-0.4f, -0.7f, -0.5f, 0.8f, 1.4f, 1f) : CubeListBuilder.create()), PartPose.offset(0f, 1.9f, 0f));
        PartDefinition p33 = p29.addOrReplaceChild("Thrust aperture", (pass == 2 ? CubeListBuilder.create().texOffs(0, 32).addBox(-0.7f, -0.06f, -0.85f, 1.4f, 0.12f, 1.7f) : CubeListBuilder.create()), PartPose.offset(0f, 1.08f, 0f));
        PartDefinition p34 = p29.addOrReplaceChild("Thrust tail", (pass == 2 ? CubeListBuilder.create().texOffs(0, 32).addBox(-0.225f, -0.4f, -0.3f, 0.45f, 0.8f, 0.6f) : CubeListBuilder.create()), PartPose.offset(0f, 3f, 0f));
        PartDefinition p35 = root.addOrReplaceChild("Shoulder harness", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-0.5f, -0.3f, -2.75f, 1f, 0.6f, 5.5f) : CubeListBuilder.create()), PartPose.offset(-2.6f, -0.3f, 0f));
        PartDefinition p36 = root.addOrReplaceChild("Shoulder harness.001", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-0.5f, -0.3f, -2.75f, 1f, 0.6f, 5.5f) : CubeListBuilder.create()), PartPose.offset(2.6f, -0.3f, 0f));
        PartDefinition p37 = root.addOrReplaceChild("Starboard pod", CubeListBuilder.create(), PartPose.offset(3.7f, 4f, 3.8f));
        PartDefinition p38 = p37.addOrReplaceChild("Cooling louvre.002", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-1.1f, -0.175f, -0.2f, 2.2f, 0.35f, 0.4f) : CubeListBuilder.create()), PartPose.offset(0f, 2.6f, 2.08f));
        PartDefinition p39 = p37.addOrReplaceChild("Cooling louvre.003", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-1.1f, -0.175f, -0.2f, 2.2f, 0.35f, 0.4f) : CubeListBuilder.create()), PartPose.offset(0f, 1.8f, 2.08f));
        PartDefinition p40 = p37.addOrReplaceChild("Inner shell rail.001", (pass == 0 ? CubeListBuilder.create().texOffs(0, 0).addBox(-0.325f, -3.9f, -1.85f, 0.65f, 7.8f, 3.7f) : CubeListBuilder.create()), PartPose.offset(-1.3f, 0.8f, 0.2f));
        PartDefinition p41 = p37.addOrReplaceChild("Intake crown.001", (pass == 0 ? CubeListBuilder.create().texOffs(0, 0).addBox(-1.6f, -0.4f, -1.8f, 3.2f, 0.8f, 3.6f) : CubeListBuilder.create()), PartPose.offset(0f, -3.5f, 0f));
        PartDefinition p42 = p37.addOrReplaceChild("Intake slat.003", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-1.05f, -0.08f, -0.2f, 2.1f, 0.16f, 0.4f) : CubeListBuilder.create()), PartPose.offset(0f, -3.96f, -1f));
        PartDefinition p43 = p37.addOrReplaceChild("Intake slat.004", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-1.05f, -0.08f, -0.2f, 2.1f, 0.16f, 0.4f) : CubeListBuilder.create()), PartPose.offset(0f, -3.96f, 0f));
        PartDefinition p44 = p37.addOrReplaceChild("Intake slat.005", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-1.05f, -0.08f, -0.2f, 2.1f, 0.16f, 0.4f) : CubeListBuilder.create()), PartPose.offset(0f, -3.96f, 1f));
        PartDefinition p45 = p37.addOrReplaceChild("Outer shell rail.001", (pass == 0 ? CubeListBuilder.create().texOffs(0, 0).addBox(-0.325f, -3.9f, -1.85f, 0.65f, 7.8f, 3.7f) : CubeListBuilder.create()), PartPose.offset(1.3f, 0.8f, 0.2f));
        PartDefinition p46 = p37.addOrReplaceChild("Pod graphite body.001", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-1.6f, -4.25f, -1.8f, 3.2f, 8.5f, 3.6f) : CubeListBuilder.create()), PartPose.offset(0f, 1f, 0f));
        PartDefinition p47 = p37.addOrReplaceChild("Rear inset.001", (pass == 0 ? CubeListBuilder.create().texOffs(32, 0).addBox(-1.05f, -3.25f, -0.075f, 2.1f, 6.5f, 0.15f) : CubeListBuilder.create()), PartPose.offset(0f, 0.4f, 1.85f));
        PartDefinition p48 = p37.addOrReplaceChild("Stabilizer hinge.001", CubeListBuilder.create(), PartPose.offset(1.5f, -0.9f, 0f));
        PartDefinition p49 = p48.addOrReplaceChild("Fold-out stabilizer.001", (pass == 0 ? CubeListBuilder.create().texOffs(32, 0).addBox(-0.8f, -1.5f, -0.275f, 1.6f, 3f, 0.55f) : CubeListBuilder.create()), PartPose.offset(0.8f, 1.2f, 0f));
        PartDefinition p50 = p48.addOrReplaceChild("Stabilizer tip.001", (pass == 0 ? CubeListBuilder.create().texOffs(0, 0).addBox(-0.25f, -0.5f, -0.325f, 0.5f, 1f, 0.65f) : CubeListBuilder.create()), PartPose.offset(1.4f, 2.2f, 0f));
        PartDefinition p51 = p37.addOrReplaceChild("Teal channel.001", (pass == 0 || pass == 1 ? CubeListBuilder.create().texOffs(96, 0).addBox(-0.325f, -2.1f, -0.075f, 0.65f, 4.2f, 0.15f) : CubeListBuilder.create()), PartPose.offset(0f, -0.4f, 1.98f));
        PartDefinition p52 = p37.addOrReplaceChild("Upper panel.001", (pass == 0 ? CubeListBuilder.create().texOffs(0, 0).addBox(-1.1f, -0.45f, -0.2f, 2.2f, 0.9f, 0.4f) : CubeListBuilder.create()), PartPose.offset(0f, -2.5f, 2f));
        PartDefinition p53 = p37.addOrReplaceChild("Vector nozzle.001", CubeListBuilder.create(), PartPose.offset(0f, 4.8f, 0f));
        PartDefinition p54 = p53.addOrReplaceChild("Nozzle collar.001", (pass == 0 ? CubeListBuilder.create().texOffs(32, 0).addBox(-1.4f, -0.45f, -1.5f, 2.8f, 0.9f, 3f) : CubeListBuilder.create()), PartPose.offset(0f, -0f, 0f));
        PartDefinition p55 = p53.addOrReplaceChild("Nozzle throat.001", (pass == 0 ? CubeListBuilder.create().texOffs(64, 0).addBox(-1.05f, -0.35f, -1.2f, 2.1f, 0.7f, 2.4f) : CubeListBuilder.create()), PartPose.offset(0f, 0.7f, 0f));
        PartDefinition p56 = p53.addOrReplaceChild("Short thrust core.001", (pass == 2 ? CubeListBuilder.create().texOffs(32, 32).addBox(-0.4f, -0.7f, -0.5f, 0.8f, 1.4f, 1f) : CubeListBuilder.create()), PartPose.offset(0f, 1.9f, 0f));
        PartDefinition p57 = p53.addOrReplaceChild("Thrust aperture.001", (pass == 2 ? CubeListBuilder.create().texOffs(0, 32).addBox(-0.7f, -0.06f, -0.85f, 1.4f, 0.12f, 1.7f) : CubeListBuilder.create()), PartPose.offset(0f, 1.08f, 0f));
        PartDefinition p58 = p53.addOrReplaceChild("Thrust tail.001", (pass == 2 ? CubeListBuilder.create().texOffs(0, 32).addBox(-0.225f, -0.4f, -0.3f, 0.45f, 0.8f, 0.6f) : CubeListBuilder.create()), PartPose.offset(0f, 3f, 0f));
        PartDefinition p59 = root.addOrReplaceChild("Upper cross brace", (pass == 0 ? CubeListBuilder.create().texOffs(32, 0).addBox(-4.5f, -0.6f, -0.5f, 9f, 1.2f, 1f) : CubeListBuilder.create()), PartPose.offset(0f, 3f, 3.3f));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(FlightFrameLayer.Motion motion) {
        root().getAllParts().forEach(ModelPart::resetPose);
        for (int side : new int[] {-1, 1}) {
            ModelPart pod = root().getChild(side < 0 ? "Port pod" : "Starboard pod");
            ModelPart nozzle = pod.getChild(side < 0 ? "Vector nozzle" : "Vector nozzle.001");
            ModelPart vane = pod.getChild(side < 0 ? "Stabilizer hinge" : "Stabilizer hinge.001");
            nozzle.xRot = motion.thrust() * (0.12f + motion.pitch());
            nozzle.zRot = motion.thrust() * (motion.bank() - side * 0.045f);
            vane.zRot = side * motion.thrust() * 0.48f;
            String suffix = side < 0 ? "" : ".001";
            nozzle.getChild("Short thrust core" + suffix).yScale = motion.thrust();
            nozzle.getChild("Thrust tail" + suffix).yScale = motion.thrust();
        }
    }
}
