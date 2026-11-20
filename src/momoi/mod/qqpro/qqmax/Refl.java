package momoi.mod.qqpro.qqmax;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Small reflection helpers shared by the ported features. */
public final class Refl {
    private Refl() {}

    public static Field findField(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    public static Object getField(Object obj, String name) {
        if (obj == null) return null;
        try {
            Field f = findField(obj.getClass(), name);
            return f == null ? null : f.get(obj);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean setField(Object obj, String name, Object value) {
        if (obj == null) return false;
        try {
            Field f = findField(obj.getClass(), name);
            if (f == null) return false;
            f.set(obj, value);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int getInt(Object obj, String name, int def) {
        Object v = getField(obj, name);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }

    public static long getLong(Object obj, String name, long def) {
        Object v = getField(obj, name);
        return v instanceof Number ? ((Number) v).longValue() : def;
    }

    public static String getString(Object obj, String name) {
        Object v = getField(obj, name);
        return v instanceof String ? (String) v : null;
    }

    public static Object call(Object obj, String methodName) {
        if (obj == null) return null;
        Class<?> c = obj.getClass();
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(methodName);
                m.setAccessible(true);
                return m.invoke(obj);
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    public static String callString(Object obj, String methodName) {
        Object v = call(obj, methodName);
        return v instanceof String ? (String) v : null;
    }
}
