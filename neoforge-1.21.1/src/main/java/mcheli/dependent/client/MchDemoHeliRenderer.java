package mcheli.dependent.client;

import mcheli.dependent.entity.MchDemoHeli;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Renders the demo helicopter using the real {@code helicopters/ah-64.mqo} model + {@code ah-64.png} skin
 * (matching {@code MchDemoHeli}'s ah-64 config). Body renders in rest pose; animated rotor blades come later.
 */
public class MchDemoHeliRenderer extends MchModelEntityRenderer<MchDemoHeli> {
    public MchDemoHeliRenderer(EntityRendererProvider.Context context) {
        super(context, "helicopters/ah-64", "textures/helicopters/ah-64.png");
    }
}
