package mcheli.agnostic.spi;

import java.io.Reader;
import java.util.List;

/**
 * Read-side access to definition files and assets, decoupling every {@code *Info}/{@code *InfoManager}
 * loader from {@code java.io.File}-against-MC-roots and {@code ResourceLocation}/model loading.
 */
public interface ResourceSource {
    /** List the definition file names under a directory (e.g. "helicopters", "weapons"). */
    List<String> list(String dir);

    /** Open a UTF-8 text resource (a key=value definition file). Caller closes it. */
    Reader openUtf8(String path);

    boolean exists(String path);

    ModelHandle loadModel(String path);
}
