package mcheli.agnostic.util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Pure parse/format/file helpers relocated from the reference's platform-bound {@code MCH_Lib}. */
public final class MchUtil {
    private MchUtil() {}

    /** Locale-tolerant double parse (accepts ',' as decimal separator), as the reference did. */
    public static double parseDouble(String s) {
        return s == null ? 0.0 : Double.parseDouble(s.replace(',', '.'));
    }

    public static String getTime() {
        return new SimpleDateFormat("HH:mm:ss:SSS").format(new Date());
    }

    public static String[] listupFileNames(String path) {
        return new File(path).list();
    }
}
