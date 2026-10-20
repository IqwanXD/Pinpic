package com.pinpic.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Data akun lokal. Foto profil disimpan di
 * Android/data/com.pinpic.app/files/account/avatar.png dan tidak dikirim ke mana pun.
 */
public final class Account {
    private Account() { }

    /** Folder account di penyimpanan khusus aplikasi (tanpa izin tambahan). */
    public static File dir(Context c) {
        File base = c.getExternalFilesDir(null);   // .../Android/data/com.pinpic.app/files
        if (base == null) base = c.getFilesDir();  // cadangan bila penyimpanan eksternal tidak ada
        return new File(base, "account");
    }

    public static File avatarFile(Context c) {
        return new File(dir(c), "avatar.png");
    }

    public static boolean hasAvatar(Context c) {
        return avatarFile(c).exists();
    }

    /** Uri avatar dengan versi (waktu tulis), supaya cache gambar tidak menampilkan foto lama. */
    public static Uri avatarUri(Context c) {
        File f = avatarFile(c);
        return Uri.fromFile(f).buildUpon()
                .appendQueryParameter("v", String.valueOf(f.lastModified()))
                .build();
    }

    /**
     * Tulis PNG lewat file sementara lalu ganti, supaya avatar lama tidak rusak kalau gagal.
     * Panggil dari background thread.
     */
    public static boolean saveAvatar(Context c, Bitmap bmp) {
        File dir = dir(c);
        if (!dir.exists() && !dir.mkdirs()) return false;
        File tmp = new File(dir, "avatar.tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, o)) return false;
        } catch (IOException e) {
            return false;
        }
        return tmp.renameTo(avatarFile(c));
    }
}
