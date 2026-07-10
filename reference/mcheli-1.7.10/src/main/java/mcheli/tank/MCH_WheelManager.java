package mcheli.tank;

import java.util.List;
import java.util.Random;
import mcheli.MCH_Config;
import mcheli.MCH_Lib;
import mcheli.aircraft.MCH_AircraftInfo;
import mcheli.aircraft.MCH_EntityAircraft;
import mcheli.particles.MCH_ParticlesUtil;
import mcheli.wrapper.W_Block;
import mcheli.wrapper.W_Blocks;
import mcheli.wrapper.W_Lib;
import mcheli.wrapper.W_WorldFunc;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class MCH_WheelManager {
   public final MCH_EntityAircraft parent;
   public MCH_EntityWheel[] wheels;
   private double minZ;
   private double maxZ;
   private double avgZ;
   public Vec3 weightedCenter;
   public float targetPitch;
   public float targetRoll;
   public float prevYaw;
   private static Random rand = new Random();

   public MCH_WheelManager(MCH_EntityAircraft ac) {
      this.parent = ac;
      this.wheels = new MCH_EntityWheel[0];
      this.weightedCenter = Vec3.createVectorHelper(0.0, 0.0, 0.0);
   }

   public void createWheels(World w, List<MCH_AircraftInfo.Wheel> list, Vec3 weightedCenter) {
      this.wheels = new MCH_EntityWheel[list.size() * 2];
      this.minZ = 999999.0;
      this.maxZ = -999999.0;
      this.weightedCenter = weightedCenter;

      for (int i = 0; i < this.wheels.length; i++) {
         MCH_EntityWheel wheel = new MCH_EntityWheel(w);
         wheel.setParents(this.parent);
         Vec3 wp = list.get(i / 2).pos;
         wheel.setWheelPos(Vec3.createVectorHelper(i % 2 == 0 ? wp.xCoord : -wp.xCoord, wp.yCoord, wp.zCoord), this.weightedCenter);
         Vec3 v = this.parent.getTransformedPosition(wheel.pos.xCoord, wheel.pos.yCoord, wheel.pos.zCoord);
         wheel.setLocationAndAngles(v.xCoord, v.yCoord + 1.0, v.zCoord, 0.0F, 0.0F);
         this.wheels[i] = wheel;
         if (wheel.pos.zCoord <= this.minZ) {
            this.minZ = wheel.pos.zCoord;
         }

         if (wheel.pos.zCoord >= this.maxZ) {
            this.maxZ = wheel.pos.zCoord;
         }
      }

      this.avgZ = this.maxZ - this.minZ;
   }

   public void move(double x, double y, double z) {
      MCH_EntityAircraft ac = this.parent;
      if (ac.getAcInfo() != null) {
         boolean showLog = ac.ticksExisted % 1 == 1;
         if (showLog) {
            MCH_Lib.DbgLog(ac.worldObj, "[" + (ac.worldObj.isRemote ? "Client" : "Server") + "] ==============================");
         }

         for (MCH_EntityWheel wheel : this.wheels) {
            wheel.prevPosX = wheel.posX;
            wheel.prevPosY = wheel.posY;
            wheel.prevPosZ = wheel.posZ;
            Vec3 v = ac.getTransformedPosition(wheel.pos.xCoord, wheel.pos.yCoord, wheel.pos.zCoord);
            wheel.motionX = v.xCoord - wheel.posX + x;
            wheel.motionY = v.yCoord - wheel.posY;
            wheel.motionZ = v.zCoord - wheel.posZ + z;
         }

         for (MCH_EntityWheel wheel : this.wheels) {
            wheel.motionY *= 0.15;
            wheel.moveEntity(wheel.motionX, wheel.motionY, wheel.motionZ);
            double f = 1.0;
            wheel.moveEntity(0.0, -0.1 * f, 0.0);
         }

         int zmog = -1;

         for (int i = 0; i < this.wheels.length / 2; i++) {
            zmog = i;
            MCH_EntityWheel w1 = this.wheels[i * 2 + 0];
            MCH_EntityWheel w2 = this.wheels[i * 2 + 1];
            if (!w1.isPlus && (w1.onGround || w2.onGround)) {
               zmog = -1;
               break;
            }
         }

         if (zmog >= 0) {
            this.wheels[zmog * 2 + 0].onGround = true;
            this.wheels[zmog * 2 + 1].onGround = true;
         }

         zmog = -1;

         for (int i = this.wheels.length / 2 - 1; i >= 0; i--) {
            zmog = i;
            MCH_EntityWheel w1 = this.wheels[i * 2 + 0];
            MCH_EntityWheel w2 = this.wheels[i * 2 + 1];
            if (w1.isPlus && (w1.onGround || w2.onGround)) {
               zmog = -1;
               break;
            }
         }

         if (zmog >= 0) {
            this.wheels[zmog * 2 + 0].onGround = true;
            this.wheels[zmog * 2 + 1].onGround = true;
         }

         Vec3 rv = Vec3.createVectorHelper(0.0, 0.0, 0.0);
         Vec3 wc = ac.getTransformedPosition(this.weightedCenter);
         wc.xCoord = wc.xCoord - ac.posX;
         wc.yCoord = this.weightedCenter.yCoord;
         wc.zCoord = wc.zCoord - ac.posZ;

         for (int i = 0; i < this.wheels.length / 2; i++) {
            MCH_EntityWheel w1 = this.wheels[i * 2 + 0];
            MCH_EntityWheel w2 = this.wheels[i * 2 + 1];
            Vec3 v1 = Vec3.createVectorHelper(
               w1.posX - (ac.posX + wc.xCoord),
               w1.posY - (ac.posY + wc.yCoord),
               w1.posZ - (ac.posZ + wc.zCoord)
            );
            Vec3 v2 = Vec3.createVectorHelper(
               w2.posX - (ac.posX + wc.xCoord),
               w2.posY - (ac.posY + wc.yCoord),
               w2.posZ - (ac.posZ + wc.zCoord)
            );
            Vec3 v = w1.pos.zCoord >= 0.0 ? v2.crossProduct(v1) : v1.crossProduct(v2);
            v = v.normalize();
            double f = Math.abs(w1.pos.zCoord / this.avgZ);
            if (!w1.onGround && !w2.onGround) {
               f = 0.0;
            }

            rv.xCoord = rv.xCoord + v.xCoord * f;
            rv.yCoord = rv.yCoord + v.yCoord * f;
            rv.zCoord = rv.zCoord + v.zCoord * f;
            if (showLog) {
               v.rotateAroundY((float)(ac.getRotYaw() * Math.PI / 180.0));
               MCH_Lib.DbgLog(
                  ac.worldObj,
                  "%2d : %.2f :[%+.1f, %+.1f, %+.1f][%s %d %d][%+.2f(%+.2f), %+.2f(%+.2f)][%+.1f, %+.1f, %+.1f]",
                  i,
                  f,
                  v.xCoord,
                  v.yCoord,
                  v.zCoord,
                  w1.isPlus ? "+" : "-",
                  w1.onGround ? 1 : 0,
                  w2.onGround ? 1 : 0,
                  w1.posY - w1.prevPosY,
                  w1.motionY,
                  w2.posY - w2.prevPosY,
                  w2.motionY,
                  v.xCoord,
                  v.yCoord,
                  v.zCoord
               );
            }
         }

         rv = rv.normalize();
         if (rv.yCoord > 0.01 && rv.yCoord < 0.7) {
            ac.motionX = ac.motionX + rv.xCoord / 50.0;
            ac.motionZ = ac.motionZ + rv.zCoord / 50.0;
         }

         rv.rotateAroundY((float)(ac.getRotYaw() * Math.PI / 180.0));
         float pitch = (float)(90.0 - Math.atan2(rv.yCoord, rv.zCoord) * 180.0 / Math.PI);
         float roll = -((float)(90.0 - Math.atan2(rv.yCoord, rv.xCoord) * 180.0 / Math.PI));
         float ogpf = ac.getAcInfo().onGroundPitchFactor;
         if (pitch - ac.getRotPitch() > ogpf) {
            pitch = ac.getRotPitch() + ogpf;
         }

         if (pitch - ac.getRotPitch() < -ogpf) {
            pitch = ac.getRotPitch() - ogpf;
         }

         float ogrf = ac.getAcInfo().onGroundRollFactor;
         if (roll - ac.getRotRoll() > ogrf) {
            roll = ac.getRotRoll() + ogrf;
         }

         if (roll - ac.getRotRoll() < -ogrf) {
            roll = ac.getRotRoll() - ogrf;
         }

         this.targetPitch = pitch;
         this.targetRoll = roll;
         if (!W_Lib.isClientPlayer(ac.getRiddenByEntity())) {
            ac.setRotPitch(pitch);
            ac.setRotRoll(roll);
         }

         if (showLog) {
            MCH_Lib.DbgLog(
               ac.worldObj,
               "%+03d, %+03d :[%.2f, %.2f, %.2f] yaw=%.2f, pitch=%.2f, roll=%.2f",
               (int)pitch,
               (int)roll,
               rv.xCoord,
               rv.yCoord,
               rv.zCoord,
               ac.getRotYaw(),
               this.targetPitch,
               this.targetRoll
            );
         }

         for (MCH_EntityWheel wheel : this.wheels) {
            Vec3 v = this.getTransformedPosition(
               wheel.pos.xCoord, wheel.pos.yCoord, wheel.pos.zCoord, ac, ac.getRotYaw(), this.targetPitch, this.targetRoll
            );
            double offset = wheel.onGround ? 0.01 : -0.0;
            double rangeH = 2.0;
            double poy = wheel.stepHeight / 2.0F;
            int b = 0;
            if (wheel.posX > v.xCoord + rangeH) {
               wheel.posX = v.xCoord + rangeH;
               wheel.posY = v.yCoord + poy;
               b |= 1;
            }

            if (wheel.posX < v.xCoord - rangeH) {
               wheel.posX = v.xCoord - rangeH;
               wheel.posY = v.yCoord + poy;
               b |= 2;
            }

            if (wheel.posZ > v.zCoord + rangeH) {
               wheel.posZ = v.zCoord + rangeH;
               wheel.posY = v.yCoord + poy;
               b |= 4;
            }

            if (wheel.posZ < v.zCoord - rangeH) {
               wheel.posZ = v.zCoord - rangeH;
               wheel.posY = v.yCoord + poy;
               b |= 8;
            }

            wheel.setPositionAndRotation(wheel.posX, wheel.posY, wheel.posZ, 0.0F, 0.0F);
         }
      }
   }

   public Vec3 getTransformedPosition(double x, double y, double z, MCH_EntityAircraft ac, float yaw, float pitch, float roll) {
      Vec3 v = MCH_Lib.RotVec3(x, y, z, -yaw, -pitch, -roll);
      return v.addVector(ac.posX, ac.posY, ac.posZ);
   }

   public void updateBlock() {
      if (MCH_Config.Collision_DestroyBlock.prmBool) {
         MCH_EntityAircraft ac = this.parent;

         for (MCH_EntityWheel w : this.wheels) {
            Vec3 v = ac.getTransformedPosition(w.pos);
            int x = (int)(v.xCoord + 0.5);
            int y = (int)(v.yCoord + 0.5);
            int z = (int)(v.zCoord + 0.5);
            Block block = ac.worldObj.getBlock(x, y, z);
            if (block == W_Block.getSnowLayer()) {
               ac.worldObj.setBlockToAir(x, y, z);
            }

            if (block == W_Blocks.waterlily || block == W_Blocks.cake) {
               W_WorldFunc.destroyBlock(ac.worldObj, x, y, z, false);
            }
         }
      }
   }

   public void particleLandingGear() {
      if (this.wheels.length > 0) {
         MCH_EntityAircraft ac = this.parent;
         double d = ac.motionX * ac.motionX + ac.motionZ * ac.motionZ + Math.abs(this.prevYaw - ac.getRotYaw());
         this.prevYaw = ac.getRotYaw();
         if (d > 0.001) {
            for (int i = 0; i < 2; i++) {
               MCH_EntityWheel w = this.wheels[rand.nextInt(this.wheels.length)];
               Vec3 v = ac.getTransformedPosition(w.pos);
               int x = MathHelper.floor_double(v.xCoord + 0.5);
               int y = MathHelper.floor_double(v.yCoord - 0.5);
               int z = MathHelper.floor_double(v.zCoord + 0.5);
               Block block = ac.worldObj.getBlock(x, y, z);
               if (Block.isEqualTo(block, Blocks.air)) {
                  y = MathHelper.floor_double(v.yCoord + 0.5);
                  block = ac.worldObj.getBlock(x, y, z);
               }

               if (!Block.isEqualTo(block, Blocks.air)) {
                  MCH_ParticlesUtil.spawnParticleTileCrack(
                     ac.worldObj,
                     x,
                     y,
                     z,
                     v.xCoord + (rand.nextFloat() - 0.5),
                     v.yCoord + 0.1,
                     v.zCoord + (rand.nextFloat() - 0.5),
                     -ac.motionX * 4.0 + (rand.nextFloat() - 0.5) * 0.1,
                     rand.nextFloat() * 0.5,
                     -ac.motionZ * 4.0 + (rand.nextFloat() - 0.5) * 0.1
                  );
               }
            }
         }
      }
   }
}
