package com.pinpic.app;

import android.os.Environment;
import com.pinpic.app.activity.FeedView;
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.http.HttpResponseCache;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pinpic.app.activity.DetailView;
import com.pinpic.app.activity.HomeScreen;
import com.pinpic.app.activity.ProfileScreen;
import com.pinpic.app.activity.Screen;
import com.pinpic.app.activity.ScreenHost;
import com.pinpic.app.activity.SearchScreen;
import com.pinpic.app.activity.SettingsActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Satu-satunya activity. Beranda, Cari, dan Saya adalah layar terpisah (com.pinpic.app.activity)
 * yang ditampung satu FrameLayout dan dipindah dengan animasi geser + fade.
 */
public class MainActivity extends Activity {
    private static final int REQ_STORAGE = 7;

    // Tautan terkait (sama dengan JetEditor). Ganti di sini bila perlu.
    private static final String URL_DISCORD = "https://discord.gg/u7QPpQD7QC";
    private static final String URL_GITHUB = "https://github.com/IqwanXD/";
    private static final String URL_TELEGRAM = "https://t.me/IqwanoINO/";

    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);

    // ----- view -----
    private PushDrawerLayout drawerLayout;
    private LinearLayout drawer;
    private FrameLayout root, container;
    /** Tumpukan pratinjau: yang paling atas yang terlihat. Ketuk gambar serupa = menumpuk, back = kembali. */
    private final ArrayList<DetailPage> detailStack = new ArrayList<>();
    private static final int MAX_DETAIL_STACK = 12;
    private TextView snack;
    private NavBar nav;
    private BottomSheet sheet;
    private Pin pendingPin;
    /** Baru saja menerima jawaban dialog izin; pengecekan izin saat onResume berikutnya dilewati. */
    private boolean permissionAnswered;

    private HomeScreen home;
    private SearchScreen searchScreen;
    private ProfileScreen profile;
    private Screen[] screens;
    private int current = NavBar.HOME;

    // ----- state -----
    private int insT, insB, insL, insR;
    private boolean chromeHidden;

    private final ScreenHost host = new ScreenHost() {
        @Override public void onOpenDetail(Pin pin, Bitmap preview, String query) { showDetail(pin, preview, query); }
        @Override public void onSave(Pin pin) { download(pin); }
        @Override public void onShare(Pin pin) { share(pin); }
        @Override public void onOpenOptions(Pin pin, int tileWidth) { showOptions(pin, tileWidth); }
        @Override public void onDeleteSaved(Downloader.Saved s) { askDelete(s); }
        @Override public void onDeleteMany(List<Downloader.Saved> list) { askDeleteMany(list); }
        @Override public void onMessage(String msg) { snack(msg); }
    };

    // ------------------------------------------------------------ lifecycle
    /** Tema yang dipakai saat layar ini dibangun. Berbeda dari M3.paletteKey = tema diubah di Pengaturan. */
    private String shownPalette = "";
    /** Feed beranda lama yang dibawa ke layar baru saat tema berubah (agar item tidak dimuat ulang). */
    private FeedView keepFeed;
    /** Layar Cari lama yang teks dan hasilnya dibawa ke layar baru saat tema berubah. */
    private SearchScreen keepSearch;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        M3.init(this);
        shownPalette = M3.paletteKey;
        try { HttpResponseCache.install(new File(getCacheDir(), "http"), 60L << 20); } catch (Exception ignored) { }
        buildUi();
        setupEdgeToEdge();
        drawerLayout.requestApplyInsets();
        screens[current].onShown();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (profile != null) profile.onActivityResult(req, res, data);
    }

    @Override protected void onResume() {
        super.onResume();
        // Tema atau warna diubah di Pengaturan: bangun ulang layar ini dengan palet baru.
        if (!M3.paletteKey.equals(shownPalette)) rebuildTheme();
        // Kembali dari Pengaturan Android atau dialog izin: lanjutkan unduhan yang tadi tertunda.
        if (pendingPin != null && canSave()) {
            Pin p = pendingPin;
            pendingPin = null;
            download(p);
        }
        // Kembali dari pengaturan/pratinjau: pastikan galeri Saya sesuai isi folder terbaru.
        if (profile != null && current == NavBar.ME) profile.refresh();
        askStorageIfNeeded();
    }

    /**
     * Tema/warna berubah di Pengaturan: bangun ulang UI di tempat (tanpa recreate activity),
     * pertahankan tab yang sedang dibuka, lalu pudarkan snapshot tampilan lama di atasnya.
     * Dibanding recreate() tidak ada kedipan layar kosong, dan tab aktif tidak kembali ke Beranda.
     */
    private void rebuildTheme() {
        Bitmap snap = null;
        if (drawerLayout != null && drawerLayout.getWidth() > 0 && drawerLayout.getHeight() > 0) {
            try {
                snap = Bitmap.createBitmap(drawerLayout.getWidth(), drawerLayout.getHeight(), Bitmap.Config.ARGB_8888);
                drawerLayout.draw(new Canvas(snap));
            } catch (OutOfMemoryError e) {
                snap = null;
            }
        }

        keepFeed = home != null ? home.feed() : null;
        keepSearch = searchScreen;
        if (searchScreen != null) searchScreen.onHidden();   // hentikan rotasi hint lama
        // Lepas UI lama: layar yang sedang tampil dihentikan, pratinjau dihancurkan.
        if (screens != null) screens[current].onHidden();
        for (DetailPage p : detailStack) p.view.destroy();
        detailStack.clear();
        sheet = null;
        snack = null;
        chromeHidden = false;

        M3.init(this);
        shownPalette = M3.paletteKey;
        buildUi();
        nav.select(current);
        setupEdgeToEdge();
        drawerLayout.requestApplyInsets();
        screens[current].onShown();

        if (snap != null) {
            final Bitmap bmp = snap;
            final ImageView cover = new ImageView(this);
            cover.setImageBitmap(bmp);
            cover.setScaleType(ImageView.ScaleType.FIT_XY);
            root.addView(cover, new FrameLayout.LayoutParams(-1, -1));
            cover.animate().alpha(0f).setDuration(220)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .withEndAction(() -> {
                        root.removeView(cover);
                        cover.setImageDrawable(null);
                        bmp.recycle();
                    }).start();
        }
    }

    @Override public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isOpen()) drawerLayout.close();
        else if (sheet != null && sheet.isOpen()) sheet.dismiss();
        else if (current == NavBar.ME && profile != null && profile.handleBack()) { /* keluar mode pilih */ }
        else if (!detailStack.isEmpty()) closeDetail();
        else if (current != NavBar.HOME) onTab(NavBar.HOME);
        else super.onBackPressed();
    }

    // ------------------------------------------------------------ edge-to-edge
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
        // Ikon bar status dan navigasi: terang di tema gelap, gelap di tema terang.
        M3.styleBars(w);
    }

    @SuppressWarnings("deprecation")
    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(M3.background);
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                insT = i.top; insB = i.bottom; insL = i.left; insR = i.right;
            } else {
                insT = ins.getSystemWindowInsetTop(); insB = ins.getSystemWindowInsetBottom();
                insL = ins.getSystemWindowInsetLeft(); insR = ins.getSystemWindowInsetRight();
            }
            applyInsets();
            return ins;
        });

        buildDrawer();

        // Tiga layar hidup berdampingan di satu FrameLayout; hanya satu yang terlihat.
        home = new HomeScreen(this, host, () -> drawerLayout.toggle(), keepFeed);
        keepFeed = null;
        searchScreen = new SearchScreen(this, host, keepSearch);
        keepSearch = null;
        profile = new ProfileScreen(this, host);
        screens = new Screen[]{home, searchScreen, profile};

        container = new FrameLayout(this);
        for (int k = 0; k < screens.length; k++) {
            View v = screens[k].getView();
            v.setVisibility(k == current ? View.VISIBLE : View.GONE);
            container.addView(v, new FrameLayout.LayoutParams(-1, -1));
        }
        root.addView(container, new FrameLayout.LayoutParams(-1, -1));

        nav = new NavBar(this, this::onTab, NavBar.HOME);
        root.addView(nav, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        // Drawer PUSH: konten terdorong ke kanan, drawer di belakangnya.
        int drawerW = (int) (getResources().getDisplayMetrics().widthPixels * 0.84f);
        drawerLayout = new PushDrawerLayout(this, drawerW);
        drawerLayout.setViews(root, drawer);
        drawerLayout.setListener(this::hideKeyboard);
        setContentView(drawerLayout);
        FontManager.attach(drawerLayout);
        applyInsets();
    }

    // ------------------------------------------------------------ drawer
    private void buildDrawer() {
        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        // Sama dengan latar utama supaya drawer dan konten menyatu saat didorong.
        drawer.setBackgroundColor(M3.background);
        drawer.setClickable(true);

        TextView title = M3.text(this, "Pinpic", 34, M3.primary, true);
        title.setLetterSpacing(-0.02f);
        drawer.addView(title, lp(-2, M3.dp(56), 0, 0, 0, 0));
        drawer.addView(M3.text(this, "Cari gambar dari mana saja", 14, M3.onSurfaceVariant, false),
                lp(-2, -2, 0, 0, 0, M3.dp(20)));

        drawer.addView(drawerItem(R.drawable.ic_info, "Informasi", () -> showInformation()));
        drawer.addView(drawerItem(R.drawable.ic_settings, "Pengaturan", () ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class))));

        View div = new View(this);
        div.setBackgroundColor(M3.alpha(M3.onSurface, 0.2f));
        drawer.addView(div, lp(-1, M3.dp(1), 0, M3.dp(16), 0, M3.dp(16)));

        TextView related = M3.text(this, "Tautan terkait", 14, M3.onSurfaceVariant, true);
        related.setPadding(0, 0, 0, M3.dp(8));
        drawer.addView(related);
        drawer.addView(drawerItem(R.drawable.ic_discord, "Discord", () -> openUrl(URL_DISCORD)));
        drawer.addView(drawerItem(R.drawable.ic_github, "GitHub", () -> openUrl(URL_GITHUB)));
        drawer.addView(drawerItem(R.drawable.ic_telegram, "Telegram", () -> openUrl(URL_TELEGRAM)));
    }

    private View drawerItem(int iconRes, String text, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, M3.dp(14), 0, M3.dp(14));
        row.setClickable(true);
        row.setBackground(M3.ripple(0, 12));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(VecIcon.of(this, iconRes, M3.onSurfaceVariant, M3.dp(26)));
        row.addView(icon, new LinearLayout.LayoutParams(M3.dp(26), M3.dp(26)));

        TextView t = M3.text(this, text, 16, M3.onSurface, false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = M3.dp(20);
        row.addView(t, tl);

        row.setOnClickListener(v -> {
            drawerLayout.close();
            action.run();
        });
        return row;
    }

    private void showInformation() {
        String ver = "1.0";
        int code = 1;
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            ver = pi.versionName;
            code = pi.versionCode;
        } catch (Exception ignored) { }

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = new ImageView(this);
        ic.setBackground(M3.shape(M3.primaryContainer, 16));
        ic.setScaleType(ImageView.ScaleType.CENTER);
        ic.setImageDrawable(VecIcon.of(this, R.drawable.ic_info, M3.onPrimaryContainer, M3.dp(28)));
        head.addView(ic, new LinearLayout.LayoutParams(M3.dp(52), M3.dp(52)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(M3.text(this, "Pinpic", 22, M3.onSurface, true));
        titles.addView(M3.text(this, "Informasi", 14, M3.onSurfaceVariant, false));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, -2, 1f);
        hl.leftMargin = M3.dp(16);
        head.addView(titles, hl);
        col.addView(head, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(M3.shape(M3.background, 20));
        card.setPadding(M3.dp(20), M3.dp(8), M3.dp(20), M3.dp(8));
        card.addView(infoRow("Versi", ver + " (" + code + ")", true));
        card.addView(infoRow("Package", getPackageName(), true));
        card.addView(infoRow("Folder unduhan", Downloader.locationLabel(), true));
        card.addView(infoRow("Sumber aktif", Prefs.sourcesOn() + " dari " + Sources.ALL.length, false));
        col.addView(card, lp(-1, -2, 0, M3.dp(20), 0, 0));

        col.addView(button("Tutup", M3.primary, M3.onPrimary, this::closeSheet), lp(-1, M3.dp(52), 0, M3.dp(20), 0, 0));
        openSheet(col);
    }

    private View infoRow(String label, String value, boolean divider) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, M3.dp(12), 0, M3.dp(12));
        row.addView(M3.text(this, label, 12, M3.onSurfaceVariant, false));
        TextView v = M3.text(this, value, 16, M3.onSurface, false);
        v.setTextIsSelectable(true);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-2, -2);
        vl.topMargin = M3.dp(2);
        row.addView(v, vl);
        if (!divider) return row;
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row);
        View line = new View(this);
        line.setBackgroundColor(M3.alpha(M3.onSurface, 0.13f));
        wrap.addView(line, new LinearLayout.LayoutParams(-1, 1));
        return wrap;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            snack("Tidak bisa membuka tautan");
        }
    }

    // ------------------------------------------------------------ navigasi antar layar
    /** Tab navigasi: 0 Beranda, 1 Cari, 2 Saya. */
    private void onTab(int i) {
        // Tab ditekan saat pratinjau terbuka: tutup semua pratinjau dulu agar langsung kembali ke layar tab.
        closeAllDetails();
        if (i == current) {
            if (i == NavBar.HOME) home.scrollToTop();
            return;
        }
        switchTo(i);
    }

    /**
     * Pindah layar dengan animasi: layar baru masuk dari arah tab yang dituju (fade + geser),
     * layar lama memudar ke arah berlawanan.
     */
    private void switchTo(int target) {
        final int from = current;
        if (target == from) return;
        current = target;
        nav.select(target);

        final View out = screens[from].getView();
        final View in = screens[target].getView();

        // Layar lain yang masih dalam animasi langsung disembunyikan agar tidak tumpang tindih.
        for (int k = 0; k < screens.length; k++) {
            if (k == from || k == target) continue;
            View other = screens[k].getView();
            other.animate().cancel();
            other.setVisibility(View.GONE);
        }
        screens[from].onHidden();
        screens[target].onShown();

        final float dx = M3.dp(28) * (target > from ? 1 : -1);
        in.animate().cancel();
        out.animate().cancel();
        in.setVisibility(View.VISIBLE);
        in.setAlpha(0f);
        in.setTranslationX(dx);
        in.bringToFront();
        in.animate().alpha(1f).translationX(0f).setDuration(320).setInterpolator(EMPHASIZED).start();
        out.animate().alpha(0f).translationX(-dx * 0.6f).setDuration(220).setInterpolator(EMPHASIZED)
                .withEndAction(() -> {
                    out.setVisibility(View.GONE);
                    out.setAlpha(1f);
                    out.setTranslationX(0f);
                }).start();
        updateChrome();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && root != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
    }

    private void applyInsets() {
        if (home == null) return;
        for (Screen s : screens) s.setInsets(insT, insB, insL, insR);
        bottomMargin(nav, insB + M3.dp(16));
        drawer.setPadding(M3.dp(24) + insL, insT + M3.dp(20), M3.dp(24) + insR, insB + M3.dp(20));
        applyDetailInsets();
    }

    // ------------------------------------------------------------ detail (pratinjau gambar)
    /** Satu halaman pratinjau di tumpukan: layer berisi DetailView dan tombol kembali. */
    private static final class DetailPage {
        final FrameLayout layer;
        final DetailView view;
        final View back;
        /** Posisi scroll pratinjau ini; navigasi hanya muncul saat sudah tidak di paling atas. */
        boolean atTop = true;

        DetailPage(FrameLayout layer, DetailView view, View back) {
            this.layer = layer;
            this.view = view;
            this.back = back;
        }
    }

    private DetailPage topDetail() {
        return detailStack.get(detailStack.size() - 1);
    }

    /**
     * Buka pratinjau. Kalau sudah ada pratinjau terbuka (mis. ketuk gambar serupa), pratinjau itu
     * tetap ada di bawah dan sedikit mundur. Back kembali ke foto sebelumnya dengan perlahan,
     * dan baru setelah semuanya ditutup kembali ke layar Cari/Beranda.
     */
    private void showDetail(final Pin pin, Bitmap preview, String query) {
        final FrameLayout layer = new FrameLayout(this);
        layer.setBackgroundColor(M3.background);
        layer.setClickable(true);

        final DetailView dv = new DetailView(this, host, pin, preview, query);
        layer.addView(dv, new FrameLayout.LayoutParams(-1, -1));

        final View back = iconButton(IconDrawable.BACK, M3.surfaceContainerHigh, M3.onSurface, 48, 24, this::closeDetail);
        layer.addView(back, new FrameLayout.LayoutParams(M3.dp(48), M3.dp(48), Gravity.TOP | Gravity.START));

        // Pratinjau yang sekarang mundur sedikit dan memudar; yang baru masuk dari kanan.
        if (!detailStack.isEmpty()) {
            View under = topDetail().layer;
            under.animate().cancel();
            under.animate().scaleX(0.94f).scaleY(0.94f).alpha(0.5f)
                    .setDuration(320).setInterpolator(EMPHASIZED).start();
        }
        layer.setAlpha(0f);
        layer.setTranslationX(M3.dp(32));
        root.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        layer.animate().alpha(1f).translationX(0f).setDuration(320).setInterpolator(EMPHASIZED).start();

        final DetailPage page = new DetailPage(layer, dv, back);
        dv.setOnScrolled(a -> {
            page.atTop = a;
            if (!detailStack.isEmpty() && topDetail() == page) updateChrome();
        });
        detailStack.add(page);
        nav.bringToFront();
        trimDetailStack();
        applyDetailInsets();
        updateChrome();
    }

    /** Back: tutup pratinjau paling atas. Pratinjau di bawahnya kembali perlahan. */
    private void closeDetail() {
        if (detailStack.isEmpty()) return;
        final DetailPage top = detailStack.remove(detailStack.size() - 1);
        top.view.destroy();
        top.back.setOnClickListener(null);
        top.layer.animate().cancel();
        top.layer.animate().alpha(0f).translationX(M3.dp(32)).setDuration(240).setInterpolator(EMPHASIZED)
                .withEndAction(() -> root.removeView(top.layer)).start();

        if (!detailStack.isEmpty()) {
            View under = topDetail().layer;
            under.animate().cancel();
            under.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(240).setInterpolator(EMPHASIZED).start();
        }
        updateChrome();
    }

    /** Tutup semua pratinjau sekaligus (dipakai saat tab navigasi ditekan). */
    private void closeAllDetails() {
        while (!detailStack.isEmpty()) {
            DetailPage p = detailStack.remove(detailStack.size() - 1);
            p.view.destroy();
            p.back.setOnClickListener(null);
            p.layer.animate().cancel();
            p.layer.animate().alpha(0f).setDuration(200).withEndAction(() -> root.removeView(p.layer)).start();
        }
        updateChrome();
    }

    /** Batasi tumpukan supaya memori tidak membengkak; halaman paling bawah yang dibuang. */
    private void trimDetailStack() {
        while (detailStack.size() > MAX_DETAIL_STACK) {
            DetailPage old = detailStack.remove(0);
            old.view.destroy();
            root.removeView(old.layer);
        }
    }

    private void applyDetailInsets() {
        for (DetailPage p : detailStack) {
            p.view.setInsets(insT, insB, insL, insR);
            FrameLayout.LayoutParams b = (FrameLayout.LayoutParams) p.back.getLayoutParams();
            b.topMargin = insT + M3.dp(12);
            b.leftMargin = insL + M3.dp(16);
            p.back.setLayoutParams(b);
        }
    }

    // ------------------------------------------------------------ bottom sheet
    /** Buka sheet baru (mengganti yang lama). Navigasi disembunyikan selama sheet terbuka. */
    private void openSheet(View content) {
        BottomSheet old = sheet;
        sheet = null;
        if (old != null && old.isOpen()) old.dismiss();
        final BottomSheet s = BottomSheet.show(root, insB, content);
        sheet = s;
        s.setOnClosingListener(() -> {
            if (sheet == s) {
                sheet = null;
                updateChrome();
            }
        });
        if (snack != null) snack.bringToFront();
        updateChrome();
    }

    private void closeSheet() { if (sheet != null) sheet.dismiss(); }

    /**
     * Navigasi turun saat sheet terbuka, atau saat pratinjau masih di bagian paling atas.
     * Bayangan atas hanya muncul di Beranda saat sheet terbuka (tidak pernah di atas pratinjau).
     */
    private void updateChrome() {
        if (nav == null || home == null) return;
        boolean sheetOpen = sheet != null && sheet.isOpen();
        // Di pratinjau, navigasi hanya tampil setelah di-scroll ke bawah (tidak di bagian paling atas).
        boolean inDetailAtTop = !detailStack.isEmpty() && topDetail().atTop;
        boolean hide = sheetOpen || inDetailAtTop;
        if (hide != chromeHidden) {
            chromeHidden = hide;
            float a = hide ? 0f : 1f;
            float ty = hide ? M3.dp(120) : 0f;
            nav.animate().alpha(a).translationY(ty).setDuration(220).start();
        }
        boolean shade = sheetOpen && detailStack.isEmpty();
        home.setShadeVisible(shade && current == NavBar.HOME);
        searchScreen.setShadeVisible(shade && current == NavBar.SEARCH);
    }

    private void showOptions(final Pin pin, int tileWidth) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        Bitmap pb = ImageLoader.cached(pin.thumb, tileWidth);
        if (pb != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(pb);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            M3.round(iv, 24);
            col.addView(iv, lp(M3.dp(132), M3.dp(176), 0, 0, 0, M3.dp(12)));
        }
        col.addView(optionRow(IconDrawable.DOWNLOAD, "Unduh gambar", () -> { closeSheet(); download(pin); }), lp(-1, M3.dp(56), 0, 0, 0, 0));
        col.addView(optionRow(IconDrawable.SHARE, "Bagikan tautan gambar", () -> { closeSheet(); share(pin); }), lp(-1, M3.dp(56), 0, 0, 0, 0));
        col.addView(optionRow(IconDrawable.OPEN, "Buka halaman sumber", () -> { closeSheet(); openLink(pin); }), lp(-1, M3.dp(56), 0, 0, 0, 0));
        openSheet(col);
    }

    private View optionRow(int iconType, String label, final Runnable r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(M3.dp(12), 0, M3.dp(12), 0);
        row.setBackground(M3.ripple(0, 20));
        row.setOnClickListener(v -> r.run());
        row.addView(icon(iconType, M3.onSurface), new LinearLayout.LayoutParams(M3.dp(24), M3.dp(24)));
        TextView t = M3.text(this, label, 16, M3.onSurface, false);
        t.setPadding(M3.dp(16), 0, 0, 0);
        row.addView(t);
        return row;
    }

    /** Bottom sheet permintaan izin / informasi. */
    private void confirmSheet(String title, String body, String okLabel, final Runnable ok) {
        confirmSheet(new IconDrawable(IconDrawable.DOWNLOAD, M3.onPrimaryContainer), title, body, okLabel, ok);
    }

    private void confirmSheet(Drawable badgeIcon, String title, String body, String okLabel, final Runnable ok) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(M3.shape(M3.primaryContainer, 36));
        ImageView badgeImg = new ImageView(this);
        badgeImg.setImageDrawable(badgeIcon);
        badge.addView(badgeImg, new FrameLayout.LayoutParams(M3.dp(32), M3.dp(32), Gravity.CENTER));
        col.addView(badge, new LinearLayout.LayoutParams(M3.dp(72), M3.dp(72)));

        TextView t = M3.text(this, title, 22, M3.onSurface, true);
        t.setGravity(Gravity.CENTER);
        col.addView(t, lp(-2, -2, 0, M3.dp(16), 0, 0));
        TextView bd = M3.text(this, body, 15, M3.onSurfaceVariant, false);
        bd.setGravity(Gravity.CENTER);
        bd.setLineSpacing(0, 1.2f);
        col.addView(bd, lp(-2, -2, 0, M3.dp(8), 0, 0));

        col.addView(button(okLabel, M3.primary, M3.onPrimary, ok), lp(-1, M3.dp(56), 0, M3.dp(24), 0, 0));
        col.addView(button("Nanti saja", 0, M3.onSurfaceVariant, this::closeSheet), lp(-1, M3.dp(56), 0, M3.dp(8), 0, 0));
        openSheet(col);
    }

    // ------------------------------------------------------------ download & izin
    /**
     * Sudah boleh menyimpan dan menghapus gambar di Pictures/Pinpic. Dicek setiap kali, jadi
     * setelah diberi izin tidak pernah ditanya lagi. Android 11+ memakai MANAGE_EXTERNAL_STORAGE,
     * Android 10 ke bawah memakai izin baca dan tulis biasa.
     */
    private boolean storageReady() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    /** Sudah bisa menyimpan: lewat folder pilihan (SAF) atau izin penyimpanan umum. */
    private boolean canSave() {
        // Folder pilihan (SAF) tidak butuh izin penyimpanan umum.
        return Prefs.downloadTree() != null || storageReady();
    }

    /**
     * Izin penyimpanan belum diberikan: tampilkan permintaan. Dipanggil setiap aplikasi kembali tampil
     * (termasuk saat pertama dibuka). Begitu izin sudah ada, sheet ini tidak pernah muncul lagi, baik
     * saat unduh maupun saat onResume.
     */
    private void askStorageIfNeeded() {
        if (permissionAnswered) { permissionAnswered = false; return; }
        if (canSave()) return;
        if (sheet != null && sheet.isOpen()) return;
        showStorageSheet(null);
    }

    private void download(final Pin pin) {
        if (!canSave()) { showStorageSheet(pin); return; }
        snack("Mengunduh…");
        Downloader.save(this, (ok, msg) -> snack(msg), pin.full, pin.thumb);
    }

    /**
     * Sheet permintaan izin penyimpanan. pin != null: unduhan yang tertunda dilanjutkan setelah izin
     * diberikan. pin == null: hanya meminta izin (saat dibuka atau kembali ke aplikasi).
     */
    private void showStorageSheet(final Pin pin) {
        final Drawable folder = VecIcon.of(this, R.drawable.ic_folder, M3.onPrimaryContainer, M3.dp(32));
        if (Build.VERSION.SDK_INT >= 30) {
            confirmSheet(folder,
                    "Izinkan akses semua file",
                    "Pinpic perlu akses ke semua file untuk menyimpan dan menghapus gambar di folder Pictures/Pinpic. Di layar berikutnya, aktifkan \"Izinkan akses ke semua file\" untuk Pinpic.",
                    "Buka pengaturan", () -> {
                        closeSheet();
                        if (pin != null) pendingPin = pin;   // unduhan dilanjutkan otomatis saat kembali ke Pinpic
                        startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    });
            return;
        }
        confirmSheet(folder,
                "Izinkan akses penyimpanan",
                "Pinpic memerlukan izin penyimpanan untuk menyimpan dan menghapus gambar di folder Pictures/Pinpic di perangkat Anda.",
                "Izinkan", () -> {
                    closeSheet();
                    if (pin != null) pendingPin = pin;
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
                });
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] res) {
        super.onRequestPermissionsResult(rc, perms, res);
        if (rc != REQ_STORAGE) return;
        permissionAnswered = true;
        boolean all = res.length > 0;
        for (int r : res) if (r != PackageManager.PERMISSION_GRANTED) all = false;
        Pin p = pendingPin;
        pendingPin = null;
        if (all) {
            if (p != null) download(p);
        } else if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            confirmSheet("Izin penyimpanan ditolak",
                    "Aktifkan izin Penyimpanan untuk Pinpic di Pengaturan agar bisa mengunduh dan menghapus gambar.",
                    "Buka Pengaturan", () -> {
                        closeSheet();
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName())));
                    });
        } else {
            snack("Izin penyimpanan ditolak");
        }
    }

    /** Konfirmasi lalu hapus beberapa gambar sekaligus dari galeri Saya. */
    private void askDeleteMany(final List<Downloader.Saved> list) {
        confirmSheet(VecIcon.of(this, R.drawable.ic_delete, M3.onPrimaryContainer, M3.dp(32)),
                "Hapus " + list.size() + " gambar?",
                "Gambar yang dipilih akan dihapus dari perangkat. Tindakan ini tidak bisa dibatalkan.",
                "Hapus", () -> {
                    closeSheet();
                    Set<String> names = new HashSet<>();
                    for (Downloader.Saved s : list) names.add(s.name);
                    Downloader.deleteNames(this, names, (ok, msg) -> {
                        snack(msg);
                        if (profile != null) {
                            profile.exitSelection();
                            profile.refresh();
                        }
                    });
                });
    }

    /** Konfirmasi lalu hapus gambar unduhan dari galeri Saya. */
    private void askDelete(final Downloader.Saved s) {
        confirmSheet(VecIcon.of(this, R.drawable.ic_delete, M3.onPrimaryContainer, M3.dp(32)),
                "Hapus gambar?",
                "Gambar \"" + s.name + "\" akan dihapus dari perangkat. Tindakan ini tidak bisa dibatalkan.",
                "Hapus", () -> {
                    closeSheet();
                    Downloader.delete(this, s, (ok, msg) -> {
                        snack(msg);
                        if (profile != null) profile.refresh();
                    });
                });
    }

    private void share(Pin pin) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, pin.full);
        startActivity(Intent.createChooser(i, "Bagikan"));
    }

    private void openLink(Pin pin) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(pin.link))); }
        catch (Exception e) { snack("Tidak bisa membuka tautan"); }
    }

    // ------------------------------------------------------------ snackbar
    private final Runnable hideSnack = () -> {
        if (snack != null) snack.animate().alpha(0f).setDuration(200).withEndAction(() -> snack.setVisibility(View.GONE)).start();
    };

    private void snack(String msg) {
        if (snack == null) {
            snack = M3.text(this, msg, 14, M3.inverseOnSurface, false);
            snack.setBackground(M3.shape(M3.inverseSurface, 16));
            snack.setPadding(M3.dp(20), M3.dp(14), M3.dp(20), M3.dp(14));
            snack.setElevation(M3.dp(10));
            root.addView(snack, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        }
        snack.setText(msg);
        bottomMargin(snack, insB + M3.dp(100));
        snack.bringToFront();
        snack.setVisibility(View.VISIBLE);
        snack.animate().cancel();
        snack.setAlpha(0f);
        snack.animate().alpha(1f).setDuration(150).start();
        snack.removeCallbacks(hideSnack);
        snack.postDelayed(hideSnack, 2600);
    }

    // ------------------------------------------------------------ view helpers
    private ImageView icon(int type, int color) {
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new IconDrawable(type, color));
        return iv;
    }

    private FrameLayout iconButton(int type, int fill, int tint, int sizeDp, float radiusDp, final Runnable click) {
        FrameLayout f = new FrameLayout(this);
        f.setBackground(M3.ripple(fill, radiusDp));
        int is = Math.round(sizeDp * 0.5f);
        f.addView(icon(type, tint), new FrameLayout.LayoutParams(M3.dp(is), M3.dp(is), Gravity.CENTER));
        f.setOnClickListener(v -> click.run());
        return f;
    }

    private TextView button(String label, int fill, int fg, final Runnable r) {
        TextView t = M3.text(this, label, 16, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(M3.ripple(fill, 28));
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private static LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    private static void bottomMargin(View v, int px) {
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) v.getLayoutParams();
        if (p == null) return;
        p.bottomMargin = px;
        v.setLayoutParams(p);
    }
}
