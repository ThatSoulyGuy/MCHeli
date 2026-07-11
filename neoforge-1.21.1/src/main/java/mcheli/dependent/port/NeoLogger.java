package mcheli.dependent.port;

import mcheli.agnostic.spi.Logger;

/**
 * Bridges the agnostic {@link Logger} SPI to slf4j. The agnostic layer uses {@code String.format}-style ({@code %s})
 * format strings (mirroring the reference {@code MCH_Lib.Log}), so format them here before handing to slf4j.
 */
public final class NeoLogger implements Logger {
    private final org.slf4j.Logger delegate;

    public NeoLogger(org.slf4j.Logger delegate) {
        this.delegate = delegate;
    }

    @Override public void info(String fmt, Object... args)  { delegate.info(fmt(fmt, args)); }
    @Override public void warn(String fmt, Object... args)  { delegate.warn(fmt(fmt, args)); }
    @Override public void debug(String fmt, Object... args) { delegate.debug(fmt(fmt, args)); }
    @Override public void error(String msg, Throwable t)    { delegate.error(msg, t); }

    private static String fmt(String fmt, Object... args) {
        if (args == null || args.length == 0) {
            return fmt;
        }
        try {
            return String.format(fmt, args);
        } catch (RuntimeException e) {
            return fmt; // never let a bad format string crash logging
        }
    }
}
