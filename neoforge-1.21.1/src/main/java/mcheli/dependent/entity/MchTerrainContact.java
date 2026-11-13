package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.physics.MCH_BoundingBox;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What the hull touches, server side: the snow / lily pad / cake light sweep (every category), the tank and car block
 * crush and entity ram (reference {@code MCH_EntityTank.updateCollisionBox}), the attitude-crash and water-submersion
 * damage (reference {@code onUpdate_CollisionGroundDamage}), and the terrain diagnostic. Stateless apart from the
 * back-reference to its vehicle; the per-part boxes come from {@link MchPartHitboxes#boxes()}. Writes no synced
 * accessor. The per-category rules stay entity hooks: {@link AbstractMchVehicle#crushWeightType()},
 * {@link AbstractMchVehicle#giveDamageRot()} and {@link AbstractMchVehicle#solidBlockBelow()}.
 */
final class MchTerrainContact {
    private final AbstractMchVehicle v;

    MchTerrainContact(AbstractMchVehicle v) {
        this.v = v;
    }

    /** SERVER, post-move (reference onUpdate_updateBlock + updateCollisionBox, MCH_EntityTank:724-725): the light sweep,
     *  then the tank crush/ram pass. {@code grief} is the caller's one Collision_DestroyBlock + mobGriefing answer. */
    void tickServer(boolean grief) {
        updateBlockLight(grief);
        updateCollisionBox(grief);
    }

    // ---- environmental / crash damage (reference onUpdate_CollisionGroundDamage) ----

    /** Attitude-crash (tilted steeply near the ground) + water-submersion damage, alive craft only (server). */
    void collisionGroundDamage() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (v.solidBlockBelow()) {
            float roll = Math.abs(Mth.wrapDegrees(v.getRollAngle()));
            float pitch = Math.abs(Mth.wrapDegrees(v.getXRot()));
            if (roll > v.giveDamageRot() || pitch > v.giveDamageRot()) {              // reference getGiveDamageRot()
                float dmg = roll + pitch;
                double dist = v.position().distanceTo(new Vec3(v.xo, v.yo, v.zo)); // distance moved this tick
                dmg = dmg < 90.0F ? (float) (dmg * 0.4F * dist) : dmg * 0.4F;
                if (dmg > 1.0F && v.getRandom().nextInt(4) == 0) {
                    v.hurt(v.damageSources().inWall(), dmg);
                }
            }
        }
        if (v.tickCount % 30 == 0 && (info == null || !info.isFloat) && waterAtProbe()) {
            v.hurt(v.damageSources().inWall(), Math.max(1, v.getMaxHp() / 10));
        }
    }

    /** Water at the submersion probe point {@code y = posY + 1.5 + submergedDamageHeight} or its 6 neighbors
     *  (reference {@code MCH_Lib.isBlockInWater}). {@code (int)} casts match the reference (not {@code Mth.floor}). */
    private boolean waterAtProbe() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        int bx = (int) (v.getX() + 0.5);
        int by = (int) (v.getY() + 1.5 + (info != null ? info.submergedDamageHeight : 0.0F));
        int bz = (int) (v.getZ() + 0.5);
        if (by <= v.level().getMinBuildHeight()) return false;
        int[][] off = {{0, 0, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}};
        for (int[] o : off) {
            if (v.level().getFluidState(new BlockPos(bx + o[0], by + o[1], bz + o[2])).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    // ---- terrain interaction: the light sweep (all categories) + the tank crush/ram ----

    /** Reference {@code MCH_EntityAircraft.onUpdate_updateBlock} (:2574-2592): four corner columns at ±0.4, two Y
     *  levels. Snow layer vanishes silently, lily pad / cake break without drops. Runs on EVERY category, every
     *  server tick — MCH_EntityHeli:776, MCP_EntityPlane:718, MCH_EntityTank:724, MCH_EntityVehicle:370 all call it. */
    private void updateBlockLight(boolean grief) {
        if (!grief) {
            return;
        }
        for (int l = 0; l < 4; l++) {
            int bx = Mth.floor(v.getX() + ((l % 2) - 0.5) * 0.8);
            int bz = Mth.floor(v.getZ() + ((l / 2) - 0.5) * 0.8);
            for (int k = 0; k < 2; k++) {
                lightSweepAt(bx, Mth.floor(v.getY()) + k, bz);
            }
        }
    }

    /** One cell of the light sweep. Shared with the tank's per-wheel pass (reference {@code MCH_WheelManager.updateBlock}
     *  :207-224 applies the identical snow/lily/cake rule under each wheel). Callers own the grief gate. */
    private void lightSweepAt(int bx, int by, int bz) {
        if (by < v.level().getMinBuildHeight() || by >= v.level().getMaxBuildHeight()) {
            return;
        }
        BlockPos p = new BlockPos(bx, by, bz);
        if (!v.level().isLoaded(p)) {
            return;
        }
        BlockState s = v.level().getBlockState(p);
        if (s.is(Blocks.SNOW)) {
            v.level().removeBlock(p, false);
        } else if (s.is(Blocks.LILY_PAD) || s.is(Blocks.CAKE)) {
            v.level().destroyBlock(p, false, v);
        }
    }

    /** Diagnostic hook for the headless self-tests: the resolved config plus the terrain solve's current output. */
    String debugTerrainState() {
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null) {
            return "<no info>";
        }
        double wcz = info instanceof mcheli.agnostic.tank.MCH_TankInfo ti ? ti.weightedCenterZ : 0.0;
        var tilt = mcheli.agnostic.sim.WheelTerrainSampler.sample(v.ref, info, wcz);
        return info.name + " wheels=" + (info.wheels == null ? -1 : info.wheels.size())
            + " wt=" + v.crushWeightType() + " tickCount=" + v.tickCount
            + (tilt == null ? " tilt=<null>"
               : " targetPitch=" + tilt.targetPitch() + " targetRoll=" + tilt.targetRoll());
    }

    /** Reference {@code MCH_EntityTank.updateCollisionBox} (:911-932). Unconditional per tick — NOT speed-gated and
     *  NOT contact-gated; the speed gate lives inside {@link #collisionEntity} only. */
    private void updateCollisionBox(boolean grief) {
        int wt = v.crushWeightType();
        if (wt < 0) {
            return;
        }
        MCH_AircraftInfo info = v.weaponHostInfo();
        if (info == null) {
            return; // reference early-returns from updateCollisionBox AND destoryBlockRange when getAcInfo() == null
        }

        // 1. Per-wheel light sweep. The reference drives real wheel ENTITIES; the port transforms each config wheel
        //    directly. createWheels MIRRORS the list in X (`i % 2 == 0 ? wp.x : -wp.x`, MCH_WheelManager:52), so both
        //    track sides must be swept — iterating info.wheels alone would only ever touch the +X side.
        if (grief && wt > 0 && info.wheels != null) {
            for (MCH_AircraftInfo.Wheel w : info.wheels) {
                for (int side = 0; side < 2; side++) {
                    double lx = side == 0 ? w.pos.x() : -w.pos.x();
                    Vec3 v = transformLocal(lx, w.pos.y(), w.pos.z());
                    // Reference (MCH_WheelManager.updateBlock:273-275) uses (int) casts, kept for X/Z. Y is floored:
                    // the 1.7.10 world never went below 0, where (int) rounds toward zero and would sweep one row
                    // too high in a 1.21 world; for y >= 0 the two agree.
                    lightSweepAt((int) (v.x + 0.5), Mth.floor(v.y + 0.5), (int) (v.z + 0.5));
                }
            }
        }

        // 2. Per-part boxes, 1-in-3 per box per tick — the SAME gate covers crush AND ram (reference :915-924).
        if (v.hitboxes().boxes() != null) {
            for (MCH_BoundingBox bb : v.hitboxes().boxes()) {
                if (v.getRandom().nextInt(3) != 0) {
                    continue;
                }
                if (grief && wt > 0) {
                    destroyBlockRange(bb.nowPos.x(), bb.nowPos.y(), bb.nowPos.z(), bb.width, bb.height, wt);
                }
                collisionEntity(MchVehicleMath.mcBox(bb), wt);
            }
        }

        // 3. Hull, ungated (reference :926-930): w = width * 1.5, h = height * 2.0, from the entity's LIVE size, which
        //    getDimensions() takes from the config Width/Height (the reference's onAcInfoReloaded setSize).
        if (grief && wt > 0) {
            destroyBlockRange(v.getX(), v.getY(), v.getZ(), v.getBbWidth() * 1.5F, v.getBbHeight() * 2.0F, wt);
        }
        collisionEntity(v.getBoundingBox(), wt);
    }

    /** Reference {@code destoryBlockRange} (:934-985). The loop bounds are exact, including the integer division of
     *  the truncated double and the asymmetric {@code +1} on the top Y. There is deliberately no hardness or
     *  blast-resistance test — {@link MchCrushableBlocks} IS the rule. X and Z keep the reference's {@code (int)}
     *  truncation; Y is floored, because the reference bounded {@code by} to 0..255 and so never truncated a negative
     *  Y, while a 1.21 world goes below 0 (identical for {@code y >= 0}). */
    private void destroyBlockRange(double vx, double vy, double vz, double w, double h, int wt) {
        int ws = (int) (w + 2.0) / 2;
        int hs = (int) (h + 2.0) / 2;
        int minY = v.level().getMinBuildHeight();
        int maxY = v.level().getMaxBuildHeight();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -ws; x <= ws; x++) {
            for (int z = -ws; z <= ws; z++) {
                for (int y = -hs; y <= hs + 1; y++) {
                    int bx = (int) (vx + x - 0.5);
                    int by = Mth.floor(vy + y - 1.0);
                    int bz = (int) (vz + z - 0.5);
                    if (by < minY || by >= maxY) {
                        continue; // reference bound was the 1.7.10 world, 0 <= by < 256
                    }
                    p.set(bx, by, bz);
                    if (!v.level().isLoaded(p)) {
                        break; // never load a chunk just to crush it
                    }
                    BlockState s = v.level().getBlockState(p);
                    if (s.isAir()) {
                        continue;
                    }
                    if (MchCrushableBlocks.noBreak(s, wt)) {
                        break; // a protected block shields the rest of the column
                    }
                    if (MchCrushableBlocks.explicitBreak(s, wt)) {
                        crushOne(p);
                        break;
                    }
                    if (MchCrushableBlocks.materialBreak(s, wt)) {
                        crushOne(p);
                    }
                }
            }
        }
    }

    /** Reference {@code MCH_EntityTank.destroyBlock} (:987-993): 1-in-8 drops its items and plays the break effect,
     *  the other 7-in-8 vanish silently — which is what keeps a tank from carpeting the ground in item entities. */
    private void crushOne(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (v.getRandom().nextInt(8) == 0) {
            v.level().destroyBlock(immutable, true, v);
        } else {
            v.level().removeBlock(immutable, false);
        }
    }

    /** Body-local → world at the CURRENT (non-interpolated) pose — reference {@code getTransformedPosition}. Uses the
     *  agnostic rotate chain rather than JOML so these points agree bit-for-bit with {@code MCH_BoundingBox.nowPos}
     *  and with {@code WheelTerrainSampler.transform}. ({@link MchRiderPose#bodyRotation} is render-side and
     *  interpolated.) The local {@code v} shadows the vehicle field, so the vehicle is {@code this.v} here. */
    private Vec3 transformLocal(double lx, double ly, double lz) {
        mcheli.agnostic.value.Vec3d v = new mcheli.agnostic.value.Vec3d(lx, ly, lz)
            .rotateAroundZ(-this.v.getRollAngle() / 180.0F * (float) Math.PI)
            .rotateAroundX(-this.v.getXRot() / 180.0F * (float) Math.PI)
            .rotateAroundY(-this.v.getYRot() / 180.0F * (float) Math.PI);
        return new Vec3(this.v.getX() + v.x(), this.v.getY() + v.y(), this.v.getZ() + v.z());
    }

    /** Reference {@code MCH_EntityTank.collisionEntity} (:805-886): ram damage {@code |motion| * 15} plus a proximity
     *  bonus, knockback, and car self-damage. Tanks only (gated by {@link AbstractMchVehicle#crushWeightType()}). */
    private void collisionEntity(AABB box, int wt) {
        if (box == null) {
            return;
        }
        Vec3 m = v.getDeltaMovement();
        double speed = m.length();
        if (speed <= 0.05) {
            return; // reference gate: a parked tank rams nothing
        }
        float damage = (float) (speed * 15.0);
        Entity rider = v.pilot();
        for (Entity e : v.level().getEntities(v, box.inflate(0.3, 0.3, 0.3), this::ramTarget)) {
            double dist = Math.min(Math.hypot(e.getX() - v.getX(), e.getZ() - v.getZ()), 5.0);
            damage += (float) (5.0 - dist); // reference ACCUMULATES across targets within one call — kept
            DamageSource ds = rider instanceof LivingEntity lr
                ? v.damageSources().mobAttack(lr) : v.damageSources().generic();
            // Reference MCH_Lib.applyEntityHurtResistantTimeConfig (:166-172): scale a LIVING target's i-frames by the
            // server option HurtResistantTime, which DEFAULTS TO 0.0 (MCH_Config.java:312), i.e. it clears them, so the
            // per-tick ram always lands; vanilla's 10-tick invulnerability would otherwise swallow ~9 of every 10 hits.
            if (e instanceof LivingEntity) {
                e.invulnerableTime = (int) (mcheli.dependent.config.MchConfig.SERVER.hurtResistantTime() * e.invulnerableTime);
            }
            e.hurt(ds, damage);
            if (e instanceof AbstractMchVehicle) {
                e.setDeltaMovement(e.getDeltaMovement().add(m.x * 0.05, 0.0, m.z * 0.05));
            } else if (e instanceof AbstractArrow) {
                e.discard();
            } else {
                e.setDeltaMovement(e.getDeltaMovement().add(m.x * 1.5, 0.0, m.z * 1.5));
            }
            if (wt != MchCrushableBlocks.WEIGHT_TANK && (e.getBbWidth() >= 1.0F || e.getBbHeight() >= 1.5F)) {
                // A CAR hurts itself ramming anything substantial; a real tank never does.
                DamageSource back = e instanceof LivingEntity le
                    ? v.damageSources().mobAttack(le) : v.damageSources().generic();
                v.hurt(back, damage / 3.0F);
            }
        }
    }

    /** Reference ram selector + {@code shouldCollisionDamage} (MCH_EntityTank:818-838, :888-905): never ourselves, our own
     *  passengers, the craft we are riding, dropped items/XP, our own projectiles. Another heavy tank is rammed only
     *  under the server option {@code Collision_EntityTankDamage} (default false, MCH_Config.java:267), everything else
     *  under {@code Collision_EntityDamage} (default true, :266).
     *  <p>Also never a 1.21-only entity state the 1.7.10 selector could not meet: a spectator (an invisible player
     *  that would otherwise take the ram, feed the {@code 5 - dist} bonus and make a car hurt itself), a marker armor
     *  stand, or the bodiless {@code Marker} / {@code Display} / {@code Interaction} utility entities. */
    private boolean ramTarget(Entity e) {
        if (e == v || e == v.getVehicle() || e.getVehicle() == v || v.getPassengers().contains(e)) {
            return false;
        }
        if (e.isSpectator() || e instanceof net.minecraft.world.entity.Marker
            || e instanceof net.minecraft.world.entity.Display || e instanceof net.minecraft.world.entity.Interaction
            || (e instanceof net.minecraft.world.entity.decoration.ArmorStand as && as.isMarker())) {
            return false;
        }
        if (e instanceof ItemEntity || e instanceof ExperienceOrb
            || e instanceof MchBullet || e instanceof MchCartridge || e instanceof MchFlare) {
            return false;
        }
        if (e instanceof MchTank t && t.crushWeightType() == MchCrushableBlocks.WEIGHT_TANK) {
            return mcheli.dependent.config.MchConfig.SERVER.collisionEntityTankDamage();
        }
        return mcheli.dependent.config.MchConfig.SERVER.collisionEntityDamage();
    }
}
