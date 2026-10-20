package com.pinpic.app.activity;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.pinpic.app.IconDrawable;
import com.pinpic.app.M3;

/** Pembantu tampilan kecil yang dipakai bersama oleh layar-layar di package ini. */
public final class Ui {

    private Ui() { }

    public static ImageView icon(Context c, int type, int color) {
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(new IconDrawable(type, color));
        return iv;
    }

    public static FrameLayout iconButton(Context c, int type, int fill, int tint, int sizeDp, float radiusDp,
                                         final Runnable click) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(M3.ripple(fill, radiusDp));
        int is = Math.round(sizeDp * 0.5f);
        f.addView(icon(c, type, tint), new FrameLayout.LayoutParams(M3.dp(is), M3.dp(is), Gravity.CENTER));
        f.setOnClickListener(v -> click.run());
        return f;
    }

    public static LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }
}
