package com.pinpic.app;

public final class Pin {
    public final String thumb, full, link;
    public float ratio; // tinggi/lebar, 0 = belum diketahui
    public String title = ""; // judul dari sumber, boleh kosong

    public Pin(String thumb, String full, String link, float ratio) {
        this.thumb = thumb;
        this.full = full;
        this.link = link;
        this.ratio = ratio;
    }
}
