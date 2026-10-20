package com.pinpic.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

/**
 * Satu font untuk seluruh UI, diambil dari assets/fonts/me.ttf.
 *
 * Pakai: FontManager.attach(rootView) sekali setelah setContentView().
 * View yang dibuat belakangan (bottom sheet, row list, dll) ikut diberi font
 * lewat global layout listener. Kalau file font tidak ada, UI memakai font default.
 */
public final class FontManager {

    private static final String FONT_ASSET = "fonts/me.ttf";

    /** Indeks = style (NORMAL 0, BOLD 1, ITALIC 2, BOLD_ITALIC 3). Null kalau font gagal dimuat. */
    private static Typeface[] variants;
    private static boolean loaded;

    private FontManager() {}

    public static synchronized void load(Context c) {
        if (loaded) return;
        loaded = true;
        try {
            Typeface base = Typeface.createFromAsset(c.getAssets(), FONT_ASSET);
            variants = new Typeface[]{
                base,
                Typeface.create(base, Typeface.BOLD),
                Typeface.create(base, Typeface.ITALIC),
                Typeface.create(base, Typeface.BOLD_ITALIC)
            };
        } catch (RuntimeException e) {
            variants = null;   // file tidak ada / rusak: pakai font default
        }
    }

    /** Pasang font ke root dan ke semua view yang muncul setelahnya. */
    public static void attach(final View root) {
        load(root.getContext().getApplicationContext());
        apply(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                apply(root);
            }
        });
    }

    /** Ganti font semua TextView (dan turunannya) di bawah v, sambil mempertahankan bold/italic. */
    public static void apply(View v) {
        if (variants == null || v == null) return;
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            Typeface cur = t.getTypeface();
            int style = cur != null ? cur.getStyle() & 3 : Typeface.NORMAL;
            Typeface target = variants[style];
            if (cur != target) t.setTypeface(target);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) apply(g.getChildAt(i));
        }
    }
}
