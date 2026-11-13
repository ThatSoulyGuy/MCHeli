package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.helicopter.MCH_HeliInfo;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.plane.MCP_PlaneInfo;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.agnostic.value.Vec3d;
import mcheli.agnostic.vehicle.MCH_VehicleInfo;
import mcheli.agnostic.weapon.WeaponAim;
import mcheli.dependent.config.MchConfig;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

/**
 * Base renderer that draws the real {@code .mqo}/{@code .obj} model for whichever vehicle an entity currently IS —
 * resolved every frame from its synced {@link AbstractMchVehicle#configName() config name} (a process-wide cache keeps
 * this a map lookup, not a re-parse). ONE renderer instance per category serves every vehicle in that category.
 *
 * <p>Transform mirrors the reference exactly: the {@link PoseStack} is already at the entity, so it applies only
 * {@code yaw} about {@code -Y}, {@code pitch} about {@code X}, {@code roll} about {@code Z} — and NO scale, because the
 * {@code .mqo} parser already divides vertices by 100 into world units. The whole model draws in one blended pass
 * ({@link MchRenderTypes#vehicle}), so cockpit glass is see-through from its texture alpha. Composition follows
 * {@code MCH_RenderAircraft.doRender}: the parts the category draws ahead of its body ({@link #renderPreBodyParts}),
 * the {@code $body} span and the static declared parts, the category's own animated parts
 * ({@link #renderDynamicParts}), then the shared animated parts of {@code renderCommonPart} ({@link #renderCommonParts},
 * canopy last) and the search-light beams. Every part takes the reference hull tint ({@link #wreckColor}: 0.75 grey,
 * a destroyed wreck near-black).
 */
public abstract class MchModelEntityRenderer<T extends AbstractMchVehicle> extends EntityRenderer<T> {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Resource directory for this category's models/textures ({@code helicopters}/{@code planes}/{@code tanks}/{@code vehicles}). */
    private final String categoryDir;
    private final Map<String, MchModel> modelCache = new HashMap<>();
    private final Map<String, Set<String>> declaredCache = new HashMap<>();
    private final Map<String, CommonMeta> commonCache = new HashMap<>();
    private final Map<String, ResourceLocation> textureCache = new HashMap<>();

    /** Per config: every group kept out of the static pass (the subclass's animated groups plus the common animated
     *  parts), and the landing gears the base animates (those the subclass does not draw itself). */
    private record CommonMeta(Set<String> excluded, List<MCH_AircraftInfo.LandingGear> gears) {}

    protected MchModelEntityRenderer(EntityRendererProvider.Context context, String categoryDir) {
        super(context);
        this.categoryDir = categoryDir;
    }

    /** The model for this entity's current config, resolved+cached by name (null before the name syncs / on load failure).
     *  A FAILED load is cached as null (negative cache) so a broken/missing model isn't re-parsed + re-warned every frame
     *  — {@code computeIfAbsent} would store nothing for a null result and retry forever. */
    protected final MchModel model(T entity) {
        String name = entity.configName();
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (this.modelCache.containsKey(name)) {
            return this.modelCache.get(name);
        }
        MchModel m = load(this.categoryDir + "/" + name);
        this.modelCache.put(name, m);
        return m;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        String tex = entity.skinTextureName();
        if (tex == null || tex.isEmpty()) {
            tex = entity.configName();
        }
        if (tex == null || tex.isEmpty()) {
            tex = "missingno";
        }
        return this.textureCache.computeIfAbsent(tex,
            t -> ResourceLocation.fromNamespaceAndPath("mcheli", "textures/" + this.categoryDir + "/" + t + ".png"));
    }

