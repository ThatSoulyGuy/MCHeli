package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.model.ModelGroup;
import mcheli.agnostic.tank.MCH_TankInfo;
import mcheli.agnostic.tank.MCH_TankInfoManager;
import mcheli.agnostic.value.Vec3d;
import mcheli.dependent.entity.MchDemoTank;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Renders the demo tank ({@code tanks/m1a2.mqo}) with an AIMING turret — the 1.21.1 port of the reference
 * {@code MCH_RenderAircraft} turret block. The hull draws statically through the base class; the whole turret assembly
 * (armour, hatch, guns) is redrawn rotated by the gunner's yaw about {@code info.turretPosition}, and the main gun
 * barrel additionally pitches (elevates) about its config mount, driven by the gunner's look pitch (clamped to the
 * weapon's {@code AddTurretWeapon} pitch limits).
 *
 * <p>The {@code .mqo} is a flattened tree: a {@code $}-part is followed by its child mesh at greater depth. The hull
 * ({@code $body}) and tracks come first, then everything from the first {@code $weapon*} onward is the turret — that
 * whole tail yaws. Within it, the main gun barrel groups ({@code $weapon0_0}/{@code $weapon0_1} + their children) also
 * pitch. The gunner's aim is the rider's free look, already replicated to every client, so no extra sync is needed.
 */
public class MchDemoTankRenderer extends MchModelEntityRenderer<MchDemoTank> {

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    private final Set<String> turretGroups;   // whole turret (yaws): dynamicGroupsLower excludes these from the hull
    private final Set<String> yawOnlyGroups;  // turret minus the barrel (rendered with yaw only)
    private final Set<String> barrelGroups;   // main gun barrel (rendered with yaw + pitch)
    private final Vec3d turretPos;            // turret ring pivot (yaw)
    private final Vec3d barrelPivot;          // gun mount pivot (pitch)
    private final float pitchMin;
    private final float pitchMax;

