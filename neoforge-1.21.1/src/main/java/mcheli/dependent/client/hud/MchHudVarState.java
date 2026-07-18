package mcheli.dependent.client.hud;

import java.util.Locale;
import mcheli.agnostic.hud.HudState;
import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The live {@link HudState} for the ridden vehicle: supplies expression variables and typed {@code DrawString} format
 * arguments from the entity + player. HP, ammo, fuel, throttle, gunner mode, UAV and heat-weapon state are all wired to
 * the real entity now; variables for genuinely-unported subsystems (entity radar + its {@code radar_rot} sweep, flares,
 * VTOL nozzle, autopilot, free-look, TV-missile, mortar range) still fall through to 0 / empty, which — because the HUD
 * configs guard those blocks with {@code If}-conditions — cleanly hides them until their feature lands.
 */
public final class MchHudVarState implements HudState {

    private final AbstractMchVehicle vehicle;
    private final Player player;
    private final int width;
    private final int height;
    private final float partialTick;
    private final Vec3 motion;
    private final double altitude;
    private final double seaAlt;
    private final long timeOfDay;
    private final int mcHour;
    private final int mcMin;
    private final int mcSec;

    public MchHudVarState(AbstractMchVehicle vehicle, Player player, int width, int height, float partialTick) {
        this.vehicle = vehicle;
        this.player = player;
        this.width = width;
        this.height = height;
        this.partialTick = partialTick;
        this.motion = vehicle.getDeltaMovement();
        this.altitude = computeAltitude();
        this.seaAlt = Math.max(0.0, vehicle.getY() - vehicle.level().getSeaLevel());
        long dayTime = vehicle.level().getDayTime();
        this.timeOfDay = ((dayTime % 24000L) + 24000L) % 24000L;
        long mc = (dayTime + 6000L) % 24000L; // reference: clock reads 06:00 at dawn
        this.mcHour = (int) (mc / 1000L);
        this.mcMin = (int) (mc % 1000L * 36L / 10L / 60L);
        this.mcSec = (int) (mc % 1000L * 36L / 10L % 60L);
    }

