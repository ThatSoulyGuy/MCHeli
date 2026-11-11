package mcheli.agnostic.weapon;

import java.util.List;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;

/**
 * One <em>selectable</em> weapon on a vehicle: a resolved {@link MCH_WeaponInfo} config plus the list of physical
 * {@link MCH_AircraftInfo.Weapon mounts} it fires from, and the live fire-control counters. This is the agnostic port
 * of the ammo/interval/reload/round-robin bookkeeping that {@code mcheli.weapon.MCH_WeaponSet} owns in the reference —
 * pure integer state, no Minecraft.
 *
 * <p><b>Fire cadence (server-authoritative, from config):</b>
 * <ul>
 *   <li><b>interval</b> = {@code info.delay} — ticks between shots ({@code countWait}); the reference sets
 *       {@code weapon.interval = info.delay} server-side (its extra client-only padding is prediction, not the
 *       authoritative rate). Taken VERBATIM, with no lower bound: {@code Delay = 0} means one shot per tick, which
 *       is what 35 of the bundled weapons — every rotary gun — are authored as.</li>
 *   <li><b>magazine</b> = {@code info.round} — rounds per burst; when it empties, a <b>reload</b> of
 *       {@code info.reloadTime} ticks runs, after which the magazine refills.</li>
 *   <li><b>barrel round-robin</b> — a set with N mounts fires <b>one</b> mount per shot, advancing
 *       {@code (barrel + 1) % N} each shot (reference {@code MCH_WeaponSet.use} line 377), so a twin/quad rack
 *       alternates instead of firing every barrel at once.</li>
 * </ul>
 *
 * <p><b>Deferred (documented, not faked):</b> the ammo <em>reserve</em> economy ({@code MaxAmmo}/{@code SuppliedNum}/
 * ammo items / dispensers) is not modelled — the magazine simply refills after each reload, so the visible cadence
 * (rate of fire, burst size, reload pause) is fully config-driven while a demo craft never runs permanently dry. Recoil
 * and shared-group cooldown are likewise deferred behind this same object; barrel <b>heat/overheat</b> IS modelled now
 * ({@code currentHeat}/{@code cooldownSpeed}, config {@code HeatCount}/{@code MaxHeatCount}).
 */
public final class WeaponSlot {

    /** Config weapon name (the {@code AddWeapon} type token), e.g. {@code "m230"}. */
    public final String weaponName;
    /** The resolved weapon definition (stats, sound, model). */
    public final MCH_WeaponInfo info;
    /** The mounts this weapon fires from (already filtered to pilot-usable positions). Never empty. */
    public final List<MCH_AircraftInfo.Weapon> mounts;

    private final boolean infiniteMagazine;
    /** True when this weapon tracks a finite RESERVE ({@code MaxAmmo > 0}) — the reference ammo economy. When false the
     *  magazine simply refills on reload (the pre-economy behaviour) and {@link #restAllAmmo} is meaningless. */
    private final boolean economy;
    /** Heat added per shot. The reference {@code MCH_WeaponCreator} forces this to at least 2 for ANY heat-capable
     *  weapon ({@code maxHeatCount > 0 && heatCount < 2 -> 2}), so a rapid low-heat gun (a Phalanx: {@code HeatCount=1})
     *  still out-gains the ~1/tick cooldown and its bar fills. */
    private final int heatPerShot;
    private int barrel;      // round-robin index over mounts
    private int countWait;   // per-shot cooldown (ticks)
    private int reloadWait;  // reload countdown (ticks); >0 == reloading
    private int magazine;    // rounds left in the current burst
    private int restAllAmmo; // RESERVE behind the magazine; a fresh vehicle spawns DRY (reference MCH_WeaponSet:51-52)
    private int currentHeat;       // accumulated barrel heat (reference MCH_WeaponSet.currentHeat)
    private int cooldownSpeed = 1; // ramps the cooldown rate the longer the barrel goes un-fired (reference)
    /** Number of selectable fire modes (reference {@code MCH_WeaponBase.numMode} = config {@code ModeNum}). */
    private final int numMode;
    private int currentMode;       // reference MCH_WeaponBase.currentMode (0 = the weapon's default mode)

    /** Ticks the weapon is blocked after a mode switch (reference {@code MCH_WeaponSet.switchMode} cntSwitch). */
    public static final int MODE_SWITCH_DELAY = 15;

    public WeaponSlot(String weaponName, MCH_WeaponInfo info, List<MCH_AircraftInfo.Weapon> mounts) {
        if (mounts == null || mounts.isEmpty()) {
            throw new IllegalArgumentException("WeaponSlot requires at least one mount");
        }
        this.weaponName = weaponName;
        this.info = info;
        this.mounts = mounts;
        this.infiniteMagazine = magSize() <= 0;
        this.economy = info.maxAmmo > 0;
        this.magazine = this.infiniteMagazine ? Integer.MAX_VALUE : (this.economy ? 0 : magSize());
        this.heatPerShot = info.maxHeatCount > 0 && info.heatCount < 2 ? 2 : info.heatCount;
        this.numMode = modeCount(info);
    }

