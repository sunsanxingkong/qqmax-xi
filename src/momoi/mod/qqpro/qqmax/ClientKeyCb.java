package momoi.mod.qqpro.qqmax;

import com.tencent.qqnt.kernel.nativeinterface.IClientKeyCallback;

import java.util.concurrent.CountDownLatch;

/**
 * Real compiled callback for IKernelTicketService.forceFetchClientKey.
 * One instance per fetch attempt — the kernel ticket fetch is single-shot
 * stateful: fix5 called it twice back-to-back with shared static state and
 * the app crashed on device; fix4's single call was stable.
 */
public class ClientKeyCb implements IClientKeyCallback {

    public volatile Object[] last;
    public final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void onResult(int code, String s1, String s2, long l1, String s3, long l2) {
        last = new Object[]{code, s1, s2, l1, s3, l2};
        latch.countDown();
    }
}
