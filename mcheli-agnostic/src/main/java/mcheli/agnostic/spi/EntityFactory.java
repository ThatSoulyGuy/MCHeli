package mcheli.agnostic.spi;

import mcheli.agnostic.value.Vec3d;

/**
 * Constructs and spawns concrete mcheli projectile entities, hiding their dependent {@code Entity}
 * constructors. Breaks the apparent agnostic→dependent cycle: a projectile's flight LOGIC is agnostic;
 * its {@code Entity} SHELL is dependent and delegates {@code tick()} back to that logic.
 * <p>Signature will firm up during weapon coercion (build order Phase 4).
 */
public interface EntityFactory {
    EntityRef spawnProjectile(ProjectileKind kind, EntityRef owner, Vec3d pos, Vec3d motion);

    enum ProjectileKind { BULLET, AA_MISSILE, AT_MISSILE, AS_MISSILE, TV_MISSILE, BOMB, ROCKET, TORPEDO, CARTRIDGE, SMOKE, MARKER }
}
