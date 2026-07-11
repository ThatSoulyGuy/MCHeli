package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import mcheli.agnostic.helicopter.MCH_HeliInfo;
import mcheli.agnostic.helicopter.MCH_HeliInfoManager;
import mcheli.agnostic.model.ModelGroup;
import mcheli.dependent.entity.MchDemoHeli;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.joml.Quaternionf;

/**
 * Renders the demo helicopter's real ah-64 model with SPINNING rotors — the 1.21.1 rewrite of
 * {@code MCH_RenderHeli.drawModelBlade}. The static hull draws through the base class; each rotor's blade group
 * ({@code $blade0} main / {@code $blade1} tail, named {@code $}+{@code modelName} from the config {@code AddRotor}
 * list) is drawn {@code bladeNum} times, each offset by {@code bladeRot} degrees and continuously spun about the
 * rotor axis at its hub.
 *
 * <p>Both ah-64 rotors are the "new" render method ({@code AddRotor}, not {@code AddRotorOld}) → the blade mesh sits
 * at its authored position and is rotated ABOUT the hub (translate to hub, rotate, translate back). Spin is
 * time-driven for now; tying it to engine RPM (and blade fold) is a later step.
 */
public class MchDemoHeliRenderer extends MchModelEntityRenderer<MchDemoHeli> {
    private final List<MCH_HeliInfo.Rotor> rotors;
    private final Set<String> bladeGroups;

    public MchDemoHeliRenderer(EntityRendererProvider.Context context) {
        super(context, "helicopters/ah-64", "textures/helicopters/ah-64.png");
        MCH_HeliInfo info = MCH_HeliInfoManager.get("ah-64");
        this.rotors = info != null ? info.rotorList : List.of();
        Set<String> excluded = new HashSet<>();
        // The spinning blade groups ($blade0/$blade1) — excluded from the static hull, redrawn spinning below.
        for (MCH_HeliInfo.Rotor r : this.rotors) {
            excluded.add(("$" + r.modelName).toLowerCase(Locale.ROOT));
        }
        // The reference draws only $body (+ its trailing non-$ groups) statically, never the other $-parts. Match that
        // for the non-body $-groups that must NOT show in-world: the drafting-table "dummy" rotor (a STATIC 3-blade
        // preview — the "3 static blades") and the $camera position markers.
        if (this.model != null) {
            for (ModelGroup g : this.model.groups()) {
                if (g == null) {
                    continue;
                }
                String n = g.name.toLowerCase(Locale.ROOT);
                if (n.startsWith("$camera") || n.contains("dummy")) {
                    excluded.add(n);
                }
            }
        }
        this.bladeGroups = excluded;
    }

    @Override
    protected Set<String> dynamicGroupsLower() {
        return this.bladeGroups;
    }

    @Override
    protected void renderDynamicParts(MchDemoHeli entity, PoseStack pose, VertexConsumer consumer, int packedLight,
                                      int overlay, float partialTick) {
        if (this.model == null) {
            return;
        }
        // Rotor angle is accumulated on the entity from the synced engine power (RPM ramps with throttle), and wraps
        // at 360; interpolate with the short-path so the wrap doesn't spin the blades backward for a frame.
        float spin = calcRot(entity.getRotorAngle(), entity.getPrevRotorAngle(), partialTick);
        for (MCH_HeliInfo.Rotor rotor : this.rotors) {
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
                // NEW method (old=false): rotate the in-place blade ABOUT the hub. OLD would omit the translate-back
                // (blade mesh authored at the origin) — kept faithful for other models.
                pose.translate(hx, hy, hz);
                pose.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(angle), ax, ay, az));
                if (!rotor.oldRenderMethod) {
                    pose.translate(-hx, -hy, -hz);
                }
                MchModelRenderer.renderGroup(this.model, pose, consumer, packedLight, overlay, 255, 255, 255, 255, group);
                pose.popPose();
            }
        }
    }
}
