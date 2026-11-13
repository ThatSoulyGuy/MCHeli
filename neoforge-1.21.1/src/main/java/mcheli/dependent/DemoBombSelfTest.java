package mcheli.dependent;

import mcheli.agnostic.aircraft.MCH_AircraftInfo;
import mcheli.agnostic.plane.MCP_PlaneInfoManager;
import mcheli.agnostic.weapon.MCH_WeaponBallistics;
import mcheli.agnostic.weapon.MCH_WeaponInfo;
import mcheli.agnostic.weapon.MCH_WeaponInfoManager;
import mcheli.agnostic.weapon.VehicleWeapons;
import mcheli.agnostic.weapon.WeaponSlot;
import mcheli.dependent.entity.MchBullet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Headless proof of the <b>bomb release path</b> (#27): (1) the b29 bomber's {@code Type=Bomb} 250kg racks are now
 * SELECTABLE (VehicleWeapons.isFireable enables "bomb"), and (2) a dropped bomb inherits the carrier's velocity
 * (reference {@code MCH_WeaponBomb.shot}, NOT a forward muzzle launch), falls under config gravity, and detonates on
 * the ground. Uses the {@link MchBullet#spawnDropped} seam that {@link mcheli.dependent.entity.AbstractMchVehicle}'s
 * bomb fire-branch drives.
 */
public final class DemoBombSelfTest extends MchSelfTest {
    private static final int CHECK_AFTER_TICKS = 200;

    private boolean bombSelectable;
    private MchBullet bomb;
    private double releaseVx;
    private double spawnY;
    private double minY = Double.MAX_VALUE;
    private boolean detonated;

    @Override
    protected void start() {
        BlockPos spawn = this.origin;

        // (1) The b29's Type=Bomb 250kg racks now build into a selectable weapon slot.
        MCH_AircraftInfo b29 = MCP_PlaneInfoManager.get("b29");
        if (b29 != null) {
            VehicleWeapons vw = VehicleWeapons.build(b29, MCH_WeaponInfoManager::get);
            for (int i = 0; i < vw.size(); i++) {
                WeaponSlot s = vw.get(i);
                if (s.info.type != null && s.info.type.equalsIgnoreCase("bomb")) {
                    this.bombSelectable = true;
                    break;
                }
            }
        }

        // (2) Drop physics via the release seam (the area covers the fall column and the inherited drift).
        MCH_WeaponInfo bombWi = MCH_WeaponInfoManager.get("250kg"); // Type=Bomb, gravity default -0.03, explosion 4
        if (bombWi == null) {
            fail("250kg weapon config not loaded");
            return;
        }
        this.spawnY = spawn.getY() + 30.0;
        Vec3 pos = new Vec3(spawn.getX() + 0.5, this.spawnY, spawn.getZ() + 0.5);
        Vec3 carrierVel = new Vec3(0.6, 0.0, 0.0); // a level bomber moving +X — the bomb should INHERIT this
        String model = bombWi.bulletModelName != null && !bombWi.bulletModelName.isEmpty()
            ? bombWi.bulletModelName : MCH_WeaponBallistics.defaultBulletModel(bombWi.type);
        this.bomb = MchBullet.spawnDropped(this.level, pos, carrierVel, bombWi.gravity, bombWi.power, 1200,
            null, model, 0xFFFFFFFF, bombWi);
        this.releaseVx = this.bomb.getDeltaMovement().x; // captured before any gravity step
    }

    @Override
    protected int timeoutTicks() {
        return CHECK_AFTER_TICKS + 20;
    }

    @Override
    protected void tick() {
        if (!this.bomb.isRemoved()) {
            this.minY = Math.min(this.minY, this.bomb.getY());
        } else {
            this.detonated = true;
        }

        if (ticks() < CHECK_AFTER_TICKS && !this.detonated) {
            return;
        }

        boolean inheritedVelocity = Math.abs(this.releaseVx - 0.6) < 0.2; // dropped at hull velocity, not muzzle-fired
        boolean fell = (this.spawnY - this.minY) > 15.0;
        boolean pass = this.bombSelectable && inheritedVelocity && fell && this.detonated;

        LOG.info("[BOMB-SELFTEST] bombSelectable={} releaseVx={} fell={}(dropY={}) detonated={}",
            this.bombSelectable, String.format("%.3f", this.releaseVx),
            String.format("%.2f", this.spawnY - this.minY), String.format("%.2f", this.minY), this.detonated);
        finish(pass, "selectable={} inheritedVelocity={} fell={} detonated={}",
            this.bombSelectable, inheritedVelocity, fell, this.detonated);
    }
}
