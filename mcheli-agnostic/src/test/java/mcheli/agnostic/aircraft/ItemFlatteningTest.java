package mcheli.agnostic.aircraft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link MCH_ItemFlattening}: 1.7.10 item tokens (name + damage value) to 1.21 ids. */
class ItemFlatteningTest {

    @Test
    void metadataFamiliesIndexByDamageValue() {
        assertEquals("minecraft:white_wool", MCH_ItemFlattening.flatten("wool", 0));
        assertEquals("minecraft:red_wool", MCH_ItemFlattening.flatten("wool", 14));
        assertEquals("minecraft:black_carpet", MCH_ItemFlattening.flatten("minecraft:carpet", 15));
        assertEquals("minecraft:dark_oak_log", MCH_ItemFlattening.flatten("log2", 1));
        assertEquals("minecraft:charcoal", MCH_ItemFlattening.flatten("coal", 1));
        assertEquals("minecraft:white_wool", MCH_ItemFlattening.flatten("wool", 99), "out of range -> meta 0");
        assertEquals("minecraft:white_wool", MCH_ItemFlattening.flatten("wool", -1));
    }

    @Test
    void slabsFollowTheOldBlockNotTheNewName() {
        // wooden_slab:0 was the OAK slab, never a stone one.
        assertEquals("minecraft:oak_slab", MCH_ItemFlattening.flatten("wooden_slab", 0));
        assertEquals("minecraft:spruce_slab", MCH_ItemFlattening.flatten("wooden_slab", 1));
        // 1.7.10 stone_slab:0 is today's smooth stone slab; 1.21's own stone_slab is a different block.
        assertEquals("minecraft:smooth_stone_slab", MCH_ItemFlattening.flatten("stone_slab", 0));
        assertEquals("minecraft:sandstone_slab", MCH_ItemFlattening.flatten("stone_slab", 1));
        assertEquals("minecraft:chiseled_sandstone", MCH_ItemFlattening.flatten("sandstone", 1));
    }

    @Test
    void renamedAndRemovedNames() {
        assertEquals("minecraft:oak_boat", MCH_ItemFlattening.flatten("boat", 0));
        assertEquals("minecraft:snow_block", MCH_ItemFlattening.flatten("snow", 0));
        assertEquals("minecraft:nether_bricks", MCH_ItemFlattening.flatten("nether_brick", 0));
        assertEquals("minecraft:nether_brick", MCH_ItemFlattening.flatten("netherbrick", 0));
        assertEquals("minecraft:music_disc_13", MCH_ItemFlattening.flatten("record_13", 0));
    }

    @Test
    void namesThatStillExistWithAnotherMeaningAreMappedFirst() {
        // Each of these is a live 1.21 id for a DIFFERENT thing, so a plain lookup would silently pick the wrong item.
        assertEquals("minecraft:melon_slice", MCH_ItemFlattening.flatten("melon", 0), "1.7.10 melon was the slice");
        assertEquals("minecraft:melon", MCH_ItemFlattening.flatten("melon_block", 0));
        assertEquals("minecraft:cobblestone_stairs", MCH_ItemFlattening.flatten("stone_stairs", 0));
        assertEquals("minecraft:cobblestone_stairs", MCH_ItemFlattening.flatten("minecraft:stone_stairs", 0));
        assertEquals("minecraft:snow", MCH_ItemFlattening.flatten("snow_layer", 0));
    }

    @Test
    void goneNamesMapToWhatTheyBecame() {
        assertEquals("minecraft:grass_block", MCH_ItemFlattening.flatten("grass", 0), "the block, not short_grass");
        assertEquals("minecraft:short_grass", MCH_ItemFlattening.flatten("tallgrass", 1), "the plant was tallgrass:1");
        assertEquals("minecraft:dead_bush", MCH_ItemFlattening.flatten("deadbush", 0));
        assertEquals("minecraft:lily_pad", MCH_ItemFlattening.flatten("waterlily", 0));
        assertEquals("minecraft:oak_sign", MCH_ItemFlattening.flatten("sign", 0));
        assertEquals("minecraft:red_bed", MCH_ItemFlattening.flatten("bed", 0));
        assertEquals("minecraft:infested_stone", MCH_ItemFlattening.flatten("monster_egg", 0));
        assertEquals("minecraft:infested_chiseled_stone_bricks", MCH_ItemFlattening.flatten("monster_egg", 5));
    }

