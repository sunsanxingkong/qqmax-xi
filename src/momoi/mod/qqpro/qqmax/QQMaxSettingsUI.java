package momoi.mod.qqpro.qqmax;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * Settings UI in the HOST app's own settings style (lan's screenshots 3/4):
 * black background, separate rounded cards with gaps, white title + gray
 * subtitle inside each card, blue chevrons, sub-page headers with a blue
 * back arrow + title + subtitle, blue section titles with gray descriptive
 * text, and pill switches. Only features actually ported are listed.
 * Values go to filesDir/qqlite_settings.json via Cfg; switches/options save
 * immediately, text values via the dark DarkInput dialog.
 */
public final class QQMaxSettingsUI {
    private QQMaxSettingsUI() {}

    private static final int BG = Color.rgb(8, 8, 10);
    private static final int CARD = Color.rgb(30, 30, 36);
    private static final int BLUE = Color.rgb(94, 177, 255);
    private static final int WHITE = Color.WHITE;
    private static final int GRAY = Color.rgb(148, 148, 156);

    private static Dialog dialog;
    private static ScrollView content;

    public static void show(final Context ctx) {
        try {
            dismiss();
            dialog = new Dialog(ctx);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            content = new ScrollView(ctx);
            content.setBackgroundColor(BG);
            dialog.setContentView(content);
            Window w = dialog.getWindow();
            if (w != null) {
                w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));
            }
            showMain(ctx);
            dialog.show();
        } catch (Throwable t) {
            Kernel.log("QQMaxSettingsUI.show failed: " + t);
        }
    }

    private static void dismiss() {
        try { if (dialog != null) dialog.dismiss(); } catch (Throwable ignored) {}
        dialog = null;
    }

    private static void setPage(LinearLayout page) {
        content.removeAllViews();
        content.addView(page);
        content.scrollTo(0, 0);
    }

    // ---------- main page (host list style) ----------

    private static void showMain(final Context ctx) {
        LinearLayout page = page(ctx);
        page.addView(header(ctx, null, "增强模块设置", "QQMax 移植功能"));
        page.addView(navRow(ctx, "基础设置", "防撤回 / 群公告 / 网页禁言 CK", new Runnable() {
            @Override public void run() { showBasic(ctx); }
        }));
        page.addView(navRow(ctx, "定时消息", "按时自动发送消息", new Runnable() {
            @Override public void run() { showTasks(ctx); }
        }));
        page.addView(navRow(ctx, "关键词回复", "按关键词触发回复", new Runnable() {
            @Override public void run() { showKeyword(ctx); }
        }));
        page.addView(navRow(ctx, "自动回复", "私聊收到消息即回复", new Runnable() {
            @Override public void run() { showAutoReply(ctx); }
        }));
        page.addView(navRow(ctx, "AI 总结设置", "自定义 url / apikey / 模型", new Runnable() {
            @Override public void run() { showSummary(ctx); }
        }));
        page.addView(note(ctx, "仅包含已移植进本包的功能，切换即时生效。"));
        setPage(page);
    }

    // ---------- sub pages ----------

    private static void showBasic(final Context ctx) {
        LinearLayout page = page(ctx);
        page.addView(header(ctx, new Runnable() {
            @Override public void run() { showMain(ctx); }
        }, "基础设置", "防撤回 / 群公告 / 通用"));
        page.addView(section(ctx, "防撤回", null));
        page.addView(switchRow(ctx, "防撤回总开关", "关闭后不拦截消息撤回", "enabled", true));
        page.addView(section(ctx, "防撤回模式", null));
        int mode = parseMode();
        page.addView(optionRow(ctx, "强制阻止撤回", "推荐 · 拦截删除，消息保留在本地",
                mode == 1, new Runnable() {
                    @Override public void run() { saveOne("mode", "1"); showBasic(ctx); }
                }));
        page.addView(optionRow(ctx, "注入还原", "不拦截删除，仅记录（灰条还原未移植）",
                mode == 2, new Runnable() {
                    @Override public void run() { saveOne("mode", "2"); showBasic(ctx); }
                }));
        page.addView(section(ctx, "群公告", null));
        page.addView(switchRow(ctx, "群公告全量加载", "打开群公告时自动翻页拉全部",
                "bulletin_all_enabled", true));
        page.addView(section(ctx, "消息后缀", null));
        page.addView(switchRow(ctx, "发送消息加后缀", "每条发出的文字消息末尾追加",
                "catgirl_enabled", false));
        page.addView(textRow(ctx, "后缀内容", "catgirl_suffix", "", false));
        page.addView(section(ctx, "网页禁言",
                "长按成员禁言走网页群管理接口，需要你自己的网页登录 CK。"
                        + "在浏览器登录 qun.qq.com 打开群管理，复制该站点的完整 Cookie"
                        + "（必须包含 uin 和 skey）粘贴到下面。CK 只存在本机设置里，过期了重新获取粘贴即可。"));
        page.addView(textRow(ctx, "网页登录 CK", "webmute_ck", "", true));
        setPage(page);
    }

    private static void showTasks(final Context ctx) {
        LinearLayout page = page(ctx);
        page.addView(header(ctx, new Runnable() {
            @Override public void run() { showMain(ctx); }
        }, "定时消息", "按时自动发送消息"));
        page.addView(section(ctx, "定时消息", null));
        page.addView(switchRow(ctx, "定时消息开关", "按设定时间自动发送", "auto_task_enabled", false));
        page.addView(textRow(ctx, "发送时间（HH:mm）", "auto_task_time", "", false));
        page.addView(textRow(ctx, "消息内容", "auto_task_content", "", false));
        page.addView(textRow(ctx, "目标会话 uid", "auto_task_peer_uid", "", false));
        page.addView(textRow(ctx, "多规则（每行：HH:mm,类型,uid,内容）", "auto_task_rules", "", true));
        page.addView(note(ctx, "类型：2=群聊，1=私聊。多规则与上面的单条设置可同时使用。"));
        setPage(page);
    }

    private static void showKeyword(final Context ctx) {
        LinearLayout page = page(ctx);
        page.addView(header(ctx, new Runnable() {
            @Override public void run() { showMain(ctx); }
        }, "关键词回复", "按关键词触发回复"));
        page.addView(section(ctx, "关键词回复", null));
        page.addView(switchRow(ctx, "关键词回复开关", "消息命中关键词时自动回复",
                "keyword_reply_enabled", false));
        page.addView(textRow(ctx, "关键词（逗号分隔）", "keyword_reply_keywords", "", false));
        page.addView(textRow(ctx, "命中回复（填 AI = 用下方 AI 生成）", "keyword_reply_content", "", false));
        page.addView(textRow(ctx, "生效会话 uid（逗号分隔，空=全部）", "keyword_reply_peers", "", false));
        page.addView(textRow(ctx, "多规则（每行：关键词|回复|会话uid）", "keyword_reply_rules", "", true));
        page.addView(section(ctx, "关键词 AI", null));
        page.addView(textRow(ctx, "接口地址", "keyword_ai_url", "", false));
        page.addView(textRow(ctx, "API Key", "keyword_ai_api_key", "", false));
        page.addView(textRow(ctx, "模型", "keyword_ai_model", "", false));
        page.addView(textRow(ctx, "人设提示词", "keyword_ai_persona", "", true));
        setPage(page);
    }

    private static void showAutoReply(final Context ctx) {
        LinearLayout page = page(ctx);
        page.addView(header(ctx, new Runnable() {
            @Override public void run() { showMain(ctx); }
        }, "自动回复", "私聊收到消息即回复"));
        page.addView(section(ctx, "自动回复", null));
        page.addView(switchRow(ctx, "自动回复开关", "收到私聊消息即自动回复", "auto_reply_enabled", false));
        page.addView(textRow(ctx, "回复内容", "auto_reply_content", "", false));
        page.addView(textRow(ctx, "生效会话 uid（逗号分隔，空=全部）", "auto_reply_peers", "", false));
        page.addView(note(ctx, "同一会话 60 秒内只回复一次，避免刷屏。"));
        setPage(page);
    }

    private static void showSummary(final Context ctx) {
        LinearLayout page = page(ctx);
        page.addView(header(ctx, new Runnable() {
            @Override public void run() { showMain(ctx); }
        }, "AI 总结设置", "自定义 url / apikey / 模型"));
        page.addView(section(ctx, "AI 总结设置", null));
        page.addView(textRow(ctx, "接口地址", "summary_url", "", false));
        page.addView(textRow(ctx, "API Key", "summary_api_key", "", false));
        page.addView(textRow(ctx, "模型", "summary_model", "", false));
        page.addView(note(ctx, "三项都填了才走自定义模型；留空则用模块原接口。"));
        setPage(page);
    }

    // ---------- building blocks (host card style) ----------

    private static LinearLayout page(Context ctx) {
        LinearLayout page = new LinearLayout(ctx);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        int pad = dp(ctx, 16);
        page.setPadding(pad, dp(ctx, 10), pad, dp(ctx, 28));
        return page;
    }

    /** Sub-page header: blue "<" + white title + gray subtitle (host style). */
    private static View header(final Context ctx, final Runnable back,
                               String title, String subtitle) {
        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(ctx, 8), 0, dp(ctx, 18));
        if (back != null) {
            TextView arrow = new TextView(ctx);
            arrow.setText("‹");
            arrow.setTextColor(BLUE);
            arrow.setTextSize(34);
            arrow.setPadding(0, 0, dp(ctx, 12), dp(ctx, 4));
            arrow.setClickable(true);
            arrow.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { back.run(); }
            });
            head.addView(arrow);
        }
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextColor(WHITE);
        t.setTextSize(22);
        texts.addView(t);
        if (subtitle != null) {
            TextView s = new TextView(ctx);
            s.setText(subtitle);
            s.setTextColor(GRAY);
            s.setTextSize(14);
            s.setPadding(0, dp(ctx, 3), 0, 0);
            texts.addView(s);
        }
        head.addView(texts);
        return head;
    }

    private static View section(Context ctx, String title, String description) {
        LinearLayout sec = new LinearLayout(ctx);
        sec.setOrientation(LinearLayout.VERTICAL);
        sec.setPadding(0, dp(ctx, 16), 0, dp(ctx, 10));
        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextColor(BLUE);
        t.setTextSize(17);
        sec.addView(t);
        if (description != null) {
            TextView d = new TextView(ctx);
            d.setText(description);
            d.setTextColor(GRAY);
            d.setTextSize(13);
            d.setPadding(0, dp(ctx, 5), 0, 0);
            d.setLineSpacing(0, 1.25f);
            sec.addView(d);
        }
        return sec;
    }

    private static TextView note(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(GRAY);
        tv.setTextSize(13);
        tv.setPadding(dp(ctx, 2), dp(ctx, 12), dp(ctx, 2), 0);
        tv.setLineSpacing(0, 1.25f);
        return tv;
    }

    /** One rounded card: title + subtitle on the left, optional widget right. */
    private static LinearLayout cardShell(Context ctx, String title, String subtitle) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
        card.setBackground(DarkInput.rounded(CARD, dp(ctx, 16)));
        int hp = dp(ctx, 16);
        card.setPadding(hp, dp(ctx, 14), hp, dp(ctx, 14));
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextColor(WHITE);
        t.setTextSize(17);
        texts.addView(t);
        if (subtitle != null && subtitle.length() > 0) {
            TextView s = new TextView(ctx);
            s.setText(subtitle);
            s.setTextColor(GRAY);
            s.setTextSize(13);
            s.setPadding(0, dp(ctx, 3), 0, 0);
            s.setLineSpacing(0, 1.2f);
            texts.addView(s);
        }
        card.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(ctx, 10);
        card.setLayoutParams(lp);
        return card;
    }

    private static TextView chevron(Context ctx) {
        TextView chev = new TextView(ctx);
        chev.setText("›");
        chev.setTextColor(BLUE);
        chev.setTextSize(26);
        chev.setPadding(dp(ctx, 8), 0, 0, dp(ctx, 3));
        return chev;
    }

    private static View navRow(Context ctx, String title, String subtitle, final Runnable go) {
        LinearLayout card = cardShell(ctx, title, subtitle);
        card.addView(chevron(ctx));
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { go.run(); }
        });
        return card;
    }

    private static View switchRow(Context ctx, String title, String subtitle,
                                  final String key, boolean def) {
        LinearLayout card = cardShell(ctx, title, subtitle);
        Switch sw = new Switch(ctx);
        sw.setChecked(Cfg.getBool(key, def));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                saveOne(key, checked);
            }
        });
        card.addView(sw);
        return card;
    }

    private static View optionRow(Context ctx, String title, String subtitle,
                                  boolean selected, final Runnable pick) {
        LinearLayout card = cardShell(ctx, title, subtitle);
        if (selected) {
            TextView check = new TextView(ctx);
            check.setText("✓");
            check.setTextColor(BLUE);
            check.setTextSize(20);
            card.addView(check);
        }
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pick.run(); }
        });
        return card;
    }

    private static View textRow(final Context ctx, String label, final String key,
                                String def, final boolean multi) {
        final String value = Cfg.getString(key, def);
        LinearLayout card = cardShell(ctx, label,
                value == null || value.length() == 0 ? "未设置" : value);
        card.addView(chevron(ctx));
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                DarkInput.show(ctx, label, null, Cfg.getString(key, ""), multi, false,
                        new DarkInput.OnOk() {
                            @Override public void onOk(String text) {
                                saveOne(key, text.trim());
                                refreshCurrent(ctx, key);
                            }
                        });
            }
        });
        return card;
    }

    // ---------- saving / helpers ----------

    private static void refreshCurrent(Context ctx, String key) {
        if (key.startsWith("auto_task")) showTasks(ctx);
        else if (key.startsWith("keyword")) showKeyword(ctx);
        else if (key.startsWith("auto_reply")) showAutoReply(ctx);
        else if (key.startsWith("summary")) showSummary(ctx);
        else showBasic(ctx);
    }

    private static void saveOne(String key, Object value) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put(key, value);
        Cfg.saveAll(m);
    }

    private static int parseMode() {
        try { return Integer.parseInt(Cfg.getString("mode", "1")); }
        catch (Throwable t) { return 1; }
    }

    private static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
