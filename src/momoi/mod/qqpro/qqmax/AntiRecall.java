package momoi.mod.qqpro.qqmax;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of the module's F01/F02 anti-recall core (MsgCache equivalent).
 * mode=1 (default): block the delete callback and restore cached elements
 * when a revoked record is rendered. mode=2: let the deletion happen but
 * remember the original text so the gray tip can show it.
 * Config keys follow the module: enabled / mode in qqlite_settings.json.
 */
public final class AntiRecall {
    private AntiRecall() {}

    private static final int MAX_CACHE = 600;

    private static final class Cached {
        ArrayList elements;   // deep-copied original elements
        String text;          // plain-text summary
        String senderUid;
        String peerUid;
        int chatType;
    }

    private static final Map<Long, Cached> CACHE = new LinkedHashMap<Long, Cached>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Cached> eldest) {
            return size() > MAX_CACHE;
        }
    };
    private static final Map<Long, Boolean> RECALLED = new LinkedHashMap<Long, Boolean>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
            return size() > 200;
        }
    };

    public static boolean enabled() {
        return Cfg.getBool("enabled", true);
    }

    public static int mode() {
        String m = Cfg.getString("mode", "1");
        try { return Integer.parseInt(m.trim()); } catch (Throwable t) { return 1; }
    }

    /** True when the delete callback should be swallowed (mode 1). */
    public static boolean shouldBlockDelete() {
        try { return enabled() && mode() == 1; } catch (Throwable t) { return false; }
    }

    public static boolean isRevokeRecord(Object record) {
        try {
            Object elements = Refl.getField(record, "elements");
            if (elements instanceof List) {
                for (Object el : (List) elements) {
                    if (el == null) continue;
                    // Host layout (verified): MsgElement.grayTipElement.revokeElement
                    Object gray = Refl.getField(el, "grayTipElement");
                    if (gray != null && Refl.getField(gray, "revokeElement") != null) return true;
                    if (Refl.getField(el, "revokeElement") != null) return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static String extractText(Object record) {
        StringBuilder sb = new StringBuilder();
        try {
            Object elements = Refl.getField(record, "elements");
            if (elements instanceof List) {
                for (Object el : (List) elements) {
                    if (el == null) continue;
                    Object te = Refl.getField(el, "textElement");
                    if (te != null) {
                        String c = Refl.getString(te, "content");
                        if (c != null) sb.append(c);
                        continue;
                    }
                    if (Refl.getField(el, "picElement") != null) sb.append("[图片]");
                    else if (Refl.getField(el, "pttElement") != null) sb.append("[语音]");
                    else if (Refl.getField(el, "fileElement") != null) sb.append("[文件]");
                    else if (Refl.getField(el, "faceElement") != null) sb.append("[表情]");
                }
            }
        } catch (Throwable ignored) {}
        return sb.toString();
    }

    /** Cache one MsgRecord's original elements (deep copy). */
    public static void cacheRecord(Object record) {
        try {
            if (record == null || !enabled()) return;
            long msgId = Refl.getLong(record, "msgId", 0L);
            if (msgId == 0L) return;
            if (isRevokeRecord(record)) {
                synchronized (CACHE) {
                    if (CACHE.containsKey(msgId)) RECALLED.put(msgId, Boolean.TRUE);
                }
                return;
            }
            Object elements = Refl.getField(record, "elements");
            if (!(elements instanceof List) || ((List) elements).isEmpty()) return;
            Cached c = new Cached();
            c.elements = (ArrayList) Replicator.deepCopy(new ArrayList((List) elements));
            c.text = extractText(record);
            c.senderUid = Refl.getString(record, "senderUid");
            c.peerUid = Refl.getString(record, "peerUid");
            c.chatType = Refl.getInt(record, "chatType", 0);
            synchronized (CACHE) { CACHE.put(msgId, c); }
        } catch (Throwable t) {
            Kernel.log("AntiRecall.cacheRecord failed: " + t);
        }
    }

    /** Accept a List of records, a single record, or an array. */
    public static void cacheAny(Object arg) {
        try {
            if (arg instanceof List) {
                for (Object o : (List) arg) cacheRecord(o);
            } else if (arg != null && arg.getClass().isArray()) {
                int n = java.lang.reflect.Array.getLength(arg);
                for (int i = 0; i < n; i++) cacheRecord(java.lang.reflect.Array.get(arg, i));
            } else if (arg != null) {
                cacheRecord(arg);
            }
        } catch (Throwable t) {
            Kernel.log("AntiRecall.cacheAny failed: " + t);
        }
    }

    /** Mark msgIds as recalled (mode 2 bookkeeping). Accepts List of Long/record, Long, or long[]. */
    public static void markDeleted(Object arg) {
        try {
            if (!enabled()) return;
            if (arg instanceof List) {
                for (Object o : (List) arg) markOne(o);
            } else if (arg != null) {
                markOne(arg);
            }
        } catch (Throwable t) {
            Kernel.log("AntiRecall.markDeleted failed: " + t);
        }
    }

    private static void markOne(Object o) {
        if (o == null) return;
        long id = 0L;
        if (o instanceof Number) id = ((Number) o).longValue();
        else id = Refl.getLong(o, "msgId", 0L);
        if (id != 0L) synchronized (CACHE) { RECALLED.put(id, Boolean.TRUE); }
    }

    public static boolean isRecalled(long msgId) {
        synchronized (CACHE) { return RECALLED.containsKey(msgId); }
    }

    /** Original text of a recalled message, for the gray tip (mode 2). */
    public static String recalledText(long msgId) {
        synchronized (CACHE) {
            Cached c = CACHE.get(msgId);
            return c == null ? null : c.text;
        }
    }

    /**
     * Render-time restore (mode 1): if this record was revoked and we hold
     * the original elements, put them back before the UI converts it.
     */
    public static void restoreForRender(Object record) {
        try {
            if (record == null || !enabled() || mode() != 1) return;
            if (!isRevokeRecord(record)) return;
            long msgId = Refl.getLong(record, "msgId", 0L);
            if (msgId == 0L) return;
            Cached c;
            synchronized (CACHE) { c = CACHE.get(msgId); }
            if (c == null || c.elements == null) return;
            Object copy = Replicator.deepCopy(c.elements);
            if (copy instanceof ArrayList) {
                Refl.setField(record, "elements", copy);
                Kernel.log("AntiRecall: restored msg " + msgId);
            }
        } catch (Throwable t) {
            Kernel.log("AntiRecall.restore failed: " + t);
        }
    }
}
