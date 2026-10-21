package com.pinpic.app.activity;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pinpic.app.M3;
import com.pinpic.app.R;
import com.pinpic.app.VecIcon;

/** Beranda: judul + umpan gambar. Bayangan atas hanya ada di sini (saat bottom sheet terbuka). */
public final class HomeScreen implements Screen {

    private final FrameLayout root;
    private final LinearLayout header;
    private final FeedView feed;
    private final View shade;

    public HomeScreen(Activity act, ScreenHost host, final Runnable onMenu) {
        this(act, host, onMenu, null);
    }

    /** prev: feed beranda sebelumnya (saat tema berubah), agar item tidak dimuat ulang. */
    public HomeScreen(Activity act, ScreenHost host, final Runnable onMenu, FeedView prev) {
        root = new FrameLayout(act);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);

        header = new LinearLayout(act);
        header.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(act);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout menuBtn = new FrameLayout(act);
        menuBtn.setBackground(M3.ripple(0, 24));
        ImageView menuIcon = new ImageView(act);
        menuIcon.setImageDrawable(VecIcon.of(act, R.drawable.ic_menu, M3.onSurface, M3.dp(24)));
        menuBtn.addView(menuIcon, new FrameLayout.LayoutParams(M3.dp(24), M3.dp(24), Gravity.CENTER));
        menuBtn.setOnClickListener(v -> onMenu.run());
        titleRow.addView(menuBtn, new LinearLayout.LayoutParams(M3.dp(48), M3.dp(48)));

        TextView title = M3.text(act, "Pinpic", 34, M3.primary, true);
        title.setLetterSpacing(-0.02f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = M3.dp(6);
        titleRow.addView(title, tl);
        header.addView(titleRow, new LinearLayout.LayoutParams(-2, -2));
        col.addView(header, new LinearLayout.LayoutParams(-1, -2));

        feed = new FeedView(act, host, false, prev);
        col.addView(feed, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        shade = new View(act);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{M3.dark ? 0xB3000000 : 0x26000000, 0x00000000}));
        shade.setAlpha(0f);
        root.addView(shade, new FrameLayout.LayoutParams(-1, M3.dp(28), Gravity.TOP));
        header.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> shade.setTranslationY(b));
    }

    public FeedView feed() { return feed; }

    /** Bayangan atas tampil hanya saat diminta (MainActivity: sheet terbuka dan tidak ada pratinjau). */
    public void setShadeVisible(boolean on) {
        shade.animate().alpha(on ? 1f : 0f).setDuration(240).start();
    }

    public void scrollToTop() {
        feed.scrollToTop();
    }

    @Override public View getView() { return root; }

    @Override public void onShown() { feed.start(); }

    @Override public void onHidden() { }

    @Override public void setInsets(int top, int bottom, int left, int right) {
        header.setPadding(M3.dp(16) + left, top + M3.dp(8), M3.dp(16) + right, M3.dp(8));
        feed.setInsets(left, right, bottom);
    }
}
