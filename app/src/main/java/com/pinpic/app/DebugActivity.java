package com.pinpic.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/**
 * Halaman error saat aplikasi crash. Warna ikut token M3 aplikasi, edge-to-edge,
 * tanpa androidx / appcompat. Baris stack trace yang berasal dari androidx/appcompat disembunyikan.
 */
public class DebugActivity extends Activity {

    /** Nama extra berisi stack trace, sama dengan yang dikirim oleh Pinpic. */
    public static final String EXTRA_ERROR = "error";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        M3.init(this);

        // Edge-to-edge: konten digambar sampai di belakang status bar dan navigation bar
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(M3.background);
        // Beri jarak dari status bar dan navigation bar supaya konten tidak tertutup
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(
                    insets.getSystemWindowInsetLeft() + M3.dp(20),
                    insets.getSystemWindowInsetTop() + M3.dp(24),
                    insets.getSystemWindowInsetRight() + M3.dp(20),
                    insets.getSystemWindowInsetBottom() + M3.dp(20));
            return insets;
        });

        // Judul
        TextView title = M3.text(this, "Error", 32, M3.onSurface, true);
        root.addView(title);

        TextView sub = M3.text(this, "Crash", 16, M3.onSurfaceVariant, false);
        sub.setPadding(0, M3.dp(4), 0, M3.dp(16));
        root.addView(sub);

        // Isi error, bisa di-scroll
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(M3.shape(M3.surfaceContainerHigh, 24));
        scroll.setPadding(M3.dp(16), M3.dp(16), M3.dp(16), M3.dp(16));

        TextView body = new TextView(this);
        body.setText(formatError(getIntent().getStringExtra(EXTRA_ERROR)));
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextSize(12);
        body.setTextColor(M3.onSurface);
        body.setTextIsSelectable(true);
        scroll.addView(body, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(scroll, scrollLp);

        // Tombol tutup di paling bawah
        TextView close = M3.text(this, "Tutup", 16, M3.onPrimary, true);
        close.setGravity(Gravity.CENTER);
        close.setBackground(M3.ripple(M3.primary, 28));
        close.setClickable(true);
        close.setOnClickListener(v -> finishAffinity());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(-1, M3.dp(56));
        closeLp.topMargin = M3.dp(16);
        root.addView(close, closeLp);

        setContentView(root);
    }

    /**
     * Buang baris yang mengandung androidx atau appcompat, lalu beri nomor
     * pada setiap baris yang ditampilkan.
     */
    private static String formatError(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Tidak ada detail error.";

        StringBuilder sb = new StringBuilder();
        int no = 0;
        for (String line : raw.split("\n")) {
            String clean = line.replace("\r", "");
            String lower = clean.toLowerCase(Locale.ROOT);
            if (lower.contains("androidx") || lower.contains("appcompat")) continue;
            no++;
            sb.append(String.format(Locale.ROOT, "%3d  ", no)).append(clean).append('\n');
        }
        return sb.length() == 0 ? "Semua baris error berasal dari androidx/appcompat." : sb.toString();
    }
}
