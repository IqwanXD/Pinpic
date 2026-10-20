package com.pinpic.app.activity;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.pinpic.app.Account;
import com.pinpic.app.CropImageView;
import com.pinpic.app.FontManager;
import com.pinpic.app.M3;
import com.pinpic.app.Prefs;

import java.io.InputStream;

/** Potong foto profil jadi persegi (512 px), lalu simpan sebagai avatar.png di folder akun lokal. */
public final class AvatarCropActivity extends Activity {

    private static final int MAX_DECODE_PX = 2048;

    private CropImageView crop;
    private TextView save;
    private boolean saving, handed;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        M3.init(this);
        getWindow().setStatusBarColor(M3.background);
        getWindow().setNavigationBarColor(M3.background);

        final Uri uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(M3.background);
        col.setFitsSystemWindows(true);

        // Bar atas: Batal · judul · Simpan
        FrameLayout bar = new FrameLayout(this);
        TextView cancel = M3.text(this, "Batal", 16, M3.onSurfaceVariant, false);
        cancel.setPadding(M3.dp(16), M3.dp(14), M3.dp(16), M3.dp(14));
        cancel.setOnClickListener(v -> finish());
        bar.addView(cancel, new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL));

        TextView title = M3.text(this, "Atur foto profil", 17, M3.onSurface, true);
        bar.addView(title, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        save = M3.text(this, "Simpan", 16, M3.primary, true);
        save.setPadding(M3.dp(16), M3.dp(14), M3.dp(16), M3.dp(14));
        save.setOnClickListener(v -> onSave());
        bar.addView(save, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));
        col.addView(bar, new LinearLayout.LayoutParams(-1, M3.dp(56)));

        crop = new CropImageView(this);
        crop.lockSquareAspect();      // kotak crop selalu persegi
        crop.setOutputSquare(true);   // hasil 512 x 512
        crop.setCropListener(new CropImageView.CropListener() {
            @Override public void onCropReady(Bitmap cropped) {
                handed = true;
                writeAvatar(cropped);
            }

            @Override public void onCropCancelled() { }
        });
        col.addView(crop, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(col);
        FontManager.attach(col);

        // Decode di background: foto kamera bisa besar.
        new Thread(() -> {
            final Bitmap bmp = decode(uri);
            runOnUiThread(() -> {
                if (bmp == null) {
                    Toast.makeText(this, "Gambar tidak bisa dibuka", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    crop.setImage(bmp);
                }
            });
        }).start();
    }

    private void onSave() {
        if (saving) return;
        saving = true;
        handed = false;
        crop.doCrop();                 // memanggil onCropReady (sinkron) bila area valid
        if (!handed) {
            saving = false;
            Toast.makeText(this, "Gambar belum siap", Toast.LENGTH_SHORT).show();
        } else {
            save.setAlpha(0.4f);
        }
    }

    private void writeAvatar(final Bitmap bmp) {
        new Thread(() -> {
            final boolean ok = Account.saveAvatar(AvatarCropActivity.this, bmp);
            runOnUiThread(() -> {
                if (ok) {
                    Toast.makeText(AvatarCropActivity.this, "Foto profil disimpan", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    saving = false;
                    save.setAlpha(1f);
                    Toast.makeText(AvatarCropActivity.this, "Gagal menyimpan foto", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    /** Decode seukuran aman (tidak lebih dari MAX_DECODE_PX), lalu putar sesuai EXIF. */
    private Bitmap decode(Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;

            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / s > MAX_DECODE_PX) s *= 2;

            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bmp;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in, null, o);
            }
            return bmp == null ? null : rotateByExif(uri, bmp);
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap rotateByExif(Uri uri, Bitmap bmp) {
        int deg;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            ExifInterface ex = new ExifInterface(in);
            int o = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (o) {
                case ExifInterface.ORIENTATION_ROTATE_90: deg = 90; break;
                case ExifInterface.ORIENTATION_ROTATE_180: deg = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: deg = 270; break;
                default: return bmp;
            }
        } catch (Exception e) {
            return bmp;
        }
        Matrix m = new Matrix();
        m.postRotate(deg);
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
    }
}
