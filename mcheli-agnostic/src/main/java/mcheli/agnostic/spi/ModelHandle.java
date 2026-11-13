package mcheli.agnostic.spi;

/**
 * Opaque render-model handle so definition holders can carry a model reference without importing any
 * platform model type. Loaded via {@link ResourceSource#loadModel}; never dereferenced in the agnostic
 * layer (the dependent renderer downcasts it to {@code mcheli.agnostic.model.MchModel}).
 */
public interface ModelHandle {
}
