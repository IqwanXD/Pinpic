package com.pinpic.app.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.pinpic.app.Account;
import com.pinpic.app.FontManager;
import com.pinpic.app.ImageLoader;
import com.pinpic.app.InoInputLayout;
import com.pinpic.app.M3;
import com.pinpic.app.Prefs;
import com.pinpic.app.R;
import com.pinpic.app.VecIcon;

/** Edit profil: foto (dipotong persegi), nama, dan bio. Semuanya disimpan lokal di perangkat. */
public final class EditProfileActivity extends Activity {

    private static final int REQ_PICK = 1;
    private static final int REQ_CROP = 2;
    private static final int NAME_MAX = 30;
    private static final int BIO_MAX = 150;
    private static final int AVATAR_DP = 112;

    private ImageView avatarImg;
    private TextView avatarLetter;
    private InoInputLayout nameIn, bioIn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Prefs.init(this);
        M3.init(this);
        getWindow().setStatusBarColor(M3.background);
        getWindow().setNavigationBarColor(M3.background);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(M3.background);
        col.setFitsSystemWindows(true);

        // Bar atas: kembali · judul · simpan. Tombol kembali dibuat sama dengan di Pengaturan.
        FrameLayout bar = new FrameLayout(this);
        ImageView back = new ImageView(this);
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setImageDrawable(VecIcon.of(this, R.drawable.ic_back, M3.onSurface, M3.dp(24)));
        RippleDrawable br = new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, null);
        br.setRadius(M3.dp(20));
        back.setBackground(br);
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(M3.dp(48), M3.dp(48),
                Gravity.START | Gravity.CENTER_VERTICAL);
        bl.leftMargin = M3.dp(4);
        bar.addView(back, bl);

        TextView title = M3.text(this, "Edit profil", 20, M3.onSurface, true);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -2, Gravity.START | Gravity.CENTER_VERTICAL);
        tl.leftMargin = M3.dp(64);
        bar.addView(title, tl);

        TextView save = M3.text(this, "Simpan", 16, M3.primary, true);
        save.setPadding(M3.dp(16), M3.dp(14), M3.dp(16), M3.dp(14));
        save.setOnClickListener(v -> saveProfile());
        bar.addView(save, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));
        col.addView(bar, new LinearLayout.LayoutParams(-1, M3.dp(56)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(M3.dp(16), M3.dp(8), M3.dp(16), M3.dp(24));

        // Avatar: lingkaran, ketuk untuk memilih foto baru
        FrameLayout avatarBox = new FrameLayout(this);
        avatarBox.setBackground(M3.shape(M3.primaryContainer, AVATAR_DP / 2f));
        M3.round(avatarBox, AVATAR_DP / 2f);
        avatarBox.setClickable(true);
        avatarBox.setOnClickListener(v -> pickAvatar());
        avatarLetter = M3.text(this, "P", 40, M3.onPrimaryContainer, true);
        avatarLetter.setGravity(Gravity.CENTER);
        avatarBox.addView(avatarLetter, new FrameLayout.LayoutParams(-1, -1));
        avatarImg = new ImageView(this);
        avatarImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarImg.setVisibility(View.GONE);
        avatarBox.addView(avatarImg, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout avatarCol = new LinearLayout(this);
        avatarCol.setOrientation(LinearLayout.VERTICAL);
        avatarCol.setGravity(Gravity.CENTER_HORIZONTAL);
        avatarCol.addView(avatarBox, new LinearLayout.LayoutParams(M3.dp(AVATAR_DP), M3.dp(AVATAR_DP)));
        TextView change = M3.text(this, "Ubah foto", 15, M3.primary, true);
        change.setPadding(M3.dp(8), M3.dp(12), M3.dp(8), M3.dp(4));
        change.setOnClickListener(v -> pickAvatar());
        avatarCol.addView(change);
        content.addView(avatarCol, new LinearLayout.LayoutParams(-1, -2));

        nameIn = input("Nama", NAME_MAX, false);
        content.addView(nameIn, spaced(M3.dp(24)));

        bioIn = input("Bio", BIO_MAX, true);
        content.addView(bioIn, spaced(M3.dp(16)));

        TextView note = M3.text(this, "Foto, nama, dan bio hanya disimpan di perangkat ini.",
                13, M3.onSurfaceVariant, false);
        content.addView(note, Ui.lp(-1, -2, M3.dp(8), M3.dp(16), M3.dp(8), 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(content);
        col.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        nameIn.setText(Prefs.name());
        bioIn.setText(Prefs.bio());
        refreshAvatar();

        setContentView(col);
        FontManager.attach(col);
    }

    private static LinearLayout.LayoutParams spaced(int topPx) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = topPx;
        return p;
    }

    /** Field dengan label mengambang (InoInputLayout). Bio banyak baris, nama satu baris. */
    private InoInputLayout input(String label, int max, boolean multi) {
        InoInputLayout in = new InoInputLayout(this);
        in.setLabelText(label);
        in.setColors(M3.primary, M3.onSurfaceVariant);
        in.setTextColor(M3.onSurface);
        in.setTextSize(16);
        in.setCornerRadius(16);
        in.setIsMultiline(multi);
        if (multi) in.setLines(3);
        in.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        return in;
    }

    private void pickAvatar() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Pilih foto profil"), REQ_PICK);
        } catch (Exception e) {
            M3.toast(this, "Tidak ada aplikasi galeri");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK) return;
        if (req == REQ_PICK && data != null && data.getData() != null) {
            Intent c = new Intent(this, AvatarCropActivity.class);
            c.setData(data.getData());
            c.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(c, REQ_CROP);
        } else if (req == REQ_CROP) {
            refreshAvatar();
        }
    }

    private void refreshAvatar() {
        String n = Prefs.name();
        avatarLetter.setText(n.isEmpty() ? "P" : n.substring(0, 1).toUpperCase());
        if (!Account.hasAvatar(this)) {
            avatarImg.setVisibility(View.GONE);
            avatarLetter.setVisibility(View.VISIBLE);
            return;
        }
        ImageLoader.loadUri(this, Account.avatarUri(this), M3.dp(AVATAR_DP) * 2, bmp -> {
            if (bmp == null) return;
            avatarImg.setImageBitmap(bmp);
            avatarImg.setVisibility(View.VISIBLE);
            avatarLetter.setVisibility(View.GONE);
        });
    }

    private void saveProfile() {
        Prefs.setName(nameIn.getText().trim());
        Prefs.setBio(bioIn.getText().trim());
        M3.toast(this, "Profil disimpan");
        setResult(RESULT_OK);
        finish();
    }
}
