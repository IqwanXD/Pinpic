package com.pinpic.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Menggabungkan semua sumber yang aktif (lihat Sources.ALL dan pengaturan):
 * diambil paralel, hasilnya selang-seling antar sumber, duplikat dibuang.
 * Beranda: topik diselang-seling antara kata yang sering dicari dan topik bawaan,
 * dan gambar yang pernah tampil di sesi sebelumnya tidak diulang.
 */
public final class Scraper {
    private static final String[] TOPICS = {
            "aesthetic wallpaper", "cosplay", "anime art", "cute cats", "street photography",
            "interior design", "landscape photography", "fashion outfit", "digital illustration",
            "food photography", "modern architecture", "tattoo ideas"};
    private static final int PERSONAL_TOPICS = 5;

    private final Sources sources = new Sources();
    private final Set<String> seen = Collections.synchronizedSet(new HashSet<String>());
    private final ExecutorService pool = Executors.newFixedThreadPool(8);
    private final Random rnd = new Random();
    private List<String> topics = buildTopics();
    private int topicOffset;

    public void reset() {
        seen.clear();
        sources.reset();
    }

    /** Topik beranda: kata sendiri selang-seling dengan topik bawaan. */
    private List<String> buildTopics() {
        List<String> mine = Prefs.topKeywords(PERSONAL_TOPICS);
        List<String> base = new ArrayList<>(Arrays.asList(TOPICS));
        Collections.shuffle(base, rnd);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < mine.size() || i < base.size(); i++) {
            if (i < mine.size()) out.add(mine.get(i));
            if (i < base.size()) out.add(base.get(i));
        }
        return out;
    }

    /** Dipanggil dari background thread. query kosong = beranda. */
    public List<Pin> fetch(String query, int page) {
        final boolean feed = query.isEmpty();
        if (feed && page == 0) {
            // Setiap beranda dimuat dari awal: topik dan urutan dihitung ulang (kata sering dicari ikut masuk).
            topics = buildTopics();
            topicOffset = rnd.nextInt(topics.size());
        }
        final String q = feed ? topics.get((topicOffset + page) % topics.size()) : query;
        final int p = feed ? page / topics.size() : page;

        List<Callable<List<Pin>>> jobs = new ArrayList<>();
        for (final Sources.Src s : Sources.ALL) {
            if (!Prefs.sourceOn(s.id)) continue;
            jobs.add(() -> sources.run(s.id, q, p, feed));
        }

        List<Pin> out = new ArrayList<>();
        if (jobs.isEmpty()) return out;

        List<List<Pin>> lists = new ArrayList<>();
        try {
            for (Future<List<Pin>> f : pool.invokeAll(jobs, 30, TimeUnit.SECONDS)) {
                try { lists.add(f.get()); } catch (Exception ignored) { }
            }
        } catch (InterruptedException ignored) { }

        boolean more = true;
        for (int i = 0; more; i++) { // selang-seling antar sumber
            more = false;
            for (List<Pin> l : lists) {
                if (i < l.size()) {
                    more = true;
                    Pin pin = l.get(i);
                    if (seen.add(pin.full) && (!feed || !SeenHistory.has(pin.full))) out.add(pin);
                }
            }
        }

        if (feed) {
            List<String> keys = new ArrayList<>();
            for (Pin pin : out) keys.add(pin.full);
            SeenHistory.addAll(keys);
        }
        return out;
    }
}
