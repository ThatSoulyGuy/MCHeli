package mcheli.dependent.client.particle;

import mcheli.dependent.particle.MchSmokeOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The MCHeli soft smoke — a faithful port of {@code MCH_EntityParticleSmoke}: a camera-facing billboard that plays the
 * 8-frame {@code smoke.png} strip (a 64×8 HORIZONTAL sheet) once across its life, grey drifting toward white, rising and
 * growing as it dissipates. The sheet is one atlas sprite; this particle sub-samples the current frame's 1/8 column by
 * overriding the U getters. Used for rotor down-wash and damage smoke in place of vanilla {@code CLOUD}/{@code LARGE_SMOKE}.
 */
@OnlyIn(Dist.CLIENT)
public class MchSmokeParticle extends TextureSheetParticle {

    private TextureAtlasSprite sheet;
    private int frame; // 0..7 within the horizontal strip
    private final float baseAlpha;
    private final float maxSize;

    protected MchSmokeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                               MchSmokeOptions o, SpriteSet sprites) {
        super(level, x, y, z);
        this.setSprite(sprites.get(0, 1));
        this.sheet = this.sprite;
        int argb = o.argb();
        this.baseAlpha = ((argb >>> 24) & 0xFF) / 255.0F;
        this.alpha = this.baseAlpha;
        this.rCol = ((argb >>> 16) & 0xFF) / 255.0F;
        this.gCol = ((argb >>> 8) & 0xFF) / 255.0F;
        this.bCol = (argb & 0xFF) / 255.0F;
        this.quadSize = Math.max(0.1F, o.size());
        this.maxSize = this.quadSize * 2.5F; // reference diffusible growth toward a max
        this.lifetime = Math.max(1, o.maxAge());
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.0F;
        this.friction = 0.92F;
        this.hasPhysics = false;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0; // the reference brightens smoke (samples sky at posY+3000) so it stays visible against terrain
    }

    @Override
    public void tick() {
        super.tick(); // age + motion (friction) + death at lifetime
        float t = (float) this.age / this.lifetime;
        this.frame = Math.min(7, (int) (t * 8.0F));
        this.yd += 0.004; // rises as it ages (reference motionY += 0.02 past a threshold, gentler here)
        if (this.quadSize < this.maxSize) {
            this.quadSize += (this.maxSize - this.quadSize) * 0.08F; // ease toward the max size (billow out)
        }
        float w = Math.min(1.0F, this.rCol + 0.012F); // drift toward white (reference toWhite)
        this.rCol = this.gCol = this.bCol = w;
        this.alpha = t > 0.7F ? this.baseAlpha * (1.0F - t) / 0.3F : this.baseAlpha; // fade out over the last 30%
    }

    private float uSpan() {
        return this.sheet.getU1() - this.sheet.getU0();
    }

    @Override
    protected float getU0() {
        return this.sheet.getU0() + uSpan() * (this.frame / 8.0F);
    }

    @Override
    protected float getU1() {
        return this.sheet.getU0() + uSpan() * ((this.frame + 1) / 8.0F);
    }

    @Override
    protected float getV0() {
        return this.sheet.getV0();
    }

    @Override
    protected float getV1() {
        return this.sheet.getV1();
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<MchSmokeOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(MchSmokeOptions options, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new MchSmokeParticle(level, x, y, z, xd, yd, zd, options, this.sprites);
        }
    }
}
