package momoi.mod.qqpro.qqmax;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Full-screen dark list menu in the style of the host's own message
 * long-press menu (dark background, blue icons, white rows).
 */
public final class StyledMenu {
    private StyledMenu() {}

    public interface OnPick {
        void onPick(int index);
    }

    private static final int BG = Color.rgb(22, 22, 22);
    private static final int DIVIDER = Color.rgb(45, 45, 45);
    private static final int ICON_BLUE = Color.rgb(96, 165, 230);
    private static final int HEADER_GRAY = Color.rgb(150, 150, 150);

    public static void show(Context ctx, String header, final String[] labels, int[] icons, final OnPick listener) {
        try {
            final Dialog dialog = new Dialog(ctx);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            LinearLayout list = new LinearLayout(ctx);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setBackgroundColor(BG);
            int topPad = dp(ctx, 12);
            list.setPadding(0, topPad, 0, topPad);

            if (header != null && header.length() > 0) {
                TextView h = new TextView(ctx);
                h.setText(header);
                h.setTextColor(HEADER_GRAY);
                h.setTextSize(13);
                h.setPadding(dp(ctx, 20), dp(ctx, 6), dp(ctx, 20), dp(ctx, 10));
                list.addView(h, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            for (int i = 0; i < labels.length; i++) {
                final int index = i;
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(dp(ctx, 20), 0, dp(ctx, 20), 0);
                row.setClickable(true);

                if (icons != null && i < icons.length && icons[i] != 0) {
                    ImageView iv = new ImageView(ctx);
                    try {
                        iv.setImageResource(icons[i]);
                        iv.setColorFilter(ICON_BLUE, PorterDuff.Mode.SRC_IN);
                    } catch (Throwable ignored) {}
                    LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(ctx, 26), dp(ctx, 26));
                    ip.rightMargin = dp(ctx, 18);
                    row.addView(iv, ip);
                }

                TextView tv = new TextView(ctx);
                tv.setText(labels[i]);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(16);
                row.addView(tv, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                row.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        dialog.dismiss();
                        if (listener != null) listener.onPick(index);
                    }
                });
                list.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 58)));

                if (i < labels.length - 1) {
                    View div = new View(ctx);
                    div.setBackgroundColor(DIVIDER);
                    LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 1);
                    dp2.leftMargin = dp(ctx, 64);
                    list.addView(div, dp2);
                }
            }

            ScrollView scroll = new ScrollView(ctx);
            scroll.setBackgroundColor(BG);
            scroll.addView(list);

            dialog.setContentView(scroll);
            Window w = dialog.getWindow();
            if (w != null) {
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                w.setBackgroundDrawable(new ColorDrawable(BG));
            }
            dialog.show();
        } catch (Throwable t) {
            Kernel.log("StyledMenu.show failed: " + t);
        }
    }

    private static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
