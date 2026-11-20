package momoi.mod.qqpro.qqmax;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Port of the module's F14: route the host's AI summary through a custom
 * OpenAI-compatible endpoint when summary_url / summary_api_key are set in
 * qqlite_settings.json (summary_model optional). Otherwise the host's own
 * summarize runs unchanged.
 * Patch point: Summarizer.summarize head (classes.dex).
 */
public final class AiSummary {
    private AiSummary() {}

    /** True = handled by the custom endpoint, skip the original summarize. */
    public static boolean tryHandle(final Object messages, final String language, final Object listener) {
        try {
            String base = Cfg.getString("summary_url", "");
            String key = Cfg.getString("summary_api_key", "");
            if (base.length() == 0 || key.length() == 0 || listener == null) return false;
            if (!(messages instanceof List) || ((List) messages).isEmpty()) return false;
            Thread t = new Thread(new Runnable() {
                @Override public void run() { AiSummary.run((List) messages, language, listener); }
            }, "qqmax-ai-summary");
            t.setDaemon(true);
            t.start();
            return true;
        } catch (Throwable t) {
            Kernel.log("AiSummary.tryHandle failed: " + t);
            return false;
        }
    }

    private static void run(List messages, String language, Object listener) {
        try {
            StringBuilder sb = new StringBuilder();
            for (Object msg : messages) {
                String sender = Refl.callString(msg, "getSender");
                String text = Refl.callString(msg, "getText");
                if (text == null) continue;
                sb.append(sender == null ? "" : sender).append(": ").append(text).append('\n');
            }
            String transcript = sb.toString();
            if (transcript.trim().length() == 0) {
                callListener(listener, "onError", "没有可总结的消息", false);
                return;
            }
            String prompt = "请总结以下聊天记录的要点，分条列出，简洁清晰"
                    + (language != null && language.length() > 0 && !"auto".equals(language)
                        ? "，使用语言：" + language : "")
                    + "：\n" + transcript;

            String base = Cfg.getString("summary_url", "");
            String key = Cfg.getString("summary_api_key", "");
            String model = Cfg.getString("summary_model", "");
            if (model.length() == 0) model = "gpt-3.5-turbo";
            String url = base.endsWith("/") ? base + "chat/completions" : base + "/chat/completions";

            JSONObject body = new JSONObject();
            body.put("model", model);
            JSONArray msgs = new JSONArray();
            JSONObject usr = new JSONObject();
            usr.put("role", "user");
            usr.put("content", prompt);
            msgs.put(usr);
            body.put("messages", msgs);

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder resp = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) resp.append(line);
            br.close();
            String content = new JSONObject(resp.toString())
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim();
            callListener(listener, "onChunk", content, null);
            callListener(listener, "onDone", 0, 0);
        } catch (Throwable t) {
            Kernel.log("AiSummary.run failed: " + t);
            callListener(listener, "onError", "网络错误，请重试", true);
        }
    }

    private static void callListener(Object listener, String name, Object a, Object b) {
        try {
            for (Method m : listener.getClass().getMethods()) {
                if (!m.getName().equals(name)) continue;
                Class<?>[] p = m.getParameterTypes();
                if (name.equals("onChunk") && p.length == 1) { m.invoke(listener, a); return; }
                if (name.equals("onError") && p.length == 2) { m.invoke(listener, a, b); return; }
                if (name.equals("onDone") && p.length == 2) { m.invoke(listener, a, b); return; }
            }
        } catch (Throwable t) {
            Kernel.log("AiSummary listener " + name + " failed: " + t);
        }
    }
}
