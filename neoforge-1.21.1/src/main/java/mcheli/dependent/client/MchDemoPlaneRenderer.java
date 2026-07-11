package mcheli.dependent.client;

import mcheli.dependent.entity.MchDemoPlane;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Renders the demo plane using the real {@code planes/a-10.mqo} model + {@code a-10.png} skin (matching
 * {@code MchDemoPlane}'s a-10 config).
 */
public class MchDemoPlaneRenderer extends MchModelEntityRenderer<MchDemoPlane> {
    public MchDemoPlaneRenderer(EntityRendererProvider.Context context) {
        super(context, "planes/a-10", "textures/planes/a-10.png");
    }
}
