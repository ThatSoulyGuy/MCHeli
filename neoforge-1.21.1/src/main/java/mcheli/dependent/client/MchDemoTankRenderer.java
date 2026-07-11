package mcheli.dependent.client;

import mcheli.dependent.entity.MchDemoTank;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Renders the demo tank using the real {@code tanks/m1a2.mqo} model + {@code m1a2.png} skin (matching
 * {@code MchDemoTank}'s m1a2 config). Hull renders in rest pose; the turret is a separate {@code $}-group whose
 * independent aim is a later increment.
 */
public class MchDemoTankRenderer extends MchModelEntityRenderer<MchDemoTank> {
    public MchDemoTankRenderer(EntityRendererProvider.Context context) {
        super(context, "tanks/m1a2", "textures/tanks/m1a2.png");
    }
}
