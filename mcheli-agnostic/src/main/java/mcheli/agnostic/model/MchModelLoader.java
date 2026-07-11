package mcheli.agnostic.model;

import java.io.InputStream;
import java.util.Locale;

/**
 * Selects the {@code .mqo}/{@code .obj} parser by file extension. The dependent {@code ResourceSource.loadModel}
 * opens the resource {@link InputStream} and delegates here; the returned {@link MchModel} is carried as a
 * {@code ModelHandle} on the {@code *Info} definitions and downcast by the dependent renderer.
 */
public final class MchModelLoader {
    private MchModelLoader() {}

    public static MchModel load(String path, InputStream in) throws ModelFormatException {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mqo")) {
            return new MqoModel(path, in);
        } else if (lower.endsWith(".obj")) {
            return new ObjModel(path, in);
        } else {
            throw new ModelFormatException("Unknown model format (expected .mqo/.obj): " + path);
        }
    }
}
