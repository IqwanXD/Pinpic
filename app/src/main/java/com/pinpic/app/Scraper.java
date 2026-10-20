package com.pinpic.app;

import java.util.ArrayList;
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
 */
public final class Scraper {
    private static final String[] TOPICS = {
            "aesthetic wallpaper", "cosplay", "anime art", "cute cats", "street photography",
            "interior design", "landscape photography", "fashion outfit", "digital illustration",
            "food photography", "modern architecture", "tattoo ideas"};

    private final Sources sources = new Sources();
    private final Set<String> seen = Collections.synchronizedSet(new HashSet<String>());
    private final ExecutorService pool = Executors.newFixedThreadPool(8);
    private int topicOffset = new Random().nextInt(TOPICS.length);

    public void reset() {
        seen.clear();
        sources.reset();
        topicOffset = new Random().nextInt(TOPICS.length);
    }

    /** Dipanggil dari background thread. query kosong = beranda (topik bergilir). */
    public List<Pin> fetch(String query, int page) {
        final boolean feed = query.isEmpty();
        final String q = feed ? TOPICS[(topicOffset + page) % TOPICS.length] : query;
        final int p = feed ? page / TOPICS.length : page;

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
                    if (seen.add(pin.full)) out.add(pin);
                }
            }
        }
        return out;
    }
}
