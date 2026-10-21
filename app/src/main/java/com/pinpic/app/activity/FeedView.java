package com.pinpic.app.activity;

import java.util.ArrayList;
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
 * - Beranda (query kosong): gambar acak yang tidak pernah habis. Halaman kosong dicoba lagi.
 * - Cari dengan kata kunci: berhenti setelah dua halaman kosong berturut-turut.
 * - Mode detail: tanpa swipe refresh; grid bisa diberi header (pratinjau) yang ikut ter-scroll.
 * Indikator memuat di bawah berada di luar SwipeRefresh, sehingga tidak bisa ditarik.
 */
public final class FeedView extends FrameLayout {

    /** Halaman kosong beruntun di beranda sebelum berhenti sementara dan menampilkan pesan. */
    private static final int FEED_RETRIES = 6;
    private static final long RETRY_DELAY_MS = 400;

    private final ScreenHost host;
    private final boolean detail;
    private Scraper scraper = new Scraper();
    /** Pin yang sudah tampil; dipakai agar tidak dimuat ulang saat tema berubah. */
    private final List<Pin> shown = new ArrayList<>();
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
        this(act, host, detail, null);
    }

    /** prev != null: lanjutkan dari layar lama (item, scraper, scroll) tanpa memuat ulang. */
    public FeedView(Activity act, final ScreenHost host, boolean detail, FeedView prev) {
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

        if (prev != null && !detail) {
            scraper = prev.scraper;          // gambar yang sudah pernah tampil tetap diingat
            query = prev.query;
            page = prev.page;
            searchGen = prev.searchGen;
            empties = prev.empties;
            exhausted = prev.exhausted;
            any = prev.any;
            started = prev.started;
            loading = false;                 // permintaan yang sedang jalan di layar lama tidak sampai ke sini
            final List<Pin> keep = new ArrayList<>(prev.shown);
            final int y = prev.scrollY();
            shown.addAll(keep);
            grid.post(() -> {
                for (Pin pin : keep) grid.addPin(pin);
                // Belum ada gambar dan masih memuat (mis. tema diganti saat loading): tampilkan lagi kotak shimmer.
                if (keep.isEmpty() && started && !exhausted) {
                    grid.showPlaceholders();
                    loadMore();
                }
                grid.post(() -> grid.scrollTo(0, y));
            });
        }
    }

    /** Posisi scroll grid, dipakai saat layar dibangun ulang. */
    public int scrollY() {
        return grid.getScrollY();
    }

    /** Muat halaman pertama sekali, saat layar pertama kali tampil. */
    public void start() {
        if (started) return;
        started = true;
        grid.showPlaceholders();
        loadMore();
    }

    /** Ganti kata kunci dan muat hasil dari awal. Kosong = gambar acak beranda. */
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
        shown.clear();
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
            empties++;
            if (query.isEmpty()) {
                // Beranda tidak pernah dianggap habis: coba lagi dengan topik dan halaman acak lain.
                if (empties < FEED_RETRIES) {
                    retryLater(g);
                    return;
                }
                empties = 0;
                setLoader(false);
                if (!any) grid.hidePlaceholders();
                host.onMessage(Prefs.sourcesOn() == 0
                        ? "Semua sumber dimatikan. Aktifkan di Pengaturan."
                        : "Belum ada gambar baru. Gulir ke bawah untuk mencoba lagi.");
                return;
            }
            if (empties >= 2) {
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
        // Kotak shimmer dibiarkan sampai gambar pertama benar-benar tampil (lihat MasonryGridView.addTile).
        shown.addAll(r);
        for (Pin pin : r) grid.addPin(pin);
        grid.post(() -> grid.checkEnd());
    }

    /** Halaman beranda kosong: tunggu sebentar, lalu muat lagi. */
    private void retryLater(final int g) {
        postDelayed(() -> {
            if (g == searchGen) loadMore();
        }, RETRY_DELAY_MS);
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
