package mcheli.dependent.client;

import java.util.ArrayList;
import java.util.List;
import mcheli.MCHeli;
import mcheli.dependent.entity.AbstractMchVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Lights living entities caught in a vehicle's search-light beam — the port of {@code MCH_ClientEventHook
 * .renderLivingEventPre} (:103-106), which overrode the lightmap coordinate of every living entity with
 * {@link AbstractMchVehicle#searchLightValue}. The list of vehicles with a lit search light is rebuilt once per frame
 * (reference {@code haveSearchLightAircraft}, rebuilt in {@code onRenderTickPre}); {@code EntityRenderDispatcherMixin}
 * asks {@link #light} for each living entity's packed light.
 *
 * <p>DELIBERATE divergence: the reference set the lightmap to {@code (value, 240)}, i.e. full sky light for every
 * living entity while any search-light aircraft existed (lighting mobs in caves as if under open sky) and letting the
 * last aircraft in the list win. This keeps the entity's own light and only raises its block light to the brightest
 * beam on it.
 */
@EventBusSubscriber(modid = MCHeli.MODID, value = Dist.CLIENT)
public final class MchSearchLights {
    private MchSearchLights() {}

    /** Vehicles within this many blocks of the camera are considered (the longest bundled beam is 90 blocks). */
    private static final double RANGE = 128.0;

    private static final List<AbstractMchVehicle> LIT = new ArrayList<>();

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        LIT.clear();
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc.getCameraEntity();
        if (mc.level == null || cam == null) {
            return;
        }
        for (AbstractMchVehicle v : mc.level.getEntitiesOfClass(AbstractMchVehicle.class, cam.getBoundingBox().inflate(RANGE))) {
            if (v.isSearchLightOn()) {
                LIT.add(v);
            }
        }
    }

    /** {@code packedLight} raised by the brightest search-light beam on {@code entity} (unchanged when none is). */
    public static int light(Entity entity, int packedLight) {
        if (LIT.isEmpty()) {
            return packedLight;
        }
        float best = 0.0F;
        for (AbstractMchVehicle v : LIT) {
            best = Math.max(best, v.searchLightValue(entity));
        }
        int block = (int) (best / 16.0F); // lightmap coordinate 0..240 -> block light 0..15
        int current = LightTexture.block(packedLight);
        return block > current ? LightTexture.pack(block, LightTexture.sky(packedLight)) : packedLight;
    }
}