    private static MchModel load(String name) {
        ModelHandle h = mcheli.dependent.port.MchContentPacks.resources().loadModel(name);
        if (!(h instanceof MchModel m)) {
            LOGGER.warn("MCHeli renderer: model '{}' did not load; entity will be invisible", name);
            return null;
        }
        return m;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight) {
        MchModel model = model(entity);
        MCH_AircraftInfo info = entity.hostInfo();
        if (model != null) {
            pose.pushPose();
            // Lift the model so its LOWEST point (minY, e.g. skids/tracks/wheels ~-0.3) sits at the entity feet (the
            // ground). The reference's wheel-suspension physics hold the hull up by |minY|; those aren't ported, so
            // without this the model origin is at the feet and the whole vehicle sinks by |minY|. World-vertical, so it
            // must come BEFORE the yaw/pitch/roll rotations (matching the reference, which lifts the entity in world-Y).
            pose.translate(0.0, -model.minY, 0.0);
            // Reference order: yaw about -Y, pitch about X, roll about Z (MCH_RenderHeli.renderAircraft). No scale.
            pose.mulPose(Axis.YP.rotationDegrees(-entityYaw));
            // Weapon recoil adds a transient pitch/roll kick to the MODEL (visible to everyone, incl. the rider in
            // third person). The first-person camera gets the same kick in CameraMixin, so the pilot feels it too.
            float hullPitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            float pitch = hullPitch + entity.animation().recoilPitchDeg(partialTick);
            float roll = calcRot(entity.getRollAngle(), entity.getPrevRollAngle(), partialTick) + entity.animation().recoilRollDeg(partialTick);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.ZP.rotationDegrees(roll));

            // The whole model draws in ONE blended pass — the reference's technique (W_Render.setCommonRenderParam:
            // glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); cull on; depth-write on). Cockpit
            // GLASS is translucent purely from the body texture's ALPHA channel (glass texels have alpha < 1), NOT from
            // any material or name. MchRenderTypes.vehicle is entityTranslucentCull without the per-frame quad sort.
            VertexConsumer consumer = buffers.getBuffer(MchRenderTypes.vehicle(getTextureLocation(entity)));
            // The reference hull tint: 0.75 grey intact, a scorched near-black wreck (see wreckColor).
            int c = wreckColor(entity);
            // A lit search light renders the vehicle full-bright (reference getBrightnessForRender 15728880).
            int light = entity.isSearchLightOn() ? LightTexture.FULL_BRIGHT : packedLight;
            // Reference setCommonRenderParam: smooth shading when both the config file and the vehicle allow it.
            boolean prevShade = MchModelRenderer.shadeModel(
                MchConfig.CLIENT.smoothShading() && (info == null || info.smoothShading));
            try {
                CommonMeta common = commonMeta(entity, model);
                Set<String> fpHidden = firstPersonHidden(entity); // HideGM weapon parts, local pilot in first person only
                if (MchModelRenderer.hasGroup(model, "$body")) {
                    // Reference composition (MCH_RenderAircraft.renderBody + per-part renderPart): draw the $body SPAN,
                    // then each config-DECLARED part's span. A $-group that is neither $body nor a declared part is
                    // NEVER drawn (the reference hides it — hidden weapon variants, dummy/marker groups). Group ORDER in
                    // the model file no longer matters, so a model that puts $weapon0 before $body (KV-2) renders
                    // exactly like one that doesn't (m1a2). Animated parts draw in renderDynamicParts and
                    // renderCommonParts; the rest draw static here so no declared part ever vanishes.
                    // The parts the reference drew BEFORE renderBody go first: in this single unsorted blended pass the
                    // draw order decides what shows through the body's glass (which writes depth).
                    renderPreBodyParts(entity, model, pose, consumer, light, OverlayTexture.NO_OVERLAY, partialTick);
                    MchModelRenderer.renderPartSpan(model, pose, consumer, light, OverlayTexture.NO_OVERLAY,
                        c, c, c, 255, "$body");
                    for (String gname : declaredGroups(entity)) {
                        if (!common.excluded().contains(gname) && !fpHidden.contains(gname)) {
                            MchModelRenderer.renderPartSpan(model, pose, consumer, light, OverlayTexture.NO_OVERLAY,
                                c, c, c, 255, gname);
                        }
                    }
                    renderAnimated(entity, info, model, pose, consumer, light, partialTick, entityYaw, hullPitch, common);
                } else if (common.excluded().isEmpty() && fpHidden.isEmpty()) {
                    // No $body group: the reference falls back to renderAll (drawing every group once).
                    MchModelRenderer.render(model, pose, consumer, light, OverlayTexture.NO_OVERLAY, c, c, c, 255);
                } else {
                    // No $body + animated/hidden parts: render all-minus-animated then the animated parts transformed
                    // (avoids the reference renderAll quirk of double-drawing animated groups on a $body-less model).
                    Set<String> exclude = new HashSet<>(common.excluded());
                    exclude.addAll(fpHidden);
                    renderPreBodyParts(entity, model, pose, consumer, light, OverlayTexture.NO_OVERLAY, partialTick);
                    MchModelRenderer.renderExcept(model, pose, consumer, light, OverlayTexture.NO_OVERLAY,
                        c, c, c, 255, exclude);
                    renderAnimated(entity, info, model, pose, consumer, light, partialTick, entityYaw, hullPitch, common);
                }
            } finally {
                MchModelRenderer.shadeModel(prevShade);
            }
            // Last: the beams use their own buffer, which ends the model batch.
            renderSearchLights(entity, info, pose, buffers, partialTick, entityYaw, hullPitch);
            pose.popPose();
        }
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    private void renderAnimated(T entity, MCH_AircraftInfo info, MchModel model, PoseStack pose, VertexConsumer consumer,
                                int light, float partialTick, float hullYaw, float hullPitch, CommonMeta common) {
        renderDynamicParts(entity, model, pose, consumer, light, OverlayTexture.NO_OVERLAY, partialTick);
        if (info != null) {
            renderCommonParts(entity, info, model, pose, consumer, light, partialTick, hullYaw, hullPitch, common);
        }
    }

    /** Config {@code HideGM} weapon groups ({@code $weaponN}) — hidden ONLY for the LOCAL player piloting this vehicle in
     *  first person, so a chin gun / gunsight doesn't fill the cockpit (port of {@code MCH_RenderAircraft}'s hideGM gate). */
    private Set<String> firstPersonHidden(T entity) {
        Set<String> gm = entity.firstPersonHiddenGroups();
        if (gm.isEmpty()) {
            return Collections.emptySet();
        }
        Minecraft mc = Minecraft.getInstance();
        boolean localPilotFirstPerson = mc.options.getCameraType().isFirstPerson() && entity.pilot() == mc.player;
        return localPilotFirstPerson ? gm : Collections.emptySet();
    }

