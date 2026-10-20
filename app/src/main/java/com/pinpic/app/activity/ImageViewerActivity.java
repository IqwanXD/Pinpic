package com.pinpic.app.activity;

import com.pinpic.app.*;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import ru.inoui.ZoomImageView;

/** Tampilan gambar layar penuh dengan zoom (pinch, geser, dan ketuk dua kali) memakai ZoomImageView dari classes.jar. */
public class ImageViewerActivity extends Activity {

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        M3.init(this);

        final Uri uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        ZoomImageView zoom = new ZoomImageView(this);
        zoom.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(zoom, new FrameLayout.LayoutParams(-1, -1));

        // Tombol kembali di pojok kiri atas, di bawah status bar
        FrameLayout top = new FrameLayout(this);
        top.setFitsSystemWindows(true);
        FrameLayout back = new FrameLayout(this);
        back.setBackground(M3.ripple(0x66000000, 24));
        ImageView bi = new ImageView(this);
        bi.setImageDrawable(new IconDrawable(IconDrawable.BACK, 0xFFFFFFFF));
        back.addView(bi, new FrameLayout.LayoutParams(M3.dp(24), M3.dp(24), Gravity.CENTER));
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(M3.dp(48), M3.dp(48), Gravity.START | Gravity.TOP);
        bl.leftMargin = M3.dp(12);
        bl.topMargin = M3.dp(12);
        top.addView(back, bl);
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        setContentView(root);

        // Decode seukuran layar (x2 untuk ketajaman saat di-zoom)
        ImageLoader.loadUri(this, uri, getResources().getDisplayMetrics().widthPixels * 2, bmp -> {
            if (bmp != null) zoom.setImageBitmap(bmp);
            else Toast.makeText(this, "Gambar tidak bisa dibuka", Toast.LENGTH_SHORT).show();
        });
    }
}
