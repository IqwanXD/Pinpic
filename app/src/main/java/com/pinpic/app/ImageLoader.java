package com.pinpic.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Image loader mini: thread pool + LruCache + downsampling. Bisa dari URL internet maupun Uri lokal. */
public final class ImageLoader {
    public interface Callback { void onLoaded(Bitmap bmp); }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(6);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 5)) {
                @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
            };

    public static Bitmap cached(String url, int w) { return CACHE.get(url + "@" + w); }

    public static void clear() { CACHE.evictAll(); }

    /** Callback selalu di main thread. bmp == null jika url & fallback gagal. */
    public static Future<?> load(final String url, final String fallback, final int w,
                          final Bitmap.Config cfg, final Callback cb) {
        final String key = url + "@" + w;
        final Bitmap hit = CACHE.get(key);
        if (hit != null) {
            MAIN.post(() -> cb.onLoaded(hit));
            return null;
        }
        return POOL.submit(() -> {
            Bitmap b = fetch(url, w, cfg);
            if (b == null && fallback != null) b = fetch(fallback, w, cfg);
            if (b != null) CACHE.put(key, b);
            final Bitmap res = b;
            MAIN.post(() -> cb.onLoaded(res));
        });
    }

    /** Gambar tersimpan di perangkat (content:// atau file://). */
    public static Future<?> loadUri(final Context ctx, final Uri uri, final int w, final Callback cb) {
        final String key = uri + "@" + w;
        final Bitmap hit = CACHE.get(key);
        if (hit != null) {
            MAIN.post(() -> cb.onLoaded(hit));
            return null;
        }
        final Context app = ctx.getApplicationContext();
        return POOL.submit(() -> {
            Bitmap b = null;
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
                if (in != null) b = decode(readAll(in), w, Bitmap.Config.ARGB_8888);
            } catch (Throwable ignored) { }
            if (b != null) CACHE.put(key, b);
            final Bitmap res = b;
            MAIN.post(() -> cb.onLoaded(res));
        });
    }

    private static Bitmap fetch(String url, int w, Bitmap.Config cfg) {
        try {
            byte[] data = Net.bytes(url, Net.UA, Net.origin(url), null, 25 << 20);
            return decode(data, w, cfg);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap decode(byte[] data, int w, Bitmap.Config cfg) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (o.outWidth <= 0) return null;
            int s = 1;
            while (o.outWidth / (s * 2) >= w) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            o.inPreferredConfig = cfg;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
