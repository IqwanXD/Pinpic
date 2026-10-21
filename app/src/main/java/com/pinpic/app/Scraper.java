package com.pinpic.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Menggabungkan semua sumber yang aktif (lihat Sources.ALL dan pengaturan):
 * diambil paralel, hasilnya selang-seling antar sumber, duplikat dibuang.
 * Beranda (query kosong): topik dan halaman diacak setiap pemuatan, kata yang sering dicari ikut masuk,
 * dan gambar yang pernah tampil di sesi sebelumnya tidak diulang.
 */
public final class Scraper {
    private static final String[] TOPICS = {
            "aesthetic wallpaper", "cosplay", "anime art", "cute cats", "street photography",
            "interior design", "landscape photography", "fashion outfit", "digital illustration",
            "food photography", "modern architecture", "tattoo ideas",
            "home decor", "vintage posters", "botanical illustration", "pastel aesthetic",
            "car photography", "japanese garden", "typography quotes", "travel destinations",
            "abstract art", "watercolor painting", "sneakers street style", "night city lights",
            "pet portraits", "minimalist room", "mountain landscape", "ocean waves",
            "retro gaming art", "graffiti art", "macro flowers", "dessert recipes",
            "vintage cameras", "space nebula", "moody forest", "furniture design"};
    private static final int PERSONAL_TOPICS = 5;
    /** Jumlah halaman acak per topik untuk beranda. Kombinasi topik x halaman membuat hasil tetap beragam. */
    private static final int FEED_PAGES = 8;
    /** Batas waktu menunggu semua sumber untuk satu halaman. Sumber yang lewat batas dilewati dulu. */
    private static final long SOURCE_WAIT_MS = 6000;

    private final Sources sources = new Sources();
    private final Set<String> seen = Collections.synchronizedSet(new HashSet<String>());
    private final ExecutorService pool = Executors.newFixedThreadPool(16);
    private final Random rnd = new Random();
    private List<String> topics = buildTopics();

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

    /**
     * Kunci identitas gambar untuk membuang duplikat. Sumber yang berbeda sering memberi foto yang sama
     * dengan URL berbeda (parameter ukuran, domain cermin, atau CDN lain), jadi yang dibandingkan adalah
     * nama berkas tanpa query string dan ekstensi, bukan URL lengkap.
     */
    static String keyOf(String url) {
        if (url == null) return "";
        String u = url;
        int cut = u.indexOf('?');
        if (cut >= 0) u = u.substring(0, cut);
        cut = u.indexOf('#');
        if (cut >= 0) u = u.substring(0, cut);
        int slash = u.lastIndexOf('/');
        String stem = (slash >= 0 ? u.substring(slash + 1) : u).toLowerCase(Locale.ROOT);
        stem = stem.replaceAll("\\.(jpe?g|png|webp|gif|bmp)$", "");
        stem = stem.replaceAll("_[a-z]$", "");   // akhiran ukuran, mis. flickr _b / _z
        return stem.length() >= 8 ? "img:" + stem : u;
    }

    /** Dipanggil dari background thread. query kosong = beranda. */
    public List<Pin> fetch(String query, int page) {
        final boolean feed = query.isEmpty();
        if (feed && page == 0) topics = buildTopics();   // setiap beranda dibuka: topik dihitung ulang

        List<Callable<List<Pin>>> jobs = new ArrayList<>();
        for (final Sources.Src s : Sources.ALL) {
            if (!Prefs.sourceOn(s.id)) continue;
            // Beranda: tiap sumber mendapat topik dan halaman acak sendiri, jadi satu pemuatan
            // berisi campuran banyak topik (bukan satu topik saja) dan tidak pernah habis.
            final String q = feed ? topics.get(rnd.nextInt(topics.size())) : query;
            final int p = feed ? rnd.nextInt(FEED_PAGES) : page;
            jobs.add(() -> sources.run(s.id, q, p, feed));
        }

        List<Pin> out = new ArrayList<>();
        if (jobs.isEmpty()) return out;

        // Setiap sumber dikirim sekaligus; hasil diambil begitu siap (bukan menunggu yang paling lambat).
        List<Future<List<Pin>>> futures = new ArrayList<>();
        for (Callable<List<Pin>> job : jobs) futures.add(pool.submit(job));
        List<List<Pin>> lists = new ArrayList<>();
        long end = System.currentTimeMillis() + SOURCE_WAIT_MS;
        for (Future<List<Pin>> f : futures) {
            long left = Math.max(0, end - System.currentTimeMillis());
            try { lists.add(f.get(left, TimeUnit.MILLISECONDS)); }
            catch (TimeoutException e) { f.cancel(true); }
            catch (Exception ignored) { }
        }

        boolean more = true;
        for (int i = 0; more; i++) { // selang-seling antar sumber
            more = false;
            for (List<Pin> l : lists) {
                if (i < l.size()) {
                    more = true;
                    Pin pin = l.get(i);
                    String key = keyOf(pin.full);
                    if (seen.add(key) && (!feed || !SeenHistory.has(key))) out.add(pin);
                }
            }
        }

        if (feed) {
            List<String> keys = new ArrayList<>();
            for (Pin pin : out) keys.add(keyOf(pin.full));
            SeenHistory.addAll(keys);
        }
        return out;
    }
}