    /** Lower-cased names of the model groups this subclass animates (excluded from the static hull). Empty by default.
     *  A landing gear named here is left to the subclass; every other common animated part is the base's. */
    protected Set<String> dynamicGroupsLower(T entity, MchModel model) {
        return Collections.emptySet();
    }

    /** Draw the animated parts the reference draws BEFORE the body ({@code renderAircraft} ahead of
     *  {@code renderBody}): only a plane has any (nozzles, wings, propellers, reference {@code MCP_RenderPlane}).
     *  Empty by default. The {@code pose} is at the entity + oriented, as for {@link #renderDynamicParts}. */
    protected void renderPreBodyParts(T entity, MchModel model, PoseStack pose, VertexConsumer consumer,
                                      int packedLight, int overlay, float partialTick) {
    }

    /** Draw the animated parts (rotor blades, gear, turret). The {@code pose} is at the entity + oriented. */
    protected void renderDynamicParts(T entity, MchModel model, PoseStack pose, VertexConsumer consumer,
                                      int packedLight, int overlay, float partialTick) {
    }

    /** The hull colour (vertex grey) for this entity — the reference {@code glColor4f} of
     *  {@code MCH_RenderAircraft.doRender}:52-57 (and {@code W_Render.setCommonRenderParam}:44): 0.75 for an intact
     *  vehicle (0.75·255 ≈ 191), 0.15 for a destroyed wreck (≈ 38). Both versions light entities with the same 0.4
     *  ambient + 0.6 diffuse model and the colour as the material, so this is what keeps the brightness equal. Every
     *  render pass (hull, declared parts, animated parts) must use this so the tint is UNIFORM. */
    protected static int wreckColor(AbstractMchVehicle entity) {
        return entity.isDestroyed() ? 38 : 191;
    }

    private CommonMeta commonMeta(T entity, MchModel model) {
        return this.commonCache.computeIfAbsent(entity.configName(), n -> {
            Set<String> sub = dynamicGroupsLower(entity, model);
            Set<String> ex = new HashSet<>(sub);
            List<MCH_AircraftInfo.LandingGear> gears = new java.util.ArrayList<>();
            MCH_AircraftInfo info = entity.hostInfo();
            if (info != null) {
                addDrawn(ex, info.canopyList);
                addDrawn(ex, info.hatchList);
                addDrawn(ex, info.lightHatchList);
                addDrawn(ex, info.partWeaponBay);
                addDrawn(ex, info.partThrottle);
                addDrawn(ex, info.partRotPart);
                addDrawn(ex, info.partSteeringWheel);
                addDrawn(ex, info.cameraList);
                for (MCH_AircraftInfo.LandingGear g : info.landingGear) {
                    if (g != null && !sub.contains(lowerPart(g.modelName))) {
                        gears.add(g);
                        ex.add(lowerPart(g.modelName));
                    }
                }
            }
            return new CommonMeta(ex, gears);
        });
    }

