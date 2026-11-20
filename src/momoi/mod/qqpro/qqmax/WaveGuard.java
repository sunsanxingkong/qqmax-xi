package momoi.mod.qqpro.qqmax;

import java.util.ArrayList;

/**
 * Port of the module's F05 fake-voice waveform crash guard:
 * PttAudioWaveView.f expects Byte elements; normalise any Number to Byte
 * before the original method runs (patched at the head of f in classes2.dex).
 */
public final class WaveGuard {
    private WaveGuard() {}

    public static void sanitize(ArrayList list) {
        if (list == null) return;
        try {
            for (int i = 0; i < list.size(); i++) {
                Object o = list.get(i);
                if (o instanceof Number && !(o instanceof Byte)) {
                    list.set(i, Byte.valueOf(((Number) o).byteValue()));
                }
            }
        } catch (Throwable t) {
            Kernel.log("WaveGuard failed: " + t);
        }
    }
}
