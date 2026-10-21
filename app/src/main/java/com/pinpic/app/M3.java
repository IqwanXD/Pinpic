package com.pinpic.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.TextView;

/** Token warna + bentuk Material 3 (Expressive) tema gelap, tanpa library. */
public final class M3 {
    public static int primary, onPrimary, primaryContainer, onPrimaryContainer;
    public static int secondaryContainer, onSecondaryContainer;
    public static int background, surfaceContainerHigh, onSurface, onSurfaceVariant, outline;
    public static int scrim = 0x99000000, inverseSurface, inverseOnSurface;
    private static float density = 1f;
    private static boolean inited;

    public static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        if (Build.VERSION.SDK_INT >= 31) { // Dynamic color (Material You)
            primary = c.getColor(android.R.color.system_accent1_200);
            onPrimary = c.getColor(android.R.color.system_accent1_800);
            primaryContainer = c.getColor(android.R.color.system_accent1_700);
            onPrimaryContainer = c.getColor(android.R.color.system_accent1_100);
            secondaryContainer = c.getColor(android.R.color.system_accent2_700);
            onSecondaryContainer = c.getColor(android.R.color.system_accent2_100);
            background = mix(c.getColor(android.R.color.system_neutral1_900), Color.BLACK, 0.35f);
            surfaceContainerHigh = c.getColor(android.R.color.system_neutral1_800);
            onSurface = c.getColor(android.R.color.system_neutral1_100);
            onSurfaceVariant = c.getColor(android.R.color.system_neutral2_200);
            outline = c.getColor(android.R.color.system_neutral2_400);
        } else { // Palet statis baseline
            primary = 0xFFD0BCFF;
            onPrimary = 0xFF381E72;
            primaryContainer = 0xFF4F378B;
            onPrimaryContainer = 0xFFEADDFF;
            secondaryContainer = 0xFF4A4458;
            onSecondaryContainer = 0xFFE8DEF8;
            background = 0xFF0F0D13;
            surfaceContainerHigh = 0xFF2B2930;
            onSurface = 0xFFE6E0E9;
            onSurfaceVariant = 0xFFCAC4D0;
            outline = 0xFF938F99;
        }
        inverseSurface = onSurface;
        inverseOnSurface = background;
        inited = true;
    }

    public static int dp(float v) { return Math.round(v * density); }

    public static int mix(int a, int b, float t) {
        return Color.rgb(
                (int) (Color.red(a) * (1 - t) + Color.red(b) * t),
                (int) (Color.green(a) * (1 - t) + Color.green(b) * t),
                (int) (Color.blue(a) * (1 - t) + Color.blue(b) * t));
    }

    public static GradientDrawable shape(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    public static GradientDrawable shape(int color, float tl, float tr, float br, float bl) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadii(new float[]{dp(tl), dp(tl), dp(tr), dp(tr), dp(br), dp(br), dp(bl), dp(bl)});
        return g;
    }

    public static Drawable ripple(int fill, float radiusDp) {
        double lum = 0.299 * Color.red(fill) + 0.587 * Color.green(fill) + 0.114 * Color.blue(fill);
        int rc = (Color.alpha(fill) > 0 && lum > 140) ? 0x33000000 : 0x33FFFFFF;
        return new RippleDrawable(ColorStateList.valueOf(rc), shape(fill, radiusDp), shape(Color.WHITE, radiusDp));
    }

    public static void round(View v, final float rDp) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(rDp));
            }
        });
        v.setClipToOutline(true);
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        // DEFAULT_BOLD/DEFAULT (bukan Typeface.create) supaya gaya tebal tetap terbawa oleh FontManager.
        t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return t;
    }

    /**
     * Jarak toast dari bawah layar (dp). Toast sistem sudah otomatis naik di atas navigation bar,
     * jadi posisi ini sama dengan snackbar "Gambar dihapus" (100dp di atas navigasi).
     */
    private static final int TOAST_BOTTOM_DP = 100;

    /**
     * Toast bawaan Android (mengambang, muncul di atas navigasi) dengan tampilan custom bergaya M3:
     * kartu inverse surface, sudut membulat, teks inverse on surface. Dipakai di semua activity.
     */
    public static void toast(Context c, CharSequence msg) {
        if (!inited) init(c);
        Context app = c.getApplicationContext();

        TextView t = new TextView(app);
        t.setText(msg);
        t.setTextSize(14);
        t.setTextColor(inverseOnSurface);
        t.setTypeface(Typeface.DEFAULT);
        t.setPadding(dp(20), dp(14), dp(20), dp(14));
        t.setBackground(shape(inverseSurface, 16));
        t.setElevation(dp(10));

        android.widget.Toast toast = new android.widget.Toast(app);
        toast.setView(t);
        toast.setDuration(android.widget.Toast.LENGTH_SHORT);
        toast.setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL, 0, dp(TOAST_BOTTOM_DP));
        toast.show();
    }
}
