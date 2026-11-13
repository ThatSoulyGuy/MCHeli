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
 * <p>Also modelled: the ammo <b>reserve</b> economy ({@code MaxAmmo}/{@code SuppliedNum}: {@link #restAllAmmo},
 * {@link #supplyRestAllAmmo}, {@link #reloadMag}); barrel <b>heat/overheat</b> ({@code currentHeat}/{@code cooldownSpeed},
 * config {@code HeatCount}/{@code MaxHeatCount}); the weapon <b>mode</b> ({@link #switchMode}, {@link MCH_WeaponMode});
 * and the <b>switch lockout</b> ({@link #onSwitchedTo}: a negative {@code countWait} that ticks back up to 0, reference
 * {@code MCH_WeaponSet.onSwitchWeapon}); and the shared-barrel cooldown of a config {@code Group}
 * ({@link #waitAndReloadByOther}).
 *
 * <p><b>Which reference timer is "the" timer.</b> The reference keeps a copy of every weapon set on both sides, and the
 * CLIENT decides when to shoot: its copy gates {@code use()}, and the server's copy runs shorter timers only so that it
 * is ready when the client's shot packet arrives (switch lockout 15 ticks against the client's 25; magazine reload
 * {@code reloadTime - 20} against the client's full {@code reloadTime}). What a player sees is the client's timing, so
 * that is what this server-authoritative port uses for the lockout and the reload.
 */
public final class WeaponSlot {

    /** Config weapon name (the {@code AddWeapon} type token), e.g. {@code "m230"}. */
    public final String weaponName;
    /** The resolved weapon definition (stats, sound, model). */
    public final MCH_WeaponInfo info;
    /** The mounts this weapon fires from (every mount of the config group, whichever seat operates it). Never empty. */
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
    private int currentMode;       // reference MCH_WeaponBase.currentMode (all mounts of a set switch together)
    private MCH_WeaponInfo[] modeInfo; // per-mode shot variant of info (explosion/piercing), built lazily

    /** Reference {@code MCH_WeaponSet.onSwitchWeapon}: {@code cntSwitch = 15}, plus 10 on the client, whose copy is the
     *  one that gates fire (see the class javadoc). */
    static final int SWITCH_LOCKOUT_TICKS = 25;
    /** Reference {@code MCH_WeaponSet.switchMode}: {@code cntSwitch = 15} on both sides. */
    static final int MODE_LOCKOUT_TICKS = 15;

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

    /** A creative / infinite-ammo rider boarding tops the magazine straight up (reference {@code MCH_WeaponSet:196-199}). */
    public void onMount(boolean creative) {
        if (creative && this.economy && !this.infiniteMagazine) {
            this.magazine = magSize();
        }
    }

    /** Burst/magazine size. The loader's {@code checkData} has already coerced {@code round<=0 -> maxAmmo}; the fallback
     *  keeps a definition built without the loader (unit tests) the same. 0 == an endless magazine. */
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
        if (this.countWait < 0) {
            this.countWait++; // a switch / mode lockout counts back up to 0 (reference MCH_WeaponSet.update:248-250)
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

    /** True if the trigger can fire this tick: cooldown and switch lockout over ({@code countWait == 0}, reference
     *  {@code canUse}), not mid-reload, ammo in the magazine, and not overheated (reference {@code MCH_WeaponSet.use}
     *  gate {@code currentHeat < maxHeatCount}). */
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
                    // Creative tops the RESERVE, not the magazine, through the maxAmmo clamp as the reference's
                    // setRestAllAmmoNum(getAmmoNumMax()) does (MCH_WeaponSet:384-386 -> :96-99).
                    setRestAllAmmo(magSize());
                }
                // The full reloadTime, not the reference server's reloadTime - 20 (see the class javadoc).
                this.reloadWait = Math.max(1, this.info.reloadTime);
            }
        }
        return mount;
    }

    /** Reset the barrel round-robin (reference {@code onSwitchWeapon} zeroes {@code currentWeaponIndex}). */
    public void onDeselect() {
        this.barrel = 0;
    }

    /**
     * The weapon a seat switches AWAY from reloads a magazine that is not full: reference
     * {@code MCH_EntityAircraft.switchWeapon} first calls {@code getCurrentWeapon(entity).reload()}, and
     * {@code MCH_WeaponSet.reload} restarts {@code countReloadWait} whenever {@code ammo < round} (the client copy also
     * empties its magazine, so the weapon cannot fire until the reload ends). Switching to the same weapon is therefore
     * the reference's manual reload.
     */
    public void reloadOnSwitchAway() {
        if (!this.infiniteMagazine && this.magazine < magSize() && this.info.reloadTime > 0) {
            this.reloadWait = this.info.reloadTime;
        }
    }

    /**
     * The weapon a seat switches TO, reference {@code MCH_WeaponSet.onSwitchWeapon}: a switch lockout (a negative
     * {@code countWait}, see {@link #lockout}), the barrel round-robin restarts, and a creative / infinite-ammo operator
     * gets a full magazine.
     */
    public void onSwitchedTo(boolean infinityAmmo) {
        lockout(SWITCH_LOCKOUT_TICKS);
        this.barrel = 0;
        if (infinityAmmo && !this.infiniteMagazine) {
            this.magazine = magSize();
        }
    }

    /** The reference lockout rule shared by {@code onSwitchWeapon} and {@code switchMode}: unless a longer lockout
     *  already runs, a cooldown longer than {@code ticks} becomes an equally long lockout, anything shorter becomes
     *  {@code ticks}. */
    private void lockout(int ticks) {
        if (this.countWait >= -ticks) {
            this.countWait = this.countWait > ticks ? -this.countWait : -ticks;
        }
    }

    /** How many modes this weapon cycles through ({@link MCH_WeaponMode#numMode}). */
    public int numMode() {
        return MCH_WeaponMode.numMode(this.info);
    }

    /** The mode a shot uses (a config {@code FixMode} wins over the switched one). */
    public int mode() {
        return MCH_WeaponMode.effectiveMode(this.info, this.currentMode);
    }

    /** Restore a mode (the client mirror of the synced mode); an out-of-range value falls back to 0. */
    public void setMode(int mode) {
        this.currentMode = mode >= 0 && mode < Math.max(1, numMode()) ? mode : 0;
    }

    /**
     * Cycle to the next mode, reference {@code MCH_WeaponSet.switchMode}. Returns whether the mode changed; a change
     * starts the {@value #MODE_LOCKOUT_TICKS}-tick lockout. A fixed-mode or single-mode weapon never changes.
     */
    public boolean switchMode() {
        int before = mode();
        this.currentMode = MCH_WeaponMode.nextMode(this.info, this.currentMode);
        boolean changed = mode() != before;
        if (changed) {
            lockout(MODE_LOCKOUT_TICKS);
        }
        return changed;
    }

    /** The definition a round fired in the current mode carries: {@link #info} itself, or a shallow variant with the
     *  mode's explosion and piercing ({@link MCH_WeaponInfo#shotVariant}). */
    public MCH_WeaponInfo shotInfo() {
        int m = mode();
        if (this.modeInfo == null || this.modeInfo.length <= m) {
            this.modeInfo = java.util.Arrays.copyOf(this.modeInfo == null ? new MCH_WeaponInfo[0] : this.modeInfo, m + 1);
        }
        MCH_WeaponInfo v = this.modeInfo[m];
        if (v == null) {
            v = this.info.shotVariant(MCH_WeaponMode.explosion(this.info, m), MCH_WeaponMode.piercing(this.info, m));
            this.modeInfo[m] = v;
        }
        return v;
    }

    /** The HUD name: the config display name plus the mode suffix (reference {@code MCH_WeaponSet.getName}). */
    public String displayName() {
        String base = this.info.displayName != null && !this.info.displayName.isEmpty()
            ? this.info.displayName : this.weaponName;
        return base + MCH_WeaponMode.suffix(this.info.type, mode());
    }

    public int magazine() {
        return this.infiniteMagazine ? -1 : this.magazine;
    }

    public boolean reloading() {
        return this.reloadWait > 0;
    }

    /**
     * Another weapon of this weapon's config {@code Group} (one barrel, several rounds: a tank's APFSDS / HE / HEAT)
     * just fired — reference {@code MCH_WeaponSet.waitAndReloadByOther}: wait out this weapon's own delay, drop any reload
     * in progress, and start a fresh one when the shot emptied the shared magazine ({@code reload}).
     */
    public void waitAndReloadByOther(boolean reload) {
        this.countWait = this.info.delay;
        this.reloadWait = 0;
        if (reload && !this.infiniteMagazine && this.info.reloadTime > 0) {
            this.reloadWait = this.info.reloadTime; // the full reloadTime (see the class javadoc)
        }
    }

    /** Reference {@code isInPreparation}: reloading, or inside a switch / mode lockout (the HUD {@code reloading}). */
    public boolean inPreparation() {
        return this.countWait < 0 || this.reloadWait > 0;
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
