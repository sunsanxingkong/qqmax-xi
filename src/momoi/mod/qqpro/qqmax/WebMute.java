package momoi.mod.qqpro.qqmax;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Member mute via the QQ group web-admin API, using a cookie (CK) the user
 * pastes into settings themselves (key webmute_ck). Auto-fetching the web
 * session (WebView cookies / kernel clientkey) proved unreliable and
 * crash-prone on device, so it was removed at lan's direction.
 *
 * Endpoint (Mojo-Webqq source, _shutup_group_member.pm):
 *   POST {qinfo.clt.qq.com|qun.qq.com}/cgi-bin/qun_info/set_group_shutup
 *   form: gc=<groupCode>, shutup_list=[{"uin":<memberUin>,"t":<seconds>}],
 *         bkn=hash33(skey from CK)
 *   Cookie: the user's CK verbatim. Success: JSON {"ec":0,...}
 */
public class WebMute {

    private static final String[] HOSTS = {"https://qinfo.clt.qq.com", "https://qun.qq.com"};
    private static final String PATH = "/cgi-bin/qun_info/set_group_shutup";
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    public static void mute(final Context ctx, final long groupCode, final long memberUin,
                            final int seconds) {
        if (!BUSY.compareAndSet(false, true)) {
            toast(ctx, "网页禁言：上一个还在处理中");
            return;
        }
        final String ck = Cfg.getString("webmute_ck", "");
        if (ck == null || ck.trim().length() == 0) {
            BUSY.set(false);
            toast(ctx, "网页禁言未配置：先到「QQMax 增强设置 → 基础设置 → 网页登录 CK」粘贴 CK");
            return;
        }
        if (memberUin <= 0) {
            BUSY.set(false);
            toast(ctx, "网页禁言失败：拿不到对方 QQ 号");
            return;
        }
        toast(ctx, seconds == 0 ? "网页禁言：正在解除…" : "网页禁言：正在提交…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    toast(ctx, doMute(ck.trim(), groupCode, memberUin, seconds));
                } catch (Throwable t) {
                    Kernel.log("WebMute failed: " + t);
                    toast(ctx, "网页禁言异常：" + shortMsg(t));
                } finally {
                    BUSY.set(false);
                }
            }
        }, "qqmax-webmute").start();
    }

    private static String doMute(String ck, long groupCode, long memberUin, int seconds)
            throws Exception {
        Map<String, String> cookies = new HashMap<String, String>();
        parseCookieString(ck, cookies);
        String skey = cookies.get("skey");
        if (skey == null || skey.length() == 0) {
            return "网页禁言失败：CK 里没有 skey，请粘贴 qun.qq.com 的完整 Cookie";
        }
        String bkn = String.valueOf(bkn(skey));
        String list = "[{\"uin\":" + memberUin + ",\"t\":" + seconds + "}]";
        String form = "gc=" + groupCode
                + "&shutup_list=" + URLEncoder.encode(list, "UTF-8")
                + "&bkn=" + bkn;
        String lastReply = null;
        for (String host : HOSTS) {
            try {
                StageLog.s("CK post begin host=" + host);
                String reply = httpPost(host + PATH, form, ck,
                        "http://qinfo.clt.qq.com/qinfo_v3/member.html");
                StageLog.s("CK post reply=" + (reply == null ? "null"
                        : reply.substring(0, Math.min(80, reply.length()))));
                lastReply = reply;
                if (reply != null && (reply.contains("\"ec\":0") || reply.contains("\"ec\": 0"))) {
                    return seconds == 0 ? "已解除禁言（网页接口）"
                            : "已禁言（网页接口） " + humanize(seconds);
                }
            } catch (Throwable t) {
                Kernel.log("WebMute host " + host + " error: " + t);
                lastReply = "网络错误：" + shortMsg(t);
            }
        }
        return "网页禁言失败，服务器返回：" + trim(lastReply, 120);
    }

    private static void parseCookieString(String c, Map<String, String> out) {
        for (String part : c.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                String k = part.substring(0, eq).trim();
                String v = part.substring(eq + 1).trim();
                if (v.length() > 0) out.put(k, v);
            }
        }
    }

    private static String httpPost(String url, String form, String cookieHeader, String referer)
            throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        if (referer != null) conn.setRequestProperty("Referer", referer);
        if (cookieHeader != null) conn.setRequestProperty("Cookie", cookieHeader);
        OutputStream os = conn.getOutputStream();
        os.write(form.getBytes("UTF-8"));
        os.close();
        InputStream is = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) return "HTTP " + conn.getResponseCode() + " (no body)";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    /** QQ web bkn/g_tk: hash33 over the skey. */
    private static int bkn(String skey) {
        long hash = 5381;
        for (int i = 0; i < skey.length(); i++) {
            hash = hash + (hash << 5) + skey.charAt(i);
        }
        return (int) (hash & 0x7fffffffL);
    }

    private static String humanize(int seconds) {
        if (seconds % 86400 == 0) return (seconds / 86400) + " 天";
        if (seconds % 3600 == 0) return (seconds / 3600) + " 小时";
        if (seconds % 60 == 0) return (seconds / 60) + " 分钟";
        return seconds + " 秒";
    }

    private static String shortMsg(Throwable t) {
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m != null ? ": " + m : "");
    }

    private static String trim(String s, int max) {
        if (s == null) return "(无返回)";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static void toast(final Context ctx, final String text) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Kernel.toast(ctx, text);
            }
        });
    }
}
