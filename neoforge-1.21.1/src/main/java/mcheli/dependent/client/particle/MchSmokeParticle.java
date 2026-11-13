package mcheli.dependent.client.particle;

import java.util.List;
import mcheli.dependent.particle.MchSmokeOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The MCHeli soft smoke — the port of {@code MCH_EntityParticleSmoke} and {@code MCH_EntityParticleSplash}: a
 * camera-facing billboard that plays the 8-frame {@code smoke.png} strip (a 64×8 HORIZONTAL sheet) once across its
 * life. The sheet is one atlas sprite; this particle sub-samples the current frame's 1/8 column by overriding the U
 * getters.
 *
 * <p>Without flags it is the port's general-purpose smoke (rotor down-wash, damage smoke): it rises, billows out toward
 * 2.5×, pales toward white and fades over its last 30%. The {@link MchSmokeOptions} flags select the reference
 * behaviours instead: plain and diffusible smoke (explosions, flares, the marker beacon), the falling splash, and the
 * {@code toWhite} drift. A flagged puff keeps its colour unless {@code TO_WHITE} is set, as in the reference.
 */
@OnlyIn(Dist.CLIENT)
public class MchSmokeParticle extends TextureSheetParticle {

    private TextureAtlasSprite sheet;
    private int frame; // 0..7 within the horizontal strip
    private final float baseAlpha;
    private final float maxSize;
    private final MchSmokeOptions opts;
    private final boolean reference; // any of PLAIN / DIFFUSIBLE / SPLASH: run the reference tick

