package momoi.mod.qqpro.qqmax;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Port of the module's F13 scheduled messages.
 * Config (qqlite_settings.json): auto_task_enabled plus either the legacy
 * single rule (auto_task_time "08:00", auto_task_content, auto_task_peer_uid,
 * auto_task_chat_type) or auto_task_rules with one rule per line:
 * HH:mm,chatType,peerUid,content
 */
public final class TaskScheduler {
    private TaskScheduler() {}

    private static volatile boolean started;
    private static final Set<String> SENT = new HashSet<String>();

    public static void ensureStarted() {
        if (started) return;
        synchronized (TaskScheduler.class) {
            if (started) return;
            started = true;
        }
        Thread t = new Thread(new Runnable() {
            @Override public void run() { loop(); }
        }, "qqmax-task-scheduler");
        t.setDaemon(true);
        t.start();
    }

    private static void loop() {
        loadSent();
        while (true) {
            try {
                Thread.sleep(20_000L);
                if (Cfg.getBool("auto_task_enabled", false)) tick();
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                Kernel.log("TaskScheduler tick failed: " + t);
            }
        }
    }

    private static void tick() {
        Date now = new Date();
        String hhmm = new SimpleDateFormat("HH:mm", Locale.US).format(now);
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now);

        String rules = Cfg.getString("auto_task_rules", "");
        if (rules != null && rules.trim().length() > 0) {
            for (String line : rules.split("\n")) {
                line = line.trim();
                if (line.length() == 0) continue;
                String[] p = line.split(",", 4);
                if (p.length < 4) continue;
                fire(day, line, p[0].trim(), hhmm, parseInt(p[1], 2), p[2].trim(), p[3]);
            }
        } else {
            String time = Cfg.getString("auto_task_time", "08:00");
            String content = Cfg.getString("auto_task_content", "");
            String peer = Cfg.getString("auto_task_peer_uid", "");
            int chatType = parseInt(Cfg.getString("auto_task_chat_type", "2"), 2);
            if (content.length() > 0 && peer.length() > 0) {
                fire(day, "legacy", time, hhmm, chatType, peer, content);
            }
        }
    }

    private static void fire(String day, String ruleKey, String at, String hhmm, int chatType, String peerUid, String content) {
        if (!at.equals(hhmm)) return;
        String key = day + "|" + ruleKey;
        synchronized (SENT) {
            if (SENT.contains(key)) return;
            SENT.add(key);
        }
        saveSent();
        boolean ok = Sender.sendText(chatType, peerUid, content);
        Kernel.log("TaskScheduler: fired rule " + ruleKey + " ok=" + ok);
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return def; }
    }

    // Persist sent markers so a restart inside the same minute doesn't resend.
    private static File markerFile() {
        Context ctx = Cfg.getAppContext();
        if (ctx == null) return null;
        return new File(ctx.getFilesDir(), "qqmax_task_sent.properties");
    }

    private static void loadSent() {
        try {
            File f = markerFile();
            if (f == null || !f.exists()) return;
            Properties p = new Properties();
            FileInputStream in = new FileInputStream(f);
            p.load(in);
            in.close();
            synchronized (SENT) {
                for (String k : p.stringPropertyNames()) SENT.add(k);
            }
        } catch (Throwable ignored) {}
    }

    private static void saveSent() {
        try {
            File f = markerFile();
            if (f == null) return;
            Properties p = new Properties();
            synchronized (SENT) {
                for (String k : SENT) p.setProperty(k, "1");
            }
            FileOutputStream out = new FileOutputStream(f);
            p.store(out, "");
            out.close();
        } catch (Throwable ignored) {}
    }
}
