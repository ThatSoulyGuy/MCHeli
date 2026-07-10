package mcheli.wrapper;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderLivingEvent.Specials.Post;
import net.minecraftforge.client.event.RenderLivingEvent.Specials.Pre;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent.Unload;

public class W_ClientEventHook {
   @SubscribeEvent
   public void onEvent_MouseEvent(MouseEvent event) {
      this.mouseEvent(event);
   }

   public void mouseEvent(MouseEvent event) {
   }

   @SubscribeEvent
   public void onEvent_renderLivingEventSpecialsPre(Pre event) {
      this.renderLivingEventSpecialsPre(event);
   }

   public void renderLivingEventSpecialsPre(Pre event) {
   }

   @SubscribeEvent
   public void onEvent_renderLivingEventSpecialsPost(Post event) {
      this.renderLivingEventSpecialsPost(event);
   }

   public void renderLivingEventSpecialsPost(Post event) {
   }

   @SubscribeEvent
   public void onEvent_renderLivingEventPre(net.minecraftforge.client.event.RenderLivingEvent.Pre event) {
      this.renderLivingEventPre(event);
   }

   public void renderLivingEventPre(net.minecraftforge.client.event.RenderLivingEvent.Pre event) {
   }

   @SubscribeEvent
   public void onEvent_renderLivingEventPost(net.minecraftforge.client.event.RenderLivingEvent.Post event) {
      this.renderLivingEventPost(event);
   }

   public void renderLivingEventPost(net.minecraftforge.client.event.RenderLivingEvent.Post event) {
   }

   @SubscribeEvent
   public void onEvent_renderPlayerPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
      this.renderPlayerPre(event);
   }

   public void renderPlayerPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre event) {
   }

   @SubscribeEvent
   public void Event_renderPlayerPost(net.minecraftforge.client.event.RenderPlayerEvent.Post event) {
      this.renderPlayerPost(event);
   }

   public void renderPlayerPost(net.minecraftforge.client.event.RenderPlayerEvent.Post event) {
   }

   @SubscribeEvent
   public void onEvent_WorldEventUnload(Unload event) {
      this.worldEventUnload(event);
   }

   public void worldEventUnload(Unload event) {
   }

   @SubscribeEvent
   public void onEvent_EntityJoinWorldEvent(EntityJoinWorldEvent event) {
      this.entityJoinWorldEvent(event);
   }

   public void entityJoinWorldEvent(EntityJoinWorldEvent event) {
   }
}
