package mcheli.dependent.client.particle;

import mcheli.dependent.particle.MuzzleFxOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The MCHeli weapon "fx" particle: a soft, fullbright, camera-facing puff whose colour, size and lifetime are all taken
 * from the config-carrying {@link MuzzleFxOptions} — so the M230's specific orange muzzle flash ({@code 254,159,84})
 * shows, and a rocket's white smoke trail reads as smoke. Standard alpha blend (the reference flash/smoke are NOT
 * additive); the puff fades out over its life. A single particle type serves muzzle flash, muzzle smoke, and the
 * projectile trail — the caller just varies the config-derived colour/size/lifetime/velocity.
 *
 * <p>Fidelity note: the reference used the 16-frame explosion sheet for the flash and the 8-frame smoke sheet for
 * smoke; here both use vanilla's soft {@code generic} sprites, so the exact sheet animation is approximated while the
 * config-driven colour/size/lifetime are exact.
 */
@OnlyIn(Dist.CLIENT)
public class MuzzleFxParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private final float baseAlpha;

    protected MuzzleFxParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                               MuzzleFxOptions o, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        int argb = o.argb();
        this.baseAlpha = ((argb >>> 24) & 0xFF) / 255.0F;
        this.alpha = this.baseAlpha;
        this.rCol = ((argb >>> 16) & 0xFF) / 255.0F;
        this.gCol = ((argb >>> 8) & 0xFF) / 255.0F;
        this.bCol = (argb & 0xFF) / 255.0F;
        this.quadSize = Math.max(0.02F, o.size());
        this.lifetime = Math.max(1, o.maxAge());
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.friction = 0.9F;   // reference smoke motion damping
        this.gravity = 0.0F;    // muzzle fx / smoke don't fall
        this.hasPhysics = false;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0; // fullbright — matches the reference's setBrightness(15728880)
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        this.alpha = this.baseAlpha * (1.0F - (float) this.age / (float) this.lifetime);
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<MuzzleFxOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(MuzzleFxOptions options, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new MuzzleFxParticle(level, x, y, z, xd, yd, zd, options, this.sprites);
        }
    }
}
