package com.pinpic.app.activity;

import android.graphics.Bitmap;

import com.pinpic.app.Downloader;
import com.pinpic.app.Pin;

/** Callback dari layar ke MainActivity (detail, menu opsi, aksi simpan/bagikan/hapus, dan pesan singkat). */
public interface ScreenHost {
    /** query = kata kunci asal gambar (dipakai sebagai judul/cadangan grid serupa). */
    void onOpenDetail(Pin pin, Bitmap preview, String query);

    void onOpenOptions(Pin pin, int tileWidth);

    void onSave(Pin pin);

    void onShare(Pin pin);

    /** Tahan gambar unduhan di galeri Saya: minta konfirmasi, lalu hapus. */
    void onDeleteSaved(Downloader.Saved s);

    /** Hapus beberapa gambar unduhan sekaligus (mode pilih di galeri Saya). */
    void onDeleteMany(java.util.List<Downloader.Saved> list);

    void onMessage(String msg);
}
