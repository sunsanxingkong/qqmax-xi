package momoi.mod.qqpro.qqmax;

import java.lang.reflect.Method;

/** Reflective access to the NT wrapper session (KernelServiceUtil.g()). */
public class KernelSession {

    public static Object session() {
        try {
            Class<?> util = Class.forName("com.tencent.qqnt.msg.KernelServiceUtil");
            Method g = util.getMethod("g");
            return g.invoke(null);
        } catch (Throwable t) {
            Kernel.log("session(): " + t);
            return null;
        }
    }
}
