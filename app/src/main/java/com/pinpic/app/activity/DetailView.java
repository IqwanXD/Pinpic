package com.pinpic.app.activity;

import android.app.Activity;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pinpic.app.ZoomImageView;
import com.pinpic.app.IconDrawable;
import com.pinpic.app.ImageLoader;
import com.pinpic.app.M3;
import com.pinpic.app.Pin;

import java.util.concurrent.Future;

/**
 * Halaman detail gambar ala Pinterest, dalam satu scroll:
 * pratinjau (bisa di-zoom; sudut membulat tetap karena kartu di-clip ke outline),
 * judul, tombol Simpan + Bagikan di kiri bawah judul, lalu grid gambar serupa di bawahnya.
 */
public final class DetailView extends FrameLayout {

    private static final int CARD_RADIUS_DP = 24;

    private final FeedView feed;
    private final LinearLayout header;
    private final FrameLayout card;
    private final LinearLayout.LayoutParams cardLp;
    private final ZoomImageView zoom;
    private final int screenW;
    private final boolean ratioFromPin;
    private Future<?> job;
    private float ratio;
    private int left, right;

    public DetailView(Activity act, final ScreenHost host, final Pin pin, Bitmap preview, String origin) {
        super(act);
        screenW = act.getResources().getDisplayMetrics().widthPixels;
        ratioFromPin = pin.ratio > 0;

        header = new LinearLayout(act);
        header.setOrientation(LinearLayout.VERTICAL);

        // Kartu pratinjau: sudut membulat lewat clipToOutline. ZoomImageView di dalamnya ikut terpotong
        // mengikuti outline, jadi sudutnya tetap membulat saat di-zoom.
        card = new FrameLayout(act);
        card.setBackground(M3.shape(M3.surfaceContainerHigh, CARD_RADIUS_DP));
        card.setClipToOutline(true);
        zoom = new ZoomImageView(act);
        zoom.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(zoom, new FrameLayout.LayoutParams(-1, -1));
        cardLp = new LinearLayout.LayoutParams(-1, M3.dp(300));
        cardLp.setMargins(M3.dp(16), 0, M3.dp(16), 0);
        header.addView(card, cardLp);

        if (preview != null && preview.getWidth() > 0) {
            ratio = preview.getHeight() / (float) preview.getWidth();
            zoom.setImageBitmap(preview);
        } else {
            ratio = 1.25f;
        }
        if (ratioFromPin) ratio = pin.ratio;

        String title = pin.title != null && !pin.title.isEmpty() ? pin.title : (origin == null ? "" : origin);
        TextView t = M3.text(act, title, 22, M3.onSurface, true);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.setMargins(M3.dp(16), M3.dp(16), M3.dp(16), 0);
        header.addView(t, tl);

        LinearLayout actions = new LinearLayout(act);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout save = new LinearLayout(act);
        save.setOrientation(LinearLayout.HORIZONTAL);
        save.setGravity(Gravity.CENTER_VERTICAL);
        save.setPadding(M3.dp(22), 0, M3.dp(26), 0);
        save.setBackground(M3.ripple(M3.primary, 26));
        save.setClickable(true);
        save.setOnClickListener(v -> host.onSave(pin));
        save.addView(Ui.icon(act, IconDrawable.DOWNLOAD, M3.onPrimary),
                new LinearLayout.LayoutParams(M3.dp(22), M3.dp(22)));
        TextView st = M3.text(act, "Simpan", 16, M3.onPrimary, true);
        st.setPadding(M3.dp(10), 0, 0, 0);
        save.addView(st);
        actions.addView(save, new LinearLayout.LayoutParams(-2, M3.dp(52)));

        actions.addView(Ui.iconButton(act, IconDrawable.SHARE, M3.secondaryContainer, M3.onSecondaryContainer,
                52, 26, () -> host.onShare(pin)), Ui.lp(M3.dp(52), M3.dp(52), M3.dp(10), 0, 0, 0));

        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-1, -2);
        al.setMargins(M3.dp(16), M3.dp(16), M3.dp(16), M3.dp(20));
        header.addView(actions, al);

        // Di bawah pratinjau: gambar acak seperti Beranda (bukan turunan dari gambar yang dibuka),
        // jadi setiap pembukaan menampilkan urutan yang berbeda.
        feed = new FeedView(act, host, true);
        feed.setHeader(header);
        addView(feed, new FrameLayout.LayoutParams(-1, -1));
        feed.search("");

        job = ImageLoader.load(pin.full, pin.thumb, screenW, Bitmap.Config.ARGB_8888, bmp -> {
            if (bmp == null) return;
            zoom.setImageBitmap(bmp);
            if (!ratioFromPin) {
                ratio = bmp.getHeight() / (float) bmp.getWidth();
                applyCardHeight();
            }
        });
    }

    /** Ruang untuk tombol kembali di atas, inset sistem, dan jarak dari tepi. */
    public void setInsets(int top, int bottom, int left, int right) {
        this.left = left;
        this.right = right;
        header.setPadding(0, top + M3.dp(68), 0, 0);
        applyCardHeight();
        feed.setInsets(left, right, bottom);
    }

    private void applyCardHeight() {
        int w = screenW - left - right - M3.dp(32);
        float r = Math.max(0.6f, Math.min(2.2f, ratio));
        cardLp.height = Math.round(w * r);
        card.setLayoutParams(cardLp);
    }

    /** Dipanggil saat pratinjau di-scroll; atTop = true bila masih di paling atas. */
    public interface OnScrolled { void onScrolled(boolean atTop); }

    public void setOnScrolled(final OnScrolled l) {
        feed.setScrollListener(l::onScrolled);
    }

    /** Batalkan pemuatan gambar penuh saat detail ditutup. */
    public void destroy() {
        if (job != null) job.cancel(false);
        job = null;
    }
}