    // ---- fire modes (reference MCH_WeaponBase.numMode/currentMode/switchMode + the per-type getName suffixes) ----

    /**
     * The number of fire modes a weapon config has — the reference {@code MCH_WeaponCreator} sets
     * {@code weapon.numMode = info.modeNum}, and {@code MCH_WeaponMachineGun2.modifyParameters} drops it to 0 for a
     * non-explosive gun (its only alternate mode is the explosive [HE] round).
     */
    public static int modeCount(MCH_WeaponInfo info) {
        if (info == null) {
            return 0;
        }
        if ("machinegun2".equalsIgnoreCase(info.type) && info.explosion == 0) {
            return 0;
        }
        return Math.max(0, info.modeNum);
    }

    /** The config {@code FixMode}, or 0 when it is out of range — reference {@code MCH_WeaponInfo.checkData} resets a
     *  {@code FixMode >= ModeNum} to 0 (e.g. a FixMode without ModeNum has no effect). */
    public static int fixModeOf(MCH_WeaponInfo info) {
        return info != null && info.fixMode > 0 && info.fixMode < info.modeNum ? info.fixMode : 0;
    }

    /** True when the mode key does anything for this weapon (more than one mode, and no config {@code FixMode}). */
    public static boolean hasSwitchableModes(MCH_WeaponInfo info) {
        return info != null && fixModeOf(info) <= 0 && modeCount(info) > 1;
    }

    /** The mode a shot uses — the config {@code FixMode} wins over the selected one (reference {@code getCurrentMode}). */
    public static int effectiveMode(MCH_WeaponInfo info, int selectedMode) {
        int fix = fixModeOf(info);
        return fix > 0 ? fix : selectedMode;
    }

    /**
     * The HUD name suffix for a weapon in {@code mode} — the reference per-type {@code getName()} overrides:
     * MachineGun2 {@code [HE]}, Rocket {@code [HEIAP]}, ATMissile {@code [TA]} (top attack), TVMissile {@code [TV]}
     * (mode 0) / {@code [TA]} (mode 2). Every other type has no suffix.
     */
    public static String modeSuffix(MCH_WeaponInfo info, int selectedMode) {
        if (info == null || info.type == null) {
            return "";
        }
        int mode = effectiveMode(info, selectedMode);
        switch (info.type.toLowerCase(java.util.Locale.ROOT)) {
            case "machinegun2":
                return mode == 0 ? "" : " [HE]";
            case "rocket":
                return mode == 0 ? "" : " [HEIAP]";
            case "atmissile":
                return mode == 1 ? " [TA]" : "";
            case "tvmissile":
                return mode == 0 ? " [TV]" : (mode == 2 ? " [TA]" : "");
            default:
                return "";
        }
    }

    /** The selected mode, before {@code FixMode} (what the mode key cycles; synced for the HUD). */
    public int selectedMode() {
        return this.currentMode;
    }

    /** The mode the next shot uses ({@code FixMode} if configured, else the selected mode). */
    public int currentMode() {
        return effectiveMode(this.info, this.currentMode);
    }

    public int numMode() {
        return this.numMode;
    }

    /**
     * Advance to the next fire mode — the reference {@code MCH_WeaponBase.switchMode} + {@code MCH_WeaponSet.switchMode}:
     * a {@code FixMode} weapon never switches; otherwise {@code mode = (mode + 1) % numMode}. A successful switch blocks
     * the trigger for {@link #MODE_SWITCH_DELAY} ticks (the reference's 15-tick {@code countWait}).
     *
     * @return true if the mode actually changed
     */
    public boolean switchMode() {
        if (fixModeOf(this.info) > 0) {
            return false;
        }
        int before = this.currentMode;
        this.currentMode = this.numMode > 0 ? (this.currentMode + 1) % this.numMode : 0;
        boolean changed = before != this.currentMode;
        if (changed && this.countWait < MODE_SWITCH_DELAY) {
            this.countWait = MODE_SWITCH_DELAY;
        }
        return changed;
    }

    /** The seat that operates this weapon ({@code AddWeapon}'s seat id; {@code <=0} == the pilot's). */
    public int seatID() {
        return this.mounts.get(0).seatID;
    }

    /** True when the PILOT may fire this weapon (config; forced true for a seat-0 weapon). */
    public boolean canUsePilot() {
        return this.mounts.get(0).canUsePilot;
    }

