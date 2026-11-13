package mcheli.agnostic.sim;

/**
 * The reference {@code MCH_EntityAircraft.modeSwitchCooldown} (:198, counted down once per tick at :1440-1442): ONE
 * counter shared by every discrete part/mode switch of a vehicle: the canopy ({@code openCanopy}/{@code closeCanopy}
 * :5603-5621), the hatch ({@code foldHatch} :5511-5526), a plane's folding or sweep wing ({@code MCP_EntityPlane}
 * :1023-1055), the landing gear ({@code fold/unfoldLandingGear} :5542-5555) and the VTOL nozzle
 * ({@code canSwitchVtol} :917-933). A manual switch is accepted only while it reads 0 and restarts it, so any switch
 * blocks every other one, and pilot boarding ({@code interactFirst}:4353), for {@link #SWITCH} ticks. The automatic
 * switches (gear auto-retract/extend, the sweep wing's auto-spread) neither read nor restart it.
 */
public final class ModeSwitchCooldown {
    /** Reference {@code setModeSwitchCooldown(20)} after a manual switch. */
    public static final int SWITCH = 20;

    private int ticks;

    /** Ticks left before another switch is accepted (reference {@code getModeSwitchCooldown}). */
    public int get() { return this.ticks; }

    /** Reference {@code setModeSwitchCooldown(n)} (the eject-seat canopy uses 40). */
    public void set(int n) { this.ticks = Math.max(0, n); }

    /** True when a switch may go ahead (reference {@code getModeSwitchCooldown() <= 0}). */
    public boolean ready() { return this.ticks <= 0; }

    /** Restart the cooldown after a switch (reference {@code setModeSwitchCooldown(20)}). */
    public void start() { this.ticks = SWITCH; }

    /** One tick (reference onUpdate:1440-1442). The host calls it exactly once per tick, on both sides. */
    public void tick() {
        if (this.ticks > 0) {
            this.ticks--;
        }
    }
}
