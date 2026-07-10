package mcheli.aircraft;

import com.google.common.io.ByteArrayDataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import mcheli.MCH_Packet;

public abstract class MCH_PacketPlayerControlBase extends MCH_Packet {
   public byte isUnmount = 0;
   public byte switchMode = -1;
   public byte switchCameraMode = 0;
   public byte switchWeapon = -1;
   public byte useFlareType = 0;
   public boolean useWeapon = false;
   public int useWeaponOption1 = 0;
   public int useWeaponOption2 = 0;
   public double useWeaponPosX = 0.0;
   public double useWeaponPosY = 0.0;
   public double useWeaponPosZ = 0.0;
   public boolean throttleUp = false;
   public boolean throttleDown = false;
   public boolean moveLeft = false;
   public boolean moveRight = false;
   public boolean openGui;
   public byte switchHatch = 0;
   public byte switchFreeLook = 0;
   public byte switchGear = 0;
   public boolean ejectSeat = false;
   public byte putDownRack = 0;
   public boolean switchSearchLight = false;
   public boolean useBrake = false;

   @Override
   public void readData(ByteArrayDataInput data) {
      try {
         short bf = data.readShort();
         this.useWeapon = this.getBit((short)bf, 0);
         this.throttleUp = this.getBit((short)bf, 1);
         this.throttleDown = this.getBit((short)bf, 2);
         this.moveLeft = this.getBit((short)bf, 3);
         this.moveRight = this.getBit((short)bf, 4);
         this.switchSearchLight = this.getBit((short)bf, 5);
         this.ejectSeat = this.getBit((short)bf, 6);
         this.openGui = this.getBit((short)bf, 7);
         this.useBrake = this.getBit((short)bf, 8);
         bf = data.readByte();
         this.putDownRack = (byte)(bf >> 6 & 3);
         this.isUnmount = (byte)(bf >> 4 & 3);
         this.useFlareType = (byte)(bf >> 0 & 15);
         this.switchMode = data.readByte();
         this.switchWeapon = data.readByte();
         if (this.useWeapon) {
            this.useWeaponOption1 = data.readInt();
            this.useWeaponOption2 = data.readInt();
            this.useWeaponPosX = data.readDouble();
            this.useWeaponPosY = data.readDouble();
            this.useWeaponPosZ = data.readDouble();
         }

         bf = data.readByte();
         this.switchCameraMode = (byte)(bf >> 6 & 3);
         this.switchHatch = (byte)(bf >> 4 & 3);
         this.switchFreeLook = (byte)(bf >> 2 & 3);
         this.switchGear = (byte)(bf >> 0 & 3);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   @Override
   public void writeData(DataOutputStream dos) {
      try {
         short bf = 0;
         bf = this.setBit((short)bf, 0, this.useWeapon);
         bf = this.setBit((short)bf, 1, this.throttleUp);
         bf = this.setBit((short)bf, 2, this.throttleDown);
         bf = this.setBit((short)bf, 3, this.moveLeft);
         bf = this.setBit((short)bf, 4, this.moveRight);
         bf = this.setBit((short)bf, 5, this.switchSearchLight);
         bf = this.setBit((short)bf, 6, this.ejectSeat);
         bf = this.setBit((short)bf, 7, this.openGui);
         bf = this.setBit((short)bf, 8, this.useBrake);
         dos.writeShort(bf);
         bf = (byte)((this.putDownRack & 3) << 6 | (this.isUnmount & 3) << 4 | this.useFlareType & 15);
         dos.writeByte(bf);
         dos.writeByte(this.switchMode);
         dos.writeByte(this.switchWeapon);
         if (this.useWeapon) {
            dos.writeInt(this.useWeaponOption1);
            dos.writeInt(this.useWeaponOption2);
            dos.writeDouble(this.useWeaponPosX);
            dos.writeDouble(this.useWeaponPosY);
            dos.writeDouble(this.useWeaponPosZ);
         }

         bf = (byte)((this.switchCameraMode & 3) << 6 | (this.switchHatch & 3) << 4 | (this.switchFreeLook & 3) << 2 | (this.switchGear & 3) << 0);
         dos.writeByte(bf);
      } catch (IOException e) {
         e.printStackTrace();
      }
   }
}
