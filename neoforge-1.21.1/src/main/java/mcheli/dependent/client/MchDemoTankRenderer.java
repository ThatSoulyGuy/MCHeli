package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
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
 * (armour, hatch, every gun) is redrawn rotated by the gunner's yaw about {@code info.turretPosition}, and EACH gun
 * part whose config marks it pitchable elevates about its own {@code AddPart[Turret]Weapon}/{@code AddPartWeaponChild}
 * mount, clamped to that weapon's {@code AddTurretWeapon} pitch limits — the main gun barrel and the secondary guns.
 *
 * <p>Config-driven from the parsed {@code partWeapon} list: each {@code partWeapon[i]} has {@code modelName="weapon"+i}
 * (→ the {@code $weapon{i}} group) and a {@code pitch} flag + a mount {@code pos}; its children map to
 * {@code $weapon{i}_{j}}. Any part/child with {@code pitch} becomes a pitch group; everything else in the turret is
 * yaw-only. The gunner's aim is the rider's free look, already replicated to every client, so no extra sync is needed.
 */
public class MchDemoTankRenderer extends MchModelEntityRenderer<MchDemoTank> {

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    /** A gun group that elevates: its model subtree, its mount pivot, and its config pitch clamp. */
    private record PitchPart(Set<String> groups, Vec3d pivot, float min, float max) {}

    private final Set<String> turretGroups;   // whole turret (yaws): dynamicGroupsLower excludes these from the hull
    private final Set<String> yawOnlyGroups;  // turret minus every pitching gun (rendered with yaw only)
    private final List<PitchPart> pitchParts; // each gun that elevates (main barrel + secondaries)
    private final Vec3d turretPos;            // turret ring pivot (yaw)

