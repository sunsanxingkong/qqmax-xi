package momoi.mod.qqpro.qqmax;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Port of the module's F10 auto-reply, F11 keyword reply and F12 keyword AI
 * reply. Config keys follow the module (qqlite_settings.json):
 * auto_reply_enabled/content/peers, keyword_reply_enabled/keywords/content/
 * peers/rules, keyword_ai_url/api_key/model/persona.
 * Keyword rule lines: keyword|reply|peersCsv  (reply "AI" = ask the AI endpoint).
 */
public final class ReplyHook {
    private ReplyHook() {}

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final long COOLDOWN_MS = 60_000L;
    private static final Map<String, Long> LAST_REPLY = new HashMap<String, Long>();
    private static final Set<Long> SEEN = new HashSet<Long>();
    private static volatile String selfUid;

    public static void noteSelfUid(String uid) {
        if (uid != null && uid.length() > 0) selfUid = uid;
    }

    private static String selfUid() {
        if (selfUid != null) return selfUid;
        try {
            Class<?> kt = Class.forName("momoi.mod.qqpro.hook.contact.SelfContactKt");
            Object contact = kt.getMethod("getSelfContact").invoke(null);
            selfUid = Refl.getString(contact, "peerUid");
        } catch (Throwable ignored) {}
        return selfUid;
    }

    /** Evaluate a batch of incoming records (List / single / array). */
    public static void onIncoming(final Object arg) {
        try {
            TaskScheduler.ensureStarted();
            if (!Cfg.getBool("auto_reply_enabled", false)
                    && !Cfg.getBool("keyword_reply_enabled", false)) return;
            EXEC.execute(new Runnable() {
                @Override public void run() {
                    try { evaluate(arg); } catch (Throwable t) { Kernel.log("ReplyHook failed: " + t); }
                }
            });
        } catch (Throwable ignored) {}
    }

    private static void evaluate(Object arg) {
        if (arg instanceof List) {
            for (Object o : (List) arg) evaluateOne(o);
        } else if (arg != null && arg.getClass().isArray()) {
            int n = java.lang.reflect.Array.getLength(arg);
            for (int i = 0; i < n; i++) evaluateOne(java.lang.reflect.Array.get(arg, i));
        } else if (arg != null) {
            evaluateOne(arg);
        }
    }

    private static void evaluateOne(Object record) {
        if (record == null) return;
        long msgId = Refl.getLong(record, "msgId", 0L);
        String senderUid = Refl.getString(record, "senderUid");
        String peerUid = Refl.getString(record, "peerUid");
        int chatType = Refl.getInt(record, "chatType", 0);
        if (peerUid == null || senderUid == null) return;
        String self = selfUid();
        if (self != null && self.equals(senderUid)) return;      // never reply to self
        if (msgId != 0L) {
            synchronized (SEEN) {
                if (!SEEN.add(msgId)) return;                    // dedup
                if (SEEN.size() > 2000) SEEN.clear();
            }
        }
        String text = AntiRecall.extractText(record);
        if (text.length() == 0) return;

        String reply = null;
        boolean ai = false;

        if (Cfg.getBool("keyword_reply_enabled", false)) {
            String[] hit = matchKeyword(text, peerUid);
            if (hit != null) {
                if ("AI".equals(hit[0])) ai = true; else reply = hit[0];
            }
        }
        if (reply == null && !ai && Cfg.getBool("auto_reply_enabled", false)) {
            if (peerAllowed(Cfg.getString("auto_reply_peers", ""), peerUid)) {
                reply = Cfg.getString("auto_reply_content", "");
                if (reply.length() == 0) reply = null;
            }
        }
        if (reply == null && !ai) return;

        long now = System.currentTimeMillis();
        synchronized (LAST_REPLY) {
            Long last = LAST_REPLY.get(peerUid);
            if (last != null && now - last < COOLDOWN_MS) return;
            LAST_REPLY.put(peerUid, now);
        }
        if (ai) {
            String generated = askAi(text);
            if (generated == null || generated.length() == 0) return;
            reply = generated;
        }
        Sender.sendText(chatType, peerUid, reply);
        Kernel.log("ReplyHook: replied to " + peerUid);
    }

    private static boolean peerAllowed(String csv, String peerUid) {
        if (csv == null || csv.trim().length() == 0) return false;
        for (String s : csv.split(",")) if (s.trim().equals(peerUid)) return true;
        return false;
    }

    /** Returns {replyText} or {"AI"} when a rule hits, else null. */
    private static String[] matchKeyword(String text, String peerUid) {
        String rules = Cfg.getString("keyword_reply_rules", "");
        if (rules != null && rules.trim().length() > 0) {
            for (String line : rules.split("\n")) {
                line = line.trim();
                if (line.length() == 0) continue;
                String[] parts = line.split("\\|", 3);
                if (parts.length < 2) continue;
                if (!text.contains(parts[0])) continue;
                if (parts.length == 3 && parts[2].trim().length() > 0
                        && !peerAllowed(parts[2], peerUid)) continue;
                return new String[]{parts[1]};
            }
        }
        String keywords = Cfg.getString("keyword_reply_keywords", "");
        String peers = Cfg.getString("keyword_reply_peers", "");
        if (keywords != null && keywords.trim().length() > 0) {
            if (peers.trim().length() > 0 && !peerAllowed(peers, peerUid)) return null;
            for (String kw : keywords.split(",")) {
                kw = kw.trim();
                if (kw.length() > 0 && text.contains(kw)) {
                    String content = Cfg.getString("keyword_reply_content", "");
                    if (content.length() > 0) return new String[]{content};
                }
            }
        }
        return null;
    }

    private static String askAi(String userText) {
        try {
            String base = Cfg.getString("keyword_ai_url", "");
            String key = Cfg.getString("keyword_ai_api_key", "");
            String model = Cfg.getString("keyword_ai_model", "");
            String persona = Cfg.getString("keyword_ai_persona", "");
            if (base.length() == 0 || key.length() == 0) return null;
            if (model.length() == 0) model = "gpt-3.5-turbo";
            String url = base.endsWith("/") ? base + "chat/completions" : base + "/chat/completions";
            JSONObject body = new JSONObject();
            body.put("model", model);
            org.json.JSONArray msgs = new org.json.JSONArray();
            if (persona.length() > 0) {
                JSONObject sys = new JSONObject();
                sys.put("role", "system"); sys.put("content", persona);
                msgs.put(sys);
            }
            JSONObject usr = new JSONObject();
            usr.put("role", "user"); usr.put("content", userText);
            msgs.put(usr);
            body.put("messages", msgs);

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + key);
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            JSONObject resp = new JSONObject(sb.toString());
            return resp.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim();
        } catch (Throwable t) {
            Kernel.log("ReplyHook.askAi failed: " + t);
            return null;
        }
    }
}
