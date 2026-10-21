package com.pinpic.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Pengaturan kecil, disimpan di SharedPreferences "pinpic". */
public final class Prefs {
    private static SharedPreferences sp;

    private Prefs() { }

    /** Sumber yang aktif secara default (pengguna baru). */
    private static final String DEFAULT_ON = "pinterest";

    public static void init(Context c) {
        SeenHistory.init(c);
        if (sp != null) return;
        sp = c.getApplicationContext().getSharedPreferences("pinpic", Context.MODE_PRIVATE);
        // Default baru: hanya Pinterest yang aktif. Pilihan lama di-reset sekali saja.
        if (!sp.getBoolean("defaults_v2", false)) {
            SharedPreferences.Editor e = sp.edit();
            for (String k : sp.getAll().keySet()) if (k.startsWith("src_")) e.remove(k);
            e.putBoolean("defaults_v2", true).apply();
        }
    }

    /** Sumber pencarian aktif? Default hanya Pinterest. Sebelum init() ikut default. */
    public static boolean sourceOn(String id) {
        boolean def = DEFAULT_ON.equals(id);
        return sp == null ? def : sp.getBoolean("src_" + id, def);
    }

    public static void setSourceOn(String id, boolean on) {
        if (sp != null) sp.edit().putBoolean("src_" + id, on).apply();
    }

    public static int sourcesOn() {
        int n = 0;
        for (Sources.Src s : Sources.ALL) if (sourceOn(s.id)) n++;
        return n;
    }

    /** Tema gelap (default, sesuai tampilan sebelumnya). false = terang. */
    public static boolean darkTheme() {
        return sp == null || sp.getBoolean("theme_dark", true);
    }

    public static void setDarkTheme(boolean on) {
        if (sp != null) sp.edit().putBoolean("theme_dark", on).apply();
    }

    /** Warna dari wallpaper (Material You, Android 12+). Default aktif. */
    public static boolean dynamicColor() {
        return sp == null || sp.getBoolean("dynamic_color", true);
    }

    public static void setDynamicColor(boolean on) {
        if (sp != null) sp.edit().putBoolean("dynamic_color", on).apply();
    }

    /** Warna aksen pilihan sendiri (ARGB). 0 = palet bawaan Pinpic. */
    public static int appColor() {
        return sp == null ? 0 : sp.getInt("app_color", 0);
    }

    public static void setAppColor(int argb) {
        if (sp != null) sp.edit().putInt("app_color", argb).apply();
    }

    public static boolean storageOk() {
        return sp != null && sp.getBoolean("storage_ok", false);
    }

    public static void setStorageOk() {
        if (sp != null) sp.edit().putBoolean("storage_ok", true).apply();
    }

    /** URI folder unduhan pilihan pengguna (SAF tree). null = pakai Pictures/Pinpic bawaan. */
    public static String downloadTree() {
        return sp == null ? null : sp.getString("download_tree", null);
    }

    public static void setDownloadTree(String uri) {
        if (sp != null) sp.edit().putString("download_tree", uri).apply();
    }

    /** Profil lokal: nama dan bio. Fotonya disimpan sebagai file (lihat Account). */
    public static String name() {
        return sp == null ? "" : sp.getString("profile_name", "");
    }

    public static void setName(String v) {
        if (sp != null) sp.edit().putString("profile_name", v).apply();
    }

    public static String bio() {
        return sp == null ? "" : sp.getString("profile_bio", "");
    }

    public static void setBio(String v) {
        if (sp != null) sp.edit().putString("profile_bio", v).apply();
    }

    private static final String KW_COUNTS = "kw_counts";
    private static final int KW_MAX = 100;

    /** Catat satu pencarian dari kolom Cari. Dipakai untuk rekomendasi beranda. */
    public static void recordSearch(String q) {
        if (sp == null || q == null) return;
        String k = q.trim().toLowerCase(Locale.ROOT);
        if (k.isEmpty() || k.length() > 60) return;
        try {
            JSONObject o = new JSONObject(sp.getString(KW_COUNTS, "{}"));
            o.put(k, o.optInt(k, 0) + 1);
            if (o.length() > KW_MAX) {
                String weakest = null;
                int min = Integer.MAX_VALUE;
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String key = it.next();
                    int v = o.optInt(key, 0);
                    if (v < min) { min = v; weakest = key; }
                }
                if (weakest != null) o.remove(weakest);
            }
            sp.edit().putString(KW_COUNTS, o.toString()).apply();
        } catch (JSONException ignored) { }
    }

    /** Kata kunci yang paling sering dicari (terbanyak dulu). */
    public static List<String> topKeywords(int n) {
        List<String> out = new ArrayList<>();
        if (sp == null) return out;
        try {
            final JSONObject o = new JSONObject(sp.getString(KW_COUNTS, "{}"));
            List<String> keys = new ArrayList<>();
            Iterator<String> it = o.keys();
            while (it.hasNext()) keys.add(it.next());
            Collections.sort(keys, (a, b) -> Integer.compare(o.optInt(b, 0), o.optInt(a, 0)));
            for (int i = 0; i < keys.size() && out.size() < n; i++) out.add(keys.get(i));
        } catch (JSONException ignored) { }
        return out;
    }
}
