package com.pinpic.app.activity;

import android.app.Activity;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import com.pinpic.app.InoLoadingIndicator;
import com.pinpic.app.M3;
import com.pinpic.app.MasonryGridView;
import com.pinpic.app.Pin;
import com.pinpic.app.Prefs;
import com.pinpic.app.Scraper;
import com.pinpic.app.SimpleSwipeRefreshLayout;

import java.util.List;

/**
 * Grid hasil gambar dengan muat halaman berikutnya.
 * - Mode biasa (Beranda/Cari): tarik ke bawah dari atas adalah satu-satunya cara refresh.
 * - Mode detail: tanpa swipe refresh; grid bisa diberi header (pratinjau) yang ikut ter-scroll.
 * Indikator memuat di bawah berada di luar SwipeRefresh, sehingga tidak bisa ditarik.
 */
public final class FeedView extends FrameLayout {

    private final ScreenHost host;
    private final boolean detail;
    private final Scraper scraper = new Scraper();
    private final MasonryGridView grid;
    private final SimpleSwipeRefreshLayout swipe; // null pada mode detail
    private final FrameLayout loader;

    private String query = "";
    private int page = 0, searchGen = 0, empties = 0;
    private boolean loading, exhausted, any, refreshing, started, loaderOn;

    public FeedView(Activity act, ScreenHost host) {
        this(act, host, false);
    }

    public FeedView(Activity act, final ScreenHost host, boolean detail) {
        super(act);
        this.host = host;
        this.detail = detail;

        grid = new MasonryGridView(act);
        grid.setListener(new MasonryGridView.Listener() {
            @Override public void onClick(Pin pin, Bitmap preview) { host.onOpenDetail(pin, preview, query); }
            @Override public void onLongClick(Pin pin) { host.onOpenOptions(pin, grid.getTileWidth()); }
            @Override public void onEndReached() { loadMore(); }
        });

        if (detail) {
            swipe = null;
            addView(grid, new FrameLayout.LayoutParams(-1, -1));
        } else {
            swipe = new SimpleSwipeRefreshLayout(act);
            swipe.setTargetView(grid);
            swipe.setProgressColor(M3.primary);
            swipe.setIndicatorBackgroundColor(M3.surfaceContainerHigh);
            swipe.addView(grid, new FrameLayout.LayoutParams(-1, -1));
            swipe.setOnRefreshListener(() -> {
                refreshing = true;
                doSearch(query);
            });
            addView(swipe, new FrameLayout.LayoutParams(-1, -1));
        }

        // Loader bawah: bukan bagian dari SwipeRefresh. clickable = menelan sentuhan,
        // sehingga menyentuh loader tidak memulai tarik-untuk-muat-ulang.
        loader = new FrameLayout(act);
        loader.setBackground(M3.shape(M3.surfaceContainerHigh, 24));
        loader.setElevation(M3.dp(6));
        loader.setClickable(true);
        loader.setScaleX(0f);
        loader.setScaleY(0f);
        loader.setVisibility(GONE);
        InoLoadingIndicator indicator = new InoLoadingIndicator(act);
        indicator.setColor(M3.primary);
        loader.addView(indicator, new FrameLayout.LayoutParams(M3.dp(28), M3.dp(28), Gravity.CENTER));
        addView(loader, new FrameLayout.LayoutParams(M3.dp(48), M3.dp(48),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
    }

    /** Muat halaman pertama sekali, saat layar pertama kali tampil. */
    public void start() {
        if (started) return;
        started = true;
        grid.showPlaceholders();
        loadMore();
    }

    /** Ganti kata kunci dan muat hasil dari awal. */
    public void search(String q) {
        doSearch(q);
    }

    /** Header di atas grid yang ikut ter-scroll (mode detail). */
    public void setHeader(View v) {
        grid.setHeader(v);
    }

    public void scrollToTop() {
        grid.smoothScrollTo(0, 0);
    }

    public void setScrollListener(MasonryGridView.ScrollListener l) {
        grid.setScrollListener(l);
    }

    /** Padding grid mengikuti inset sistem dan ruang untuk navigasi melayang (atau tidak, pada detail). */
    public void setInsets(int left, int right, int bottom) {
        int nav = detail ? M3.dp(24) : M3.dp(104);
        grid.setPadding(left, M3.dp(4), right, bottom + nav);
        grid.refreshLayout();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) loader.getLayoutParams();
        lp.bottomMargin = bottom + (detail ? M3.dp(24) : M3.dp(96));
        loader.setLayoutParams(lp);
    }

    private void doSearch(String q) {
        query = q;
        page = 0;
        empties = 0;
        searchGen++;
        exhausted = false;
        any = false;
        loading = false;
        scraper.reset();
        grid.clear();
        if (!refreshing) grid.showPlaceholders();
        loadMore();
    }

    private void loadMore() {
        if (loading || exhausted) return;
        loading = true;
        if (!refreshing) setLoader(true);
        final int g = searchGen, p = page++;
        final String q = query;
        new Thread(() -> {
            final List<Pin> r = scraper.fetch(q, p);
            post(() -> onPage(g, r));
        }).start();
    }

    private void onPage(int g, List<Pin> r) {
        if (g != searchGen) return;
        if (refreshing) {
            refreshing = false;
            if (swipe != null) swipe.setRefreshing(false);
        }
        loading = false;
        if (r.isEmpty()) {
            if (++empties >= 2) {
                exhausted = true;
                setLoader(false);
                if (!any) {
                    grid.hidePlaceholders();
                    host.onMessage(Prefs.sourcesOn() == 0
                            ? "Semua sumber dimatikan. Aktifkan di Pengaturan."
                            : "Tidak ada gambar. Periksa koneksi internet.");
                }
            } else {
                loadMore();
            }
            return;
        }
        empties = 0;
        any = true;
        setLoader(false);
        grid.hidePlaceholders();
        for (Pin pin : r) grid.addPin(pin);
        grid.post(() -> grid.checkEnd());
    }

    /** Tampilkan / sembunyikan loader bawah dengan animasi scale. */
    private void setLoader(boolean on) {
        if (on == loaderOn) return;
        loaderOn = on;
        loader.animate().cancel();
        if (on) {
            loader.setVisibility(VISIBLE);
            loader.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
        } else {
            loader.animate().scaleX(0f).scaleY(0f).setDuration(200)
                    .withEndAction(() -> {
                        if (!loaderOn) loader.setVisibility(GONE);
                    }).start();
        }
    }
}
