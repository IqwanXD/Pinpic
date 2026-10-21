package com.pinpic.app;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Downloader {
    public interface Result { void done(boolean ok, String msg); }

    /** Satu gambar hasil unduhan. date = waktu dibuat (ms), dipakai untuk urutan terbaru dulu. */
    public static final class Saved {
        public final Uri uri;
        public final String name;
        public final long date;

        public Saved(Uri uri, String name, long date) {
            this.uri = uri;
            this.name = name;
            this.date = date;
        }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String FOLDER = "Pinpic";

    /**
     * Coba url satu per satu (mis. full lalu thumbnail). Simpan ke folder pilihan pengguna,
     * atau ke Pictures/Pinpic bila belum memilih. Nama file dari hash isi, jadi tidak tersimpan dua kali.
     */
    public static void save(Context ctx, final Result cb, final String... urls) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            String err = "Gagal mengunduh gambar";
            for (String u : urls) {
                if (u == null) continue;
                try {
                    final boolean fresh = write(app, u);
                    final String msg = fresh
                            ? "Tersimpan di " + locationLabel()
                            : "Gambar ini sudah ada di " + locationLabel();
                    MAIN.post(() -> cb.done(true, msg));
                    return;
                } catch (Exception e) {
                    err = "Gagal mengunduh: " + e.getMessage();
                }
            }
            final String m = err;
            MAIN.post(() -> cb.done(false, m));
        }).start();
    }

    /** Hapus satu gambar unduhan (dari Pictures/Pinpic maupun folder pilihan). */
    public static void delete(Context ctx, final Saved s, final Result cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            boolean ok;
            try {
                ok = remove(app, s.uri);
            } catch (Exception e) {
                ok = false;
            }
            final boolean res = ok;
            MAIN.post(() -> cb.done(res, res ? "Gambar dihapus" : "Gagal menghapus gambar"));
        }).start();
    }

    /** Teks lokasi unduhan untuk ditampilkan di pengaturan. */
    public static String locationLabel() {
        String t = Prefs.downloadTree();
        if (t == null) return "Pictures/" + FOLDER;
        String seg = Uri.parse(t).getLastPathSegment();   // mis. "primary:Download/Foto"
        if (seg == null) return "Folder pilihan";
        int i = seg.indexOf(':');
        String path = i >= 0 ? seg.substring(i + 1) : seg;
        return path.isEmpty() ? "Penyimpanan utama" : path;
    }

    /** Hapus beberapa gambar sekaligus berdasarkan nama file (termasuk salinan di lokasi lain). */
    public static void deleteNames(Context ctx, final Set<String> names, final Result cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            int n = 0;
            for (Saved s : listAll(app)) {
                if (!names.contains(s.name)) continue;
                try { if (remove(app, s.uri)) n++; } catch (Exception ignored) { }
            }
            final int count = n;
            MAIN.post(() -> cb.done(count > 0, count + " gambar dihapus"));
        }).start();
    }

    /**
     * Akses file langsung: Android 11+ dengan MANAGE_EXTERNAL_STORAGE (bisa menulis dan menghapus
     * file apa pun di Pictures/Pinpic), atau Android 9 ke bawah. Android 10 memakai MediaStore.
     */
    static boolean fileAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return Build.VERSION.SDK_INT < 29;
    }

    /** Daftar gambar hasil unduhan, terbaru dulu. Panggil dari background thread. */
    public static List<Saved> listSaved(Context ctx) {
        List<Saved> all = listAll(ctx);
        Collections.sort(all, (a, b) -> Long.compare(b.date, a.date));
        // Gambar yang sama bisa ada di dua lokasi (mis. setelah folder unduhan diganti): tampilkan sekali.
        Map<String, Saved> seen = new LinkedHashMap<>();
        for (Saved s : all) if (!seen.containsKey(s.name)) seen.put(s.name, s);
        return new ArrayList<>(seen.values());
    }

    private static List<Saved> listAll(Context ctx) {
        List<Saved> out = new ArrayList<>();
        out.addAll(listDefault(ctx));
        String t = Prefs.downloadTree();
        if (t != null) out.addAll(listTree(ctx, Uri.parse(t)));
        return out;
    }

    /** Gambar dengan nama ini sudah ada di lokasi mana pun (Pictures/Pinpic atau folder pilihan)? */
    private static boolean existsAnywhere(Context ctx, String tree, String name) {
        for (Saved s : listDefault(ctx)) if (s.name.equals(name)) return true;
        if (tree != null) for (Saved s : listTree(ctx, Uri.parse(tree))) if (s.name.equals(name)) return true;
        return false;
    }

    private static List<Saved> listDefault(Context ctx) {
        List<Saved> out = new ArrayList<>();
        try {
            if (!fileAccess() && Build.VERSION.SDK_INT >= 29) {
                Uri coll = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                String[] proj = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.DATE_ADDED};
                String sel = MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
                String[] args = {Environment.DIRECTORY_PICTURES + "/" + FOLDER + "%"};
                try (Cursor c = ctx.getContentResolver().query(coll, proj, sel, args, null)) {
                    if (c != null) {
                        while (c.moveToNext()) {
                            long id = c.getLong(0);
                            out.add(new Saved(ContentUris.withAppendedId(coll, id),
                                    c.getString(1), c.getLong(2) * 1000L));
                        }
                    }
                }
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER);
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (sniffName(f.getName())) out.add(new Saved(Uri.fromFile(f), f.getName(), f.lastModified()));
                    }
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Gambar di dalam folder pilihan (SAF). Tidak butuh izin penyimpanan umum. */
    private static List<Saved> listTree(Context ctx, Uri tree) {
        List<Saved> out = new ArrayList<>();
        try {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                    tree, DocumentsContract.getTreeDocumentId(tree));
            String[] proj = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_LAST_MODIFIED};
            try (Cursor c = ctx.getContentResolver().query(children, proj, null, null, null)) {
                if (c == null) return out;
                while (c.moveToNext()) {
                    String name = c.getString(1);
                    if (name == null || !sniffName(name)) continue;
                    Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0));
                    out.add(new Saved(doc, name, c.getLong(2)));
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static boolean sniffName(String n) {
        String l = n.toLowerCase(Locale.ROOT);
        return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp") || l.endsWith(".gif");
    }

    /** @return true jika file baru disimpan; false jika gambar identik sudah ada (tidak disimpan ulang). */
    private static boolean write(Context ctx, String url) throws Exception {
        byte[] d = Net.bytes(url, Net.UA, Net.origin(url), null, 40 << 20);
        String mime = sniff(d);
        if (mime == null) throw new IOException("bukan file gambar");
        String ext = mime.substring(6).replace("jpeg", "jpg");
        String name = "pinpic_" + sha256(d).substring(0, 16) + "." + ext;

        String tree = Prefs.downloadTree();
        if (existsAnywhere(ctx, tree, name)) return false;   // sudah ada: jangan disimpan dua kali
        if (tree != null) return writeTree(ctx, Uri.parse(tree), name, mime, d);
        if (!fileAccess() && Build.VERSION.SDK_INT >= 29) return writeMedia(ctx, name, mime, d);
        return writeFile(ctx, name, mime, d);
    }

    /** Folder pilihan pengguna (SAF). */
    private static boolean writeTree(Context ctx, Uri tree, String name, String mime, byte[] d) throws IOException {
        ContentResolver r = ctx.getContentResolver();
        for (Saved s : listTree(ctx, tree)) {
            if (s.name.equals(name)) return false;
        }
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        Uri created = DocumentsContract.createDocument(r, parent, mime, name);
        if (created == null) throw new IOException("folder pilihan tidak bisa ditulis");
        try (OutputStream o = r.openOutputStream(created)) {
            if (o == null) throw new IOException("file tidak bisa dibuat");
            o.write(d);
        } catch (IOException e) {
            DocumentsContract.deleteDocument(r, created);   // jangan tinggalkan file kosong
            throw e;
        }
        return true;
    }

    /** Pictures/Pinpic lewat MediaStore (Android 10+). */
    private static boolean writeMedia(Context ctx, String name, String mime, byte[] d) throws IOException {
        ContentResolver r = ctx.getContentResolver();
        Uri coll = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String sel = MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + " LIKE ?";
        String[] args = {name, Environment.DIRECTORY_PICTURES + "/" + FOLDER + "%"};
        try (Cursor c = r.query(coll, new String[]{MediaStore.Images.Media._ID}, sel, args, null)) {
            if (c != null && c.moveToFirst()) return false;
        }
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER);
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = r.insert(coll, v);
        if (uri == null) throw new IOException("MediaStore gagal");
        try (OutputStream o = r.openOutputStream(uri)) { o.write(d); }
        v.clear();
        v.put(MediaStore.MediaColumns.IS_PENDING, 0);
        r.update(uri, v, null, null);
        return true;
    }

    /** Pictures/Pinpic sebagai file (Android 9 ke bawah). */
    private static boolean writeFile(Context ctx, String name, String mime, byte[] d) throws IOException {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("folder tidak bisa dibuat");
        File f = new File(dir, name);
        if (f.exists()) return false;
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(d); }
        MediaScannerConnection.scanFile(ctx, new String[]{f.getAbsolutePath()}, new String[]{mime}, null);
        return true;
    }

    private static boolean remove(Context ctx, Uri uri) throws Exception {
        if ("file".equals(uri.getScheme())) {
            File f = new File(uri.getPath());
            if (!f.delete()) return false;
            MediaScannerConnection.scanFile(ctx, new String[]{f.getAbsolutePath()}, null, null);
            return true;
        }
        ContentResolver r = ctx.getContentResolver();
        String path = uri.getPath();
        if (path != null && path.contains("/document/")) return DocumentsContract.deleteDocument(r, uri);
        return r.delete(uri, null, null) > 0;
    }

    private static String sha256(byte[] d) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(d);
        StringBuilder sb = new StringBuilder(h.length * 2);
        for (byte b : h) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }

    private static String sniff(byte[] d) {
        if (d.length < 12) return null;
        if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8) return "image/jpeg";
        if ((d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
        if (d[0] == 'G' && d[1] == 'I' && d[2] == 'F') return "image/gif";
        if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return "image/webp";
        return null;
    }
}