    /** Height above ground (AGL): a downward clip to the first solid block, else absolute Y. */
    private double computeAltitude() {
        Vec3 from = this.vehicle.position();
        Vec3 to = from.subtract(0.0, 512.0, 0.0);
        var hit = this.vehicle.level().clip(
            new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.vehicle));
        return hit.getType() != HitResult.Type.MISS ? from.y - hit.getLocation().y : from.y;
    }

    // ---- expression variables (unknown -> 0, so unbuilt features' HUD blocks stay hidden) ----
    @Override
    public double get(String name) {
        return switch (name) {
            case "plyr_yaw" -> Mth.wrapDegrees(this.player.getYRot());
            case "plyr_pitch" -> this.player.getXRot();
            case "yaw" -> Mth.wrapDegrees(this.vehicle.getYRot());
            case "pitch" -> this.vehicle.getXRot();
            case "roll" -> Mth.wrapDegrees(this.vehicle.getRollAngle());
            case "altitude" -> this.altitude;
            case "sea_alt" -> this.seaAlt;
            case "time" -> this.timeOfDay;
            // The reference throttle readout is getCurrentThrottle() — the flight-sim throttle, 0 at rest — NOT the
            // engine power (getEnginePower), which idles at 0.5 while merely ridden and made a parked vehicle read 50%.
            case "throttle" -> this.vehicle.getThrottleInput();
            case "pos_x" -> this.vehicle.getX();
            case "pos_y" -> this.vehicle.getY();
            case "pos_z" -> this.vehicle.getZ();
            case "motion_x" -> this.motion.x;
            case "motion_y" -> this.motion.y;
            case "motion_z" -> this.motion.z;
            case "speed" -> this.motion.length();
            // Fuel fraction 0..1 — faithfully 0 for a config with no tank (reference getFuelP): the low-fuel warning
            // itself gates on maxFuel>0, so an empty gauge on a fuel-less vehicle raises no false alarm.
            case "fuel" -> this.vehicle.getFuelP();
            case "low_fuel" -> this.vehicle.getMaxFuel() > 0 && this.vehicle.getFuelP() < 0.1F
                && !this.vehicle.isInfinityFuel(false) ? 1.0 : 0.0;
            // State gates the HUD configs use to show/hide blocks — now wired to the REAL vehicle state instead of 0,
            // so a UAV shows its UAV panel, a heat-seeker shows its lock cue, and gunner mode swaps the reticle
            // (reference MCH_HudItem.updateVarMap). Genuinely-unported subsystems (vtol_stat, auto_pilot, free_look,
            // radar, tv-missile) stay 0, so their blocks cleanly stay hidden until those features land.
            case "cam_zoom" -> mcheli.dependent.client.MchGunnerView.currentZoom();
            // FREE LOOK cue (reference MCH_HudItem.getFreeLook, pilot-only): lit while the pilot is free-looking, or on
            // a permanently-free-look ground vehicle (locksViewToVehicle()==false, the reference defaultFreelook case).
            case "free_look" -> this.vehicle.seatIndexOf(this.player) == 0
                && (mcheli.dependent.client.MchFreeLook.active() || !this.vehicle.locksViewToVehicle()) ? 1.0 : 0.0;
            case "gunner_mode" -> this.vehicle.isSeatGunnerMode(Math.max(0, this.vehicle.seatIndexOf(this.player)))
                ? 1.0 : 0.0;
            // Entity radar: have_radar gates the whole dial block; radar_rot spins the sweep line (interpolated).
            case "have_radar" -> this.vehicle.isRadarMounted() ? 1.0 : 0.0;
            case "radar_rot" -> this.vehicle.radarRotInterp(this.partialTick);
            case "is_uav" -> this.vehicle.hostInfo() != null && this.vehicle.hostInfo().isUAV ? 1.0 : 0.0;
            case "is_heat_wpn" -> this.vehicle.isSelectedWeaponHeat() ? 1.0 : 0.0;
            // Barrel heat 0..1 fills the overheat gauge as you fire; the weapon locks out at 1 until it cools.
            case "wpn_heat" -> this.vehicle.getSelectedHeat();
            // Selected-weapon aiming reticle: 1 = rocket/gun move-sight, 2 = lock-on missile sight (drives hud/sight.txt).
            case "sight_type" -> this.vehicle.selectedWeaponSightType();
            // Missile lock-on progress 0..1 for the sight.txt lock reticle (fills + tints red at 1). Client-side lock
            // state (reference lock resolves on the client); only meaningful for a lock-on sight (sight_type==2).
            case "lock" -> mcheli.dependent.client.MchLockTracker.progress();
            // Flares/countermeasures: have_flare lights the cue for a flare-equipped aircraft; can_flare dims it while a
            // dispense window is running (synced), matching the reference MCH_HudItem have_flare/can_flare vars.
            case "have_flare" -> this.vehicle.haveFlare() ? 1.0 : 0.0;
            case "can_flare" -> this.vehicle.haveFlare() && this.vehicle.flareDispenserIdle() ? 1.0 : 0.0;
            // Mortar range readout gate (config DisplayMortarDistance); mt_dist (the ballistic range) is unported -> 0,
            // so hud/mortar.txt shows "DIST = ----" for a mortar weapon rather than a bogus number.
            case "dsp_mt_dist" -> this.vehicle.selectedWeaponDisplaysMortarDist() ? 1.0 : 0.0;
            // Autopilot indicator: the reference lights it for a PLANE whose pilot is in gunner mode (the airframe then
            // holds a straight course while the pilot aims) — MCP_EntityPlane + getIsGunnerMode.
            case "auto_pilot" -> this.vehicle instanceof mcheli.dependent.entity.MchPlane
                && this.vehicle.isSeatGunnerMode(0) ? 1.0 : 0.0;
            // VTOL nozzle mode (reference MCH_HudItem getVtolMode): 0 = forward, 1 = transition, 2 = full hover — the
            // Harrier/F-35 HUD keys its VTOL block off this. Synced via the derived nozzle angle; 0 for non-planes.
            case "vtol_stat" -> this.vehicle instanceof mcheli.dependent.entity.MchPlane p ? p.getVtolMode() : 0.0;
            // HP bar: hp/max_hp raw values; hp_rto 0..1 drives the bar width + colour threshold (reference MCH_HudItem).
            case "hp" -> this.vehicle.getHp();
            case "max_hp" -> this.vehicle.getMaxHp();
            case "hp_rto" -> this.vehicle.getMaxHp() > 0 ? (double) this.vehicle.getHp() / this.vehicle.getMaxHp() : 0.0;
            // Control-stick indicator: the accumulated mouse-stick position (reference stick_x/stick_y), in [-1,1].
            // MCHeli flies by mouse — MchClientRotation accumulates the mouse delta into this virtual stick, clamps it,
            // and decays it to centre when the mouse is still; the HUD dot tracks the mouse and eases back on release.
            case "stick_x" -> mcheli.dependent.client.MchClientRotation.hudStickX();
            case "stick_y" -> mcheli.dependent.client.MchClientRotation.hudStickY();
            // Weapon cooldown bar: reload_time 0..1 drives the depleting bar, reloading 0/1 its colour (yellow->red).
            case "reload_time" -> this.vehicle.getSelectedReload();
            case "reloading" -> this.vehicle.getSelectedReload() > 0.0F ? 1.0 : 0.0;
            case "center_x" -> this.width / 2.0;
            case "center_y" -> this.height / 2.0;
            case "width" -> this.width;
            case "height" -> this.height;
            default -> 0.0;
        };
    }

    // ---- typed DrawString format arguments (must match the reference's types for %d/%f/%s) ----
    @Override
    public Object formatArg(String argName) {
        switch (argName.toUpperCase(Locale.ROOT)) {
            case "ALTITUDE": return (int) this.altitude;
            case "YAW": return rot360(this.vehicle.getYRot() + 180.0F);
            case "PITCH": return (double) -this.vehicle.getXRot();
            case "ROLL": return (double) Mth.wrapDegrees(this.vehicle.getRollAngle());
            case "PLYR_YAW": return rot360(this.player.getYRot() + 180.0F);
            case "PLYR_PITCH": return (double) -this.player.getXRot();
            case "POS_X": return this.vehicle.getX();
            case "POS_Y": return this.vehicle.getY();
            case "POS_Z": return this.vehicle.getZ();
            case "MOTION_X": return this.motion.x;
            case "MOTION_Y": return this.motion.y;
            case "MOTION_Z": return this.motion.z;
            case "THROTTLE": return this.vehicle.getThrottleInput() * 100.0;  // sim throttle, 0 at rest (not idle power)
            case "CAM_ZOOM": return (double) mcheli.dependent.client.MchGunnerView.currentZoom(); // %.1f -> Double
            case "MC_THOR": return this.mcHour;
            case "MC_TMIN": return this.mcMin;
            case "MC_TSEC": return this.mcSec;
            // While the pilot is in the resupply GUI the reference BLANKS the weapon readout (MCH_HudItem:473-476):
            // the name becomes "-- Reloading --" and both ammo figures become "----".
            case "WPN_NAME": return this.vehicle.isPilotReloading()
                ? "-- Reloading --" : this.vehicle.selectedWeaponName();
            case "WPN_AMMO": return this.vehicle.isPilotReloading()
                ? "----" : ammoStr(this.vehicle.getSelectedAmmo());              // current magazine (synced)
            // "RM" is REMAINING: the reserve left behind the magazine (reference getRestAllAmmoNum), NOT the config max.
            case "WPN_RM_AMMO": return this.vehicle.isPilotReloading()
                ? "----" : ammoStr(this.vehicle.getSelectedRestAmmo());
            case "RELOAD_SEC": return (double) this.vehicle.getSelectedReloadSeconds(); // cooldown countdown text
            case "RELOAD_PER": return (double) (this.vehicle.getSelectedReload() * 100.0F);
            case "HP": return this.vehicle.getHp();          // int (%d)
            case "MAX_HP": return this.vehicle.getMaxHp();    // int (%d)
            case "INVENTORY": return 0;
            case "HP_PER": return this.vehicle.getMaxHp() > 0  // Double 0..100 (%.0f) — NOT the 0..1 fraction
                ? (double) this.vehicle.getHp() / this.vehicle.getMaxHp() * 100.0 : 0.0;
            case "KEY_GUI": return "G";
            default: return "";
        }
    }

    private static double rot360(double v) {
        double r = v % 360.0;
        return r < 0.0 ? r + 360.0 : r;
    }

    /** Ammo as a string: {@code -1} (no weapon / infinite) shows as an infinity glyph. */
    private static String ammoStr(int n) {
        return n < 0 ? "∞" : Integer.toString(n);
    }

    @Override public int screenWidth() { return this.width; }
    @Override public int screenHeight() { return this.height; }
    @Override public float lineWidth() { return 1.0F; }
    @Override public double[] radarEntities() { return this.vehicle.radarEntityXZ(); }
    @Override public double[] radarEnemies() { return this.vehicle.radarEnemyXZ(); }

    // DrawCameraRot: the port has no MCH_Camera object, so an "active gunner camera" is the local viewer being in a
    // gunner view — a gunner seat, or the pilot with gunner mode engaged. The marker then tracks the viewer's own look.
    @Override public boolean hasGunnerCamera() {
        return this.vehicle.isSeatGunnerMode(Math.max(0, this.vehicle.seatIndexOf(this.player)));
    }
    @Override public double cameraPitchDeg() { return this.player.getXRot(); }
    @Override public double cameraYawDiffDeg() {
        return Mth.wrapDegrees(this.player.getYRot() - this.vehicle.getYRot());
    }
}
