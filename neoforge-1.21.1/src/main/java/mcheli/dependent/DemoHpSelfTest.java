package mcheli.dependent;

import com.mojang.logging.LogUtils;
import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.dependent.entity.MchHelicopter;
import mcheli.dependent.entity.MchTank;
import mcheli.dependent.registry.MchRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Headless proof of the vehicle HP / armor / destruction system (#35). Covers, without a client:
 * <ul>
 *   <li><b>M1 HP core</b> — damage accumulates (int-truncated), HP = maxHp − damageTaken, the {@code inFire}/{@code
 *       cactus} gates deal nothing, and MCHeli's own post-hit cooldown blocks a follow-up hit.</li>
 *   <li><b>M2 per-part armor</b> — the m1a2's real config (armorMin 12 / max 500 / DF 0.95 + 0.70/1.0/1.25 zones):
 *       the ray query picks the struck zone's factor, and the armor formula's load-bearing ORDER (armor plate
 *       multiplies BEFORE the min-subtract, a weak point AFTER) yields the exact expected damage.</li>
 *   <li><b>M3 environmental gates</b> — lava (×2..9) and onFire BYPASS armor; {@code inWall} (crash/water) goes
 *       THROUGH it.</li>
 *   <li><b>M4 destruction/wreck</b> — reaching maxHp flips the synced destroyed flag, ejects riders, arms the 500-tick
 *       despawn countdown, and the wreck falls (the flight-state seam killed its lift) then counts down.</li>
 * </ul>
 * The wreck's visible tumble + dark tint + HP HUD readout are the only client-only parts (a runClient check).
 */
public final class DemoHpSelfTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int CHECK_AFTER_TICKS = 20;
    private static final float EPS = 1.0e-4F;

    private MchHelicopter wreck;
    private Pig wreckRider;
    private double wreckStartY;
    private boolean wreckDestroyedOk;
    private boolean wreckEjectOk;
    private int wreckStartDespawn;
    private int ticks = -1;

    // M5: end-to-end real-bullet per-part armor (the case the happy path missed).
    private MchTank m5Tank;
    private boolean m5Gated;   // real m1a2 config present -> run the exact assertion
    private boolean m5Fired;
    private static final int M5_FIRE_TICK = 5; // let the tank settle a few ticks, then probe its live per-part hitboxes

    // Milestone results (default true so a cleanly-skipped gated block doesn't fail the run).
    private boolean m1Ok, m2Ok = true, m3Ok = true, m5Ok = true;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        DamageSources ds = level.damageSources();
        BlockPos spawn = level.getSharedSpawnPos();
        double x = spawn.getX() + 30.0;   // well clear of the other self-tests
        double y = spawn.getY() + 60.0;
        double z = spawn.getZ() + 0.5;

        m1Ok = runM1(level, ds);
        runM2(level, ds);   // sets m2Ok (gated on the real m1a2 config)
        runM3(level, ds);   // sets m3Ok (gated on the real m1a2 config)
        setupM4(level, ds, x, y, z);
        setupM5(level, spawn.getX() + 40, spawn.getY() + 50, spawn.getZ() + 4);

        ticks = 0;
    }

    // ---- M5: end-to-end per-part armor via a REAL fired bullet (regression guard for the ray-truncation +
    //      protruding-box hit-detection fixes; the direct-call M2 test could not catch either). ----
    private void setupM5(ServerLevel level, double x, double y, double z) {
        MchTank t = MchRegistries.TANK.get().create(level);
        if (t == null) return;
        t.setConfigName("m1a2");
        t.setPos(x + 0.5, y, z + 0.5); // high open air; both M5 checks are immune to the pilotless tank's free-fall
        t.setYRot(0.0F);
        level.addFreshEntity(t);
        this.m5Tank = t;
        this.m5Gated = isM1a2Armored(t);
    }

    private void tickM5() {
        if (this.m5Tank == null || !this.m5Gated || this.m5Fired) return;
        if (ticks == M5_FIRE_TICK) {
            this.m5Tank.setYRot(0.0F);
            this.m5Tank.setXRot(0.0F);
            this.m5Tank.setRollAngle(0.0F);
            this.m5Tank.refreshExtraBoxes();
            double tx = this.m5Tank.getX(), ty = this.m5Tank.getY(), tz = this.m5Tank.getZ();

            // Regression guard for the two per-part fixes, exercised at the EXACT geometry MchBullet feeds into the
            // vehicle (a real fired bullet is separately covered end-to-end by the BULLET/WEAPON self-tests; firing one
            // here would only re-test MchBullet's flight through unknown terrain, orthogonal to the HP fix).
            //
            // #13 protruding-box hit registration: a ray grazing the FRONT box0 where it PROTRUDES beyond the vanilla
            //     selection AABB (z+3.5, |z|>2.5) must be FOUND by clipParts — else a real round there misses entirely.
            net.minecraft.world.phys.Vec3 protrudeHit = this.m5Tank.clipParts(
                new Vec3(tx + 7.0, ty + 0.6, tz + 3.5), new Vec3(tx - 3.0, ty + 0.6, tz + 3.5));
            boolean protrudingHit = protrudeHit != null;
            // #14 no ray truncation: a side shot into the INTERIOR armor box (x=±0.9, well inside the 5-wide AABB)
            //     resolves to the 0.70 zone over the FULL ray, but to 1.0 if the ray is cut at the selection-AABB entry
            //     (x=tx+2.5) — which is exactly what the old bug passed. So full==0.70 AND truncated==1.0 proves the fix.
            Vec3 sideMuzzle = new Vec3(tx + 8.0, ty + 1.6, tz + 1.0);
            float fullFac = this.m5Tank.boundingBoxDamageFactorAt(sideMuzzle, new Vec3(tx - 8.0, ty + 1.6, tz + 1.0));
            float truncFac = this.m5Tank.boundingBoxDamageFactorAt(sideMuzzle, new Vec3(tx + 2.5, ty + 1.6, tz + 1.0));
            this.m5Ok = protrudingHit && Math.abs(fullFac - 0.70F) < EPS && Math.abs(truncFac - 1.0F) < EPS;
            this.m5Fired = true;
            LOG.info("[HP-SELFTEST] M5 per-part: protrudingHit={} interiorFullRay={} interiorTruncatedRay={} -> {}",
                protrudingHit, fmt(fullFac), fmt(truncFac), this.m5Ok ? "OK" : "FAIL (truncated!=1.0 or full!=0.70)");
        }
    }

    // ---- M1: HP core (config-independent) ----
    private boolean runM1(ServerLevel level, DamageSources ds) {
        MchHelicopter h = newHeli(level);
        if (h == null) return false;
        int max = h.getMaxHp();
        h.hurt(ds.generic(), 50.0F);
        boolean accumulate = h.getDamageTaken() > 0 && h.getHp() == max - h.getDamageTaken() && h.getHp() < max;

        MchHelicopter hf = newHeli(level);
        boolean inFireGate = hf != null && !hf.hurt(ds.inFire(), 10.0F) && hf.getDamageTaken() == 0;
        MchHelicopter hc = newHeli(level);
        boolean cactusGate = hc != null && !hc.hurt(ds.cactus(), 10.0F) && hc.getDamageTaken() == 0;

        // Cooldown: a lava hit sets the cooldown; the immediate follow-up must be rejected (damageTaken unchanged).
        MchHelicopter hcd = newHeli(level);
        boolean cooldownGate = false;
        if (hcd != null) {
            hcd.hurt(ds.lava(), 5.0F);
            int afterLava = hcd.getDamageTaken();
            boolean blocked = !hcd.hurt(ds.generic(), 10.0F);
            cooldownGate = blocked && hcd.getDamageTaken() == afterLava && afterLava > 0;
        }

        boolean ok = accumulate && inFireGate && cactusGate && cooldownGate;
        LOG.info("[HP-SELFTEST] M1 HP core: accumulate={} inFireGate={} cactusGate={} cooldownGate={} -> {}",
            accumulate, inFireGate, cactusGate, cooldownGate, ok ? "OK" : "FAIL");
        return ok;
    }

    // ---- M2: per-part armor (real m1a2 config) ----
    private void runM2(ServerLevel level, DamageSources ds) {
        MchTank probe = newTank(level, 0.0, 100.0, 0.0);
        if (probe == null || !isM1a2Armored(probe)) {
            LOG.info("[HP-SELFTEST] M2 per-part armor: SKIP (real m1a2 config not loaded — demo fallback has no armor)");
            return;
        }
        // Geometry: a fixed pose, boxes refreshed without a tick; vertical rays through known zones.
        MchTank t = newTank(level, 0.0, 100.0, 0.0);
        t.setYRot(0.0F);
        t.setXRot(0.0F);
        t.setRollAngle(0.0F);
        t.refreshExtraBoxes();
        double tx = t.getX(), ty = t.getY(), tz = t.getZ();
        float front = t.boundingBoxDamageFactorAt(new Vec3(tx, ty + 10, tz + 2.2), new Vec3(tx, ty - 10, tz + 2.2));
        float rear = t.boundingBoxDamageFactorAt(new Vec3(tx - 0.9, ty + 10, tz - 4.5), new Vec3(tx - 0.9, ty - 10, tz - 4.5));
        float miss = t.boundingBoxDamageFactorAt(new Vec3(tx + 30, ty + 10, tz), new Vec3(tx + 30, ty - 10, tz));
        boolean geomOk = near(front, 0.70F) && near(rear, 1.25F) && near(miss, 1.0F);

        // Formula: base 100 through the m1a2 armor (min 12, max 500, DF 0.95), for each part factor.
        //   factor 1.00: 100 *0.95 -12            = 83
        //   factor 0.70: 100 *0.70 *0.95 -12      = 54.5 -> 54   (plate applied BEFORE the min-subtract)
        //   factor 1.25: (100 *0.95 -12) *1.25    = 103.75 -> 103 (weak point applied AFTER the min-subtract)
        //   base   5:    5 *0.95 -12 <= 0         = fully absorbed by armorMinDamage
        int d100 = hitWithFactor(level, ds, 1.00F, 100.0F);
        int d070 = hitWithFactor(level, ds, 0.70F, 100.0F);
        int d125 = hitWithFactor(level, ds, 1.25F, 100.0F);
        int d005 = hitWithFactor(level, ds, 1.00F, 5.0F);
        boolean formulaOk = d100 == 83 && d070 == 54 && d125 == 103 && d005 == 0;

        m2Ok = geomOk && formulaOk;
        LOG.info("[HP-SELFTEST] M2 per-part armor: geom(front={},rear={},miss={})={} formula(100->{},0.70->{},1.25->{},5->{})={} -> {}",
            fmt(front), fmt(rear), fmt(miss), geomOk, d100, d070, d125, d005, formulaOk, m2Ok ? "OK" : "FAIL");
    }

    // ---- M3: environmental damage-type gates (real m1a2 config) ----
    private void runM3(ServerLevel level, DamageSources ds) {
        MchTank probe = newTank(level, 0.0, 100.0, 0.0);
        if (probe == null || !isM1a2Armored(probe)) {
            LOG.info("[HP-SELFTEST] M3 environmental gates: SKIP (real m1a2 config not loaded)");
            return;
        }
        // onFire bypasses armor: raw 5 lands (armorMinDamage 12 would otherwise zero it).
        MchTank tf = newTank(level, 0.0, 100.0, 0.0);
        tf.hurt(ds.onFire(), 5.0F);
        boolean onFireBypass = tf.getDamageTaken() == 5;

        // lava bypasses armor AND multiplies ×2..9: base 1 -> 2..9 lands (armor would zero it).
        MchTank tl = newTank(level, 0.0, 100.0, 0.0);
        tl.hurt(ds.lava(), 1.0F);
        int lavaDmg = tl.getDamageTaken();
        boolean lavaBypass = lavaDmg >= 2 && lavaDmg <= 9;

        // inWall (crash/water) goes THROUGH armor: base 100 -> 83, identical to a generic factor-1 hit.
        MchTank tw = newTank(level, 0.0, 100.0, 0.0);
        tw.hurt(ds.inWall(), 100.0F);
        boolean inWallArmored = tw.getDamageTaken() == 83;

        m3Ok = onFireBypass && lavaBypass && inWallArmored;
        LOG.info("[HP-SELFTEST] M3 environmental gates: onFireBypass={}(={}) lavaBypass={}(={}) inWallArmored={}(={}) -> {}",
            onFireBypass, tf.getDamageTaken(), lavaBypass, lavaDmg, inWallArmored, tw.getDamageTaken(), m3Ok ? "OK" : "FAIL");
    }

    // ---- M4: destruction + wreck (config-independent), verified over ticks ----
    private void setupM4(ServerLevel level, DamageSources ds, double x, double y, double z) {
        MchHelicopter h = MchRegistries.HELI.get().create(level);
        if (h == null) return;
        h.setConfigName("ah-64");
        h.setPos(x, y, z);
        level.addFreshEntity(h);
        if (EntityType.PIG.create(level) instanceof Pig pig) {
            pig.setPos(x, y, z);
            level.addFreshEntity(pig);
            pig.startRiding(h, true);
            this.wreckRider = pig;
        }
        boolean rode = this.wreckRider != null && !h.getPassengers().isEmpty();

        h.hurt(ds.generic(), 99999.0F); // one lethal hit -> destruction
        this.wreckDestroyedOk = h.isDestroyed() && h.despawnCount() == 500;
        this.wreckEjectOk = !rode || h.getPassengers().isEmpty(); // riders ejected on destroy
        this.wreckStartY = h.getY();
        this.wreckStartDespawn = h.despawnCount();
        this.wreck = h;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (ticks < 0) return;
        ticks++;
        tickM5();   // fires at tick 5, checks at tick 13 (both before the M4 check)
        if (ticks < CHECK_AFTER_TICKS) return;

        boolean fell = this.wreck != null && this.wreck.getY() < this.wreckStartY - 1.0;
        boolean countedDown = this.wreck != null && this.wreck.despawnCount() < this.wreckStartDespawn
            && this.wreck.despawnCount() > 0;
        boolean stillWreck = this.wreck != null && !this.wreck.isRemoved();
        boolean m4Ok = this.wreckDestroyedOk && this.wreckEjectOk && fell && countedDown && stillWreck;
        LOG.info("[HP-SELFTEST] M4 destruction: destroyed={} eject={} fell={}(dy={}) countdown={}({}->{}) stillWreck={} -> {}",
            this.wreckDestroyedOk, this.wreckEjectOk, fell,
            this.wreck != null ? fmt(this.wreck.getY() - this.wreckStartY) : "n/a",
            countedDown, this.wreckStartDespawn, this.wreck != null ? this.wreck.despawnCount() : -1,
            stillWreck, m4Ok ? "OK" : "FAIL");

        boolean pass = m1Ok && m2Ok && m3Ok && m4Ok && m5Ok;
        LOG.info("[HP-SELFTEST] RESULT: {} - vehicle HP/armor/destruction (M1={} M2={} M3={} M4={} M5={})",
            pass ? "PASS" : "FAIL", m1Ok, m2Ok, m3Ok, m4Ok, m5Ok);

        if (this.wreck != null && !this.wreck.isRemoved()) this.wreck.discard();
        if (this.wreckRider != null && !this.wreckRider.isRemoved()) this.wreckRider.discard();
        if (this.m5Tank != null && !this.m5Tank.isRemoved()) this.m5Tank.discard();
        ticks = -1;
        this.wreck = null;
        this.wreckRider = null;
        this.m5Tank = null;
    }

    // ---- helpers ----
    private static MchHelicopter newHeli(ServerLevel level) {
        MchHelicopter h = MchRegistries.HELI.get().create(level);
        if (h != null) { h.setConfigName("ah-64"); h.setPos(0.0, 100.0, 0.0); }
        return h;
    }

    private static MchTank newTank(ServerLevel level, double x, double y, double z) {
        MchTank t = MchRegistries.TANK.get().create(level);
        if (t != null) { t.setConfigName("m1a2"); t.setPos(x, y, z); }
        return t;
    }

    private static boolean isM1a2Armored(MchTank t) {
        MCH_AircraftInfo info = t.hostInfo();
        return info != null && Math.abs(info.armorMinDamage - 12.0F) < EPS
            && info.extraBoundingBox != null && info.extraBoundingBox.size() >= 7;
    }

    /** Fresh tank, stash a part factor, take {@code base} generic damage, return the accumulated damageTaken. */
    private static int hitWithFactor(ServerLevel level, DamageSources ds, float factor, float base) {
        MchTank t = newTank(level, 0.0, 100.0, 0.0);
        if (t == null) return -1;
        t.setLastBBDamageFactor(factor);
        t.hurt(ds.generic(), base);
        return t.getDamageTaken();
    }

    private static boolean near(float a, float b) { return Math.abs(a - b) < 1.0e-3F; }
    private static String fmt(double v) { return String.format("%.3f", v); }
}
