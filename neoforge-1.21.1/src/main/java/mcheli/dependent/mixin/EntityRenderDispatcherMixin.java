package mcheli.dependent.mixin;

import mcheli.dependent.client.MchSearchLights;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a vehicle's search light brighten the living entities in its beam ({@link MchSearchLights}) — the reference
 * changed the lightmap coordinate in {@code RenderLivingEvent.Pre}, and NeoForge's event no longer lets a listener
 * change the light, so the packed light is adjusted where the level renderer asks for it.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "getPackedLightCoords", at = @At("RETURN"), cancellable = true)
    private void mcheli$searchLight(Entity entity, float partialTicks, CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof LivingEntity) {
            int lit = MchSearchLights.light(entity, cir.getReturnValueI());
            if (lit != cir.getReturnValueI()) {
                cir.setReturnValue(lit);
            }
        }
    }
}
