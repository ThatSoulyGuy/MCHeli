package mcheli.dependent;

import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.VehicleWeapons;
import mcheli.dependent.entity.MchBullet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Headless proof of the <b>dispenser weapon</b> (#31): {@code dispenser} is now SELECTABLE, and a dropped dispenser
 * round "uses" its configured item across a radius on impact (reference {@code MCH_EntityDispensedItem} — a FakePlayer
 * right-clicks the item onto each block). Here a {@code torch} dispenser (range 3) is dropped onto a stone platform;
 * afterwards torch blocks must have been PLACED on the platform — proving the item resolves + the {@code useOn} radius
 * runs. (The config item name is now kept agnostic-side + resolved dependent-side, which it never was before.)
 */
public final class DemoDispenserSelfTest extends MchSelfTest {
    private static final int CHECK_AFTER_TICKS = 80;

    private int cx, cz, baseY;
    private boolean fireable;

    @Override
    protected void start() {
        this.fireable = VehicleWeapons.isFireable("dispenser");

        BlockPos spawn = this.origin;
        this.cx = spawn.getX();
        this.cz = spawn.getZ();
        this.baseY = spawn.getY() + 6;
        // A clear stone platform with open air above (so torches can be placed on top).
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                this.level.setBlockAndUpdate(new BlockPos(this.cx + x, this.baseY, this.cz + z),
                    Blocks.STONE.defaultBlockState());
                for (int y = 1; y <= 4; y++) {
                    this.level.setBlockAndUpdate(new BlockPos(this.cx + x, this.baseY + y, this.cz + z),
                        Blocks.AIR.defaultBlockState());
                }
            }
        }

        // Drop a torch dispenser onto the platform centre.
        MCH_WeaponInfo wi = new MCH_WeaponInfo("mch_test_dispenser");
        wi.type = "dispenser";
        wi.dispenseItemName = "torch";
        wi.dispenseItem = mcheli.dependent.port.NeoItemHandle.resolve("torch", 0); // what resolveItems() does at load
        wi.dispenseRange = 3;
        wi.explosion = 0;
        wi.power = 0;
        MchBullet.spawnWeapon(this.level, new Vec3(this.cx + 0.5, this.baseY + 8.0, this.cz + 0.5),
            new Vec3(0, -1, 0), 0.6F, 1.0F, -0.03F, 0, 200, null, "bullet", 0xFFFFFFFF, wi);
    }

    @Override
    protected void tick() {
        if (ticks() < CHECK_AFTER_TICKS) {
            return;
        }

        int torchesPlaced = 0;
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 1; y <= 2; y++) {
                    BlockState bs = this.level.getBlockState(new BlockPos(this.cx + x, this.baseY + y, this.cz + z));
                    if (bs.is(Blocks.TORCH) || bs.is(Blocks.WALL_TORCH)) {
                        torchesPlaced++;
                    }
                }
            }
        }

        boolean dispensed = torchesPlaced > 0;
        boolean pass = this.fireable && dispensed;
        LOG.info("[DISPENSER-SELFTEST] fireable={} torchesPlaced={}", this.fireable, torchesPlaced);
        finish(pass, "fireable={} dispensed={} ({} torches)", this.fireable, dispensed, torchesPlaced);
    }
}
