package mcheli.agnostic.spi;

/**
 * Opaque render-model handle so definition holders can carry a model reference without importing any
 * platform model type. Loaded via {@link ResourceSource#loadModel}, registered via {@link Registrar};
 * never dereferenced in the agnostic layer (the dependent renderer downcasts it).
 */
public interface ModelHandle {
}
