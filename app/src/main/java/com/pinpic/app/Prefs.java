package com.pinpic.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Pengaturan kecil, disimpan di SharedPreferences "pinpic". */
public final class Prefs {
    private static SharedPreferences sp;

    private Prefs() { }

    /** Sumber yang aktif secara default (pengguna baru). */
    private static final String DEFAULT_ON = "pinterest";

    public static void init(Context c) {
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
}
