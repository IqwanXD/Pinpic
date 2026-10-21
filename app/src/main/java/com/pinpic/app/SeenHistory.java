package com.pinpic.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * Gambar beranda yang pernah ditampilkan, disimpan antar sesi aplikasi.
 * Gambar yang sudah pernah muncul tidak diulang lagi di beranda saat aplikasi dibuka.
 * Disimpan sebagai file teks (satu URL per baris), dibatasi MAX entri terbaru.
 */
final class SeenHistory {

    private static final int MAX = 6000;
    private static final LinkedHashSet<String> seen = new LinkedHashSet<>();
    private static File file;

    private SeenHistory() { }

    static synchronized void init(Context c) {
        if (file != null) return;
        file = new File(c.getApplicationContext().getFilesDir(), "seen_feed.txt");
        if (!file.exists()) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) if (!line.isEmpty()) seen.add(line);
        } catch (IOException ignored) { }
        trim();
    }

    static synchronized boolean has(String key) {
        return seen.contains(key);
    }

    /** Tandai sebagai sudah tampil. Yang terbaru ditaruh di akhir. */
    static synchronized void addAll(Collection<String> keys) {
        if (file == null || keys.isEmpty()) return;
        for (String k : keys) {
            seen.remove(k);
            seen.add(k);
        }
        trim();
        save();
    }

    private static void trim() {
        while (seen.size() > MAX) {
            Iterator<String> it = seen.iterator();
            it.next();
            it.remove();
        }
    }

    private static void save() {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            for (String k : seen) w.write(k + "\n");
        } catch (IOException ignored) { }
    }
}