    /** True when this weapon tracks a finite reserve (config {@code MaxAmmo > 0}). */
    public boolean hasEconomy() {
        return this.economy;
    }

    /** Rounds in the RESERVE (behind the magazine), or -1 when this weapon has no economy. */
    public int restAllAmmo() {
        return this.economy ? this.restAllAmmo : -1;
    }

    /** Magazine + reserve — what the HUD's remaining-ammo readout and the NBT round-trip carry. */
    public int totalAmmo() {
        return this.economy ? this.restAllAmmo + Math.max(this.magazine, 0) : -1;
    }

    public int maxAmmo() {
        return this.info.maxAmmo;
    }

    /** Set the reserve, clamped against {@code maxAmmo − the CURRENT magazine} (reference {@code MCH_WeaponSet:96-101}:
     *  the rounds already loaded count against the cap, so a full mag shrinks the reserve ceiling). */
    public void setRestAllAmmo(int n) {
        if (!this.economy) {
            return;
        }
        int cap = this.info.maxAmmo - Math.max(this.magazine, 0);
        this.restAllAmmo = Math.max(0, Math.min(n, Math.max(0, cap)));
    }

    /** One supply pulse (reference {@code MCH_WeaponSet:103-108}): the magazine is deliberately RE-ABSORBED into the
     *  reserve before adding {@code suppliedNum} — that is load-bearing, not a bug (it is how the cap stays honest). */
    public void supplyRestAllAmmo() {
        if (this.economy && this.restAllAmmo + Math.max(this.magazine, 0) < this.info.maxAmmo) {
            setRestAllAmmo(this.restAllAmmo + Math.max(this.magazine, 0) + this.info.suppliedNum);
        }
    }

    /** Pull rounds from the reserve into the magazine (reference {@code reloadMag}, {@code MCH_WeaponSet:144-155}). */
    public void reloadMag() {
        if (!this.economy || this.infiniteMagazine) {
            return;
        }
        int n = Math.min(magSize() - this.magazine, this.restAllAmmo);
        if (n > 0) {
            this.magazine += n;
            this.restAllAmmo -= n;
        }
    }

    /** A creative rider boarding tops the magazine straight up (reference {@code MCH_WeaponSet:196-199}). */
    public void onMount(boolean creative) {
        if (creative && this.economy && !this.infiniteMagazine) {
            this.magazine = magSize();
        }
    }

    /** Burst/magazine size. {@code checkData} has already coerced {@code round<=0 -> maxAmmo}; if still 0, infinite. */
    private int magSize() {
        if (this.info.round > 0) {
            return this.info.round;
        }
        return this.info.maxAmmo; // 0 => treated as infinite (see infiniteMagazine)
    }

    /** Advance the fire-control counters one tick (cooldown + reload + heat). Call once per server tick. */
    public void tick() {
        // Barrel cooldown (reference MCH_WeaponSet.update:233-241): bleed off heat, accelerating the longer the barrel
        // stays below its cap (cooldownSpeed ramps, is reset to 1 on each shot). The +30 overheat penalty parked above
        // the cap bleeds at the base rate first, so an overheated gun stays locked ~1.5s before it can fire again.
        if (this.currentHeat > 0) {
            if (this.currentHeat < this.info.maxHeatCount) {
                this.cooldownSpeed++;
            }
            this.currentHeat -= this.cooldownSpeed / 20 + 1;
            if (this.currentHeat < 0) {
                this.currentHeat = 0;
            }
        }
        if (this.countWait > 0) {
            this.countWait--;
        }
        if (this.reloadWait > 0) {
            this.reloadWait--;
            if (this.reloadWait == 0) {
                if (this.economy) {
                    reloadMag();           // pull from the reserve — a dry vehicle stays dry
                } else {
                    this.magazine = magSize(); // no economy configured: refill (pre-economy behaviour)
                }
            }
        }
    }

    /** True if the trigger can fire this tick: cooldown elapsed, not mid-reload, ammo in the magazine, and not overheated
     *  (reference {@code MCH_WeaponSet.use} gate {@code currentHeat < maxHeatCount}). */
    public boolean canFire() {
        return this.countWait == 0 && this.reloadWait == 0 && this.magazine > 0 && !overheated();
    }

    /** True while a heat-capable weapon is at/over its cap and must cool before it can fire again. */
    public boolean overheated() {
        return this.info.maxHeatCount > 0 && this.currentHeat >= this.info.maxHeatCount;
    }

    /**
     * Consume one shot and return the mount it fires from (round-robin). The caller MUST have checked {@link #canFire()}
     * first. Sets the per-shot cooldown to {@code info.delay}, decrements the magazine, and starts a reload when the
     * burst empties. Never returns {@code null}.
     */
    public MCH_AircraftInfo.Weapon fireOneShot() {
        return fireOneShot(false);
    }

