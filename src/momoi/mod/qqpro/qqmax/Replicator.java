package momoi.mod.qqpro.qqmax;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflection-based deep copy for kernel nativeinterface data objects
 * (MsgElement and its sub-elements), mirroring the module's ElementReplicator.
 * Used by the anti-recall cache so later mutation of the live record
 * (revoke replacement) cannot corrupt the cached original.
 */
public final class Replicator {
    private Replicator() {}

    public static Object deepCopy(Object obj) {
        return copy(obj, 0);
    }

    private static Object copy(Object obj, int depth) {
        if (obj == null || depth > 8) return obj;
        Class<?> cls = obj.getClass();
        if (obj instanceof String || obj instanceof Number || obj instanceof Boolean
                || obj instanceof Character || cls.isEnum()) {
            return obj;
        }
        if (obj instanceof List) {
            List src = (List) obj;
            ArrayList out = new ArrayList(src.size());
            for (Object e : src) out.add(copy(e, depth + 1));
            return out;
        }
        if (cls.isArray()) {
            int n = Array.getLength(obj);
            Object out = Array.newInstance(cls.getComponentType(), n);
            for (int i = 0; i < n; i++) Array.set(out, i, copy(Array.get(obj, i), depth + 1));
            return out;
        }
        String name = cls.getName();
        if (!name.startsWith("com.tencent.qqnt.kernel.nativeinterface")
                && !name.startsWith("com.tencent.qqnt.kernelpublic")) {
            return obj; // unknown foreign object: share the reference
        }
        try {
            Object out = cls.getDeclaredConstructor().newInstance();
            Class<?> c = cls;
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    f.set(out, copy(v, depth + 1));
                }
                c = c.getSuperclass();
            }
            return out;
        } catch (Throwable t) {
            return obj;
        }
    }
}
