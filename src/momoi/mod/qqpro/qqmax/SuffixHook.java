package momoi.mod.qqpro.qqmax;

import java.util.ArrayList;

/**
 * Port of the module's F09 custom suffix ("catgirl suffix"):
 * when catgirl_enabled is set in qqlite_settings.json, append the suffix
 * (catgirl_suffix, default 喵~) to the last text element of an outgoing
 * message. Patched at the head of MsgServiceImpl.sendMsg overloads
 * (classes7.dex). Idempotent: never appends twice.
 */
public final class SuffixHook {
    private SuffixHook() {}

    public static void apply(ArrayList elements) {
        try {
            if (elements == null || elements.isEmpty()) return;
            if (!Cfg.getBool("catgirl_enabled", false)) return;
            String suffix = Cfg.getString("catgirl_suffix", "");
            if (suffix == null || suffix.length() == 0) suffix = "喵~";
            for (int i = elements.size() - 1; i >= 0; i--) {
                Object el = elements.get(i);
                if (el == null) continue;
                Object textElement = Refl.getField(el, "textElement");
                if (textElement == null) continue;
                Object content = Refl.getField(textElement, "content");
                if (!(content instanceof String)) continue;
                String s = (String) content;
                if (s.endsWith(suffix)) return;
                Refl.setField(textElement, "content", s + suffix);
                return;
            }
        } catch (Throwable t) {
            Kernel.log("SuffixHook failed: " + t);
        }
    }
}
