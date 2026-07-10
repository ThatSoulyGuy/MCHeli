package mcheli.agnostic.weapon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import mcheli.agnostic.spi.ItemHandle;
import mcheli.agnostic.spi.Role;
import org.junit.jupiter.api.Test;

/**
 * Drives the coerced {@link MCH_WeaponInfo} parser, focusing on the platform coercions: DamageFactor keyed by
 * {@link Role} (incl. the now-live PLAYER factor), the {@code DispenseItem} {@code itemByName} seam, the
 * {@code RoundItem} ItemHandle field, plus the reference's {@code soundpattern} no-op branch quirk. Zero Minecraft.
 */
class WeaponInfoParsingTest {

    @Test
    void parsesDamageFactorAsRole() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("gau8");
        w.loadItemData("damagefactor", "heli, 0.5");
        w.loadItemData("damagefactor", "player, 0.3");
        w.loadItemData("damagefactor", "tank, 2.0");

        assertNotNull(w.damageFactor);
        assertEquals(0.5f, w.damageFactor.getDamageFactor(Role.HELICOPTER), 1e-6f);
        // COERCION: the reference keyed EntityPlayer.class (never matched a runtime player subclass); by Role it lives
        assertEquals(0.3f, w.damageFactor.getDamageFactor(Role.PLAYER), 1e-6f);
        assertEquals(2.0f, w.damageFactor.getDamageFactor(Role.TANK), 1e-6f);
        assertEquals(1.0f, w.damageFactor.getDamageFactor(Role.PLANE), 1e-6f); // unset -> default 1.0
    }

    @Test
    void dispenseItemResolvesViaSeam() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("disp") {
            @Override protected ItemHandle itemByName(String name) { return () -> "resolved:" + name; }
        };
        w.loadItemData("dispenseitem", "minecraft:tnt, 5");
        assertNotNull(w.dispenseItem);
        assertEquals("resolved:minecraft:tnt", w.dispenseItem.name());
        assertEquals(5, w.dispenseDamege);
    }

    @Test
    void defaultDispenseItemSeamReturnsNull() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("disp");
        w.loadItemData("dispenseitem", "minecraft:tnt, 5");
        assertNull(w.dispenseItem);       // agnostic default seam: no registry
        assertEquals(5, w.dispenseDamege); // but the damage still parses
    }

    @Test
    void parsesRoundItemsAndBulletColor() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("gun");
        w.loadItemData("item", "5, minecraft:arrow, 2");
        assertEquals(1, w.roundItems.size());
        MCH_WeaponInfo.RoundItem ri = w.roundItems.get(0);
        assertEquals(5, ri.num);
        assertEquals("minecraft:arrow", ri.itemName);
        assertEquals(2, ri.damage);
        assertNull(ri.item); // ItemHandle resolved dependent-side; null agnostic-side

        w.loadItemData("bulletcolor", "255, 128, 64, 32"); // config order is a,r,g,b
        assertEquals(1.0f, w.color.a, 1e-6f);
        assertEquals(128f / 255f, w.color.r, 1e-3f);
        assertEquals(64f / 255f, w.color.g, 1e-3f);
        assertEquals(32f / 255f, w.color.b, 1e-3f);
    }

    @Test
    void soundPatternDirectiveIsNoOpButSiblingsApply() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("gun");
        w.loadItemData("soundpattern", "3");
        assertEquals(0, w.soundPattern); // reference quirk: SoundPattern directive is dead
        w.loadItemData("soundvolume", "0.5");
        assertEquals(0.5f, w.soundVolume, 1e-6f); // sibling directives (nested under != soundpattern) still work
        w.loadItemData("locktime", "50");
        assertEquals(50, w.lockTime);
    }

    @Test
    void typeBombSetsGravityAndCheckDataComputesAngle() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("mk82");
        w.loadItemData("type", "bomb");
        assertEquals("bomb", w.type);
        assertEquals(-0.03f, w.gravity, 1e-6f);
        assertEquals(-0.03f, w.gravityInWater, 1e-6f);
        assertEquals("Bomb", w.getWeaponTypeName());

        w.loadItemData("length", "4");
        w.loadItemData("radius", "3");
        w.checkData();
        assertEquals((float) (Math.atan2(3.0, 4.0) * 180.0 / Math.PI), w.angle, 1e-4f);
    }

    @Test
    void parsesZoomArray() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("scope");
        w.loadItemData("zoom", "2, 4, 8");
        assertArrayEquals(new float[]{2f, 4f, 8f}, w.zoom, 1e-6f);
    }
}
