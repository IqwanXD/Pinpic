package com.pinpic.app.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.pinpic.app.Account;
import com.pinpic.app.Downloader;
import com.pinpic.app.ImageLoader;
import com.pinpic.app.M3;
import com.pinpic.app.Prefs;
import com.pinpic.app.R;
import com.pinpic.app.SimpleSwipeRefreshLayout;
import com.pinpic.app.VecIcon;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saya: profil (foto, nama, bio) + galeri gambar yang sudah diunduh.
 * Foto: ketuk untuk melihat, ketuk pensil untuk mengganti. Nama/bio: ketuk untuk mengedit.
 * Galeri: susunan masonry. Tinggi tiap gambar mengikuti rasio aslinya, jadi tidak semua kotak sama.
 * Ketuk gambar untuk membukanya. Tahan gambar untuk masuk mode pilih banyak, lalu hapus sekaligus.
 */
public final class ProfileScreen implements Screen {

    /** Kode permintaan untuk memilih dan memotong foto profil. Hasilnya diteruskan lewat onActivityResult. */
    public static final int REQ_PICK_AVATAR = 61;
    public static final int REQ_CROP_AVATAR = 62;

    private static final int COLS = 3;
    private static final int GAP_DP = 4;
    /** Batas rasio tinggi/lebar supaya tile tidak terlalu pendek atau terlalu tinggi. */
    private static final float MIN_RATIO = 0.66f;
    private static final float MAX_RATIO = 1.8f;
    /** Rasio gambar sudah dibaca. Kunci = uri. Dipakai ulang tiap kali galeri dimuat. */
    private static final Map<String, Float> ratioCache = new ConcurrentHashMap<>();

    /** Satu gambar di galeri. loaded = bitmap sudah diminta (pemuatan dilakukan saat terlihat). */
    private static final class Tile {
        final Downloader.Saved s;
        final ImageView iv;
        boolean loaded;

        Tile(Downloader.Saved s, ImageView iv) {
            this.s = s;
            this.iv = iv;
        }
    }

    private final Activity act;
    private final ScreenHost host;
    private final FrameLayout root;
    private final LinearLayout col;
    private final ScrollView gallery;
    private final LinearLayout board;
    private final LinearLayout[] columns = new LinearLayout[COLS];
    private final SimpleSwipeRefreshLayout swipe;
    private final TextView countText, emptyText, hintText, nameText, bioText, avatarLetter, selectCount;
    private final LinearLayout selectBar;
    private final ImageView avatarImg;
    private final List<Downloader.Saved> items = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    /** Kunci pilihan = uri gambar. */
    private final Set<String> selected = new HashSet<>();
    private boolean selecting;
    private final int pad, tileW, gap;
    private GradientDrawable overlay;

