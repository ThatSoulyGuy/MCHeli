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
 * (rate of fire, burst size, reload pause) is fully config-driven while a demo craft never runs permanently dry. Heat/
 * overheat, recoil, and shared-group cooldown are likewise deferred behind this same object.
 */
public final class WeaponSlot {

    /** Config weapon name (the {@code AddWeapon} type token), e.g. {@code "m230"}. */
    public final String weaponName;
    /** The resolved weapon definition (stats, sound, model). */
    public final MCH_WeaponInfo info;
    /** The mounts this weapon fires from (already filtered to pilot-usable positions). Never empty. */
    public final List<MCH_AircraftInfo.Weapon> mounts;

    private final boolean infiniteMagazine;
    private int barrel;      // round-robin index over mounts
    private int countWait;   // per-shot cooldown (ticks)
    private int reloadWait;  // reload countdown (ticks); >0 == reloading
    private int magazine;    // rounds left in the current burst

    public WeaponSlot(String weaponName, MCH_WeaponInfo info, List<MCH_AircraftInfo.Weapon> mounts) {
        if (mounts == null || mounts.isEmpty()) {
            throw new IllegalArgumentException("WeaponSlot requires at least one mount");
        }
        this.weaponName = weaponName;
        this.info = info;
        this.mounts = mounts;
        this.infiniteMagazine = magSize() <= 0;
        this.magazine = this.infiniteMagazine ? Integer.MAX_VALUE : magSize();
    }

    /** Burst/magazine size. {@code checkData} has already coerced {@code round<=0 -> maxAmmo}; if still 0, infinite. */
    private int magSize() {
        if (this.info.round > 0) {
            return this.info.round;
        }
        return this.info.maxAmmo; // 0 => treated as infinite (see infiniteMagazine)
    }

    /** Advance the fire-control counters one tick (cooldown + reload). Call once per server tick. */
    public void tick() {
        if (this.countWait > 0) {
            this.countWait--;
        }
        if (this.reloadWait > 0) {
            this.reloadWait--;
            if (this.reloadWait == 0) {
                this.magazine = magSize(); // reload complete — refill (reserve economy deferred)
            }
        }
    }

    /** True if the trigger can fire this tick: cooldown elapsed, not mid-reload, and ammo in the magazine. */
    public boolean canFire() {
        return this.countWait == 0 && this.reloadWait == 0 && this.magazine > 0;
    }

    /**
     * Consume one shot and return the mount it fires from (round-robin). The caller MUST have checked {@link #canFire()}
     * first. Sets the per-shot cooldown to {@code info.delay}, decrements the magazine, and starts a reload when the
     * burst empties. Never returns {@code null}.
     */
    public MCH_AircraftInfo.Weapon fireOneShot() {
        MCH_AircraftInfo.Weapon mount = this.mounts.get(this.barrel);
        this.barrel = (this.barrel + 1) % this.mounts.size();
        this.countWait = Math.max(1, this.info.delay);
        if (!this.infiniteMagazine) {
            this.magazine--;
            if (this.magazine <= 0) {
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
