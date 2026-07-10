package mcheli.agnostic.info;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * Line reader over a {@link Reader} supplied by {@code ResourceSource}. The reference opened a
 * {@code java.io.File} directly against the mod's source path; the port reads through the resource
 * system instead, so this class no longer touches the filesystem.
 */
public class MCH_InputFile {
    public BufferedReader br = null;

    /** Wrap a reader (typically {@code ResourceSource.openUtf8(path)}). */
    public boolean open(Reader reader) {
        this.close();
        if (reader == null) {
            return false;
        }
        this.br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader);
        return true;
    }

    public String readLine() {
        try {
            return this.br != null ? this.br.readLine() : null;
        } catch (IOException e) {
            return null;
        }
    }

    public void close() {
        try {
            if (this.br != null) {
                this.br.close();
            }
        } catch (IOException e) {
        }
        this.br = null;
    }
}