    /**
     * The shared animated parts — the port of {@code MCH_RenderAircraft.renderCommonPart} (:437-450) minus the parts
     * drawn elsewhere (weapons, wheels, tracks and rollers by the category renderers; the rope with the unported
     * repelling). Reference order: rotation parts, hatches, steering wheels, light hatches, throttle levers, camera
     * turrets, landing gear, weapon bays and the canopy LAST, so the canopy glass blends over everything under it.
     */
    private void renderCommonParts(T e, MCH_AircraftInfo info, MchModel model, PoseStack pose, VertexConsumer vc,
                                     int light, float pt, float hullYaw, float hullPitch, CommonMeta common) {
        int c = wreckColor(e);
        int ov = OverlayTexture.NO_OVERLAY;
        // renderRotPart (:466-484): unwrap a 360→0 step, then smooth (MCH_Lib.smooth = prev + (rot - prev) * t).
        for (int i = 0; i < info.partRotPart.size(); i++) {
            MCH_AircraftInfo.RotPart h = info.partRotPart.get(i);
            float rot = e.vehicleParts().rotPartRotation(i);
            float prev = e.vehicleParts().prevRotPartRotation(i);
            if (prev > rot) {
                rot += 360.0F;
            }
            pivot(pose, h.pos, prev + (rot - prev) * pt, h.rot);
            part(model, pose, vc, light, ov, c, h.modelName);
            pose.popPose();
        }
        // renderHatch (:855-878): a slide hatch translates by pos × travel; otherwise it swings by rot × maxRotFactor.
        float hatch = e.vehicleParts().hatchRotation(pt);
        float hatchF = e.vehicleParts().hatchFactor(pt);
        for (MCH_AircraftInfo.Hatch h : info.hatchList) {
            swingOrSlide(pose, h.pos, h.rot, h.isSlide, hatch * h.maxRotFactor, hatchF);
            part(model, pose, vc, light, ov, c, h.modelName);
            pose.popPose();
        }
        // renderSteeringWheel (:469-482): the steer fraction × rotDir about the part axis.
        float steer = Mth.lerp(pt, e.animation().prevWheelSteer(), e.animation().wheelSteer());
        for (MCH_AircraftInfo.PartWheel t : info.partSteeringWheel) {
            pivot(pose, t.pos, steer * t.rotDir, t.rot);
            part(model, pose, vc, light, ov, c, t.modelName);
            pose.popPose();
        }
        // renderLightHatch (:452-467): the 0..1 light-hatch travel × maxRot degrees.
        float lh = e.vehicleParts().lightHatchRotation(pt);
        for (MCH_AircraftInfo.Hatch t : info.lightHatchList) {
            pivot(pose, t.pos, lh * t.maxRot, t.rot);
            part(model, pose, vc, light, ov, c, t.modelName);
            pose.popPose();
        }
        // renderThrottle (:880-895): swing by throttle × rot2 about the pivot, then slide by throttle × slide.
        float throttle = e.vehicleParts().throttleLever(pt);
        for (MCH_AircraftInfo.Throttle h : info.partThrottle) {
            pivot(pose, h.pos, throttle * h.rot2, h.rot);
            pose.translate(h.slide.x() * throttle, h.slide.y() * throttle, h.slide.z() * throttle);
            part(model, pose, vc, light, ov, c, h.modelName);
            pose.popPose();
        }
        // renderCamera (:921-944): the camera turret follows the seat-1 (else pilot) look, relative to the hull.
        if (!info.cameraList.isEmpty()) {
            float yaw = Mth.wrapDegrees(e.vehicleParts().cameraPartYaw(pt) - hullYaw);
            float pitch = e.vehicleParts().cameraPartPitch(pt) - hullPitch;
            for (MCH_AircraftInfo.Camera cam : info.cameraList) {
                pose.pushPose();
                pose.translate(cam.pos.x(), cam.pos.y(), cam.pos.z());
                if (cam.yawSync) {
                    pose.mulPose(Axis.YP.rotationDegrees(-yaw)); // glRotatef(yaw, 0, -1, 0)
                }
                if (cam.pitchSync) {
                    pose.mulPose(Axis.XP.rotationDegrees(pitch));
                }
                pose.translate(-cam.pos.x(), -cam.pos.y(), -cam.pos.z());
                part(model, pose, vc, light, ov, c, cam.modelName);
                pose.popPose();
            }
        }
        renderLandingGear(e, model, pose, vc, light, pt, common.gears(), c);
        // renderWeaponBay (:897-919): door angle × maxRotFactor, or a slide by pos × angle / 90.
        for (int i = 0; i < info.partWeaponBay.size(); i++) {
            MCH_AircraftInfo.WeaponBay w = info.partWeaponBay.get(i);
            float rot = e.vehicleParts().weaponBayRotation(i, pt);
            swingOrSlide(pose, w.pos, w.rot, w.isSlide, rot * w.maxRotFactor, rot / 90.0F);
            part(model, pose, vc, light, ov, c, w.modelName);
            pose.popPose();
        }
        // renderCanopy (:946-968).
        float canopy = e.vehicleParts().canopyRotation(pt);
        float canopyF = e.vehicleParts().canopyFactor(pt);
        for (MCH_AircraftInfo.Canopy cp : info.canopyList) {
            swingOrSlide(pose, cp.pos, cp.rot, cp.isSlide, canopy * cp.maxRotFactor, canopyF);
            part(model, pose, vc, light, ov, c, cp.modelName);
            pose.popPose();
        }
    }

    /**
     * Port of {@code MCH_RenderAircraft.renderLandingGear} (:970-1022) for the gears the category renderer does not
     * draw itself: a normal strut swings by the retract angle × {@code maxRotFactor}, a reverse strut ({@code LGRev}) by
     * its complement, a gear door ({@code LGHatch}) snaps open and shut around mid-travel
     * ({@code 90·sin(2·angle)·3}, capped at 90), an optional second axis turns by {@code maxRotFactor2}, and a
     * {@code SlideRotLG} strut also slides by the (un-interpolated) travel fraction. The angle is the synced gear state
     * (0 deployed .. 90 retracted).
     */
    protected void renderLandingGear(T e, MchModel model, PoseStack pose, VertexConsumer vc, int light, float pt,
                                     List<MCH_AircraftInfo.LandingGear> gears, int c) {
        if (gears.isEmpty()) {
            return;
        }
        float rot = e.vehicleParts().getGearAngle();
        float prevRot = e.vehicleParts().getPrevGearAngle();
        float rot1 = prevRot + (rot - prevRot) * pt;
        float rot1Rev = (90.0F - prevRot) + ((90.0F - rot) - (90.0F - prevRot)) * pt;
        float rotHatch = Math.min(90.0F, 90.0F * Mth.sin(rot1 * 2.0F * (float) Math.PI / 180.0F) * 3.0F);
        for (MCH_AircraftInfo.LandingGear n : gears) {
            pose.pushPose();
            pose.translate(n.pos.x(), n.pos.y(), n.pos.z());
            if (!n.reverse) {
                rotate(pose, (n.hatch ? rotHatch : rot1) * n.maxRotFactor, n.rot);
            } else {
                rotate(pose, rot1Rev * n.maxRotFactor, n.rot);
            }
            if (n.enableRot2) {
                rotate(pose, (n.reverse ? rot1Rev : rot1) * n.maxRotFactor2, n.rot2);
            }
            pose.translate(-n.pos.x(), -n.pos.y(), -n.pos.z());
            if (n.slide != null) {
                float f = n.reverse ? 1.0F - rot / 90.0F : rot / 90.0F;
                pose.translate(f * n.slide.x(), f * n.slide.y(), f * n.slide.z());
            }
            part(model, pose, vc, light, OverlayTexture.NO_OVERLAY, c, n.modelName);
            pose.popPose();
        }
    }

