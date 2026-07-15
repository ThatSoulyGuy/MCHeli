package mcheli.dependent.entity;

import mcheli.agnostic.weapon.MCH_ExplosionMath;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.dependent.port.NeoEntityRef;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * The MCHeli weapon explosion — a faithful port of {@code mcheli.MCH_Explosion.doExplosionA}'s entity phase, with
 * vanilla handling only blocks + particles + sound. The two things vanilla's explosion can't express are preserved:
 *
 * <ul>
 *   <li><b>Entity radius = {@code 2·power}</b> (not the block radius) with the custom {@code floor((t²+t)/2·8·radius+1)}
 *       falloff and the per-victim {@link MCH_WeaponInfo#getDamageFactor damage factor} — run in our own loop
 *       ({@link MCH_ExplosionMath}).</li>
 *   <li><b>FAE vs normal vs in-water</b> variant differences: FAE does NOT destroy blocks and ignites entities
 *       ({@code countSetFireEntity=15}) and ignores the damage factor; normal destroys blocks (gated by the
 *       {@code mobGriefing} gamerule, via {@link Level.ExplosionInteraction#MOB}) and applies the damage factor.</li>
 * </ul>
 *
 * <p>Vanilla's own entity damage/knockback is suppressed by {@link #NO_VANILLA_ENTITY_DAMAGE} so it doesn't
 * double-hit — we then apply the faithful damage, the weaker/flatter {@code 0.4/0.1/0.4} knockback, and fire.
 *
 * <p>The rich MCHeli blast visual ({@code effectExplosion}'s scaled fireball + arcing burning debris + smoke cloud) is
 * approximated in {@link #spawnBlastEffect} via {@link ServerLevel#sendParticles}, on top of vanilla's explosion.
 *
 * <p><b>Deferred:</b> the custom 16³ shell block-ray-march (vanilla's block breaking is close enough), the in-water
 * resistance slash + splash particles, blast-protection knockback reduction, and the reference's actual
 * {@code MCH_EntityFlare} debris ENTITIES (the sendParticles lava blobs stand in for them).
 * <b>Player-friendly deviation:</b> the shooter and its own vehicle/passengers are excluded from the blast (so a
 * pilot's own rockets don't nuke them at close range) — the reference does not exclude them.
 */
public final class MchExplosion {
    private MchExplosion() {}

    /** Lets vanilla break blocks + emit particles/sound while we run our own (faithful) entity damage AND knockback.
     *  Vanilla's knockback is applied outside the {@code shouldDamageEntity} gate, so it must be zeroed separately or
     *  every entity gets launched twice (and the shooter-exclusion is defeated). */
    private static final ExplosionDamageCalculator NO_VANILLA_ENTITY_DAMAGE = new ExplosionDamageCalculator() {
        @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }
        @Override public float getKnockbackMultiplier(Entity entity) {
            return 0.0F;
        }
    };

    /**
     * Detonate at {@code center}.
     *
     * @param power        entity-damage power (= {@code info.explosion} or {@code explosionInWater}); radius is {@code 2·power}
     * @param sizeBlock    block-destruction strength (= {@code info.explosionBlock})
     * @param flaming      ignite flammable blocks
     * @param destroyBlock break blocks (false for FAE) — still gated by the {@code mobGriefing} gamerule
     * @param fireCount    seconds-of-fire scaler for direct entity ignition (15 for FAE, 0 normal)
     * @param damageInfo   the weapon (for the per-victim damage factor); null = factor 1.0 (FAE passes null)
     * @param shooter      the firing entity, excluded (with its vehicle/passengers) from the blast
     */
    public static void explode(ServerLevel level, Vec3 center, int power, int sizeBlock, boolean flaming,
                               boolean destroyBlock, int fireCount, MCH_WeaponInfo damageInfo, Entity shooter) {
        if (power <= 0 && sizeBlock <= 0) {
            return;
        }

        if (power > 0) {
            float radius = MCH_ExplosionMath.entityRadius(power);
            AABB box = new AABB(center.x, center.y, center.z, center.x, center.y, center.z).inflate(radius + 1.0);
            DamageSource src = level.damageSources().explosion(shooter, shooter);
            for (Entity ent : level.getEntitiesOfClass(Entity.class, box, e -> canAffect(e, shooter))) {
                Vec3 ep = ent.getBoundingBox().getCenter();
                double dist = center.distanceTo(ep);
                if (dist > radius) {
                    continue;
                }
                float seen = Explosion.getSeenPercent(center, ent);
                float t = MCH_ExplosionMath.impactFactor(dist, radius, seen);
                if (t <= 0.0F) {
                    continue;
                }
                float factor = damageInfo != null ? damageInfo.getDamageFactor(new NeoEntityRef(ent)) : 1.0F;
                Entity victim = ent instanceof PartEntity<?> part ? part.getParent() : ent;
                // Zero the hurt-cooldown so the blast STACKS with a same-tick direct hit (the reference zeroes
                // hurtResistantTime before every weapon hurt); otherwise invuln frames absorb the smaller of the two.
                victim.invulnerableTime = 0;
                victim.hurt(src, MCH_ExplosionMath.explosionDamage(t, radius) * factor);

                Vec3 dir = ep.subtract(center);
                if (dir.lengthSqr() > 1.0e-8) {
                    Vec3 n = dir.normalize();
                    ent.push(n.x * t * MCH_ExplosionMath.KNOCKBACK_HORIZONTAL,
                        n.y * t * MCH_ExplosionMath.KNOCKBACK_VERTICAL,
                        n.z * t * MCH_ExplosionMath.KNOCKBACK_HORIZONTAL);
                }
                if (fireCount > 0) {
                    float sec = MCH_ExplosionMath.fireSeconds(dist, radius, fireCount);
                    if (sec > 0.0F) {
                        ent.setRemainingFireTicks((int) (sec * 20.0F));
                    }
                }
            }
        }

        // Blocks + particles + sound via vanilla (its entity damage is suppressed above). Block destruction happens
        // only when destroyBlock AND sizeBlock>0 (the reference destroys nothing when explosionBlock==0) — via MOB
        // interaction, which respects the mobGriefing gamerule. Otherwise NONE breaks nothing but still puffs.
        boolean breakBlocks = destroyBlock && sizeBlock > 0;
        float visualRadius = breakBlocks ? sizeBlock : Math.max(1, power);
        level.explode(shooter, level.damageSources().explosion(shooter, shooter), NO_VANILLA_ENTITY_DAMAGE,
            center.x, center.y, center.z, visualRadius, flaming,
            breakBlocks ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);

        spawnBlastEffect(level, center, Math.max(power, sizeBlock));
    }

    /**
     * The rich MCHeli blast visual on top of vanilla's explosion — the port of {@code MCH_Explosion.effectExplosion}'s
     * client particles (a faithful approximation via {@link ServerLevel#sendParticles}, not the reference's
     * per-block ray-march + {@code MCH_EntityFlare} entities): a cluster of scaled explosion emitters for the fireball
     * (the reference's scale-10 {@code hugeexplosion}), lava blobs launched radially as the arcing burning debris (the
     * reference's flares), plus a smoke + flame cloud. Everything scales with {@code size} (= the explosion power), so a
     * small rocket gets a modest puff and the 46cm naval gun gets an enormous blast. No-op below the reference's size-2
     * "hugeexplosion" threshold (small rounds keep the plain vanilla poof).
     */
    private static void spawnBlastEffect(ServerLevel level, Vec3 c, int size) {
        if (size < 2) {
            return;
        }
        net.minecraft.util.RandomSource rand = level.getRandom();
        double r = Math.min(size * 0.25, 4.0);

        // A shell into water throws up a white column, not a fireball (reference effectExplosionInWater) — a lava blast
        // underwater would look absurd, and the 46cm is a naval gun that mostly hits the sea.
        boolean inWater = !level.getFluidState(net.minecraft.core.BlockPos.containing(c.x, c.y, c.z)).isEmpty();
        if (inWater) {
            int col = Math.min(size * 3, 60);
            blast(level, ParticleTypes.CLOUD, c.x, c.y + 0.5, c.z, col, r * 0.5, r, r * 0.5, 0.15);
            blast(level, ParticleTypes.SPLASH, c.x, c.y, c.z, col, r, r * 0.4, r, 0.3);
            int gouts = Math.min(size, 20);
            for (int i = 0; i < gouts; i++) {
                double a = rand.nextDouble() * Math.PI * 2.0;
                double h = 0.1 + rand.nextDouble() * size * 0.03;
                blast(level, ParticleTypes.SPLASH, c.x, c.y, c.z, 0,
                    Math.cos(a) * h, 0.4 + rand.nextDouble() * size * 0.06, Math.sin(a) * h, 1.0);
            }
            return;
        }

        // Fireball: the MCHeli scaled explosion-sheet billboards (MchExplodeParticle, port of MCH_EntityParticleExplode)
        // — big config-scaled sprites, NOT the tiny vanilla emitter, spread over the blast to roll into one huge fire.
        int puffs = Math.min(3 + size / 2, 12);
        double spread = Math.max(1.0, size / 4.0);
        float pSize = Math.min(size < 2 ? 2.0F : (float) size, 10.0F); // reference caps ~16; toned to 10 for playability
        for (int i = 0; i < puffs; i++) {
            double ox = (rand.nextDouble() - 0.5) * spread * 2.0;
            double oy = (rand.nextDouble() - 0.5) * spread;
            double oz = (rand.nextDouble() - 0.5) * spread * 2.0;
            blast(level, new mcheli.dependent.particle.MchExplodeOptions(
                    pSize * (0.6F + rand.nextFloat() * 0.5F), fireColor(rand), 14 + rand.nextInt(12)),
                c.x + ox, c.y + oy, c.z + oz, 0, 0.0, 0.0, 0.0, 0.0);
        }
        // One vanilla EXPLOSION_EMITTER at the core for the bright central flash people expect.
        blast(level, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 0, 0.0, 0.0, 0.0, 0.0);
        // Arcing burning debris (reference MCH_EntityFlare): lava blobs launched radially outward + up; count=0 so each
        // call's (dx,dy,dz) is the launch VELOCITY (they arc + fall under the particle's own gravity).
        int flares = Math.min(size * 2, 40);
        for (int i = 0; i < flares; i++) {
            double a = rand.nextDouble() * Math.PI * 2.0;
            double h = 0.2 + rand.nextDouble() * size * 0.05;
            double vy = 0.25 + rand.nextDouble() * size * 0.06;
            blast(level, ParticleTypes.LAVA, c.x, c.y + 0.2, c.z, 0,
                Math.cos(a) * h, vy, Math.sin(a) * h, 1.0);
        }
        // Smoke cloud + fire core, spread over a size-scaled radius (count>0 => (dx,dy,dz) is the gaussian spread).
        int cloud = Math.min(size * 2, 40);
        blast(level, ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.5, c.z, cloud, r, r * 0.6, r, 0.02);
        blast(level, ParticleTypes.FLAME, c.x, c.y, c.z, cloud, r * 0.7, r * 0.5, r * 0.7, 0.05);

        // Block debris: chips of the ground the blast tore up, flung outward and falling (reference spawnParticleTileDust).
        net.minecraft.core.BlockPos ground = net.minecraft.core.BlockPos.containing(c.x, c.y - 0.5, c.z);
        net.minecraft.world.level.block.state.BlockState hit = level.getBlockState(ground);
        if (!hit.isAir()) {
            int chips = Math.min(size * 4, 70);
            blast(level, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, hit),
                c.x, c.y + 0.3, c.z, chips, r, r, r, 0.6); // count>0: scatter chips over the radius with an outward toss
        }
    }

    /**
     * Send a particle burst with the LONG-DISTANCE flag (512-block reach) to every player — the plain
     * {@link ServerLevel#sendParticles} bulk overload hard-codes {@code longDistance=false} = a 32-block cutoff, so for
     * a long-range naval gun the shell lands beyond it and only vanilla's explosion (64-block reach) was visible. This
     * matches the impact to the shooter no matter where it lands within weapon range.
     */
    private static void blast(ServerLevel level, ParticleOptions type, double x, double y, double z, int count,
                              double dx, double dy, double dz, double speed) {
        for (ServerPlayer p : level.players()) {
            level.sendParticles(p, type, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    /** The reference explode-particle tint (grey-orange fire): base 0.3..0.7, {@code r+0.1}, {@code g+0.05}, opaque. */
    private static int fireColor(net.minecraft.util.RandomSource rand) {
        float base = 0.3F + rand.nextFloat() * 0.4F;
        return 0xFF000000 | (clamp255(base + 0.1F) << 16) | (clamp255(base + 0.05F) << 8) | clamp255(base);
    }

    private static int clamp255(float f) {
        int i = (int) (f * 255.0F);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    /** Exclude other in-flight rounds and the shooter's own craft from the blast. */
    private static boolean canAffect(Entity e, Entity shooter) {
        if (e instanceof MchBullet || e.isSpectator() || !e.isAlive()) {
            return false;
        }
        if (shooter != null && (e == shooter || shooter.getPassengers().contains(e) || e.getVehicle() == shooter)) {
            return false;
        }
        return true;
    }
}
