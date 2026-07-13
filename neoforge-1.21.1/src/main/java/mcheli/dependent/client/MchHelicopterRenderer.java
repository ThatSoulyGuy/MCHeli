package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import mcheli.agnostic.helicopter.MCH_HeliInfo;
import mcheli.agnostic.helicopter.MCH_HeliInfoManager;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.model.ModelGroup;
import mcheli.dependent.entity.MchHelicopter;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.joml.Quaternionf;

/**
 * Renders every helicopter with SPINNING rotors — the 1.21.1 rewrite of {@code MCH_RenderHeli.drawModelBlade}. Which
 * heli it is comes from the entity's config name; the rotor list ({@code AddRotor}) + blade groups are resolved per
 * config and cached. Each rotor's blade group ({@code $}+{@code modelName}) is drawn {@code bladeNum} times, offset by
 * {@code bladeRot}, spun about the rotor axis at its hub.
 */
public class MchHelicopterRenderer extends MchModelEntityRenderer<MchHelicopter> {

    private record HeliMeta(Set<String> bladeGroups, List<MCH_HeliInfo.Rotor> rotors) {}
    private final Map<String, HeliMeta> cache = new HashMap<>();

    public MchHelicopterRenderer(EntityRendererProvider.Context context) {
        super(context, "helicopters");
    }

    private HeliMeta meta(MchHelicopter entity, MchModel model) {
        return this.cache.computeIfAbsent(entity.configName(), name -> build(name, model));
    }

    private static HeliMeta build(String name, MchModel model) {
        MCH_HeliInfo info = MCH_HeliInfoManager.get(name);
        List<MCH_HeliInfo.Rotor> rotors = info != null ? info.rotorList : List.of();
        Set<String> excluded = new HashSet<>();
        // The spinning blade groups — animated below, excluded from the base's static declared pass.
        for (MCH_HeliInfo.Rotor r : rotors) {
            excluded.add(("$" + r.modelName).toLowerCase(Locale.ROOT));
        }
        // The $weaponN weapon parts — drawn AIMED by renderWeaponParts.
        // (Undeclared groups — $camera markers, the drafting-table dummy rotor — are never drawn by the base's
        //  explicit composition, exactly like the reference; no name-pattern hack needed.)
        excluded.addAll(weaponGroupsLower(info));
        return new HeliMeta(excluded, rotors);
    }

    @Override
    protected Set<String> dynamicGroupsLower(MchHelicopter entity, MchModel model) {
        return meta(entity, model).bladeGroups();
    }

    @Override
    protected void renderDynamicParts(MchHelicopter entity, MchModel model, PoseStack pose, VertexConsumer consumer,
                                      int packedLight, int overlay, float partialTick) {
        HeliMeta m = meta(entity, model);
        int c = wreckColor(entity);
        // Rotor angle is accumulated on the entity from the synced engine power; interpolate with the short-path.
        float spin = calcRot(entity.getRotorAngle(), entity.getPrevRotorAngle(), partialTick);
        for (MCH_HeliInfo.Rotor rotor : m.rotors()) {
            String group = "$" + rotor.modelName;
            double hx = rotor.pos.x();
            double hy = rotor.pos.y();
            double hz = rotor.pos.z();
            float ax = (float) rotor.rot.x();
            float ay = (float) rotor.rot.y();
            float az = (float) rotor.rot.z();
            for (int i = 0; i < rotor.bladeNum; i++) {
                float angle = spin + i * rotor.bladeRot;
                pose.pushPose();
                pose.translate(hx, hy, hz);
                pose.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(angle), ax, ay, az));
                if (!rotor.oldRenderMethod) {
                    pose.translate(-hx, -hy, -hz);
                }
                MchModelRenderer.renderPartSpan(model, pose, consumer, packedLight, overlay, c, c, c, 255,
                    group, java.util.Collections.emptySet());
                pose.popPose();
            }
        }
        // Weapon parts ($weaponN) aimed by the rider's free look (chin gun yaws, rocket pods pitch, gatling spins).
        renderWeaponParts(entity, model, pose, consumer, packedLight, overlay, partialTick);
    }
}
