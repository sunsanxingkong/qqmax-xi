package momoi.mod.qqpro.qqmax;

import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Crash-proof stage logger: appends one line per stage to a PUBLIC file
 * (Download/qqmax-stage.log) with an fsync after every line, so after a
 * native crash the last line in the file names the call that died.
 * Also mirrored to logcat via Kernel.log.
 */
public final class StageLog {
    private StageLog() {}

    private static File file;

    private static File file() {
        if (file != null) return file;
        try {
            File dir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            if (dir != null) {
                if (!dir.exists()) dir.mkdirs();
                file = new File(dir, "qqmax-stage.log");
                return file;
            }
        } catch (Throwable ignored) {}
        try {
            file = new File("/sdcard/Download/qqmax-stage.log");
        } catch (Throwable ignored) {}
        return file;
    }

    public static synchronized void s(String stage) {
        Kernel.log("[stage] " + stage);
        try {
            File f = file();
            if (f == null) return;
            String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                    .format(new Date()) + " " + stage + "\n";
            FileOutputStream out = new FileOutputStream(f, true);
            out.write(line.getBytes("UTF-8"));
            out.getFD().sync();
            out.close();
        } catch (Throwable ignored) {}
    }
}