    public ProfileScreen(Activity act, ScreenHost host) {
        this.act = act;
        this.host = host;
        pad = M3.dp(16);
        gap = M3.dp(GAP_DP);
        tileW = (act.getResources().getDisplayMetrics().widthPixels - pad * 2 - gap * (COLS - 1)) / COLS;

        root = new FrameLayout(act);
        root.setBackgroundColor(M3.background);

        col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView title = M3.text(act, "Saya", 34, M3.primary, true);
        title.setLetterSpacing(-0.02f);
        col.addView(title, Ui.lp(-2, -2, pad, M3.dp(8), pad, 0));

        // Kartu profil: foto, nama, dan bio.
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        // Bentuk membulat dipasang sebagai ISI RippleDrawable. Kalau ripple langsung di-set sebagai
        // background, bentuk kartu hilang (kotak). Clip ke outline menjaga ripple tetap di sudut membulat.
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(M3.rippleColor()),
                M3.shape(M3.surfaceContainerHigh, 28), null));
        card.setClipToOutline(true);
        card.setClickable(true);
        card.setOnClickListener(v -> act.startActivity(new Intent(act, EditProfileActivity.class)));
        card.setPadding(M3.dp(16), M3.dp(16), M3.dp(16), M3.dp(16));

        FrameLayout avatarBox = new FrameLayout(act);
        avatarBox.setBackground(M3.shape(M3.primaryContainer, 32));
        M3.round(avatarBox, 32);
        avatarBox.setClickable(true);
        avatarBox.setOnClickListener(v -> onAvatarTap());
        avatarLetter = M3.text(act, "P", 28, M3.onPrimaryContainer, true);
        avatarLetter.setGravity(Gravity.CENTER);
        avatarBox.addView(avatarLetter, new FrameLayout.LayoutParams(-1, -1));
        avatarImg = new ImageView(act);
        avatarImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarImg.setVisibility(View.GONE);
        avatarBox.addView(avatarImg, new FrameLayout.LayoutParams(-1, -1));
        card.addView(avatarBox, new LinearLayout.LayoutParams(M3.dp(64), M3.dp(64)));

        // Nama dan bio. Ketuk kartu (di luar foto) untuk mengedit profil.
        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        nameText = M3.text(act, "Pinpic", 20, M3.onSurface, true);
        texts.addView(nameText);
        bioText = M3.text(act, "", 14, M3.onSurfaceVariant, false);
        bioText.setMaxLines(2);
        bioText.setEllipsize(TextUtils.TruncateAt.END);
        bioText.setVisibility(View.GONE);
        texts.addView(bioText, Ui.lp(-2, -2, 0, M3.dp(2), 0, 0));
        countText = M3.text(act, "Memuat…", 13, M3.onSurfaceVariant, false);
        texts.addView(countText, Ui.lp(-2, -2, 0, M3.dp(4), 0, 0));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = M3.dp(16);
        card.addView(texts, tl);

        col.addView(card, Ui.lp(-1, -2, pad, M3.dp(12), pad, M3.dp(16)));

        col.addView(M3.text(act, "Tersimpan", 14, M3.primary, true), Ui.lp(-2, -2, pad + M3.dp(8), 0, pad, 0));

        // Bilah pilih banyak: muncul saat mode pilih aktif.
        selectBar = new LinearLayout(act);
        selectBar.setOrientation(LinearLayout.HORIZONTAL);
        selectBar.setGravity(Gravity.CENTER_VERTICAL);
        selectBar.setVisibility(View.GONE);
        selectBar.addView(Ui.iconButton(act, com.pinpic.app.IconDrawable.CLOSE, 0, M3.onSurface, 40, 20,
                this::exitSelection), new LinearLayout.LayoutParams(M3.dp(40), M3.dp(40)));
        selectCount = M3.text(act, "0 dipilih", 15, M3.onSurface, true);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        cl.leftMargin = M3.dp(8);
        selectBar.addView(selectCount, cl);
        TextView del = M3.text(act, "Hapus", 14, M3.onPrimary, true);
        del.setPadding(M3.dp(18), M3.dp(10), M3.dp(18), M3.dp(10));
        del.setBackground(M3.ripple(M3.primary, 20));
        del.setOnClickListener(v -> deleteSelected());
        selectBar.addView(del);
        col.addView(selectBar, Ui.lp(-1, -2, pad, M3.dp(4), pad, M3.dp(4)));

        hintText = M3.text(act, "Ketuk untuk membuka, tahan untuk memilih banyak.", 12, M3.onSurfaceVariant, false);
        hintText.setVisibility(View.GONE);
        col.addView(hintText, Ui.lp(-2, -2, pad + M3.dp(8), M3.dp(2), pad, M3.dp(4)));

        emptyText = M3.text(act, "Belum ada gambar tersimpan.\nUnduh gambar dari beranda, lalu gambar itu muncul di sini.",
                14, M3.onSurfaceVariant, false);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setVisibility(View.GONE);
        col.addView(emptyText, Ui.lp(-1, -2, pad, M3.dp(24), pad, 0));

        // Galeri masonry: tiga kolom berdampingan. Setiap gambar masuk ke kolom yang paling pendek.
        board = new LinearLayout(act);
        board.setOrientation(LinearLayout.HORIZONTAL);
        board.setPadding(pad, 0, pad, M3.dp(120));
        board.setClipToPadding(false);
        for (int k = 0; k < COLS; k++) {
            LinearLayout c = new LinearLayout(act);
            c.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            if (k > 0) p.leftMargin = gap;
            board.addView(c, p);
            columns[k] = c;
        }
        // Begitu tata letak galeri berubah (kolom diisi), muat gambar yang sedang terlihat.
        board.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> loadVisible());

        gallery = new ScrollView(act);
        gallery.setVerticalScrollBarEnabled(false);
        gallery.setOverScrollMode(View.OVER_SCROLL_NEVER);
        gallery.addView(board, new FrameLayout.LayoutParams(-1, -2));
        gallery.setOnScrollChangeListener((v, x, y, ox, oy) -> loadVisible());

        swipe = new SimpleSwipeRefreshLayout(act);
        swipe.setTargetView(gallery);
        swipe.setProgressColor(M3.primary);
        swipe.setIndicatorBackgroundColor(M3.surfaceContainerHigh);
        swipe.addView(gallery, new FrameLayout.LayoutParams(-1, -1));
        swipe.setOnRefreshListener(this::refresh);
        col.addView(swipe, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
    }

    /** Muat ulang profil (foto, nama, bio) dan daftar gambar tersimpan. */
    public void refresh() {
        refreshAccount();
        new Thread(() -> {
            final List<Downloader.Saved> list = Downloader.listSaved(act);
            final float[] ratios = new float[list.size()];
            for (int i = 0; i < list.size(); i++) ratios[i] = ratioOf(list.get(i));
            act.runOnUiThread(() -> {
                items.clear();
                items.addAll(list);
                // Buang pilihan yang gambarnya sudah tidak ada.
                Set<String> keys = new HashSet<>();
                for (Downloader.Saved s : list) keys.add(s.uri.toString());
                selected.retainAll(keys);
                selectCount.setText(selected.size() + " dipilih");
                buildBoard(ratios);
                swipe.setRefreshing(false);
                countText.setText(list.isEmpty() ? "Belum ada gambar" : list.size() + " gambar tersimpan");
                emptyText.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                hintText.setVisibility(list.isEmpty() || selecting ? View.GONE : View.VISIBLE);
            });
        }).start();
    }

    /** Rasio tinggi/lebar gambar, dibaca dari header file (tanpa memuat bitmap penuh). Dipanggil di thread latar. */
    private float ratioOf(Downloader.Saved s) {
        String k = s.uri.toString();
        Float cached = ratioCache.get(k);
        if (cached != null) return cached;
        float r = 1f;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = act.getContentResolver().openInputStream(s.uri);
            if (in != null) {
                try {
                    BitmapFactory.decodeStream(in, null, o);
                } finally {
                    in.close();
                }
            }
            if (o.outWidth > 0 && o.outHeight > 0) r = o.outHeight / (float) o.outWidth;
        } catch (Exception ignored) {
            // Gagal membaca ukuran: pakai kotak.
        }
        r = Math.max(MIN_RATIO, Math.min(MAX_RATIO, r));
        ratioCache.put(k, r);
        return r;
    }

    /** Susun ulang galeri: setiap gambar ke kolom yang paling pendek, tinggi sesuai rasionya. */
    private void buildBoard(float[] ratios) {
        for (LinearLayout c : columns) c.removeAllViews();
        tiles.clear();
        int[] heights = new int[COLS];
        for (int i = 0; i < items.size(); i++) {
            int k = 0;
            for (int j = 1; j < COLS; j++) if (heights[j] < heights[k]) k = j;
            Downloader.Saved s = items.get(i);
            int h = Math.round(tileW * ratios[i]);
            Tile t = createTile(s);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(tileW, h);
            p.bottomMargin = gap;
            columns[k].addView(t.iv, p);
            heights[k] += h + gap;
            tiles.add(t);
        }
        loadVisible();
    }

    /** Muat bitmap hanya untuk tile yang sedang terlihat di layar. Yang sudah dimuat tidak diminta lagi. */
    private void loadVisible() {
        Rect r = new Rect();
        for (final Tile t : tiles) {
            if (t.loaded || !t.iv.getGlobalVisibleRect(r)) continue;
            t.loaded = true;
            ImageLoader.loadUri(act, t.s.uri, tileW, bmp -> {
                if (bmp != null) t.iv.setImageBitmap(bmp);
            });
        }
    }

    private void refreshAccount() {
        String name = Prefs.name();
        nameText.setText(name.isEmpty() ? "Pinpic" : name);
        avatarLetter.setText(name.isEmpty() ? "P" : name.substring(0, 1).toUpperCase());

        String bio = Prefs.bio();
        bioText.setText(bio);
        bioText.setVisibility(bio.isEmpty() ? View.GONE : View.VISIBLE);

        if (Account.hasAvatar(act)) {
            ImageLoader.loadUri(act, Account.avatarUri(act), M3.dp(64) * 2, bmp -> {
                if (bmp == null) return;
                avatarImg.setImageBitmap(bmp);
                avatarImg.setVisibility(View.VISIBLE);
                avatarLetter.setVisibility(View.GONE);
            });
        } else {
            avatarImg.setImageDrawable(null);
            avatarImg.setVisibility(View.GONE);
            avatarLetter.setVisibility(View.VISIBLE);
        }
    }

    // ------------------------------------------------------------ foto profil

    /** Ketuk foto: bila sudah ada, buka untuk dilihat. Bila belum, langsung pilih foto. */
    private void onAvatarTap() {
        if (!Account.hasAvatar(act)) {
            pickAvatar();
            return;
        }
        Intent i = new Intent(act, ImageViewerActivity.class);
        i.setData(Account.avatarUri(act));
        i.putExtra(ImageViewerActivity.EXTRA_AVATAR, true);
        act.startActivity(i);
    }

    /** Pilih foto dari galeri, lalu potong (AvatarCropActivity) dan simpan sebagai foto profil. */
    private void pickAvatar() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            act.startActivityForResult(Intent.createChooser(i, "Pilih foto profil"), REQ_PICK_AVATAR);
        } catch (Exception e) {
            M3.toast(act, "Tidak ada aplikasi galeri");
        }
    }

    /** Dipanggil dari MainActivity. @return true bila hasil ini milik profil. */
    public boolean onActivityResult(int req, int res, Intent data) {
        if (req == REQ_PICK_AVATAR) {
            if (res == Activity.RESULT_OK && data != null && data.getData() != null) {
                Intent c = new Intent(act, AvatarCropActivity.class);
                c.setData(data.getData());
                act.startActivityForResult(c, REQ_CROP_AVATAR);
            }
            return true;
        }
        if (req == REQ_CROP_AVATAR) {
            if (res == Activity.RESULT_OK) refreshAccount();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------ mode pilih banyak
    /** Tombol kembali: keluar dari mode pilih dulu. @return true bila tombol kembali sudah dipakai di sini. */
    public boolean handleBack() {
        if (!selecting) return false;
        exitSelection();
        return true;
    }

    public void exitSelection() {
        selecting = false;
        selected.clear();
        selectBar.setVisibility(View.GONE);
        hintText.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        refreshSelection();
    }

    private void enterSelection(Downloader.Saved s) {
        selecting = true;
        selectBar.setVisibility(View.VISIBLE);
        hintText.setVisibility(View.GONE);
        toggle(s);
    }

    private void toggle(Downloader.Saved s) {
        String k = s.uri.toString();
        if (!selected.remove(k)) selected.add(k);
        selectCount.setText(selected.size() + " dipilih");
        refreshSelection();
    }

    /** Tampilkan bingkai pilihan hanya pada tile yang dipilih. Tile tidak dibangun ulang. */
    private void refreshSelection() {
        for (Tile t : tiles) {
            t.iv.setForeground(selected.contains(t.s.uri.toString()) ? selectedOverlay() : null);
        }
    }

    private void deleteSelected() {
        List<Downloader.Saved> list = new ArrayList<>();
        for (Downloader.Saved s : items) if (selected.contains(s.uri.toString())) list.add(s);
        if (!list.isEmpty()) host.onDeleteMany(list);
    }

    private GradientDrawable selectedOverlay() {
        if (overlay == null) {
            overlay = new GradientDrawable();
            overlay.setCornerRadius(M3.dp(16));
            overlay.setColor(0x66000000);
            overlay.setStroke(M3.dp(3), M3.primary);
        }
        return overlay;
    }

    private void openViewer(Downloader.Saved s) {
        Intent i = new Intent(act, ImageViewerActivity.class);
        i.setData(s.uri);
        act.startActivity(i);
    }

    /** Tile dibuat dengan tinggi awal dari rasio (diatur di buildBoard). Gambarnya dimuat saat terlihat. */
    private Tile createTile(Downloader.Saved s) {
        ImageView iv = new ImageView(act);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(M3.surfaceContainerHigh);
        M3.round(iv, 16);
        iv.setForeground(selected.contains(s.uri.toString()) ? selectedOverlay() : null);
        Tile t = new Tile(s, iv);
        iv.setOnClickListener(v -> {
            if (selecting) toggle(s);
            else openViewer(s);
        });
        iv.setOnLongClickListener(v -> {
            if (selecting) toggle(s);
            else enterSelection(s);
            return true;
        });
        return t;
    }

    @Override public View getView() { return root; }

    @Override public void onShown() { refresh(); }

    @Override public void onHidden() { exitSelection(); }

    @Override public void setInsets(int top, int bottom, int left, int right) {
        col.setPadding(left, top, right, 0);
        board.setPadding(pad, 0, pad, bottom + M3.dp(120));   // ruang untuk navigasi melayang
    }
}
