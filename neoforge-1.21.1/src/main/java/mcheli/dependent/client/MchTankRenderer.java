package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.value.Vec3d;
import mcheli.dependent.entity.MchTank;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/**
 * Renders every tank — the 1.21.1 port of the reference tank pipeline ({@code MCH_RenderTank.renderAircraft} +
 * {@code MCH_RenderAircraft.renderCommonPart}): the base class draws the {@code $body} span and the declared parts;
 * this class animates the RUNNING GEAR ({@code $wheelN} spin+steer, {@code $track_rollerN} per-side spin,
 * {@code $crawler_trackN} belt scroll) and defers the TURRET/GUNS to the shared {@link #renderWeaponParts} pass —
 * each declared {@code $weaponN} span aims by the gunner's free look exactly as the reference {@code renderWeapon}
 * does. There is deliberately NO "first-$weapon-to-end" bulk slice: the turret is whatever the config DECLARES
 * ({@code AddPartWeapon}/{@code AddPartTurretWeapon} → the {@code $weaponN} span), so model group order is irrelevant
 * (the KV-2 puts {@code $weapon0} before {@code $body}; the m1a2 after — both render identically).
 */
public class MchTankRenderer extends MchModelEntityRenderer<MchTank> {

    /** Per-config animated-group set: the declared weapon spans + the running gear. */
    private final Map<String, Set<String>> dynamicCache = new HashMap<>();

    public MchTankRenderer(EntityRendererProvider.Context context) {
        super(context, "tanks");
    }

    @Override
    protected Set<String> dynamicGroupsLower(MchTank entity, MchModel model) {
        return this.dynamicCache.computeIfAbsent(entity.configName(), n -> {
            MCH_AircraftInfo info = entity.hostInfo();
            Set<String> s = new HashSet<>(weaponGroupsLower(info));
            if (info != null) {
                for (MCH_AircraftInfo.PartWheel w : info.partWheel) {
                    s.add(("$" + w.modelName).toLowerCase(java.util.Locale.ROOT));
                }
                for (MCH_AircraftInfo.CrawlerTrack c : info.partCrawlerTrack) {
                    s.add(("$" + c.modelName).toLowerCase(java.util.Locale.ROOT));
                }
                for (MCH_AircraftInfo.TrackRoller r : info.partTrackRoller) {
                    s.add(("$" + r.modelName).toLowerCase(java.util.Locale.ROOT));
                }
            }
            return s;
        });
    }

    @Override
    protected void renderDynamicParts(MchTank entity, MchModel model, PoseStack pose, VertexConsumer consumer,
                                      int packedLight, int overlay, float partialTick) {
        // Running gear in the HULL frame (never yaws with the gun).
        renderRunningGear(entity, model, pose, consumer, packedLight, overlay, partialTick);
        // Turret + guns: the shared faithful renderWeapon pass (ring yaw, clamped look yaw/pitch, children, barrel).
        renderWeaponParts(entity, model, pose, consumer, packedLight, overlay, partialTick);
    }

