package mcheli.agnostic.model;

/**
 * Thrown when a {@code .mqo}/{@code .obj} model fails to parse — the agnostic stand-in for Forge's
 * {@code ModelFormatException}, kept unchecked so the parsers mirror the reference control flow without
 * threading checked exceptions through the SPI.
 */
public class ModelFormatException extends RuntimeException {
    public ModelFormatException(String message) {
        super(message);
    }

    public ModelFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