    public MchDemoTankRenderer(EntityRendererProvider.Context context) {
        super(context, "tanks/m1a2", "textures/tanks/m1a2.png");
        MCH_TankInfo info = MCH_TankInfoManager.get("m1a2");
        this.turretPos = info != null ? info.turretPosition : Vec3d.ZERO;

        Set<String> turret = new HashSet<>();
        List<PitchPart> pitches = new ArrayList<>();
        Set<String> pitchGroups = new HashSet<>();

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
                // The turret = the whole tail after the hull+tracks (armour, guns, hatches). This yaws as one.
                for (int i = firstWeapon; i < all.size(); i++) {
                    if (all.get(i) != null) {
                        turret.add(all.get(i).name.toLowerCase(Locale.ROOT));
                    }
                }
                // Config-driven pitch: which $weapon{i}/$weapon{i}_{j} groups elevate, around which mount, clamped how.
                var pitchInfo = pitchGroups(info);
                for (int i = firstWeapon; i < all.size(); i++) {
                    ModelGroup g = all.get(i);
                    if (g == null) {
                        continue;
                    }
                    float[] pi = pitchInfo.get(g.name.toLowerCase(Locale.ROOT));
                    if (pi == null) {
                        continue;
                    }
                    Set<String> sub = subtree(all, i, g.depth); // this gun's mesh
                    pitches.add(new PitchPart(sub, new Vec3d(pi[0], pi[1], pi[2]), pi[3], pi[4]));
                    pitchGroups.addAll(sub);
                }
            }
        }
        this.turretGroups = turret;
        this.pitchParts = pitches;
        Set<String> yawOnly = new HashSet<>(turret);
        yawOnly.removeAll(pitchGroups);
        this.yawOnlyGroups = yawOnly;
        LOG.info("[TURRET] m1a2 renderer: {} turret group(s), {} pitching gun(s), yaw pivot={}",
            turret.size(), pitches.size(), this.turretPos);
    }

    /** Map each pitchable weapon-part model group ($weapon{i}/{_j}) to {pivotX,Y,Z, pitchMin, pitchMax} from the config. */
    private static java.util.Map<String, float[]> pitchGroups(MCH_TankInfo info) {
        java.util.Map<String, float[]> out = new java.util.HashMap<>();
        if (info == null || info.partWeapon == null) {
            return out;
        }
        for (MCH_AircraftInfo.PartWeapon p : info.partWeapon) {
            if (p == null) {
                continue;
            }
            float[] lim = pitchLimits(info, p.name);
            if (p.pitch) {
                out.put(("$" + p.modelName).toLowerCase(Locale.ROOT),
                    new float[]{(float) p.pos.x(), (float) p.pos.y(), (float) p.pos.z(), lim[0], lim[1]});
            }
            if (p.child != null) {
                for (MCH_AircraftInfo.PartWeaponChild c : p.child) {
                    if (c != null && c.pitch) {
                        out.put(("$" + c.modelName).toLowerCase(Locale.ROOT),
                            new float[]{(float) c.pos.x(), (float) c.pos.y(), (float) c.pos.z(), lim[0], lim[1]});
                    }
                }
            }
        }
        return out;
    }

    /** The pitch clamp for a part = its weapon's AddTurretWeapon min/max pitch (matched by name), or unclamped. */
    private static float[] pitchLimits(MCH_TankInfo info, String[] names) {
        if (info.weaponSetList != null && names != null) {
            for (String nm : names) {
                for (MCH_AircraftInfo.WeaponSet ws : info.weaponSetList) {
                    if (ws != null && ws.type != null && ws.type.equalsIgnoreCase(nm)
                        && ws.weapons != null && !ws.weapons.isEmpty()) {
                        MCH_AircraftInfo.Weapon w = ws.weapons.get(0);
                        return new float[]{w.minPitch, w.maxPitch};
                    }
                }
            }
        }
        return new float[]{-90.0F, 90.0F};
    }

    /** The group at {@code start} plus its child mesh (following groups deeper than {@code depth}), lower-cased. */
    private static Set<String> subtree(List<ModelGroup> all, int start, int depth) {
        Set<String> sub = new HashSet<>();
        sub.add(all.get(start).name.toLowerCase(Locale.ROOT));
        for (int j = start + 1; j < all.size(); j++) {
            ModelGroup c = all.get(j);
            if (c == null) {
                continue;
            }
            if (c.depth <= depth) {
                break;
            }
            sub.add(c.name.toLowerCase(Locale.ROOT));
        }
        return sub;
    }

    @Override
    protected Set<String> dynamicGroupsLower() {
        return this.turretGroups; // exclude the whole turret from the static hull; redrawn (yaw + per-gun pitch) below
    }

    @Override
    protected void renderDynamicParts(MchDemoTank entity, PoseStack pose, VertexConsumer consumer, int packedLight,
                                      int overlay, float partialTick) {
        if (this.model == null || this.turretGroups.isEmpty()) {
            return;
        }
        float turretYaw = 0.0F;
        float lookPitch = 0.0F;
        Entity rider = entity.getFirstPassenger();
        if (rider != null) {
            // Aim = the rider's free LOOK (head yaw / look pitch), already replicated to every client.
            float headYaw = rider.getYHeadRot();
            float headYawO = rider instanceof net.minecraft.world.entity.LivingEntity le ? le.yHeadRotO : headYaw;
            turretYaw = calcRot(Mth.wrapDegrees(headYaw - entity.getYRot()),
                Mth.wrapDegrees(headYawO - entity.yRotO), partialTick);
            lookPitch = Mth.lerp(partialTick, rider.xRotO, rider.getXRot());
        }

        pose.pushPose();
        // Turret yaw about the ring (pose is already in the hull frame, so this is yaw relative to the hull).
        pose.translate(this.turretPos.x(), this.turretPos.y(), this.turretPos.z());
        pose.mulPose(Axis.YP.rotationDegrees(-turretYaw));
        pose.translate(-this.turretPos.x(), -this.turretPos.y(), -this.turretPos.z());

        renderGroups(this.yawOnlyGroups, pose, consumer, packedLight, overlay); // armour, hatch, non-pitch parts

        for (PitchPart pp : this.pitchParts) {
            float pitch = Mth.clamp(lookPitch, pp.min(), pp.max()); // per-gun elevation, clamped to its config limits
            pose.pushPose();
            pose.translate(pp.pivot().x(), pp.pivot().y(), pp.pivot().z());
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.translate(-pp.pivot().x(), -pp.pivot().y(), -pp.pivot().z());
            renderGroups(pp.groups(), pose, consumer, packedLight, overlay);
            pose.popPose();
        }

        pose.popPose();
    }

    private void renderGroups(Set<String> groups, PoseStack pose, VertexConsumer consumer, int light, int overlay) {
        for (String group : groups) {
            MchModelRenderer.renderGroup(this.model, pose, consumer, light, overlay, 255, 255, 255, 255, group);
        }
    }
}
