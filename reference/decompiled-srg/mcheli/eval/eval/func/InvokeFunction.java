/*
 * Decompiled with CFR 0.152.
 */
package mcheli.eval.eval.func;

import java.lang.reflect.Method;
import mcheli.eval.eval.func.Function;

/*
 * Exception performing whole class analysis ignored.
 */
public class InvokeFunction
implements Function {
    public long evalLong(Object object, String name, Long[] args) throws Throwable {
        if (object == null) {
            return 0L;
        }
        Object r = InvokeFunction.callMethod((Object)object, (String)name, (Object[])args);
        return ((Number)r).longValue();
    }

    public double evalDouble(Object object, String name, Double[] args) throws Throwable {
        if (object == null) {
            return 0.0;
        }
        Object r = InvokeFunction.callMethod((Object)object, (String)name, (Object[])args);
        return ((Number)r).doubleValue();
    }

    public Object evalObject(Object object, String name, Object[] args) throws Throwable {
        if (object == null) {
            return null;
        }
        return InvokeFunction.callMethod((Object)object, (String)name, (Object[])args);
    }

    public static Object callMethod(Object obj, String name, Object[] args) throws Exception {
        Class<?> c = obj.getClass();
        Class[] types = new Class[args.length];
        for (int i = 0; i < types.length; ++i) {
            types[i] = args[i].getClass();
        }
        Method m = c.getMethod(name, types);
        return m.invoke(obj, args);
    }
}