    /**
     * Consume one shot. {@code infinityAmmo} (a creative operator / the server's infinite-ammo setting) does NOT skip
     * the decrement — the reference refills the RESERVE when the magazine empties (MCH_WeaponSet:384-391), so the
     * reload cadence a config authored still plays out.
     */
    public MCH_AircraftInfo.Weapon fireOneShot(boolean infinityAmmo) {
        MCH_AircraftInfo.Weapon mount = this.mounts.get(this.barrel);
        this.barrel = (this.barrel + 1) % this.mounts.size();
        // Reference MCH_WeaponSet.use:340 — `this.countWait = crtWpn.interval;` with NO floor. A Delay=0 weapon
        // therefore leaves countWait at 0 and can fire again on the VERY NEXT tick; the old Math.max(1, delay) here
        // halved the rate of every one of them. That is 35 bundled weapons, and they are exactly the rotary guns
        // (GAU-8, GAU-12U, M61A1, the M134s, the GSh cannons, mg3_l/mg3_r, xm301).
        //
        // It also broke OVERHEATING outright, because heat accrues per SHOT while the bleed is per TICK: an m61a1 is
        // HeatCount=1 -> heatPerShot 2, MaxHeatCount 150. Reference: +2 and -1 every tick = +1/tick, so it caps in
        // 150 ticks. Halved: +2 per 2 ticks against -2 of bleed = net ZERO — the Vulcan could never overheat at all.
        //
        // Firing twice in one tick is not a risk: the trigger is serviced once per tick per seat in
        // AbstractMchVehicle.tick(), so countWait == 0 means "eligible next tick", not "free-running".
        this.countWait = this.info.delay;
        // Barrel heat (reference MCH_WeaponSet.use:358-363): each shot adds heatPerShot and resets the cooldown ramp;
        // the shot that reaches the cap parks +30 above it, forcing an extra ~1.5s of cooldown (the overheat penalty).
        if (this.info.maxHeatCount > 0) {
            this.cooldownSpeed = 1;
            this.currentHeat += this.heatPerShot;
            if (this.currentHeat >= this.info.maxHeatCount) {
                this.currentHeat += 30;
            }
        }
        if (!this.infiniteMagazine) {
            this.magazine--;
            if (this.magazine <= 0) {
                if (infinityAmmo && this.economy && this.restAllAmmo < magSize()) {
                    this.restAllAmmo = magSize(); // creative tops the RESERVE, not the magazine
                }
                this.reloadWait = Math.max(1, this.info.reloadTime);
            }
        }
        return mount;
    }

    /** Reset the barrel round-robin (reference {@code onSwitchWeapon} zeroes {@code currentWeaponIndex}). */
    public void onDeselect() {
        this.barrel = 0;
    }

    public int magazine() {
        return this.infiniteMagazine ? -1 : this.magazine;
    }

    public boolean reloading() {
        return this.reloadWait > 0;
    }

    /** True if this weapon overheats at all (config {@code MaxHeatCount > 0}) — gates the HUD heat gauge. */
    public boolean hasHeat() {
        return this.info.maxHeatCount > 0;
    }

    /** Barrel heat 0..1 for the HUD {@code wpn_heat} gauge (reference {@code currentHeat / maxHeatCount}, capped). */
    public float heatFraction() {
        if (this.info.maxHeatCount <= 0) {
            return 0.0F;
        }
        float f = (float) this.currentHeat / this.info.maxHeatCount;
        return f > 1.0F ? 1.0F : f;
    }

    /**
     * Fraction 0..1 of the fire interval still to wait before the next shot — the reference {@code reload_time} that
     * drives the HUD cooldown bar. Faithful to {@code MCH_HudItem.updateVarMap_Weapon}: a slow weapon whose per-shot
     * {@code delay} exceeds its {@code reloadTime} shows the per-shot cooldown ({@code countWait/delay}); a fast weapon
     * with a longer magazine reload shows the reload ({@code reloadWait/reloadTime}). 0 == ready to fire.
     */
    public float reloadFraction() {
        float t;
        if (this.info.delay > this.info.reloadTime) {
            t = (float) this.countWait / (this.info.delay > 0 ? this.info.delay : 1);
        } else {
            t = (float) this.reloadWait / (this.info.reloadTime > 0 ? this.info.reloadTime : 1);
        }
        t = Math.abs(t);
        return t > 1.0F ? 1.0F : t;
    }

    /** The larger of the per-shot cooldown / reload interval in ticks — the denominator behind {@link #reloadFraction},
     *  so the client can turn the synced fraction back into a "%.2fsec" countdown. */
    public int reloadIntervalTicks() {
        return Math.max(this.info.delay, this.info.reloadTime);
    }
}
