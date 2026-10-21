package com.pinpic.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Kotak placeholder dengan kilau lembut yang melintas dari kiri ke kanan (efek shimmer).
 * Warna dasar mengikuti tema. Animasi hanya berjalan saat view terpasang di jendela
 * dan berhenti saat dilepas, supaya tidak membebani layar yang tidak tampil.
 */
public final class ShimmerView extends View {
    private static final int SWEEP_MS = 1500;      // satu kali kilau melintas
    private static final float SHINE = 0.07f;      // seberapa terang kilau dibanding warna dasar

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final float radius;
    private final ValueAnimator anim;
    private Shader shader;
    private int shaderW = -1, baseColor, shineColor;

    public ShimmerView(Context c, float radiusDp) {
        super(c);
        radius = M3.dp(radiusDp);
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(SWEEP_MS);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setRepeatMode(ValueAnimator.RESTART);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            matrix.setTranslate(((float) a.getAnimatedValue() * 2f - 1f) * shaderW, 0f);
            if (shader != null) shader.setLocalMatrix(matrix);
            invalidate();
        });
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Dimatikan oleh pengaturan animasi sistem: tampil sebagai kotak diam.
        // areAnimatorsEnabled() baru ada di API 26; minSdk proyek 24, jadi dicek dulu.
        boolean animate = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled();
        if (animate && !anim.isStarted()) anim.start();
    }

    @Override protected void onDetachedFromWindow() {
        anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        buildShader(w);
    }

    private void buildShader(int w) {
        shaderW = w;
        baseColor = M3.surfaceContainerHigh;
        shineColor = M3.mix(baseColor, M3.onSurface, SHINE);
        // Warna dasar di tepi (CLAMP), kilau di tengah gradien: kilau yang geser terlihat seperti sapuan lembut.
        int[] colors = {baseColor, baseColor, shineColor, baseColor, baseColor};
        float[] stops = {0f, 0.35f, 0.5f, 0.65f, 1f};
        shader = new LinearGradient(0, 0, Math.max(1, w), 0, colors, stops, Shader.TileMode.CLAMP);
        matrix.setTranslate(0f, 0f);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (shader == null) {
            paint.setShader(null);
            paint.setColor(M3.surfaceContainerHigh);
        }
        rect.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(rect, radius, radius, paint);
    }
}