    @Test
    void packedBlockStateBitsAreMaskedOffTheVariant() {
        // log/log2: bits 2-3 are the axis.
        assertEquals("minecraft:spruce_log", MCH_ItemFlattening.flatten("log", 1 | 4), "spruce, east-west");
        assertEquals("minecraft:jungle_log", MCH_ItemFlattening.flatten("log", 3 | 12), "jungle, bark on all sides");
        assertEquals("minecraft:dark_oak_log", MCH_ItemFlattening.flatten("log2", 1 | 8));
        // leaves/leaves2: bit 2 no-decay, bit 3 check-decay.
        assertEquals("minecraft:birch_leaves", MCH_ItemFlattening.flatten("leaves", 2 | 4));
        assertEquals("minecraft:acacia_leaves", MCH_ItemFlattening.flatten("leaves2", 0 | 8));
        // Slabs: bit 3 is the top half; saplings: bit 3 the growth stage.
        assertEquals("minecraft:spruce_slab", MCH_ItemFlattening.flatten("wooden_slab", 1 | 8));
        assertEquals("minecraft:cobblestone_slab", MCH_ItemFlattening.flatten("stone_slab", 3 | 8));
        assertEquals("minecraft:jungle_sapling", MCH_ItemFlattening.flatten("sapling", 3 | 8));
        // quartz_block 2/3/4: the pillar, vertical / north-south / east-west.
        assertEquals("minecraft:chiseled_quartz_block", MCH_ItemFlattening.flatten("quartz_block", 1));
        for (int m = 2; m <= 4; m++) {
            assertEquals("minecraft:quartz_pillar", MCH_ItemFlattening.flatten("quartz_block", m), "meta " + m);
        }
        // Outside the 4-bit block range nothing is unpacked: a wildcard falls back to the meta-0 member.
        assertEquals("minecraft:oak_log", MCH_ItemFlattening.flatten("log", 32767));
        assertEquals("minecraft:quartz_block", MCH_ItemFlattening.flatten("quartz_block", 5));
        assertEquals("minecraft:acacia_log", MCH_ItemFlattening.flatten("log2", -1));
    }

    @Test
    void unknownModernAndForeignNamesAreLookedUpAsWritten() {
        assertNull(MCH_ItemFlattening.flatten("iron_block", 0));
        assertNull(MCH_ItemFlattening.flatten("oak_planks", 0), "a modern name passes through");
        assertNull(MCH_ItemFlattening.flatten("mossy_cobblestone", 0));
        assertNull(MCH_ItemFlattening.flatten("mcheli:ah-6", 0), "another namespace is never remapped");
        assertNull(MCH_ItemFlattening.flatten("", 0));
        assertNull(MCH_ItemFlattening.flatten(null, 0));
    }

    @Test
    void potionDamageValues() {
        assertEquals("minecraft:regeneration", MCH_ItemFlattening.potionId(8193)); // us-2's recipe ingredient
        assertEquals("minecraft:strong_regeneration", MCH_ItemFlattening.potionId(8193 | 32));
        assertEquals("minecraft:long_regeneration", MCH_ItemFlattening.potionId(8193 | 64));
        assertEquals("minecraft:fire_resistance", MCH_ItemFlattening.potionId(8195 | 32), "no strong form in 1.21");
        assertEquals("minecraft:strong_healing", MCH_ItemFlattening.potionId(8197 | 32 | 64));
        assertEquals("minecraft:invisibility", MCH_ItemFlattening.potionId(8206));
        assertEquals("minecraft:water", MCH_ItemFlattening.potionId(0));
        assertEquals("minecraft:awkward", MCH_ItemFlattening.potionId(16));
        assertEquals("minecraft:mundane", MCH_ItemFlattening.potionId(8192));
        assertNull(MCH_ItemFlattening.potionId(8199), "effect 7 was never brewable");
        assertTrue(MCH_ItemFlattening.isSplashPotion(16385));
        assertEquals("minecraft:regeneration", MCH_ItemFlattening.potionId(16385));
        assertFalse(MCH_ItemFlattening.isSplashPotion(8193));
    }
}
