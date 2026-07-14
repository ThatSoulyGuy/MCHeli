package mcheli.dependent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.helicopter.MCH_HeliInfo;
import mcheli.agnostic.model.MchModel;
import mcheli.agnostic.model.ModelGroup;
import mcheli.agnostic.plane.MCP_PlaneInfo;
import mcheli.agnostic.spi.ModelHandle;
import mcheli.agnostic.value.Vec3d;
import mcheli.agnostic.vehicle.MCH_VehicleInfo;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.port.NeoResourceSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
 * {@code .mqo} parser already divides vertices by 100 into world units. The cockpit glass/canopy groups are dropped
 * (their texture is opaque, so they'd wall off the pilot's view). A destroyed wreck renders near-black.
 */
public abstract class MchModelEntityRenderer<T extends AbstractMchVehicle> extends EntityRenderer<T> {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Resource directory for this category's models/textures ({@code helicopters}/{@code planes}/{@code tanks}/{@code vehicles}). */
    private final String categoryDir;
    private final Map<String, MchModel> modelCache = new HashMap<>();
    private final Map<String, Set<String>> declaredCache = new HashMap<>();
    private final Map<String, ResourceLocation> textureCache = new HashMap<>();

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

    /** Groups NEVER drawn — cockpit glass meshes (their texture renders opaque in the cutout pipeline and would wall off
     *  the pilot's view). A group that is itself a DECLARED part ({@code $canopy0} from {@code AddPartCanopy}, …) is
     *  exempt: the reference draws declared parts, and the name-substring veto must not delete them (it cost six planes
     *  their whole canopy + the mxtmv its door). Cached per config name. */
    protected Set<String> hiddenGroups(T entity, MchModel model) {
        // The reference hides NO group by name — cockpit glass is DRAWN (translucent via its texture alpha), and the
        // pilot looks through it. The port used to hide "glass"/"canopy" groups because they rendered OPAQUE in the
        // cutout pipeline and walled off the cockpit; now that the model draws in the blended entityTranslucentCull
        // pass (see render()), glass renders see-through from the texture alpha and must NOT be hidden. Nothing is
        // hidden here anymore; the method stays so span drawing keeps its (now-empty) skip set.
        return java.util.Collections.emptySet();
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
        ModelHandle h = new NeoResourceSource().loadModel(name);
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
        if (model != null) {
            pose.pushPose();
            // Lift the model so its LOWEST point (minY, e.g. skids/tracks/wheels ~-0.3) sits at the entity feet (the
            // ground). The reference's wheel-suspension physics hold the hull up by |minY|; those aren't ported, so
            // without this the model origin is at the feet and the whole vehicle sinks by |minY|. World-vertical, so it
            // must come BEFORE the yaw/pitch/roll rotations (matching the reference, which lifts the entity in world-Y).
            pose.translate(0.0, -model.minY, 0.0);
            // Reference order: yaw about -Y, pitch about X, roll about Z (MCH_RenderHeli.renderAircraft). No scale.
            pose.mulPose(Axis.YP.rotationDegrees(-entityYaw));
            float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            float roll = calcRot(entity.getRollAngle(), entity.getPrevRollAngle(), partialTick);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.ZP.rotationDegrees(roll));

            // The whole model draws in ONE blended pass — the reference's technique (W_Render.setCommonRenderParam:
            // glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); cull on; depth-write on). Cockpit
            // GLASS is translucent purely from the body texture's ALPHA channel (glass texels have alpha < 1), NOT from
            // any material or name — so entityTranslucentCull (alpha-blend + backface cull + depth-write) reproduces it
            // exactly. The old entityCutoutNoCull alpha-TESTED (binary), rendering any glass texel fully opaque.
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentCull(getTextureLocation(entity)));
            // Destroyed wreck: darken the whole hull to a scorched near-black (reference 0.15·255 ≈ 38).
            int c = entity.isDestroyed() ? 38 : 255;
            Set<String> dynamic = dynamicGroupsLower(entity, model);
            Set<String> hidden = hiddenGroups(entity, model);
            Set<String> fpHidden = firstPersonHidden(entity); // HideGM weapon parts, local pilot in first person only
            if (MchModelRenderer.hasGroup(model, "$body")) {
                // Reference composition (MCH_RenderAircraft.renderBody + per-part renderPart): draw the $body SPAN, then
                // each config-DECLARED part's span. A $-group that is neither $body nor a declared part is NEVER drawn
                // (the reference hides it — hidden weapon variants, dummy/marker groups). Group ORDER in the model file
                // no longer matters, so a model that puts $weapon0 before $body (KV-2) renders exactly like one that
                // doesn't (m1a2). Declared parts whose animation is ported draw in renderDynamicParts; the rest draw
                // static here so no declared part ever vanishes.
                MchModelRenderer.renderPartSpan(model, pose, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                    c, c, c, 255, "$body", hidden);
                for (String gname : declaredGroups(entity)) {
                    if (!dynamic.contains(gname) && !fpHidden.contains(gname)) {
                        // hidden can no longer contain a declared part's own name (see hiddenGroups), so a declared
                        // canopy/door span draws — with any glass sub-mesh inside it still skipped.
                        MchModelRenderer.renderPartSpan(model, pose, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                            c, c, c, 255, gname, hidden);
                    }
                }
                renderDynamicParts(entity, model, pose, consumer, packedLight, OverlayTexture.NO_OVERLAY, partialTick);
            } else if (dynamic.isEmpty() && hidden.isEmpty() && fpHidden.isEmpty()) {
                // No $body group: the reference falls back to renderAll (drawing every group once).
                MchModelRenderer.render(model, pose, consumer, packedLight, OverlayTexture.NO_OVERLAY, c, c, c, 255);
            } else {
                // No $body + animated/hidden parts: render all-minus-animated then the animated parts transformed
                // (avoids the reference renderAll quirk of double-drawing animated groups on a $body-less model).
                Set<String> exclude = new HashSet<>(dynamic);
                exclude.addAll(hidden);
                exclude.addAll(fpHidden);
                MchModelRenderer.renderExcept(model, pose, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                    c, c, c, 255, exclude);
                if (!dynamic.isEmpty()) {
                    renderDynamicParts(entity, model, pose, consumer, packedLight, OverlayTexture.NO_OVERLAY, partialTick);
                }
            }
            pose.popPose();
        }
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
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

    /** Lower-cased names of the model groups this subclass animates (excluded from the static hull). Empty by default. */
    protected Set<String> dynamicGroupsLower(T entity, MchModel model) {
        return Collections.emptySet();
    }

    /** Draw the animated parts (rotor blades, gear, turret). The {@code pose} is at the entity + oriented. */
    protected void renderDynamicParts(T entity, MchModel model, PoseStack pose, VertexConsumer consumer,
                                      int packedLight, int overlay, float partialTick) {
    }

    /** The hull colour for this entity: scorched near-black for a destroyed wreck (reference 0.15·255 ≈ 38), else white.
     *  Every render pass (hull, declared parts, animated parts) must use this so a wreck darkens UNIFORMLY. */
    protected static int wreckColor(AbstractMchVehicle entity) {
        return entity.isDestroyed() ? 38 : 255;
    }

    /** ALL lower-cased {@code $}-group names the config DECLARES as drawable parts (every {@code DrawnPart} list across
     *  the category infos) — the reference draws exactly {@code $body} + these; anything else is never drawn. Cached per
     *  config name. */
    private Set<String> declaredGroups(T entity) {
        return this.declaredCache.computeIfAbsent(entity.configName(), n -> {
            Set<String> s = new HashSet<>();
            MCH_AircraftInfo info = entity.hostInfo();
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
        });
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
        float barrelDeg = calcRot(entity.barrelSpin(), entity.prevBarrelSpin(), partialTick);
        Vec3d tp = info.turretPosition;
        int c = wreckColor(entity);
        Set<String> hidden = hiddenGroups(entity, model); // glass sub-meshes stay hidden inside weapon spans too

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
            pose.pushPose();
            // The RING is the pilot's turret slew (never a gunner's head); a weapon's own aim is its operator's look.
            float ring = w.turret ? ringYaw : 0.0F;
            if (w.turret) { // block 1: onto the turret ring about turretPosition; glRotatef(ring,0,-1,0) == Axis.YP(-ring)
                pose.translate(tp.x(), tp.y(), tp.z());
                pose.mulPose(Axis.YP.rotationDegrees(-ring));
                pose.translate(-tp.x(), -tp.y(), -tp.z());
            }
            pose.translate(w.pos.x(), w.pos.y(), w.pos.z());
            if (w.yaw && operated) {
                pose.mulPose(Axis.YP.rotationDegrees(-calcRot(
                    weaponYaw(weapon, relYaw, w.turret ? relYaw : 0.0F),
                    weaponYaw(weapon, relYawO, w.turret ? relYawO : 0.0F), partialTick)));
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
                float p = weaponPitch(weapon, relPitch);
                float pO = weaponPitch(weapon, relPitchO);
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
                "$" + w.modelName, hidden);
            if (w.child != null) {
                for (MCH_AircraftInfo.PartWeaponChild wc : w.child) {
                    pose.pushPose();
                    renderWeaponChild(model, wc, weapon, relYaw, relYawO, relPitch, relPitchO,
                        pose, consumer, light, overlay, partialTick, c, hidden, operated);
                    pose.popPose();
                }
            }
            pose.popPose();
        }
    }

    /** Port of {@code MCH_RenderAircraft.renderWeaponChild} (687-764): same aim as the parent MINUS the turret ring and
     *  the barrel; the {@code pose} is already at the parent's post-{@code (-w.pos)} frame. */
    private static void renderWeaponChild(MchModel model, MCH_AircraftInfo.PartWeaponChild w, MCH_AircraftInfo.Weapon weapon,
                                          float relYaw, float relYawO, float relPitch, float relPitchO,
                                          PoseStack pose, VertexConsumer consumer, int light, int overlay, float partialTick,
                                          int c, Set<String> hidden, boolean operated) {
        pose.translate(w.pos.x(), w.pos.y(), w.pos.z());
        if (w.yaw && operated) {
            pose.mulPose(Axis.YP.rotationDegrees(-calcRot(
                weaponYaw(weapon, relYaw, 0.0F), weaponYaw(weapon, relYawO, 0.0F), partialTick)));
        }
        boolean revSign = false;
        float defYaw = weapon != null ? weapon.defaultYaw : 0.0F;
        if ((int) defYaw != 0) {
            float t = Mth.wrapDegrees(defYaw);
            revSign = (t >= 45.0F && t <= 135.0F) || (t <= -45.0F && t >= -135.0F);
            pose.mulPose(Axis.YP.rotationDegrees(defYaw));
        }
        if (w.pitch && operated) {
            float p = weaponPitch(weapon, relPitch);   // clamp FIRST, then rev_sign negate (reference order)
            float pO = weaponPitch(weapon, relPitchO);
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
            "$" + w.modelName, hidden);
    }

    /** {@code ws.rotationYaw − ws.defaultRotationYaw} for a single rider (renderWeapon 592-601 + updateWeaponsRotation
     *  5055-5078): the rider look-yaw relative to the hull, clamped to a FINITE yaw range, plus the turret-ring yaw. */
    private static float weaponYaw(MCH_AircraftInfo.Weapon weapon, float relYaw, float ty) {
        if (weapon == null) {
            return relYaw; // reference `else e!=null` branch: rider yaw minus hull
        }
        float ey = Mth.wrapDegrees(relYaw - weapon.defaultYaw - ty);
        boolean finite = Math.abs((int) weapon.minYaw) < 360 && Math.abs((int) weapon.maxYaw) < 360;
        float rotationYaw = finite ? clampF(ey, weapon.minYaw, weapon.maxYaw) + weapon.defaultYaw + ty : ey + ty;
        return rotationYaw - weapon.defaultYaw;
    }

    /** {@code ws.rotationPitch} for a single rider (updateWeaponsRotation 5081-5082): rider look-pitch relative to the
     *  hull, clamped to the weapon's pitch range. */
    private static float weaponPitch(MCH_AircraftInfo.Weapon weapon, float relPitch) {
        return weapon == null ? relPitch : clampF(relPitch, weapon.minPitch, weapon.maxPitch);
    }

    private static float clampF(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

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
