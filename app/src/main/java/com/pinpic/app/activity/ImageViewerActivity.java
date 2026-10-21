package com.pinpic.app.activity;

import com.pinpic.app.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Tampilan gambar layar penuh, edge-to-edge: gambar digambar sampai di belakang status bar
 * dan navigation bar, dan awalnya berada di tengah. Zoom memakai ZoomImageView dari classes.jar.
 * Geser kiri/kanan pindah ke gambar unduhan sebelumnya/berikutnya (saat tidak di-zoom).
 * Tombol titik tiga di kanan atas membuka menu: Hapus (lewat bottom sheet), Bagikan, Jadikan wallpaper.
 */
public class ImageViewerActivity extends Activity {
    /** Extra boolean: true bila yang dibuka adalah foto profil (bukan gambar galeri). Menu tidak ditampilkan. */
    public static final String EXTRA_AVATAR = "avatar";

    private static final long SLIDE_MS = 220;
    private static final float COMMIT_FRACTION = 0.25f;
    private static final float FLING_VX = 1500f;

    private Pager pager;
    private BottomSheet sheet;
    /** Inset sistem, dipakai untuk menaruh tombol dan panel sheet di atas navigation bar. */
    private int insT, insB, insL, insR;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        M3.init(this);

        // Edge-to-edge penuh: status bar dan navigation bar transparan, gambar digambar di belakangnya
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        // Matikan scrim otomatis dari sistem (kontras navigation bar), sama seperti MainActivity
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            android.view.WindowManager.LayoutParams wlp = getWindow().getAttributes();
            wlp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(wlp);
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        final Uri uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }

        pager = new Pager(this);
        pager.setBackgroundColor(Color.BLACK);

        // Tombol kembali di pojok kiri atas, di bawah status bar
        FrameLayout back = new FrameLayout(this);
        back.setBackground(M3.ripple(0x66000000, 24));
        ImageView bi = new ImageView(this);
        bi.setImageDrawable(new IconDrawable(IconDrawable.BACK, 0xFFFFFFFF));
        back.addView(bi, new FrameLayout.LayoutParams(M3.dp(24), M3.dp(24), Gravity.CENTER));
        back.setOnClickListener(v -> finish());
        final FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(M3.dp(48), M3.dp(48), Gravity.START | Gravity.TOP);
        bl.leftMargin = M3.dp(12);
        bl.topMargin = M3.dp(12);
        pager.addView(back, bl);

        // Tombol more (titik tiga) di kanan atas, sejajar dengan tombol kembali
        FrameLayout more = new FrameLayout(this);
        more.setBackground(M3.ripple(0x66000000, 24));
        TextView dots = new TextView(this);
        dots.setText("⋮");
        dots.setTextColor(0xFFFFFFFF);
        dots.setTextSize(22);
        dots.setGravity(Gravity.CENTER);
        more.addView(dots, new FrameLayout.LayoutParams(-1, -1));
        more.setOnClickListener(v -> showMenu(more));
        final FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(M3.dp(48), M3.dp(48), Gravity.END | Gravity.TOP);
        ml.rightMargin = M3.dp(12);
        ml.topMargin = M3.dp(12);
        pager.addView(more, ml);
        final boolean avatar = getIntent().getBooleanExtra(EXTRA_AVATAR, false);
        if (avatar) more.setVisibility(android.view.View.GONE);

        // Tombol mengikuti inset sistem supaya tidak tertutup status bar
        pager.setOnApplyWindowInsetsListener((v, ins) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                insT = i.top; insB = i.bottom; insL = i.left; insR = i.right;
            } else {
                insT = ins.getSystemWindowInsetTop(); insB = ins.getSystemWindowInsetBottom();
                insL = ins.getSystemWindowInsetLeft(); insR = ins.getSystemWindowInsetRight();
            }
            bl.topMargin = insT + M3.dp(12);
            bl.leftMargin = insL + M3.dp(12);
            ml.topMargin = insT + M3.dp(12);
            ml.rightMargin = insR + M3.dp(12);
            back.setLayoutParams(bl);
            more.setLayoutParams(ml);
            return ins;
        });

        setContentView(pager);
        pager.showFirst(uri);

        // Daftar gambar unduhan (urut seperti galeri Saya) dimuat di background, lalu geser bisa dipakai
        // Foto profil tidak ada di galeri, jadi tidak perlu daftar.
        if (!avatar) {
            final Context app = getApplicationContext();
            new Thread(() -> {
                final List<Downloader.Saved> list = Downloader.listSaved(app);
                runOnUiThread(() -> pager.setSaved(list, uri));
            }).start();
        }
    }

    private ZoomImageView newPage(Uri u) {
        final ZoomImageView zoom = new ZoomImageView(this);
        zoom.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // Decode seukuran layar (x2 untuk ketajaman saat di-zoom)
        ImageLoader.loadUri(this, u, getResources().getDisplayMetrics().widthPixels * 2, bmp -> {
            if (bmp != null) zoom.setImageBitmap(bmp);
            else M3.toast(this, "Gambar tidak bisa dibuka");
        });
        return zoom;
    }

    // ------------------------------------------------------------ menu more

    private void showMenu(View anchor) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, M3.dp(6), 0, M3.dp(6));
        col.setBackground(M3.shape(M3.surfaceContainerHigh, 16));
        col.setElevation(M3.dp(8));

        final PopupWindow menu = new PopupWindow(col, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        menu.setFocusable(true);
        menu.setOutsideTouchable(true);
        menu.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        col.addView(menuItem("Hapus", () -> { menu.dismiss(); confirmDelete(); }));
        col.addView(menuItem("Bagikan", () -> { menu.dismiss(); share(); }));
        col.addView(menuItem("Jadikan wallpaper", () -> { menu.dismiss(); setWallpaper(); }));
        menu.showAsDropDown(anchor, 0, M3.dp(4), Gravity.END);
    }

    private TextView menuItem(String text, Runnable action) {
        TextView t = M3.text(this, text, 15, M3.onSurface, false);
        t.setPadding(M3.dp(20), M3.dp(14), M3.dp(28), M3.dp(14));
        t.setMinWidth(M3.dp(200));
        t.setBackground(M3.ripple(0, 0));
        t.setClickable(true);
        t.setOnClickListener(v -> action.run());
        return t;
    }

    // ------------------------------------------------------------ hapus (bottom sheet, sama seperti galeri Saya)

    private void confirmDelete() {
        final Downloader.Saved s = pager.currentSaved();
        if (s == null) {
            M3.toast(this, "Gambar belum siap");
            return;
        }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(M3.shape(M3.primaryContainer, 36));
        ImageView badgeImg = new ImageView(this);
        badgeImg.setImageDrawable(VecIcon.of(this, R.drawable.ic_delete, M3.onPrimaryContainer, M3.dp(32)));
        badge.addView(badgeImg, new FrameLayout.LayoutParams(M3.dp(32), M3.dp(32), Gravity.CENTER));
        col.addView(badge, new LinearLayout.LayoutParams(M3.dp(72), M3.dp(72)));

        TextView t = M3.text(this, "Hapus gambar?", 22, M3.onSurface, true);
        t.setGravity(Gravity.CENTER);
        col.addView(t, lp(-2, -2, 0, M3.dp(16), 0, 0));

        TextView bd = M3.text(this, "Gambar \"" + s.name + "\" akan dihapus dari perangkat. Tindakan ini tidak bisa dibatalkan.",
                15, M3.onSurfaceVariant, false);
        bd.setGravity(Gravity.CENTER);
        bd.setLineSpacing(0, 1.2f);
        col.addView(bd, lp(-2, -2, 0, M3.dp(8), 0, 0));

        col.addView(button("Hapus", M3.primary, M3.onPrimary, () -> {
            closeSheet();
            Downloader.delete(this, s, (ok, msg) -> {
                M3.toast(this, msg);
                if (ok) finish();
            });
        }), lp(-1, M3.dp(56), 0, M3.dp(24), 0, 0));
        col.addView(button("Nanti saja", 0, M3.onSurfaceVariant, this::closeSheet), lp(-1, M3.dp(56), 0, M3.dp(8), 0, 0));
        openSheet(col);
    }

    private void openSheet(View content) {
        if (sheet != null) sheet.dismiss();
        sheet = BottomSheet.show(pager, insB, content);
    }

    private void closeSheet() {
        if (sheet != null) sheet.dismiss();
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

    // ------------------------------------------------------------ bagikan & wallpaper

    private void share() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_STREAM, pager.currentUri());
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Bagikan"));
    }

    private void setWallpaper() {
        Intent i = new Intent(Intent.ACTION_ATTACH_DATA);
        i.setDataAndType(pager.currentUri(), "image/*");
        i.putExtra("mimeType", "image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Jadikan wallpaper"));
    }

    // ------------------------------------------------------------ pager

    /**
     * Pager sederhana: halaman aktif, dan satu halaman tetangga yang ikut bergeser saat digeser.
     * Hanya mengambil alih sentuhan saat gambar aktif tidak di-zoom, gerakannya mendatar,
     * dan tidak ada bottom sheet yang terbuka.
     */
    private final class Pager extends FrameLayout {
        private final List<Uri> uris = new ArrayList<>();
        private List<Downloader.Saved> saved = new ArrayList<>();
        private final int slop;
        private int index;
        private ZoomImageView cur, next;
        private int nextIndex = -1, dir;      // dir: -1 = halaman sebelumnya, +1 = berikutnya
        private boolean dragging, busy;
        private float downX, downY;
        private VelocityTracker vt;

        Pager(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
        }

        void showFirst(Uri u) {
            uris.clear();
            uris.add(u);
            index = 0;
            cur = newPage(u);
            addView(cur, 0, new FrameLayout.LayoutParams(-1, -1));   // penuh, edge-to-edge
        }

        /** Dipanggil setelah daftar gambar tersedia. Posisi hanya dipakai bila pengguna belum menggeser. */
        void setSaved(List<Downloader.Saved> list, Uri current) {
            saved = list;
            if (busy || dragging || uris.size() != 1) return;
            List<Uri> all = new ArrayList<>();
            int at = -1;
            for (Downloader.Saved s : list) {
                if (s.uri.equals(current)) at = all.size();
                all.add(s.uri);
            }
            if (at < 0) return;
            uris.clear();
            uris.addAll(all);
            index = at;
        }

        Uri currentUri() {
            return uris.get(index);
        }

        Downloader.Saved currentSaved() {
            Uri u = currentUri();
            for (Downloader.Saved s : saved) if (s.uri.equals(u)) return s;
            return null;
        }

        private boolean isZoomed(View v) {
            return v instanceof ZoomImageView && ((ZoomImageView) v).getCurrentScaleFactor() > 1.01f;
        }

        private boolean sheetOpen() {
            return sheet != null && sheet.isOpen();
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    downY = ev.getY();
                    dragging = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) return true;
                    if (busy || cur == null || sheetOpen() || ev.getPointerCount() > 1 || isZoomed(cur)) return false;
                    float dx = ev.getX() - downX, dy = ev.getY() - downY;
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        beginDrag(ev);
                        return true;
                    }
                    return false;
                default:
                    return dragging;
            }
        }

        private void beginDrag(MotionEvent ev) {
            dragging = true;
            vt = VelocityTracker.obtain();
            vt.addMovement(ev);
        }

        @Override public boolean onTouchEvent(MotionEvent ev) {
            if (!dragging) return false;
            if (vt != null) vt.addMovement(ev);
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    move(ev.getX() - downX);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    release();
                    return true;
                default:
                    return true;
            }
        }

        private void move(float dx) {
            int d = dx > 0 ? -1 : 1;
            int target = index + d;
            if (target < 0 || target >= uris.size()) {
                removeNext();
                cur.setTranslationX(dx * 0.3f);   // ujung daftar: tarikan kecil saja
                return;
            }
            if (next == null || d != dir) {
                removeNext();
                dir = d;
                next = newPage(uris.get(target));
                nextIndex = target;
                addView(next, 0, new FrameLayout.LayoutParams(-1, -1));
            }
            float w = getWidth();
            cur.setTranslationX(dx);
            next.setTranslationX(dx + d * w);
        }

        private void removeNext() {
            if (next != null) removeView(next);
            next = null;
            nextIndex = -1;
        }

        private void release() {
            dragging = false;
            float vx = 0;
            if (vt != null) {
                vt.computeCurrentVelocity(1000);
                vx = vt.getXVelocity();
                vt.recycle();
                vt = null;
            }
            float dx = cur.getTranslationX();
            float w = getWidth();
            boolean sameSide = next != null && Math.signum(dx) == -dir;
            boolean commit = sameSide && (Math.abs(dx) > w * COMMIT_FRACTION
                    || (Math.abs(vx) > FLING_VX && Math.signum(vx) == -dir));

            if (commit) {
                busy = true;
                final ZoomImageView old = cur;
                final ZoomImageView incoming = next;
                final int newIndex = nextIndex;
                old.animate().translationX(-dir * w).setDuration(SLIDE_MS)
                        .setInterpolator(new DecelerateInterpolator()).start();
                incoming.animate().translationX(0).setDuration(SLIDE_MS)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> {
                            removeView(old);
                            cur = incoming;
                            index = newIndex;
                            next = null;
                            nextIndex = -1;
                            busy = false;
                        }).start();
            } else {
                busy = true;
                cur.animate().translationX(0).setDuration(SLIDE_MS)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> busy = false).start();
                if (next != null) {
                    final ZoomImageView out = next;
                    out.animate().translationX(dir * w).setDuration(SLIDE_MS)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() -> {
                                removeView(out);
                                if (next == out) { next = null; nextIndex = -1; }
                            }).start();
                }
            }
        }
    }
}
