package mcheli.dependent.entity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import mcheli.agnostic.weapon.MCH_ExplosionMath;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.dependent.config.MchConfig;
import mcheli.dependent.config.MchServerRules;
import mcheli.dependent.particle.ClientboundExplosionFxPayload;
import mcheli.dependent.port.NeoEntityRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The MCHeli weapon explosion — a port of {@code mcheli.MCH_Explosion}: our own entity phase ({@code doExplosionA}),
 * vanilla's block ray-march for the blocks ({@code doExplosionB}), the reference's explosion sound, and one
 * {@link ClientboundExplosionFxPayload} per nearby player for the visual ({@code MCH_PacketEffectExplosion}).
 *
 * <ul>
 *   <li><b>Entity radius = {@code 2·power}</b> (not the block radius) with the custom {@code floor((t²+t)/2·8·radius+1)}
 *       falloff, the global {@code applyDamageVsEntity} factor and then the weapon's per-victim
 *       {@link MCH_WeaponInfo#getDamageFactor damage factor} ({@link MCH_ExplosionMath}).</li>
 *   <li><b>FAE vs normal vs in-water</b>: FAE does NOT destroy blocks, ignites entities ({@code countSetFireEntity=15})
 *       and ignores the damage factor; normal destroys blocks (gated by the {@code mobGriefing} gamerule and the
 *       server's {@code explosionDestroyBlock}); in water every block's resistance is cut to {@code 20..40%}.</li>
 * </ul>
 *
 * <p>The vanilla {@link Explosion} is built and run directly instead of through {@code ServerLevel.explode}: that
 * would also send vanilla's explode packet, whose sound and centre particle the reference does not have (the sound is
 * the reference's own, skipped for two in three bomblets, and the centre flash is part of the client effect).
 *
 * <p><b>Who is spared and who is credited</b> are separate: {@code exclude} (with everyone aboard it) takes nothing,
 * {@code attacker} is credited with the damage (reference {@code explodedPlayer}). A round passes its launcher as
 * {@code exclude} and its operator as {@code attacker} ({@link MchBullet}: reference {@code shootingAircraft} /
 * {@code shootingEntity}). <b>Player-friendly deviation:</b> the reference spares only the round itself, so a
 * pilot's own rockets could hit his aircraft and crew at close range; here the launcher, its crew and (for a round's
 * blast) the operator are spared, as the round itself never strikes them. A wreck spares nobody and credits its
 * killer, as the reference does.
 */
public final class MchExplosion {
    private MchExplosion() {}

    /** Reference {@code MCH_CommonPacketHandler.onPacketEffectExplosion}: clients draw blasts up to 200 blocks away. */
    private static final double EFFECT_RANGE = 200.0;

    /** Lets vanilla break blocks while we run our own (faithful) entity damage AND knockback. Vanilla's knockback is
     *  applied outside the {@code shouldDamageEntity} gate, so it must be zeroed separately or every entity gets
     *  launched twice (and the launcher/operator exclusion is defeated). */
    private static final ExplosionDamageCalculator NO_VANILLA_ENTITY_DAMAGE = new ExplosionDamageCalculator() {
        @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }
        @Override public float getKnockbackMultiplier(Entity entity) {
            return 0.0F;
        }
    };

    /** {@link #NO_VANILLA_ENTITY_DAMAGE} for a blast in water: reference {@code doExplosionA} multiplies each block's
     *  resistance by {@code rand·0.2+0.2} when {@code isInWater} (water itself resists 100, so a dry-rules blast
     *  breaks next to nothing underwater). */
    private static final ExplosionDamageCalculator NO_VANILLA_ENTITY_DAMAGE_IN_WATER = new ExplosionDamageCalculator() {
        @Override public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter getter,
                                                                     BlockPos pos, BlockState state, FluidState fluid) {
            return super.getBlockExplosionResistance(explosion, getter, pos, state, fluid).map(r ->
                r * MCH_ExplosionMath.waterResistanceMul(java.util.concurrent.ThreadLocalRandom.current().nextFloat()));
        }
        @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }
        @Override public float getKnockbackMultiplier(Entity entity) {
            return 0.0F;
        }
    };

    /**
     * A blast that is not a round's own (a vehicle wreck or crash, the destruct charge, the self-tests): dry, with the
     * sound, no hit marker, and {@code attacker} credited without being spared (the reference's aircraft and plastic
     * bomb call {@code MCH_Explosion.newExplosion(world, exploder, player, ...)} directly, never a round's
     * {@code newExplosion}).
     *
     * @param power        entity-damage power (= {@code info.explosion}); radius is {@code 2·power}
     * @param sizeBlock    block-destruction strength (= {@code info.explosionBlock})
     * @param flaming      ignite flammable blocks
     * @param destroyBlock break blocks (false for FAE) — still gated by the {@code mobGriefing} gamerule
     * @param fireCount    seconds-of-fire scaler for direct entity ignition (15 for FAE, 0 normal)
     * @param damageInfo   the weapon (for the per-victim damage factor); null = factor 1.0 (FAE passes null)
     * @param exclude      spared, with everyone aboard it; null spares nobody
     * @param attacker     credited with the damage; null credits nobody
     */
    public static void explode(ServerLevel level, Vec3 center, int power, int sizeBlock, boolean flaming,
                               boolean destroyBlock, int fireCount, MCH_WeaponInfo damageInfo, Entity exclude,
                               Entity attacker) {
        explode(level, center, power, sizeBlock, flaming, destroyBlock, fireCount, damageInfo, exclude, attacker,
            false, true, false);
    }

    /**
     * Detonate at {@code center} — reference {@code MCH_Explosion.newExplosion} / {@code newExplosionInWater}.
     *
     * @param exclude    spared, with everyone aboard it: a round's launcher (reference {@code shootingAircraft}), the
     *                   vehicle a destruct charge blows up; null spares nobody
     * @param attacker   credited with the damage, the block grief and the sculk event (reference
     *                   {@code explodedPlayer}): a round's operator (reference {@code shootingEntity}); null credits
     *                   nobody
     * @param inWater    {@code newExplosionInWater}: weakened block resistance and the splash visual
     * @param playSound  the reference plays the sound for every blast except two in three bomblets
     * @param roundBlast a round's own blast ({@link MchBullet}, FAE included): its attacker is the operator who fired
     *                   it, who is spared as the round itself never strikes its shooter ({@code canBeCollidedEntity}),
     *                   and gets the hit marker when the blast reaches anything (reference {@code newExplosion}:
     *                   {@code result.hitEntity} -> {@code notifyHitBullet}). False for the wreck, crash and destruct
     *                   blasts, which credit their attacker without sparing it and report nothing.
     */
    public static void explode(ServerLevel level, Vec3 center, int power, int sizeBlock, boolean flaming,
                               boolean destroyBlock, int fireCount, MCH_WeaponInfo damageInfo, Entity exclude,
                               Entity attacker, boolean inWater, boolean playSound, boolean roundBlast) {
        if (power <= 0 && sizeBlock <= 0) {
            return;
        }
        Entity spared = roundBlast ? attacker : null;
        if (power > 0 && damageEntities(level, center, power, fireCount, damageInfo, exclude, spared, attacker)
            && roundBlast) {
            mcheli.dependent.control.ClientboundHitMarkPayload.sendTo(attacker);
        }

        // Blocks via vanilla (its entity damage is suppressed above). Reference doExplosionB: blocks break only when
        // isDestroyBlock AND explosionSizeBlock > 0 AND the server's Explosion_DestroyBlock; the ray-march itself runs
        // at the block strength, and fire goes into its air cells (one in three, on an opaque floor) when the round is
        // flaming AND Explosion_FlamingBlock — even for a blast that breaks nothing.
        boolean vanillaRan = false;
        if (sizeBlock > 0) {
            boolean breakBlocks = destroyBlock && MchConfig.SERVER.explosionDestroyBlock();
            boolean fire = flaming && MchConfig.SERVER.explosionFlamingBlock();
            // Reference isSmoking = the mobGriefing gamerule (EventHooks.canEntityGrief also lets mods veto it).
            Explosion.BlockInteraction interaction = breakBlocks && EventHooks.canEntityGrief(level, attacker)
                ? (level.getGameRules().getBoolean(GameRules.RULE_MOB_EXPLOSION_DROP_DECAY)
                    ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.DESTROY)
                : Explosion.BlockInteraction.KEEP;
            if (interaction != Explosion.BlockInteraction.KEEP || fire) {
                vanillaRan = true;
                Explosion explosion = new Explosion(level, attacker, level.damageSources().explosion(attacker, attacker),
                    inWater ? NO_VANILLA_ENTITY_DAMAGE_IN_WATER : NO_VANILLA_ENTITY_DAMAGE,
                    center.x, center.y, center.z, sizeBlock, fire, interaction,
                    ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, SoundEvents.GENERIC_EXPLODE);
                if (!EventHooks.onExplosionStart(level, explosion)) {
                    explosion.explode();
                    explosion.finalizeExplosion(false); // server side: breaks blocks and lights fires, no sound
                }
            }
        }
        if (!vanillaRan) {
            level.gameEvent(attacker, GameEvent.EXPLODE, center); // what Explosion.explode() would have told sculk
        }

        if (playSound) {
            playExplosionSound(level, center);
        }

        // The visual: one small packet per player in range; each client builds the effect. The reference picks the
        // effect by inWater alone; a blast centred in water also gets the splash here, because a fireball and
        // burning debris under the sea look absurd and the 46cm naval gun mostly hits the sea.
        boolean wetVisual = inWater || level.getFluidState(BlockPos.containing(center)).is(FluidTags.WATER);
        PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, EFFECT_RANGE,
            new ClientboundExplosionFxPayload(center.x, center.y, center.z, power, wetVisual));
    }

    /** Reference {@code doExplosionA}'s entity loop. Each victim is hurt ONCE: a multipart mob's parts (NeoForge
     *  {@link PartEntity}) all fall in the box, so the hit is resolved against its most exposed part (the largest
     *  impact factor) and dealt to the mob itself. Returns whether the blast reached anything but an item, an
     *  experience orb or bottle or a falling block (reference {@code result.hitEntity}, which drives the hit marker). */
    private static boolean damageEntities(ServerLevel level, Vec3 center, int power, int fireCount,
                                          MCH_WeaponInfo damageInfo, Entity exclude, Entity spared, Entity attacker) {
        float radius = MCH_ExplosionMath.entityRadius(power);
        AABB box = new AABB(center.x, center.y, center.z, center.x, center.y, center.z).inflate(radius + 1.0);
        Map<Entity, Hit> hits = new LinkedHashMap<>();
        for (Entity ent : level.getEntitiesOfClass(Entity.class, box, e -> canAffect(e, exclude, spared))) {
            Entity victim = ent instanceof PartEntity<?> part ? part.getParent() : ent;
            if (victim != ent && !canAffect(victim, exclude, spared)) {
                continue;
            }
            Vec3 ep = ent.getBoundingBox().getCenter();
            double dist = center.distanceTo(ep);
            if (dist > radius) {
                continue;
            }
            float t = MCH_ExplosionMath.impactFactor(dist, radius, Explosion.getSeenPercent(center, ent));
            Hit prev = hits.get(victim);
            if (prev == null || t > prev.t) {
                hits.put(victim, new Hit(t, dist, ep));
            }
        }

        DamageSource src = level.damageSources().explosion(attacker, attacker);
        boolean hitEntity = false;
        for (Map.Entry<Entity, Hit> e : hits.entrySet()) {
            Entity victim = e.getKey();
            Hit h = e.getValue();
            hitEntity |= !(victim instanceof net.minecraft.world.entity.item.ItemEntity
                || victim instanceof net.minecraft.world.entity.ExperienceOrb
                || victim instanceof net.minecraft.world.entity.projectile.ThrownExperienceBottle
                || victim instanceof net.minecraft.world.entity.item.FallingBlockEntity);
            // Reference: even a fully shielded victim inside the radius takes the formula's minimum of 1.
            float damage = MCH_ExplosionMath.explosionDamage(h.t, radius);
            damage = MchServerRules.applyDamageVsEntity(victim, damage);
            // The weapon's DamageFactor, from the victim itself (never a part), as MchBullet does.
            damage *= damageInfo != null ? damageInfo.getDamageFactor(new NeoEntityRef(victim)) : 1.0F;
            applyHurtResistantTime(victim);
            victim.hurt(src, damage);

            // Reference knockback: 0.4/0.1/0.4 of t along the push, reduced by Blast Protection. The reference's
            // EnchantmentProtection.func_92092_a floors the reduction, which zeroes it below level 7; 1.21 expresses
            // Blast Protection as the explosion-knockback-resistance attribute, so that is what is applied here.
            Vec3 dir = h.pos.subtract(center);
            if (dir.lengthSqr() > 1.0e-8) {
                double k = h.t;
                if (victim instanceof LivingEntity le) {
                    k *= 1.0 - le.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
                }
                Vec3 n = dir.normalize();
                victim.push(n.x * k * MCH_ExplosionMath.KNOCKBACK_HORIZONTAL,
                    n.y * k * MCH_ExplosionMath.KNOCKBACK_VERTICAL,
                    n.z * k * MCH_ExplosionMath.KNOCKBACK_HORIZONTAL);
                if (victim instanceof ServerPlayer p && !(p.isCreative() && p.getAbilities().flying)) {
                    p.hurtMarked = true; // a player's motion is client-side: send the push even if the hurt was refused
                }
            }
            if (damage > 0.0F && fireCount > 0) {
                // Reference setFire((int)(fireFactor * countSetFireEntity)): whole seconds, never shortening a fire.
                int sec = (int) MCH_ExplosionMath.fireSeconds(h.dist, radius, fireCount);
                if (sec > 0) {
                    victim.igniteForSeconds(sec);
                }
            }
        }
        return hitEntity;
    }

    /** Reference {@code playExplosionSound} / {@code doExplosionB}: "random.explode" at volume 4 (heard 64 blocks
     *  out), pitch {@code (1 + (r - r)·0.2)·0.7}. Also the whole effect of a round whose {@code Explosion} is negative. */
    public static void playExplosionSound(ServerLevel level, Vec3 at) {
        net.minecraft.util.RandomSource r = level.getRandom();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4.0F,
            (1.0F + (r.nextFloat() - r.nextFloat()) * 0.2F) * 0.7F);
    }

    /** One victim's strongest hit: the impact factor, the distance and the struck part's centre. */
    private record Hit(float t, double dist, Vec3 pos) {}

    /**
     * Reference {@code MCH_Lib.applyEntityHurtResistantTimeConfig}: a living victim's remaining hurt immunity is scaled
     * by the server's {@code hurtResistantTime} (0 by default, so every MCHeli hit lands and stacks with a same-tick
     * direct hit). Other entities keep the port's zeroing, which MCHeli vehicles rely on.
     */
    static void applyHurtResistantTime(Entity victim) {
        if (victim instanceof LivingEntity) {
            victim.invulnerableTime = (int) (MchConfig.SERVER.hurtResistantTime() * victim.invulnerableTime);
        } else {
            victim.invulnerableTime = 0;
        }
    }

    /** What a blast may touch: not other in-flight rounds, not {@code exclude} or anyone aboard it, not
     *  {@code spared} (a round's operator; null for any other blast). */
    private static boolean canAffect(Entity e, Entity exclude, Entity spared) {
        if (e instanceof MchBullet || e.isSpectator() || !e.isAlive()) {
            return false;
        }
        if (exclude != null && (e == exclude || exclude.getPassengers().contains(e) || e.getVehicle() == exclude)) {
            return false;
        }
        return spared == null || e != spared;
    }
}
