package com.pinpic.app.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pinpic.app.Account;
import com.pinpic.app.Downloader;
import com.pinpic.app.ImageLoader;
import com.pinpic.app.M3;
import com.pinpic.app.Prefs;
import com.pinpic.app.SimpleSwipeRefreshLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Saya: profil (foto, nama, bio) + galeri gambar yang sudah diunduh.
 * Ketuk gambar untuk membukanya. Tahan gambar untuk masuk mode pilih banyak, lalu hapus sekaligus.
 */
public final class ProfileScreen implements Screen {

    private static final int COLS = 3;

    private final Activity act;
    private final ScreenHost host;
    private final FrameLayout root;
    private final LinearLayout col;
    private final GridView gridView;
    private final SimpleSwipeRefreshLayout swipe;
    private final TextView countText, emptyText, hintText, nameText, bioText, avatarLetter, selectCount;
    private final LinearLayout selectBar;
    private final ImageView avatarImg;
    private final List<Downloader.Saved> items = new ArrayList<>();
    /** Kunci pilihan = uri gambar. */
    private final Set<String> selected = new HashSet<>();
    private boolean selecting;
    private final Adapter adapter = new Adapter();
    private final int pad, tileW;

    public ProfileScreen(Activity act, ScreenHost host) {
        this.act = act;
        this.host = host;
        pad = M3.dp(16);
        tileW = (act.getResources().getDisplayMetrics().widthPixels - pad * 2 - M3.dp(4) * (COLS - 1)) / COLS;

        root = new FrameLayout(act);
        root.setBackgroundColor(M3.background);

        col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView title = M3.text(act, "Saya", 34, M3.primary, true);
        title.setLetterSpacing(-0.02f);
        col.addView(title, Ui.lp(-2, -2, pad, M3.dp(8), pad, 0));

        // Kartu profil: ketuk untuk mengedit foto, nama, dan bio.
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(M3.ripple(M3.surfaceContainerHigh, 28));
        card.setPadding(M3.dp(16), M3.dp(16), M3.dp(16), M3.dp(16));
        card.setClickable(true);
        card.setOnClickListener(v -> act.startActivity(new Intent(act, EditProfileActivity.class)));

        FrameLayout avatarBox = new FrameLayout(act);
        avatarBox.setBackground(M3.shape(M3.primaryContainer, 32));
        M3.round(avatarBox, 32);
        avatarLetter = M3.text(act, "P", 28, M3.onPrimaryContainer, true);
        avatarLetter.setGravity(Gravity.CENTER);
        avatarBox.addView(avatarLetter, new FrameLayout.LayoutParams(-1, -1));
        avatarImg = new ImageView(act);
        avatarImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarImg.setVisibility(View.GONE);
        avatarBox.addView(avatarImg, new FrameLayout.LayoutParams(-1, -1));
        card.addView(avatarBox, new LinearLayout.LayoutParams(M3.dp(64), M3.dp(64)));

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

        TextView edit = M3.text(act, "Edit", 14, M3.primary, true);
        edit.setPadding(M3.dp(8), 0, 0, 0);
        card.addView(edit);
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

        gridView = new GridView(act);
        gridView.setNumColumns(COLS);
        gridView.setColumnWidth(tileW);
        gridView.setStretchMode(GridView.NO_STRETCH);
        gridView.setHorizontalSpacing(M3.dp(4));
        gridView.setVerticalSpacing(M3.dp(4));
        gridView.setPadding(pad, 0, pad, M3.dp(120));
        gridView.setClipToPadding(false);
        gridView.setVerticalScrollBarEnabled(false);
        gridView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        gridView.setAdapter(adapter);

        swipe = new SimpleSwipeRefreshLayout(act);
        swipe.setTargetView(gridView);
        swipe.setProgressColor(M3.primary);
        swipe.setIndicatorBackgroundColor(M3.surfaceContainerHigh);
        swipe.addView(gridView, new FrameLayout.LayoutParams(-1, -1));
        swipe.setOnRefreshListener(this::refresh);
        col.addView(swipe, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(col, new FrameLayout.LayoutParams(-1, -1));
    }

    /** Muat ulang profil (foto, nama, bio) dan daftar gambar tersimpan. */
    public void refresh() {
        refreshAccount();
        new Thread(() -> {
            final List<Downloader.Saved> list = Downloader.listSaved(act);
            act.runOnUiThread(() -> {
                items.clear();
                items.addAll(list);
                // Buang pilihan yang gambarnya sudah tidak ada.
                Set<String> keys = new HashSet<>();
                for (Downloader.Saved s : list) keys.add(s.uri.toString());
                selected.retainAll(keys);
                selectCount.setText(selected.size() + " dipilih");
                adapter.notifyDataSetChanged();
                swipe.setRefreshing(false);
                countText.setText(list.isEmpty() ? "Belum ada gambar" : list.size() + " gambar tersimpan");
                emptyText.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                hintText.setVisibility(list.isEmpty() || selecting ? View.GONE : View.VISIBLE);
            });
        }).start();
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
        adapter.notifyDataSetChanged();
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
        adapter.notifyDataSetChanged();
    }

    private void deleteSelected() {
        List<Downloader.Saved> list = new ArrayList<>();
        for (Downloader.Saved s : items) if (selected.contains(s.uri.toString())) list.add(s);
        if (!list.isEmpty()) host.onDeleteMany(list);
    }

    private GradientDrawable selectedOverlay() {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(M3.dp(16));
        g.setColor(0x66000000);
        g.setStroke(M3.dp(3), M3.primary);
        return g;
    }

    private void openViewer(Downloader.Saved s) {
        Intent i = new Intent(act, ImageViewerActivity.class);
        i.setData(s.uri);
        act.startActivity(i);
    }

    private ImageView createTile() {
        ImageView iv = new ImageView(act);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(M3.surfaceContainerHigh);
        M3.round(iv, 16);
        iv.setLayoutParams(new AbsListView.LayoutParams(tileW, tileW));
        return iv;
    }

    @Override public View getView() { return root; }

    @Override public void onShown() { refresh(); }

    @Override public void onHidden() { exitSelection(); }

    @Override public void setInsets(int top, int bottom, int left, int right) {
        col.setPadding(left, top, right, 0);
        gridView.setPadding(pad, 0, pad, bottom + M3.dp(120));   // ruang untuk navigasi melayang
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int pos) { return items.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override public View getView(int pos, View convert, ViewGroup parent) {
            final Downloader.Saved s = items.get(pos);
            final ImageView iv = convert instanceof ImageView ? (ImageView) convert : createTile();
            iv.setImageDrawable(null);
            iv.setTag(s);
            iv.setForeground(selected.contains(s.uri.toString()) ? selectedOverlay() : null);
            ImageLoader.loadUri(act, s.uri, tileW, bmp -> {
                if (bmp != null && iv.getTag() == s) iv.setImageBitmap(bmp);
            });
            iv.setOnClickListener(v -> {
                if (selecting) toggle(s);
                else openViewer(s);
            });
            iv.setOnLongClickListener(v -> {
                if (selecting) toggle(s);
                else enterSelection(s);
                return true;
            });
            return iv;
        }
    }
}
