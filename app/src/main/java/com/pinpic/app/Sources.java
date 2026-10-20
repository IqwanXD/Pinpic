package com.pinpic.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semua sumber gambar. Tiap fungsi mengembalikan Pin untuk (query, halaman).
 * Sumber yang gagal, diblokir, atau berubah format cukup mengembalikan daftar kosong
 * (atau melempar exception yang ditangkap Scraper), tidak menghentikan sumber lain.
 *
 * Mode aman: semua sumber diminta SFW (SafeSearch / purity / over_18 difilter).
 */
public final class Sources {

    public static final class Src {
        public final String id, name;
        public Src(String id, String name) { this.id = id; this.name = name; }
    }

    /** Daftar sumber. id dipakai untuk pengaturan (Prefs.sourceOn) dan switch di run(). */
    public static final Src[] ALL = {
            new Src("bing", "Bing Images"),
            new Src("ddg", "DuckDuckGo Images"),
            new Src("google", "Google Images"),
            new Src("yandex", "Yandex Images"),
            new Src("brave", "Brave Images"),
            new Src("pinterest", "Pinterest"),
            new Src("openverse", "Openverse"),
            new Src("commons", "Wikimedia Commons"),
            new Src("unsplash", "Unsplash"),
            new Src("flickr", "Flickr"),
            new Src("wallhaven", "Wallhaven"),
            new Src("reddit", "Reddit"),
    };

    // State per sumber (direset saat pencarian baru)
    private String ddgQuery, ddgVqd;
    private String pinQuery = "", pinBookmark = "";
    private String redditAfter = "";

    void reset() {
        ddgQuery = null;
        ddgVqd = null;
        pinQuery = "";
        pinBookmark = "";
        redditAfter = "";
    }

    /** Dipanggil dari background thread. feed = beranda (tanpa query pengguna). */
    List<Pin> run(String id, String q, int p, boolean feed) throws Exception {
        switch (id) {
            case "bing": return bing(q, p);
            case "ddg": return ddg(q, p);
            case "google": return google(q, p);
            case "yandex": return yandex(q, p);
            case "brave": return brave(q, p);
            case "pinterest": return pinterest(q, p);
            case "openverse": return openverse(q, p);
            case "commons": return commons(q, p);
            case "unsplash": return unsplash(q, p);
            case "flickr": return flickr(q, p);
            case "wallhaven": return wallhaven(q, p);
            case "reddit": return reddit(q, !feed);
            default: return new ArrayList<>();
        }
    }

    // ------------------------------------------------------------ helper
    private static String unesc(String s) {
        return s.replace("\\/", "/").replace("\\u0026", "&").replace("\\u003d", "=").replace("&amp;", "&");
    }

    private static boolean isHttp(String u) { return u != null && u.startsWith("http"); }

    /** tinggi/lebar, 0 = tidak diketahui (loader akan mengukur sendiri). */
    private static float ratio(int w, int h) { return w > 0 && h > 0 ? h / (float) w : 0f; }

    /** Ambil semua kecocokan regex (grup 1) dari html menjadi Pin tanpa rasio. */
    private static List<Pin> regexPins(String html, String regex) {
        Matcher m = Pattern.compile(regex).matcher(html);
        List<Pin> out = new ArrayList<>();
        while (m.find()) {
            String u = m.group(1);
            if (isHttp(u)) out.add(new Pin(u, u, u, 0f));
        }
        return out;
    }

