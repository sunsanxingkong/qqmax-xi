package momoi.mod.qqpro.qqmax;

import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Sends a plain-text message through the NT kernel IMsgService, located
 * reflectively (the host's service accessor and send method names are
 * obfuscated). Used by auto-reply, keyword reply and scheduled tasks.
 */
public final class Sender {
    private Sender() {}

    private static Object msgService;
    private static Method sendMethod;

    private static synchronized Object service() {
        if (msgService != null) return msgService;
        try {
            Class<?> util = Class.forName("com.tencent.qqnt.msg.KernelServiceUtil");
            for (Method m : util.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 0) continue;
                if (m.getReturnType().getName().contains("IMsgService")) {
                    m.setAccessible(true);
                    msgService = m.invoke(null);
                    if (msgService != null) break;
                }
            }
        } catch (Throwable t) {
            Kernel.log("Sender.service failed: " + t);
        }
        return msgService;
    }

    private static synchronized Method findSendMethod(Class<?> svcClass) {
        if (sendMethod != null) return sendMethod;
        Method best = null;
        for (Method m : svcClass.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            boolean hasContact = false, hasList = false;
            for (Class<?> c : p) {
                if (c.getName().endsWith("nativeinterface.Contact")) hasContact = true;
                if (c == ArrayList.class || c == java.util.List.class) hasList = true;
            }
            if (hasContact && hasList) {
                // Prefer the (long, Contact, ArrayList, IOperateCallback) shape.
                if (p.length == 4 && p[0] == long.class) { best = m; break; }
                if (best == null) best = m;
            }
        }
        if (best != null) { best.setAccessible(true); sendMethod = best; }
        return sendMethod;
    }

    public static boolean sendText(int chatType, String peerUid, String text) {
        try {
            Object svc = service();
            if (svc == null) { Kernel.log("Sender: no IMsgService"); return false; }
            Method m = findSendMethod(svc.getClass());
            if (m == null) { Kernel.log("Sender: no send method"); return false; }

            Class<?> contactCls = Class.forName("com.tencent.qqnt.kernel.nativeinterface.Contact");
            Object contact = contactCls.getDeclaredConstructor().newInstance();
            Refl.setField(contact, "chatType", chatType);
            Refl.setField(contact, "peerUid", peerUid);
            if (Refl.findField(contactCls, "guildId") != null) Refl.setField(contact, "guildId", "");

            Class<?> elCls = Class.forName("com.tencent.qqnt.kernel.nativeinterface.MsgElement");
            Object el = elCls.getDeclaredConstructor().newInstance();
            if (Refl.findField(elCls, "elementType") != null) Refl.setField(el, "elementType", 1);
            Class<?> teCls = Class.forName("com.tencent.qqnt.kernel.nativeinterface.TextElement");
            Object te = teCls.getDeclaredConstructor().newInstance();
            Refl.setField(te, "content", text);
            Refl.setField(el, "textElement", te);
            ArrayList elements = new ArrayList();
            elements.add(el);

            Class<?>[] p = m.getParameterTypes();
            Object[] args = new Object[p.length];
            for (int i = 0; i < p.length; i++) {
                if (p[i] == long.class) args[i] = 0L;
                else if (p[i].getName().endsWith("nativeinterface.Contact")) args[i] = contact;
                else if (p[i] == ArrayList.class || p[i] == java.util.List.class) args[i] = elements;
                else if (p[i] == HashMap.class || p[i] == java.util.Map.class) args[i] = new HashMap();
                else if (p[i].getName().endsWith("IOperateCallback")) {
                    args[i] = new IOperateCallback() {
                        @Override public void onResult(int code, String msg) {
                            if (code != 0) Kernel.log("Sender send failed: " + code + " " + msg);
                        }
                    };
                } else args[i] = null;
            }
            m.invoke(svc, args);
            return true;
        } catch (Throwable t) {
            Kernel.log("Sender.sendText failed: " + t);
            return false;
        }
    }
}
