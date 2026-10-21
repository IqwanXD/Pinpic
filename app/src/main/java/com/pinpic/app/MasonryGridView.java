package com.pinpic.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

/**
 * GridView staggered (ala Pinterest) buatan sendiri, tanpa RecyclerView.
 * Tile ditaruh ke kolom terpendek; bitmap hanya dimuat untuk tile di sekitar layar
 * dan dilepas saat jauh dari layar (cache di LruCache).
 */
public final class MasonryGridView extends ScrollView {
    /** Dipanggil saat posisi scroll berubah; atTop = true bila grid berada di paling atas. */
    public interface ScrollListener { void onScrolled(boolean atTop); }

    public interface Listener {
        void onClick(Pin pin, Bitmap preview);
        void onLongClick(Pin pin);
        void onEndReached();
    }

    private final LinearLayout row;
    private final LinearLayout content;
    private View header;
    private final List<Tile> tiles = new ArrayList<>();
    private LinearLayout[] cols = new LinearLayout[0];
    private int[] heights = new int[0];
    private int colCount = 0, tileW = 0, gen = 0, pending = 0;
    private final int gap;
    private Listener listener;
    private ScrollListener scrollListener;

    // Kotak kosong (placeholder) saat hasil belum datang
    private static final float[] PH_RATIOS = {1.2f, 1.5f, 1.0f, 1.35f, 1.6f, 1.1f, 1.4f, 1.25f};
    private static final int PH_COUNT = 8;
    private final List<View> placeholders = new ArrayList<>();
    private boolean wantPlaceholders;

