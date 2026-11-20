package momoi.mod.qqpro.qqmax;

import android.os.Handler;
import android.os.Looper;

import com.tencent.qqnt.kernel.api.IGroupService;
import com.tencent.qqnt.kernel.nativeinterface.GroupBulletinListReq;
import com.tencent.qqnt.kernel.nativeinterface.IKernelGroupService;
import com.tencent.qqnt.kernel.nativeinterface.IOperateCallback;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Port of the module's F22 full group-bulletin list.
 * The host's GroupBulletinApi.fetch only triggers a single pushed batch;
 * this replaces it (when bulletin_all_enabled, default on) with paged
 * IKernelGroupService.getGroupBulletinList requests, then feeds the
 * accumulated records through the host's own flatten() and callback.
 * Any failure falls back to the host's original fetch.
 * Patch points: GroupBulletinApi.fetch head (classes.dex) and the empty
 * GroupBulletinApi$Listener.onGetGroupBulletinListResult stub (classes3.dex).
 */
public final class BulletinAll {
    private BulletinAll() {}

    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGES = 30;

    private static final class State {
        Object callback;
        final ArrayList feeds = new ArrayList();
        int pages;
        int startIndex;
        boolean finished;
    }

    private static final Map<Long, State> STATES = new ConcurrentHashMap<Long, State>();
    private static final Set<Long> FALLBACK = new HashSet<Long>();
    private static boolean listenerRegistered;

    /** Called at GroupBulletinApi.fetch head. True = handled, skip original. */
    public static boolean tryFetch(final long groupCode, Object callback) {
        try {
            if (!Cfg.getBool("bulletin_all_enabled", true)) return false;
            if (callback == null) return false;
            synchronized (FALLBACK) {
                if (FALLBACK.remove(groupCode)) return false; // one-shot fallback pass
            }
            if (Kernel.nativeGroup() == null) return false;
            ensureListenerRegistered();
            State st = new State();
            st.callback = callback;
            STATES.put(groupCode, st);
            requestPage(groupCode, 0);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() { onTimeout(groupCode); }
            }, 12000L);
            return true;
        } catch (Throwable t) {
            Kernel.log("BulletinAll.tryFetch failed: " + t);
            STATES.remove(groupCode);
            return false;
        }
    }

    private static void ensureListenerRegistered() {
        if (listenerRegistered) return;
        try {
            IGroupService api = Kernel.groupApi();
            if (api == null) return;
            Class<?> listenerCls = Class.forName("com.tencent.qqnt.kernel.nativeinterface.IKernelGroupListener");
            Class<?> momoiListener = Class.forName("momoi.mod.qqpro.api.GroupBulletinApi$Listener");
            Object listener = momoiListener.getField("INSTANCE").get(null);
            for (Method m : api.getClass().getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 1 && p[0] == listenerCls) {
                    m.invoke(api, listener);
                    listenerRegistered = true;
                    return;
                }
            }
        } catch (Throwable t) {
            Kernel.log("BulletinAll.register failed: " + t);
        }
    }

    private static void requestPage(final long groupCode, int startIndex) {
        try {
            IKernelGroupService svc = Kernel.nativeGroup();
            if (svc == null) { onTimeout(groupCode); return; }
            GroupBulletinListReq req = new GroupBulletinListReq();
            req.startIndex = startIndex;
            req.num = PAGE_SIZE;
            req.needPublisherInfo = 1;
            svc.getGroupBulletinList(groupCode, "", "", req, new IOperateCallback() {
                @Override public void onResult(int code, String msg) {
                    if (code != 0) {
                        Kernel.log("BulletinAll page ack failed: " + code + " " + msg);
                        State st = STATES.get(groupCode);
                        if (st != null && st.pages == 0 && st.feeds.isEmpty()) onTimeout(groupCode);
                    }
                }
            });
        } catch (Throwable t) {
            Kernel.log("BulletinAll.requestPage failed: " + t);
            onTimeout(groupCode);
        }
    }

    /** Called from the patched Listener.onGetGroupBulletinListResult. */
    public static void onListResult(long groupCode, Object result) {
        try {
            State st = STATES.get(groupCode);
            if (st == null || result == null || st.finished) return;
            Object feeds = Refl.getField(result, "feeds");
            int nextIndex = Refl.getInt(result, "nextIndex", -1);
            int got = 0;
            if (feeds instanceof List) {
                st.feeds.addAll((List) feeds);
                got = ((List) feeds).size();
            }
            st.pages++;
            boolean more = got > 0 && nextIndex > st.startIndex && st.pages < MAX_PAGES;
            if (more) {
                st.startIndex = nextIndex;
                requestPage(groupCode, nextIndex);
            } else {
                finish(groupCode, st);
            }
        } catch (Throwable t) {
            Kernel.log("BulletinAll.onListResult failed: " + t);
        }
    }

    private static void onTimeout(long groupCode) {
        State st = STATES.get(groupCode);
        if (st == null || st.finished) return;
        if (!st.feeds.isEmpty()) {
            finish(groupCode, st);
            return;
        }
        // Nothing arrived: fall back to the host's original fetch once.
        STATES.remove(groupCode);
        st.finished = true;
        synchronized (FALLBACK) { FALLBACK.add(groupCode); }
        try {
            Class<?> apiCls = Class.forName("momoi.mod.qqpro.api.GroupBulletinApi");
            Object api = apiCls.getField("INSTANCE").get(null);
            Method fetch = apiCls.getMethod("fetch", long.class,
                    Class.forName("kotlin.jvm.functions.Function1"));
            fetch.invoke(api, groupCode, st.callback);
        } catch (Throwable t) {
            Kernel.log("BulletinAll fallback failed: " + t);
        }
    }

    private static void finish(final long groupCode, State st) {
        st.finished = true;
        STATES.remove(groupCode);
        final Object callback = st.callback;
        final ArrayList feeds = st.feeds;
        try {
            Class<?> gbCls = Class.forName("com.tencent.qqnt.kernel.nativeinterface.GroupBulletin");
            Object gb = gbCls.getDeclaredConstructor().newInstance();
            Refl.setField(gb, "groupCode", groupCode);
            Refl.setField(gb, "feedsRecords", feeds);
            Class<?> apiCls = Class.forName("momoi.mod.qqpro.api.GroupBulletinApi");
            Object api = apiCls.getField("INSTANCE").get(null);
            Method flatten = null;
            for (Method m : apiCls.getDeclaredMethods()) {
                if (m.getName().equals("flatten")) { flatten = m; break; }
            }
            if (flatten == null) throw new NoSuchMethodException("flatten");
            flatten.setAccessible(true);
            final Object items = flatten.invoke(api, gb);
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override public void run() {
                    try {
                        Class<?> f1 = Class.forName("kotlin.jvm.functions.Function1");
                        f1.getMethod("invoke", Object.class).invoke(callback, items);
                    } catch (Throwable t) {
                        Kernel.log("BulletinAll deliver failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            Kernel.log("BulletinAll.finish failed: " + t);
        }
    }
}
