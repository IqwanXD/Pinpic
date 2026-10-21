package com.pinpic.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

final class Net {
    static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    /** Sopan untuk API publik yang meminta User-Agent deskriptif (mis. Wikimedia). */
    static final String API_UA = "Pinpic/1.0 (Android; https://github.com/IqwanXD/)";

    static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    static String origin(String url) {
        try {
            URL u = new URL(url);
            return u.getProtocol() + "://" + u.getHost() + "/";
        } catch (Exception e) { return null; }
    }

    static HttpURLConnection open(String url, String ua, String referer, String cookie) throws IOException {
        return open(url, ua, referer, cookie, null);
    }

    /** Redirect ditangani manual supaya http -> https tetap jalan. extra = pasangan nama, nilai header. */
    static HttpURLConnection open(String url, String ua, String referer, String cookie, String[] extra) throws IOException {
        for (int i = 0; i < 6; i++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(10000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", ua);
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9,id;q=0.8");
            if (referer != null) c.setRequestProperty("Referer", referer);
            if (cookie != null) c.setRequestProperty("Cookie", cookie);
            if (extra != null) {
                for (int k = 0; k + 1 < extra.length; k += 2) c.setRequestProperty(extra[k], extra[k + 1]);
            }
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("redirect tanpa Location");
                url = new URL(new URL(url), loc).toString();
                continue;
            }
            if (code >= 400) {
                c.disconnect();
                throw new IOException("HTTP " + code);
            }
            return c;
        }
        throw new IOException("terlalu banyak redirect");
    }

    static byte[] bytes(String url, String ua, String referer, String cookie, int max) throws IOException {
        return bytes(url, ua, referer, cookie, null, max);
    }

    static byte[] bytes(String url, String ua, String referer, String cookie, String[] extra, int max) throws IOException {
        HttpURLConnection c = open(url, ua, referer, cookie, extra);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > max) throw new IOException("file terlalu besar");
            }
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    static String string(String url, String ua, String referer, String cookie) throws IOException {
        return string(url, ua, referer, cookie, null);
    }

    static String string(String url, String ua, String referer, String cookie, String[] extra) throws IOException {
        return new String(bytes(url, ua, referer, cookie, extra, 8 << 20), "UTF-8");
    }
}