    // ------------------------------------------------------------ Bing Images (HTML, SafeSearch strict)
    private List<Pin> bing(String q, int p) throws Exception {
        String url = "https://www.bing.com/images/async?q=" + Net.enc(q)
                + "&first=" + (p * 35 + 1) + "&count=35&mmasync=1";
        String html = Net.string(url, Net.DESKTOP_UA, null, "SRCHHPGUSR=ADLT=STRICT");
        html = html.replace("&quot;", "\"").replace("&amp;", "&");
        String[] parts = html.split("class=\"iusc\"");
        List<Pin> out = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String c = parts[i];
            if (c.length() > 2500) c = c.substring(0, 2500);
            String murl = grab(c, "murl"), turl = grab(c, "turl"), purl = grab(c, "purl");
            if (!isHttp(murl)) continue;
            out.add(new Pin(turl != null ? turl : murl, murl, purl != null ? purl : murl, 0f));
        }
        return out;
    }

    private static String grab(String s, String key) {
        Matcher m = Pattern.compile("\"" + key + "\":\"(.*?)\"").matcher(s);
        return m.find() ? m.group(1).replace("\\/", "/").replace("\\u0026", "&") : null;
    }

    // ------------------------------------------------------------ DuckDuckGo Images (vqd + i.js JSON)
    private List<Pin> ddg(String q, int p) throws Exception {
        if (ddgVqd == null || !q.equals(ddgQuery)) {
            String html = Net.string("https://duckduckgo.com/?q=" + Net.enc(q) + "&iax=images&ia=images",
                    Net.DESKTOP_UA, "https://duckduckgo.com/", null);
            Matcher m = Pattern.compile("vqd=['\"]?([\\d-]+)").matcher(html);
            if (!m.find()) throw new IOException("vqd DuckDuckGo tidak ditemukan");
            ddgVqd = m.group(1);
            ddgQuery = q;
        }
        String js = Net.string("https://duckduckgo.com/i.js?l=wt-wt&o=json&p=1&f=,,,,,&q=" + Net.enc(q)
                + "&vqd=" + ddgVqd + "&s=" + (p * 100), Net.DESKTOP_UA, "https://duckduckgo.com/", null);
        JSONArray a = new JSONObject(js).getJSONArray("results");
        List<Pin> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String full = o.optString("image");
            if (!isHttp(full)) continue;
            String thumb = o.optString("thumbnail", full);
            out.add(new Pin(isHttp(thumb) ? thumb : full, full, o.optString("url", full),
                    ratio(o.optInt("width"), o.optInt("height"))));
        }
        return out;
    }

    // ------------------------------------------------------------ Google Images (HTML, safe=active)
    private List<Pin> google(String q, int p) throws Exception {
        String html = unesc(Net.string("https://www.google.com/search?tbm=isch&safe=active&hl=id&gl=id&q="
                + Net.enc(q) + "&start=" + (p * 20), Net.DESKTOP_UA, "https://www.google.com/", "CONSENT=YES+"));
        return regexPins(html, "\"ou\":\"(https?://[^\"]+)\"");
    }

    // ------------------------------------------------------------ Yandex Images (HTML)
    private List<Pin> yandex(String q, int p) throws Exception {
        String html = unesc(Net.string("https://yandex.com/images/search?text=" + Net.enc(q) + "&p=" + p,
                Net.DESKTOP_UA, "https://yandex.com/", null));
        return regexPins(html, "\"img_href\":\"(https?://[^\"]+)\"");
    }

    // ------------------------------------------------------------ Brave Images (HTML, thumbnail proxy)
    private List<Pin> brave(String q, int p) throws Exception {
        String html = Net.string("https://search.brave.com/images?q=" + Net.enc(q) + "&offset=" + p,
                Net.DESKTOP_UA, "https://search.brave.com/", null).replace("&amp;", "&");
        return regexPins(html, "(?:src|data-src)=\"(https://imgs\\.search\\.brave\\.com/[^\"]+)\"");
    }

    // ------------------------------------------------------------ Pinterest (endpoint resource pencarian pin)
    private static String firstNonEmpty(String... v) {
        for (String s : v) if (s != null && !s.trim().isEmpty()) return s.trim();
        return "";
    }

    private List<Pin> pinterest(String q, int p) throws Exception {
        if (p == 0 || !q.equals(pinQuery)) {
            pinBookmark = "";
            pinQuery = q;
        }
        JSONObject opts = new JSONObject()
                .put("query", q)
                .put("scope", "pins")
                .put("page_size", 25)
                .put("bookmarks", new JSONArray().put(pinBookmark));
        JSONObject data = new JSONObject().put("options", opts).put("context", new JSONObject());
        String url = "https://www.pinterest.com/resource/BaseSearchResource/get/?source_url="
                + Net.enc("/search/pins/?q=" + q) + "&data=" + Net.enc(data.toString())
                + "&_=" + System.currentTimeMillis();
        String js = Net.string(url, Net.DESKTOP_UA, "https://www.pinterest.com/", null, new String[]{
                "Accept", "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With", "XMLHttpRequest",
                "X-Pinterest-PWS-Handler", "www/search/[scope].js",
                "X-Pinterest-AppState", "active"});
        JSONObject rr = new JSONObject(js).getJSONObject("resource_response");
        pinBookmark = rr.optString("bookmark", "");
        JSONArray results = rr.getJSONObject("data").optJSONArray("results");
        List<Pin> out = new ArrayList<>();
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject pin = results.optJSONObject(i);
            JSONObject imgs = pin == null ? null : pin.optJSONObject("images");
            if (imgs == null) continue;
            JSONObject big = imgs.optJSONObject("orig");
            if (big == null) big = imgs.optJSONObject("736x");
            if (big == null) continue;
            String full = big.optString("url");
            if (!isHttp(full)) continue;
            JSONObject small = imgs.optJSONObject("236x");
            String thumb = small != null && isHttp(small.optString("url")) ? small.optString("url") : full;
            Pin made = new Pin(thumb, full, "https://www.pinterest.com/pin/" + pin.optString("id") + "/",
                    ratio(big.optInt("width"), big.optInt("height")));
            made.title = firstNonEmpty(pin.optString("grid_title"), pin.optString("title"), pin.optString("description"));
            out.add(made);
        }
        return out;
    }

    // ------------------------------------------------------------ Openverse (API publik, lisensi terbuka)
    private List<Pin> openverse(String q, int p) throws Exception {
        String js = Net.string("https://api.openverse.org/v1/images/?q=" + Net.enc(q)
                + "&page=" + (p + 1) + "&page_size=20&mature=false", Net.API_UA, null, null);
        JSONArray a = new JSONObject(js).getJSONArray("results");
        List<Pin> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String full = o.optString("url");
            if (!isHttp(full)) continue;
            String thumb = o.optString("thumbnail", full);
            out.add(new Pin(isHttp(thumb) ? thumb : full, full, o.optString("foreign_landing_url", full),
                    ratio(o.optInt("width"), o.optInt("height"))));
        }
        return out;
    }

    // ------------------------------------------------------------ Wikimedia Commons (MediaWiki API)
    private List<Pin> commons(String q, int p) throws Exception {
        String js = Net.string("https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2"
                + "&generator=search&gsrnamespace=6&gsrlimit=30&gsroffset=" + (p * 30)
                + "&gsrsearch=" + Net.enc("filetype:bitmap " + q)
                + "&prop=imageinfo&iiprop=url|size|mime&iiurlwidth=640", Net.API_UA, null, null);
        List<Pin> out = new ArrayList<>();
        JSONObject query = new JSONObject(js).optJSONObject("query");
        JSONArray pages = query == null ? null : query.optJSONArray("pages");
        if (pages == null) return out;
        for (int i = 0; i < pages.length(); i++) {
            JSONObject page = pages.optJSONObject(i);
            JSONArray ii = page == null ? null : page.optJSONArray("imageinfo");
            JSONObject info = ii == null ? null : ii.optJSONObject(0);
            if (info == null) continue;
            String mime = info.optString("mime");
            if (!mime.equals("image/jpeg") && !mime.equals("image/png") && !mime.equals("image/webp")) continue;
            String full = info.optString("url");
            if (!isHttp(full)) continue;
            String thumb = info.optString("thumburl", full);
            out.add(new Pin(isHttp(thumb) ? thumb : full, full, info.optString("descriptionurl", full),
                    ratio(info.optInt("width"), info.optInt("height"))));
        }
        return out;
    }

    // ------------------------------------------------------------ Unsplash (endpoint pencarian situs)
    private List<Pin> unsplash(String q, int p) throws Exception {
        String js = Net.string("https://unsplash.com/napi/search/photos?query=" + Net.enc(q)
                + "&per_page=30&page=" + (p + 1), Net.DESKTOP_UA, "https://unsplash.com/", null);
        JSONArray a = new JSONObject(js).optJSONArray("results");
        List<Pin> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            JSONObject urls = o == null ? null : o.optJSONObject("urls");
            if (urls == null) continue;
            String full = urls.optString("regular");
            if (!isHttp(full)) continue;
            String thumb = urls.optString("small", full);
            JSONObject links = o.optJSONObject("links");
            String link = links != null ? links.optString("html", full) : full;
            out.add(new Pin(isHttp(thumb) ? thumb : full, full, link,
                    ratio(o.optInt("width"), o.optInt("height"))));
        }
        return out;
    }

    // ------------------------------------------------------------ Flickr (HTML, URL staticflickr)
    private List<Pin> flickr(String q, int p) throws Exception {
        String html = Net.string("https://www.flickr.com/search/?text=" + Net.enc(q) + "&page=" + (p + 1),
                Net.DESKTOP_UA, "https://www.flickr.com/", null).replace("\\/", "/");
        return regexPins(html, "(https://live\\.staticflickr\\.com/[^\"\\s\\\\]+?\\.jpg)");
    }

    // ------------------------------------------------------------ Wallhaven (JSON publik, SFW saja)
    private List<Pin> wallhaven(String q, int p) throws Exception {
        String js = Net.string("https://wallhaven.cc/api/v1/search?q=" + Net.enc(q) + "&page=" + (p + 1)
                + "&purity=100&categories=111&ratios=portrait&sorting=relevance", Net.UA, null, null);
        JSONArray a = new JSONObject(js).getJSONArray("data");
        List<Pin> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            try {
                JSONObject w = a.getJSONObject(i);
                float r = w.optInt("dimension_y", 0) / (float) Math.max(1, w.optInt("dimension_x", 1));
                out.add(new Pin(w.getJSONObject("thumbs").getString("large"), w.getString("path"),
                        w.optString("url"), r));
            } catch (Exception ignored) { }
        }
        return out;
    }

    // ------------------------------------------------------------ Reddit (JSON publik, non-NSFW)
    private List<Pin> reddit(String q, boolean useAfter) throws Exception {
        String after = redditAfter;
        String url = "https://www.reddit.com/search.json?q=" + Net.enc(q) + "&limit=50&raw_json=1&type=link"
                + (useAfter && !after.isEmpty() ? "&after=" + after : "");
        String js = Net.string(url, "android:com.pinpic.app:v1.0 (by /u/pinpic)", null, null);
        JSONObject data = new JSONObject(js).getJSONObject("data");
        if (useAfter) redditAfter = data.isNull("after") ? "" : data.getString("after");
        JSONArray kids = data.getJSONArray("children");
        List<Pin> out = new ArrayList<>();
        for (int i = 0; i < kids.length(); i++) {
            try {
                JSONObject d = kids.getJSONObject(i).getJSONObject("data");
                if (d.optBoolean("over_18") || !"image".equals(d.optString("post_hint"))) continue;
                JSONObject img = d.getJSONObject("preview").getJSONArray("images").getJSONObject(0);
                JSONObject src = img.getJSONObject("source");
                String thumb = src.getString("url");
                JSONArray res = img.optJSONArray("resolutions");
                if (res != null) {
                    for (int j = 0; j < res.length(); j++) {
                        if (res.getJSONObject(j).getInt("width") <= 640) thumb = res.getJSONObject(j).getString("url");
                    }
                }
                float r = src.getInt("height") / (float) Math.max(1, src.getInt("width"));
                out.add(new Pin(thumb, d.getString("url"), "https://www.reddit.com" + d.optString("permalink"), r));
            } catch (Exception ignored) { }
        }
        return out;
    }
}