    /**
     * The search-light beams — the port of {@code MCH_RenderAircraft.renderLight} (:145-210): while the light is on,
     * each {@code AddSearchLight} cone points along the seat-1 (else pilot) look relative to the hull, a fixed or
     * steering light along its own direction (a steering one turned by the steer fraction × {@code stRot}); the fan
     * runs from {@code colorStart} at the lamp to {@code colorEnd} on a 24-segment rim {@code height} away and
     * {@code width} across, drawn additively without depth write.
     */
    private void renderSearchLights(T e, MCH_AircraftInfo info, PoseStack pose, MultiBufferSource buffers, float pt,
                                    float hullYaw, float hullPitch) {
        if (info == null || info.searchLights.isEmpty() || !e.isSearchLightOn()) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(MchRenderTypes.SEARCH_LIGHT);
        float lookYaw = e.vehicleParts().cameraPartYaw(pt);
        float lookPitch = e.vehicleParts().cameraPartPitch(pt);
        float steer = Mth.lerp(pt, e.animation().prevWheelSteer(), e.animation().wheelSteer());
        for (MCH_AircraftInfo.SearchLight sl : info.searchLights) {
            pose.pushPose();
            pose.translate(sl.pos.x(), sl.pos.y(), sl.pos.z());
            if (!sl.fixDir) {
                pose.mulPose(Axis.YP.rotationDegrees(-(lookYaw - hullYaw + sl.yaw)));
                pose.mulPose(Axis.XP.rotationDegrees(lookPitch + 90.0F - hullPitch + sl.pitch));
            } else {
                float stRot = sl.steering ? -steer * sl.stRot : 0.0F;
                pose.mulPose(Axis.YP.rotationDegrees(-(sl.yaw + stRot)));
                pose.mulPose(Axis.XP.rotationDegrees(90.0F + sl.pitch));
            }
            PoseStack.Pose last = pose.last();
            float w = sl.width / 2.0F;
            int cs = sl.colorStart;
            int ce = sl.colorEnd;
            for (int i = 0; i < 24; i++) {
                float a0 = (float) (15.0 * i / 180.0 * Math.PI);
                float a1 = (float) (15.0 * (i + 1) / 180.0 * Math.PI);
                vc.addVertex(last, 0.0F, 0.0F, 0.0F).setColor(cs >> 16 & 0xFF, cs >> 8 & 0xFF, cs & 0xFF, cs >>> 24);
                vc.addVertex(last, Mth.sin(a0) * w, sl.height, Mth.cos(a0) * w)
                    .setColor(ce >> 16 & 0xFF, ce >> 8 & 0xFF, ce & 0xFF, ce >>> 24);
                vc.addVertex(last, Mth.sin(a1) * w, sl.height, Mth.cos(a1) * w)
                    .setColor(ce >> 16 & 0xFF, ce >> 8 & 0xFF, ce & 0xFF, ce >>> 24);
            }
            pose.popPose();
        }
    }

    /** Push, then rotate {@code deg} about {@code axis} through {@code pos} (glTranslate/glRotate/glTranslate). The
     *  caller pops. */
    private static void pivot(PoseStack pose, Vec3d pos, float deg, Vec3d axis) {
        pose.pushPose();
        pose.translate(pos.x(), pos.y(), pos.z());
        rotate(pose, deg, axis);
        pose.translate(-pos.x(), -pos.y(), -pos.z());
    }

    /** Push, then either slide by {@code pos × factor} or swing {@code deg} about {@code axis} through {@code pos} — the
     *  hatch / weapon bay / canopy transform. The caller pops. */
    private static void swingOrSlide(PoseStack pose, Vec3d pos, Vec3d axis, boolean slide, float deg, float factor) {
        if (slide) {
            pose.pushPose();
            pose.translate(pos.x() * factor, pos.y() * factor, pos.z() * factor);
        } else {
            pivot(pose, pos, deg, axis);
        }
    }