    protected MchSmokeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                               MchSmokeOptions o, SpriteSet sprites) {
        super(level, x, y, z);
        this.setSprite(sprites.get(0, 1));
        this.sheet = this.sprite;
        this.opts = o;
        this.reference = o.has(MchSmokeOptions.PLAIN) || o.has(MchSmokeOptions.DIFFUSIBLE)
            || o.has(MchSmokeOptions.SPLASH);
        int argb = o.argb();
        this.baseAlpha = ((argb >>> 24) & 0xFF) / 255.0F;
        this.alpha = this.baseAlpha;
        this.rCol = ((argb >>> 16) & 0xFF) / 255.0F;
        this.gCol = ((argb >>> 8) & 0xFF) / 255.0F;
        this.bCol = (argb & 0xFF) / 255.0F;
        if (o.has(MchSmokeOptions.DIFFUSIBLE)) {
            // Reference MCH_ParticlesUtil.spawnParticle: diffusible starts at size*0.2 and grows to size*2.
            this.quadSize = Math.max(0.01F, o.size() * 0.2F);
            this.maxSize = o.size() * 2.0F;
        } else if (this.reference) {
            this.quadSize = Math.max(0.01F, o.size());
            this.maxSize = this.quadSize;
        } else {
            this.quadSize = Math.max(0.1F, o.size());
            this.maxSize = this.quadSize * 2.5F; // the port's general smoke billows toward 2.5x
        }
        if (o.maxAge() > 0) {
            this.lifetime = o.maxAge();
        } else {
            // Reference constructors: MCH_EntityParticleSmoke 16/(r*0.8+0.2)+2, MCH_EntityParticleSplash 80/(...)+2.
            double base = o.has(MchSmokeOptions.SPLASH) ? 80.0 : 16.0;
            this.lifetime = (int) (base / (this.random.nextFloat() * 0.8 + 0.2)) + 2;
        }
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.gravity = 0.0F;
        this.friction = 0.92F;
        this.hasPhysics = false; // the reference kinds collide through moveReference, not vanilla's sticky move
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        // The reference samples the light 3000 blocks up (full sky light, no block light) so smoke stays visible
        // against terrain but still darkens at night. The port's general smoke keeps its old full-bright look.
        return this.reference ? LightTexture.pack(0, 15) : 0xF000F0;
    }

    @Override
    public void tick() {
        if (this.reference) {
            tickReference();
            return;
        }
        super.tick(); // age + motion (friction) + death at lifetime
        float t = (float) this.age / this.lifetime;
        this.frame = Math.min(7, (int) (t * 8.0F));
        this.yd += 0.004; // rises as it ages (reference motionY += 0.02 past a threshold, gentler here)
        if (this.quadSize < this.maxSize) {
            this.quadSize += (this.maxSize - this.quadSize) * 0.08F; // ease toward the max size (billow out)
        }
        // Lighten as it dissipates: each channel on its own, so a tinted puff pales instead of collapsing to grey
        // (all three channels used to be set from red on the first tick).
        this.rCol = Math.min(1.0F, this.rCol + 0.012F);
        this.gCol = Math.min(1.0F, this.gCol + 0.012F);
        this.bCol = Math.min(1.0F, this.bCol + 0.012F);
        toWhite();
        this.alpha = t > 0.7F ? this.baseAlpha * (1.0F - t) / 0.3F : this.baseAlpha; // fade out over the last 30%
    }

    /** Reference {@code MCH_EntityParticleSmoke.onUpdate} / {@code MCH_EntityParticleSplash.onUpdate}. */
    private void tickReference() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        boolean splash = this.opts.has(MchSmokeOptions.SPLASH);
        if (this.age < this.lifetime) {
            this.frame = Math.min(7, (int) (8.0F * this.age / this.lifetime));
            this.age++;
        } else {
            this.remove();
            if (!splash) {
                return; // the splash still moves on the tick it dies (reference setDead without a return)
            }
        }
        if (splash) {
            tickSplash();
            return;
        }
        if (this.opts.has(MchSmokeOptions.DIFFUSIBLE) && this.quadSize < this.maxSize) {
            this.quadSize = Math.min(this.maxSize, this.quadSize + 0.08F); // reference particleScale += 0.8
        }
        toWhite();
        // Reference motionYUpAge defaults to 2.0 (never reached) and gravity to 0, so no vertical force here.
        moveReference(this.xd, this.yd, this.zd);
        if (this.opts.has(MchSmokeOptions.DIFFUSIBLE)) {
            this.xd *= 0.96;
            this.yd *= 0.96;
            this.zd *= 0.96;
        } else {
            this.xd *= 0.9; // the reference does not damp a plain puff's vertical motion
            this.zd *= 0.9;
        }
    }

    /** Reference {@code MCH_EntityParticleSplash.onUpdate}: fall, splash on dropping back into water, die on land. */
    private void tickSplash() {
        this.yd -= 0.06F;
        boolean beforeInWater = isWaterAt(this.x, this.y, this.z);
        moveReference(this.xd, this.yd, this.zd);
        boolean nowInWater = isWaterAt(this.x, this.y, this.z);
        if (this.yd < -0.6 && !beforeInWater && nowInWater) {
            double p = -this.yd * 10.0;
            for (int i = 0; i < p; i++) {
                this.level.addParticle(ParticleTypes.SPLASH,
                    this.x + 0.5 + (this.random.nextDouble() - 0.5) * 2.0, this.y + this.random.nextDouble(),
                    this.z + 0.5 + (this.random.nextDouble() - 0.5) * 2.0,
                    (this.random.nextDouble() - 0.5) * 2.0, 4.0, (this.random.nextDouble() - 0.5) * 2.0);
                this.level.addParticle(ParticleTypes.BUBBLE,
                    this.x + 0.5 + (this.random.nextDouble() - 0.5) * 2.0, this.y - this.random.nextDouble(),
                    this.z + 0.5 + (this.random.nextDouble() - 0.5) * 2.0,
                    (this.random.nextDouble() - 0.5) * 2.0, -0.5, (this.random.nextDouble() - 0.5) * 2.0);
            }
        } else if (this.onGround) {
            this.remove();
        }
        this.xd *= 0.9;
        this.zd *= 0.9;
    }

    /** Reference {@code W_WorldFunc.isBlockWater} at {@code (int)(pos + 0.5)}, floored so it holds at negative
     *  coordinates too. */
    private boolean isWaterAt(double px, double py, double pz) {
        return this.level.getFluidState(BlockPos.containing(Mth.floor(px + 0.5), Mth.floor(py + 0.5),
            Mth.floor(pz + 0.5))).is(FluidTags.WATER);
    }

    /** Reference {@code toWhite} (MCH_EntityParticleSmoke:31-39): only a clearly coloured puff (channel spread
     *  above 0.2) drifts, each channel easing 1.6% per tick toward the brightest one. */
    private void toWhite() {
        if (!this.opts.has(MchSmokeOptions.TO_WHITE)) {
            return;
        }
        float mn = Math.min(Math.min(this.bCol, this.gCol), this.rCol);
        float mx = Math.max(Math.max(this.bCol, this.gCol), this.rCol);
        if (mx - mn > 0.2F) {
            this.rCol += (mx - this.rCol) * 0.016F;
            this.gCol += (mx - this.gCol) * 0.016F;
            this.bCol += (mx - this.bCol) * 0.016F;
        }
    }

    /** The reference {@code moveEntity}: clip the move against block collision boxes and stop only the blocked axes.
     *  (Vanilla's {@code Particle.move} instead freezes a particle for good once its fall is blocked.) */
    private void moveReference(double dx, double dy, double dz) {
        double ox = dx;
        double oy = dy;
        double oz = dz;
        if ((dx != 0.0 || dy != 0.0 || dz != 0.0) && dx * dx + dy * dy + dz * dz < 10000.0) {
            Vec3 v = Entity.collideBoundingBox(null, new Vec3(dx, dy, dz), this.getBoundingBox(), this.level, List.of());
            dx = v.x;
            dy = v.y;
            dz = v.z;
        }
        if (dx != 0.0 || dy != 0.0 || dz != 0.0) {
            this.setBoundingBox(this.getBoundingBox().move(dx, dy, dz));
            this.setLocationFromBoundingbox();
        }
        this.onGround = oy != dy && oy < 0.0;
        if (ox != dx) {
            this.xd = 0.0;
        }
        if (oy != dy) {
            this.yd = 0.0;
        }
        if (oz != dz) {
            this.zd = 0.0;
        }
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
