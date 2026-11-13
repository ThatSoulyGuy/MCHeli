package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.aircraft.MCH_PartStatus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The switchable and animated parts of one vehicle: the canopy (with the pilot boarding gate and the new-pilot close),
 * the hatch, the wing fold and sweep, the weapon bays, the light hatch, the rotation parts, the throttle lever, the
 * camera-turret look and the landing gear (reference MCH_Parts on DataWatcher 31, updateWeaponBay, updatePartLightHatch,
 * onUpdate_PartRotation, updatePartCameraRotate, the throttle lever and the gear part). Only the status bits sync
 * ({@link AbstractMchVehicle#DATA_PART_STATUS}, laid out by {@link MCH_PartStatus}, and
 * {@link AbstractMchVehicle#DATA_GEAR}); every animator ramps toward its bit on BOTH sides, like the reference.
 *
 * <p>The only writer of DATA_PART_STATUS and DATA_GEAR: {@link MchVehicleVision} changes the camera and search-light
 * bits through {@link #setCameraModeBits} and {@link #toggleSearchLight}. The one part/mode switch cooldown stays in the
 * entity ({@link AbstractMchVehicle#modeSwitchCooldown()}), shared with the plane's VTOL switch and pilot boarding.
 * The public methods are the client render read-models; everything else is reached through the entity.
 */
public final class MchVehicleParts {

    private final AbstractMchVehicle v;

    private String partsConfig;                       // the config the animators below were built for
    private mcheli.agnostic.aircraft.MCH_Parts canopyPart; // reference createCanopy: 3.5°/tick
    private mcheli.agnostic.aircraft.MCH_Parts hatchPart;  // reference createHatch: 1.5°/tick
    private mcheli.agnostic.aircraft.MCH_Parts wingPart;   // reference MCP_EntityPlane.createWing: 2.5°/tick
    private float[] bayRot = new float[0];
    private float[] prevBayRot = new float[0];
    private float lightHatch, prevLightHatch;         // reference rotLightHatch 0..1 (±0.5/tick with the search light)
    private float[] rotPartRot = new float[0];
    private float[] prevRotPartRot = new float[0];
    private float throttleLever, prevThrottleLever;   // CLIENT: the synced flight throttle, per tick, for the lever
    private float cameraPartYaw, prevCameraPartYaw;   // CLIENT: reference camera.partRotationYaw/Pitch (world look)
    private float cameraPartPitch, prevCameraPartPitch;
    /** SERVER: a new pilot found the canopy open while the cooldown ran (see {@link #closeCanopyForNewPilot}). */
    private boolean pendingCanopyClose;
    /** The gear part (status, angle 0 down .. 90 up, switch cooldown); inert for a config without {@code AddPartLG*}. */
    private final mcheli.agnostic.sim.LandingGear landingGear = new mcheli.agnostic.sim.LandingGear();

    MchVehicleParts(AbstractMchVehicle v) {
        this.v = v;
    }

    /** The synced part status word (read by {@link MchVehicleVision} too). */
    int partStatus() { return v.getEntityData().get(AbstractMchVehicle.DATA_PART_STATUS); }

    private void setPartBit(int bit, boolean on) {
        int s = partStatus();
        v.getEntityData().set(AbstractMchVehicle.DATA_PART_STATUS, MCH_PartStatus.with(s, bit, on));
    }

    /** (Re)build the animators when the config changes; a new animator adopts the synced status at rest, so a client
     *  that starts tracking a vehicle with its hatch open sees it open rather than watching it swing open. */
    private void ensureParts() {
        String name = v.configName();
        if (name.equals(this.partsConfig)) {
            return;
        }
        this.partsConfig = name;
        MCH_AircraftInfo info = v.weaponHostInfo();
        int s = partStatus();
        this.canopyPart = info != null && info.haveCanopy() ? atRest(new mcheli.agnostic.aircraft.MCH_Parts("Canopy", 90.0F, 3.5F), (s & MCH_PartStatus.CANOPY) != 0) : null;
        this.hatchPart = info != null && info.haveHatch() ? atRest(new mcheli.agnostic.aircraft.MCH_Parts("Hatch", 90.0F, 1.5F), (s & MCH_PartStatus.HATCH) != 0) : null;
        this.wingPart = info instanceof mcheli.agnostic.plane.MCP_PlaneInfo pi && pi.haveWing()
            ? atRest(new mcheli.agnostic.aircraft.MCH_Parts("Wing", 90.0F, 2.5F), (s & MCH_PartStatus.WING) != 0) : null;
        int bays = info != null ? Math.min(MCH_PartStatus.MAX_BAYS, info.partWeaponBay.size()) : 0;
        this.bayRot = new float[bays];
        this.prevBayRot = new float[bays];
        for (int i = 0; i < bays; i++) {
            this.bayRot[i] = this.prevBayRot[i] = MCH_PartStatus.bayOpen(s, i) ? 90.0F : 0.0F;
        }
        int rp = info != null ? info.partRotPart.size() : 0;
        this.rotPartRot = new float[rp];
        this.prevRotPartRot = new float[rp];
        this.lightHatch = this.prevLightHatch = (s & MCH_PartStatus.SEARCH_LIGHT) != 0 ? 1.0F : 0.0F;
    }

    private static mcheli.agnostic.aircraft.MCH_Parts atRest(mcheli.agnostic.aircraft.MCH_Parts p, boolean on) {
        p.forceSwitch(on);
        return p;
    }

    /** One tick of every part (both sides) — the reference order is the cooldown, the light hatch and camera turret,
     *  then {@code updateParts} after the flight update, then the rotation parts; none of them feed each other. The
     *  server half also runs the camera viewers ({@link MchVehicleVision#updateViewers}) at their reference position. */
    void tick() {
        ensureParts();
        MCH_AircraftInfo info = v.weaponHostInfo();
        v.modeSwitchCooldown().tick(); // reference onUpdate:1440-1442, the one shared part/mode switch cooldown
        boolean server = !v.level().isClientSide;
        if (server) {
            if (this.pendingCanopyClose && v.modeSwitchCooldown().ready()) {
                this.pendingCanopyClose = false;
                if (v.pilot() != null) {
                    v.closeCanopy();
                }
            }
            if (v.isDestroyed() && (partStatus() & MCH_PartStatus.DESTROY_CLEAR) != 0) {
                // Reference destroyAircraft: the search light goes out and every camera returns to normal.
                v.getEntityData().set(AbstractMchVehicle.DATA_PART_STATUS, partStatus() & ~MCH_PartStatus.DESTROY_CLEAR);
            }
            updateWeaponBayTargets(info);
            autoUnfoldSweepWing();
            v.vision().updateViewers();
        }
        int s = partStatus();
        if (!v.isDestroyed()) { // reference updateParts: a wreck's parts freeze where they are
            stepPart(this.canopyPart, (s & MCH_PartStatus.CANOPY) != 0, server, CANOPY_SOUNDS);
            stepPart(this.hatchPart, (s & MCH_PartStatus.HATCH) != 0, server, HATCH_SOUNDS);
            stepPart(this.wingPart, (s & MCH_PartStatus.WING) != 0, server, WING_SOUNDS);
        }
        // Weapon bays: 3°/tick toward 90 while a seated crew member has a bay weapon selected (reference updateWeaponBay).
        for (int i = 0; i < this.bayRot.length; i++) {
            this.prevBayRot[i] = this.bayRot[i];
            boolean open = MCH_PartStatus.bayOpen(s, i);
            this.bayRot[i] = open ? Math.min(90.0F, this.bayRot[i] + 3.0F) : Math.max(0.0F, this.bayRot[i] - 3.0F);
        }
        // Light hatch: reference updatePartLightHatch, ±0.5 per tick clamped to 0..1.
        this.prevLightHatch = this.lightHatch;
        this.lightHatch = Mth.clamp(this.lightHatch + ((s & MCH_PartStatus.SEARCH_LIGHT) != 0 ? 0.5F : -0.5F), 0.0F, 1.0F);
        if (!server && info != null) {
            tickClientPartMotion(info);
        }
    }

    /** A part's sounds (reference {@code MCH_Parts.Sound} slots, volume 1). Every reference part uses the same sound
     *  for both start directions and for both ends, so one of each is enough; null = silent. */
    private record PartSounds(String switching, float switchPitch, String start, float startPitch, String end, float endPitch) {}
    private static final PartSounds CANOPY_SOUNDS = new PartSounds(null, 0.0F, null, 0.0F, "plane_cc", 1.0F);
    private static final PartSounds HATCH_SOUNDS = new PartSounds("plane_cv", 0.5F, null, 0.0F, "plane_cc", 1.0F);
    private static final PartSounds WING_SOUNDS = new PartSounds(null, 0.0F, "plane_cc", 0.5F, "plane_cc", 0.5F);

    /** Advance one animator toward its status bit. The SERVER plays the reference part sounds: the switching/start
     *  ones when the status flips ({@code setStatusServer}), the end ones when the travel completes ({@code update}). */
    private void stepPart(mcheli.agnostic.aircraft.MCH_Parts p, boolean on, boolean server, PartSounds snd) {
        if (p == null) {
            return;
        }
        if (p.setStatus(on) && server) {
            if (snd.switching() != null) {
                v.playMchSound(snd.switching(), 1.0F, snd.switchPitch());
            }
            if (snd.start() != null) {
                v.playMchSound(snd.start(), 1.0F, snd.startPitch());
            }
        }
        if (p.update() != mcheli.agnostic.aircraft.MCH_Parts.Event.NONE && server && snd.end() != null) {
            v.playMchSound(snd.end(), 1.0F, snd.endPitch());
        }
    }

    /** CLIENT: the purely visual part motion — the throttle lever, the rotation parts (reference
     *  {@code onUpdate_PartRotation}, client-only via {@code haveRotPart}) and the camera turret look (reference
     *  {@code updatePartCameraRotate}: the seat-1 crew member, else the pilot; held when nobody rides). */
    private void tickClientPartMotion(MCH_AircraftInfo info) {
        this.prevThrottleLever = this.throttleLever;
        this.throttleLever = v.getThrottleInput();
        boolean ridden = v.pilot() != null;
        for (int i = 0; i < this.rotPartRot.length && i < info.partRotPart.size(); i++) {
            MCH_AircraftInfo.RotPart rp = info.partRotPart.get(i);
            this.prevRotPartRot[i] = this.rotPartRot[i];
            if (!v.isDestroyed() && rp.rotAlways || ridden) { // reference operator precedence, kept
                float r = this.rotPartRot[i] + rp.rotSpeed;
                if (r < 0.0F) {
                    r += 360.0F;
                }
                if (r >= 360.0F) {
                    r -= 360.0F;
                }
                this.rotPartRot[i] = r;
            }
        }
        Entity e = v.seatPassenger(1);
        if (e == null) {
            e = v.pilot();
        }
        this.prevCameraPartYaw = this.cameraPartYaw;
        this.prevCameraPartPitch = this.cameraPartPitch;
        if (e != null) {
            // DELIBERATE divergence: the reference copied the new yaw into prevPartRotationYaw before rendering, so the
            // camera turret's yaw stepped at 20 Hz; tracking the previous tick keeps it interpolated like the pitch.
            this.cameraPartYaw = e.getYRot();
            this.cameraPartPitch = e.getXRot();
        }
    }

    /** SERVER: which weapon bays are open — the reference {@code updateWeaponBay}: a bay opens while any OCCUPIED seat
     *  has one of the bay's weapons selected. */
    private void updateWeaponBayTargets(MCH_AircraftInfo info) {
        if (info == null || this.bayRot.length == 0) {
            return;
        }
        int bits = 0;
        for (int b = 0; b < this.bayRot.length; b++) {
            MCH_AircraftInfo.WeaponBay bay = info.partWeaponBay.get(b);
            if (v.weaponLoadout() != null && bayWeaponSelected(info, bay)) {
                bits |= 1 << b;
            }
        }
        int s = partStatus();
        int next = MCH_PartStatus.withBays(s, bits);
        if (next != s) {
            v.getEntityData().set(AbstractMchVehicle.DATA_PART_STATUS, next);
        }
    }

    private boolean bayWeaponSelected(MCH_AircraftInfo info, MCH_AircraftInfo.WeaponBay bay) {
        for (int sid = 0; sid < v.seatCount(); sid++) {
            int sel = v.weaponLoadout().selectedIndex(sid);
            if (sel < 0 || sel >= v.weaponLoadout().size() || v.seatPassenger(sid) == null) {
                continue;
            }
            String selected = v.weaponLoadout().get(sel).weaponName;
            for (Integer wid : bay.weaponIds) {
                if (wid != null && wid >= 0 && wid < info.weaponSetList.size()
                    && info.weaponSetList.get(wid).type.equalsIgnoreCase(selected)) {
                    return true;
                }
            }
        }
        return false;
    }

    // -- canopy (reference openCanopy / closeCanopy / isCanopyClose) --

    boolean haveCanopy() { ensureParts(); return this.canopyPart != null; }

    /** Reference {@code isCanopyClose}: no canopy, or switched closed AND at rest. The flight models gate control on it. */
    boolean isCanopyClose() {
        ensureParts();
        return this.canopyPart == null || !this.canopyPart.getStatus() && this.canopyPart.rotation <= 0.01F;
    }

    /** SERVER: open the canopy (reference {@code openCanopy}: off cooldown, then a 20-tick cooldown). */
    void openCanopy() {
        if (v.haveCanopy() && v.modeSwitchCooldown().ready() && !v.level().isClientSide) {
            setPartBit(MCH_PartStatus.CANOPY, true);
            v.modeSwitchCooldown().start();
        }
    }

    /** SERVER: close the canopy if it is open (reference {@code closeCanopy}). */
    void closeCanopy() {
        if (v.haveCanopy() && v.modeSwitchCooldown().ready() && !v.level().isClientSide && (partStatus() & MCH_PartStatus.CANOPY) != 0) {
            setPartBit(MCH_PartStatus.CANOPY, false);
            v.modeSwitchCooldown().start();
        }
    }

    /** SERVER: a rider just took the PILOT seat. An open canopy blocks all flight control, so it closes, as the
     *  reference {@code interactFirst} does on every pilot boarding, the seat-switch path included ({@code switchSeat},
     *  :4358). The boarding click ({@link #canopyBoardingGate}) has already closed it; this covers the paths that skip
     *  that gate (a gunner grabbing the pilot seat, a forced mount). Where the reference's {@code closeCanopy} silently
     *  did nothing while the switch cooldown ran, leaving the new pilot unable to fly, the close is held until the
     *  cooldown ends (a deliberate divergence). */
    void closeCanopyForNewPilot() {
        if (!v.haveCanopy() || (partStatus() & MCH_PartStatus.CANOPY) == 0) {
            return;
        }
        v.closeCanopy();
        this.pendingCanopyClose = (partStatus() & MCH_PartStatus.CANOPY) != 0;
    }

    /** Canopy rotation 0..90 interpolated, for the renderer (0 without a canopy). */
    public float canopyRotation(float pt) { return this.canopyPart != null ? this.canopyPart.lerp(pt) : 0.0F; }
    /** Canopy travel fraction 0..1 interpolated (the slide-canopy offset factor). */
    public float canopyFactor(float pt) { return this.canopyPart != null ? this.canopyPart.lerp(pt) / this.canopyPart.rotationMax : 0.0F; }

    /** Reference {@code interactFirst}:4347-4358, boarding the PILOT seat: a closed canopy opens first and the click is
     *  spent, boarding waits out the switch cooldown, and the pilot climbing in closes it again. Returns null when the
     *  boarding may go ahead (having closed the canopy), else the result to return. The cooldown refusal applies to a
     *  canopy-less vehicle too: in the reference it sits outside the canopy test (:4353), so a gear, hatch, wing or VTOL
     *  switch also holds the pilot seat for its 20 ticks. SERVER. */
    InteractionResult canopyBoardingGate() {
        if (!v.haveCanopy()) {
            return v.modeSwitchCooldown().ready() ? null : InteractionResult.PASS;
        }
        if (v.isCanopyClose()) {
            v.openCanopy();
            return InteractionResult.SUCCESS;
        }
        if (!v.modeSwitchCooldown().ready()) {
            return InteractionResult.PASS; // reference :4353: any part/mode switch still cooling down
        }
        v.closeCanopy();
        return null;
    }

    // -- hatch and wing fold (reference foldHatch / MCP_EntityPlane.foldWing, both on switchHatch / KeyZoom) --

    boolean haveHatch() { ensureParts(); return this.hatchPart != null; }

    /** Reference {@code canFoldHatch}: off cooldown and fully closed. */
    boolean canFoldHatch() { return v.haveHatch() && v.modeSwitchCooldown().ready() && this.hatchPart.isOFF(); }

    /** Reference {@code canUnfoldHatch}: off cooldown and fully open. */
    boolean canUnfoldHatch() { return v.haveHatch() && v.modeSwitchCooldown().ready() && this.hatchPart.isON(); }

    /** Hatch rotation 0..90 interpolated, and its travel fraction (the slide-hatch offset). */
    public float hatchRotation(float pt) { return this.hatchPart != null ? this.hatchPart.lerp(pt) : 0.0F; }
    public float hatchFactor(float pt) { return this.hatchPart != null ? this.hatchPart.lerp(pt) / this.hatchPart.rotationMax : 0.0F; }
    /** The hatch's current travel fraction (reference {@code partHatch.getFactor()}, the sweep-wing stand-in). */
    float hatchFactor() { return this.hatchPart != null ? this.hatchPart.getFactor() : 0.0F; }

    boolean haveWing() { ensureParts(); return this.wingPart != null; }

    /** Reference {@code MCP_EntityPlane.canUseWing}: a folded wing grounds the plane (a variable-sweep wing only below
     *  0.2 throttle, where the sweep means it is parked folded). */
    boolean canUseWing() {
        ensureParts();
        if (this.wingPart == null) {
            return true;
        }
        if (v.weaponHostInfo() instanceof mcheli.agnostic.plane.MCP_PlaneInfo pi && pi.isVariableSweepWing) {
            return v.simThrottle() < 0.2 ? this.wingPart.isOFF() : true;
        }
        return this.wingPart.isOFF();
    }

    /** Reference {@code canFoldWing}. A variable-sweep wing sweeps back in flight above 0.7 throttle or on the ground
     *  at idle; a folding wing folds only on (or just above) the ground with the throttle closed. */
    boolean canFoldWing() {
        if (!v.haveWing() || !v.modeSwitchCooldown().ready()) {
            return false;
        }
        double throttle = v.simThrottle();
        if (v.weaponHostInfo() instanceof mcheli.agnostic.plane.MCP_PlaneInfo pi && pi.isVariableSweepWing) {
            if (!v.onGround() && !blockBelow(3, 20)) {
                if (throttle < 0.7F) {
                    return false;
                }
            } else if (throttle > 0.1F) {
                return false;
            }
        } else {
            if (!v.onGround() && !blockBelow(3, 3)) {
                return false;
            }
            if (throttle > 0.01F) {
                return false;
            }
        }
        return this.wingPart.isOFF();
    }

    /** Reference {@code canUnfoldWing}: off cooldown and fully folded. */
    boolean canUnfoldWing() { return v.haveWing() && v.modeSwitchCooldown().ready() && this.wingPart.isON(); }

    public float wingRotation(float pt) { return this.wingPart != null ? this.wingPart.lerp(pt) : 0.0F; }

    /** True when a variable-sweep morph part exists (reference {@code getMaxSpeed}: the wing, else the hatch). */
    boolean hasSweepPart() { ensureParts(); return this.wingPart != null || this.hatchPart != null; }

    /** The sweep part's travel fraction 0..1 (reference {@code partWing.getFactor()}, else {@code partHatch}'s). */
    float sweepPartFactor() {
        ensureParts();
        return this.wingPart != null ? this.wingPart.getFactor() : v.hatchFactor();
    }

    /** SERVER: the pilot's hatch/wing switch — reference {@code switchHatch} from KeyZoom outside gunner mode
     *  ({@code MCH_HeliPacketHandler:113}, {@code MCP_PlanePacketHandler:100-105}, {@code MCH_TankPacketHandler:99}):
     *  the hatch if there is one, else a plane's wing; open when at rest closed, close when at rest open. */
    void switchHatch(Player player) {
        if (v.level().isClientSide || v.seatIndexOf(player) != 0 || v.isDestroyed()) {
            return;
        }
        if (v.haveHatch()) {
            if (v.canFoldHatch()) {
                setPartBit(MCH_PartStatus.HATCH, true);
                v.modeSwitchCooldown().start();
            } else if (v.canUnfoldHatch()) {
                setPartBit(MCH_PartStatus.HATCH, false);
                v.modeSwitchCooldown().start();
            }
        } else if (v.canFoldWing()) {
            setPartBit(MCH_PartStatus.WING, true);
            v.modeSwitchCooldown().start();
        } else if (v.canUnfoldWing()) {
            setPartBit(MCH_PartStatus.WING, false);
            v.modeSwitchCooldown().start();
        }
    }

    /** SERVER: reference {@code MCP_EntityPlane.updateParts}:896-903 — a swept variable-sweep wing spreads again when
     *  the throttle is opened past 0.2 while slow (below 0.5) or near the ground. */
    private void autoUnfoldSweepWing() {
        if (this.wingPart != null && !v.isDestroyed() && this.wingPart.isON()
            && v.weaponHostInfo() instanceof mcheli.agnostic.plane.MCP_PlaneInfo pi && pi.isVariableSweepWing) {
            double throttle = v.simThrottle();
            if (!(throttle < 0.2F) && (throttle < 0.5 || blockBelow(1, 10))) {
                setPartBit(MCH_PartStatus.WING, false);
            }
        }
    }

    /** Reference {@code MCH_Lib.getBlockIdY(this, size, -len) != 0}: anything collidable in the {@code size}×{@code size}
     *  column within {@code len} blocks down. Every port of {@code getBlockIdY} shares the ONE probe,
     *  {@link mcheli.agnostic.sim.GroundProbe}: the reference {@code canCollideCheck(0, true)} test (every block but air
     *  and fire, so water and plants count as ground, and a floatplane can fold its wings afloat) with floor rounding,
     *  which stays right below y = 0. */
    private boolean blockBelow(int size, int len) {
        return mcheli.agnostic.sim.GroundProbe.blockIdInColumn(v.ref, size, -len) != 0;
    }

    // -- weapon bays, light hatch, throttle lever, rotation parts, camera turret (renderer accessors) --

    /** Weapon bay {@code i}'s door angle 0..90 interpolated (reference {@code WeaponBay.rot}). */
    public float weaponBayRotation(int i, float pt) {
        return i >= 0 && i < this.bayRot.length ? this.prevBayRot[i] + (this.bayRot[i] - this.prevBayRot[i]) * pt : 0.0F;
    }

    /** Light hatch travel 0..1 interpolated (reference {@code rotLightHatch}). */
    public float lightHatchRotation(float pt) { return this.prevLightHatch + (this.lightHatch - this.prevLightHatch) * pt; }

    /** CLIENT: the throttle-lever value interpolated (reference {@code MCH_Lib.smooth(getCurrentThrottle, prev, t)}). */
    public float throttleLever(float pt) { return this.prevThrottleLever + (this.throttleLever - this.prevThrottleLever) * pt; }

    /** CLIENT: rotation part {@code i}'s angle, raw and previous (the renderer unwraps the 360→0 step). */
    public float rotPartRotation(int i) { return i >= 0 && i < this.rotPartRot.length ? this.rotPartRot[i] : 0.0F; }
    public float prevRotPartRotation(int i) { return i >= 0 && i < this.prevRotPartRot.length ? this.prevRotPartRot[i] : 0.0F; }

    /** CLIENT: the camera turret / search light look, interpolated, in WORLD degrees (reference
     *  {@code camera.partRotationYaw/Pitch}, {@code lastSearchLightYaw/Pitch}). */
    public float cameraPartYaw(float pt) { return Mth.rotLerp(pt, this.prevCameraPartYaw, this.cameraPartYaw); }
    public float cameraPartPitch(float pt) { return Mth.lerp(pt, this.prevCameraPartPitch, this.cameraPartPitch); }

    // -- the camera-mode and search-light bits, written here for MchVehicleVision --

    /** Replace {@code seat}'s camera-mode bits (the vision's camera key, viewer change and release). */
    void setCameraModeBits(int seat, int mode) {
        v.getEntityData().set(AbstractMchVehicle.DATA_PART_STATUS, MCH_PartStatus.withCameraMode(partStatus(), seat, mode));
    }

    /** Switch the search light (the vision's camera key on a vehicle with a search light). */
    void toggleSearchLight() {
        setPartBit(MCH_PartStatus.SEARCH_LIGHT, (partStatus() & MCH_PartStatus.SEARCH_LIGHT) == 0);
    }

    /** Part status to save (reference {@code PartStatus & getLastPartStatusMask()} plus {@code SearchLight}). */
    void save(CompoundTag tag) {
        tag.putInt("PartStatus", MCH_PartStatus.saved(partStatus()));
        tag.putBoolean("SearchLight", (partStatus() & MCH_PartStatus.SEARCH_LIGHT) != 0);
    }

    void load(CompoundTag tag) {
        int s = MCH_PartStatus.restored(tag.getInt("PartStatus"), tag.getBoolean("SearchLight"));
        v.getEntityData().set(AbstractMchVehicle.DATA_PART_STATUS, s);
        this.partsConfig = null; // rebuild the animators at rest in the loaded state
    }

    // -- landing gear (reference MCH_EntityAircraft gear part on DATA_GEAR) --

    /** Landing-gear angle 0..90 (down..up) — reference {@code getLandingGearRotation}; the renderer interpolates with
     *  the prev. Always 0 for a config without landing gear. */
    public float getGearAngle() { return this.landingGear.rotation(); }
    public float getPrevGearAngle() { return this.landingGear.prevRotation(); }

    /** Whether the landing gear is up (synced status; reference {@code isLandingGearFolded}). */
    boolean isLandingGearFolded() { return this.landingGear.isFolded(); }

    /** Reference {@code canFoldLandingGear}: a config with gear, fully down, nothing collidable within 10 below. */
    boolean canFoldLandingGear() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        return info != null && info.haveLandingGear() && this.landingGear.canFold(v.ref);
    }

    /** Reference {@code canUnfoldLandingGear}: a config with gear, fully up. */
    boolean canUnfoldLandingGear() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        return info != null && info.haveLandingGear() && this.landingGear.canUnfold();
    }

    /** SERVER: reference {@code foldLandingGear} (gated only by the shared part/mode switch cooldown, like the
     *  reference, which it restarts). */
    void foldLandingGear() {
        if (!v.level().isClientSide && hasLandingGear() && this.landingGear.fold(v.modeSwitchCooldown())) {
            onLandingGearSwitched();
        }
    }

    /** SERVER: reference {@code unfoldLandingGear} (gated only by the shared part/mode switch cooldown). */
    void unfoldLandingGear() {
        if (!v.level().isClientSide && hasLandingGear() && this.landingGear.unfold(v.modeSwitchCooldown())) {
            onLandingGearSwitched();
        }
    }

    /** SERVER: the pilot's gear key (reference {@code KeyGearUpDown} -> {@code switchGear} 1/2): retract when
     *  {@link #canFoldLandingGear}, else extend when {@link #canUnfoldLandingGear}. Re-checked here, not trusted. */
    void toggleLandingGear(Player player) {
        if (v.level().isClientSide || v.seatIndexOf(player) != 0 || v.isDestroyed()) {
            return;
        }
        if (v.canFoldLandingGear()) {
            v.foldLandingGear();
        } else if (v.canUnfoldLandingGear()) {
            v.unfoldLandingGear();
        }
    }

    private boolean hasLandingGear() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        return info != null && info.haveLandingGear(); // reference partLandingGear != null
    }

    /** SERVER: publish a gear status change + the reference part sounds ({@code soundSwitching} + {@code soundStart*}). */
    private void onLandingGearSwitched() {
        v.getEntityData().set(AbstractMchVehicle.DATA_GEAR, this.landingGear.isFolded());
        v.playMchSound("plane_cv", 1.0F, 0.75F);
        v.playMchSound("plane_cc", 1.0F, 0.5F);
    }

    /**
     * Reference {@code updateParts} for the landing gear (MCH_EntityAircraft:5406-5446), on both sides after the
     * physics: the client takes the synced status, both sides advance the travel, and the server plays the end-of-
     * travel sound and applies the automatic retract/extend. A wreck's gear freezes. (The reference server also
     * stepped the part once inside {@code setStatusServer}; the port does not, so the server gear's angle no longer
     * leads the client's by one step.)
     */
    void tickLandingGear() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null || !info.haveLandingGear() || v.isDestroyed()) {
            return;
        }
        boolean client = v.level().isClientSide;
        if (client) {
            this.landingGear.setFolded(v.getEntityData().get(AbstractMchVehicle.DATA_GEAR)); // updateStatusClient
        }
        mcheli.agnostic.sim.LandingGear.Event ev = this.landingGear.update();
        if (client) {
            return;
        }
        if (ev != mcheli.agnostic.sim.LandingGear.Event.NONE) {
            v.playMchSound("plane_cc", 1.0F, 0.5F); // soundEndSwichOn / soundEndSwichOff
        }
        if (this.landingGear.autoSwitch(v.ref, info, v.simThrottle(), v.unfoldLandingGearThrottle(), v.getVtolMode())) {
            onLandingGearSwitched();
        }
    }
}