    /** {@code glRotatef(deg, x, y, z)}: GL normalises the axis; a zero axis or angle is a no-op. */
    protected static void rotate(PoseStack pose, float deg, Vec3d axis) {
        double len = Math.sqrt(axis.x() * axis.x() + axis.y() * axis.y() + axis.z() * axis.z());
        if (deg == 0.0F || len < 1.0e-6) {
            return;
        }
        pose.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(deg),
            (float) (axis.x() / len), (float) (axis.y() / len), (float) (axis.z() / len)));
    }

    /** Reference {@code renderPart(model, info.model, name)}: the part's {@code $name} span of the body model. */
    private static void part(MchModel model, PoseStack pose, VertexConsumer vc, int light, int overlay, int c, String name) {
        MchModelRenderer.renderPartSpan(model, pose, vc, light, overlay, c, c, c, 255, "$" + name);
    }

    private static String lowerPart(String modelName) {
        return ("$" + modelName).toLowerCase(Locale.ROOT);
    }

    /** ALL lower-cased {@code $}-group names the config DECLARES as drawable parts (every {@code DrawnPart} list across
     *  the category infos) — the reference draws exactly {@code $body} + these; anything else is never drawn. Cached per
     *  config name. */
    private Set<String> declaredGroups(T entity) {
        return this.declaredCache.computeIfAbsent(entity.configName(), n -> declaredGroupsOf(entity.hostInfo()));
    }

    /** {@link #declaredGroups} for a config (also the item icon's static composition). */
    static Set<String> declaredGroupsOf(MCH_AircraftInfo info) {
        Set<String> s = new HashSet<>();
        if (info == null) {
            return s;
        }
        addDrawn(s, info.partWeapon);
        if (info.partWeapon != null) {
            for (MCH_AircraftInfo.PartWeapon w : info.partWeapon) {
                if (w != null && w.child != null) {
                    addDrawn(s, w.child);
                }
            }
        }
        addDrawn(s, info.partWeaponBay);
        addDrawn(s, info.hatchList);
        addDrawn(s, info.lightHatchList);
        addDrawn(s, info.canopyList);
        addDrawn(s, info.landingGear);
        addDrawn(s, info.partThrottle);
        addDrawn(s, info.partRotPart);
        addDrawn(s, info.cameraList);
        addDrawn(s, info.partWheel);
        addDrawn(s, info.partSteeringWheel);
        addDrawn(s, info.partCrawlerTrack);
        addDrawn(s, info.partTrackRoller);
        if (info instanceof MCH_HeliInfo hi) {
            addDrawn(s, hi.rotorList);
        }
        if (info instanceof MCP_PlaneInfo pi) {
            addDrawn(s, pi.nozzles);
            addDrawn(s, pi.rotorList);
            for (MCP_PlaneInfo.Rotor r : pi.rotorList) {
                if (r != null) {
                    addDrawn(s, r.blades);
                }
            }
            addDrawn(s, pi.wingList);
            for (MCP_PlaneInfo.Wing w : pi.wingList) {
                if (w != null && w.pylonList != null) {
                    addDrawn(s, w.pylonList);
                }
            }
        }
        if (info instanceof MCH_VehicleInfo vi) {
            collectVParts(vi.partList, s);
        }
        return s;
    }

    private static void addDrawn(Set<String> out, java.util.List<? extends MCH_AircraftInfo.DrawnPart> parts) {
        if (parts != null) {
            for (MCH_AircraftInfo.DrawnPart p : parts) {
                if (p != null) {
                    out.add(("$" + p.modelName).toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    private static void collectVParts(java.util.List<MCH_VehicleInfo.VPart> parts, Set<String> out) {
        if (parts != null) {
            for (MCH_VehicleInfo.VPart p : parts) {
                if (p != null) {
                    out.add(("$" + p.modelName).toLowerCase(Locale.ROOT));
                    collectVParts(p.child, out);
                }
            }
        }
    }

    /** Lower-cased {@code $weaponN}/{@code $weaponN_M} groups the config declares as {@code AddPartWeapon}s — the aircraft
     *  renderers exclude these from the static hull so {@link #renderWeaponParts} can draw them AIMED (not frozen). */
    protected static Set<String> weaponGroupsLower(MCH_AircraftInfo info) {
        Set<String> s = new HashSet<>();
        if (info != null && info.partWeapon != null) {
            for (MCH_AircraftInfo.PartWeapon w : info.partWeapon) {
                s.add(("$" + w.modelName).toLowerCase(Locale.ROOT));
                if (w.child != null) {
                    for (MCH_AircraftInfo.PartWeaponChild c : w.child) {
                        s.add(("$" + c.modelName).toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return s;
    }

    /**
     * Faithful port of {@code MCH_RenderAircraft.renderWeapon} (523-685): draw each {@code AddPartWeapon} group aimed by
     * the rider's free look. A {@code HideGM} weapon is skipped ENTIRELY (parent + children) when the local player is
     * piloting in first person AND operates that weapon ({@code canUsePilot}); a {@code turret} weapon rides the turret
     * ring; {@code yaw}/{@code pitch} weapons swing/elevate to the rider look (RELATIVE to the hull, since the model is
     * already hull-rotated) clamped to the weapon's config range; {@code defaultRotationYaw}+{@code rev_sign} bracket the
     * pitch; a {@code rotBarrel} weapon spins about its axis. Aim source is the single rider (the port's stand-in for
     * {@code MCH_WeaponSet.rotationYaw/Pitch}); the 15°/tick slew, recoil and missile-cooldown gate are omitted (no
     * synced state, cosmetic).
     */
    protected void renderWeaponParts(T entity, MchModel model, PoseStack pose, VertexConsumer consumer,
                                     int light, int overlay, float partialTick) {
        MCH_AircraftInfo info = entity.hostInfo();
        if (info == null || info.partWeapon == null || info.partWeapon.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        // The turret RING rides the latched PILOT aim (reference lastRiderYaw / rotationTurretYaw) — the SAME expression
        // the seat attachment and the emplacement turret use, so seat/turret/camera never shear.
        // turretOrbitYaw ALREADY interpolates — lerping it again gave a pt-squared ease that could never agree with the
        // seat orbit / emplacement turret (which use it raw), shearing the gun off the turret every tick.
        float ringYaw = entity.turretOrbitYaw(partialTick);
        float barrelDeg = calcRot(entity.animation().barrelSpin(), entity.animation().prevBarrelSpin(), partialTick);
        Vec3d tp = info.turretPosition;
        int c = wreckColor(entity);

        for (MCH_AircraftInfo.PartWeapon w : info.partWeapon) {
            MCH_AircraftInfo.Weapon weapon = info.getWeaponByName(w.name[0]);
            // Reference aims a weapon only while ITS OWN seat is manned (updateWeaponsRotation), and the weapon tracks
            // THAT operator's look — not the pilot's. An unmanned turret weapon still RIDES the ring (block 1) but stays
            // frozen at its default yaw/pitch (its ws.rotationYaw never updates; rotationTurretYaw cancels block 2).
            Entity op = entity.weaponOperator(weapon);
            boolean operated = op != null;
            // Each operator aims by their OWN look, relative to the hull (the model is already hull-rotated).
            float relYaw = 0.0F, relYawO = 0.0F, relPitch = 0.0F, relPitchO = 0.0F;
            if (operated) {
                // BODY yaw (getYRot()/yRotO), NOT head yaw: matches the reference (renderWeapon reads w.rotationYaw,
                // fed by the rider's rotationYaw) AND — crucially — the SAME field the server-side fire reads, so the
                // barrel and the projectile can never diverge. getYHeadRot() is not synced to the server for a
                // passenger, so aiming the model by it while firing by the body yaw is exactly what split them.
                float hy = op.getYRot();
                float hyO = op.yRotO;
                relYaw = Mth.wrapDegrees(hy - entity.getYRot());
                relYawO = Mth.wrapDegrees(hyO - entity.yRotO);
                relPitch = Mth.wrapDegrees(op.getXRot() - entity.getXRot());
                relPitchO = Mth.wrapDegrees(op.xRotO - entity.xRotO);
            }
            // An UNMANNED weapon takes no aim rotation at all (below): it rides the ring at its default facing, which is
            // what the reference renders (its ws.rotationYaw never updates and rotationTurretYaw cancels the ring block).
            // HideGM hides a weapon ONLY for the player who OPERATES it, in first person (reference: the skip fires on
            // isClientPlayer(getWeaponUserByWeaponName) — a gun a different crew member mans stays visible to you).
            if (w.hideGM && firstPerson && op == mc.player) {
                continue;
            }
            // A fired MISSILE part vanishes from its rail until the weapon reloads (reference renderWeapon isMissile +
            // isWeaponNotCooldown gate) — so an emptied pylon reads as empty.
            if (w.isMissile && entity.isMissilePartHidden(w.name[0])) {
                continue;
            }
            pose.pushPose();
            // The RING is the pilot's turret slew (never a gunner's head); a weapon's own aim is its operator's look.
            float ring = w.turret ? ringYaw : 0.0F;
            // The AIM ty keys off Weapon.turret (AddTurretWeapon), NOT PartWeapon.turret (AddPartTurretWeapon) — the
            // reference splits them: updateWeaponsRotation:4495 reads wi.turret, renderWeapon:534/557 reads w.turret.
            // m1a2 mixes the two (main gun: AddPartWeapon + AddTurretWeapon), so `ring` above is the wrong quantity here.
            float aimTy = WeaponAim.turretBase(weapon, ringYaw);
            if (w.turret) { // block 1: onto the turret ring about turretPosition; glRotatef(ring,0,-1,0) == Axis.YP(-ring)
                pose.translate(tp.x(), tp.y(), tp.z());
                pose.mulPose(Axis.YP.rotationDegrees(-ring));
                pose.translate(-tp.x(), -tp.y(), -tp.z());
            }
            pose.translate(w.pos.x(), w.pos.y(), w.pos.z());
            if (w.yaw && operated) {
                // Passing the operator's OWN relYaw as ty (as this did) makes ey collapse to -defaultYaw, which
                // defeats the finite-yaw clamp entirely — m1a2's m240_r traversed a full 360 against its -220..30
                // config. ty is the ring, and it is shared by both interpolation endpoints because turretOrbitYaw
                // already interpolates (re-lerping it gives a partialTick-squared ease).
                pose.mulPose(Axis.YP.rotationDegrees(-calcRot(
                    WeaponAim.renderYaw(weapon, relYaw, aimTy),
                    WeaponAim.renderYaw(weapon, relYawO, aimTy), partialTick)));
            }
            if (w.turret && operated) { // block 2: rotationTurretYaw==0 for the OPERATING player; an unmanned weapon's
                pose.mulPose(Axis.YP.rotationDegrees(ring)); // rotationTurretYaw tracks the ring -> block 2 cancels
            }
            boolean revSign = false;
            float defYaw = weapon != null ? weapon.defaultYaw : 0.0F;
            if ((int) defYaw != 0) {
                float t = Mth.wrapDegrees(defYaw);
                revSign = (t >= 45.0F && t <= 135.0F) || (t <= -45.0F && t >= -135.0F);
                pose.mulPose(Axis.YP.rotationDegrees(defYaw)); // glRotatef(-defYaw,0,-1,0)
            }
            if (w.pitch && operated) {
                // Reference order: clamp the relative pitch FIRST (updateWeaponsRotation RNG), THEN negate for a
                // side-mounted gun (renderWeapon rev_sign) — negate-then-clamp breaks asymmetric ranges at the limits.
                float p = WeaponAim.rotationPitch(weapon, relPitch);
                float pO = WeaponAim.rotationPitch(weapon, relPitchO);
                if (revSign) {
                    p = -p;
                    pO = -pO;
                }
                pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, pO, p)));
            }
            if ((int) defYaw != 0) {
                pose.mulPose(Axis.YP.rotationDegrees(-defYaw)); // glRotatef(+defYaw,0,-1,0)
            }
            if (w.rotBarrel) {
                pose.mulPose(new Quaternionf().rotateAxis((float) Math.toRadians(barrelDeg),
                    (float) w.rot.x(), (float) w.rot.y(), (float) w.rot.z()));
            }
            pose.translate(-w.pos.x(), -w.pos.y(), -w.pos.z());
            MchModelRenderer.renderPartSpan(model, pose, consumer, light, overlay, c, c, c, 255,
                "$" + w.modelName);
            if (w.child != null) {
                for (MCH_AircraftInfo.PartWeaponChild wc : w.child) {
                    pose.pushPose();
                    renderWeaponChild(model, wc, weapon, relYaw, relYawO, relPitch, relPitchO, aimTy,
                        pose, consumer, light, overlay, partialTick, c, operated);
                    pose.popPose();
                }
            }
            pose.popPose();
        }
    }

    /** Port of {@code MCH_RenderAircraft.renderWeaponChild} (687-764): same aim as the parent MINUS the turret ring and
     *  the barrel; the {@code pose} is already at the parent's post-{@code (-w.pos)} frame. */
    private static void renderWeaponChild(MchModel model, MCH_AircraftInfo.PartWeaponChild w, MCH_AircraftInfo.Weapon weapon,
                                          float relYaw, float relYawO, float relPitch, float relPitchO, float aimTy,
                                          PoseStack pose, VertexConsumer consumer, int light, int overlay, float partialTick,
                                          int c, boolean operated) {
        pose.translate(w.pos.x(), w.pos.y(), w.pos.z());
        if (w.yaw && operated) {
            pose.mulPose(Axis.YP.rotationDegrees(-calcRot(
                WeaponAim.renderYaw(weapon, relYaw, aimTy), WeaponAim.renderYaw(weapon, relYawO, aimTy), partialTick)));
        }
        boolean revSign = false;
        float defYaw = weapon != null ? weapon.defaultYaw : 0.0F;
        if ((int) defYaw != 0) {
            float t = Mth.wrapDegrees(defYaw);
            revSign = (t >= 45.0F && t <= 135.0F) || (t <= -45.0F && t >= -135.0F);
            pose.mulPose(Axis.YP.rotationDegrees(defYaw));
        }
        if (w.pitch && operated) {
            float p = WeaponAim.rotationPitch(weapon, relPitch); // clamp FIRST, then rev_sign negate (reference order)
            float pO = WeaponAim.rotationPitch(weapon, relPitchO);
            if (revSign) {
                p = -p;
                pO = -pO;
            }
            pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, pO, p)));
        }
        if ((int) defYaw != 0) {
            pose.mulPose(Axis.YP.rotationDegrees(-defYaw));
        }
        pose.translate(-w.pos.x(), -w.pos.y(), -w.pos.z());
        MchModelRenderer.renderPartSpan(model, pose, consumer, light, overlay, c, c, c, 255,
            "$" + w.modelName);
    }

    // The weapon aim formula lives in mcheli.agnostic.weapon.WeaponAim, shared VERBATIM with the server's
    // MchFireControl.fireSelectedWeapon. Two copies of it here and there is exactly how the rendered barrel and
    // the projectile drifted apart: this file's local copy was passing the operator's own yaw as `ty`, which
    // collapsed the finite-yaw clamp, while the server fired along the pilot's turret ring.

    /**
     * Short-path angle interpolation — the 1.21.1 port of {@code MCH_RenderAircraft.calcRot}. Both endpoints are
     * wrapped to [-180,180]; when they straddle the seam, {@code prev} is shifted by ±360 so it takes the short way.
     */
    protected static float calcRot(float rot, float prevRot, float tickTime) {
        rot = Mth.wrapDegrees(rot);
        prevRot = Mth.wrapDegrees(prevRot);
        if (rot - prevRot < -180.0F) {
            prevRot -= 360.0F;
        } else if (prevRot - rot < -180.0F) {
            prevRot += 360.0F;
        }
        return prevRot + (rot - prevRot) * tickTime;
    }
}
