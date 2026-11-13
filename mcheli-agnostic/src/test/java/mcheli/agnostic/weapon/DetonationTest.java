package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import mcheli.agnostic.weapon.MCH_Detonation.At;
import mcheli.agnostic.weapon.MCH_Detonation.Blast;
import mcheli.agnostic.weapon.MCH_Detonation.Kind;
import org.junit.jupiter.api.Test;

/**
 * Pins the blast-variant choice against {@code MCH_EntityBaseBullet.onImpact} (:771-821) and
 * {@code onUpdateTimeout} (:553-563).
 *
 * <p>The regression these exist for: the port used to send fuse timeouts through the impact rules, so a submerged
 * round with {@code ExplosionInWater = 0} that timed out burst at full air power (the reference makes no blast), and
 * an FAE round that timed out made an FAE blast (the reference's timeout never does).
 */
class DetonationTest {

    @Test
    void timeoutUnderwaterWithoutWaterPowerMakesNoBlast() {
        Blast b = MCH_Detonation.onTimeout(8, 0, 8, true);
        assertEquals(Kind.NONE, b.kind());
    }

    @Test
    void timeoutUnderwaterUsesWaterPowerForBothRadii() {
        Blast b = MCH_Detonation.onTimeout(8, 5, 3, true);
        assertEquals(new Blast(Kind.WATER, 5, 5, At.SELF), b);
    }

    @Test
    void timeoutInAirIsANormalBlastAtTheRoundEvenForFae() {
        assertEquals(new Blast(Kind.NORMAL, 8, 4, At.SELF), MCH_Detonation.onTimeout(8, 0, 4, false));
        assertEquals(Kind.SOUND, MCH_Detonation.onTimeout(-1, 0, 0, false).kind());
        assertEquals(Kind.NONE, MCH_Detonation.onTimeout(0, 0, 0, false).kind());
    }

    @Test
    void impactWithoutWaterPowerIgnoresWater() {
        // ExplosionInWater = 0: the in-water variant is never chosen, even for a round sitting in water.
        assertEquals(new Blast(Kind.NORMAL, 6, 2, At.HIT), MCH_Detonation.onImpact(6, 0, 2, false, false, true, true));
        assertEquals(Kind.SOUND, MCH_Detonation.onImpact(-1, 0, 0, false, true, false, false).kind());
    }

    @Test
    void faeIsCentredOnTheRoundAndKeepsTheBlockPower() {
        assertEquals(new Blast(Kind.FAE, 10, 3, At.SELF), MCH_Detonation.onImpact(10, 0, 3, true, false, false, false));
    }

    @Test
    void entityHitPicksTheVariantFromTheRoundOnly() {
        assertEquals(new Blast(Kind.WATER, 4, 4, At.HIT), MCH_Detonation.onImpact(6, 4, 2, false, true, true, false));
        // blockInWater is irrelevant for an entity hit.
        assertEquals(new Blast(Kind.NORMAL, 6, 2, At.HIT), MCH_Detonation.onImpact(6, 4, 2, false, true, false, true));
    }

    @Test
    void blockHitNextToWaterGoesOffAtTheBlock() {
        assertEquals(new Blast(Kind.WATER, 4, 4, At.BLOCK), MCH_Detonation.onImpact(6, 4, 2, false, false, false, true));
        assertEquals(new Blast(Kind.NORMAL, 6, 2, At.HIT), MCH_Detonation.onImpact(6, 4, 2, false, false, false, false));
    }
}
