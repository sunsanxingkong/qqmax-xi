package momoi.mod.qqpro.qqmax;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Map;

/**
 * Reads the QQMax enhancement module's own settings file
 * (filesDir/qqlite_settings.json, same keys/defaults as the Xposed module),
 * so ported features keep the module's configuration semantics.
 */
public final class Cfg {
    private Cfg() {}

    private static Context appContext;
    private static JSONObject cache;
    private static long cacheStamp = -1L;

    public static Context getAppContext() {
        if (appContext != null) return appContext;
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = at.getMethod("currentApplication").invoke(null);
            if (app instanceof Context) appContext = (Context) app;
        } catch (Throwable ignored) {}
        return appContext;
    }

    public static void setAppContext(Context ctx) {
        try {
            if (ctx != null) appContext = ctx.getApplicationContext();
        } catch (Throwable ignored) {}
    }

    private static synchronized JSONObject load() {
        Context ctx = getAppContext();
        if (ctx == null) return null;
        try {
            File f = new File(ctx.getFilesDir(), "qqlite_settings.json");
            long stamp = f.exists() ? f.lastModified() ^ f.length() : -2L;
            if (cache != null && stamp == cacheStamp) return cache;
            cacheStamp = stamp;
            if (!f.exists()) {
                cache = new JSONObject();
                return cache;
            }
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[(int) Math.min(f.length(), 1 << 20)];
            int off = 0, r;
            while (off < buf.length && (r = in.read(buf, off, buf.length - off)) > 0) off += r;
            in.close();
            cache = new JSONObject(new String(buf, 0, off, "UTF-8"));
            return cache;
        } catch (Throwable t) {
            return cache;
        }
    }

    public static boolean getBool(String key, boolean def) {
        JSONObject o = load();
        if (o == null) return def;
        return o.optBoolean(key, def);
    }

    public static String getString(String key, String def) {
        JSONObject o = load();
        if (o == null) return def;
        String v = o.optString(key, def);
        return v == null ? def : v;
    }

    public static int getInt(String key, int def) {
        JSONObject o = load();
        if (o == null) return def;
        return o.optInt(key, def);
    }

    /** Merges updates into qqlite_settings.json on disk, preserving other keys. */
    public static synchronized boolean saveAll(Map<String, Object> updates) {
        Context ctx = getAppContext();
        if (ctx == null || updates == null) return false;
        try {
            File f = new File(ctx.getFilesDir(), "qqlite_settings.json");
            JSONObject o = new JSONObject();
            if (f.exists()) {
                FileInputStream in = new FileInputStream(f);
                byte[] buf = new byte[(int) Math.min(f.length(), 1 << 20)];
                int off = 0, r;
                while (off < buf.length && (r = in.read(buf, off, buf.length - off)) > 0) off += r;
                in.close();
                if (off > 0) o = new JSONObject(new String(buf, 0, off, "UTF-8"));
            }
            for (Map.Entry<String, Object> e : updates.entrySet()) {
                o.put(e.getKey(), e.getValue());
            }
            FileOutputStream out = new FileOutputStream(f, false);
            out.write(o.toString().getBytes("UTF-8"));
            out.close();
            synchronized (Cfg.class) {
                cache = o;
                cacheStamp = f.lastModified() ^ f.length();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