    public MasonryGridView(Context c) {
        super(c);
        gap = M3.dp(10);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);
        row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(gap / 2, 0, gap / 2, 0);
        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        addView(content, new FrameLayout.LayoutParams(-1, -2));
    }

    /** Header di atas grid yang ikut ter-scroll (mis. pratinjau gambar di halaman detail). */
    public void setHeader(View v) {
        if (header != null) content.removeView(header);
        header = v;
        content.addView(header, 0, new LinearLayout.LayoutParams(-1, -2));
        header.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> refreshVisible());
    }

    private int headerH() {
        return header == null ? 0 : header.getHeight();
    }

    public void setListener(Listener l) { listener = l; }
    public void setScrollListener(ScrollListener l) { scrollListener = l; }
    public int getTileWidth() { return tileW; }
    public void refreshLayout() { relayout(); }

    public void clear() {
        gen++;
        pending = 0;
        for (Tile t : tiles) t.hide();
        tiles.clear();
        placeholders.clear();
        for (LinearLayout c : cols) c.removeAllViews();
        Arrays.fill(heights, 0);
        scrollTo(0, 0);
    }

    public void addPin(final Pin pin) {
        if (pin.ratio > 0) { addTile(pin, pin.ratio); return; }
        // rasio belum diketahui: muat thumbnail dulu, baru tile ditempatkan dengan tinggi asli
        final int g = gen;
        pending++;
        ImageLoader.load(pin.thumb, pin.full, Math.max(tileW, M3.dp(160)), Bitmap.Config.ARGB_8888, bmp -> {
            if (g != gen) return;
            pending--;
            if (bmp != null) addTile(pin, bmp.getHeight() / (float) bmp.getWidth());
            else if (pending == 0 && tiles.isEmpty()) hidePlaceholders();   // semua gagal: jangan kotak kosong selamanya
            post(() -> checkEnd());
        });
    }

    private void addTile(Pin pin, float ratio) {
        Tile t = new Tile(getContext(), pin, Math.max(0.6f, Math.min(2f, ratio)));
        tiles.add(t);
        if (tileW > 0 && cols.length > 0) {
            // Kotak kosong bertahan sampai ada gambar yang benar-benar bisa ditaruh di grid.
            if (wantPlaceholders) hidePlaceholders();   // hidePlaceholders juga menaruh ulang semua tile
            else place(t);
            refreshVisible();
        }
    }

    private void relayout() {
        int usable = getWidth() - getPaddingLeft() - getPaddingRight();
        if (usable <= 0) return;
        int n = Math.max(2, usable / M3.dp(190));
        int tw = (usable - gap) / n - gap;
        if (n == colCount && tw == tileW && cols.length == n) return;
        colCount = n;
        tileW = tw;
        row.removeAllViews();
        cols = new LinearLayout[n];
        heights = new int[n];
        placeholders.clear();
        for (int i = 0; i < n; i++) {
            LinearLayout col = new LinearLayout(getContext());
            col.setOrientation(LinearLayout.VERTICAL);
            row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            cols[i] = col;
        }
        for (Tile t : tiles) place(t);
        if (!tiles.isEmpty()) wantPlaceholders = false;   // sudah ada gambar: tidak perlu kotak kosong
        addPlaceholders();
        refreshVisible();
    }

    private void place(Tile t) {
        int best = 0;
        for (int i = 1; i < heights.length; i++) if (heights[i] < heights[best]) best = i;
        int h = Math.round(tileW * t.ratio);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, h);
        lp.setMargins(gap / 2, gap / 2, gap / 2, gap / 2);
        t.y = heights[best] + gap / 2;
        t.h = h;
        t.reqW = tileW;
        t.hide();
        if (t.getParent() instanceof ViewGroup) ((ViewGroup) t.getParent()).removeView(t);
        cols[best].addView(t, lp);
        heights[best] += h + gap;
    }

    private void refreshVisible() {
        int top = getScrollY() - getHeight();
        int bottom = getScrollY() + getHeight() * 2;
        int pt = getPaddingTop() + headerH();
        for (Tile t : tiles) {
            int y = pt + t.y;
            if (y + t.h > top && y < bottom) t.show(); else t.hide();
        }
    }

    /** Tampilkan kotak kosong sambil menunggu hasil pertama. */
    public void showPlaceholders() {
        wantPlaceholders = true;
        addPlaceholders();
    }

    /** Hapus kotak kosong, lalu susun ulang tile yang sudah ada. */
    public void hidePlaceholders() {
        if (!wantPlaceholders && placeholders.isEmpty()) return;
        wantPlaceholders = false;
        placeholders.clear();
        if (cols.length == 0) return;
        Arrays.fill(heights, 0);
        for (LinearLayout c : cols) c.removeAllViews();
        for (Tile t : tiles) place(t);
        refreshVisible();
    }

    private void addPlaceholders() {
        if (!wantPlaceholders || cols.length == 0 || tileW == 0 || !tiles.isEmpty()) return;
        for (int i = 0; i < PH_COUNT; i++) {
            int best = 0;
            for (int k = 1; k < heights.length; k++) if (heights[k] < heights[best]) best = k;
            int h = Math.round(tileW * PH_RATIOS[i % PH_RATIOS.length]);
            // Kilau shimmer lembut: menandakan sedang memuat, bukan layar kosong.
            View v = new ShimmerView(getContext(), 22);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, h);
            lp.setMargins(gap / 2, gap / 2, gap / 2, gap / 2);
            cols[best].addView(v, lp);
            placeholders.add(v);
            heights[best] += h + gap;
        }
    }

    public void checkEnd() {
        if (pending > 0 || listener == null || tileW == 0 || tiles.isEmpty()) return;
        int max = 0;
        for (int h : heights) max = Math.max(max, h);
        // Muat halaman berikutnya saat sisa konten di bawah layar kurang dari dua layar.
        if (getScrollY() + getHeight() * 3 >= getPaddingTop() + headerH() + max) listener.onEndReached();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        relayout();
    }

    @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
        super.onScrollChanged(l, t, ol, ot);
        refreshVisible();
        checkEnd();
        if (scrollListener != null) scrollListener.onScrolled(t <= 0);
    }

    // ---------------------------------------------------------------- Tile
    final class Tile extends ImageView {
        final Pin pin;
        final float ratio;
        int y, h, reqW;
        boolean shown;
        Future<?> job;
        /** Kilau shimmer selama gambar belum siap; berhenti begitu gambar tampil atau tile disembunyikan. */
        final ShimmerDrawable shimmer;

        Tile(Context c, final Pin p, float r) {
            super(c);
            pin = p;
            ratio = r;
            setScaleType(ScaleType.CENTER_CROP);
            shimmer = new ShimmerDrawable(M3.dp(22));
            setBackground(shimmer);
            M3.round(this, 22);
            setOnClickListener(v -> {
                if (listener != null) listener.onClick(p, ImageLoader.cached(p.thumb, reqW));
            });
            setOnLongClickListener(v -> {
                if (listener != null) listener.onLongClick(p);
                return true;
            });
            setOnTouchListener((v, e) -> {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        animate().scaleX(.95f).scaleY(.95f).setDuration(120).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        animate().scaleX(1f).scaleY(1f).setDuration(260)
                                .setInterpolator(new OvershootInterpolator(2f)).start();
                        break;
                }
                return false;
            });
        }

        void show() {
            if (shown) return;
            shown = true;
            Bitmap b = ImageLoader.cached(pin.thumb, reqW);
            if (b != null) { shimmer.stop(); setImageBitmap(b); return; }
            shimmer.start();
            final int g = gen;
            job = ImageLoader.load(pin.thumb, pin.full, reqW, Bitmap.Config.ARGB_8888, bmp -> {
                if (!shown || g != gen) return;
                shimmer.stop();
                if (bmp == null) return;
                setImageBitmap(bmp);
                setAlpha(0f);
                animate().alpha(1f).setDuration(200).start();
            });
        }

        void hide() {
            if (!shown) return;
            shown = false;
            if (job != null) job.cancel(false);
            job = null;
            shimmer.stop();
            animate().cancel();
            setAlpha(1f);
            setScaleX(1f);
            setScaleY(1f);
            setImageDrawable(null);
        }
    }
}
