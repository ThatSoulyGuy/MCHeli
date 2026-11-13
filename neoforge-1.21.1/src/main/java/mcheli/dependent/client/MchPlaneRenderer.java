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
 * per config and cached. Each {@code $lg<i>} group rotates about its hinge from the gear part's state (0 deployed .. 90
 * retracted; {@code MchVehicleParts.getGearAngle}), scaled by {@code maxRotFactor}, flipped for a {@code reverse}
 * strut, with the reference hatch-door curve, second hinge ({@code rot2}) and {@code slide} travel.
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
        // The VTOL nozzles and the folding/sweeping wings (+ their pylons) — drawn swivelled by renderNozzlesAndWings.
        if (info instanceof MCP_PlaneInfo pi) {
            for (MCH_AircraftInfo.DrawnPart n : pi.nozzles) {
                gg.add(("$" + n.modelName).toLowerCase(Locale.ROOT));
            }
            for (MCP_PlaneInfo.Wing w : pi.wingList) {
                gg.add(("$" + w.modelName).toLowerCase(Locale.ROOT));
                if (w.pylonList != null) {
                    for (MCP_PlaneInfo.Pylon py : w.pylonList) {
                        gg.add(("$" + py.modelName).toLowerCase(Locale.ROOT));
                    }
                }
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
        // Reference MCH_RenderAircraft.renderLandingGear:968-1010. The gear angle 0 (down) .. 90 (up) is the gear part's
        // real state (the pilot's gear key + the automatic retract/extend), plain-lerped (no wrap). A reverse strut uses
        // 90 - angle; a hatch door swings 90*sin(2*angle)*3 (capped at 90), so it opens and closes within the first and
        // last 15 degrees of the travel; rot2 adds a second hinge, and slide a linear travel by the unlerped fraction.
        float rot = entity.vehicleParts().getGearAngle();
        float prevRot = entity.vehicleParts().getPrevGearAngle();
        float rot1 = prevRot + (rot - prevRot) * partialTick;
        float rot1Rev = (90.0F - prevRot) + ((90.0F - rot) - (90.0F - prevRot)) * partialTick;
        float rotHatch = Math.min(90.0F, 90.0F * Mth.sin(rot1 * 2.0F * (float) Math.PI / 180.0F) * 3.0F);
        for (MCH_AircraftInfo.LandingGear g : meta(entity).gears()) {
            String group = "$" + g.modelName;
            float angle = !g.reverse ? (!g.hatch ? rot1 : rotHatch) : rot1Rev;
            pose.pushPose();
            pose.translate(g.pos.x(), g.pos.y(), g.pos.z());
            pose.mulPose(new Quaternionf().rotateAxis(
                (float) Math.toRadians(angle * g.maxRotFactor), (float) g.rot.x(), (float) g.rot.y(), (float) g.rot.z()));
            if (g.enableRot2) {
                float angle2 = (!g.reverse ? rot1 : rot1Rev) * g.maxRotFactor2;
                pose.mulPose(new Quaternionf().rotateAxis(
                    (float) Math.toRadians(angle2), (float) g.rot2.x(), (float) g.rot2.y(), (float) g.rot2.z()));
            }
            pose.translate(-g.pos.x(), -g.pos.y(), -g.pos.z());
            if (g.slide != null) {
                float f = g.reverse ? 1.0F - rot / 90.0F : rot / 90.0F;
                pose.translate(f * g.slide.x(), f * g.slide.y(), f * g.slide.z());
            }
            MchModelRenderer.renderPartSpan(model, pose, consumer, packedLight, overlay, c, c, c, 255,
                group, java.util.Collections.emptySet());
            pose.popPose();
        }
        // Weapon parts ($weaponN) aimed by the rider's free look (gun pods pitch, a gatling gun spins).
        renderWeaponParts(entity, model, pose, consumer, packedLight, overlay, partialTick);
    }

    /** Reference {@code MCP_RenderPlane.renderAircraft} order: {@code renderNozzle}, {@code renderWing},
     *  {@code renderRotor}, THEN {@code renderBody}. Drawn ahead of the {@code $body} span, so the body's tinted glass
     *  blends over a propeller or nozzle behind it instead of hiding it (one unsorted blended pass, depth write on). */
    @Override
    protected void renderPreBodyParts(MchPlane entity, MchModel model, PoseStack pose, VertexConsumer consumer,
                                      int packedLight, int overlay, float partialTick) {
        int c = wreckColor(entity);
        renderNozzlesAndWings(entity, model, pose, consumer, packedLight, overlay, partialTick, c);
        renderRotors(entity, model, pose, consumer, packedLight, overlay, partialTick, c);
    }

    /**
     * Spin the propeller(s) — the port of {@code MCP_RenderPlane.renderRotor}. Each config {@code Rotor} (from
     * {@code AddPartRotor}) draws its hub, then each {@code Blade} (from {@code AddBlade}) is spun by the shared
     * {@link mcheli.dependent.entity.MchVehicleAnimation#rotorSpin() rotorSpin} about the blade's config axis and drawn
     * {@code numBlade} times, each copy offset a further {@code rotBlade} degrees. All geometry, axes and counts come
     * from config; the spin rate is the config {@code RotorSpeed}. Jets have no {@code rotorList}, so nothing spins.
     */
    private void renderRotors(MchPlane entity, MchModel model, PoseStack pose, VertexConsumer consumer,
                              int packedLight, int overlay, float partialTick, int c) {
        List<MCP_PlaneInfo.Rotor> rotors = meta(entity).rotors();
        if (rotors.isEmpty()) {
            return;
        }
        float spin = calcRot(entity.animation().rotorSpin(), entity.animation().prevRotorSpin(), partialTick);
        // The synced VTOL nozzle angle (reference getNozzleRotation, 0 forward .. 90 hover) tilts the rotor hubs: the
        // MV-22's prop-rotors swing from forward flight to hover. It stays 0 on a non-VTOL plane, so its hubs never tilt.
        float nozzle = entity.nozzleRotationLerp(partialTick);
        for (MCP_PlaneInfo.Rotor r : rotors) {
            pose.pushPose();
            // Hub: translate to the rotor pivot, tilt by nozzle×maxRotFactor about its axis, translate back, draw. The
            // blades inherit this frame (the whole assembly tilts with the nozzle).
            pose.translate(r.pos.x(), r.pos.y(), r.pos.z());
            if (nozzle * r.maxRotFactor != 0.0F) {
                pose.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(nozzle * r.maxRotFactor),
                    (float) r.rot.x(), (float) r.rot.y(), (float) r.rot.z()));
            }
            pose.translate(-r.pos.x(), -r.pos.y(), -r.pos.z());
            MchModelRenderer.renderPartSpan(model, pose, consumer, packedLight, overlay, c, c, c, 255,
                "$" + r.modelName);
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
                        "$" + b.modelName);
                }
                pose.popPose();
            }
            pose.popPose();
        }
    }
    /**
     * The VTOL nozzles and the wing fold — the port of {@code MCP_RenderPlane.renderNozzle} and {@code renderWing}. Each
     * {@code AddPartNozzle} swivels by the synced nozzle angle about its own axis (no factor, as in the reference); each
     * {@code AddPartWing} turns by the wing fold/sweep angle × its {@code maxRotFactor}, and its pylons turn inside the
     * wing's frame by the same angle × their own factor, so a store stays aligned on a sweeping wing.
     */
    private void renderNozzlesAndWings(MchPlane entity, MchModel model, PoseStack pose, VertexConsumer consumer,
                                       int packedLight, int overlay, float partialTick, int c) {
        if (!(entity.hostInfo() instanceof MCP_PlaneInfo pi)) {
            return;
        }
        float nozzle = entity.nozzleRotationLerp(partialTick);
        for (MCH_AircraftInfo.DrawnPart n : pi.nozzles) {
            pose.pushPose();
            pose.translate(n.pos.x(), n.pos.y(), n.pos.z());
            rotate(pose, nozzle, n.rot);
            pose.translate(-n.pos.x(), -n.pos.y(), -n.pos.z());
            MchModelRenderer.renderPartSpan(model, pose, consumer, packedLight, overlay, c, c, c, 255, "$" + n.modelName);
            pose.popPose();
        }
        float wing = entity.vehicleParts().wingRotation(partialTick);
        for (MCP_PlaneInfo.Wing w : pi.wingList) {
            pose.pushPose();
            pose.translate(w.pos.x(), w.pos.y(), w.pos.z());
            rotate(pose, wing * w.maxRotFactor, w.rot);
            pose.translate(-w.pos.x(), -w.pos.y(), -w.pos.z());
            MchModelRenderer.renderPartSpan(model, pose, consumer, packedLight, overlay, c, c, c, 255, "$" + w.modelName);
            if (w.pylonList != null) {
                for (MCP_PlaneInfo.Pylon py : w.pylonList) {
                    pose.pushPose();
                    pose.translate(py.pos.x(), py.pos.y(), py.pos.z());
                    rotate(pose, wing * py.maxRotFactor, py.rot);
                    pose.translate(-py.pos.x(), -py.pos.y(), -py.pos.z());
                    MchModelRenderer.renderPartSpan(model, pose, consumer, packedLight, overlay, c, c, c, 255,
                        "$" + py.modelName);
                    pose.popPose();
                }
            }
            pose.popPose();
        }
    }
}
