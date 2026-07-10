package mcheli.agnostic.spi;

/** Logging sink replacing the reference's {@code MCH_Lib.Log}/{@code DbgLog} (which reached FML). */
public interface Logger {
    void info(String fmt, Object... args);
    void warn(String fmt, Object... args);
    void debug(String fmt, Object... args);
    void error(String msg, Throwable t);
}
