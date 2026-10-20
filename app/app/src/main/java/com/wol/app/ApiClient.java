package com.wol.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 原生 HTTP 客户端：Basic Auth + 2FA Cookie + JSON 解析
 * 直接调用路由器 CGI 接口，不依赖 Web 页面。
 */
public class ApiClient {

    private static final String TAG = "WolApi";
    private final SharedPreferences prefs;
    private String baseUrl;

    public ApiClient(Context ctx) {
        prefs = ctx.getSharedPreferences("wol_prefs", Context.MODE_PRIVATE);
        baseUrl = prefs.getString("url", "");
        if (!baseUrl.endsWith("/")) baseUrl += "/";
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String url) {
        if (!url.endsWith("/")) url += "/";
        baseUrl = url;
        prefs.edit().putString("url", url).apply();
    }

    public String getUsername() {
        return prefs.getString("username", "");
    }

    public void setCredentials(String user, String pass) {
        prefs.edit().putString("username", user).putString("password", pass).apply();
    }

    public String getPassword() {
        return prefs.getString("password", "");
    }

    private String getCookie() {
        return prefs.getString("cookie", "");
    }

    private void saveCookie(String cookie) {
        prefs.edit().putString("cookie", cookie).apply();
    }

    public void clearCookie() {
        prefs.edit().remove("cookie").apply();
    }

    /** 发起 GET 请求，返回 JSONObject；失败抛异常 */
    public JSONObject get(String path) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Accept", "application/json");

            String user = getUsername();
            String pass = getPassword();
            if (!user.isEmpty()) {
                String auth = Base64.getEncoder()
                        .encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
                conn.setRequestProperty("Authorization", "Basic " + auth);
            }
            String cookie = getCookie();
            if (!cookie.isEmpty()) {
                conn.setRequestProperty("Cookie", cookie);
            }

            int code = conn.getResponseCode();

            // 2FA session cookie
            String setCookie = conn.getHeaderField("Set-Cookie");
            if (setCookie != null && setCookie.contains("wol_2fa=")) {
                String tokenPart = setCookie.split(";")[0].trim();
                saveCookie(tokenPart);
                Log.d(TAG, "saved cookie: " + tokenPart);
            }

            InputStream is = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
            String body = readAll(is);

            if (code == 401) {
                JSONObject err = new JSONObject();
                err.put("ok", false);
                err.put("err", "auth_failed");
                err.put("http", 401);
                return err;
            }
            if (body == null || body.isEmpty()) {
                JSONObject err = new JSONObject();
                err.put("ok", false);
                err.put("err", "empty_response");
                err.put("http", code);
                return err;
            }
            return new JSONObject(body);
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line).append('\n');
        }
        r.close();
        return sb.toString();
    }

    // ---------- 业务接口封装 ----------
    public JSONObject check2fa() throws Exception { return get("cgi-bin/check2fa"); }
    public JSONObject verify2fa(String code) throws Exception { return get("cgi-bin/verify2fa?code=" + code); }
    public JSONObject devices() throws Exception { return get("cgi-bin/devices"); }
    public JSONObject status(String mac) throws Exception { return get("cgi-bin/status?mac=" + mac); }
    public JSONObject wake(String mac) throws Exception { return get("cgi-bin/wol?mac=" + mac); }
    public JSONObject scan() throws Exception { return get("cgi-bin/scan"); }
    public JSONObject history(String mac) throws Exception { return get("cgi-bin/wakelog?mac=" + mac); }
    public JSONObject addDevice(String name, String mac) throws Exception {
        return get("cgi-bin/devices?action=add&name=" + URLEncoderUtil.encode(name) + "&mac=" + URLEncoderUtil.encode(mac));
    }
    public JSONObject updateDevice(int idx, String name, String mac) throws Exception {
        return get("cgi-bin/devices?action=upd&idx=" + idx + "&name=" + URLEncoderUtil.encode(name) + "&mac=" + URLEncoderUtil.encode(mac));
    }
    public JSONObject deleteDevice(int idx) throws Exception {
        return get("cgi-bin/devices?action=del&idx=" + idx);
    }

    /** 简易 URL 编码 */
    public static class URLEncoderUtil {
        public static String encode(String s) {
            try {
                return java.net.URLEncoder.encode(s, "UTF-8");
            } catch (Exception e) {
                return s;
            }
        }
    }
}
