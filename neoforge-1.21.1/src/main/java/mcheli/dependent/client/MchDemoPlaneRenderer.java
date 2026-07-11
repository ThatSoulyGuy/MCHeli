package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.plane.MCP_PlaneInfoManager;
import mcheli.dependent.entity.MchDemoPlane;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * Renders the demo plane's real a-10 model with RETRACTING landing gear — the 1.21.1 rewrite of
 * {@code MCH_RenderAircraft.renderLandingGear}. Each {@code AddPartLG} entry's {@code $lg<i>} group is drawn about its
 * hinge at an angle interpolated from the synced gear state (0 = deployed on the ground, 90 = retracted airborne),
 * scaled by the part's {@code maxRotFactor} and flipped for a {@code reverse} strut.
 *
 * <p>Hatch struts (sin-driven door swing) and secondary rotations are deferred — the a-10 uses neither.
 */
public class MchDemoPlaneRenderer extends MchModelEntityRenderer<MchDemoPlane> {
    private final List<MCH_AircraftInfo.LandingGear> gears;
    private final Set<String> gearGroups;

    public MchDemoPlaneRenderer(EntityRendererProvider.Context context) {
        super(context, "planes/a-10", "textures/planes/a-10.png");
        MCH_AircraftInfo info = MCP_PlaneInfoManager.get("a-10");
        this.gears = info != null ? info.landingGear : List.of();
        Set<String> gg = new HashSet<>();
        for (MCH_AircraftInfo.LandingGear g : this.gears) {
            gg.add(("$" + g.modelName).toLowerCase(Locale.ROOT));
        }
        this.gearGroups = gg;
    }

    @Override
    protected Set<String> dynamicGroupsLower() {
        return this.gearGroups;
    }

    @Override
    protected void renderDynamicParts(MchDemoPlane entity, PoseStack pose, VertexConsumer consumer, int packedLight,
                                      int overlay, float partialTick) {
        if (this.model == null) {
            return;
        }
        // Gear angle 0..90 (no wrap) -> plain lerp, matching the reference rot1 interpolation.
        float gearRot = Mth.lerp(partialTick, entity.getPrevGearAngle(), entity.getGearAngle());
        for (MCH_AircraftInfo.LandingGear g : this.gears) {
            String group = "$" + g.modelName;
            float angle = (g.reverse ? (90.0F - gearRot) : gearRot) * g.maxRotFactor;
            pose.pushPose();
            pose.translate(g.pos.x(), g.pos.y(), g.pos.z());
            pose.mulPose(new Quaternionf().rotateAxis(
                (float) Math.toRadians(angle), (float) g.rot.x(), (float) g.rot.y(), (float) g.rot.z()));
            pose.translate(-g.pos.x(), -g.pos.y(), -g.pos.z());
            MchModelRenderer.renderGroup(this.model, pose, consumer, packedLight, overlay, 255, 255, 255, 255, group);
            pose.popPose();
        }
    }
}
