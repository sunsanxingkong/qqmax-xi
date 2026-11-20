package momoi.mod.qqpro.qqmax;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Port of the module's F20: long-press an image in the mod's image viewer
 * (MediaPager) to save it into /sdcard/Pictures/QQ Max.
 * Patched at the head of MediaPager$MediaAdapter.onBindViewHolder (classes.dex).
 * Image lookup mirrors the module's reflective approach.
 */
public final class PicSaver {
    private PicSaver() {}

    public static void attach(Object holder, final Object adapter, final int position) {
        try {
            Object iv = Refl.getField(holder, "itemView");
            if (!(iv instanceof View)) return;
            View itemView = (View) iv;
            Cfg.setAppContext(itemView.getContext());
            itemView.setLongClickable(true);
            itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showSaveDialog(v.getContext(), adapter, position);
                    return true;
                }
            });
        } catch (Throwable t) {
            Kernel.log("PicSaver.attach failed: " + t);
        }
    }

    private static void showSaveDialog(final Context ctx, final Object adapter, final int position) {
        try {
            new AlertDialog.Builder(ctx)
                    .setItems(new String[]{"保存图片"}, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            new Thread(new Runnable() {
                                @Override public void run() { save(ctx, adapter, position); }
                            }, "qqmax-picsaver").start();
                        }
                    })
                    .show();
        } catch (Throwable t) {
            Kernel.log("PicSaver dialog failed: " + t);
        }
    }

    private static Object itemAt(Object adapter, int position) {
        Object items = Refl.getField(adapter, "items");
        if (items instanceof List) {
            List list = (List) items;
            if (position >= 0 && position < list.size()) return list.get(position);
        }
        return null;
    }

    private static void save(Context ctx, Object adapter, int position) {
        try {
            Object item = itemAt(adapter, position);
            String local = null;
            String url = null;
            if (item != null) {
                local = Refl.callString(item, "getImageLocalPath");
                if (local == null) local = Refl.callString(item, "getLocalPath");
                Object img = Refl.call(item, "getImage");
                if (img instanceof String) {
                    String s = (String) img;
                    if (s.startsWith("http")) url = s; else if (local == null) local = s;
                }
                url = firstNonEmpty(url, Refl.callString(item, "getFullUrl"), Refl.callString(item, "getImageUrl"), Refl.callString(item, "getUrl"));
            }
            if (url == null) url = firstNonEmpty(Refl.callString(adapter, "getFullUrl"), Refl.callString(adapter, "getImageUrl"));

            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "QQ Max");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "Image_" + System.currentTimeMillis() + ".jpg");

            boolean ok = false;
            if (local != null && new File(local).isFile()) {
                copy(new File(local), out);
                ok = out.length() > 0;
            } else if (url != null && url.startsWith("http")) {
                download(url, out);
                ok = out.length() > 0;
            }
            if (ok) {
                MediaScannerConnection.scanFile(ctx, new String[]{out.getAbsolutePath()}, null, null);
                Kernel.toast(ctx, "已保存到 Pictures/QQ Max");
            } else {
                Kernel.toast(ctx, "保存失败：未找到图片源");
            }
        } catch (Throwable t) {
            Kernel.log("PicSaver.save failed: " + t);
            Kernel.toast(ctx, "保存失败：" + t.getMessage());
        }
    }

    private static String firstNonEmpty(String... vals) {
        for (String v : vals) if (v != null && v.length() > 0) return v;
        return null;
    }

    private static void copy(File src, File dst) throws Exception {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        out.flush();
        out.close();
        in.close();
    }

    private static void download(String urlStr, File dst) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        InputStream in = conn.getInputStream();
        OutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        out.flush();
        out.close();
        in.close();
        conn.disconnect();
    }
}
