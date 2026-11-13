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
 * {@link Role} (incl. the now-live PLAYER factor), the {@code DispenseItem} / {@code RoundItem} names and their
 * load-time {@link ItemHandle.Resolver} pass, plus the reference's {@code soundpattern} no-op branch quirk. Zero
 * Minecraft.
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
    void dispenseItemParsesRawThenResolvesWithItsMetadata() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("disp");
        w.loadItemData("dispenseitem", "Dye, 15");
        assertNull(w.dispenseItem);           // nothing resolved at parse time
        assertEquals("dye", w.dispenseItemName);
        assertEquals(15, w.dispenseDamege);

        // the resolver sees the name AND the 1.7.10 metadata (dye:15 was bone meal)
        w.resolveItems((name, meta) -> () -> "resolved:" + name + "@" + meta);
        assertNotNull(w.dispenseItem);
        assertEquals("resolved:dye@15", w.dispenseItem.name());
    }

    @Test
    @SuppressWarnings("unchecked") // MCH_InfoManagerBase.getMap() is a raw Map
    void unknownItemsStayNullAndManagerCountsThem() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("disp");
        w.loadItemData("dispenseitem", "no_such_item");
        w.loadItemData("item", "2, iron_ingot");
        w.loadItemData("item", "1, bogus");
        ItemHandle.Resolver r = (name, meta) -> "iron_ingot".equals(name) ? () -> "minecraft:iron_ingot" : null;
        w.resolveItems(r);
        assertNull(w.dispenseItem);
        assertEquals("minecraft:iron_ingot", w.roundItems.get(0).item.name());
        assertNull(w.roundItems.get(1).item);

        MCH_WeaponInfoManager mgr = MCH_WeaponInfoManager.getInstance();
        mgr.clear();
        try {
            mgr.getMap().put("disp", w);
            assertEquals(2, mgr.resolveItems(r, null), "the unknown dispense item and the unknown round item");
        } finally {
            mgr.clear();
        }
    }

    @Test
    void weaponWithoutDispenseItemResolvesToNull() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("gun");
        w.resolveItems((name, meta) -> { throw new AssertionError("no names to resolve"); });
        assertNull(w.dispenseItem);
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
        assertNull(ri.item); // resolved later by resolveItems(), not at parse time

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
    void explosionBlockDefaultsToExplosionOnLoad() throws Exception {
        // The reference loader ran checkData() after parsing, so a weapon without an explicit ExplosionBlock broke
        // blocks with its Explosion power. The port's loader only calls isValidData() -> the default must apply there.
        MCH_WeaponInfo w = new MCH_WeaponInfo("rocket");
        w.loadItemData("explosion", "3");
        assertEquals(-1, w.explosionBlock); // unset after parsing
        w.isValidData();
        assertEquals(3, w.explosionBlock);

        MCH_WeaponInfo explicit = new MCH_WeaponInfo("rocket_noblock");
        explicit.loadItemData("explosion", "3");
        explicit.loadItemData("explosionblock", "0");
        explicit.isValidData();
        assertEquals(0, explicit.explosionBlock); // an explicit value is kept
    }

    @Test
    void loadRunsTheWholeReferenceCheckData() throws Exception {
        // MW-1: Bomblet = 2, BombletSTime = 0 -> the reference coerces the split time to 1, or the cluster never splits.
        MCH_WeaponInfo mw1 = new MCH_WeaponInfo("mw-1");
        mw1.loadItemData("type", "Bomb");
        mw1.loadItemData("bomblet", "2");
        mw1.loadItemData("bombletstime", "0");
        mw1.isValidData();
        assertEquals(1, mw1.bombletSTime);

        // lau-68: Round = 19 over MaxAmmo = 8 -> the magazine is clamped to the reserve cap.
        MCH_WeaponInfo lau68 = new MCH_WeaponInfo("lau-68");
        lau68.loadItemData("type", "Rocket");
        lau68.loadItemData("round", "19");
        lau68.loadItemData("maxammo", "8");
        lau68.isValidData();
        assertEquals(8, lau68.round);

        // A weapon without a reserve gets an endless magazine (round 0), and Round <= 0 takes the reserve size.
        MCH_WeaponInfo gun = new MCH_WeaponInfo("gun");
        gun.loadItemData("round", "30");
        gun.isValidData();
        assertEquals(0, gun.round);
        MCH_WeaponInfo mortar = new MCH_WeaponInfo("mortar");
        mortar.loadItemData("maxammo", "57");
        mortar.isValidData();
        assertEquals(57, mortar.round);

        // plastic_bomb: Destruct latches the delay so it fires once; FAE never takes the in-water branch; a round
        // without a delay fuse never ricochets.
        MCH_WeaponInfo bomb = new MCH_WeaponInfo("plastic_bomb");
        bomb.loadItemData("destruct", "true");
        bomb.loadItemData("fae", "true");
        bomb.loadItemData("explosion", "4");
        bomb.loadItemData("explosioninwater", "6");
        bomb.loadItemData("bound", "0.5");
        bomb.isValidData();
        assertEquals(1000000, bomb.delay);
        assertEquals(0, bomb.explosionInWater);
        assertEquals(0.0F, bomb.bound, 0.0F);
    }

    @Test
    void shotVariantCopiesWithoutTouchingTheDefinition() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("m230");
        w.loadItemData("explosion", "2");
        w.loadItemData("piercing", "1");
        assertEquals(w, w.shotVariant(2, 1), "no change -> the definition itself");
        MCH_WeaponInfo v = w.shotVariant(0, 1);
        assertEquals(0, v.explosion);
        assertEquals(2, w.explosion);
        assertEquals(w.name, v.name);
        assertEquals(w.roundItems, v.roundItems, "lists are shared, not copied");
    }

    @Test
    void parsesZoomArray() {
        MCH_WeaponInfo w = new MCH_WeaponInfo("scope");
        w.loadItemData("zoom", "2, 4, 8");
        assertArrayEquals(new float[]{2f, 4f, 8f}, w.zoom, 1e-6f);
    }
}
