package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.plane.MCP_PlaneInfoManager;
import mcheli.dependent.entity.MchPlane;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

/**
 * Renders every plane with RETRACTING landing gear — the 1.21.1 rewrite of {@code MCH_RenderAircraft.renderLandingGear}.
 * Which plane it is comes from the entity's config name; the gear list ({@code AddPartLG}) + gear groups are resolved
 * per config and cached. Each {@code $lg<i>} group rotates about its hinge from the synced gear state (0 deployed .. 90
 * retracted), scaled by {@code maxRotFactor} and flipped for a {@code reverse} strut.
 */
public class MchPlaneRenderer extends MchModelEntityRenderer<MchPlane> {

    private record PlaneMeta(Set<String> gearGroups, List<MCH_AircraftInfo.LandingGear> gears) {}
    private final Map<String, PlaneMeta> cache = new HashMap<>();

    public MchPlaneRenderer(EntityRendererProvider.Context context) {
        super(context, "planes");
    }

    private PlaneMeta meta(MchPlane entity) {
        return this.cache.computeIfAbsent(entity.configName(), MchPlaneRenderer::build);
    }

    private static PlaneMeta build(String name) {
        MCH_AircraftInfo info = MCP_PlaneInfoManager.get(name);
        List<MCH_AircraftInfo.LandingGear> gears = info != null ? info.landingGear : List.of();
        Set<String> gg = new HashSet<>();
        for (MCH_AircraftInfo.LandingGear g : gears) {
            gg.add(("$" + g.modelName).toLowerCase(Locale.ROOT));
        }
        // The $weaponN weapon parts — excluded from the static hull so renderWeaponParts can draw them AIMED.
        gg.addAll(weaponGroupsLower(info));
        return new PlaneMeta(gg, gears);
    }

    @Override
    protected Set<String> dynamicGroupsLower(MchPlane entity, MchModel model) {
        return meta(entity).gearGroups();
    }

    @Override
    protected void renderDynamicParts(MchPlane entity, MchModel model, PoseStack pose, VertexConsumer consumer,
                                      int packedLight, int overlay, float partialTick) {
        int c = wreckColor(entity);
        // Gear angle 0..90 (no wrap) -> plain lerp, matching the reference rot1 interpolation.
        float gearRot = Mth.lerp(partialTick, entity.getPrevGearAngle(), entity.getGearAngle());
        for (MCH_AircraftInfo.LandingGear g : meta(entity).gears()) {
            String group = "$" + g.modelName;
            float angle = (g.reverse ? (90.0F - gearRot) : gearRot) * g.maxRotFactor;
            pose.pushPose();
            pose.translate(g.pos.x(), g.pos.y(), g.pos.z());
            pose.mulPose(new Quaternionf().rotateAxis(
                (float) Math.toRadians(angle), (float) g.rot.x(), (float) g.rot.y(), (float) g.rot.z()));
            pose.translate(-g.pos.x(), -g.pos.y(), -g.pos.z());
            MchModelRenderer.renderPartSpan(model, pose, consumer, packedLight, overlay, c, c, c, 255,
                group, java.util.Collections.emptySet());
            pose.popPose();
        }
        // Weapon parts ($weaponN) aimed by the rider's free look (gun pods pitch, a gatling gun spins).
        renderWeaponParts(entity, model, pose, consumer, packedLight, overlay, partialTick);
    }
}
