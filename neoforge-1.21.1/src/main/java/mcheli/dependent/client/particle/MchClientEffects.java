package mcheli.dependent.client.particle;

import java.util.concurrent.atomic.AtomicInteger;
import mcheli.dependent.entity.MchCartridge;
import mcheli.dependent.particle.ClientboundCartridgePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-only entities: the reference spawns shell casings ({@code MCH_EntityCartridge.spawnCartridge}) and the burning
 * debris of a blast ({@code MCH_Explosion.effectExplosion}'s {@code MCH_EntityFlare}s) straight into the CLIENT world,
 * where only that client ever ticks and draws them. They never exist on the server, so they cost no tracking traffic.
 *
 * <p>Each gets a negative id: the server never hands those out, so a casing can never evict (or be evicted by) a
 * real entity that arrives with the same id ({@code ClientLevel.addEntity} first removes any entity holding it).
 */
@OnlyIn(Dist.CLIENT)
public final class MchClientEffects {
    private MchClientEffects() {}

    private static final AtomicInteger NEXT_ID = new AtomicInteger(-1_000_000);

    /** Add a client-only {@code entity} to its client level (no-op if it is not on one). */
    public static void add(Entity entity) {
        if (!(entity.level() instanceof ClientLevel level)) {
            return;
        }
        int id = NEXT_ID.decrementAndGet();
        if (id > -1_000_000) { // wrapped around after ~2 billion effects: restart the negative range
            NEXT_ID.set(-1_000_000);
            id = NEXT_ID.decrementAndGet();
        }
        entity.setId(id);
        level.addEntity(entity);
    }

    /** {@link ClientboundCartridgePayload} handler: eject the described casing on this client. */
    public static void spawnCartridge(ClientboundCartridgePayload p) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        add(MchCartridge.create(level, p.cfg(), new Vec3(p.x(), p.y(), p.z()), p.gunYaw(), p.gunPitch(),
            new Vec3(p.mx(), p.my(), p.mz())));
    }
}
