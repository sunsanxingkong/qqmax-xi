package momoi.mod.qqpro.qqmax;

/**
 * Single dispatch facade for all batch-2 smali patch points, so each patched
 * dex only gains one or two new method references (classes4.dex is close to
 * the 64K method limit).
 *
 * call codes:
 *   1 = incoming message batch (cache + reply evaluation)
 *   2 = delete/recall event (mode-2 bookkeeping)
 *   3 = record about to be rendered (mode-1 restore)
 *   5 = own sent message batch (cache + learn self uid)
 * callZ codes:
 *   1 = should the delete callback be swallowed (anti-recall mode 1)
 */
public final class Hook {
    private Hook() {}

    public static void call(int code, Object arg) {
        try {
            switch (code) {
                case 1:
                    AntiRecall.cacheAny(arg);
                    ReplyHook.onIncoming(arg);
                    break;
                case 2:
                    AntiRecall.markDeleted(arg);
                    break;
                case 3:
                    AntiRecall.restoreForRender(arg);
                    break;
                case 5:
                    AntiRecall.cacheAny(arg);
                    noteSelf(arg);
                    TaskScheduler.ensureStarted();
                    break;
                default:
                    break;
            }
        } catch (Throwable t) {
            Kernel.log("Hook.call(" + code + ") failed: " + t);
        }
    }

    public static boolean callZ(int code, Object arg) {
        try {
            if (code == 1) return AntiRecall.shouldBlockDelete();
        } catch (Throwable t) {
            Kernel.log("Hook.callZ(" + code + ") failed: " + t);
        }
        return false;
    }

    private static void noteSelf(Object arg) {
        try {
            Object rec = arg;
            if (arg instanceof java.util.List && !((java.util.List) arg).isEmpty()) {
                rec = ((java.util.List) arg).get(0);
            }
            if (rec != null) ReplyHook.noteSelfUid(Refl.getString(rec, "senderUid"));
        } catch (Throwable ignored) {}
    }
}
