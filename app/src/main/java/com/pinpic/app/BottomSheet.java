package com.pinpic.app;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Modal bottom sheet buatan sendiri (scrim, drag handle, swipe-to-dismiss).
 * - Panel punya elevation lebih tinggi dari navigasi, jadi selalu menimpa navigasi.
 * - Bayangan gelap di atas tepi panel menandai batas bawah area scroll.
 */
public final class BottomSheet extends FrameLayout {

    interface Listener {
        /** Dipanggil saat sheet mulai ditutup (animasi keluar dimulai). */
        void onClosing();
    }

    private static final int SHADOW_DP = 32;

    private final View scrim;
    private final LinearLayout panel;
    private boolean closing;
    private Listener listener;

    public static BottomSheet show(ViewGroup root, int bottomInset, View content) {
        BottomSheet s = new BottomSheet(root.getContext(), bottomInset, content);
        root.addView(s, new FrameLayout.LayoutParams(-1, -1));
        s.open();
        return s;
    }

    private BottomSheet(Context c, int bottomInset, View content) {
        super(c);
        setClickable(true);

        scrim = new View(c);
        scrim.setBackgroundColor(M3.scrim);
        scrim.setAlpha(0f);
        scrim.setOnClickListener(v -> dismiss());
        addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(M3.shape(M3.surfaceContainerHigh, 32, 32, 0, 0));
        panel.setPadding(M3.dp(24), 0, M3.dp(24), bottomInset + M3.dp(24));
        panel.setVisibility(INVISIBLE);
        panel.setClickable(true);
        panel.setClipChildren(false);     // bayangan di atas panel boleh keluar dari batasnya
        panel.setElevation(M3.dp(16));    // lebih tinggi dari navigasi (8dp): sheet menimpa navigasi

        // Bayangan atas: ikut bergerak bersama panel karena anaknya.
        View topShadow = new View(c);
        topShadow.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x99000000}));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, M3.dp(SHADOW_DP));
        sl.topMargin = -M3.dp(SHADOW_DP);
        panel.addView(topShadow, sl);

        FrameLayout handleArea = new FrameLayout(c);
        View handle = new View(c);
        handle.setBackground(M3.shape(M3.outline, 2));
        handleArea.addView(handle, new FrameLayout.LayoutParams(M3.dp(36), M3.dp(4), Gravity.CENTER));
        handleArea.setOnTouchListener(new OnTouchListener() {
            float downY;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        panel.setTranslationY(Math.max(0f, e.getRawY() - downY));
                        scrim.setAlpha(1f - panel.getTranslationY() / Math.max(1, panel.getHeight()));
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (panel.getTranslationY() > panel.getHeight() / 4f) {
                            dismiss();
                        } else {
                            panel.animate().translationY(0).setDuration(200).start();
                            scrim.animate().alpha(1f).setDuration(200).start();
                        }
                        return true;
                }
                return false;
            }
        });
        panel.addView(handleArea, new LinearLayout.LayoutParams(-1, M3.dp(32)));
        panel.addView(content, new LinearLayout.LayoutParams(-1, -2));
        addView(panel, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
    }

    void setOnClosingListener(Listener l) { listener = l; }

    private void open() {
        panel.post(() -> {
            panel.setTranslationY(panel.getHeight());
            panel.setVisibility(VISIBLE);
            panel.animate().translationY(0).setDuration(400)
                    .setInterpolator(new PathInterpolator(0.05f, 0.7f, 0.1f, 1f)).start();
            scrim.animate().alpha(1f).setDuration(250).start();
        });
    }

    public boolean isOpen() { return getParent() != null && !closing; }

    public void dismiss() {
        if (closing) return;
        closing = true;
        if (listener != null) listener.onClosing();
        scrim.animate().alpha(0f).setDuration(240).start();
        panel.animate().translationY(panel.getHeight()).setDuration(240).withEndAction(() -> {
            if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        }).start();
    }
}
