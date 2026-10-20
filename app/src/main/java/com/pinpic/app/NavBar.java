package com.pinpic.app;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * Navigasi melayang 3 tab: Beranda, Cari, Saya.
 * Wadah berbentuk kotak membulat (bukan pil); setiap item juga kotak membulat.
 * Dipakai oleh MainActivity.
 */
final class NavBar extends LinearLayout {

    static final int HOME = 0, SEARCH = 1, ME = 2;

    private static final int CONTAINER_RADIUS_DP = 24;
    private static final int ITEM_RADIUS_DP = 16;

    interface Listener { void onTab(int index); }

    private static final int[] ICONS = {IconDrawable.HOME, IconDrawable.SEARCH, IconDrawable.PROFILE};

    private final FrameLayout[] tabs = new FrameLayout[3];
    private final ImageView[] icons = new ImageView[3];

    NavBar(Context c, final Listener l, int selected) {
        super(c);
        setOrientation(HORIZONTAL);
        setPadding(M3.dp(6), M3.dp(6), M3.dp(6), M3.dp(6));
        setBackground(M3.shape(M3.surfaceContainerHigh, CONTAINER_RADIUS_DP));
        setElevation(M3.dp(8));
        for (int k = 0; k < 3; k++) {
            final int idx = k;
            FrameLayout f = new FrameLayout(c);
            ImageView iv = new ImageView(c);
            f.addView(iv, new FrameLayout.LayoutParams(M3.dp(24), M3.dp(24), Gravity.CENTER));
            f.setOnClickListener(v -> l.onTab(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(M3.dp(64), M3.dp(56));
            lp.setMargins(M3.dp(2), 0, M3.dp(2), 0);
            addView(f, lp);
            tabs[k] = f;
            icons[k] = iv;
        }
        select(selected);
    }

    /** Tandai tab aktif: item aktif berwarna, item lain transparan; keduanya kotak membulat. */
    void select(int sel) {
        for (int k = 0; k < 3; k++) {
            boolean on = k == sel;
            tabs[k].setBackground(M3.ripple(on ? M3.primaryContainer : 0, ITEM_RADIUS_DP));
            icons[k].setImageDrawable(new IconDrawable(ICONS[k], on ? M3.onPrimaryContainer : M3.onSurfaceVariant));
        }
    }
}
