package mcheli.dependent.port;

import mcheli.agnostic.spi.RandomSource;

/**
 * The concrete {@link RandomSource} port: adapts a Minecraft {@link net.minecraft.util.RandomSource}
 * to the agnostic RNG surface. The agnostic ballistics / HUD-jitter code draws through this so it never
 * touches {@code java.util.Random} or {@code level.random} directly.
 */
public final class NeoRandomSource implements RandomSource {
    private final net.minecraft.util.RandomSource r;

    public NeoRandomSource(net.minecraft.util.RandomSource r) { this.r = r; }

    @Override public double  nextDouble()        { return r.nextDouble(); }
    @Override public float   nextFloat()         { return r.nextFloat(); }
    @Override public int     nextInt(int bound)  { return r.nextInt(bound); }
    @Override public double  nextGaussian()      { return r.nextGaussian(); }
    @Override public boolean nextBoolean()       { return r.nextBoolean(); }
}
