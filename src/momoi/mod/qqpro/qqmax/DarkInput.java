package momoi.mod.qqpro.qqmax;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Dark rounded-card input dialog in the host settings style (lan's
 * screenshots 3/4), replacing the light system AlertDialog whose white
 * background clashed with the dark UI (his screenshots 1/2).
 */
public final class DarkInput {
    private DarkInput() {}

    public interface OnOk {
        void onOk(String text);
    }

    static final int CARD = Color.rgb(32, 32, 38);
    static final int FIELD = Color.rgb(22, 22, 26);
    static final int BLUE = Color.rgb(94, 177, 255);
    static final int GRAY = Color.rgb(150, 150, 158);

    public static GradientDrawable rounded(int color, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static void show(final Context ctx, String title, String hint, String initial,
                            boolean multi, boolean numeric, final OnOk onOk) {
        try {
            final Dialog dialog = new Dialog(ctx);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(ctx, 20);
            card.setPadding(pad, pad, pad, dp(ctx, 14));
            card.setBackground(rounded(CARD, dp(ctx, 18)));

            TextView t = new TextView(ctx);
            t.setText(title);
            t.setTextColor(Color.WHITE);
            t.setTextSize(18);
            card.addView(t);

            final EditText et = new EditText(ctx);
            et.setText(initial == null ? "" : initial);
            if (hint != null) et.setHint(hint);
            et.setHintTextColor(GRAY);
            et.setTextColor(Color.WHITE);
            et.setTextSize(16);
            et.setBackground(rounded(FIELD, dp(ctx, 10)));
            int ep = dp(ctx, 12);
            et.setPadding(ep, ep, ep, ep);
            boolean fixedHeight = false;
            if (numeric) {
                et.setInputType(InputType.TYPE_CLASS_NUMBER);
                et.setSingleLine(true);
            } else if (multi) {
                et.setSingleLine(false);
                et.setMinLines(4);
                // Fixed height + internal scroll: a long paste (e.g. a full
                // cookie) must not push the Save button off the screen.
                et.setGravity(android.view.Gravity.TOP);
                et.setVerticalScrollBarEnabled(true);
                fixedHeight = true;
            } else {
                et.setSingleLine(true);
            }
            LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    fixedHeight ? dp(ctx, 200) : ViewGroup.LayoutParams.WRAP_CONTENT);
            etLp.topMargin = dp(ctx, 14);
            card.addView(et, etLp);

            LinearLayout buttons = new LinearLayout(ctx);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.setGravity(android.view.Gravity.END);
            TextView cancel = button(ctx, "取消", GRAY);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { dialog.dismiss(); }
            });
            TextView ok = button(ctx, "保存", BLUE);
            ok.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    dialog.dismiss();
                    if (onOk != null) onOk.onOk(et.getText().toString());
                }
            });
            buttons.addView(cancel);
            buttons.addView(ok);
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bLp.topMargin = dp(ctx, 10);
            card.addView(buttons, bLp);

            dialog.setContentView(card);
            Window w = dialog.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                        Color.TRANSPARENT));
                w.setLayout((int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.92),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            dialog.show();
        } catch (Throwable t) {
            Kernel.log("DarkInput failed: " + t);
        }
    }

    private static TextView button(Context ctx, String text, int color) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(16);
        int hp = dp(ctx, 16);
        tv.setPadding(hp, dp(ctx, 8), hp, dp(ctx, 8));
        tv.setClickable(true);
        return tv;
    }

    static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
