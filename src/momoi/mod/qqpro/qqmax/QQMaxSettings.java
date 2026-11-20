package momoi.mod.qqpro.qqmax;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Settings entry points. An entry row is added to the chat settings page
 * (SettingFrame) and to the mods' SettingsActivity; tapping it opens the
 * module-style paged UI (QQMaxSettingsUI). Values live in
 * filesDir/qqlite_settings.json, the same file the ported features read.
 * Patch points: SettingFrame.onViewCreated_0_1 head and
 * SettingsActivity.onCreate_0 tail (classes.dex).
 */
public final class QQMaxSettings {
    private QQMaxSettings() {}

    private static final String ROW_TAG = "qqmax_settings_row";

    public static void attach(Object viewObj) {
        try {
            if (!(viewObj instanceof View)) return;
            final View root = (View) viewObj;
            Cfg.setAppContext(root.getContext());
            root.post(new Runnable() {
                @Override public void run() { addRow(root); }
            });
            root.postDelayed(new Runnable() {
                @Override public void run() { addRow(root); }
            }, 800L);
            root.postDelayed(new Runnable() {
                @Override public void run() { addRow(root); }
            }, 2000L);
        } catch (Throwable t) {
            Kernel.log("QQMaxSettings.attach failed: " + t);
        }
    }

    /** Entry from an Activity (e.g. the mods' SettingsActivity): walk its window. */
    public static void attachActivity(Object activityObj) {
        try {
            if (activityObj == null) return;
            java.lang.reflect.Method getWindow = activityObj.getClass().getMethod("getWindow");
            Object window = getWindow.invoke(activityObj);
            if (window == null) return;
            Object decor = window.getClass().getMethod("getDecorView").invoke(window);
            if (decor instanceof View) attach(decor);
        } catch (Throwable t) {
            Kernel.log("QQMaxSettings.attachActivity failed: " + t);
        }
    }

    private static void addRow(View root) {
        try {
            ViewGroup container = findContainer(root);
            if (container == null) return;
            for (int i = 0; i < container.getChildCount(); i++) {
                if (ROW_TAG.equals(container.getChildAt(i).getTag())) return;
            }
            Context ctx = root.getContext();
            TextView row = new TextView(ctx);
            row.setTag(ROW_TAG);
            row.setText("QQMax 增强设置 ›");
            row.setTextSize(17);
            row.setTextColor(android.graphics.Color.WHITE);
            row.setBackground(DarkInput.rounded(
                    android.graphics.Color.rgb(30, 30, 36), dp(ctx, 16)));
            int pad = dp(ctx, 16);
            row.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = dp(ctx, 10);
            rowLp.bottomMargin = dp(ctx, 10);
            row.setLayoutParams(rowLp);
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { show(v.getContext()); }
            });
            container.addView(row, rowLp);
        } catch (Throwable t) {
            Kernel.log("QQMaxSettings.addRow failed: " + t);
        }
    }

    private static ViewGroup findContainer(View root) {
        ScrollView sv = findScrollView(root);
        if (sv != null) {
            for (int i = 0; i < sv.getChildCount(); i++) {
                if (sv.getChildAt(i) instanceof ViewGroup) return (ViewGroup) sv.getChildAt(i);
            }
            return sv;
        }
        return root instanceof ViewGroup ? (ViewGroup) root : null;
    }

    private static ScrollView findScrollView(View v) {
        if (v instanceof ScrollView) return (ScrollView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                ScrollView r = findScrollView(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    public static void show(Context ctx) {
        QQMaxSettingsUI.show(ctx);
    }

    private static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
