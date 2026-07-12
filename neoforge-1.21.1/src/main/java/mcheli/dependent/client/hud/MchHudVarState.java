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
 * arguments from the entity + player. Variables/args the port doesn't have yet (HP, ammo, fuel, radar, flare, gunner,
 * …) fall through to 0 / empty, which — because the HUD configs guard those blocks with {@code If}-conditions — cleanly
 * hides them until their feature lands.
 */
public final class MchHudVarState implements HudState {

    private final AbstractMchVehicle vehicle;
    private final Player player;
    private final int width;
    private final int height;
    private final Vec3 motion;
    private final double altitude;
    private final double seaAlt;
    private final long timeOfDay;
    private final int mcHour;
    private final int mcMin;
    private final int mcSec;

    public MchHudVarState(AbstractMchVehicle vehicle, Player player, int width, int height) {
        this.vehicle = vehicle;
        this.player = player;
        this.width = width;
        this.height = height;
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
            case "throttle" -> this.vehicle.getEnginePower();
            case "pos_x" -> this.vehicle.getX();
            case "pos_y" -> this.vehicle.getY();
            case "pos_z" -> this.vehicle.getZ();
            case "motion_x" -> this.motion.x;
            case "motion_y" -> this.motion.y;
            case "motion_z" -> this.motion.z;
            case "speed" -> this.motion.length();
            case "fuel" -> 1.0;        // stub: tank full until the fuel system lands (keeps low_fuel = 0)
            case "cam_zoom" -> 1.0;
            // Control-stick indicator: the accumulated mouse-stick position (reference stick_x/stick_y), in [-1,1].
            // MCHeli flies by mouse — MchClientRotation accumulates the mouse delta into this virtual stick, clamps it,
            // and decays it to centre when the mouse is still; the HUD dot tracks the mouse and eases back on release.
            case "stick_x" -> mcheli.dependent.client.MchClientRotation.hudStickX();
            case "stick_y" -> mcheli.dependent.client.MchClientRotation.hudStickY();
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
            case "THROTTLE": return this.vehicle.getEnginePower() * 100.0;
            case "CAM_ZOOM": return 1.0;   // %.1f in every config -> must be a Double, not an int
            case "MC_THOR": return this.mcHour;
            case "MC_TMIN": return this.mcMin;
            case "MC_TSEC": return this.mcSec;
            case "WPN_NAME": return this.vehicle.selectedWeaponName();
            case "WPN_AMMO": return ammoStr(this.vehicle.getSelectedAmmo());     // current magazine (synced)
            case "WPN_RM_AMMO": return ammoStr(this.vehicle.getSelectedMaxAmmo()); // config reserve (full economy = #37)
            case "HP": case "MAX_HP": case "INVENTORY": return 0;
            case "HP_PER": return 0.0;
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
}
