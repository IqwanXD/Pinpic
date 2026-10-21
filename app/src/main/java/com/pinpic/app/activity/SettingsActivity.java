package com.pinpic.app.activity;

import com.pinpic.app.*;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
    private static final int C_DIVIDER = 0x22FFFFFF;
    private static final int C_RIPPLE = 0x33FFFFFF;
    private static final int REQ_FOLDER = 41;

    private int expandedH, collapsedH, maxScroll, lastY;
    private FrameLayout header;
    private TextView title;
    private LinearLayout col;
    private TextView folderSub;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.init(this);
        M3.init(this);
        getWindow().setStatusBarColor(M3.background);
        getWindow().setNavigationBarColor(M3.background);

        expandedH = M3.dp(176);
        collapsedH = M3.dp(56);
        maxScroll = expandedH - collapsedH;

        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(M3.dp(16), expandedH, M3.dp(16), M3.dp(24));

        addBlock(section("Sumber pencarian"));
        addBlock(sourcesCard());

        addBlock(section("Umum"));
        addBlock(card(
                actionRow(R.drawable.ic_delete, "Hapus cache gambar", "Kosongkan gambar yang tersimpan sementara", v -> {
                    ImageLoader.clear();
                    M3.toast(this, "Cache gambar dihapus");
                }),
                folderRow(),
                infoRow(R.drawable.ic_info, "Versi", versionName())));

        // Konten mulai di bawah header (lewat padding atas konten, bukan padding ScrollView).
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(col);
        // Daftar pendek tetap bisa di-scroll sejauh maxScroll agar header bisa mengecil.
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int min = (b - t) + maxScroll;
            if (col.getMinimumHeight() != min) col.setMinimumHeight(min);
        });
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> collapse(y));

        header = buildHeader();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(M3.background);
        root.setFitsSystemWindows(true);   // ruang untuk status bar & navigasi sistem
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        root.addView(header, new FrameLayout.LayoutParams(-1, expandedH, Gravity.TOP));

        setContentView(root);
        FontManager.attach(root);
        collapse(0);
    }

    private FrameLayout buildHeader() {
        FrameLayout h = new FrameLayout(this);
        h.setBackgroundColor(M3.background);

        ImageView back = new ImageView(this);
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setImageDrawable(VecIcon.of(this, R.drawable.ic_back, M3.onSurface, M3.dp(24)));
        // Ripple lingkaran kecil di sekitar ikon, tidak melebar ke seluruh bar.
        RippleDrawable br = new RippleDrawable(ColorStateList.valueOf(C_RIPPLE), null, null);
        br.setRadius(M3.dp(20));
        back.setBackground(br);
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(M3.dp(48), M3.dp(48), Gravity.TOP | Gravity.START);
        bl.topMargin = (collapsedH - M3.dp(48)) / 2;
        bl.leftMargin = M3.dp(4);
        h.addView(back, bl);

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
        float topCollapsed = (collapsedH - th * END_SCALE) / 2f;  // sejajar tombol kembali

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
                divider.setBackgroundColor(C_DIVIDER);
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

            sw.setChecked(Prefs.sourceOn(s.id));
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

    private LinearLayout baseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(M3.dp(16), M3.dp(14), M3.dp(16), M3.dp(14));
        // Mask (bukan null) membatasi ripple di dalam baris; tanpa mask ripple melebar ke baris lain.
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(C_RIPPLE), null, new ColorDrawable(Color.WHITE)));
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
