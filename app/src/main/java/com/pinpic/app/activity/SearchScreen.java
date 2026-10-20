package com.pinpic.app.activity;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.graphics.drawable.GradientDrawable;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.pinpic.app.IconDrawable;
import com.pinpic.app.M3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Cari: kolom pencarian + saran kata kunci (popup mengambang di bawah kolom) + hasil gambar. */
public final class SearchScreen implements Screen {

    /** Contoh kata kunci untuk hint yang berganti acak saat kolom kosong. */
    private static final String[] HINTS = {
            "pemandangan gunung", "kucing lucu", "dekorasi kamar", "resep masakan",
            "wallpaper minimalis", "ilustrasi anime", "arsitektur modern", "bunga musim semi"
    };
    /** Kata tambahan untuk saran yang dibuat dari teks yang diketik user. */
    private static final String[] MODIFIERS = {
            "estetik", "minimalis", "HD", "aesthetic", "terbaru", "lucu", "keren"
    };
    private static final long HINT_INTERVAL_MS = 2500;
    private static final int SUGGEST_COUNT = 4;

    private final Activity act;
    private final Random rnd = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable rotateHint = new Runnable() {
        @Override public void run() {
            if (input != null && input.length() == 0) input.setHint(HINTS[rnd.nextInt(HINTS.length)]);
            handler.postDelayed(this, HINT_INTERVAL_MS);
        }
    };

    private final LinearLayout root;
    private final LinearLayout header;
    private final LinearLayout bar;
    private final View clearBtn;
    private final TextView hint;
    private final FeedView feed;
    private final PopupWindow popup;
    private final LinearLayout suggestList;
    private EditText input;

    public SearchScreen(Activity act, ScreenHost host) {
        this.act = act;

        root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);

        header = new LinearLayout(act);
        header.setOrientation(LinearLayout.VERTICAL);

        TextView title = M3.text(act, "Cari", 34, M3.primary, true);
        title.setLetterSpacing(-0.02f);
        header.addView(title, Ui.lp(-2, -2, M3.dp(4), 0, 0, 0));

        bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(M3.shape(M3.surfaceContainerHigh, 28));
        bar.setPadding(M3.dp(18), 0, M3.dp(8), 0);
        bar.addView(Ui.icon(act, IconDrawable.SEARCH, M3.onSurfaceVariant),
                new LinearLayout.LayoutParams(M3.dp(22), M3.dp(22)));

        input = new EditText(act);
        input.setBackground(null);
        input.setHint(HINTS[rnd.nextInt(HINTS.length)]);
        input.setHintTextColor(M3.onSurfaceVariant);
        input.setTextColor(M3.onSurface);
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(M3.dp(12), 0, M3.dp(8), 0);
        bar.addView(input, new LinearLayout.LayoutParams(0, -1, 1f));

        clearBtn = Ui.iconButton(act, IconDrawable.CLOSE, 0, M3.onSurfaceVariant, 40, 20,
                () -> input.setText(""));
        clearBtn.setVisibility(View.GONE);
        bar.addView(clearBtn, new LinearLayout.LayoutParams(M3.dp(40), M3.dp(40)));

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                clearBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                updateSuggestions(s.toString().trim());
            }
        });
        input.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                submit(v.getText().toString().trim());
                return true;
            }
            return false;
        });
        header.addView(bar, Ui.lp(-1, M3.dp(56), 0, M3.dp(10), 0, M3.dp(14)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout content = new FrameLayout(act);
        feed = new FeedView(act, host);
        feed.setVisibility(View.GONE);
        content.addView(feed, new FrameLayout.LayoutParams(-1, -1));

        hint = M3.text(act, "Ketik kata kunci lalu tekan Cari di keyboard.", 15, M3.onSurfaceVariant, false);
        hint.setGravity(Gravity.CENTER);
        content.addView(hint, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        // Gradasi di tepi atas daftar: gambar yang lewat di bawah kolom pencarian memudar.
        View fade = new View(act);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{M3.background, M3.background & 0x00FFFFFF}));
        content.addView(fade, new FrameLayout.LayoutParams(-1, M3.dp(32), Gravity.TOP));

        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Popup saran: mengambang di bawah kolom pencarian (gaya YouTube).
        // Tidak bisa fokus, jadi keyboard tetap di kolom pencarian.
        suggestList = new LinearLayout(act);
        suggestList.setOrientation(LinearLayout.VERTICAL);
        suggestList.setPadding(0, M3.dp(6), 0, M3.dp(6));
        suggestList.setBackground(M3.shape(M3.surfaceContainerHigh, 16));
        popup = new PopupWindow(suggestList, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        popup.setFocusable(false);
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(M3.dp(8));
    }

    /** Saran dibuat dari teks user, misalnya "kucing" menjadi "kucing lucu" atau "kucing HD". */
    private void updateSuggestions(String typed) {
        if (typed.isEmpty() || bar.getWidth() <= 0) {
            dismissSuggestions();
            return;
        }
        suggestList.removeAllViews();
        List<String> pool = new ArrayList<>(Arrays.asList(MODIFIERS));
        Collections.shuffle(pool, rnd);
        for (int i = 0; i < SUGGEST_COUNT && i < pool.size(); i++) {
            suggestList.addView(suggestRow(typed + " " + pool.get(i)));
        }
        int w = bar.getWidth();
        popup.setWidth(w);
        if (popup.isShowing()) popup.update(bar, 0, M3.dp(6), w, ViewGroup.LayoutParams.WRAP_CONTENT);
        else popup.showAsDropDown(bar, 0, M3.dp(6));
    }

    private View suggestRow(final String s) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(M3.dp(16), M3.dp(12), M3.dp(16), M3.dp(12));
        row.setBackground(M3.ripple(0, 0));
        row.addView(Ui.icon(act, IconDrawable.SEARCH, M3.onSurfaceVariant),
                new LinearLayout.LayoutParams(M3.dp(20), M3.dp(20)));
        TextView t = M3.text(act, s, 15, M3.onSurface, false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = M3.dp(16);
        row.addView(t, tl);
        row.setOnClickListener(v -> {
            input.setText(s);
            input.setSelection(s.length());
            submit(s);
        });
        return row;
    }

    private void dismissSuggestions() {
        if (popup.isShowing()) popup.dismiss();
    }

    private void submit(String q) {
        hideKeyboard();
        input.clearFocus();
        dismissSuggestions();
        if (q.isEmpty()) return;
        hint.setVisibility(View.GONE);
        feed.setVisibility(View.VISIBLE);
        feed.search(q);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    @Override public View getView() { return root; }

    @Override public void onShown() { handler.removeCallbacks(rotateHint); handler.post(rotateHint); }

    @Override public void onHidden() {
        handler.removeCallbacks(rotateHint);
        dismissSuggestions();
        hideKeyboard();
    }

    @Override public void setInsets(int top, int bottom, int left, int right) {
        header.setPadding(M3.dp(16) + left, top + M3.dp(8), M3.dp(16) + right, 0);
        feed.setInsets(left, right, bottom);
    }
}
