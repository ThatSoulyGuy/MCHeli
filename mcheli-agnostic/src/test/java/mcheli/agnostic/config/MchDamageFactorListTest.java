package mcheli.agnostic.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The reference's global damage-factor lists ({@code MCH_Config.DamageFactor}, newDamageEntity :586-593,
 * applyDamageVsEntity / applyDamageByExternal :595-651) and the {@code IgnoreBulletHit} class-name rule.
 */
class MchDamageFactorListTest {

    @Test
    void parsesFactorAndOptionalClassFragment() {
        MchDamageFactorList l = MchDamageFactorList.parse(List.of("1.0", " 3.0 , flansmod ", "0.5,a,b"));
        assertEquals(3, l.entries().size());
        assertEquals(new MchDamageFactorList.Entry(1.0, ""), l.entries().get(0));
        assertEquals(new MchDamageFactorList.Entry(3.0, "flansmod"), l.entries().get(1));
        // reference: any other field count is the neutral (1.0, "")
        assertEquals(new MchDamageFactorList.Entry(1.0, ""), l.entries().get(2));
    }

    @Test
    void unparseableLinesAreSkippedInsteadOfFailingTheLoad() {
        MchDamageFactorList l = MchDamageFactorList.parse(List.of("abc", "", "2.0, x", "NaN"));
        assertEquals(1, l.entries().size());
        assertFalse(MchDamageFactorList.isValidLine("abc"));
        assertFalse(MchDamageFactorList.isValidLine(3.0));
        assertTrue(MchDamageFactorList.isValidLine("0.5, flansmod"));
        assertSame(MchDamageFactorList.EMPTY, MchDamageFactorList.parse(List.of()));
        assertSame(MchDamageFactorList.EMPTY, MchDamageFactorList.parse(null));
    }

    @Test
    void everyMatchingEntryMultiplies() {
        // the reference default for DamageVsEntity after its first write: "1.0" + "3.0, flansmod"
        MchDamageFactorList l = MchDamageFactorList.parse(List.of("1.0", "3.0, flansmod"));
        assertEquals(10.0f, l.apply(10.0f, "net.minecraft.world.entity.boat.Boat"), 1e-6f);
        assertEquals(30.0f, l.apply(10.0f, "com.flansmod.common.driveables.EntityPlane"), 1e-6f);

        MchDamageFactorList two = MchDamageFactorList.parse(List.of("2.0", "0.5, Zombie", "3.0, monster"));
        assertEquals(30.0f, two.apply(10.0f, "net.minecraft.world.entity.monster.Zombie"), 1e-5f);
        assertEquals(10.0f, MchDamageFactorList.EMPTY.apply(10.0f, "x"), 0.0f);
    }

    @Test
    void byExternalMatchesTheAttackerOrTheDirectSource() {
        MchDamageFactorList l = MchDamageFactorList.parse(List.of("0.5, flansmod"));
        assertEquals(5.0f, l.apply(10.0f, null, "com.flansmod.common.guns.EntityBullet"), 1e-6f);
        assertEquals(5.0f, l.apply(10.0f, "com.flansmod.Player", null), 1e-6f);
        assertEquals(10.0f, l.apply(10.0f, (String) null, null), 0.0f);
        assertEquals(10.0f, l.apply(10.0f), 0.0f);
        // matching is case-sensitive, like the reference's indexOf
        assertEquals(10.0f, l.apply(10.0f, "com.FlansMod.X"), 0.0f);
    }

    @Test
    void ignoreBulletHitIsACaseInsensitiveClassNameMatch() {
        List<String> defaults = List.of("flansmod.common.guns.EntityBullet", "flansmod.common.guns.EntityGrenade");
        assertTrue(MchDamageFactorList.classNameMatchesAny("com.FLANSMOD.common.guns.EntityBullet", defaults));
        assertFalse(MchDamageFactorList.classNameMatchesAny("mcheli.dependent.entity.MchBullet", defaults));
        assertFalse(MchDamageFactorList.classNameMatchesAny("anything", List.of(" ", "")));
        assertFalse(MchDamageFactorList.classNameMatchesAny(null, defaults));
    }
}
