package mcheli.dependent;

import java.util.Map;
import mcheli.agnostic.weapon.MCH_LockAudit;
import mcheli.agnostic.weapon.MCH_LockGeometry;
import mcheli.agnostic.weapon.WeaponSlot;
import mcheli.dependent.control.ServerboundControlPayload;
import mcheli.dependent.control.ServerboundNotifyLockPayload;
import mcheli.dependent.control.ServerboundWeaponSwitchPayload;
import mcheli.dependent.entity.AbstractMchVehicle;
import mcheli.dependent.entity.MchBullet;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Headless proof of the REAL guided-missile fire path, from the network handlers to the homing round. The homing and
 * flare self-tests start their round with {@link MchBullet#spawnGuided} directly; this one goes in the way a client
 * does: a player in the AH-64's pilot seat selects the AIM-92 through {@link ServerboundWeaponSwitchPayload#handle},
 * then speaks the client's lock protocol ({@code MchLockTracker} + {@code MchClientInput}): a
 * {@link ServerboundNotifyLockPayload} naming the target on the lock's first tick and every 15 ticks after, FIRE held
 * through {@link ServerboundControlPayload#handle} with no lock while the lock builds, and the completed-lock claim
 * ({@code lockTargetId}) once the client's counter reaches the lock time. The server's lock audit
 * ({@link MCH_LockAudit}) has to accept that claim and {@code fireSelectedWeapon} has to launch a guided round at it.
 *
 * <p>The target is placed where a real client could lock it: an iron golem hanging 16 blocks up (an AA seeker locks
 * only airborne targets, and the client calls anything within 12 blocks of the ground "on the ground"), well ahead and
 * off to the side, with the pilot turned to look straight at it, inside the seeker's 5-degree cone and in clear sight.
 * The AIM-92 rails are fixed, so the missile still leaves hull-forward and has to turn onto it.
 *
 * <p>Phases, all through the real handlers:
 * <ol>
 *   <li>select {@code aim92} by sending weapon-switch payloads (the payload only queues a cycle; the vehicle applies it
 *       on its own tick), refill it, and wait out the switch lockout, so the weapon could fire;</li>
 *   <li>hold FIRE with NO usable lock for {@value #NO_LOCK_TICKS} ticks: no lock ({@code -1}), a lock on the heli
 *       itself, and a claim on the golem with no lock notice ever sent. The fire path must refuse all three. Nothing may
 *       launch and no ammo may be spent (the reference {@code shot()} returns false before a lock). Meanwhile a second
 *       player who is NOT aboard sends FIRE with a lock on the golem; the handler's passenger check must drop it;</li>
 *   <li>a modified client's shortcut: the lock notice and the completed-lock claim together, held for
 *       {@value #EARLY_TICKS} ticks, far less than the lock time. The audit must refuse it: nothing launches, no ammo is
 *       spent. The trigger is then released and the notices stop long enough ({@link MCH_LockAudit#CANDIDATE_TIMEOUT})
 *       for the next lock to start a fresh candidacy, as a broken-off lock does;</li>
 *   <li>the real protocol: notices on lock ticks 0, 15, 30 ..., FIRE without a lock until the lock completes, then
 *       with the golem's id. The guided round must appear on the server tick that handles the first claim;</li>
 *   <li>follow the round: it must be homing on the golem, close within 5 blocks of it, and detonate;</li>
 *   <li>stealth: a {@code penetrator} helicopter ({@code Stealth = 1.0}) hangs on the pilot's clear sight line to the
 *       golem, and the pilot looks at it. The reference's seeker range on it is 0 (range scales by
 *       {@code 1 - stealth}), so no client can lock it; the honest protocol run on it anyway, claims held for three lock
 *       times with the AIM-92 ready to fire, must launch nothing and spend no ammo. Without the stealth the same claims
 *       pass, as phase 4 shows on the same line of sight;</li>
 *   <li>a manual reload: one weapon-switch payload moves the pilot off the half-empty AIM-92 rack, which reloads
 *       (reference {@code switchWeapon} -> {@code MCH_WeaponSet.reload}). Its rail must hide
 *       ({@code isMissilePartHidden}) for the ReloadTime and then show again.</li>
 * </ol>
 * Right after the launch, after the switch and after the reload, every seat-0 weapon reader of the heli
 * ({@link #weaponView}) must equal a golden recorded before the AbstractMchVehicle split.
 * The test player is a real {@link ServerPlayer} (see {@link #newTestPlayer}), because seat assignment, weapon
 * eligibility and the switch handler all require a player, as they do in game.
 */
public final class DemoLockFireSelfTest extends MchSelfTest {
    private static final String WEAPON = "aim92";
    private static final int SETTLE_WINDOW = 60;
    private static final int NO_LOCK_TICKS = 12;
    private static final int EARLY_TICKS = 3;
    private static final int QUIET_TICKS = MCH_LockAudit.CANDIDATE_TIMEOUT + 2;
    /** MchClientInput's notice cadence while a lock builds or holds. */
    private static final int NOTICE_INTERVAL = 15;
    private static final int FLIGHT_TICKS = 120;
    /** A {@code Stealth = 1.0} helicopter: no seeker range on it at all. */
    private static final String STEALTHY = "penetrator";
    /** How far along the pilot's sight line to the golem the stealthy target hangs (golem = 1). */
    private static final double STEALTHY_AT = 0.75;

    private MchHelicopter heli;
    private ServerPlayer pilot;
    private ServerPlayer outsider;
    private LivingEntity target;
    private float targetStartHealth;

    private int switches;
    private int selectedAt = -1;
    private int weaponIndex = -1;
    private int lockTime;
    private int readyAt = -1;
    private int ammoBefore;
    private int ammoAfterNoLock;
    private int noLockRounds = -1;
    private int ammoAfterEarly;
    private int earlyRounds = -1;
    private boolean outsiderIgnored = true;

    private int lockStart = -1;
    private MchBullet missile;
    private int launchedAt = -1;
    private int launchLockTick = -1;
    private boolean homingOnTarget;
    private double minDist = Double.MAX_VALUE;
    private boolean detonated;

    private Entity aimAt;                 // what the pilot looks at: the golem, then the stealthy heli
    private MchHelicopter stealthy;
    private Vec3 stealthyPos;
    private int stealthStart = -1;
    private int stealthRoundsBefore;
    private int ammoBeforeStealth;

    private float stealthRead;
    private boolean stealthRefused;
    private float targetDamage;
    private float stealthDist;
    private int stealthRoundsAfter;
    private int ammoAfterStealth;

    // Weapon-view goldens: the synced seat-weapon readers, read on the server. Recorded twice on the pre-split tree
    // (identical both runs) and hard-coded.
    /** Every seat-0 weapon reader right after the honest launch ({@link #weaponView}). */
    private static final String GOLDEN_LAUNCH_VIEW = "hidden=false name=AIM-92 Stinger idx=3 slot=aim92 total=3 mag=1 "
        + "rest=2 reload=0.0000 reloadSec=0.0000 heat=0.0000 prep=false sight=2 heatWpn=false mortar=false info=aim92 "
        + "mode=0 hud=heli count=7 would=true";
    /** The readers on the first tick the AIM-92 rail hides after the phase-7 switch away from it. */
    private static final String GOLDEN_SWITCH_VIEW = "hidden=true name=AGM-114 Hellfire idx=4 slot=agm114 total=0 "
        + "mag=0 rest=0 reload=0.2500 reloadSec=1.2500 heat=0.0000 prep=true sight=2 heatWpn=false mortar=false "
        + "info=agm114 mode=0 hud=heli count=7 would=true";
    /** The readers on the first tick the rail shows again, plus the AIM-92's total. */
    private static final String GOLDEN_RELOADED_VIEW = "hidden=false name=AGM-114 Hellfire idx=4 slot=agm114 total=0 "
        + "mag=0 rest=0 reload=0.0000 reloadSec=0.0000 heat=0.0000 prep=false sight=2 heatWpn=false mortar=false "
        + "info=agm114 mode=0 hud=heli count=7 would=true aim92Total=3";
    /** Ticks from the phase-7 switch payload until the rail hides, and until it shows again. */
    private static final int GOLDEN_RAIL_HIDDEN_AFTER_SWITCH = 1;
    private static final int GOLDEN_RAIL_SHOWN_AFTER_RELOAD = 111;
    /** Phase 7 gives up this many ticks after the switch (the AIM-92's ReloadTime is 110). */
    private static final int RELOAD_WINDOW = 150;
    private String launchView;
    private boolean railHiddenInFlight;
    private boolean railHiddenAtStealth = true;
    private int reloadSwitchAt = -1;
    private int railHiddenAfterSwitch = -1;
    private String switchView;
    private int railShownAfterReload = -1;
    private String reloadedView;

    @Override
    protected int[] areaChunks() {
        return new int[] {-2, 3, -2, 7}; // the target sits 60 blocks down +Z, 15 off to +X
    }

    @Override
    protected void start() {
        this.heli = MchRegistries.HELI.get().create(this.level);
        if (this.heli == null) {
            fail("EntityType.create returned null for the heli type");
            return;
        }
        double x = this.origin.getX() + 0.5;
        double z = this.origin.getZ() + 0.5;
        this.heli.setConfigName("ah-64");
        this.heli.setPos(x, this.origin.getY(), z); // parked on the ground, facing +Z
        this.heli.setYRot(0.0F);
        this.level.addFreshEntity(this.heli);

        this.pilot = newTestPlayer("pilot");
        this.pilot.setPos(x, this.origin.getY(), z);
        boolean boarded = this.pilot.startRiding(this.heli, true);
        int seat = this.heli.seatIndexOf(this.pilot);
        if (!boarded || seat != 0) {
            fail("the test player did not take the pilot seat (boarded={}, seat={})", boarded, seat);
            return;
        }
        this.outsider = newTestPlayer("outsider");
        this.outsider.setPos(x + 3.0, this.origin.getY(), z);

        if (EntityType.IRON_GOLEM.create(this.level) instanceof LivingEntity g) {
            if (g instanceof Mob m) {
                m.setNoAi(true);
            }
            g.setNoGravity(true);
            g.setPos(this.origin.getX() + 15.5, this.origin.getY() + 16.0, this.origin.getZ() + 60.5);
            this.level.addFreshEntity(g);
            this.target = g;
            this.aimAt = g;
            this.targetStartHealth = g.getHealth();
        } else {
            fail("could not create the iron golem target");
            return;
        }
        LOG.info("[LOCKFIRE-SELFTEST] ah-64 id={} with test pilot in seat {}, target golem id={} up ahead, off-axis",
            this.heli.getId(), seat, this.target.getId());
    }

    @Override
    protected void tick() {
        int t = ticks();
        // The pilot keeps the golem in his sight, as a player holding a lock does (the seat follows the vehicle; the
        // look is the rider's own, and the test player has no client turning it).
        aimPilotAtTarget();
        if (this.stealthy != null) {
            // An unmanned heli sinks; hold it where it was placed.
            this.stealthy.setPos(this.stealthyPos.x, this.stealthyPos.y, this.stealthyPos.z);
            this.stealthy.setDeltaMovement(Vec3.ZERO);
        }

        // ---- 1. select the AIM-92 through the weapon-switch handler, refill it, let the switch lockout run out ----
        if (this.selectedAt < 0) {
            WeaponSlot sel = this.heli.weaponAt(this.heli.selectedWeaponIndex(0));
            if (sel != null && WEAPON.equals(sel.weaponName)) {
                this.selectedAt = t;
                this.weaponIndex = this.heli.selectedWeaponIndex(0);
                this.lockTime = MCH_LockGeometry.effectiveCountMax(Math.max(1, sel.info.lockTime),
                    AbstractMchVehicle.lockStealth(this.target));
                // A fresh aim92 rack spawns DRY (MaxAmmo=8): the ammo economy, orthogonal to the fire path. Fill it.
                this.heli.debugSupplySeatWeapon(0);
                LOG.info("[LOCKFIRE-SELFTEST] {} selected after {} switch payload(s), lock time {}", WEAPON,
                    this.switches, this.lockTime);
            } else if (this.switches > 2 * Math.max(1, this.heli.weaponCount())) {
                fail("could not select {} through ServerboundWeaponSwitchPayload after {} payloads (selected={})",
                    WEAPON, this.switches, sel == null ? "<none>" : sel.weaponName);
            } else if (t % 2 == 1) { // one cycle per vehicle tick, then look at the synced selection
                ServerboundWeaponSwitchPayload.handle(payload(ServerboundWeaponSwitchPayload.class,
                    Map.of("vehicleId", this.heli.getId(), "direction", 1)), payloadContext(this.pilot));
                this.switches++;
            }
            return;
        }
        if (this.readyAt < 0) {
            // A refused shot only proves the audit once the weapon itself could fire: loaded, out of the lockout.
            if (!this.heli.selectedInPreparation(0) && this.heli.selectedAmmo(0) > 0) {
                this.readyAt = t;
                this.ammoBefore = this.heli.ammoOf(this.weaponIndex);
            } else if (t - this.selectedAt > SETTLE_WINDOW) {
                fail("{} never became ready to fire ({})", WEAPON, this.heli.debugSeatWeapon(0));
            }
            return;
        }
        int phase = t - this.readyAt;

        // ---- 2. FIRE held without a usable lock: nothing may launch, no ammo may be spent ----
        if (phase < NO_LOCK_TICKS) {
            int[] badLocks = {-1, this.heli.getId(), this.target.getId()}; // none / ourselves / never announced
            int badLock = badLocks[phase % badLocks.length];
            sendControl(this.pilot, ServerboundControlPayload.FIRE, badLock);
            sendControl(this.outsider, ServerboundControlPayload.FIRE, this.target.getId());
            if (badLock != this.target.getId() && this.heli.controlState(0).lockTargetId == this.target.getId()) {
                this.outsiderIgnored = false; // a non-passenger's packet reached the pilot seat
            }
            return;
        }
        phase -= NO_LOCK_TICKS;
        if (this.noLockRounds < 0) {
            // The vehicle has now ticked once on the last no-lock state.
            this.noLockRounds = countGuidedRounds();
            this.ammoAfterNoLock = this.heli.ammoOf(this.weaponIndex);
            LOG.info("[LOCKFIRE-SELFTEST] no-lock phase: guidedRounds={} ammo {} -> {} outsiderIgnored={}",
                this.noLockRounds, this.ammoBefore, this.ammoAfterNoLock, this.outsiderIgnored);
        }

        // ---- 3. a notice and the claim together (a modified client): refused; then the lock lapses ----
        if (phase < EARLY_TICKS) {
            if (phase == 0) {
                sendNotice(this.target.getId());
            }
            sendControl(this.pilot, ServerboundControlPayload.FIRE, this.target.getId());
            return;
        }
        phase -= EARLY_TICKS;
        if (this.earlyRounds < 0) {
            this.earlyRounds = countGuidedRounds();
            this.ammoAfterEarly = this.heli.ammoOf(this.weaponIndex);
            sendControl(this.pilot, 0, -1); // trigger released: the client drops its lock and stops the notices
            LOG.info("[LOCKFIRE-SELFTEST] early-claim phase: guidedRounds={} ammo {} -> {}", this.earlyRounds,
                this.ammoAfterNoLock, this.ammoAfterEarly);
        }
        if (phase < QUIET_TICKS) {
            return;
        }
        phase -= QUIET_TICKS;

        // ---- 4. the client's lock protocol, then the completed-lock claim ----
        if (this.missile == null) {
            this.missile = firstGuidedRound();
            if (this.missile != null) {
                this.launchedAt = t;
                this.launchLockTick = phase;
                this.homingOnTarget = this.missile.isHomingOn(this.target);
                this.launchView = weaponView();
                sendControl(this.pilot, 0, -1);
                LOG.info("[LOCKFIRE-SELFTEST] guided round id={} launched on lock tick {} (lock time {}), "
                    + "homingOnTarget={}", this.missile.getId(), phase, this.lockTime, this.homingOnTarget);
                LOG.info("[LOCKFIRE-SELFTEST] weapon view at launch: {}", this.launchView);
            } else if (phase > this.lockTime + 40) {
                fail("no guided round launched within 40 ticks of a completed lock ({})",
                    this.heli.debugSeatWeapon(0));
                return;
            } else {
                // MchLockTracker: the lock acquires on lock tick 0 (count 1) and completes when the count reaches the
                // lock time, lockTime - 1 ticks later; MchClientInput sends the notice first, then the controls.
                if (phase % NOTICE_INTERVAL == 0) {
                    sendNotice(this.target.getId());
                }
                boolean complete = phase >= this.lockTime - 1;
                sendControl(this.pilot, ServerboundControlPayload.FIRE, complete ? this.target.getId() : -1);
                return;
            }
        }

        // ---- 5. follow it onto the target ----
        if (this.stealthy == null) {
            // Round = 2: the launch leaves one round in the rack, so no reload starts and the rail never hides.
            this.railHiddenInFlight |= this.heli.isMissilePartHidden(WEAPON);
            if (!this.missile.isRemoved()) {
                this.minDist = Math.min(this.minDist, this.missile.distanceTo(this.target));
            } else {
                this.detonated = true;
            }
            if (!this.detonated && t - this.launchedAt < FLIGHT_TICKS) {
                return;
            }
            // Phase 6 only proves something while the AIM-92 could fire: its second round loaded, past the Delay.
            if (this.heli.selectedInPreparation(0) || this.heli.selectedAmmo(0) <= 0) {
                if (t - this.launchedAt > FLIGHT_TICKS + SETTLE_WINDOW) {
                    fail("{} never became ready for the stealth phase ({})", WEAPON, this.heli.debugSeatWeapon(0));
                }
                return;
            }
            spawnStealthy();
            if (this.stealthy == null) {
                return; // spawnStealthy failed the test
            }
            this.stealthStart = t;
            this.railHiddenAtStealth = this.heli.isMissilePartHidden(WEAPON);
            this.stealthRoundsBefore = countGuidedRounds();
            this.ammoBeforeStealth = this.heli.ammoOf(this.weaponIndex);
            this.aimAt = this.stealthy;
            return;
        }

        // ---- 6. the honest protocol on a Stealth 1.0 target, claims held for three lock times: nothing launches ----
        int sp = t - this.stealthStart - 1;
        if (sp < 3 * this.lockTime) {
            if (sp % NOTICE_INTERVAL == 0) {
                sendNotice(this.stealthy.getId());
            }
            sendControl(this.pilot, ServerboundControlPayload.FIRE, sp >= this.lockTime - 1 ? this.stealthy.getId() : -1);
            return;
        }
        if (this.reloadSwitchAt < 0) {
            sendControl(this.pilot, 0, -1);
            this.stealthRead = AbstractMchVehicle.lockStealth(this.stealthy);
            this.stealthRefused = this.stealthRead == 1.0F && AbstractMchVehicle.lockStealth(this.target) == 0.0F
                && countGuidedRounds() == this.stealthRoundsBefore
                && this.heli.ammoOf(this.weaponIndex) == this.ammoBeforeStealth;
            this.targetDamage = this.targetStartHealth - this.target.getHealth();
            this.stealthDist = this.pilot.distanceTo(this.stealthy);
            this.stealthRoundsAfter = countGuidedRounds();
            this.ammoAfterStealth = this.heli.ammoOf(this.weaponIndex);

            // ---- 7. a manual reload: one switch away from the half-empty rack reloads it (reference switchWeapon ->
            //         MCH_WeaponSet.reload), so its rail hides for the ReloadTime and then shows again ----
            ServerboundWeaponSwitchPayload.handle(payload(ServerboundWeaponSwitchPayload.class,
                Map.of("vehicleId", this.heli.getId(), "direction", 1)), payloadContext(this.pilot));
            this.reloadSwitchAt = t;
            return;
        }
        int rp = t - this.reloadSwitchAt;
        boolean railHidden = this.heli.isMissilePartHidden(WEAPON);
        if (this.railHiddenAfterSwitch < 0 && railHidden) {
            this.railHiddenAfterSwitch = rp;
            this.switchView = weaponView();
            LOG.info("[LOCKFIRE-SELFTEST] weapon view after the switch: {}", this.switchView);
        } else if (this.railHiddenAfterSwitch >= 0 && this.railShownAfterReload < 0 && !railHidden) {
            this.railShownAfterReload = rp;
            this.reloadedView = weaponView() + " aim92Total=" + this.heli.ammoOf(this.weaponIndex);
            LOG.info("[LOCKFIRE-SELFTEST] weapon view after the reload: {}", this.reloadedView);
        }
        if (this.railShownAfterReload < 0 && rp <= RELOAD_WINDOW) {
            return;
        }
        boolean stealthRefused = this.stealthRefused;
        float stealthRead = this.stealthRead;

        boolean heldFire = this.noLockRounds == 0 && this.ammoAfterNoLock == this.ammoBefore;
        boolean earlyRefused = this.earlyRounds == 0 && this.ammoAfterEarly == this.ammoBefore;
        // The claim goes out on lock tick lockTime - 1; the vehicle fires on its next tick, which this test sees as
        // lock tick lockTime. Later means the audit held an honest client back.
        boolean onTime = this.launchLockTick >= this.lockTime - 1 && this.launchLockTick <= this.lockTime + 1;
        boolean homedClose = this.minDist < 5.0;
        LOG.info("[LOCKFIRE-SELFTEST] weapon view observed: launch=[{}] railHiddenInFlight={} railHiddenAtStealth={} "
                + "switch=[{}] railHiddenAfterSwitch={} reloaded=[{}] railShownAfterReload={}",
            this.launchView, this.railHiddenInFlight, this.railHiddenAtStealth, this.switchView,
            this.railHiddenAfterSwitch, this.reloadedView, this.railShownAfterReload);
        boolean weaponView = java.util.Objects.equals(GOLDEN_LAUNCH_VIEW, this.launchView)
            && !this.railHiddenInFlight && !this.railHiddenAtStealth
            && java.util.Objects.equals(GOLDEN_SWITCH_VIEW, this.switchView)
            && this.railHiddenAfterSwitch == GOLDEN_RAIL_HIDDEN_AFTER_SWITCH
            && java.util.Objects.equals(GOLDEN_RELOADED_VIEW, this.reloadedView)
            && this.railShownAfterReload == GOLDEN_RAIL_SHOWN_AFTER_RELOAD;
        boolean pass = heldFire && this.outsiderIgnored && earlyRefused && onTime && this.homingOnTarget && homedClose
            && this.detonated && stealthRefused && weaponView;
        finish(pass, "heldFireWithoutLock={} (rounds={} ammo {}->{}) outsiderIgnored={} earlyClaimRefused={} "
                + "(rounds={} ammo ->{}) launchedOnLockTick={} (lock time {}) homingOnTarget={} minDist={} "
                + "detonated={} targetDamage={} stealthClaimRefused={} (stealth {}, dist {}, rounds {}->{}, ammo {}->{}) "
                + "weaponView={} (rail hidden {} ticks after the switch, shown {} ticks after)",
            heldFire, this.noLockRounds, this.ammoBefore, this.ammoAfterNoLock, this.outsiderIgnored, earlyRefused,
            this.earlyRounds, this.ammoAfterEarly, this.launchLockTick, this.lockTime, this.homingOnTarget,
            String.format("%.2f", this.minDist), this.detonated,
            String.format("%.1f", this.targetDamage), stealthRefused, stealthRead,
            String.format("%.1f", this.stealthDist), this.stealthRoundsBefore, this.stealthRoundsAfter,
            this.ammoBeforeStealth, this.ammoAfterStealth, weaponView, this.railHiddenAfterSwitch,
            this.railShownAfterReload);
    }

    /** Every seat-0 weapon reader of the heli in one line: the synced selection, name and mode, the spent-rail mask,
     *  the totals, the seat's live magazine/reload/heat numbers, the sight and HUD gates, and the config list. */
    private String weaponView() {
        int idx = this.heli.selectedWeaponIndex(0);
        WeaponSlot slot = this.heli.weaponAt(idx);
        mcheli.agnostic.weapon.MCH_WeaponInfo info = this.heli.selectedClientWeaponInfo(0);
        java.util.Locale l = java.util.Locale.ROOT;
        return "hidden=" + this.heli.isMissilePartHidden(WEAPON)
            + " name=" + this.heli.selectedWeaponName(0)
            + " idx=" + idx
            + " slot=" + (slot != null ? slot.weaponName : null)
            + " total=" + this.heli.ammoOf(idx)
            + " mag=" + this.heli.selectedAmmo(0)
            + " rest=" + this.heli.selectedRestAmmo(0)
            + " reload=" + String.format(l, "%.4f", this.heli.selectedReload(0))
            + " reloadSec=" + String.format(l, "%.4f", this.heli.selectedReloadSeconds(0))
            + " heat=" + String.format(l, "%.4f", this.heli.selectedHeat(0))
            + " prep=" + this.heli.selectedInPreparation(0)
            + " sight=" + this.heli.selectedWeaponSightType(0)
            + " heatWpn=" + this.heli.isSelectedWeaponHeat(0)
            + " mortar=" + this.heli.selectedWeaponDisplaysMortarDist(0)
            + " info=" + (info != null ? info.name : null)
            + " mode=" + this.heli.weaponMode(idx)
            + " hud=" + this.heli.hudName(0)
            + " count=" + this.heli.weaponCount()
            + " would=" + this.heli.wouldSwitchWeapon(0, 1);
    }

    /**
     * Hang the stealthy heli on the pilot's sight line to the golem, {@link #STEALTHY_AT} of the way out, centred on
     * it: phase 4 proved that line clear, and the cone holds it, so only its stealth can refuse the claims.
     */
    private void spawnStealthy() {
        MchHelicopter h = MchRegistries.HELI.get().create(this.level);
        if (h == null) {
            fail("EntityType.create returned null for the {} heli", STEALTHY);
            return;
        }
        h.setConfigName(STEALTHY);
        Vec3 eye = this.pilot.getEyePosition();
        Vec3 golem = this.target.position().add(0.0, this.target.getBbHeight() / 2.0, 0.0);
        Vec3 centre = eye.add(golem.subtract(eye).scale(STEALTHY_AT));
        this.stealthyPos = centre.subtract(0.0, h.getBbHeight() / 2.0, 0.0);
        h.setPos(this.stealthyPos.x, this.stealthyPos.y, this.stealthyPos.z);
        h.setNoGravity(true);
        this.level.addFreshEntity(h);
        this.stealthy = h;
        LOG.info("[LOCKFIRE-SELFTEST] {} id={} (stealth {}) placed {} blocks out on the sight line", STEALTHY,
            h.getId(), AbstractMchVehicle.lockStealth(h), String.format("%.1f", this.pilot.distanceTo(h)));
    }

    @Override
    protected void cleanup() {
        if (this.pilot != null && this.pilot.isPassenger()) {
            this.pilot.stopRiding();
        }
    }

    /** Turn the pilot to look at the current target's centre ({@link #aimAt}) from where both the client tracker and
     *  the server audit measure the seeker cone (the operator's position, {@code MCH_LockGeometry.inLockCone}). */
    private void aimPilotAtTarget() {
        Entity aim = this.aimAt;
        if (this.pilot == null || aim == null) {
            return;
        }
        double dx = aim.getX() - this.pilot.getX();
        double dy = aim.getY() + aim.getBbHeight() / 2.0 - this.pilot.getY();
        double dz = aim.getZ() - this.pilot.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        this.pilot.setYRot(yaw);
        this.pilot.setYHeadRot(yaw);
        this.pilot.setXRot(pitch);
    }

    /** Deliver a lock notice from the pilot through the real server handler. */
    private void sendNotice(int targetId) {
        ServerboundNotifyLockPayload.handle(payload(ServerboundNotifyLockPayload.class, Map.of("targetId", targetId)),
            payloadContext(this.pilot));
    }

    /** Deliver a control packet from {@code sender} through the real server handler. */
    private void sendControl(ServerPlayer sender, int bits, int lockTargetId) {
        ServerboundControlPayload.handle(payload(ServerboundControlPayload.class,
                Map.of("vehicleId", this.heli.getId(), "bits", bits, "lockTargetId", lockTargetId)),
            payloadContext(sender));
    }

    private MchBullet firstGuidedRound() {
        for (Entity e : spawned()) {
            if (e instanceof MchBullet b && b.isGuided()) {
                return b;
            }
        }
        return null;
    }

    private int countGuidedRounds() {
        int n = 0;
        for (Entity e : spawned()) {
            if (e instanceof MchBullet b && b.isGuided()) {
                n++;
            }
        }
        return n;
    }
}
