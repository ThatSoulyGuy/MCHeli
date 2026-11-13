package mcheli.dependent.client.hud;

import java.util.Date;
import mcheli.MCHeli;
import mcheli.agnostic.hud.HudState;
import mcheli.agnostic.hud.MCH_HudItemStringArgs;
import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The live {@link HudState} for the ridden vehicle: every reference expression variable ({@link HudState#VARIABLES},
 * from {@code MCH_HudItem.updateVarMap}) and every {@code DrawString} format argument ({@code MCH_HudItemString}), read
 * from the entity and the local player, boxed with the reference's Java types. Built once per frame by
 * {@link MchHudOverlay}.
 *
 * <p>Every weapon value comes from the VIEWER's seat (reference {@code ac.getCurrentWeapon(player)}), so a gunner reads
 * his own weapon, reload, heat, lock and mortar distance. Values that need a subsystem the port does not have yet come
 * from the "seam" methods at the bottom of the class. Each one returns the reference's own value for "feature absent",
 * so the HUD configs' {@code If} guards keep those blocks hidden: the UAV station ({@code uav_fs}, {@code UAV_DIST})
 * and the cargo inventory ({@code inventory}). The TV-missile readouts and
 * {@code test_mode} likewise report the reference's values for "no TV missile" (the missile camera is not ported) and
 * "TestMode off".
 */
public final class MchHudVarState implements HudState {

    /** Reference {@code MCH_HudItem.Altitude}/{@code altitudeUpdateCount}: the ground scan runs once every 31 frames and
     *  the value holds in between (also when the scan finds no block). Static, as in the reference. */
    private static int altitude;
    private static int altitudeUpdateCount;
    /** Reference {@code MCH_HudItem.countFuelWarn}: the low-fuel warning blinks, lit for 160 of every 280 frames. */
    private static int countFuelWarn;
    private static String modVersion;

    private final AbstractMchVehicle vehicle;
    private final Player player;
    private final int seat;
    private final int width;
    private final int height;
    private final float partialTick;
    private final Vec3 motion;
    private final double seaAlt;
    private final long timeOfDay;
    private final int mcHour;
    private final int mcMin;
    private final int mcSec;
    private final boolean lowFuel;
    // The viewer's weapon, read once per frame (reference updateVarMap_Weapon fills its map once per frame too): the
    // HUD asks for these in nearly every expression, and each read went back to the vehicle's synced data.
    private final boolean wpn;
    private final boolean reloadGui;
    private final int ammo;
    private final float reload;
    private final float heat;
    private final boolean inPreparation;
    private final boolean heatWeapon;
    private final int sightType;
    private final boolean displaysMortarDist;

    public MchHudVarState(AbstractMchVehicle vehicle, Player player, int width, int height, float partialTick) {
        this.vehicle = vehicle;
        this.player = player;
        this.seat = Math.max(0, vehicle.seatIndexOf(player));
        this.width = width;
        this.height = height;
        this.partialTick = partialTick;
        this.motion = vehicle.getDeltaMovement();
        updateAltitude(vehicle);
        this.seaAlt = Math.max(0.0, vehicle.getY() - vehicle.level().getSeaLevel()); // reference getSeaAltitude
        long dayTime = vehicle.level().getDayTime();
        this.timeOfDay = ((dayTime % 24000L) + 24000L) % 24000L;
        long mc = (dayTime + 6000L) % 24000L; // reference: clock reads 06:00 at dawn
        this.mcHour = (int) (mc / 1000L);
        this.mcMin = (int) (mc % 1000L * 36L / 10L / 60L);
        this.mcSec = (int) (mc % 1000L * 36L / 10L % 60L);
        this.lowFuel = isLowFuel(vehicle);
        this.wpn = vehicle.selectedWeaponIndex(this.seat) >= 0;
        this.reloadGui = vehicle.isPilotReloading();
        this.ammo = this.wpn ? vehicle.selectedAmmo(this.seat) : -1;
        this.reload = this.wpn ? vehicle.selectedReload(this.seat) : 0.0F;
        this.heat = this.wpn ? vehicle.selectedHeat(this.seat) : 0.0F;
        this.inPreparation = this.wpn && vehicle.selectedInPreparation(this.seat);
        this.heatWeapon = this.wpn && vehicle.isSelectedWeaponHeat(this.seat);
        this.sightType = this.wpn ? vehicle.selectedWeaponSightType(this.seat) : 0;
        this.displaysMortarDist = this.wpn && vehicle.selectedWeaponDisplaysMortarDist(this.seat);
    }

    /**
     * Reference {@code MCH_HudItem.updateAltitude}: the number of blocks between the vehicle and the first non-air block
     * straight below it, counting water, leaves and plants. Parked on the ground it reads 1. Above the build limit the
     * height over the limit is added. The 1.7.10 world bounds 0..256 map to this level's build range. The column is
     * the block the vehicle is in: the reference's {@code (int)} cast picked the neighbouring column at negative
     * coordinates.
     */
    private static void updateAltitude(AbstractMchVehicle v) {
        if (altitudeUpdateCount > 0) {
            altitudeUpdateCount--;
            return;
        }
        Level level = v.level();
        int top = level.getMaxBuildHeight();
        int bottom = level.getMinBuildHeight();
        int heliY = Math.min(Mth.floor(v.getY()), top);
        int x = Mth.floor(v.getX());
        int z = Mth.floor(v.getZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < top - bottom && heliY - i > bottom; i++) {
            if (!level.getBlockState(pos.set(x, heliY - i, z)).isAir()) {
                altitude = v.getY() > top ? (int) (i + (v.getY() - top)) : i;
                break;
            }
        }
        altitudeUpdateCount = 30;
    }

    /** Reference {@code MCH_HudItem.isLowFuel}: advanced once per frame, so the warning blinks. */
    private static boolean isLowFuel(AbstractMchVehicle v) {
        if (countFuelWarn <= 0) {
            countFuelWarn = 280;
        }
        countFuelWarn--;
        return countFuelWarn < 160 && v.getMaxFuel() > 0 && v.getFuelP() < 0.1F && !v.isInfinityFuel(false);
    }

    // ---- expression variables (reference MCH_HudItem.updateVarMap / updateVarMap_Weapon) ----
    @Override
    public double get(String name) {
        boolean wpn = this.wpn;
        return switch (name) {
            case "center_x" -> this.width / 2.0;
            case "center_y" -> this.height / 2.0;
            case "width" -> this.width;
            case "height" -> this.height;
            case "time" -> this.timeOfDay;
            case "test_mode" -> 0.0; // MCH_Config.TestMode is unported (no port option); the reference default is off
            case "plyr_yaw" -> Mth.wrapDegrees(this.player.getYRot());
            case "plyr_pitch" -> this.player.getXRot();
            case "yaw" -> Mth.wrapDegrees(this.vehicle.getYRot());
            case "pitch" -> this.vehicle.getXRot();
            case "roll" -> Mth.wrapDegrees(this.vehicle.getRollAngle());
            case "altitude" -> altitude;
            case "sea_alt" -> this.seaAlt;
            // Entity radar: have_radar gates the whole dial block; radar_rot spins the sweep line (interpolated).
            case "have_radar" -> this.vehicle.isRadarMounted() ? 1.0 : 0.0;
            case "radar_rot" -> this.vehicle.animation().radarRotInterp(this.partialTick);
            // HP bar: hp/max_hp raw values; hp_rto 0..1 drives the bar width + colour threshold.
            case "hp" -> this.vehicle.getHp();
            case "max_hp" -> this.vehicle.getMaxHp();
            case "hp_rto" -> this.vehicle.getMaxHp() > 0 ? (double) this.vehicle.getHp() / this.vehicle.getMaxHp() : 0.0;
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
            case "low_fuel" -> this.lowFuel ? 1.0 : 0.0;
            // Control-stick indicator: the accumulated mouse-stick position (reference stick_x/stick_y), in [-1,1].
            // MCHeli flies by mouse — MchClientRotation accumulates the mouse delta into this virtual stick, clamps it,
            // and decays it to centre when the mouse is still; the HUD dot tracks the mouse and eases back on release.
            case "stick_x" -> mcheli.dependent.client.MchClientRotation.hudStickX();
            case "stick_y" -> mcheli.dependent.client.MchClientRotation.hudStickY();
            // Weapon block (reference updateVarMap_Weapon): all 0 when the viewer has no current weapon (ws == null).
            // reload_time 0..1 drives the depleting cooldown bar, reloading 0/1 its colour (yellow->red); reloading is
            // the reference isInPreparation, so a weapon-switch lockout lights it too.
            case "reloading" -> this.inPreparation ? 1.0 : 0.0;
            case "reload_time" -> this.reload;
            // Barrel heat 0..1 fills the overheat gauge as you fire; the weapon locks out at 1 until it cools.
            case "wpn_heat" -> this.heat;
            case "is_heat_wpn" -> this.heatWeapon ? 1.0 : 0.0;
            // Selected-weapon aiming reticle: 1 = rocket/gun move-sight, 2 = lock-on missile sight (drives hud/sight.txt).
            case "sight_type" -> this.sightType;
            // Missile lock-on progress 0..1 for the sight.txt lock reticle (fills + tints red at 1). Client-side lock
            // state (reference lock resolves on the client); only meaningful for a lock-on sight (sight_type==2).
            case "lock" -> wpn ? mcheli.dependent.client.MchLockTracker.progress() : 0.0;
            case "dsp_mt_dist" -> this.displaysMortarDist ? 1.0 : 0.0;
            case "mt_dist" -> mortarDistance();
            // VTOL nozzle mode (reference getVtolStat): 0 = forward, 1 = transition, 2 = full hover — the Harrier/F-35
            // HUD keys its VTOL block off this. Synced via the derived nozzle angle; 0 for non-planes.
            case "vtol_stat" -> this.vehicle instanceof mcheli.dependent.entity.MchPlane p ? p.getVtolMode() : 0.0;
            // FREE LOOK cue (reference getFreeLook, pilot-only): lit while the pilot is free-looking, or on a
            // permanently-free-look ground vehicle (locksViewToVehicle()==false, the reference defaultFreelook case).
            case "free_look" -> this.seat == 0
                && (mcheli.dependent.client.MchFreeLook.active() || !this.vehicle.locksViewToVehicle()) ? 1.0 : 0.0;
            // Reference getIsGunnerMode(player): the pilot's toggle, a dedicated gunner seat (always), or a switchable
            // gunner seat's synced toggle (on from boarding, flipped by the gunner-mode key).
            case "gunner_mode" -> this.vehicle.isSeatGunnerMode(this.seat) ? 1.0 : 0.0;
            case "cam_mode" -> cameraMode();
            case "cam_zoom" -> mcheli.dependent.client.MchGunnerView.currentZoom();
            // Autopilot indicator (reference getAutoPilot): a PLANE whose PILOT is in gunner mode (the airframe then
            // holds a straight course while the pilot aims) — MCP_EntityPlane && isPilot(player) && getIsGunnerMode.
            case "auto_pilot" -> this.vehicle instanceof mcheli.dependent.entity.MchPlane && this.seat == 0
                && this.vehicle.isSeatGunnerMode(0) ? 1.0 : 0.0;
            // Flares/countermeasures: have_flare lights the cue for a flare-equipped aircraft; can_flare dims it while a
            // dispense window is running (synced), matching the reference have_flare/can_flare vars.
            case "have_flare" -> this.vehicle.haveFlare() ? 1.0 : 0.0;
            case "can_flare" -> this.vehicle.haveFlare() && this.vehicle.flareDispenserIdle() ? 1.0 : 0.0;
            case "inventory" -> inventorySize();
            case "hovering" -> hoveringMode();
            case "is_uav" -> this.vehicle.hostInfo() != null && this.vehicle.hostInfo().isUAV ? 1.0 : 0.0;
            case "uav_fs" -> uavSignal();
            default -> 0.0; // "color" (MCH_Hud supplies it) and names outside HudState.VARIABLES are never asked
        };
    }

    // ---- typed DrawString format arguments (reference MCH_HudItemString.execute: same boxed types) ----
    @Override
    public Object formatArg(String argName) {
        MCH_HudItemStringArgs arg = MCH_HudItemStringArgs.toArgs(argName);
        boolean wpn = this.wpn;
        boolean reloadGui = this.reloadGui; // pilot in the resupply GUI: the readout blanks
        int ammo = this.ammo;               // -1 == the weapon has no magazine (getAmmoNumMax <= 0)
        return switch (arg) {
            case NAME -> this.vehicle.displayName();
            case ALTITUDE -> altitude;                                        // Integer (%d)
            case DATE -> new Date();
            case MC_THOR -> this.mcHour;
            case MC_TMIN -> this.mcMin;
            case MC_TSEC -> this.mcSec;
            case MAX_HP -> this.vehicle.getMaxHp();                           // Integer (%d)
            case HP -> this.vehicle.getHp();                                  // Integer (%d)
            case HP_PER -> this.vehicle.getMaxHp() > 0                        // Double 0..100 (%.0f) — not the fraction
                ? (double) this.vehicle.getHp() / this.vehicle.getMaxHp() * 100.0 : 0.0;
            case POS_X -> this.vehicle.getX();
            case POS_Y -> this.vehicle.getY();
            case POS_Z -> this.vehicle.getZ();
            case MOTION_X -> this.motion.x;
            case MOTION_Y -> this.motion.y;
            case MOTION_Z -> this.motion.z;
            case INVENTORY -> inventorySize();
            // The weapon readouts skip the whole string without a current weapon. While the pilot is in the resupply
            // GUI the name becomes "-- Reloading --" and the ammo figures "----" (reference MCH_HudItem.updateWeapon).
            case WPN_NAME -> !wpn ? SKIP_STRING : reloadGui ? "-- Reloading --" : this.vehicle.selectedWeaponName(this.seat);
            case WPN_AMMO -> !wpn || ammo < 0 ? SKIP_STRING : reloadGui ? "----" : String.format("%4d", ammo);
            // "RM" is REMAINING: the reserve left behind the magazine (reference getRestAllAmmoNum), NOT the config
            // max. A weapon without a finite reserve has none to count, shown as an infinity glyph at the same width.
            case WPN_RM_AMMO -> {
                if (!wpn || ammo < 0) {
                    yield SKIP_STRING;
                }
                int rest = this.vehicle.selectedRestAmmo(this.seat);
                yield reloadGui ? "----" : rest < 0 ? String.format("%4s", "∞") : String.format("%4d", rest);
            }
            // Reference ReloadPer = (1 - cooldown fraction) * 100: 100 = ready to fire.
            case RELOAD_PER -> !wpn ? SKIP_STRING : (1.0F - this.reload) * 100.0F;
            case RELOAD_SEC -> !wpn ? SKIP_STRING : this.vehicle.selectedReloadSeconds(this.seat);
            case MORTAR_DIST -> !wpn ? SKIP_STRING : (float) mortarDistance();
            case MC_VER -> SharedConstants.VERSION_STRING;
            case MOD_VER -> modVersion();
            case MOD_NAME -> "MC Helicopter MOD";
            case YAW -> rot360(this.vehicle.getYRot() + 180.0F);              // Double (reference getRotate360)
            case PITCH -> -this.vehicle.getXRot();                            // Float
            case ROLL -> Mth.wrapDegrees(this.vehicle.getRollAngle());        // Float
            case PLYR_YAW -> rot360(this.player.getYRot() + 180.0F);          // Double
            case PLYR_PITCH -> -this.player.getXRot();                        // Float
            // TV-missile readouts: the TV missile steers toward the operator's aim but the missile camera (the view the
            // reference reads these for) is not ported, so they report the reference's getTVMissile() == null -> 0.
            case TVM_POS_X, TVM_POS_Y, TVM_POS_Z, TVM_DIFF -> 0.0;
            case CAM_ZOOM -> mcheli.dependent.client.MchGunnerView.currentZoom(); // Float (%.1f)
            case UAV_DIST -> uavStationDistance() < 0.0 ? 0.0 : (double) (float) uavStationDistance();
            case KEY_GUI -> mcheli.dependent.client.MchKeybinds.GUI.getTranslatedKeyMessage().getString();
            case THROTTLE -> this.vehicle.getThrottleInput() * 100.0;         // sim throttle, 0 at rest (not idle power)
            case NONE -> null;
        };
    }

    private static double rot360(double v) {
        double r = v % 360.0;
        return r < 0.0 ? r + 360.0 : r;
    }

    private static String modVersion() {
        if (modVersion == null) {
            modVersion = net.neoforged.fml.ModList.get().getModContainerById(MCHeli.MODID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("");
        }
        return modVersion;
    }

    @Override public int screenWidth() { return this.width; }
    @Override public int screenHeight() { return this.height; }
    @Override public float lineWidth() { return 1.0F; }
    @Override public double[] radarEntities() { return this.vehicle.animation().radarEntityXZ(); }
    @Override public double[] radarEnemies() { return this.vehicle.animation().radarEnemyXZ(); }

    // DrawCameraRot: the reference MCH_Camera took the riding player's look every tick (updateCameraRotate), for the
    // pilot as for a gunner, and every aircraft had one, so the box is always drawn and its marker is the viewer's look.
    @Override public double cameraPitchDeg() { return this.player.getXRot(); }
    @Override public double cameraYawDiffDeg() {
        return Mth.wrapDegrees(this.player.getYRot() - this.vehicle.getYRot());
    }

    // ---- seams: each returns the reference's "feature absent" value until the named subsystem exists ----

    /** Camera mode (reference {@code ac.getCameraMode(player)} = {@code MCH_Camera.getMode(seat)}: 0 normal,
     *  1 night vision, 2 thermal) of the viewer's own seat, synced on the vehicle; 0 for a seat without a camera. */
    private double cameraMode() {
        return this.vehicle.cameraMode(this.seat);
    }

    /** Helicopter hover mode (reference {@code ac instanceof MCH_EntityHeli && ac.isHoveringMode()}): the requested,
     *  synced mode, not {@code isHovering()} (which gunner mode also sets). */
    private double hoveringMode() {
        return this.vehicle instanceof mcheli.dependent.entity.MchHelicopter h && h.isHoveringMode() ? 1.0 : 0.0;
    }

    /** Horizontal distance from this UAV to its {@code MCH_EntityUavStation}, or -1 without one (reference
     *  {@code updateUAV}/{@code getUAV_Fs}, which read {@code ac.getUavStation()}). The port has no UAV station entity,
     *  so there is never one. */
    private static double uavStationDistance() {
        return -1.0;
    }

    /** Reference {@code getUAV_Fs}: UAV signal strength {@code 1 - min(dist, 120) / 120}, 0 without a station. */
    private double uavSignal() {
        double d = uavStationDistance();
        if (d < 0.0 || this.vehicle.hostInfo() == null || !this.vehicle.hostInfo().isUAV) {
            return 0.0;
        }
        return 1.0F - (float) Math.min(d, 120.0) / 120.0F;
    }

    /** Cargo inventory size (reference {@code ac.getSizeInventory()} = the config {@code InventorySize}). The port's
     *  riding menu holds only the fuel slots, so there is no cargo inventory to report. */
    private static int inventorySize() {
        return 0;
    }

    /** Mortar landing distance of the viewer's weapon (reference {@code ac.getLandInDistance(player)}: a 50-step
     *  ballistic ray march along the weapon's aim, recomputed every 5 ticks), or -1 without a landing point; only a
     *  {@code DisplayMortarDistance} weapon's HUD reads it, so only that one pays for the march. */
    private double mortarDistance() {
        if (!this.displaysMortarDist) {
            return -1.0;
        }
        return this.vehicle.mortarLandInDistance(this.player);
    }
}
