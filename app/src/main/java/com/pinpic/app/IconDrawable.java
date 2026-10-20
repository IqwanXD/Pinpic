package com.pinpic.app;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Ikon digambar manual (kanvas 24x24) supaya tidak butuh drawable XML. */
public final class IconDrawable extends Drawable {
    public static final int SEARCH = 0, BACK = 1, CLOSE = 2, DOWNLOAD = 3, SHARE = 4, HOME = 5, REFRESH = 6, OPEN = 7, PROFILE = 8;

    private final int type;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public IconDrawable(int type, int color) {
        this.type = type;
        p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / 24f;
        c.save();
        c.translate(b.left, b.top);
        c.scale(s, s);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f);
        Path pa = new Path();
        switch (type) {
            case SEARCH:
                c.drawCircle(10.5f, 10.5f, 6.5f, p);
                c.drawLine(15.3f, 15.3f, 20.5f, 20.5f, p);
                break;
            case BACK:
                c.drawLine(20, 12, 4.5f, 12, p);
                c.drawLine(11, 5.5f, 4.5f, 12, p);
                c.drawLine(11, 18.5f, 4.5f, 12, p);
                break;
            case CLOSE:
                c.drawLine(6, 6, 18, 18, p);
                c.drawLine(18, 6, 6, 18, p);
                break;
            case DOWNLOAD:
                c.drawLine(12, 4, 12, 15.5f, p);
                c.drawLine(6.5f, 10, 12, 15.5f, p);
                c.drawLine(17.5f, 10, 12, 15.5f, p);
                c.drawLine(5, 20, 19, 20, p);
                break;
            case SHARE:
                c.drawLine(8.2f, 10.8f, 15.8f, 6.7f, p);
                c.drawLine(8.2f, 13.2f, 15.8f, 17.3f, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(18, 5.5f, 2.8f, p);
                c.drawCircle(6, 12, 2.8f, p);
                c.drawCircle(18, 18.5f, 2.8f, p);
                break;
            case HOME:
                pa.moveTo(3.5f, 11.5f); pa.lineTo(12, 4.5f); pa.lineTo(20.5f, 11.5f);
                c.drawPath(pa, p);
                pa.reset();
                pa.moveTo(5.5f, 10f); pa.lineTo(5.5f, 19.5f); pa.lineTo(10f, 19.5f); pa.lineTo(10f, 14f);
                pa.lineTo(14f, 14f); pa.lineTo(14f, 19.5f); pa.lineTo(18.5f, 19.5f); pa.lineTo(18.5f, 10f);
                c.drawPath(pa, p);
                break;
            case PROFILE:
                c.drawCircle(12, 8.5f, 4f, p);
                c.drawArc(new RectF(4.5f, 13.5f, 19.5f, 30f), 180, 180, false, p);
                c.drawLine(4.5f, 21.75f, 19.5f, 21.75f, p);
                break;
            case REFRESH: {
                c.drawArc(new RectF(5, 5, 19, 19), 40, 280, false, p);
                double a = Math.toRadians(320);
                float ex = 12 + 7 * (float) Math.cos(a), ey = 12 + 7 * (float) Math.sin(a);
                float tx = (float) -Math.sin(a), ty = (float) Math.cos(a);
                float nx = (float) Math.cos(a), ny = (float) Math.sin(a);
                p.setStyle(Paint.Style.FILL);
                pa.moveTo(ex + tx * 3.6f, ey + ty * 3.6f);
                pa.lineTo(ex + nx * 3.2f, ey + ny * 3.2f);
                pa.lineTo(ex - nx * 3.2f, ey - ny * 3.2f);
                pa.close();
                c.drawPath(pa, p);
                break;
            }
            case OPEN:
                pa.moveTo(10, 5); pa.lineTo(5, 5); pa.lineTo(5, 19); pa.lineTo(19, 19); pa.lineTo(19, 14);
                c.drawPath(pa, p);
                c.drawLine(14, 5, 19, 5, p);
                c.drawLine(19, 5, 19, 10, p);
                c.drawLine(19, 5, 11, 13, p);
                break;
        }
        c.restore();
    }

    @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
