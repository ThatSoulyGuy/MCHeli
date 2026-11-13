package mcheli.agnostic.weapon;

/**
 * Pure missile-guidance vector math — the port of {@code MCH_EntityBaseBullet.guidanceToTarget} (the 6:1 pursuit
 * blend), the AT direct hard-snap and top-attack profile ({@code MCH_EntityATMissile.onUpdateMotion} guidanceType 0 and
 * 1), the AS ground-point steering ({@code MCH_EntityASMissile.onUpdate}) and the guided-torpedo run
 * ({@code MCH_EntityTorpedo.onUpdateGuided}). Operates on plain {@code double} triples so it never depends on a
 * Minecraft {@code Vec3}; the dependent {@code MchBullet} resolves the live positions and feeds the scalars in. ZERO
 * Minecraft imports.
 *
 * <p><b>Fidelity:</b> the reference {@code guidanceToTarget} NEVER reads its {@code accelerationFactor} argument — it
 * is pure pursuit with a fixed turn rate (no proportional-navigation lead), so a fast crossing target can out-turn the
 * missile exactly as in the reference. {@link #guide} preserves that; only the AT-direct {@link #hardSnap} applies
 * the ramp factor {@code af}.
 */
public final class MCH_MissileGuidance {

    private MCH_MissileGuidance() {}

    /** A plain 3-vector guidance result (keeps the sim free of any Minecraft vector type). */
    public static final class Motion {
        public final double x;
        public final double y;
        public final double z;
        public Motion(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    /**
     * Pure-pursuit blend toward the target (reference {@code guidanceToTarget}): {@code desired = unit(target-pos) *
     * speed}; {@code newMotion = (motion*6 + desired)/7}. {@code speed} is the missile's cruise speed (the reference
     * {@code this.acceleration}). Used by AA homing and the AT top-attack phases ({@link #topAttackAim}).
     */
    public static Motion guide(double mx, double my, double mz, double px, double py, double pz,
                               double tx, double ty, double tz, double speed) {
        double dx = tx - px, dy = ty - py, dz = tz - pz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1.0e-6) {
            return new Motion(mx, my, mz);
        }
        double desiredX = dx * speed / d;
        double desiredY = dy * speed / d;
        double desiredZ = dz * speed / d;
        return new Motion((mx * 6.0 + desiredX) / 7.0, (my * 6.0 + desiredY) / 7.0, (mz * 6.0 + desiredZ) / 7.0);
    }

    /**
     * AT direct hard-snap (reference guidanceType 0): {@code motion = unit(target-pos) * speed * af} — an instant
     * heading change toward the target, scaled by the ramp factor {@code af} (0.5 during the initial rigidity boost,
     * 1.0 afterwards).
     */
    public static Motion hardSnap(double px, double py, double pz, double tx, double ty, double tz,
                                  double speed, float af) {
        double dx = tx - px, dy = ty - py, dz = tz - pz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1.0e-6) {
            return new Motion(0.0, 0.0, 0.0);
        }
        return new Motion(dx * speed / d * af, dy * speed / d * af, dz * speed / d * af);
    }

    /**
     * The point an AT top-attack missile steers at this tick (reference {@code MCH_EntityATMissile.onUpdateMotion}
     * guidanceType 1, applied once {@code age > rigidity}): for 20 ticks it climbs toward a point 150 blocks above the
     * SHOOTER's altitude over the target, for 10 more it levels off at the shooter's altitude, then it dives on the
     * target. Each phase steers with {@link #guide}. Returns {x, y, z}.
     */
    public static double[] topAttackAim(int age, int rigidity, double tx, double ty, double tz, double shooterY) {
        if (age <= rigidity + 20) {
            return new double[]{tx, shooterY + 150.0, tz};
        }
        if (age <= rigidity + 30) {
            return new double[]{tx, shooterY, tz};
        }
        return new double[]{tx, ty, tz};
    }

    /** The tick a top-attack dive arms: the reference raises the round's power by 20 % and its explosion by one at
     *  {@code countOnUpdate == rigidityTime + 35}. */
    public static boolean isTopAttackDiveTick(int age, int rigidity) {
        return age == rigidity + 35;
    }

    /**
     * AS missile steering toward its ground point (reference {@code MCH_EntityASMissile.onUpdate}). A gravity-free
     * missile snaps its heading at the point, aiming 20 blocks above it for the first 10 ticks so it clears the
     * launcher's surroundings; a gliding (gravity) missile steers only horizontally and keeps its falling
     * {@code motionY} (the reference flattens the vertical term to 30 % for the normalisation only).
     *
     * @param ballistic the round has a non-zero gravity (the reference {@code getGravity() != 0})
     */
    public static Motion asMissile(double mx, double my, double mz, double px, double py, double pz,
                                   double tx, double ty, double tz, double speed, boolean ballistic, int age) {
        if (!ballistic) {
            double up = age < 10 ? 20.0 : 0.0;
            return hardSnap(px, py, pz, tx, ty + up, tz, speed, 1.0F);
        }
        double x = tx - px;
        double y = (ty - py) * 0.3;
        double z = tz - pz;
        double d = Math.sqrt(x * x + y * y + z * z);
        if (d < 1.0e-6) {
            return new Motion(mx, my, mz);
        }
        return new Motion(x * speed / d, my, z * speed / d);
    }

    /** A torpedo's underwater cruise-speed ramp (reference {@code MCH_EntityTorpedo.onUpdateNoGuided}/{@code Guided}):
     *  +0.1 per tick below {@code accelerationInWater}, -0.1 per tick above it plus a 0.2 dead band. */
    public static double torpedoSpeed(double speed, double accelerationInWater) {
        if (speed < accelerationInWater) {
            return speed + 0.1;
        }
        if (speed > accelerationInWater + 0.2) {
            return speed - 0.1;
        }
        return speed;
    }
}
