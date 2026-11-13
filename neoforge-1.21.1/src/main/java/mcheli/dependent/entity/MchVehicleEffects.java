package mcheli.dependent.entity;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.WeaponSlot;
import mcheli.dependent.particle.MuzzleFxOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The particle cosmetics of one vehicle: the client weapon smoke trails and damage smoke (reference
 * {@code onUpdate_Client} / {@code onUpdate_Particle*}), spawned from {@link AbstractMchVehicle#clientAmbientEffects},
 * and the server-broadcast fire cosmetics at the barrel. Stateless: every draw goes through the vehicle's own
 * {@code getRandom()}, in the same order as before.
 */
final class MchVehicleEffects {

    private final AbstractMchVehicle v;

    MchVehicleEffects(AbstractMchVehicle v) {
        this.v = v;
    }

    /**
     * Coloured smoke trails while the PILOT has a smoke weapon selected ({@code smoke_red} ...) and the throttle is up —
     * the port of {@code MCH_EntityAircraft.onUpdate_ParticleSmoke} (:2810-2845). Each mount emits, with a chance that
     * grows with the throttle, {@code SmokeNum} puffs one block behind the mount, blown backward at the weapon's
     * {@code Acceleration}, in the weapon's {@code SmokeColor}, sized by {@code SmokeSize} and living up to
     * {@code SmokeMaxAge} ticks.
     */
    void spawnWeaponSmoke() {
        float throttle = v.getThrottleInput();
        if (throttle <= 0.1F) {
            return;
        }
        WeaponSlot slot = v.selectedHudSlot(0);
        if (slot == null || !"smoke".equalsIgnoreCase(slot.info.type)) {
            return;
        }
        MCH_WeaponInfo wi = slot.info;
        float yaw = v.getYRot();
        float pitch = v.getXRot();
        float roll = v.getRollAngle();
        org.joml.Quaternionf qHull = new org.joml.Quaternionf()
            .rotateY((float) Math.toRadians(-yaw))
            .rotateX((float) Math.toRadians(pitch))
            .rotateZ((float) Math.toRadians(roll));
        for (MCH_AircraftInfo.Weapon m : slot.mounts) {
            // Reference RotVec3(0,0,1, -yaw-180+fixYaw, pitch-fixPitch, roll): straight back, the mount's fixed angles
            // mirrored into the backward frame.
            Vec3 back = MchVehicleMath.aimVector(yaw - m.yaw, pitch - m.pitch, roll).scale(-1.0);
            if (v.getRandom().nextFloat() > throttle * 1.5F) {
                continue;
            }
            org.joml.Vector3f p = new org.joml.Vector3f((float) m.pos.x(), (float) m.pos.y(), (float) m.pos.z());
            qHull.transform(p);
            double x = v.getX() + p.x + back.x;
            double y = v.getY() + p.y + back.y;
            double z = v.getZ() + p.z + back.z;
            for (int i = 0; i < wi.smokeNum; i++) {
                float c = v.getRandom().nextFloat() * 0.05F;
                int age = Math.max(1, (int) (v.getRandom().nextDouble() * wi.smokeMaxAge));
                // Reference size (5..9)*SmokeSize, a diffusible puff that grows from 0.2x to 2x it; MchSmokeOptions
                // takes the half-size (0.1 * particle scale) and grows 2.5x, so start at 0.08 to end at the same 0.2.
                float size = (v.getRandom().nextInt(5) + 5.0F) * wi.smokeSize * 0.08F;
                int argb = MchVehicleMath.packArgb(Math.min(1.0F, wi.color.a + v.getRandom().nextFloat() * 0.05F),
                    Math.min(1.0F, wi.color.r + c), Math.min(1.0F, wi.color.g + c), Math.min(1.0F, wi.color.b + c));
                v.level().addParticle(new mcheli.dependent.particle.MchSmokeOptions(size, argb, age), x, y, z,
                    back.x * wi.acceleration + (v.getRandom().nextDouble() - 0.5) * 0.2,
                    back.y * wi.acceleration + (v.getRandom().nextDouble() - 0.5) * 0.2,
                    back.z * wi.acceleration + (v.getRandom().nextDouble() - 0.5) * 0.2);
            }
        }
    }

    /**
     * Dark smoke from a wounded vehicle — the port of the shared {@code onUpdate_Particle2} (heli
     * {@code MCH_EntityHeli}:677, plane/tank equivalents): only below 50 % HP, with an emission probability that rises
     * as HP falls (reference {@code p < rand/2}, so it stays rare near 50 % and pours out near 0). Grey {@code SMOKE}
     * approximates the reference's {@code 0.2..0.5}-grey {@code smoke.png} billboard. The body of the overridable
     * {@link AbstractMchVehicle#spawnDamageSmoke} hook.
     */
    void baseSpawnDamageSmoke() {
        int maxHp = v.getMaxHp();
        if (maxHp <= 0) {
            return;
        }
        float p = v.getHp() / (float) maxHp;
        if (p > 0.5F || !(p < v.getRandom().nextFloat() / 2.0F)) {
            return; // above 50% HP, or the HP-scaled emission roll didn't fire this tick
        }
        int burst = 2; // the reference emits a trail per rotor; a small burst approximates that density
        for (int i = 0; i < burst; i++) {
            double x = v.getX() + (v.getRandom().nextDouble() - 0.5) * v.getBbWidth();
            double y = v.getY() + v.getBbHeight() * 0.6 + (v.getRandom().nextDouble() - 0.5) * 0.5;
            double z = v.getZ() + (v.getRandom().nextDouble() - 0.5) * v.getBbWidth();
            // Dark damage smoke — the MCHeli smoke.png billboard (port of MCH_EntityParticleSmoke), which rises and
            // lightens as it dissipates, in place of the vanilla LARGE_SMOKE stand-in.
            v.level().addParticle(
                new mcheli.dependent.particle.MchSmokeOptions(0.9F, 0xC0343434, 24 + v.getRandom().nextInt(16)),
                x, y, z,
                (v.getRandom().nextDouble() - 0.5) * 0.3, v.getRandom().nextDouble() * 0.1, (v.getRandom().nextDouble() - 0.5) * 0.3);
        }
    }

    /**
     * Config-driven fire cosmetics at the barrel — nothing hardcoded per weapon:
     * <ul>
     *   <li><b>Muzzle flash</b>: one {@link mcheli.dependent.particle.MchExplodeOptions} flash (the explosion-sheet
     *       billboard, reference {@code spawnParticleExplode}) per {@code AddMuzzleFlash} entry, at that entry's
     *       distance, in its colour dimmed by 0..10%, sized {@code 2·flash.size}, living {@code 1+flash.age+rand(3)}
     *       (so the M230's orange {@code 254,159,84} shows). A weapon with no flash config shows none.</li>
     *   <li><b>Muzzle smoke</b>: {@code num} particles per {@code AddMuzzleFlashSmoke} entry, colour/size/age/count all
     *       from the entry, jittered by its {@code range}.</li>
     *   <li><b>Cartridge</b>: the {@code SetCartridge} model shell, ejected per its config direction/speed.</li>
     * </ul>
     * The reference spawns these client-side; here the server broadcasts them ({@link ServerLevel#sendParticles}, and
     * a cartridge packet from which each client spawns its own casing) so every nearby player sees them.
     */
    void spawnFireCosmetics(Vec3 muzzle, Vec3 dir, MCH_WeaponInfo wi, float gunYaw, float gunPitch) {
        if (v.level() instanceof ServerLevel sl) {
            if (wi.listMuzzleFlash != null) {
                for (MCH_WeaponInfo.MuzzleFlash mf : wi.listMuzzleFlash) {
                    Vec3 p = muzzle.add(dir.scale(mf.dist));
                    float c = v.getRandom().nextFloat() * 0.1F + 0.9F;
                    mcheli.dependent.particle.MchExplodeOptions fx = new mcheli.dependent.particle.MchExplodeOptions(
                        mf.size, MchVehicleMath.packArgb(mf.a, c * mf.r, c * mf.g, c * mf.b), 1 + mf.age + v.getRandom().nextInt(3));
                    sl.sendParticles(fx, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            if (wi.listMuzzleFlashSmoke != null) {
                for (MCH_WeaponInfo.MuzzleFlash mf : wi.listMuzzleFlashSmoke) {
                    Vec3 p = muzzle.add(dir.scale(mf.dist));
                    double spread = mf.range * 0.5;
                    MuzzleFxOptions fx = new MuzzleFxOptions(MchVehicleMath.packArgb(mf.a, mf.r, mf.g, mf.b), 0.1F * mf.size,
                        Math.max(1, mf.age));
                    // Outward drift ~0.2 (reference motionX/Z = rand*±0.2) so the smoke billows instead of sitting.
                    sl.sendParticles(fx, p.x, p.y, p.z, Math.max(1, mf.num), spread, spread * 0.15, spread, 0.2);
                }
            }
        }
        // Cartridge shell — every parameter from the weapon's SetCartridge config.
        if (wi.cartridge != null) {
            MchCartridge.spawn(v.level(), wi.cartridge, muzzle, gunYaw, gunPitch, v.getDeltaMovement());
        }
    }
}
