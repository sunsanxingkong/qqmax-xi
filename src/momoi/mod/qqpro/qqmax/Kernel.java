package momoi.mod.qqpro.qqmax;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.tencent.qqnt.kernel.api.IGroupService;
import com.tencent.qqnt.kernel.api.impl.GroupService;
import com.tencent.qqnt.kernel.nativeinterface.IKernelGroupService;
import com.tencent.qqnt.msg.KernelServiceUtil;

/** Access to QQ NT kernel services, mirroring the mod's own GroupMute helper. */
public final class Kernel {
    private Kernel() {}

    public static final String TAG = "QQMaxPort";

    public static void log(String msg) {
        try { Log.i(TAG, msg); } catch (Throwable ignored) {}
    }

    public static IGroupService groupApi() {
        try {
            return KernelServiceUtil.b();
        } catch (Throwable t) {
            log("groupApi failed: " + t);
            return null;
        }
    }

    public static IKernelGroupService nativeGroup() {
        try {
            IGroupService svc = KernelServiceUtil.b();
            if (svc instanceof GroupService) {
                Object nativeSvc = ((GroupService) svc).getService();
                if (nativeSvc instanceof IKernelGroupService) {
                    return (IKernelGroupService) nativeSvc;
                }
            }
        } catch (Throwable t) {
            log("nativeGroup failed: " + t);
        }
        return null;
    }

    public static void toast(final Context ctx, final String msg) {
        if (ctx == null) return;
        Runnable r = new Runnable() {
            @Override public void run() {
                try { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) {}
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else new Handler(Looper.getMainLooper()).post(r);
    }
}
