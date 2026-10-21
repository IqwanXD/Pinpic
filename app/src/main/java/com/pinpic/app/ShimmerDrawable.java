package com.pinpic.app;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.animation.LinearInterpolator;

/**
 * Latar shimmer untuk satu gambar yang sedang dimuat (dipakai di tile grid).
 * Sama seperti ShimmerView, tetapi berupa Drawable sehingga bisa dipasang sebagai background
 * tile. Animasi hanya jalan saat start() dipanggil dan berhenti di stop(), jadi tile yang
 * sudah selesai tampil atau sudah di luar layar tidak membebani frame.
 */
public final class ShimmerDrawable extends Drawable {
    private static final int SWEEP_MS = 1500;      // satu kali kilau melintas
    private static final float SHINE = 0.07f;      // seberapa terang kilau dibanding warna dasar

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final float radius;
    private final ValueAnimator anim;
    private Shader shader;
    private int shaderW = -1;

    public ShimmerDrawable(float radiusPx) {
        radius = radiusPx;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(SWEEP_MS);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.RESTART);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            matrix.setTranslate(((float) a.getAnimatedValue() * 2f - 1f) * shaderW, 0f);
            if (shader != null) shader.setLocalMatrix(matrix);
            invalidateSelf();
        });
    }

    /** Mulai kilau. Diabaikan bila animasi sistem dimatikan (tampil sebagai kotak diam). */
    public void start() {
        boolean animate = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
        if (animate && !anim.isStarted()) anim.start();
    }

    public void stop() {
        anim.cancel();
    }

    @Override protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        buildShader(bounds.width());
    }

    private void buildShader(int w) {
        if (w <= 0) return;
        shaderW = w;
        int base = M3.surfaceContainerHigh;
        int shine = M3.mix(base, M3.onSurface, SHINE);
        // Warna dasar di tepi (CLAMP), kilau di tengah gradien: kilau yang geser terlihat seperti sapuan lembut.
        int[] colors = {base, base, shine, base, base};
        float[] stops = {0f, 0.35f, 0.5f, 0.65f, 1f};
        shader = new LinearGradient(0, 0, w, 0, colors, stops, Shader.TileMode.CLAMP);
        matrix.setTranslate(0f, 0f);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        if (shader == null || shaderW != b.width()) buildShader(b.width());
        // Gradien dibuat dari (0,0): geser kanvas ke posisi drawable agar tidak meleset.
        canvas.save();
        canvas.translate(b.left, b.top);
        rect.set(0, 0, b.width(), b.height());
        if (shader != null) paint.setShader(shader);
        else paint.setColor(M3.surfaceContainerHigh);
        canvas.drawRoundRect(rect, radius, radius, paint);
        canvas.restore();
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }

    @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
