package mcheli.aircraft;

import mcheli.MCH_ClientTickHandlerBase;
import mcheli.MCH_Config;
import mcheli.MCH_Key;
import mcheli.MCH_Lib;
import mcheli.wrapper.W_Network;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

public class MCH_ClientSeatTickHandler extends MCH_ClientTickHandlerBase {
   protected boolean isRiding = false;
   protected boolean isBeforeRiding = false;
   public MCH_Key KeySwitchNextSeat;
   public MCH_Key KeySwitchPrevSeat;
   public MCH_Key KeyParachuting;
   public MCH_Key KeyFreeLook;
   public MCH_Key KeyUnmountForce;
   public MCH_Key[] Keys;

   public MCH_ClientSeatTickHandler(Minecraft minecraft, MCH_Config config) {
      super(minecraft);
      this.updateKeybind(config);
   }

   @Override
   public void updateKeybind(MCH_Config config) {
      this.KeySwitchNextSeat = new MCH_Key(MCH_Config.KeyExtra.prmInt);
      this.KeySwitchPrevSeat = new MCH_Key(MCH_Config.KeyGUI.prmInt);
      this.KeyParachuting = new MCH_Key(MCH_Config.KeySwitchHovering.prmInt);
      this.KeyUnmountForce = new MCH_Key(42);
      this.KeyFreeLook = new MCH_Key(MCH_Config.KeyFreeLook.prmInt);
      this.Keys = new MCH_Key[]{this.KeySwitchNextSeat, this.KeySwitchPrevSeat, this.KeyParachuting, this.KeyUnmountForce, this.KeyFreeLook};
   }

   @Override
   protected void onTick(boolean inGUI) {
      for (MCH_Key k : this.Keys) {
         k.update();
      }

      this.isBeforeRiding = this.isRiding;
      EntityPlayer player = this.mc.thePlayer;
      if (player != null && player.ridingEntity instanceof MCH_EntitySeat) {
         MCH_EntitySeat seat = (MCH_EntitySeat)player.ridingEntity;
         if (seat.getParent() == null || seat.getParent().getAcInfo() == null) {
            return;
         }

         MCH_EntityAircraft ac = seat.getParent();
         if (!inGUI && !ac.isDestroyed()) {
            this.playerControl(player, seat, ac);
         }

         this.isRiding = true;
      } else {
         this.isRiding = false;
      }

      if (this.isBeforeRiding != this.isRiding && !this.isRiding) {
         MCH_Lib.setRenderViewEntity(player);
      }
   }

   private void playerControlInGUI(EntityPlayer player, MCH_EntitySeat seat, MCH_EntityAircraft ac) {
   }

   private void playerControl(EntityPlayer player, MCH_EntitySeat seat, MCH_EntityAircraft ac) {
      MCH_PacketSeatPlayerControl pc = new MCH_PacketSeatPlayerControl();
      boolean send = false;
      if (this.KeyFreeLook.isKeyDown() && ac.canSwitchGunnerFreeLook(player)) {
         ac.switchGunnerFreeLookMode();
      }

      if (this.KeyParachuting.isKeyDown()) {
         if (ac.canParachuting(player)) {
            pc.parachuting = true;
            send = true;
         } else if (ac.canRepelling(player)) {
            pc.parachuting = true;
            send = true;
         } else {
            playSoundNG();
         }
      }

      if (send) {
         W_Network.sendToServer(pc);
      }
   }
}
