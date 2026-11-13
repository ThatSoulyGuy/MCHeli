package mcheli.dependent.mixin;

import mcheli.dependent.client.MchClientRotation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the local player's raw mouse turn to {@link MchClientRotation} before {@code Entity.turn} clamps the pitch to
 * ±90. The mouse-flown aircraft stick is built from that turn, as the reference built it from the raw
 * {@code mouseHelper} delta ({@code MCH_ClientCommonTickHandler.updateMouseDelta}:250-292): measured from the player's
 * clamped rotation instead, a steep climb or dive (the player is held at the hull pitch, near ±90) lost most of its
 * vertical input, and any code that set the player's pitch would have read as a stick deflection. Client only; the
 * call does nothing for any entity but the local player.
 */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @Inject(method = "turn(DD)V", at = @At("HEAD"))
    private void mcheli$rawMouseTurn(double yRot, double xRot, CallbackInfo ci) {
        MchClientRotation.onLocalPlayerTurn((Entity) (Object) this, yRot, xRot);
    }
}
