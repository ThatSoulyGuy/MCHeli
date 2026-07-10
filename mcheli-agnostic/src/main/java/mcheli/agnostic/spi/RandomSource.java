package mcheli.agnostic.spi;

/** Agnostic RNG so ballistics spread / HUD jitter stay off {@code java.util.Random} + world.rand
 *  directly, keeping the agnostic layer deterministic and unit-testable. */
public interface RandomSource {
    double nextDouble();
    float nextFloat();
    int nextInt(int bound);
    double nextGaussian();
    boolean nextBoolean();
}
