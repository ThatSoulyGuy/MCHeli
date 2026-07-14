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
import mcheli.agnostic.plane.MCP_PlaneInfo;
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

    private record PlaneMeta(Set<String> gearGroups, List<MCH_AircraftInfo.LandingGear> gears,
                             List<MCP_PlaneInfo.Rotor> rotors) {}
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
        List<MCP_PlaneInfo.Rotor> rotors = info instanceof MCP_PlaneInfo pi ? pi.rotorList : List.of();
        Set<String> gg = new HashSet<>();
        for (MCH_AircraftInfo.LandingGear g : gears) {
            gg.add(("$" + g.modelName).toLowerCase(Locale.ROOT));
        }
        // The propeller rotor hub + blade groups — excluded from the static hull so they draw SPINNING below, not frozen.
        for (MCP_PlaneInfo.Rotor r : rotors) {
            gg.add(("$" + r.modelName).toLowerCase(Locale.ROOT));
            for (MCP_PlaneInfo.Blade b : r.blades) {
                gg.add(("$" + b.modelName).toLowerCase(Locale.ROOT));
            }
        }
        // The $weaponN weapon parts — excluded from the static hull so renderWeaponParts can draw them AIMED.
        gg.addAll(weaponGroupsLower(info));
        return new PlaneMeta(gg, gears, rotors);
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
        renderRotors(entity, model, pose, consumer, packedLight, overlay, partialTick, c);
        // Weapon parts ($weaponN) aimed by the rider's free look (gun pods pitch, a gatling gun spins).
        renderWeaponParts(entity, model, pose, consumer, packedLight, overlay, partialTick);
    }

    /**
     * Spin the propeller(s) — the port of {@code MCP_RenderPlane.renderRotor}. Each config {@code Rotor} (from
     * {@code AddPartRotor}) draws its hub, then each {@code Blade} (from {@code AddBlade}) is spun by the shared
     * {@link mcheli.dependent.entity.AbstractMchVehicle#rotorSpin() rotorSpin} about the blade's config axis and drawn
     * {@code numBlade} times, each copy offset a further {@code rotBlade} degrees. All geometry, axes and counts come
     * from config; the spin rate is the config {@code RotorSpeed}. Jets have no {@code rotorList}, so nothing spins.
     */
    private void renderRotors(MchPlane entity, MchModel model, PoseStack pose, VertexConsumer consumer,
                              int packedLight, int overlay, float partialTick, int c) {
        List<MCP_PlaneInfo.Rotor> rotors = meta(entity).rotors();
        if (rotors.isEmpty()) {
            return;
        }
        float spin = calcRot(entity.rotorSpin(), entity.prevRotorSpin(), partialTick);
        float nozzle = 0.0F; // VTOL thrust-vectoring (the reference's getNozzleRotation) is unported -> no hub tilt
        for (MCP_PlaneInfo.Rotor r : rotors) {
            pose.pushPose();
            // Hub: translate to the rotor pivot, tilt by nozzle×maxRotFactor about its axis, translate back, draw. The
            // blades inherit this frame (the whole assembly tilts with the nozzle). With nozzle=0 this is a no-op tilt.
            pose.translate(r.pos.x(), r.pos.y(), r.pos.z());
            if (nozzle * r.maxRotFactor != 0.0F) {
                pose.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(nozzle * r.maxRotFactor),
                    (float) r.rot.x(), (float) r.rot.y(), (float) r.rot.z()));
            }
            pose.translate(-r.pos.x(), -r.pos.y(), -r.pos.z());
            MchModelRenderer.renderPartSpan(model, pose, consumer, packedLight, overlay, c, c, c, 255,
                "$" + r.modelName, java.util.Collections.emptySet());
            for (MCP_PlaneInfo.Blade b : r.blades) {
                pose.pushPose();
                // Spin the blade assembly by rotorSpin about the blade's config axis, pivoted at the blade position.
                pose.translate(b.pos.x(), b.pos.y(), b.pos.z());
                pose.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(spin),
                    (float) b.rot.x(), (float) b.rot.y(), (float) b.rot.z()));
                pose.translate(-b.pos.x(), -b.pos.y(), -b.pos.z());
                // numBlade copies of the ONE blade model, each a further rotBlade around (cumulative — the reference's
                // no-pop loop), so a single $blade group makes a 2-/3-/4-blade propeller.
                for (int i = 0; i < b.numBlade; i++) {
                    pose.translate(b.pos.x(), b.pos.y(), b.pos.z());
                    if (b.rotBlade != 0) {
                        pose.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(b.rotBlade),
                            (float) b.rot.x(), (float) b.rot.y(), (float) b.rot.z()));
                    }
                    pose.translate(-b.pos.x(), -b.pos.y(), -b.pos.z());
                    MchModelRenderer.renderPartSpan(model, pose, consumer, packedLight, overlay, c, c, c, 255,
                        "$" + b.modelName, java.util.Collections.emptySet());
                }
                pose.popPose();
            }
            pose.popPose();
        }
    }
}
