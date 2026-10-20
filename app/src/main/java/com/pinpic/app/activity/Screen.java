package com.pinpic.app.activity;

import android.view.View;

/** Kontrak layar utama. MainActivity menampung semua layar di satu FrameLayout dan memindahkannya dengan animasi. */
public interface Screen {
    View getView();

    /** Dipanggil saat layar mulai tampil. */
    void onShown();

    /** Dipanggil saat layar mulai disembunyikan. */
    void onHidden();

    /** Inset sistem (status bar, navigasi, notch) dari MainActivity. */
    void setInsets(int top, int bottom, int left, int right);
}
