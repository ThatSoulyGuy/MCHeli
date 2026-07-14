package mcheli.dependent.client.particle;

import mcheli.dependent.particle.MchExplodeOptions;
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
 * The MCHeli big explosion fireball — a faithful port of {@code MCH_EntityParticleExplode}: a stationary, camera-facing,
 * fullbright billboard that plays vanilla's 16-frame {@code explosion.png} sheet (a 4×4 grid) ONCE across its lifetime,
 * at the config-scaled size {@code 2·particleSize} carried by {@link MchExplodeOptions}. The sheet is stitched into the
 * particle atlas as a single 128×128 sprite; this particle sub-samples the current frame's cell by overriding the UV
 * getters (so no image-split tooling is needed). Several of these, spread over the blast, give the 46cm's enormous
 * rolling fireball the tiny vanilla {@code EXPLOSION_EMITTER} could never.
 */
@OnlyIn(Dist.CLIENT)
public class MchExplodeParticle extends TextureSheetParticle {

    private TextureAtlasSprite sheet;
    private int frame; // 0..15 within the 4×4 grid, advanced by age

    protected MchExplodeParticle(ClientLevel level, double x, double y, double z, MchExplodeOptions o, SpriteSet sprites) {
        super(level, x, y, z);
        this.setSprite(sprites.get(0, 1)); // the single 128×128 explosion sprite
        this.sheet = this.sprite;
        int argb = o.argb();
        this.alpha = ((argb >>> 24) & 0xFF) / 255.0F;
        this.rCol = ((argb >>> 16) & 0xFF) / 255.0F;
        this.gCol = ((argb >>> 8) & 0xFF) / 255.0F;
        this.bCol = (argb & 0xFF) / 255.0F;
        this.quadSize = Math.max(0.5F, 2.0F * o.size()); // reference render extent f10 = 2·particleSize
        this.lifetime = Math.max(1, o.maxAge());
        this.xd = this.yd = this.zd = 0.0; // the reference explode billboard does not move
        this.gravity = 0.0F;
        this.hasPhysics = false;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0; // fullbright — the reference setBrightness(15728880)
    }

    @Override
    public void tick() {
        super.tick(); // advances age + removes at lifetime
        this.frame = Math.min(15, (int) ((float) this.age * 16.0F / this.lifetime));
    }

    // Sub-sample the current frame's cell from the 4×4 grid within the sprite's atlas region.
    private float uSpan() {
        return this.sheet.getU1() - this.sheet.getU0();
    }

    private float vSpan() {
        return this.sheet.getV1() - this.sheet.getV0();
    }

    @Override
    protected float getU0() {
        return this.sheet.getU0() + uSpan() * ((this.frame % 4) / 4.0F);
    }

    @Override
    protected float getU1() {
        return this.sheet.getU0() + uSpan() * ((this.frame % 4 + 1) / 4.0F);
    }

    @Override
    protected float getV0() {
        return this.sheet.getV0() + vSpan() * ((this.frame / 4) / 4.0F);
    }

    @Override
    protected float getV1() {
        return this.sheet.getV0() + vSpan() * ((this.frame / 4 + 1) / 4.0F);
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<MchExplodeOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(MchExplodeOptions options, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new MchExplodeParticle(level, x, y, z, options, this.sprites);
        }
    }
}
