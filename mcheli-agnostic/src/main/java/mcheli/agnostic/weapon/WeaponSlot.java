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
 *       authoritative rate).</li>
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
        this.countWait = Math.max(1, this.info.delay);
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