    /** Draw the wheels (spin + steer), track rollers (per-side spin) and crawler belt (single link replicated +
     *  per-side scroll) in the HULL frame — the 1.21.1 port of {@code MCH_RenderAircraft.renderWheel/
     *  renderTrackRoller/renderCrawlerTrack}. */
    private void renderRunningGear(MchTank entity, MchModel model, PoseStack pose, VertexConsumer consumer,
                                   int packedLight, int overlay, float partialTick) {
        MCH_AircraftInfo info = entity.hostInfo();
        if (info == null || (info.partWheel.isEmpty() && info.partCrawlerTrack.isEmpty() && info.partTrackRoller.isEmpty())) {
            return;
        }
        int col = wreckColor(entity);
        Set<String> hidden = hiddenGroups(entity, model); // glass sub-meshes stay hidden inside running-gear spans
        float wheelDeg = calcRot(entity.wheelSpin(), entity.prevWheelSpin(), partialTick);      // road-wheel spin about X
        float steer = Mth.lerp(partialTick, entity.prevWheelSteer(), entity.wheelSteer());       // [-1,1] steer fraction
        for (MCH_AircraftInfo.PartWheel w : info.partWheel) {
            renderWheel(model, pose, consumer, packedLight, overlay, w, steer, wheelDeg, col, hidden);
        }
        // Rollers spin at their OWN side's speed; the belt scrolls at its OWN side's phase (differential on pivot turns).
        float[] rollerDeg = {
            calcRot(entity.rollerSpin(0), entity.prevRollerSpin(0), partialTick),
            calcRot(entity.rollerSpin(1), entity.prevRollerSpin(1), partialTick)};
        for (MCH_AircraftInfo.TrackRoller r : info.partTrackRoller) {
            spinAboutX(model, pose, consumer, packedLight, overlay, r.pos, rollerDeg[r.side], "$" + r.modelName, col, hidden);
        }
        float[] scroll = {
            lerpScroll(entity.prevTrackScroll(0), entity.trackScroll(0), partialTick),
            lerpScroll(entity.prevTrackScroll(1), entity.trackScroll(1), partialTick)};
        // Crawler belt: ONE link mesh stamped along the densified point loop, scrolled by this side's phase (0..1).
        for (MCH_AircraftInfo.CrawlerTrack c : info.partCrawlerTrack) {
            int steps = c.lp.size() - 1;
            if (steps < 1) {
                continue;
            }
            String group = "$" + c.modelName;
            float sc = scroll[c.side];
            for (int i = 0; i < steps; i++) {
                MCH_AircraftInfo.CrawlerTrackPrm cp = c.lp.get(i);
                MCH_AircraftInfo.CrawlerTrackPrm np = c.lp.get((i + 1) % steps);
                double r1 = cp.r, r2 = np.r;
                if (r2 - r1 < -180.0) r2 += 360.0;
                else if (r2 - r1 > 180.0) r2 -= 360.0;
                double x = cp.x + (np.x - cp.x) * sc; // path vertical (Y)
                double y = cp.y + (np.y - cp.y) * sc; // path forward (Z)
                double r = r1 + (r2 - r1) * sc;
                pose.pushPose();
                pose.translate(0.0, x, y);
                pose.mulPose(Axis.XN.rotationDegrees((float) r)); // glRotatef(r,-1,0,0)
                MchModelRenderer.renderPartSpan(model, pose, consumer, packedLight, overlay, col, col, col, 255,
                    group, hidden);
                pose.popPose();
            }
        }
    }

    private static void spinAboutX(MchModel model, PoseStack pose, VertexConsumer consumer, int light, int overlay,
                                   Vec3d pos, float deg, String group, int c, Set<String> hidden) {
        pose.pushPose();
        pose.translate(pos.x(), pos.y(), pos.z());
        pose.mulPose(Axis.XP.rotationDegrees(deg));
        pose.translate(-pos.x(), -pos.y(), -pos.z());
        MchModelRenderer.renderPartSpan(model, pose, consumer, light, overlay, c, c, c, 255,
            group, hidden);
        pose.popPose();
    }

    /** Port of {@code MCH_RenderAircraft.renderWheel}: a steered wheel ({@code rotDir != 0}) first yaws {@code steer ×
     *  rotDir} degrees about its steer axis at {@code pos2}, then every wheel spins about its X axle at {@code pos}. */
    private static void renderWheel(MchModel model, PoseStack pose, VertexConsumer consumer, int light, int overlay,
                                    MCH_AircraftInfo.PartWheel w, float steer, float spinDeg, int c, Set<String> hidden) {
        pose.pushPose();
        if (w.rotDir != 0.0F) {
            org.joml.Vector3f axis = new org.joml.Vector3f((float) w.rot.x(), (float) w.rot.y(), (float) w.rot.z());
            if (axis.lengthSquared() < 1.0e-6F) {
                axis.set(0.0F, 1.0F, 0.0F);
            }
            pose.translate(w.pos2.x(), w.pos2.y(), w.pos2.z());
            pose.mulPose(new org.joml.Quaternionf().rotateAxis((float) Math.toRadians(steer * w.rotDir), axis.normalize()));
            pose.translate(-w.pos2.x(), -w.pos2.y(), -w.pos2.z());
        }
        pose.translate(w.pos.x(), w.pos.y(), w.pos.z());
        pose.mulPose(Axis.XP.rotationDegrees(spinDeg));
        pose.translate(-w.pos.x(), -w.pos.y(), -w.pos.z());
        MchModelRenderer.renderPartSpan(model, pose, consumer, light, overlay, c, c, c, 255,
            "$" + w.modelName, hidden);
        pose.popPose();
    }

    /** Interpolate a [0,1) scroll phase, taking the short way across the 1->0 wrap. */
    private static float lerpScroll(float prev, float cur, float t) {
        float d = cur - prev;
        if (d < -0.5F) d += 1.0F;
        else if (d > 0.5F) d -= 1.0F;
        float s = (prev + d * t) % 1.0F;
        return s < 0.0F ? s + 1.0F : s;
    }
}
