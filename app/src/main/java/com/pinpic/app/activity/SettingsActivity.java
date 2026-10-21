package com.pinpic.app.activity;

import com.pinpic.app.*;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Pengaturan. Desain mengikuti SettingActivity di JetEditor: judul besar yang mengecil
 * dan pindah ke samping tombol kembali saat di-scroll. Font sama dengan seluruh aplikasi.
 */
public class SettingsActivity extends Activity {

    private static final float END_SCALE = 18f / 34f;   // 34sp mengecil jadi 18sp
    private static final int REQ_FOLDER = 41;
    /** Warna aksen bawaan, dipakai saat belum pernah memilih warna sendiri. */
    private static final int DEFAULT_ACCENT = 0xFFD0BCFF;
    private static final String KEY_SCROLL = "scrollY";

    private int expandedH, collapsedH, maxScroll, lastY;
    private int topInset, bottomInset;   // inset sistem: status bar dan navigasi
    private FrameLayout header;
    private FrameLayout rootFrame;   // tempat bottom sheet pemilih warna
    /** Jeda minimum antar pembangunan ulang tema saat ketukan beruntun (ms). Ketukan pertama langsung diproses. */
    private static final long THEME_STEP_MS = 120;
    private long lastThemeAt;
    private View backBtn;
    private TextView title;
    private LinearLayout col;
    private TextView folderSub;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.init(this);
        M3.init(this);
        final int restoreY = savedInstanceState == null ? 0 : savedInstanceState.getInt(KEY_SCROLL, 0);
        buildScreen(restoreY, null);
    }

    /**
     * Bangun seluruh isi layar. Dipakai saat pertama dibuka dan saat tema/warna berubah.
     * Saat tema berubah layar TIDAK di-recreate (itu yang membuat layar berkedip, scroll lompat
     * ke atas, dan saklar mulai dari OFF): isinya dibangun ulang di tempat, posisi scroll
     * dipulihkan sebelum frame pertama digambar, dan tampilan lama (snapshot) dipudarkan perlahan.
     *
     * @param restoreY posisi scroll yang dipulihkan (0 = dari atas)
     * @param snapshot gambar tampilan lama untuk crossfade, atau null
     */
    private void buildScreen(final int restoreY, Bitmap snapshot) {
        // Tinggi area tampil sebelumnya: dipakai agar rentang scroll sudah benar di layout pertama.
        final int viewportH = rootFrame != null ? rootFrame.getHeight() : 0;

        computeHeights();

        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(M3.dp(16), expandedH, M3.dp(16), bottomInset + M3.dp(24));
        if (viewportH > 0) col.setMinimumHeight(viewportH + maxScroll);

        addBlock(section("Sumber pencarian"));
        addBlock(sourcesCard());

        addBlock(section("Tampilan"));
        addBlock(displayCard());

        addBlock(section("Umum"));
        addBlock(card(
                actionRow(R.drawable.ic_delete, "Hapus cache gambar", "Kosongkan gambar yang tersimpan sementara", v -> {
                    ImageLoader.clear();
                    M3.toast(this, "Cache gambar dihapus");
                }),
                folderRow(),
                infoRow(R.drawable.ic_info, "Versi", versionName())));

        // Konten mulai di bawah header (lewat padding atas konten, bukan padding ScrollView).
        final ScrollView sv = new ScrollView(this);
        scroll = sv;
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(col);
        // Daftar pendek tetap bisa di-scroll sejauh maxScroll agar header bisa mengecil.
        sv.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int min = (b - t) + maxScroll;
            if (col.getMinimumHeight() != min) col.setMinimumHeight(min);
        });
        sv.setOnScrollChangeListener((v, x, y, ox, oy) -> collapse(y));

        header = buildHeader();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(M3.background);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        root.addView(header, new FrameLayout.LayoutParams(-1, expandedH, Gravity.TOP));
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                topInset = i.top;
                bottomInset = i.bottom;
            } else {
                topInset = ins.getSystemWindowInsetTop();
                bottomInset = ins.getSystemWindowInsetBottom();
            }
            applyInsets();
            return ins;
        });

        rootFrame = root;
        setContentView(root);
        // Window di-set SETELAH setContentView, sama urutannya dengan MainActivity.
        // Sebelumnya dipasang sebelum setContentView, dan bar navigasi masih kena scrim di beberapa perangkat.
        setupEdgeToEdge();
        FontManager.attach(root);
        collapse(restoreY);

        // Pulihkan posisi scroll SEBELUM frame pertama digambar, jadi tidak ada lompatan.
        if (restoreY > 0) {
            sv.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    sv.getViewTreeObserver().removeOnPreDrawListener(this);
                    sv.scrollTo(0, restoreY);
                    collapse(restoreY);
                    return true;
                }
            });
        }

        // Tampilan lama dipudarkan di atas tampilan baru: perubahan warna terasa halus.
        if (snapshot != null) fadeOutSnapshot(root, snapshot);
    }

    private void fadeOutSnapshot(final FrameLayout host, final Bitmap bmp) {
        final ImageView cover = new ImageView(this);
        cover.setImageBitmap(bmp);
        cover.setScaleType(ImageView.ScaleType.FIT_XY);
        host.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        cover.animate().alpha(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .withEndAction(() -> {
                    host.removeView(cover);
                    cover.setImageDrawable(null);
                    bmp.recycle();
                }).start();
    }

    /** Hentikan ripple/pressed di seluruh pohon view dan langsung loncat ke kondisi akhir. */
    private void endRipples(View v) {
        if (v == null) return;
        Drawable bg = v.getBackground();
        if (bg instanceof RippleDrawable) {
            v.setPressed(false);
            v.setHovered(false);
            bg.setState(new int[0]);
            bg.jumpToCurrentState();
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) endRipples(g.getChildAt(i));
        }
    }

    /** Ambil gambar tampilan sekarang, atau null kalau belum bisa. */
    private Bitmap snapshotOf(View v) {
        if (v == null || v.getWidth() <= 0 || v.getHeight() <= 0) return null;
        try {
            Bitmap b = Bitmap.createBitmap(v.getWidth(), v.getHeight(), Bitmap.Config.ARGB_8888);
            v.draw(new Canvas(b));
            return b;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(KEY_SCROLL, lastY);
    }

    /** Edge-to-edge sama persis dengan MainActivity.setupEdgeToEdge: window penuh, bar sistem transparan. */
    private void setupEdgeToEdge() {
        Window w = getWindow();
        w.setBackgroundDrawable(new ColorDrawable(M3.background));
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setNavigationBarContrastEnforced(false);
            w.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        M3.styleBars(w);
    }

    /** Tinggi header memperhitungkan status bar, supaya judul dan tombol kembali tidak tertutup. */
    private void computeHeights() {
        expandedH = topInset + M3.dp(176);
        collapsedH = topInset + M3.dp(56);
        maxScroll = expandedH - collapsedH;
    }

    /** Dipanggil saat inset sistem diketahui: atur ulang padding konten dan posisi header. */
    private void applyInsets() {
        computeHeights();
        if (col != null) col.setPadding(M3.dp(16), expandedH, M3.dp(16), bottomInset + M3.dp(24));
        if (backBtn != null) {
            FrameLayout.LayoutParams bl = (FrameLayout.LayoutParams) backBtn.getLayoutParams();
            bl.topMargin = topInset + (M3.dp(56) - M3.dp(48)) / 2;
            backBtn.setLayoutParams(bl);
        }
        if (header != null) collapse(lastY);
    }

    private FrameLayout buildHeader() {
        FrameLayout h = new FrameLayout(this);
        h.setBackgroundColor(M3.background);

        ImageView back = new ImageView(this);
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setImageDrawable(VecIcon.of(this, R.drawable.ic_back, M3.onSurface, M3.dp(24)));
        // Ripple lingkaran kecil di sekitar ikon, tidak melebar ke seluruh bar.
        RippleDrawable br = new RippleDrawable(ColorStateList.valueOf(M3.rippleColor()), null, null);
        br.setRadius(M3.dp(20));
        back.setBackground(br);
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(M3.dp(48), M3.dp(48), Gravity.TOP | Gravity.START);
        bl.topMargin = (collapsedH - M3.dp(48)) / 2;
        bl.leftMargin = M3.dp(4);
        h.addView(back, bl);
        backBtn = back;

        // Satu judul saja: posisinya dihitung di collapse() dari bawah header (terbuka)
        // ke sejajar tombol kembali (tertutup).
        title = M3.text(this, "Pengaturan", 34, M3.onSurface, true);
        title.setLetterSpacing(-0.02f);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        tl.leftMargin = M3.dp(16);
        h.addView(title, tl);

        // Font dipasang setelah layout pertama, tinggi judul bisa berubah: hitung ulang posisinya.
        h.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> collapse(lastY));
        return h;
    }

    /** Dipanggil setiap scroll. scrollY = jarak konten yang sudah di-scroll. */
    private void collapse(int scrollY) {
        lastY = scrollY;
        float p = Math.max(0f, Math.min(1f, scrollY / (float) maxScroll));

        int h = Math.max(collapsedH, expandedH - scrollY);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) header.getLayoutParams();
        if (lp.height != h) {
            lp.height = h;
            header.setLayoutParams(lp);
        }

        float th = title.getHeight();
        if (th == 0) {
            title.measure(0, 0);
            th = title.getMeasuredHeight();
        }
        float s = 1f + (END_SCALE - 1f) * p;
        float topExpanded = expandedH - M3.dp(16) - th;           // menempel di bawah header
        float topCollapsed = topInset + (M3.dp(56) - th * END_SCALE) / 2f;  // sejajar tombol kembali

        title.setPivotX(0f);
        title.setPivotY(0f);
        title.setScaleX(s);
        title.setScaleY(s);
        title.setTranslationX(M3.dp(48) * p);                     // 16dp -> 64dp
        title.setTranslationY(topExpanded + (topCollapsed - topExpanded) * p);
    }

    // ------------------------------------------------------------ bagian & kartu

    private TextView section(String label) {
        TextView t = M3.text(this, label, 14, M3.primary, true);
        t.setPadding(M3.dp(8), M3.dp(20), M3.dp(8), M3.dp(4));
        return t;
    }

    private void addBlock(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = M3.dp(8);
        col.addView(v, lp);
    }

    /** Kartu berisi baris-baris, dipisahkan garis tipis. */
    private LinearLayout card(View... rows) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(M3.shape(M3.surfaceContainerHigh, 20));
        card.setClipToOutline(true);
        for (int i = 0; i < rows.length; i++) {
            card.addView(rows[i], new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (i < rows.length - 1) {
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, M3.dp(1));
                dl.leftMargin = M3.dp(60);
                View divider = new View(this);
                divider.setBackgroundColor(M3.alpha(M3.onSurface, 0.13f));
                card.addView(divider, dl);
            }
        }
        return card;
    }

    /** Kartu sumber pencarian: satu saklar per sumber. Disimpan langsung ke Prefs. */
    private LinearLayout sourcesCard() {
        View[] rows = new View[Sources.ALL.length];
        for (int i = 0; i < Sources.ALL.length; i++) {
            final Sources.Src s = Sources.ALL[i];
            final LinearLayout row = baseRow();
            final TextView sub = M3.text(this, "", 13, M3.onSurfaceVariant, false);
            final MaterialSwitch sw = new MaterialSwitch(this);
            // Saklar hanya tampilan: baris yang menangani ketukan. MaterialSwitch menelan sentuhan
            // dan langsung toggle sendiri, jadi dinonaktifkan agar klik baris tetap berjalan.
            sw.setEnabled(false);
            sw.setFocusable(false);
            sw.setActiveColor(M3.primary);
            sw.setInactiveColor(Color.TRANSPARENT);
            sw.setStrokeActiveColor(M3.primary);
            sw.setStrokeInactiveColor(M3.outline);
            sw.setThumbActiveColor(M3.onPrimary);
            sw.setThumbInactiveColor(M3.outline);

            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.addView(M3.text(this, s.name, 16, M3.onSurface, false));
            texts.addView(sub);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
            row.addView(texts, tl);
            row.addView(sw, new LinearLayout.LayoutParams(-2, -2));

            sw.setCheckedImmediate(Prefs.sourceOn(s.id));
            sub.setText(sw.isChecked() ? "Aktif" : "Nonaktif");
            row.setOnClickListener(v -> {
                boolean on = !sw.isChecked();
                sw.setChecked(on);
                Prefs.setSourceOn(s.id, on);
                sub.setText(on ? "Aktif" : "Nonaktif");
            });
            rows[i] = row;
        }
        return card(rows);
    }

    // ------------------------------------------------------------ tampilan

    private interface Toggle { void onToggle(boolean on); }

    /**
     * Kartu Tampilan: tema, dynamic color, dan warna aksen. Warna aksen hanya muncul
     * saat dynamic color mati (di bawah dynamic color).
     */
    private LinearLayout displayCard() {
        final boolean dynamicOk = Build.VERSION.SDK_INT >= 31;
        final boolean dynamicOn = dynamicOk && Prefs.dynamicColor();

        final LinearLayout accentBox = new LinearLayout(this);
        accentBox.setOrientation(LinearLayout.VERTICAL);
        accentBox.addView(divider(), dividerParams());
        accentBox.addView(accentRow(), new LinearLayout.LayoutParams(-1, -2));
        accentBox.setVisibility(dynamicOn ? View.GONE : View.VISIBLE);

        LinearLayout themeRow = switchRow("Tema aplikasi", "Gelap", "Terang",
                Prefs.darkTheme(), true, null, on -> {
                    Prefs.setDarkTheme(on);
                    applyTheme();
                });
        LinearLayout dynRow = switchRow("Dynamic color", "Warna dari wallpaper", "Warna dari App color",
                dynamicOn, dynamicOk, "Butuh Android 12 atau lebih baru", on -> {
                    Prefs.setDynamicColor(on);
                    accentBox.setVisibility(on ? View.GONE : View.VISIBLE);
                    applyTheme();
                });

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(M3.shape(M3.surfaceContainerHigh, 20));
        card.setClipToOutline(true);
        card.addView(themeRow, new LinearLayout.LayoutParams(-1, -2));
        card.addView(divider(), dividerParams());
        card.addView(dynRow, new LinearLayout.LayoutParams(-1, -2));
        card.addView(accentBox, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    /** Baris dengan saklar. Saklar hanya tampilan; ketukan pada baris yang menanganinya. */
    private LinearLayout switchRow(String label, final String onText, final String offText, boolean on,
                                   boolean enabled, String disabledText, final Toggle toggle) {
        final LinearLayout row = baseRow();
        final TextView sub = M3.text(this, "", 13, M3.onSurfaceVariant, false);
        final MaterialSwitch sw = new MaterialSwitch(this);
        sw.setEnabled(false);
        sw.setFocusable(false);
        sw.setActiveColor(M3.primary);
        sw.setInactiveColor(Color.TRANSPARENT);
        sw.setStrokeActiveColor(M3.primary);
        sw.setStrokeInactiveColor(M3.outline);
        sw.setThumbActiveColor(M3.onPrimary);
        sw.setThumbInactiveColor(M3.outline);
        sw.setCheckedImmediate(on);
        sub.setText(!enabled ? disabledText : (on ? onText : offText));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(M3.text(this, label, 16, M3.onSurface, false));
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(sw, new LinearLayout.LayoutParams(-2, -2));

        if (enabled) {
            row.setOnClickListener(v -> {
                boolean next = !sw.isChecked();
                sw.setChecked(next);
                sub.setText(next ? onText : offText);
                toggle.onToggle(next);
            });
        } else {
            row.setClickable(false);
            row.setBackground(null);
            row.setAlpha(0.38f);   // tampilan nonaktif M3
        }
        return row;
    }

    /** Baris "App color": ketuk untuk membuka pemilih warna di bottom sheet. */
    private LinearLayout accentRow() {
        final LinearLayout row = baseRow();
        final int saved = Prefs.appColor();
        final int current = saved != 0 ? saved : DEFAULT_ACCENT;

        final TextView sub = M3.text(this, saved == 0 ? "Palet bawaan Pinpic" : hex(saved),
                13, M3.onSurfaceVariant, false);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(M3.text(this, "App color", 16, M3.onSurface, false));
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(current);
        View dotView = new View(this);
        dotView.setBackground(dot);
        row.addView(dotView, new LinearLayout.LayoutParams(M3.dp(28), M3.dp(28)));

        row.setOnClickListener(v -> ColorPicker.show(this, current, color -> {
            Prefs.setAppColor(color);
            applyTheme();
        }));
        return row;
    }

    /**
     * Tema atau warna berubah. Beri waktu sebentar supaya saklar yang diketuk selesai bergeser,
     * lalu isi layar dibangun ulang di tempat dengan palet baru (tanpa recreate). Layar utama
     * menyesuaikan diri saat kita kembali.
     */
    private void applyTheme() {
        // Preferensi langsung disimpan. Ketukan pertama langsung diproses (frame berikutnya); ketukan
        // beruntun dibatasi tiap THEME_STEP_MS, jadi tema tetap berubah sambil di-spam tanpa menunggu berhenti.
        rootFrame.removeCallbacks(rebuildRunnable);
        long wait = Math.max(0, lastThemeAt + THEME_STEP_MS - SystemClock.uptimeMillis());
        rootFrame.postDelayed(rebuildRunnable, wait);
    }

    private final Runnable rebuildRunnable = () -> rebuildTheme();

    private void rebuildTheme() {
        if (isFinishing() || isDestroyed()) return;
        lastThemeAt = SystemClock.uptimeMillis();
        final int y = lastY;
        // Akhiri semua ripple dulu: snapshot digambar lewat Canvas biasa (tanpa clipToOutline),
        // jadi ripple yang masih jalan akan terekam sebagai kotak persegi lalu ikut memudar.
        endRipples(rootFrame);
        final Bitmap snap = snapshotOf(rootFrame);
        M3.init(this);   // palet dihitung ulang dari Prefs terbaru
        buildScreen(y, snap);
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(M3.alpha(M3.onSurface, 0.13f));
        return v;
    }

    private LinearLayout.LayoutParams dividerParams() {
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, M3.dp(1));
        dl.leftMargin = M3.dp(60);
        return dl;
    }

    private static String hex(int argb) {
        return String.format("#%06X", 0xFFFFFF & argb);
    }

    private LinearLayout baseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(M3.dp(16), M3.dp(14), M3.dp(16), M3.dp(14));
        // Mask (bukan null) membatasi ripple di dalam baris; tanpa mask ripple melebar ke baris lain.
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(M3.rippleColor()), null, new ColorDrawable(Color.WHITE)));
        row.setClickable(true);
        return row;
    }

    private View actionRow(int iconRes, String label, String sub, View.OnClickListener click) {
        LinearLayout row = baseRow();
        row.addView(icon(iconRes), new LinearLayout.LayoutParams(M3.dp(24), M3.dp(24)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(M3.text(this, label, 16, M3.onSurface, false));
        texts.addView(M3.text(this, sub, 13, M3.onSurfaceVariant, false));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = M3.dp(20);
        row.addView(texts, tl);
        row.setOnClickListener(click);
        return row;
    }

    private View infoRow(int iconRes, String label, String value) {
        LinearLayout row = baseRow();
        row.setClickable(false);
        row.addView(icon(iconRes), new LinearLayout.LayoutParams(M3.dp(24), M3.dp(24)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(M3.text(this, label, 16, M3.onSurface, false));
        texts.addView(M3.text(this, value, 13, M3.onSurfaceVariant, false));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = M3.dp(20);
        row.addView(texts, tl);
        return row;
    }

    private ImageView icon(int iconRes) {
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(VecIcon.of(this, iconRes, M3.onSurfaceVariant, M3.dp(24)));
        return iv;
    }

    /** Baris "Lokasi unduhan": ketuk untuk memilih folder, tahan untuk kembali ke Pictures/Pinpic. */
    private View folderRow() {
        LinearLayout row = baseRow();
        row.addView(icon(R.drawable.ic_folder), new LinearLayout.LayoutParams(M3.dp(24), M3.dp(24)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(M3.text(this, "Lokasi unduhan", 16, M3.onSurface, false));
        folderSub = M3.text(this, Downloader.locationLabel(), 13, M3.onSurfaceVariant, false);
        texts.addView(folderSub);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = M3.dp(20);
        row.addView(texts, tl);
        row.setOnClickListener(v -> pickFolder());
        row.setOnLongClickListener(v -> {
            Prefs.setDownloadTree(null);
            folderSub.setText(Downloader.locationLabel());
            M3.toast(this, "Kembali ke Pictures/Pinpic");
            return true;
        });
        return row;
    }

    /** Buka pemilih folder sistem (Storage Access Framework). Tidak butuh izin penyimpanan. */
    private void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_FOLDER);
        } catch (ActivityNotFoundException e) {
            M3.toast(this, "Pengelola file tidak ditemukan di perangkat");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_FOLDER || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            // Simpan izin supaya tetap bisa menulis setelah aplikasi ditutup.
            getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException e) {
            M3.toast(this, "Folder ini tidak bisa dipakai");
            return;
        }
        Prefs.setDownloadTree(tree.toString());
        folderSub.setText(Downloader.locationLabel());
        M3.toast(this, "Lokasi unduhan diubah");
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "-";
        }
    }
}