    public MchDemoTankRenderer(EntityRendererProvider.Context context) {
        super(context, "tanks/m1a2", "textures/tanks/m1a2.png");
        MCH_TankInfo info = MCH_TankInfoManager.get("m1a2");
        this.turretPos = info != null ? info.turretPosition : Vec3d.ZERO;

        Set<String> turret = new HashSet<>();
        Set<String> barrel = new HashSet<>();
        if (this.model != null) {
            List<ModelGroup> all = this.model.groups();
            int firstWeapon = -1;
            for (int i = 0; i < all.size(); i++) {
                ModelGroup g = all.get(i);
                if (g != null && g.name.toLowerCase(Locale.ROOT).startsWith("$weapon")) {
                    firstWeapon = i;
                    break;
                }
            }
            if (firstWeapon >= 0) {
                // The turret is the whole tail after the hull+tracks: everything from the first $weapon onward
                // (armour, guns, hatches). This yaws as one.
                for (int i = firstWeapon; i < all.size(); i++) {
                    if (all.get(i) != null) {
                        turret.add(all.get(i).name.toLowerCase(Locale.ROOT));
                    }
                }
                // The main gun barrel that ALSO pitches: the $weapon0_0 (mantlet) and $weapon0_1 (barrel) subtrees.
                for (int i = firstWeapon; i < all.size(); i++) {
                    ModelGroup g = all.get(i);
                    if (g == null) {
                        continue;
                    }
                    String n = g.name.toLowerCase(Locale.ROOT);
                    if (n.equals("$weapon0_0") || n.equals("$weapon0_1")) {
                        barrel.add(n);
                        for (int j = i + 1; j < all.size(); j++) {
                            ModelGroup c = all.get(j);
                            if (c == null) {
                                continue;
                            }
                            if (c.depth <= g.depth) {
                                break;
                            }
                            barrel.add(c.name.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
        }
        this.turretGroups = turret;
        this.barrelGroups = barrel;
        Set<String> yawOnly = new HashSet<>(turret);
        yawOnly.removeAll(barrel);
        this.yawOnlyGroups = yawOnly;

        // Barrel pitch pivot + limits from the first TURRET weapon's config (AddTurretWeapon). m1a2: pos (0,2.10,2.02),
        // pitch clamp -50..7. Falls back to the turret ring / no clamp if the config is absent.
        Vec3d pivot = this.turretPos;
        float pmin = -90.0F;
        float pmax = 90.0F;
        if (info != null && info.weaponSetList != null) {
            outer:
            for (MCH_AircraftInfo.WeaponSet ws : info.weaponSetList) {
                if (ws == null || ws.weapons == null) {
                    continue;
                }
                for (MCH_AircraftInfo.Weapon w : ws.weapons) {
                    if (w != null && w.turret) {
                        pivot = w.pos;
                        pmin = w.minPitch;
                        pmax = w.maxPitch;
                        break outer;
                    }
                }
            }
        }
        this.barrelPivot = pivot;
        this.pitchMin = pmin;
        this.pitchMax = pmax;
        LOG.info("[TURRET] m1a2 renderer: {} turret group(s), {} barrel group(s); yaw pivot={} barrel pivot={} pitch[{}..{}]",
            turret.size(), barrel.size(), this.turretPos, this.barrelPivot, pmin, pmax);
    }

    @Override
    protected Set<String> dynamicGroupsLower() {
        return this.turretGroups; // exclude the whole turret from the static hull; redrawn (yaw + barrel pitch) below
    }

    @Override
    protected void renderDynamicParts(MchDemoTank entity, PoseStack pose, VertexConsumer consumer, int packedLight,
                                      int overlay, float partialTick) {
        if (this.model == null || this.turretGroups.isEmpty()) {
            return;
        }
        float turretYaw = 0.0F;
        float barrelPitch = 0.0F;
        Entity rider = entity.getFirstPassenger();
        if (rider != null) {
            // Aim = the rider's free LOOK (head yaw / look pitch), already replicated to every client.
            float headYaw = rider.getYHeadRot();
            float headYawO = rider instanceof net.minecraft.world.entity.LivingEntity le ? le.yHeadRotO : headYaw;
            turretYaw = calcRot(Mth.wrapDegrees(headYaw - entity.getYRot()),
                Mth.wrapDegrees(headYawO - entity.yRotO), partialTick);
            float lookPitch = Mth.lerp(partialTick, rider.xRotO, rider.getXRot());
            barrelPitch = Mth.clamp(lookPitch, this.pitchMin, this.pitchMax); // reference clamps to the weapon's range
        }

        pose.pushPose();
        // Turret yaw about the ring (pose is already in the hull frame, so this is yaw relative to the hull).
        pose.translate(this.turretPos.x(), this.turretPos.y(), this.turretPos.z());
        pose.mulPose(Axis.YP.rotationDegrees(-turretYaw));
        pose.translate(-this.turretPos.x(), -this.turretPos.y(), -this.turretPos.z());

        renderGroups(this.yawOnlyGroups, pose, consumer, packedLight, overlay); // armour, hatch, secondary guns

        // Main gun barrel: additionally pitch about the gun mount (nested in the turret yaw).
        pose.pushPose();
        pose.translate(this.barrelPivot.x(), this.barrelPivot.y(), this.barrelPivot.z());
        pose.mulPose(Axis.XP.rotationDegrees(barrelPitch));
        pose.translate(-this.barrelPivot.x(), -this.barrelPivot.y(), -this.barrelPivot.z());
        renderGroups(this.barrelGroups, pose, consumer, packedLight, overlay);
        pose.popPose();

        pose.popPose();
    }

    private void renderGroups(Set<String> groups, PoseStack pose, VertexConsumer consumer, int light, int overlay) {
        for (String group : groups) {
            MchModelRenderer.renderGroup(this.model, pose, consumer, light, overlay, 255, 255, 255, 255, group);
        }
    }
}
