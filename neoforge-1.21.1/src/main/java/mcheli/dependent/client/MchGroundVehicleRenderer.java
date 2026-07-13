package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.vehicle.MCH_VehicleInfo;
import mcheli.agnostic.vehicle.MCH_VehicleInfoManager;
import mcheli.dependent.entity.MchGroundVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Renders every ground vehicle ({@code vehicles/*}: the Phalanx CIWS, VADS, 46cm cannon, SAM launchers, …) from its real
 * model. These are static emplacements — the base does not move — but they are RIDDEN and AIMED: the config's
 * {@code AddPart}/{@code AddChildPart} tree ({@code $partN}, {@code $partN_M}) is a turret that yaws and elevates to
 * follow the gunner's free look, the 1.21.1 port of {@code MCH_RenderVehicle.drawPart}. A {@code RotationYaw} part swings
 * to the rider's head yaw about its mount; a {@code RotationPitch} part elevates to the rider's look pitch, clamped to
 * the config's {@code MinRotationPitch}/{@code MaxRotationPitch}; child parts inherit their parent's aimed frame.
 */
public class MchGroundVehicleRenderer extends MchModelEntityRenderer<MchGroundVehicle> {

    /** Per-config turret data: the animated {@code $partN} groups (excluded from the static hull), the top-level part
     *  tree, and the elevation clamp shared by every pitching part. */
    private record VehicleMeta(Set<String> partGroups, List<MCH_VehicleInfo.VPart> topParts, float minPitch, float maxPitch) {}

    private final Map<String, VehicleMeta> cache = new HashMap<>();

    public MchGroundVehicleRenderer(EntityRendererProvider.Context context) {
        super(context, "vehicles");
    }

    private VehicleMeta meta(MchGroundVehicle entity) {
        return this.cache.computeIfAbsent(entity.configName(), MchGroundVehicleRenderer::build);
    }

    private static VehicleMeta build(String name) {
        MCH_VehicleInfo info = MCH_VehicleInfoManager.get(name);
        List<MCH_VehicleInfo.VPart> top = info != null ? info.partList : Collections.emptyList();
        Set<String> groups = new HashSet<>();
        for (MCH_VehicleInfo.VPart p : top) {
            collectGroups(p, groups);
        }
        float minP = info != null ? info.minRotationPitch : 0.0F;
        float maxP = info != null ? info.maxRotationPitch : 0.0F;
        return new VehicleMeta(groups, top, minP, maxP);
    }

    /** Every part group ({@code $partN} + nested {@code $partN_M}), lower-cased, so the hull pass skips them. */
    private static void collectGroups(MCH_VehicleInfo.VPart p, Set<String> out) {
        out.add(("$" + p.modelName).toLowerCase(Locale.ROOT));
        if (p.child != null) {
            for (MCH_VehicleInfo.VPart c : p.child) {
                collectGroups(c, out);
            }
        }
    }

    @Override
    protected Set<String> dynamicGroupsLower(MchGroundVehicle entity, MchModel model) {
        return meta(entity).partGroups();
    }

    @Override
    protected void renderDynamicParts(MchGroundVehicle entity, MchModel model, PoseStack pose, VertexConsumer consumer,
                                      int packedLight, int overlay, float partialTick) {
        VehicleMeta m = meta(entity);
        if (m.topParts().isEmpty()) {
            return;
        }
        // Turret aim from the gunner's free look. Yaw is RELATIVE to the (static) base; pitch is the ABSOLUTE look pitch
        // clamped to the elevation limits MINUS the hull render pitch (reference drawPart: RNG(lastRiderPitch,min,max) -
        // pitch — the pose is already hull-pitched, so subtracting keeps the gun's elevation world-absolute). The aim is
        // LATCHED so a dismounted emplacement holds its last pose (reference isUsedPlayer/lastRiderYaw).
        float turretYaw = 0.0F;
        float lookPitch = 0.0F;
        Entity rider = entity.getFirstPassenger();
        if (rider != null) {
            float headYaw = rider.getYHeadRot();
            float headYawO = rider instanceof LivingEntity le ? le.yHeadRotO : headYaw;
            turretYaw = calcRot(Mth.wrapDegrees(headYaw - entity.getYRot()),
                Mth.wrapDegrees(headYawO - entity.yRotO), partialTick);
            lookPitch = Mth.lerp(partialTick, rider.xRotO, rider.getXRot());
            entity.latchAim(turretYaw, lookPitch);
        } else if (entity.hasAimLatch()) {
            turretYaw = entity.latchedAimYaw();
            lookPitch = entity.latchedAimPitch();
        }
        float hullPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        float pitch = Mth.clamp(lookPitch, m.minPitch(), m.maxPitch()) - hullPitch;
        // First-person cockpit hide: a part whose config {@code DrawFirstPerson} flag is FALSE is skipped only for the
        // LOCAL player who is piloting this emplacement in first person (so the gun mount right at the camera does not
        // wall off the view) — the 1.21.1 port of MCH_RenderVehicle.drawPart's isClientPlayer && isFirstPerson gate.
        Minecraft mc = Minecraft.getInstance();
        boolean hideFirstPerson = mc.options.getCameraType().isFirstPerson() && entity.getFirstPassenger() == mc.player;
        // Gatling barrel spin (type==1 parts, e.g. the Phalanx cannon): accumulates while the gun is firing.
        float barrelDeg = calcRot(entity.barrelSpin(), entity.prevBarrelSpin(), partialTick);
        int c = wreckColor(entity);
        Set<String> hidden = hiddenGroups(entity, model); // glass sub-meshes stay hidden inside part spans too
        for (MCH_VehicleInfo.VPart p : m.topParts()) {
            drawPart(p, model, pose, consumer, packedLight, overlay, turretYaw, pitch, barrelDeg, hideFirstPerson, c, hidden);
        }
    }

    /** Port of {@code MCH_RenderVehicle.drawPart}: rotate this part about its mount ({@code pos}) — yaw if it's a
     *  {@code RotationYaw} part, then pitch if it's a {@code RotationPitch} part — draw its group (unless the config hides
     *  it in the first-person cockpit), and recurse into child parts inside the SAME (aimed) frame so a barrel inherits
     *  the gun's yaw+pitch. A hidden parent still transforms and still draws its (independently-flagged) children. */
    private void drawPart(MCH_VehicleInfo.VPart p, MchModel model, PoseStack pose, VertexConsumer consumer,
                          int packedLight, int overlay, float turretYaw, float pitch, float barrelDeg, boolean hideFirstPerson,
                          int c, Set<String> hidden) {
        pose.pushPose();
        if (p.rotYaw || p.rotPitch || p.type == 1) {
            pose.translate(p.pos.x(), p.pos.y(), p.pos.z());
            if (p.rotYaw) {
                pose.mulPose(Axis.YP.rotationDegrees(-turretYaw));
            }
            if (p.rotPitch) {
                pose.mulPose(Axis.XP.rotationDegrees(pitch));
            }
            if (p.type == 1) {
                pose.mulPose(Axis.ZN.rotationDegrees(barrelDeg)); // glRotatef(rotBrl, 0,0,-1) — gatling barrel roll
            }
            pose.translate(-p.pos.x(), -p.pos.y(), -p.pos.z());
        }
        if (p.drawFP || !hideFirstPerson) {
            MchModelRenderer.renderPartSpan(model, pose, consumer, packedLight, overlay, c, c, c, 255,
                "$" + p.modelName, hidden);
        }
        if (p.child != null) {
            for (MCH_VehicleInfo.VPart child : p.child) {
                drawPart(child, model, pose, consumer, packedLight, overlay, turretYaw, pitch, barrelDeg, hideFirstPerson, c, hidden);
            }
        }
        pose.popPose();
    }
}
